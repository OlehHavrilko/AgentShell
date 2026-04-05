package dev.agentshell.llm.provider

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class ProviderYaml(
    val id: String = "",
    val enabled: Boolean = false,
    val apiKey: String? = null,
    val model: String? = null,
    val host: String? = null,
    val endpoint: String? = null,
    val deployment: String? = null,
    val projectId: String? = null,
    val location: String? = null,
    val modelPath: String? = null,
    val binaryPath: String? = null,
    val threads: Int = 4,
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
)

@Serializable
data class LlmProvidersConfig(
    val providers: List<ProviderYaml> = emptyList(),
)

object LlmProvidersLoader {
    private val log = LoggerFactory.getLogger(LlmProvidersLoader::class.java)

    fun load(resourcePath: String = "llm_providers.yaml"): LlmProvidersConfig {
        val stream = Thread.currentThread().contextClassLoader
            ?.getResourceAsStream(resourcePath)
            ?: LlmProvidersLoader::class.java.classLoader?.getResourceAsStream(resourcePath)
            ?: run {
                log.warn("llm_providers.yaml not found, using empty config")
                return LlmProvidersConfig()
            }
        return try {
            val text = stream.bufferedReader().readText()
                .replace(Regex("\\$\\{([^}]+)\\}")) { mr ->
                    System.getenv(mr.groupValues[1]) ?: ""
                }
            Yaml.default.decodeFromString(LlmProvidersConfig.serializer(), text)
        } catch (e: Exception) {
            log.error("Failed to load llm_providers.yaml: {}", e.message)
            LlmProvidersConfig()
        }
    }

    fun registerAll(config: LlmProvidersConfig) {
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        config.providers.filter { it.enabled }.forEach { p ->
            val provider: LlmProvider? = when (p.id) {
                "openai" -> dev.agentshell.llm.provider.impl.OpenAiProvider(okHttpClient, p)
                "anthropic" -> dev.agentshell.llm.provider.impl.AnthropicProvider(okHttpClient, p)
                "azure_openai" -> dev.agentshell.llm.provider.impl.AzureOpenAiProvider(okHttpClient, p)
                "vertex_ai" -> dev.agentshell.llm.provider.impl.VertexAiProvider(okHttpClient, p)
                "ollama" -> dev.agentshell.llm.provider.impl.OllamaProvider(okHttpClient, p)
                "huggingface" -> dev.agentshell.llm.provider.impl.HuggingFaceProvider(okHttpClient, p)
                "cohere" -> dev.agentshell.llm.provider.impl.CohereProvider(okHttpClient, p)
                "mistral" -> dev.agentshell.llm.provider.impl.MistralProvider(okHttpClient, p)
                "groq" -> dev.agentshell.llm.provider.impl.GroqProvider(okHttpClient, p)
                "deepseek" -> dev.agentshell.llm.provider.impl.DeepSeekProvider(okHttpClient, p)
                "llama_cpp" -> dev.agentshell.llm.provider.impl.LlamaCppProvider(p)
                else -> { log.warn("Unknown provider id: {}", p.id); null }
            }
            provider?.let { LlmProviderRegistry.register(it) }
        }
        log.info("Registered {} LLM providers", LlmProviderRegistry.all().size)
    }
}
