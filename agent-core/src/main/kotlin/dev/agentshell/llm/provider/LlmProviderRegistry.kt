package dev.agentshell.llm.provider

import java.util.concurrent.ConcurrentHashMap

object LlmProviderRegistry {
    private val providers = ConcurrentHashMap<String, LlmProvider>()

    fun register(provider: LlmProvider) {
        providers[provider.id] = provider
    }

    fun get(id: String): LlmProvider? = providers[id]

    fun all(): List<LlmProvider> = providers.values.sortedBy { it.id }

    fun enabled(): List<LlmProvider> = all() // all registered = enabled

    fun clear() = providers.clear()
}
