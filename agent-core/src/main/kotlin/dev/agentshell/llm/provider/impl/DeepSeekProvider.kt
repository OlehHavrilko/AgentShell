package dev.agentshell.llm.provider.impl

import dev.agentshell.llm.provider.CompletionRequest
import dev.agentshell.llm.provider.CompletionResponse
import dev.agentshell.llm.provider.ProviderYaml
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient

class DeepSeekProvider(http: OkHttpClient, private val cfg: ProviderYaml) : BaseHttpProvider(http) {
    override val id = "deepseek"
    override val displayName = "DeepSeek"
    private val apiKey get() = cfg.apiKey?.ifBlank { null } ?: System.getenv("DEEPSEEK_API_KEY") ?: ""
    private val model get() = cfg.model ?: "deepseek-chat"

    override suspend fun complete(request: CompletionRequest): CompletionResponse {
        val body = buildJsonObject {
            put("model", request.modelOverride ?: model)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature)
            put("messages", buildMessagesArray(request.messages, request.systemPrompt))
        }
        val resp = post("https://api.deepseek.com/v1/chat/completions", body, mapOf("Authorization" to "Bearer $apiKey"))
        return parseOpenAiResponse(resp, id, model)
    }
}
