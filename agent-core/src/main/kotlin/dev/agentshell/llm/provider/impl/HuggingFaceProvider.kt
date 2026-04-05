package dev.agentshell.llm.provider.impl

import dev.agentshell.llm.provider.CompletionRequest
import dev.agentshell.llm.provider.CompletionResponse
import dev.agentshell.llm.provider.ProviderYaml
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient

class HuggingFaceProvider(http: OkHttpClient, private val cfg: ProviderYaml) : BaseHttpProvider(http) {
    override val id = "huggingface"
    override val displayName = "Hugging Face Inference"
    private val apiKey get() = cfg.apiKey?.ifBlank { null } ?: System.getenv("HF_API_KEY") ?: ""
    private val model get() = cfg.model ?: "meta-llama/Meta-Llama-3.2-3B-Instruct"
    private val url get() = "https://api-inference.huggingface.co/models/$model/v1/chat/completions"

    override suspend fun complete(request: CompletionRequest): CompletionResponse {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature)
            put("messages", buildMessagesArray(request.messages, request.systemPrompt))
        }
        val resp = post(url, body, mapOf("Authorization" to "Bearer $apiKey"))
        return parseOpenAiResponse(resp, id, model)
    }
}
