package dev.agentshell.llm.provider.impl

import dev.agentshell.llm.LlmMessage
import dev.agentshell.llm.LlmResponse
import dev.agentshell.llm.provider.CompletionRequest
import dev.agentshell.llm.provider.CompletionResponse
import dev.agentshell.llm.provider.ProviderYaml
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient

class AnthropicProvider(http: OkHttpClient, private val cfg: ProviderYaml) : BaseHttpProvider(http) {
    override val id = "anthropic"
    override val displayName = "Anthropic (Claude)"
    private val apiKey get() = cfg.apiKey?.ifBlank { null } ?: System.getenv("ANTHROPIC_API_KEY") ?: ""
    private val model get() = cfg.model ?: "claude-3-5-sonnet-20241022"

    override suspend fun complete(request: CompletionRequest): CompletionResponse {
        val userMessages = request.messages.filter { it.role != LlmMessage.Role.system }
        val sysPrompt = request.systemPrompt
            ?: request.messages.firstOrNull { it.role == LlmMessage.Role.system }?.content

        val body = buildJsonObject {
            put("model", request.modelOverride ?: model)
            put("max_tokens", request.maxTokens)
            sysPrompt?.let { put("system", it) }
            put("messages", buildJsonArray {
                userMessages.forEach { msg ->
                    add(buildJsonObject {
                        put("role", if (msg.role == LlmMessage.Role.assistant) "assistant" else "user")
                        put("content", msg.content ?: "")
                    })
                }
            })
        }
        val resp = post(
            "https://api.anthropic.com/v1/messages", body, mapOf(
                "x-api-key" to apiKey,
                "anthropic-version" to "2023-06-01",
            )
        )
        return parseAnthropicResponse(resp)
    }

    private fun parseAnthropicResponse(body: String): CompletionResponse {
        val root = json.parseToJsonElement(body).jsonObject
        val content = root["content"]?.jsonArray
            ?.firstOrNull { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
            ?.jsonObject?.get("text")?.jsonPrimitive?.content
        val usage = root["usage"]?.jsonObject
        val stopReason = when (root["stop_reason"]?.jsonPrimitive?.content) {
            "end_turn" -> LlmResponse.StopReason.END_TURN
            "max_tokens" -> LlmResponse.StopReason.MAX_TOKENS
            else -> LlmResponse.StopReason.END_TURN
        }
        return CompletionResponse(
            content = content,
            stopReason = stopReason,
            inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.intOrNull ?: 0,
            outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.intOrNull ?: 0,
            providerId = id,
            modelUsed = model,
        )
    }
}
