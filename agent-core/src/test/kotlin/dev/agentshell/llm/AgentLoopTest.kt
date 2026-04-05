package dev.agentshell.llm

import dev.agentshell.audit.AuditTrail
import dev.agentshell.domain.RunStatus
import dev.agentshell.runtime.AgentRunner
import dev.agentshell.state.InMemoryStateStore
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A fake LlmGateway that plays back a scripted sequence of responses. */
class FakeLlmGateway(private vararg val responses: LlmResponse) : LlmGateway {
    var callCount = 0
    val capturedMessages = mutableListOf<List<LlmMessage>>()
    val capturedTools = mutableListOf<List<LlmTool>>()

    override fun complete(messages: List<LlmMessage>, tools: List<LlmTool>, systemPrompt: String?): LlmResponse {
        capturedMessages.add(messages.toList())
        capturedTools.add(tools.toList())
        val resp = responses.getOrNull(callCount) ?: LlmResponse(content = "done", stopReason = LlmResponse.StopReason.END_TURN)
        callCount++
        return resp
    }
}

class AgentLoopTest {

    private fun makeRunner(gateway: LlmGateway): AgentRunner {
        val store = InMemoryStateStore()
        return AgentRunner(stateStore = store, gateway = gateway)
    }

    @Test
    fun `END_TURN on first response — completes immediately`() {
        val gateway = FakeLlmGateway(
            LlmResponse(content = "Task complete!", stopReason = LlmResponse.StopReason.END_TURN)
        )
        val runner = makeRunner(gateway)
        val config = AgentRunner.AgenticConfig(agentId = "test-agent", goal = "Say hello")

        val outcome = runner.executeAgentic(config)

        assertEquals(RunStatus.COMPLETED, outcome.status)
        assertEquals("Task complete!", outcome.finalMessage)
        assertEquals(0, outcome.results.size)
        assertEquals(1, gateway.callCount)
    }

    @Test
    fun `tool call executed then END_TURN — one tool result`() {
        val gateway = FakeLlmGateway(
            LlmResponse(
                content = null,
                toolCalls = listOf(LlmToolCall("tc1", "shell_exec", """{"command":"echo hello"}""")),
                stopReason = LlmResponse.StopReason.TOOL_USE,
            ),
            LlmResponse(content = "Done. Output was: hello", stopReason = LlmResponse.StopReason.END_TURN),
        )
        val runner = makeRunner(gateway)
        val config = AgentRunner.AgenticConfig(agentId = "test-agent", goal = "Run echo hello")

        val outcome = runner.executeAgentic(config)

        assertEquals(RunStatus.COMPLETED, outcome.status)
        assertEquals(1, outcome.results.size)
        assertTrue(outcome.results[0].success)
        assertEquals(2, gateway.callCount)
    }

    @Test
    fun `tool result is appended to next LLM call`() {
        val gateway = FakeLlmGateway(
            LlmResponse(
                content = null,
                toolCalls = listOf(LlmToolCall("tc2", "shell_exec", """{"command":"date"}""")),
                stopReason = LlmResponse.StopReason.TOOL_USE,
            ),
            LlmResponse(content = "Got the date", stopReason = LlmResponse.StopReason.END_TURN),
        )
        val runner = makeRunner(gateway)
        val config = AgentRunner.AgenticConfig(agentId = "a", goal = "What is the date?")

        runner.executeAgentic(config)

        // Second call should have: user message + assistant (with tool calls) + tool result
        val secondCallMessages = gateway.capturedMessages[1]
        assertTrue(secondCallMessages.size >= 3, "Expected at least 3 messages in second call, got ${secondCallMessages.size}")
        assertTrue(secondCallMessages.any { it.role == LlmMessage.Role.tool }, "Expected tool role message")
    }

    @Test
    fun `exceeds maxIterations — returns FAILED`() {
        val infiniteGateway = FakeLlmGateway(
            *Array(10) {
                LlmResponse(
                    content = null,
                    toolCalls = listOf(LlmToolCall("tc$it", "shell_exec", """{"command":"true"}""")),
                    stopReason = LlmResponse.StopReason.TOOL_USE,
                )
            }
        )
        val runner = makeRunner(infiniteGateway)
        val config = AgentRunner.AgenticConfig(agentId = "a", goal = "Loop forever", maxIterations = 3)

        val outcome = runner.executeAgentic(config)

        assertEquals(RunStatus.FAILED, outcome.status)
    }

    @Test
    fun `tool failure does not crash loop — LLM sees error and continues`() {
        val gateway = FakeLlmGateway(
            LlmResponse(
                content = null,
                toolCalls = listOf(LlmToolCall("tc-bad", "shell_exec", """{"command":"cat /nonexistent/file"}""")),
                stopReason = LlmResponse.StopReason.TOOL_USE,
            ),
            LlmResponse(content = "The file doesn't exist, task done", stopReason = LlmResponse.StopReason.END_TURN),
        )
        val runner = makeRunner(gateway)
        val config = AgentRunner.AgenticConfig(agentId = "a", goal = "Read missing file")

        val outcome = runner.executeAgentic(config)

        // Even if tool fails, loop continues and LLM ends the run
        assertEquals(RunStatus.COMPLETED, outcome.status)
        assertEquals(2, gateway.callCount)
    }

    @Test
    fun `LLM receives available tools list`() {
        val gateway = FakeLlmGateway(
            LlmResponse(content = "ok", stopReason = LlmResponse.StopReason.END_TURN)
        )
        val runner = makeRunner(gateway)
        val config = AgentRunner.AgenticConfig(agentId = "a", goal = "Check tools")

        runner.executeAgentic(config)

        val tools = gateway.capturedTools[0]
        assertTrue(tools.isNotEmpty(), "Expected tools to be passed to LLM")
        assertTrue(tools.any { it.name == "shell_exec" }, "Expected shell_exec in tools")
    }

    @Test
    fun `gateway not set — throws meaningful error`() {
        val store = InMemoryStateStore()
        val runner = AgentRunner(stateStore = store) // no gateway

        val ex = runCatching {
            runner.executeAgentic(AgentRunner.AgenticConfig(agentId = "a", goal = "test"))
        }.exceptionOrNull()

        assertNotNull(ex)
        assertTrue(ex!!.message?.contains("LlmGateway") == true)
    }
}
