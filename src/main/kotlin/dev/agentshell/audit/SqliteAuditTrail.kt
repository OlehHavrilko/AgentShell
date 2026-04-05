package dev.agentshell.audit

import dev.agentshell.security.SecretsVault
import dev.agentshell.state.sqlite.DatabaseManager
import java.sql.Connection

class SqliteAuditTrail(private val db: DatabaseManager) : AuditTrail {

    private val conn: Connection get() = db.connection

    override fun record(event: AuditEvent): Unit = synchronized(conn) {
        // Mask any secrets before persisting
        val safeDetail = event.detail?.let { SecretsVault.mask(it) }
        val safeArgsJson = event.argsJson?.let { SecretsVault.mask(it) }
        val safeResultJson = event.resultJson?.let { SecretsVault.mask(it) }

        conn.prepareStatement(
            """INSERT INTO audit_events
               (event_type, run_id, step_id, tool_name, args_json, result_json, error_code, detail, timestamp_ms)
               VALUES (?,?,?,?,?,?,?,?,?)"""
        ).use { stmt ->
            stmt.setString(1, event.eventType.name)
            stmt.setString(2, event.runId)
            stmt.setObject(3, event.stepId)
            stmt.setObject(4, event.toolName)
            stmt.setObject(5, safeArgsJson)
            stmt.setObject(6, safeResultJson)
            stmt.setObject(7, event.errorCode)
            stmt.setObject(8, safeDetail)
            stmt.setLong(9, event.timestampMs)
            stmt.executeUpdate()
        }
    }

    override fun queryByRun(runId: String): List<AuditEvent> = synchronized(conn) {
        conn.prepareStatement(
            "SELECT * FROM audit_events WHERE run_id=? ORDER BY timestamp_ms ASC"
        ).use { stmt ->
            stmt.setString(1, runId)
            stmt.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            AuditEvent(
                                eventType = AuditEventType.valueOf(rs.getString("event_type")),
                                runId = rs.getString("run_id"),
                                stepId = rs.getString("step_id"),
                                toolName = rs.getString("tool_name"),
                                argsJson = rs.getString("args_json"),
                                resultJson = rs.getString("result_json"),
                                errorCode = rs.getString("error_code"),
                                detail = rs.getString("detail"),
                                timestampMs = rs.getLong("timestamp_ms"),
                            )
                        )
                    }
                }
            }
        }
    }
}
