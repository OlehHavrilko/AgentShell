package dev.agentshell.state.sqlite

import java.sql.Connection
import java.sql.DriverManager

/**
 * Manages a SQLite connection and applies schema migrations from classpath resources.
 * Migrations live in db/migrations/V{N}__*.sql and are applied in version order.
 */
class DatabaseManager(val dbPath: String) {

    val connection: Connection by lazy {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:$dbPath").also { conn ->
            conn.createStatement().use { it.execute("PRAGMA journal_mode=WAL") }
            conn.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
            applyMigrations(conn)
        }
    }

    private fun applyMigrations(conn: Connection) {
        conn.createStatement().use { stmt ->
            stmt.execute(
                """CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER PRIMARY KEY,
                    applied_at TEXT NOT NULL
                )"""
            )
        }

        val applied = mutableSetOf<Int>()
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT version FROM schema_version").use { rs ->
                while (rs.next()) applied.add(rs.getInt(1))
            }
        }

        val migrations = loadMigrations()
        for ((version, sql) in migrations.entries.sortedBy { it.key }) {
            if (version in applied) continue
            conn.autoCommit = false
            try {
                conn.createStatement().use { it.executeUpdate(sql) }
                conn.prepareStatement("INSERT INTO schema_version(version, applied_at) VALUES (?, datetime('now'))").use {
                    it.setInt(1, version)
                    it.executeUpdate()
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw IllegalStateException("Migration V$version failed: ${e.message}", e)
            } finally {
                conn.autoCommit = true
            }
        }
    }

    private fun loadMigrations(): Map<Int, String> {
        val loader = Thread.currentThread().contextClassLoader
        val migrationDir = "db/migrations"
        val result = mutableMapOf<Int, String>()

        // Enumerate classpath resources via manifest listing
        val resourceUrl = loader.getResource(migrationDir) ?: return result
        val dir = java.io.File(resourceUrl.toURI())
        if (!dir.exists()) return result

        dir.listFiles { f -> f.name.matches(Regex("V\\d+__.*\\.sql")) }
            ?.forEach { file ->
                val version = file.name.removePrefix("V").substringBefore("__").toIntOrNull() ?: return@forEach
                result[version] = file.readText()
            }
        return result
    }

    fun close() {
        if (!connection.isClosed) connection.close()
    }
}
