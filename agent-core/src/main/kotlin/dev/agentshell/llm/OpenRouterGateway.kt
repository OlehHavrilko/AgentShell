package dev.agentshell.llm

import org.slf4j.LoggerFactory
import java.net.http.HttpClient
import java.time.Duration

/**
 * LlmGateway implementation for [OpenRouter](https://openrouter.ai).
 *
 * OpenRouter exposes an OpenAI-compatible `/chat/completions` endpoint that
 * routes to hundreds of models (Claude, Gemini, Llama, Mistral, …).
 *
 * Required env var: OPENROUTER_API_KEY
 * Optional env var: OPENROUTER_APP_NAME (shown in OpenRouter dashboard)
 *
 * Popular models:
 *   - "anthropic/claude-3-5-sonnet"
 *   - "google/gemini-2.0-flash-001"
 *   - "meta-llama/llama-3.3-70b-instruct"
 *   - "mistralai/mistral-large"
 *   - "openai/gpt-4o"
 *
 * @param model OpenRouter model identifier
 * @param apiKey API key (defaults to OPENROUTER_API_KEY env var)
 * @param appName Your app name sent in X-Title header (for dashboard attribution)
 * @param siteUrl Your site URL sent in HTTP-Referer header (optional)
 */
class OpenRouterGateway(
    private val model: String = "anthropic/claude-3-5-sonnet",
    private val apiKey: String = System.getenv("OPENROUTER_API_KEY") ?: "",
    private val appName: String = System.getenv("OPENROUTER_APP_NAME") ?: "AgentShell",
    private val siteUrl: String = "https://github.com/agentshell",
    private val timeoutSeconds: Long = 120,
) : LlmGateway {

    private val log = LoggerFactory.getLogger(OpenRouterGateway::class.java)

    // Reuse OpenAI adapter with OpenRouter base URL + extra headers
    private val delegate = OpenAiGateway(
        model = model,
        baseUrl = "https://openrouter.ai/api/v1",
        apiKey = apiKey.also {
            require(it.isNotBlank()) {
                "OpenRouter API key is required. Set OPENROUTER_API_KEY environment variable."
            }
        },
        timeoutSeconds = timeoutSeconds,
        extraHeaders = mapOf(
            "HTTP-Referer" to siteUrl,
            "X-Title" to appName,
        ),
    )

    override fun complete(
        messages: List<LlmMessage>,
        tools: List<LlmTool>,
        systemPrompt: String?,
    ): LlmResponse {
        log.debug("OpenRouter request: model={} messages={}", model, messages.size)
        return delegate.complete(messages, tools, systemPrompt)
    }
}
