package dev.agentshell.llm.provider.impl

import dev.agentshell.llm.provider.CompletionRequest
import dev.agentshell.llm.provider.CompletionResponse
import dev.agentshell.llm.provider.ProviderYaml
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient

class CohereProvider(http: OkHttpClient, private val cfg: ProviderYaml) : BaseHttpProvider(http) {
    override val id = "cohere"
    override val displayName = "Cohere"
    private val apiKey get() = cfg.apiKey?.ifBlank { null } ?: System.getenv("COHERE_API_KEY") ?: ""
    private val model get() = cfg.model ?: "command-r-plus"

    override suspend fun complete(request: CompletionRequest): CompletionResponse {
        val body = buildJsonObject {
            put("model", request.modelOverride ?: model)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature)
            put("messages", buildMessagesArray(request.messages, request.systemPrompt))
        }
        val resp = post("https://api.cohere.com/v2/chat", body, mapOf("Authorization" to "Bearer $apiKey"))
        return parseCohereResponse(resp)
    }

    private fun parseCohereResponse(body: String): CompletionResponse {
        return try {
            parseOpenAiResponse(body, id, model)
        } catch (e: Exception) {
            val root = json.parseToJsonElement(body).jsonObject
            val text = root["text"]?.jsonPrimitive?.content
            CompletionResponse(content = text, providerId = id, modelUsed = model)
        }
    }
}
