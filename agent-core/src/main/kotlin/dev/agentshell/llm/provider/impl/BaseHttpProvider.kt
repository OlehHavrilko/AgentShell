package dev.agentshell.llm.provider.impl

import dev.agentshell.llm.LlmMessage
import dev.agentshell.llm.LlmTool
import dev.agentshell.llm.LlmToolCall
import dev.agentshell.llm.provider.CompletionRequest
import dev.agentshell.llm.provider.CompletionResponse
import dev.agentshell.llm.provider.LlmException
import dev.agentshell.llm.provider.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory

abstract class BaseHttpProvider(protected val http: OkHttpClient) : LlmProvider {
    protected val log = LoggerFactory.getLogger(this::class.java)
    protected val json = Json { ignoreUnknownKeys = true }
    protected val JSON_MT = "application/json".toMediaType()

    protected suspend fun post(url: String, body: JsonObject, headers: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val reqBody = json.encodeToString(JsonObject.serializer(), body).toRequestBody(JSON_MT)
            val reqBuilder = Request.Builder().url(url).post(reqBody)
            headers.forEach { (k, v) -> reqBuilder.header(k, v) }
            http.newCall(reqBuilder.build()).execute().use { resp ->
                val bodyStr = resp.body?.string() ?: ""
                if (!resp.isSuccessful) {
                    log.error("{} HTTP {}: {}", id, resp.code, bodyStr.take(300))
                    throw LlmException("$id HTTP ${resp.code}: ${bodyStr.take(200)}")
                }
                bodyStr
            }
        }

    /** Build messages array in OpenAI format */
    protected fun buildMessagesArray(messages: List<LlmMessage>, systemPrompt: String?): JsonArray = buildJsonArray {
        systemPrompt?.let { add(buildJsonObject { put("role", "system"); put("content", it) }) }
        for (msg in messages) {
            add(buildJsonObject {
                put("role", msg.role.name)
                msg.toolCallId?.let { put("tool_call_id", it) }
                msg.content?.let { put("content", it) }
                if (msg.toolCalls.isNotEmpty()) {
                    put("tool_calls", buildJsonArray {
                        msg.toolCalls.forEach { tc ->
                            add(buildJsonObject {
                                put("id", tc.id)
                                put("type", "function")
                                put("function", buildJsonObject {
                                    put("name", tc.toolName)
                                    put("arguments", tc.argumentsJson)
                                })
                            })
                        }
                    })
                }
            })
        }
    }

    protected fun buildToolsArray(tools: List<LlmTool>): JsonArray = buildJsonArray {
        tools.forEach { tool ->
            add(buildJsonObject {
                put("type", "function")
                put("function", buildJsonObject {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", json.parseToJsonElement(tool.parametersJson))
                })
            })
        }
    }

    /** Parse OpenAI-compatible response */
    protected fun parseOpenAiResponse(body: String, provId: String, modelName: String): CompletionResponse {
        val root = json.parseToJsonElement(body).jsonObject
        val usage = root["usage"]?.jsonObject
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: return CompletionResponse(content = null, providerId = provId, modelUsed = modelName)
        val message = choice["message"]?.jsonObject
            ?: return CompletionResponse(content = null, providerId = provId, modelUsed = modelName)
        val content = message["content"]?.jsonPrimitive?.contentOrNull
        val finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNull
        val toolCalls = message["tool_calls"]?.jsonArray?.map { tc ->
            val o = tc.jsonObject
            val fn = o["function"]?.jsonObject
            LlmToolCall(
                id = o["id"]?.jsonPrimitive?.content ?: "",
                toolName = fn?.get("name")?.jsonPrimitive?.content ?: "",
                argumentsJson = fn?.get("arguments")?.jsonPrimitive?.content ?: "{}",
            )
        } ?: emptyList()
        val stopReason = when {
            toolCalls.isNotEmpty() -> dev.agentshell.llm.LlmResponse.StopReason.TOOL_USE
            finishReason == "length" -> dev.agentshell.llm.LlmResponse.StopReason.MAX_TOKENS
            else -> dev.agentshell.llm.LlmResponse.StopReason.END_TURN
        }
        return CompletionResponse(
            content = content,
            toolCalls = toolCalls,
            stopReason = stopReason,
            inputTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.intOrNull ?: 0,
            outputTokens = usage?.get("completion_tokens")?.jsonPrimitive?.intOrNull ?: 0,
            providerId = provId,
            modelUsed = modelName,
        )
    }
}
