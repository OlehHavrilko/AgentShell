package dev.agentshell.llm.provider

import dev.agentshell.llm.LlmMessage
import dev.agentshell.llm.provider.impl.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import java.util.concurrent.TimeUnit

class ProviderHttpTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @BeforeEach fun setup() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()
    }

    @AfterEach fun teardown() = server.shutdown()

    private val baseUrl get() = server.url("/").toString().trimEnd('/')

    private val openAiResponse = """
        {"choices":[{"message":{"role":"assistant","content":"Hello!"},"finish_reason":"stop"}],
         "usage":{"prompt_tokens":10,"completion_tokens":5}}
    """.trimIndent()

    private val ollamaResponse = """
        {"message":{"role":"assistant","content":"Hi there!"},"done":true,
         "prompt_eval_count":8,"eval_count":4}
    """.trimIndent()

    private val req = CompletionRequest(
        messages = listOf(LlmMessage(LlmMessage.Role.user, "hello")),
        systemPrompt = "You are helpful"
    )

    @Test fun `OpenAiProvider parses response correctly`() = runBlocking {
        server.enqueue(MockResponse().setBody(openAiResponse).setResponseCode(200))
        val cfg = ProviderYaml(id = "openai", enabled = true, apiKey = "sk-test", model = "gpt-4o-mini", endpoint = baseUrl)
        val provider = OpenAiProvider(client, cfg)
        val resp = provider.complete(req)
        assertEquals("Hello!", resp.content)
        assertEquals("openai", resp.providerId)
        assertEquals(10, resp.inputTokens)
    }

    @Test fun `OllamaProvider parses response correctly`() = runBlocking {
        server.enqueue(MockResponse().setBody(ollamaResponse).setResponseCode(200))
        val cfg = ProviderYaml(id = "ollama", enabled = true, model = "llama3.2", host = baseUrl)
        val provider = OllamaProvider(client, cfg)
        val resp = provider.complete(req)
        assertEquals("Hi there!", resp.content)
        assertEquals("ollama", resp.providerId)
        assertEquals(8, resp.inputTokens)
        assertTrue(provider.isLocal)
    }

    @Test fun `AnthropicProvider config is correct`() {
        val cfg = ProviderYaml(id = "anthropic", enabled = true, apiKey = "sk-ant-test", model = "claude-3-5-sonnet-20241022")
        assertEquals("anthropic", cfg.id)
        assertEquals("claude-3-5-sonnet-20241022", cfg.model)
    }

    @Test fun `GroqProvider has correct id and displayName`() {
        val cfg = ProviderYaml(id = "groq", enabled = true, apiKey = "gsk_test", model = "llama-3.3-70b-versatile")
        val provider = GroqProvider(client, cfg)
        assertEquals("groq", provider.id)
        assertEquals("Groq (Ultra-fast)", provider.displayName)
    }

    @Test fun `LlamaCppProvider isLocal is true`() {
        val cfg = ProviderYaml(id = "llama_cpp", modelPath = "/models/test.gguf", threads = 4)
        val provider = LlamaCppProvider(cfg)
        assertTrue(provider.isLocal)
        assertEquals("llama_cpp", provider.id)
    }

    @Test fun `MistralProvider has correct id`() {
        val cfg = ProviderYaml(id = "mistral", apiKey = "test", model = "mistral-large-latest")
        val provider = MistralProvider(client, cfg)
        assertEquals("mistral", provider.id)
        assertEquals("Mistral AI", provider.displayName)
    }

    @Test fun `DeepSeekProvider has correct id`() {
        val cfg = ProviderYaml(id = "deepseek", apiKey = "test", model = "deepseek-chat")
        val provider = DeepSeekProvider(client, cfg)
        assertEquals("deepseek", provider.id)
    }

    @Test fun `provider returns LlmException on HTTP error`() {
        server.enqueue(MockResponse().setBody("""{"error":"rate limit"}""").setResponseCode(429))
        val cfg = ProviderYaml(id = "openai", enabled = true, apiKey = "sk-test", model = "gpt-4o-mini", endpoint = baseUrl)
        val provider = OpenAiProvider(client, cfg)
        assertThrows(LlmException::class.java) { runBlocking { provider.complete(req) } }
    }
}
