package dev.agentshell.state

import dev.agentshell.domain.Checkpoint
import dev.agentshell.domain.Run
import dev.agentshell.domain.RunStatus
import dev.agentshell.state.sqlite.DatabaseManager
import dev.agentshell.state.sqlite.SqliteStateStore
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SqliteStateStoreTest {

    @TempDir
    lateinit var tmpDir: File

    private lateinit var db: DatabaseManager
    private lateinit var store: SqliteStateStore

    @BeforeEach
    fun setup() {
        db = DatabaseManager("${tmpDir.absolutePath}/test.db")
        store = SqliteStateStore(db)
    }

    @AfterEach
    fun teardown() {
        db.close()
    }

    private fun run(id: String, agentId: String = "agent1", status: RunStatus = RunStatus.RUNNING) = Run(
        runId = id,
        agentId = agentId,
        status = status,
        heartbeatMs = System.currentTimeMillis(),
    )

    @Test
    fun `saveRun and findRunByAgent returns the run`() {
        val r = run("r1")
        store.saveRun(r)
        val found = store.findRunByAgent("agent1", setOf(RunStatus.RUNNING))
        assertNotNull(found)
        assertEquals("r1", found.runId)
        assertEquals(RunStatus.RUNNING, found.status)
    }

    @Test
    fun `findRunByAgent with wrong status returns null`() {
        store.saveRun(run("r2", status = RunStatus.COMPLETED))
        assertNull(store.findRunByAgent("agent1", setOf(RunStatus.RUNNING)))
    }

    @Test
    fun `updateStatus persists new status`() {
        store.saveRun(run("r3"))
        val updated = store.updateStatus("r3", RunStatus.COMPLETED)
        assertEquals(RunStatus.COMPLETED, updated.status)
        assertEquals(RunStatus.COMPLETED, store.findRunByAgent("agent1", setOf(RunStatus.COMPLETED))!!.status)
    }

    @Test
    fun `updateHeartbeat persists new heartbeat`() {
        store.saveRun(run("r4"))
        val newTs = 99_999_999L
        val updated = store.updateHeartbeat("r4", newTs)
        assertEquals(newTs, updated.heartbeatMs)
    }

    @Test
    fun `updateCheckpoint persists checkpoint and retrieves it`() {
        store.saveRun(run("r5"))
        val cp = Checkpoint(stepIndex = 3, contextSummary = "ctx", artifactRefs = listOf("file1.txt", "file2.txt"))
        val updated = store.updateCheckpoint("r5", cp)
        val cp2 = updated.checkpoint
        assertNotNull(cp2)
        assertEquals(3, cp2.stepIndex)
        assertEquals("ctx", cp2.contextSummary)
        assertEquals(listOf("file1.txt", "file2.txt"), cp2.artifactRefs)
    }

    @Test
    fun `saveRun upsert updates existing run`() {
        val r = run("r6")
        store.saveRun(r)
        val updated = r.copy(status = RunStatus.FAILED)
        store.saveRun(updated)
        val found = store.findRunByAgent("agent1", setOf(RunStatus.FAILED))
        assertNotNull(found)
        assertEquals(RunStatus.FAILED, found.status)
    }

    @Test
    fun `findRunByAgent returns most recent by heartbeat`() {
        store.saveRun(run("r7a").copy(heartbeatMs = 1000L))
        store.saveRun(run("r7b", agentId = "agent1").copy(heartbeatMs = 9000L))
        val found = store.findRunByAgent("agent1", setOf(RunStatus.RUNNING))
        assertEquals("r7b", found?.runId)
    }

    @Test
    fun `run without checkpoint has null checkpoint`() {
        store.saveRun(run("r8"))
        val found = store.findRunByAgent("agent1", setOf(RunStatus.RUNNING))
        assertNull(found?.checkpoint)
    }

    @Test
    fun `migrations are idempotent across reconnects`() {
        db.close()
        val db2 = DatabaseManager("${tmpDir.absolutePath}/test.db")
        val store2 = SqliteStateStore(db2)
        store2.saveRun(run("r9"))
        assertNotNull(store2.findRunByAgent("agent1", setOf(RunStatus.RUNNING)))
        db2.close()
    }
}
