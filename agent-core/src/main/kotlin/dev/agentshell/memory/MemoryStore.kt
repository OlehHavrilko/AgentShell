package dev.agentshell.memory

import java.time.Instant

data class MemoryEntry(
    val id: String,
    val runId: String,
    val agentId: String,
    val text: String,
    val tags: List<String> = emptyList(),
    val score: Double = 0.0,
    val createdAt: Instant = Instant.now(),
    /**
     * Temporal decay half-life in milliseconds.
     * After this duration the entry's relevance score is halved.
     * [Long.MAX_VALUE] = no decay (default).
     */
    val decayHalfLifeMs: Long = Long.MAX_VALUE,
)

interface MemoryStore {
    fun store(entry: MemoryEntry)
    fun search(query: String, topK: Int = 5): List<MemoryEntry>
    fun searchByTag(tag: String, topK: Int = 20): List<MemoryEntry>
    fun getAll(): List<MemoryEntry>
    fun getByRunId(runId: String): List<MemoryEntry>
    fun clear()
}
