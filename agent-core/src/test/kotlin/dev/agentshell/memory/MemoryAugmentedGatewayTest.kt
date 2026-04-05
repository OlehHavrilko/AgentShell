package dev.agentshell.memory

import dev.agentshell.llm.LlmGateway
import dev.agentshell.llm.LlmMessage
import dev.agentshell.llm.LlmResponse
import dev.agentshell.llm.LlmTool
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MemoryAugmentedGatewayTest {

    private val fakeResponse = LlmResponse(
        content = "done",
        stopReason = LlmResponse.StopReason.END_TURN
    )

    private fun makeGateway(memoryStore: MemoryStore): Pair<CapturingGateway, MemoryAugmentedGateway> {
        val capturing = CapturingGateway(fakeResponse)
        val augmented = MemoryAugmentedGateway(capturing, memoryStore, topK = 2)
        return capturing to augmented
    }

    @Test
    fun `passes through when memory is empty`() {
        val store = InMemoryMemoryStore()
        val (capturing, gw) = makeGateway(store)

        gw.complete(
            messages = listOf(LlmMessage(LlmMessage.Role.user, "do something")),
            tools = emptyList(),
            systemPrompt = "You are helpful"
        )

        assertNotNull(capturing.lastSystemPrompt)
        assertTrue(capturing.lastSystemPrompt!!.contains("You are helpful"))
        assertTrue(!capturing.lastSystemPrompt!!.contains("Memory"))
    }

    @Test
    fun `injects memory block when relevant entries exist`() {
        val store = InMemoryMemoryStore()
        store.store(MemoryEntry(UUID.randomUUID().toString(), "run-1", "agent-1",
            "agent executed shell command gradle test successfully"))

        val (capturing, gw) = makeGateway(store)

        gw.complete(
            messages = listOf(LlmMessage(LlmMessage.Role.user, "run gradle tests")),
            tools = emptyList(),
            systemPrompt = "You are an agent"
        )

        assertTrue(capturing.lastSystemPrompt!!.contains("past experiences"))
        assertTrue(capturing.lastSystemPrompt!!.contains("agent"))
    }

    @Test
    fun `creates system prompt when none provided`() {
        val store = InMemoryMemoryStore()
        store.store(MemoryEntry(UUID.randomUUID().toString(), "run-2", "bot",
            "file read operation returned project structure"))

        val (capturing, gw) = makeGateway(store)

        gw.complete(
            messages = listOf(LlmMessage(LlmMessage.Role.user, "read the project files")),
            tools = emptyList(),
            systemPrompt = null
        )

        assertNotNull(capturing.lastSystemPrompt)
        assertTrue(capturing.lastSystemPrompt!!.contains("Relevant past experiences"))
    }

    /** Records the systemPrompt passed to complete() */
    class CapturingGateway(private val response: LlmResponse) : LlmGateway {
        var lastSystemPrompt: String? = null

        override fun complete(messages: List<LlmMessage>, tools: List<LlmTool>, systemPrompt: String?): LlmResponse {
            lastSystemPrompt = systemPrompt
            return response
        }
    }
}
