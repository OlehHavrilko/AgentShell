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

        val resourceUrl = loader.getResource(migrationDir) ?: return result

        // Handle classpath resources from JAR files (jar:file:...!/path)
        if (resourceUrl.protocol == "jar") {
            val jarPath = resourceUrl.path.substringBefore("!/").removePrefix("file:")
            val jarFile = java.util.jar.JarFile(java.net.URLDecoder.decode(jarPath, "UTF-8"))
            jarFile.entries().asSequence().use { entries ->
                entries.filter { it.name.startsWith("$migrationDir/") && it.name.endsWith(".sql") }
                    .forEach { entry ->
                        val name = entry.name
                        val version = Regex("V(\\d+)__").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: return@forEach
                        result[version] = jarFile.getInputStream(entry).bufferedReader().readText()
                    }
            }
            return result
        }

        // Handle filesystem paths
        val dir = try { java.io.File(resourceUrl.toURI()) } catch (_: Exception) { return result }
        if (!dir.exists() || !dir.isDirectory) return result

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
