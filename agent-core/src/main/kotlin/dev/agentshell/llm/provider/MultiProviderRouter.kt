package dev.agentshell.llm.provider

import org.slf4j.LoggerFactory

class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause)

class MultiProviderRouter(
    private val registry: LlmProviderRegistry = LlmProviderRegistry,
    val fallbackOrder: List<String> = listOf("ollama", "openai", "groq", "mistral"),
) {
    private val log = LoggerFactory.getLogger(MultiProviderRouter::class.java)

    suspend fun complete(request: CompletionRequest): CompletionResponse {
        val order = if (fallbackOrder.isEmpty()) registry.all().map { it.id } else fallbackOrder
        var lastError: Exception? = null
        for (id in order) {
            val provider = registry.get(id) ?: continue
            try {
                log.debug("Trying provider: {}", id)
                val resp = provider.complete(request)
                log.debug("Provider {} succeeded", id)
                return resp
            } catch (e: Exception) {
                log.warn("Provider {} failed: {}", id, e.message)
                lastError = e
            }
        }
        throw LlmException("All fallback providers failed. Last error: ${lastError?.message}", lastError)
    }
}
