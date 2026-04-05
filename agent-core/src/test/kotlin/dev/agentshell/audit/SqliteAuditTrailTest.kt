package dev.agentshell.audit

import dev.agentshell.state.sqlite.DatabaseManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqliteAuditTrailTest {

    private lateinit var db: DatabaseManager
    private lateinit var trail: SqliteAuditTrail
    private lateinit var dbFile: File

    @BeforeEach
    fun setup() {
        dbFile = File.createTempFile("audit-test-", ".db").also { it.deleteOnExit() }
        db = DatabaseManager(dbFile.absolutePath)
        trail = SqliteAuditTrail(db)
    }

    @AfterEach
    fun teardown() {
        db.close()
    }

    @Test
    fun `record and query events by run`() {
        trail.record(AuditEvent(AuditEventType.RUN_STARTED, runId = "run-1"))
        trail.record(AuditEvent(AuditEventType.STEP_STARTED, runId = "run-1", stepId = "s1", toolName = "shell_exec"))
        trail.record(AuditEvent(AuditEventType.STEP_COMPLETED, runId = "run-1", stepId = "s1", toolName = "shell_exec"))
        trail.record(AuditEvent(AuditEventType.RUN_COMPLETED, runId = "run-1"))

        val events = trail.queryByRun("run-1")

        assertEquals(4, events.size)
        assertEquals(AuditEventType.RUN_STARTED, events[0].eventType)
        assertEquals(AuditEventType.RUN_COMPLETED, events[3].eventType)
    }

    @Test
    fun `events are ordered by timestamp`() {
        repeat(5) { i ->
            trail.record(AuditEvent(AuditEventType.STEP_STARTED, runId = "run-ts", stepId = "s$i", timestampMs = i.toLong()))
        }

        val events = trail.queryByRun("run-ts")
        val timestamps = events.map { it.timestampMs }
        assertEquals(timestamps.sorted(), timestamps)
    }

    @Test
    fun `query returns empty for unknown run`() {
        val events = trail.queryByRun("no-such-run")
        assertTrue(events.isEmpty())
    }

    @Test
    fun `multiple runs are isolated`() {
        trail.record(AuditEvent(AuditEventType.RUN_STARTED, runId = "run-A"))
        trail.record(AuditEvent(AuditEventType.RUN_STARTED, runId = "run-B"))
        trail.record(AuditEvent(AuditEventType.RUN_COMPLETED, runId = "run-A"))

        assertEquals(2, trail.queryByRun("run-A").size)
        assertEquals(1, trail.queryByRun("run-B").size)
    }

    @Test
    fun `optional fields stored and retrieved`() {
        trail.record(
            AuditEvent(
                eventType = AuditEventType.STEP_FAILED,
                runId = "run-x",
                stepId = "s1",
                toolName = "shell_exec",
                errorCode = "SCHEMA_VIOLATION",
                detail = "missing field: command",
                argsJson = """{"command":""}""",
            )
        )

        val events = trail.queryByRun("run-x")
        assertEquals(1, events.size)
        val e = events[0]
        assertEquals("SCHEMA_VIOLATION", e.errorCode)
        assertEquals("missing field: command", e.detail)
        assertEquals("""{"command":""}""", e.argsJson)
        assertEquals("shell_exec", e.toolName)
    }
}
