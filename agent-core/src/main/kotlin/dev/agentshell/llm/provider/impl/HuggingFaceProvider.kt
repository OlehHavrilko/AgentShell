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
    private val url get() = "https://api-inference.huggingface.co/models/$model"

    override suspend fun complete(request: CompletionRequest): CompletionResponse {
        // HF Inference API: {"inputs": "<text>", "parameters": {...}}
        val lastMessage = request.messages.lastOrNull()?.content ?: ""
        val body = buildJsonObject {
            put("inputs", lastMessage)
            put("parameters", buildJsonObject {
                put("max_new_tokens", request.maxTokens)
                put("temperature", request.temperature)
                put("return_full_text", false)
            })
        }
        val resp = post(url, body, mapOf("Authorization" to "Bearer $apiKey"))
        return parseHuggingFaceResponse(resp)
    }

    private fun parseHuggingFaceResponse(body: String): CompletionResponse {
        // Response is a JSON array: [{"generated_text": "..."}]
        val root = json.parseToJsonElement(body)
        val text = when {
            root is JsonArray -> root.firstOrNull()?.jsonObject
                ?.get("generated_text")?.jsonPrimitive?.contentOrNull
            root is JsonObject -> root["generated_text"]?.jsonPrimitive?.contentOrNull
            else -> null
        }
        return CompletionResponse(content = text, providerId = id, modelUsed = model)
    }
}
