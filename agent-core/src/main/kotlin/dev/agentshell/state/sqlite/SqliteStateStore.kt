package dev.agentshell.state.sqlite

import dev.agentshell.domain.Checkpoint
import dev.agentshell.domain.Run
import dev.agentshell.domain.RunStatus
import dev.agentshell.state.StateStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.ResultSet

/**
 * SQLite-backed StateStore. Thread-safe via synchronized access to a single connection.
 * For production use, replace with a connection pool.
 */
class SqliteStateStore(private val db: DatabaseManager) : StateStore {

    private val conn: Connection get() = db.connection
    private val json = Json { ignoreUnknownKeys = true }

    override fun saveRun(run: Run): Run = synchronized(conn) {
        conn.prepareStatement(
            """INSERT INTO runs(run_id, agent_id, status, heartbeat_ms,
               checkpoint_step_index, checkpoint_context, checkpoint_artifact_refs)
               VALUES (?,?,?,?,?,?,?)
               ON CONFLICT(run_id) DO UPDATE SET
                 agent_id=excluded.agent_id, status=excluded.status,
                 heartbeat_ms=excluded.heartbeat_ms,
                 checkpoint_step_index=excluded.checkpoint_step_index,
                 checkpoint_context=excluded.checkpoint_context,
                 checkpoint_artifact_refs=excluded.checkpoint_artifact_refs"""
        ).use { stmt ->
            stmt.setString(1, run.runId)
            stmt.setString(2, run.agentId)
            stmt.setString(3, run.status.name)
            stmt.setLong(4, run.heartbeatMs)
            stmt.setObject(5, run.checkpoint?.stepIndex)
            stmt.setObject(6, run.checkpoint?.contextSummary)
            stmt.setObject(7, run.checkpoint?.artifactRefs?.let { json.encodeToString(it) })
            stmt.executeUpdate()
        }
        run
    }

    override fun getRun(runId: String): Run? = synchronized(conn) {
        conn.prepareStatement("SELECT * FROM runs WHERE run_id=?").use { stmt ->
            stmt.setString(1, runId)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.toRun() else null }
        }
    }

    override fun listAll(): List<Run> = synchronized(conn) {
        conn.prepareStatement("SELECT * FROM runs ORDER BY heartbeat_ms DESC").use { stmt ->
            stmt.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.toRun()) } }
        }
    }

    override fun findRunByAgent(agentId: String, statuses: Set<RunStatus>): Run? = synchronized(conn) {
        if (statuses.isEmpty()) return null
        val placeholders = statuses.joinToString(",") { "?" }
        conn.prepareStatement(
            "SELECT * FROM runs WHERE agent_id=? AND status IN ($placeholders) ORDER BY heartbeat_ms DESC LIMIT 1"
        ).use { stmt ->
            stmt.setString(1, agentId)
            statuses.forEachIndexed { i, s -> stmt.setString(i + 2, s.name) }
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.toRun() else null
            }
        }
    }

    override fun listRunsByStatus(statuses: Set<RunStatus>): List<Run> = synchronized(conn) {
        if (statuses.isEmpty()) return emptyList()
        val placeholders = statuses.joinToString(",") { "?" }
        conn.prepareStatement(
            "SELECT * FROM runs WHERE status IN ($placeholders) ORDER BY heartbeat_ms DESC"
        ).use { stmt ->
            statuses.forEachIndexed { i, s -> stmt.setString(i + 1, s.name) }
            stmt.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.toRun()) }
            }
        }
    }

    override fun updateHeartbeat(runId: String, heartbeatMs: Long): Run = synchronized(conn) {
        conn.prepareStatement("UPDATE runs SET heartbeat_ms=? WHERE run_id=?").use { stmt ->
            stmt.setLong(1, heartbeatMs)
            stmt.setString(2, runId)
            stmt.executeUpdate()
        }
        loadRun(runId)
    }

    override fun updateCheckpoint(runId: String, checkpoint: Checkpoint): Run = synchronized(conn) {
        conn.prepareStatement(
            "UPDATE runs SET checkpoint_step_index=?, checkpoint_context=?, checkpoint_artifact_refs=? WHERE run_id=?"
        ).use { stmt ->
            stmt.setInt(1, checkpoint.stepIndex)
            stmt.setString(2, checkpoint.contextSummary)
            stmt.setString(3, json.encodeToString(checkpoint.artifactRefs))
            stmt.setString(4, runId)
            stmt.executeUpdate()
        }
        loadRun(runId)
    }

    override fun updateStatus(runId: String, status: RunStatus): Run = synchronized(conn) {
        conn.prepareStatement("UPDATE runs SET status=? WHERE run_id=?").use { stmt ->
            stmt.setString(1, status.name)
            stmt.setString(2, runId)
            stmt.executeUpdate()
        }
        loadRun(runId)
    }

    private fun loadRun(runId: String): Run {
        return conn.prepareStatement("SELECT * FROM runs WHERE run_id=?").use { stmt ->
            stmt.setString(1, runId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.toRun()
                else error("run not found: $runId")
            }
        }
    }

    private fun ResultSet.toRun(): Run {
        val stepIndex = getObject("checkpoint_step_index") as? Int
        val checkpoint = if (stepIndex != null) {
            val refs: List<String> = getString("checkpoint_artifact_refs")
                ?.let { json.decodeFromString(it) } ?: emptyList()
            Checkpoint(
                stepIndex = stepIndex,
                contextSummary = getString("checkpoint_context") ?: "",
                artifactRefs = refs,
            )
        } else null

        return Run(
            runId = getString("run_id"),
            agentId = getString("agent_id"),
            status = RunStatus.valueOf(getString("status")),
            heartbeatMs = getLong("heartbeat_ms"),
            checkpoint = checkpoint,
        )
    }
}
