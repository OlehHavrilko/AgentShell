package dev.agentshell.memory

import dev.agentshell.audit.AuditEvent
import dev.agentshell.audit.AuditEventType
import dev.agentshell.audit.AuditTrail
import dev.agentshell.domain.Run
import dev.agentshell.domain.RunStatus
import dev.agentshell.state.StateStore
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemoryIndexerTest {

    private fun makeRun(runId: String, agentId: String = "agent-1") = Run(
        runId = runId,
        agentId = agentId,
        status = RunStatus.COMPLETED,
        heartbeatMs = System.currentTimeMillis()
    )

    @Test
    fun `indexRun stores entries from STEP_COMPLETED events`() {
        val runId = UUID.randomUUID().toString()
        val run = makeRun(runId)

        val stateStore = mockk<StateStore>()
        every { stateStore.getRun(runId) } returns run

        val auditTrail = mockk<AuditTrail>()
        every { auditTrail.queryByRun(runId) } returns listOf(
            AuditEvent(AuditEventType.STEP_COMPLETED, runId, stepId = "s1", toolName = "shell_exec", argsJson = "{\"cmd\":\"ls\"}", resultJson = "file.kt"),
            AuditEvent(AuditEventType.STEP_COMPLETED, runId, stepId = "s2", toolName = "file_read", argsJson = "{\"path\":\"README.md\"}", resultJson = "# Project")
        )

        val memoryStore = InMemoryMemoryStore()
        val indexer = MemoryIndexer(memoryStore, auditTrail, stateStore)
        indexer.indexRun(runId)

        // 2 step entries + 1 run summary
        val stored = memoryStore.getByRunId(runId)
        assertEquals(3, stored.size)
    }

    @Test
    fun `indexRun skips unknown runId`() {
        val stateStore = mockk<StateStore>()
        every { stateStore.getRun(any()) } returns null

        val auditTrail = mockk<AuditTrail>()
        val memoryStore = InMemoryMemoryStore()
        val indexer = MemoryIndexer(memoryStore, auditTrail, stateStore)

        indexer.indexRun("nonexistent-run")
        assertTrue(memoryStore.search("anything").isEmpty())
    }

    @Test
    fun `indexed entries are searchable`() {
        val runId = UUID.randomUUID().toString()
        val stateStore = mockk<StateStore>()
        every { stateStore.getRun(runId) } returns makeRun(runId)

        val auditTrail = mockk<AuditTrail>()
        every { auditTrail.queryByRun(runId) } returns listOf(
            AuditEvent(AuditEventType.STEP_COMPLETED, runId, toolName = "shell_exec", argsJson = "{\"cmd\":\"gradle test\"}", resultJson = "BUILD SUCCESS 42 tests")
        )

        val memoryStore = InMemoryMemoryStore()
        MemoryIndexer(memoryStore, auditTrail, stateStore).indexRun(runId)

        val results = memoryStore.search("gradle test build")
        assertTrue(results.isNotEmpty())
    }
}
