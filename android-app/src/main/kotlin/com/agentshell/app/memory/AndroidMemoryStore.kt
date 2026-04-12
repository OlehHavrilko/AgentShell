package com.agentshell.app.memory

import com.agentshell.app.db.MemoryDao
import com.agentshell.app.db.MemoryEntity
import dev.agentshell.memory.MemoryEntry
import dev.agentshell.memory.MemoryStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class AndroidMemoryStore(
    private val dao: MemoryDao,
) : MemoryStore {

    override fun store(entry: MemoryEntry) {
        dao.upsert(entry.toEntity())
    }

    override fun search(query: String, topK: Int): List<MemoryEntry> {
        val needle = query.trim().lowercase()
        return dao.getAll()
            .map { it.toDomain() }
            .filter { entry ->
                needle.isBlank() ||
                    entry.text.lowercase().contains(needle) ||
                    entry.tags.any { it.lowercase().contains(needle) }
            }
            .take(topK)
    }

    override fun searchByTag(tag: String, topK: Int): List<MemoryEntry> {
        val needle = tag.trim().lowercase()
        return dao.getAll()
            .map { it.toDomain() }
            .filter { entry -> entry.tags.any { it.lowercase().contains(needle) } }
            .take(topK)
    }

    override fun getAll(): List<MemoryEntry> = dao.getAll().map { it.toDomain() }

    override fun getByRunId(runId: String): List<MemoryEntry> = dao.getByRunId(runId).map { it.toDomain() }

    override fun clear() {
        dao.clear()
    }

    fun recentFlow(limit: Int = 50): Flow<List<MemoryEntry>> =
        dao.recent(limit).map { items -> items.map { it.toDomain() } }

    fun searchFlow(query: String, limit: Int = 50): Flow<List<MemoryEntry>> =
        if (query.isBlank()) recentFlow(limit)
        else dao.search(query.trim(), limit).map { items -> items.map { it.toDomain() } }

    private fun MemoryEntry.toEntity(): MemoryEntity = MemoryEntity(
        memoryId = id,
        runId = runId,
        agentId = agentId,
        text = text,
        tags = tags.joinToString("|"),
        score = score,
        createdAtEpochMs = createdAt.toEpochMilli(),
        decayHalfLifeMs = decayHalfLifeMs,
    )

    private fun MemoryEntity.toDomain(): MemoryEntry = MemoryEntry(
        id = memoryId,
        runId = runId,
        agentId = agentId,
        text = text,
        tags = tags.split('|').filter { it.isNotBlank() },
        score = score,
        createdAt = Instant.ofEpochMilli(createdAtEpochMs),
        decayHalfLifeMs = decayHalfLifeMs,
    )
}