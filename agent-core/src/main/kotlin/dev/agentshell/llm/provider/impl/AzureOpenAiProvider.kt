package dev.agentshell.llm.provider.impl

import dev.agentshell.llm.provider.CompletionRequest
import dev.agentshell.llm.provider.CompletionResponse
import dev.agentshell.llm.provider.ProviderYaml
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient

class AzureOpenAiProvider(http: OkHttpClient, private val cfg: ProviderYaml) : BaseHttpProvider(http) {
    override val id = "azure_openai"
    override val displayName = "Azure OpenAI"
    private val apiKey get() = cfg.apiKey?.ifBlank { null } ?: System.getenv("AZURE_OPENAI_KEY") ?: ""
    private val endpoint get() = cfg.endpoint?.trimEnd('/') ?: ""
    private val deployment get() = cfg.deployment ?: "gpt-4o"
    private val url get() = "$endpoint/openai/deployments/$deployment/chat/completions?api-version=2024-02-01"

    override suspend fun complete(request: CompletionRequest): CompletionResponse {
        val body = buildJsonObject {
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature)
            put("messages", buildMessagesArray(request.messages, request.systemPrompt))
            if (request.tools.isNotEmpty()) put("tools", buildToolsArray(request.tools))
        }
        val resp = post(url, body, mapOf("api-key" to apiKey))
        return parseOpenAiResponse(resp, id, deployment)
    }
}
