package dev.agentshell.llm

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * LlmGateway implementation for Google Gemini via the Generative Language API.
 *
 * Required env var: GEMINI_API_KEY (or GOOGLE_API_KEY)
 *
 * Popular models:
 *   - "gemini-2.0-flash"         (fast, cheap, multimodal)
 *   - "gemini-2.0-flash-lite"    (fastest, cheapest)
 *   - "gemini-1.5-pro"           (large context, 1M tokens)
 *   - "gemini-1.5-flash"         (fast)
 *
 * Uses the REST API: POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent
 */
class GeminiGateway(
    private val model: String = "gemini-2.0-flash",
    private val apiKey: String = (System.getenv("GEMINI_API_KEY") ?: System.getenv("GOOGLE_API_KEY") ?: ""),
    private val timeoutSeconds: Long = 120,
) : LlmGateway {

    private val log = LoggerFactory.getLogger(GeminiGateway::class.java)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val json = Json { ignoreUnknownKeys = true }
    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models"

    init {
        require(apiKey.isNotBlank()) {
            "Gemini API key is required. Set GEMINI_API_KEY or GOOGLE_API_KEY environment variable."
        }
    }

    override fun complete(
        messages: List<LlmMessage>,
        tools: List<LlmTool>,
        systemPrompt: String?,
    ): LlmResponse {
        val body = buildJsonObject {
            // System instruction
            if (systemPrompt != null) {
                put("system_instruction", buildJsonObject {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", JsonPrimitive(systemPrompt)) })
                    })
                })
            }
            // Contents — convert conversation to Gemini format
            put("contents", buildContentsArray(messages))
            // Tools
            if (tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    add(buildJsonObject {
                        put("function_declarations", buildToolsArray(tools))
                    })
                })
                put("tool_config", buildJsonObject {
                    put("function_calling_config", buildJsonObject {
                        put("mode", JsonPrimitive("AUTO"))
                    })
                })
            }
            // Generation config
            put("generationConfig", buildJsonObject {
                put("temperature", JsonPrimitive(0.7))
            })
        }

        val url = "$baseUrl/$model:generateContent?key=$apiKey"
        log.debug("Gemini request: model={} messages={} tools={}", model, messages.size, tools.size)

        val request = HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(body)))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .build()

        return try {
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                log.error("Gemini error {}: {}", response.statusCode(), response.body().take(300))
                return LlmResponse(
                    content = "Gemini API error ${response.statusCode()}: ${response.body().take(200)}",
                    stopReason = LlmResponse.StopReason.ERROR,
                )
            }
            parseResponse(response.body())
        } catch (e: Exception) {
            log.error("Gemini request failed: {}", e.message, e)
            LlmResponse(content = "Gemini unreachable: ${e.message}", stopReason = LlmResponse.StopReason.ERROR)
        }
    }

    private fun parseResponse(body: String): LlmResponse {
        val root = json.parseToJsonElement(body).jsonObject

        val usageMetadata = root["usageMetadata"]?.jsonObject
        val inputTokens = usageMetadata?.get("promptTokenCount")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val outputTokens = usageMetadata?.get("candidatesTokenCount")?.jsonPrimitive?.content?.toIntOrNull() ?: 0

        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: return LlmResponse(content = null, stopReason = LlmResponse.StopReason.ERROR, inputTokens = inputTokens, outputTokens = outputTokens)

        val finishReason = candidate["finishReason"]?.jsonPrimitive?.content

        val parts = candidate["content"]?.jsonObject?.get("parts")?.jsonArray ?: return LlmResponse(
            content = null, stopReason = LlmResponse.StopReason.END_TURN,
            inputTokens = inputTokens, outputTokens = outputTokens,
        )

        val textParts = mutableListOf<String>()
        val toolCalls = mutableListOf<LlmToolCall>()

        for (part in parts) {
            val partObj = part.jsonObject
            partObj["text"]?.jsonPrimitive?.content?.let { textParts.add(it) }
            partObj["functionCall"]?.jsonObject?.let { fc ->
                val name = fc["name"]?.jsonPrimitive?.content ?: return@let
                val argsJson = fc["args"]?.let { args ->
                    when (args) {
                        is JsonObject -> json.encodeToString(args)
                        else -> args.toString()
                    }
                } ?: "{}"
                toolCalls.add(LlmToolCall(
                    id = "gemini-${name}-${System.nanoTime()}",
                    toolName = name,
                    argumentsJson = argsJson,
                ))
            }
        }

        val content = textParts.joinToString("\n").ifBlank { null }
        val stopReason = when {
            toolCalls.isNotEmpty() -> LlmResponse.StopReason.TOOL_USE
            finishReason == "MAX_TOKENS" -> LlmResponse.StopReason.MAX_TOKENS
            else -> LlmResponse.StopReason.END_TURN
        }

        log.debug("Gemini response: stopReason={} toolCalls={} tokens={}/{}", stopReason, toolCalls.size, inputTokens, outputTokens)

        return LlmResponse(
            content = content,
            toolCalls = toolCalls,
            stopReason = stopReason,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
        )
    }

    /**
     * Convert LlmMessages to Gemini `contents` format.
     * Gemini roles: "user" | "model" (not "assistant")
     * Tool results go as `functionResponse` parts on the "user" turn.
     */
    private fun buildContentsArray(messages: List<LlmMessage>): JsonArray = buildJsonArray {
        for (msg in messages) {
            val geminiRole = when (msg.role) {
                LlmMessage.Role.assistant -> "model"
                LlmMessage.Role.tool -> "user"
                else -> "user"
            }
            add(buildJsonObject {
                put("role", JsonPrimitive(geminiRole))
                put("parts", buildJsonArray {
                    // Text content
                    if (!msg.content.isNullOrBlank()) {
                        add(buildJsonObject { put("text", JsonPrimitive(msg.content)) })
                    }
                    // Tool calls (assistant → functionCall parts)
                    for (tc in msg.toolCalls) {
                        add(buildJsonObject {
                            put("functionCall", buildJsonObject {
                                put("name", JsonPrimitive(tc.toolName))
                                put("args", json.parseToJsonElement(tc.argumentsJson))
                            })
                        })
                    }
                    // Tool result (tool role → functionResponse part)
                    if (msg.role == LlmMessage.Role.tool && msg.toolCallId != null) {
                        add(buildJsonObject {
                            put("functionResponse", buildJsonObject {
                                put("name", JsonPrimitive(msg.toolCallId))
                                put("response", buildJsonObject {
                                    put("output", JsonPrimitive(msg.content ?: ""))
                                })
                            })
                        })
                    }
                })
            })
        }
    }

    private fun buildToolsArray(tools: List<LlmTool>): JsonArray = buildJsonArray {
        for (tool in tools) {
            add(buildJsonObject {
                put("name", JsonPrimitive(tool.name))
                put("description", JsonPrimitive(tool.description))
                put("parameters", json.parseToJsonElement(tool.parametersJson))
            })
        }
    }
}
