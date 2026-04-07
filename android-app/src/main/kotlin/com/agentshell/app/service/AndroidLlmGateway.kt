package com.agentshell.app.service

import dev.agentshell.llm.*
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * OkHttp-based OpenAI-compatible [LlmGateway] for use from Android.
 *
 * Works with: OpenAI, Groq, DeepSeek, Mistral, OpenRouter, Ollama (v1-compat), LMStudio.
 * Supports tool_calls (function calling).
 */
class AndroidLlmGateway(
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String = "https://api.openai.com/v1",
    timeoutSeconds: Long = 120,
) : LlmGateway {

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    override fun complete(
        messages: List<LlmMessage>,
        tools: List<LlmTool>,
        systemPrompt: String?,
    ): LlmResponse {
        val allMessages = buildList {
            if (systemPrompt != null) add(LlmMessage(LlmMessage.Role.system, systemPrompt))
            addAll(messages)
        }

        val bodyObj = buildJsonObject {
            put("model", model)
            put("messages", buildMessagesArray(allMessages))
            if (tools.isNotEmpty()) put("tools", buildToolsArray(tools))
        }

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(bodyObj.toString().toRequestBody(mediaType))
            .build()

        val responseBody = http.newCall(request).execute().use { resp ->
            resp.body?.string() ?: return LlmResponse(
                content = "Empty response from $baseUrl",
                stopReason = LlmResponse.StopReason.ERROR
            )
        }

        return parseResponse(responseBody)
    }

    private fun buildMessagesArray(messages: List<LlmMessage>) = buildJsonArray {
        messages.forEach { msg ->
            addJsonObject {
                put("role", msg.role.name)
                when {
                    msg.content != null -> put("content", msg.content)
                    msg.toolCallId != null -> {
                        put("tool_call_id", msg.toolCallId)
                        put("content", "")
                    }
                }
                if (msg.toolCalls.isNotEmpty()) {
                    put("tool_calls", buildJsonArray {
                        msg.toolCalls.forEach { tc ->
                            addJsonObject {
                                put("id", tc.id)
                                put("type", "function")
                                putJsonObject("function") {
                                    put("name", tc.toolName)
                                    put("arguments", tc.argumentsJson)
                                }
                            }
                        }
                    })
                }
            }
        }
    }

    private fun buildToolsArray(tools: List<LlmTool>) = buildJsonArray {
        tools.forEach { tool ->
            addJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", json.parseToJsonElement(tool.parametersJson))
                }
            }
        }
    }

    private fun parseResponse(body: String): LlmResponse {
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val choice = root["choices"]?.jsonArray?.getOrNull(0)?.jsonObject
                ?: return LlmResponse(content = body, stopReason = LlmResponse.StopReason.END_TURN)
            val message = choice["message"]?.jsonObject
                ?: return LlmResponse(content = body, stopReason = LlmResponse.StopReason.END_TURN)

            val content = message["content"]?.jsonPrimitive?.contentOrNull
            val finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
            val usage = root["usage"]?.jsonObject

            val toolCalls = message["tool_calls"]?.jsonArray?.mapNotNull { tc ->
                runCatching {
                    val obj = tc.jsonObject
                    val fn = obj["function"]?.jsonObject ?: return@mapNotNull null
                    LlmToolCall(
                        id = obj["id"]?.jsonPrimitive?.content ?: "",
                        toolName = fn["name"]?.jsonPrimitive?.content ?: "",
                        argumentsJson = fn["arguments"]?.jsonPrimitive?.content ?: "{}",
                    )
                }.getOrNull()
            } ?: emptyList()

            val stopReason = when (finishReason) {
                "tool_calls" -> LlmResponse.StopReason.TOOL_USE
                "length" -> LlmResponse.StopReason.MAX_TOKENS
                else -> LlmResponse.StopReason.END_TURN
            }

            LlmResponse(
                content = content,
                toolCalls = toolCalls,
                stopReason = stopReason,
                inputTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.intOrNull ?: 0,
                outputTokens = usage?.get("completion_tokens")?.jsonPrimitive?.intOrNull ?: 0,
            )
        }.getOrElse { e ->
            LlmResponse(content = "Parse error: ${e.message}", stopReason = LlmResponse.StopReason.ERROR)
        }
    }
}
