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
 * LlmGateway implementation for Ollama local models.
 * Uses the Ollama /api/chat endpoint (supports tool calling since Ollama 0.3+).
 *
 * @param model e.g. "llama3.1", "mistral", "qwen2.5-coder"
 * @param baseUrl Ollama server URL (default: http://localhost:11434)
 */
class OllamaGateway(
    private val model: String = "llama3.1",
    private val baseUrl: String = "http://localhost:11434",
    private val timeoutSeconds: Long = 120,
) : LlmGateway {

    private val log = LoggerFactory.getLogger(OllamaGateway::class.java)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val json = Json { ignoreUnknownKeys = true }

    override fun complete(
        messages: List<LlmMessage>,
        tools: List<LlmTool>,
        systemPrompt: String?,
    ): LlmResponse {
        val allMessages = buildList {
            if (systemPrompt != null) add(LlmMessage(LlmMessage.Role.system, systemPrompt))
            addAll(messages)
        }

        val body = buildJsonObject {
            put("model", JsonPrimitive(model))
            put("stream", JsonPrimitive(false))
            put("messages", buildMessagesArray(allMessages))
            if (tools.isNotEmpty()) put("tools", buildToolsArray(tools))
        }

        log.debug("Ollama request: model={} messages={} tools={}", model, allMessages.size, tools.size)

        val request = HttpRequest.newBuilder(URI.create("$baseUrl/api/chat"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(body)))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .build()

        return try {
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                log.error("Ollama error {}: {}", response.statusCode(), response.body())
                return LlmResponse(content = "Ollama error ${response.statusCode()}", stopReason = LlmResponse.StopReason.ERROR)
            }
            parseResponse(response.body())
        } catch (e: Exception) {
            log.error("Ollama connection failed: {}", e.message)
            LlmResponse(content = "Ollama unreachable: ${e.message}", stopReason = LlmResponse.StopReason.ERROR)
        }
    }

    private fun parseResponse(body: String): LlmResponse {
        val root = json.parseToJsonElement(body).jsonObject
        val message = root["message"]?.jsonObject
            ?: return LlmResponse(content = null, stopReason = LlmResponse.StopReason.ERROR)

        val content = message["content"]?.jsonPrimitive?.content
        val doneReason = root["done_reason"]?.jsonPrimitive?.content

        val promptTokens = root["prompt_eval_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val evalTokens = root["eval_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0

        // Ollama tool_calls format
        val toolCalls = message["tool_calls"]?.jsonArray?.mapNotNull { tc ->
            val fn = tc.jsonObject["function"]?.jsonObject ?: return@mapNotNull null
            LlmToolCall(
                id = "ollama-${System.nanoTime()}",
                toolName = fn["name"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                argumentsJson = fn["arguments"]?.let { args ->
                    when (args) {
                        is JsonObject -> json.encodeToString(args)
                        is JsonPrimitive -> args.content
                        else -> "{}"
                    }
                } ?: "{}",
            )
        } ?: emptyList()

        val stopReason = when {
            toolCalls.isNotEmpty() -> LlmResponse.StopReason.TOOL_USE
            doneReason == "length" -> LlmResponse.StopReason.MAX_TOKENS
            else -> LlmResponse.StopReason.END_TURN
        }

        log.debug("Ollama response: stopReason={} toolCalls={} tokens={}/{}", stopReason, toolCalls.size, promptTokens, evalTokens)

        return LlmResponse(
            content = content,
            toolCalls = toolCalls,
            stopReason = stopReason,
            inputTokens = promptTokens,
            outputTokens = evalTokens,
        )
    }

    private fun buildMessagesArray(messages: List<LlmMessage>): JsonArray = buildJsonArray {
        for (msg in messages) {
            add(buildJsonObject {
                put("role", JsonPrimitive(msg.role.name))
                if (msg.content != null) put("content", JsonPrimitive(msg.content))
                if (msg.toolCalls.isNotEmpty()) {
                    put("tool_calls", buildJsonArray {
                        for (tc in msg.toolCalls) {
                            add(buildJsonObject {
                                put("function", buildJsonObject {
                                    put("name", JsonPrimitive(tc.toolName))
                                    put("arguments", json.parseToJsonElement(tc.argumentsJson))
                                })
                            })
                        }
                    })
                }
            })
        }
    }

    private fun buildToolsArray(tools: List<LlmTool>): JsonArray = buildJsonArray {
        for (tool in tools) {
            add(buildJsonObject {
                put("type", JsonPrimitive("function"))
                put("function", buildJsonObject {
                    put("name", JsonPrimitive(tool.name))
                    put("description", JsonPrimitive(tool.description))
                    put("parameters", json.parseToJsonElement(tool.parametersJson))
                })
            })
        }
    }
}
