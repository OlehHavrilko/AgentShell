package com.agentshell.app.llm

data class AndroidProviderDefinition(
    val id: String,
    val displayName: String,
    val isLocal: Boolean,
    val defaultModel: String,
    val defaultHost: String = "",
)

object AndroidProviderCatalog {
    val supported = listOf(
        AndroidProviderDefinition(
            id = "ollama",
            displayName = "Ollama (Local)",
            isLocal = true,
            defaultModel = "llama3.2",
            defaultHost = "http://10.0.2.2:11434",
        ),
        AndroidProviderDefinition(
            id = "openai",
            displayName = "OpenAI",
            isLocal = false,
            defaultModel = "gpt-4o-mini",
        ),
        AndroidProviderDefinition(
            id = "groq",
            displayName = "Groq",
            isLocal = false,
            defaultModel = "llama-3.3-70b-versatile",
        ),
        AndroidProviderDefinition(
            id = "deepseek",
            displayName = "DeepSeek",
            isLocal = false,
            defaultModel = "deepseek-chat",
        ),
        AndroidProviderDefinition(
            id = "mistral",
            displayName = "Mistral",
            isLocal = false,
            defaultModel = "mistral-large-latest",
        ),
        AndroidProviderDefinition(
            id = "openrouter",
            displayName = "OpenRouter",
            isLocal = false,
            defaultModel = "anthropic/claude-3.5-sonnet",
        ),
    )

    fun byId(id: String): AndroidProviderDefinition? = supported.find { it.id == id }
}
