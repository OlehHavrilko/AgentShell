package dev.agentshell.llm.provider

import dev.agentshell.llm.LlmMessage
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MultiProviderRouterTest {

    @AfterEach fun cleanup() = LlmProviderRegistry.clear()

    private val req = CompletionRequest(messages = listOf(LlmMessage(LlmMessage.Role.user, "hi")))

    @Test fun `returns first successful provider`() = runBlocking {
        LlmProviderRegistry.register(successProvider("a", "from-a"))
        LlmProviderRegistry.register(successProvider("b", "from-b"))
        val router = MultiProviderRouter(fallbackOrder = listOf("a", "b"))
        val resp = router.complete(req)
        assertEquals("from-a", resp.content)
    }

    @Test fun `falls back on error`() = runBlocking {
        LlmProviderRegistry.register(failingProvider("x"))
        LlmProviderRegistry.register(successProvider("y", "from-y"))
        val router = MultiProviderRouter(fallbackOrder = listOf("x", "y"))
        val resp = router.complete(req)
        assertEquals("from-y", resp.content)
    }

    @Test fun `throws when all fail`() {
        LlmProviderRegistry.register(failingProvider("p1"))
        LlmProviderRegistry.register(failingProvider("p2"))
        val router = MultiProviderRouter(fallbackOrder = listOf("p1", "p2"))
        assertThrows(LlmException::class.java) { runBlocking { router.complete(req) } }
    }

    @Test fun `skips unregistered provider ids`() = runBlocking {
        LlmProviderRegistry.register(successProvider("real", "ok"))
        val router = MultiProviderRouter(fallbackOrder = listOf("ghost", "real"))
        val resp = router.complete(req)
        assertEquals("ok", resp.content)
    }

    private fun successProvider(id: String, content: String) = object : LlmProvider {
        override val id = id
        override val displayName = id
        override suspend fun complete(r: CompletionRequest) = CompletionResponse(content = content, providerId = id, modelUsed = "m")
    }

    private fun failingProvider(id: String) = object : LlmProvider {
        override val id = id
        override val displayName = id
        override suspend fun complete(r: CompletionRequest): CompletionResponse = throw LlmException("$id failed")
    }
}
