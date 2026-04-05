package dev.agentshell.report

import dev.agentshell.audit.AuditEvent
import dev.agentshell.audit.AuditEventType
import dev.agentshell.audit.AuditTrail
import dev.agentshell.domain.Run
import dev.agentshell.domain.RunStatus
import dev.agentshell.state.InMemoryStateStore
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RunReportGeneratorTest {

    private class InMemoryAuditTrail : AuditTrail {
        private val events = mutableListOf<AuditEvent>()
        override fun record(event: AuditEvent) { events.add(event) }
        override fun queryByRun(runId: String) = events.filter { it.runId == runId }
    }

    private fun makeRun(runId: String, agentId: String = "test-agent", status: RunStatus = RunStatus.COMPLETED) =
        Run(runId = runId, agentId = agentId, status = status, heartbeatMs = System.currentTimeMillis())

    @Test
    fun `generates report for completed run`() {
        val store = InMemoryStateStore()
        val trail = InMemoryAuditTrail()
        val gen = RunReportGenerator(store, trail)

        val run = makeRun("run-1")
        store.saveRun(run)

        val t = System.currentTimeMillis()
        trail.record(AuditEvent(AuditEventType.RUN_STARTED,   "run-1", timestampMs = t))
        trail.record(AuditEvent(AuditEventType.STEP_STARTED,  "run-1", stepId = "s1", toolName = "shell_exec", argsJson = "{}", timestampMs = t + 10))
        trail.record(AuditEvent(AuditEventType.STEP_COMPLETED,"run-1", stepId = "s1", toolName = "shell_exec", resultJson = """{"output":"ok"}""", timestampMs = t + 50))
        trail.record(AuditEvent(AuditEventType.RUN_COMPLETED, "run-1", timestampMs = t + 100))

        val report = gen.generate("run-1")

        assertEquals("run-1", report.runId)
        assertEquals("COMPLETED", report.status)
        assertEquals(1, report.stepCount)
        assertEquals(0, report.failedSteps)
        assertEquals(100L, report.durationMs)
        assertEquals("shell_exec", report.steps[0].toolName)
        assertEquals("COMPLETED", report.steps[0].status)
        assertEquals(40L, report.steps[0].durationMs)
    }

    @Test
    fun `failed step is counted`() {
        val store = InMemoryStateStore()
        val trail = InMemoryAuditTrail()
        val gen = RunReportGenerator(store, trail)

        store.saveRun(makeRun("run-2", status = RunStatus.FAILED))
        val t = System.currentTimeMillis()
        trail.record(AuditEvent(AuditEventType.RUN_STARTED,  "run-2", timestampMs = t))
        trail.record(AuditEvent(AuditEventType.STEP_STARTED, "run-2", stepId = "s1", toolName = "git_exec", timestampMs = t + 5))
        trail.record(AuditEvent(AuditEventType.STEP_FAILED,  "run-2", stepId = "s1", toolName = "git_exec", errorCode = "ERR_SANDBOX_KILLED", timestampMs = t + 20))
        trail.record(AuditEvent(AuditEventType.RUN_FAILED,   "run-2", timestampMs = t + 25))

        val report = gen.generate("run-2")
        assertEquals(1, report.failedSteps)
        assertEquals("FAILED", report.steps[0].status)
        assertEquals("ERR_SANDBOX_KILLED", report.steps[0].errorCode)
    }

    @Test
    fun `error when run not found`() {
        val store = InMemoryStateStore()
        val trail = InMemoryAuditTrail()
        val gen = RunReportGenerator(store, trail)

        val ex = runCatching { gen.generate("missing-run") }.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex!!.message!!.contains("missing-run"))
    }

    @Test
    fun `generateJson produces valid JSON`() {
        val store = InMemoryStateStore()
        val trail = InMemoryAuditTrail()
        val gen = RunReportGenerator(store, trail)
        store.saveRun(makeRun("run-3"))
        trail.record(AuditEvent(AuditEventType.RUN_STARTED, "run-3"))
        trail.record(AuditEvent(AuditEventType.RUN_COMPLETED, "run-3"))

        val json = gen.generateJson("run-3")
        assertTrue(json.contains("\"runId\""))
        assertTrue(json.contains("run-3"))
    }

    @Test
    fun `empty run has zero steps`() {
        val store = InMemoryStateStore()
        val trail = InMemoryAuditTrail()
        val gen = RunReportGenerator(store, trail)
        store.saveRun(makeRun("run-4"))
        val report = gen.generate("run-4")
        assertEquals(0, report.stepCount)
        assertTrue(report.steps.isEmpty())
    }
}
