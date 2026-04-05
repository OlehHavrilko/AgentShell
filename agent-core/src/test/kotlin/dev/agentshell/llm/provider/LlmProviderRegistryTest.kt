package dev.agentshell.llm.provider

import dev.agentshell.llm.LlmMessage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LlmProviderRegistryTest {

    @AfterEach fun cleanup() = LlmProviderRegistry.clear()

    @Test fun `register and retrieve provider`() {
        val p = object : LlmProvider {
            override val id = "test"
            override val displayName = "Test"
            override suspend fun complete(request: CompletionRequest) =
                CompletionResponse(content = "ok", providerId = id, modelUsed = "test-model")
        }
        LlmProviderRegistry.register(p)
        assertNotNull(LlmProviderRegistry.get("test"))
        assertEquals(1, LlmProviderRegistry.all().size)
    }

    @Test fun `get unknown returns null`() {
        assertNull(LlmProviderRegistry.get("nonexistent"))
    }

    @Test fun `adapter bridges to LlmGateway`() {
        val p = object : LlmProvider {
            override val id = "bridge"
            override val displayName = "Bridge"
            override suspend fun complete(request: CompletionRequest) =
                CompletionResponse(content = "bridged", providerId = id, modelUsed = "m")
        }
        val gateway = LlmProviderAdapter(p)
        val response = gateway.complete(
            messages = listOf(LlmMessage(LlmMessage.Role.user, "hello")),
        )
        assertEquals("bridged", response.content)
    }
}
