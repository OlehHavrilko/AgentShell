package dev.agentshell.llm.provider.impl

import dev.agentshell.llm.LlmMessage
import dev.agentshell.llm.LlmResponse
import dev.agentshell.llm.LlmToolCall
import dev.agentshell.llm.provider.CompletionRequest
import dev.agentshell.llm.provider.CompletionResponse
import dev.agentshell.llm.provider.ProviderYaml
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient

/** Google Gemini via the Generative Language REST API (not Vertex AI). */
class GeminiProvider(http: OkHttpClient, private val cfg: ProviderYaml) : BaseHttpProvider(http) {
    override val id = "gemini"
    override val displayName = "Google Gemini"
    private val apiKey get() = cfg.apiKey?.ifBlank { null } ?: System.getenv("GEMINI_API_KEY") ?: System.getenv("GOOGLE_API_KEY") ?: ""
    private val model get() = cfg.model ?: "gemini-1.5-flash"
    private val url get() = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

    override suspend fun complete(request: CompletionRequest): CompletionResponse {
        val contextMessages = request.messages.filter { it.role != LlmMessage.Role.system }
        val sysPrompt = request.systemPrompt
            ?: request.messages.firstOrNull { it.role == LlmMessage.Role.system }?.content

        val body = buildJsonObject {
            sysPrompt?.let {
                put("system_instruction", buildJsonObject {
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", it) }) })
                })
            }
            put("contents", buildJsonArray {
                contextMessages.forEach { msg ->
                    add(buildJsonObject {
                        put("role", if (msg.role == LlmMessage.Role.assistant) "model" else "user")
                        put("parts", buildJsonArray {
                            if (!msg.content.isNullOrBlank()) {
                                add(buildJsonObject { put("text", msg.content) })
                            }
                            for (tc in msg.toolCalls) {
                                add(buildJsonObject {
                                    put("functionCall", buildJsonObject {
                                        put("name", tc.toolName)
                                        put("args", json.parseToJsonElement(tc.argumentsJson))
                                    })
                                })
                            }
                        })
                    })
                }
            })
            if (request.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    add(buildJsonObject {
                        put("function_declarations", buildJsonArray {
                            request.tools.forEach { tool ->
                                add(buildJsonObject {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("parameters", json.parseToJsonElement(tool.parametersJson))
                                })
                            }
                        })
                    })
                })
            }
            put("generationConfig", buildJsonObject {
                put("temperature", request.temperature)
                put("maxOutputTokens", request.maxTokens)
            })
        }
        // Gemini doesn't use Authorization header — API key is in the URL
        val resp = post(url, body, emptyMap())
        return parseGeminiResponse(resp)
    }

    private fun parseGeminiResponse(body: String): CompletionResponse {
        val root = json.parseToJsonElement(body).jsonObject
        val usage = root["usageMetadata"]?.jsonObject
        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: return CompletionResponse(content = null, providerId = id, modelUsed = model)
        val finishReason = candidate["finishReason"]?.jsonPrimitive?.contentOrNull
        val parts = candidate["content"]?.jsonObject?.get("parts")?.jsonArray ?: return CompletionResponse(
            content = null, providerId = id, modelUsed = model
        )
        val textParts = mutableListOf<String>()
        val toolCalls = mutableListOf<LlmToolCall>()
        for (part in parts) {
            val obj = part.jsonObject
            obj["text"]?.jsonPrimitive?.contentOrNull?.let { textParts.add(it) }
            obj["functionCall"]?.jsonObject?.let { fc ->
                val name = fc["name"]?.jsonPrimitive?.content ?: return@let
                val argsJson = fc["args"]?.let { json.encodeToString(JsonElement.serializer(), it) } ?: "{}"
                toolCalls.add(LlmToolCall(id = "gemini-$name-${System.nanoTime()}", toolName = name, argumentsJson = argsJson))
            }
        }
        val stopReason = when {
            toolCalls.isNotEmpty() -> LlmResponse.StopReason.TOOL_USE
            finishReason == "MAX_TOKENS" -> LlmResponse.StopReason.MAX_TOKENS
            else -> LlmResponse.StopReason.END_TURN
        }
        return CompletionResponse(
            content = textParts.joinToString("\n").ifBlank { null },
            toolCalls = toolCalls,
            stopReason = stopReason,
            inputTokens = usage?.get("promptTokenCount")?.jsonPrimitive?.intOrNull ?: 0,
            outputTokens = usage?.get("candidatesTokenCount")?.jsonPrimitive?.intOrNull ?: 0,
            providerId = id,
            modelUsed = model,
        )
    }
}
