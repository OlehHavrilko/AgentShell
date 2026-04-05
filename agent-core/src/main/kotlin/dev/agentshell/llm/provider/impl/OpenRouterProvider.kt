package dev.agentshell.llm.provider.impl

import dev.agentshell.llm.provider.CompletionRequest
import dev.agentshell.llm.provider.CompletionResponse
import dev.agentshell.llm.provider.ProviderYaml
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient

/** OpenRouter: OpenAI-compatible endpoint that routes to 100s of models. */
class OpenRouterProvider(http: OkHttpClient, private val cfg: ProviderYaml) : BaseHttpProvider(http) {
    override val id = "openrouter"
    override val displayName = "OpenRouter"
    private val apiKey get() = cfg.apiKey?.ifBlank { null } ?: System.getenv("OPENROUTER_API_KEY") ?: ""
    private val model get() = cfg.model ?: "anthropic/claude-3.5-sonnet"
    private val appName get() = cfg.appName ?: System.getenv("OPENROUTER_APP_NAME") ?: "AgentShell"

    override suspend fun complete(request: CompletionRequest): CompletionResponse {
        val body = buildJsonObject {
            put("model", request.modelOverride ?: model)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature)
            put("messages", buildMessagesArray(request.messages, request.systemPrompt))
            if (request.tools.isNotEmpty()) put("tools", buildToolsArray(request.tools))
        }
        val resp = post(
            "https://openrouter.ai/api/v1/chat/completions", body, mapOf(
                "Authorization" to "Bearer $apiKey",
                "HTTP-Referer" to "https://github.com/agentshell",
                "X-Title" to appName,
            )
        )
        return parseOpenAiResponse(resp, id, model)
    }
}
