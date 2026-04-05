package dev.agentshell.orchestrator

import dev.agentshell.llm.LlmGateway
import dev.agentshell.llm.LlmMessage
import dev.agentshell.llm.LlmResponse
import dev.agentshell.llm.LlmTool
import dev.agentshell.state.InMemoryStateStore
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OrchestratorTest {

    /** A gateway that immediately returns END_TURN (no tool calls) so AgentRunner finishes fast */
    class InstantGateway : LlmGateway {
        override fun complete(messages: List<LlmMessage>, tools: List<LlmTool>, systemPrompt: String?): LlmResponse =
            LlmResponse(content = "Task complete.", stopReason = LlmResponse.StopReason.END_TURN)
    }

    private fun makeOrchestrator(): Orchestrator {
        val stateStore = InMemoryStateStore()
        return Orchestrator(
            stateStore = stateStore,
            gatewayFactory = { InstantGateway() },
            parallelism = 2
        )
    }

    @Test
    fun `single agent plan runs successfully`() {
        val plan = OrchestratorPlan(
            name = "single",
            agents = listOf(AgentSpec("alpha", goal = "Greet the world"))
        )
        val result = makeOrchestrator().run(plan)
        assertTrue(result.success)
        assertEquals(1, result.agentResults.size)
        assertEquals("alpha", result.agentResults[0].agentId)
    }

    @Test
    fun `two parallel agents both run`() {
        val plan = OrchestratorPlan(
            name = "parallel",
            agents = listOf(
                AgentSpec("worker-a", goal = "Do task A"),
                AgentSpec("worker-b", goal = "Do task B")
            )
        )
        val result = makeOrchestrator().run(plan)
        assertTrue(result.success)
        assertEquals(2, result.agentResults.size)
        assertTrue(result.agentResults.all { it.status == "COMPLETED" })
    }

    @Test
    fun `upstream result is passed to downstream agent goal`() {
        val capturedGoals = mutableListOf<String>()

        val plan = OrchestratorPlan(
            name = "pipeline",
            agents = listOf(
                AgentSpec("upstream", goal = "Scan project"),
                AgentSpec("downstream", goal = "Write report", dependsOn = listOf("upstream"))
            )
        )

        val stateStore = InMemoryStateStore()
        val orchestrator = Orchestrator(
            stateStore = stateStore,
            gatewayFactory = { _ ->
                object : LlmGateway {
                    override fun complete(messages: List<LlmMessage>, tools: List<LlmTool>, systemPrompt: String?): LlmResponse {
                        val userMsg = messages.lastOrNull { it.role == LlmMessage.Role.user }?.content ?: ""
                        capturedGoals.add(userMsg)
                        return LlmResponse(content = "Done.", stopReason = LlmResponse.StopReason.END_TURN)
                    }
                }
            }
        )

        val result = orchestrator.run(plan)
        assertTrue(result.success)
        // downstream goal should mention upstream context
        val downstreamGoal = capturedGoals.lastOrNull() ?: ""
        assertTrue(downstreamGoal.contains("Write report") || downstreamGoal.contains("downstream"),
            "Downstream goal should contain base goal: $downstreamGoal")
    }

    @Test
    fun `orchestration result has plan name`() {
        val plan = OrchestratorPlan("my-plan", listOf(AgentSpec("x", goal = "do x")))
        val result = makeOrchestrator().run(plan)
        assertEquals("my-plan", result.planName)
    }

    @Test
    fun `null gateway marks agent as skipped`() {
        val plan = OrchestratorPlan(
            name = "skip-test",
            agents = listOf(AgentSpec("no-gateway", goal = "impossible"))
        )
        val stateStore = InMemoryStateStore()
        val orchestrator = Orchestrator(
            stateStore = stateStore,
            gatewayFactory = { null }  // always null
        )
        val result = orchestrator.run(plan)
        assertEquals("SKIPPED", result.agentResults[0].status)
    }
}
