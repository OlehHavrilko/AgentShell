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
 * LlmGateway implementation for OpenAI-compatible APIs (ChatGPT, compatible endpoints).
 *
 * Reads OPENAI_API_KEY from environment. Supports tool_calls.
 *
 * @param model e.g. "gpt-4o" or "gpt-4o-mini"
 * @param baseUrl Override for compatible endpoints (e.g. Azure, local proxy)
 * @param apiKey API key override (falls back to OPENAI_API_KEY env var)
 */
class OpenAiGateway(
    private val model: String = "gpt-4o-mini",
    private val baseUrl: String = "https://api.openai.com/v1",
    private val apiKey: String = System.getenv("OPENAI_API_KEY") ?: "",
    private val timeoutSeconds: Long = 60,
) : LlmGateway {

    private val log = LoggerFactory.getLogger(OpenAiGateway::class.java)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val json = Json { ignoreUnknownKeys = true }

    init {
        require(apiKey.isNotBlank()) {
            "OpenAI API key is required. Set OPENAI_API_KEY environment variable."
        }
    }

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
            put("messages", buildMessagesArray(allMessages))
            if (tools.isNotEmpty()) put("tools", buildToolsArray(tools))
        }

        log.debug("OpenAI request: model={} messages={} tools={}", model, allMessages.size, tools.size)

        val request = HttpRequest.newBuilder(URI.create("$baseUrl/chat/completions"))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(body)))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            log.error("OpenAI error {}: {}", response.statusCode(), response.body())
            return LlmResponse(
                content = "API error ${response.statusCode()}: ${response.body().take(200)}",
                stopReason = LlmResponse.StopReason.ERROR,
            )
        }

        return parseResponse(response.body())
    }

    private fun parseResponse(body: String): LlmResponse {
        val root = json.parseToJsonElement(body).jsonObject
        val usage = root["usage"]?.jsonObject
        val inputTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val outputTokens = usage?.get("completion_tokens")?.jsonPrimitive?.content?.toIntOrNull() ?: 0

        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: return LlmResponse(content = null, stopReason = LlmResponse.StopReason.ERROR)

        val finishReason = choice["finish_reason"]?.jsonPrimitive?.content
        val message = choice["message"]?.jsonObject ?: return LlmResponse(content = null, stopReason = LlmResponse.StopReason.ERROR)

        val content = message["content"]?.jsonPrimitive?.content

        val toolCalls = message["tool_calls"]?.jsonArray?.map { tc ->
            val tcObj = tc.jsonObject
            val fn = tcObj["function"]?.jsonObject
            LlmToolCall(
                id = tcObj["id"]?.jsonPrimitive?.content ?: "",
                toolName = fn?.get("name")?.jsonPrimitive?.content ?: "",
                argumentsJson = fn?.get("arguments")?.jsonPrimitive?.content ?: "{}",
            )
        } ?: emptyList()

        val stopReason = when {
            toolCalls.isNotEmpty() -> LlmResponse.StopReason.TOOL_USE
            finishReason == "length" -> LlmResponse.StopReason.MAX_TOKENS
            else -> LlmResponse.StopReason.END_TURN
        }

        log.debug("OpenAI response: stopReason={} toolCalls={} inputTokens={} outputTokens={}",
            stopReason, toolCalls.size, inputTokens, outputTokens)

        return LlmResponse(
            content = content,
            toolCalls = toolCalls,
            stopReason = stopReason,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
        )
    }

    private fun buildMessagesArray(messages: List<LlmMessage>): JsonArray = buildJsonArray {
        for (msg in messages) {
            add(buildJsonObject {
                put("role", JsonPrimitive(msg.role.name))
                if (msg.toolCallId != null) put("tool_call_id", JsonPrimitive(msg.toolCallId))
                if (msg.content != null) put("content", JsonPrimitive(msg.content))
                if (msg.toolCalls.isNotEmpty()) {
                    put("tool_calls", buildJsonArray {
                        for (tc in msg.toolCalls) {
                            add(buildJsonObject {
                                put("id", JsonPrimitive(tc.id))
                                put("type", JsonPrimitive("function"))
                                put("function", buildJsonObject {
                                    put("name", JsonPrimitive(tc.toolName))
                                    put("arguments", JsonPrimitive(tc.argumentsJson))
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
