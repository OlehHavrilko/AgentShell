package dev.agentshell.llm.config

data class AnthropicConfig(
    val apiKey: String,
    val model: String = "claude-3-5-sonnet-20240620",
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
)

data class AzureOpenAiConfig(
    val endpoint: String,
    val apiKey: String,
    val deployment: String = "gpt-4o",
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
)

data class VertexAiConfig(
    val projectId: String,
    val location: String = "us-central1",
    val model: String = "gemini-1.5-flash",
    val maxTokens: Int = 8192,
    val temperature: Double = 0.7,
)

data class CohereConfig(
    val apiKey: String,
    val model: String = "command-r-plus",
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
)

data class MistralConfig(
    val apiKey: String,
    val model: String = "mistral-large-latest",
    val maxTokens: Int = 8192,
    val temperature: Double = 0.7,
)

data class GroqConfig(
    val apiKey: String,
    val model: String = "mixtral-8x7b-32768",
    val maxTokens: Int = 32768,
    val temperature: Double = 0.7,
)

data class DeepSeekConfig(
    val apiKey: String,
    val model: String = "deepseek-coder-v2",
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
)

data class HuggingFaceConfig(
    val apiKey: String,
    val model: String = "meta-llama/Meta-Llama-3.2-3B-Instruct",
    val maxTokens: Int = 4096,
    val temperature: Double = 0.7,
)

data class OllamaConfig(
    val host: String = "http://127.0.0.1:11434",
    val model: String = "llama3.2",
)

data class LlamaCppConfig(
    val binaryPath: String,
    val modelPath: String,
    val threads: Int = 4,
)
