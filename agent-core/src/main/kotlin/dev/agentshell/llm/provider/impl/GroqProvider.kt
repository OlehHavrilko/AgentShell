package dev.agentshell.llm.provider.impl

import dev.agentshell.llm.provider.CompletionRequest
import dev.agentshell.llm.provider.CompletionResponse
import dev.agentshell.llm.provider.ProviderYaml
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient

class GroqProvider(http: OkHttpClient, private val cfg: ProviderYaml) : BaseHttpProvider(http) {
    override val id = "groq"
    override val displayName = "Groq (Ultra-fast)"
    private val apiKey get() = cfg.apiKey?.ifBlank { null } ?: System.getenv("GROQ_API_KEY") ?: ""
    private val model get() = cfg.model ?: "llama-3.3-70b-versatile"

    override suspend fun complete(request: CompletionRequest): CompletionResponse {
        val body = buildJsonObject {
            put("model", request.modelOverride ?: model)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature)
            put("messages", buildMessagesArray(request.messages, request.systemPrompt))
            if (request.tools.isNotEmpty()) put("tools", buildToolsArray(request.tools))
        }
        val resp = post("https://api.groq.com/openai/v1/chat/completions", body, mapOf("Authorization" to "Bearer $apiKey"))
        return parseOpenAiResponse(resp, id, model)
    }
}
