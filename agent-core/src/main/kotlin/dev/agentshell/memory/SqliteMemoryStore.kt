package dev.agentshell.memory

import dev.agentshell.state.sqlite.DatabaseManager
import java.time.Instant

/**
 * SQLite-backed MemoryStore. Uses LIKE-based full-text search for portability,
 * with TF-IDF re-ranking after retrieval.
 * Supports temporal decay via [MemoryEntry.decayHalfLifeMs].
 */
class SqliteMemoryStore(private val db: DatabaseManager) : MemoryStore {

    private val inMemory = InMemoryMemoryStore()

    init {
        createTable()
        loadAll()
    }

    private fun createTable() {
        db.connection.createStatement().use { stmt ->
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS memory_entries (
                    id TEXT PRIMARY KEY,
                    run_id TEXT NOT NULL,
                    agent_id TEXT NOT NULL,
                    text TEXT NOT NULL,
                    tags TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL,
                    decay_half_life_ms INTEGER NOT NULL DEFAULT 9223372036854775807
                )
            """)
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mem_run ON memory_entries(run_id)")
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_mem_agent ON memory_entries(agent_id)")
            // Add decay column to existing DBs (idempotent)
            runCatching {
                stmt.execute(
                    "ALTER TABLE memory_entries ADD COLUMN decay_half_life_ms INTEGER NOT NULL DEFAULT 9223372036854775807"
                )
            }
        }
    }

    private fun loadAll() {
        db.connection.createStatement().use { stmt ->
            val rs = stmt.executeQuery(
                "SELECT id, run_id, agent_id, text, tags, created_at, decay_half_life_ms FROM memory_entries ORDER BY created_at ASC"
            )
            while (rs.next()) {
                val entry = MemoryEntry(
                    id = rs.getString("id"),
                    runId = rs.getString("run_id"),
                    agentId = rs.getString("agent_id"),
                    text = rs.getString("text"),
                    tags = rs.getString("tags").split(",").filter { it.isNotBlank() },
                    createdAt = Instant.ofEpochMilli(rs.getLong("created_at")),
                    decayHalfLifeMs = rs.getLong("decay_half_life_ms"),
                )
                inMemory.store(entry)
            }
        }
    }

    override fun store(entry: MemoryEntry) {
        inMemory.store(entry)
        val sql = """
            INSERT OR REPLACE INTO memory_entries
                (id, run_id, agent_id, text, tags, created_at, decay_half_life_ms)
            VALUES (?,?,?,?,?,?,?)
        """
        db.connection.prepareStatement(sql).use { ps ->
            ps.setString(1, entry.id)
            ps.setString(2, entry.runId)
            ps.setString(3, entry.agentId)
            ps.setString(4, entry.text)
            ps.setString(5, entry.tags.joinToString(","))
            ps.setLong(6, entry.createdAt.toEpochMilli())
            ps.setLong(7, entry.decayHalfLifeMs)
            ps.executeUpdate()
        }
    }

    override fun search(query: String, topK: Int): List<MemoryEntry> =
        inMemory.search(query, topK)

    override fun searchByTag(tag: String, topK: Int): List<MemoryEntry> =
        inMemory.searchByTag(tag, topK)

    override fun getAll(): List<MemoryEntry> =
        inMemory.getAll()

    override fun getByRunId(runId: String): List<MemoryEntry> =
        inMemory.getByRunId(runId)

    override fun clear() {
        inMemory.clear()
        db.connection.createStatement().use { it.execute("DELETE FROM memory_entries") }
    }
}
