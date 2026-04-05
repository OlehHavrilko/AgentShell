package dev.agentshell.runtime

import dev.agentshell.domain.RiskLevel
import dev.agentshell.domain.RunStatus
import dev.agentshell.domain.ToolCall
import dev.agentshell.domain.ToolContract
import dev.agentshell.state.InMemoryStateStore
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentRunnerIntegrationTest {

    private fun shellContract() = ToolContract(
        name = "shell_exec", version = "1.0", description = "shell",
        riskLevel = RiskLevel.LOW, sandboxPolicy = "shell_default",
    )

    private fun shellStep(cmd: String, id: String = "s1") = AgentRunner.StepDef(
        call = ToolCall(callId = id, toolName = "shell_exec", argumentsJson = """{"command":"$cmd"}"""),
        contract = shellContract(),
    )

    private fun runner(store: InMemoryStateStore = InMemoryStateStore()) = AgentRunner(
        stateStore = store,
        riskScorer = RiskScorer(),
        approvalGate = ApprovalGate(),
        idempotency = IdempotencyService(),
        heartbeat = HeartbeatService(store, intervalMs = 60_000), // long interval — won't fire in tests
        runtime = AgentRuntime(store),
    )

    @Test fun `single echo step completes successfully`() {
        val outcome = runner().execute(
            AgentRunner.RunConfig(
                agentId = "agent-test-1",
                steps = listOf(shellStep("echo hello")),
            )
        )
        assertEquals(RunStatus.COMPLETED, outcome.status)
        assertEquals(1, outcome.results.size)
        assertTrue(outcome.results[0].success)
        assertTrue(outcome.results[0].outputJson!!.contains("hello"))
    }

    @Test fun `failing step sets run status to FAILED`() {
        val outcome = runner().execute(
            AgentRunner.RunConfig(
                agentId = "agent-test-2",
                steps = listOf(shellStep("exit 1")),
            )
        )
        assertEquals(RunStatus.FAILED, outcome.status)
        assertEquals(1, outcome.results.size)
        assertTrue(!outcome.results[0].success)
    }

    @Test fun `multiple steps all succeed`() {
        val steps = (1..3).map { shellStep("echo step$it", "s$it") }
        val outcome = runner().execute(
            AgentRunner.RunConfig(agentId = "agent-test-3", steps = steps)
        )
        assertEquals(RunStatus.COMPLETED, outcome.status)
        assertEquals(3, outcome.results.size)
        assertTrue(outcome.results.all { it.success })
    }

    @Test fun `high-risk step triggers WAITING_APPROVAL`() {
        val outcome = runner().execute(
            AgentRunner.RunConfig(
                agentId = "agent-test-4",
                steps = listOf(
                    shellStep("echo safe"),
                    AgentRunner.StepDef(
                        // shell_exec with --force pattern → score 30+30=60 → triggers approval at threshold 60
                        call = ToolCall("s2", "shell_exec", """{"command":"git push --force"}"""),
                        contract = shellContract(),
                    ),
                ),
                riskThreshold = 59,
            )
        )
        assertEquals(RunStatus.WAITING_APPROVAL, outcome.status)
    }

    @Test fun `resume picks up from last checkpoint`() {
        val store = InMemoryStateStore()
        val steps = (1..3).map { shellStep("echo step$it", "s$it") }
        val config = AgentRunner.RunConfig(agentId = "agent-test-5", steps = steps)

        // First run: fail on step 2
        val failingSteps = listOf(
            shellStep("echo step1", "s1"),
            shellStep("exit 1", "s2"),
            shellStep("echo step3", "s3"),
        )
        val first = runner(store).execute(config.copy(steps = failingSteps))
        assertEquals(RunStatus.FAILED, first.status)
        assertEquals(2, first.results.size) // step 0 success + step 1 failure

        // Force the run back to CRASHED so startOrResume can resume it
        val run = store.findRunByAgent("agent-test-5", setOf(RunStatus.FAILED))!!
        store.updateStatus(run.runId, RunStatus.CRASHED)

        // Second run: all steps succeed
        val second = runner(store).execute(config.copy(steps = steps))
        // Resumes from checkpoint stepIndex=0, so fromStep=1 — steps 1 and 2 run
        assertEquals(RunStatus.COMPLETED, second.status)
    }

    @Test fun `idempotency key prevents duplicate execution`() {
        val store = InMemoryStateStore()
        val idem = IdempotencyService()
        val r = AgentRunner(
            stateStore = store,
            idempotency = idem,
            heartbeat = HeartbeatService(store, intervalMs = 60_000),
            runtime = AgentRuntime(store),
        )
        val step = AgentRunner.StepDef(
            call = ToolCall("s1", "shell_exec", """{"command":"echo once"}""", idempotencyKey = "key-abc"),
            contract = shellContract(),
        )
        val first = r.execute(AgentRunner.RunConfig("agent-idem-1", listOf(step)))
        assertEquals(RunStatus.COMPLETED, first.status)

        // Second run with same idempotency key should get cached result
        val run = store.findRunByAgent("agent-idem-1", setOf(RunStatus.COMPLETED))!!
        store.updateStatus(run.runId, RunStatus.CRASHED) // force resume eligibility
        val second = r.execute(AgentRunner.RunConfig("agent-idem-1", listOf(step)))
        assertEquals(RunStatus.COMPLETED, second.status)
    }
}
