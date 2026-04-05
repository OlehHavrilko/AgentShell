package dev.agentshell.memory

import java.time.Instant

data class MemoryEntry(
    val id: String,
    val runId: String,
    val agentId: String,
    val text: String,
    val tags: List<String> = emptyList(),
    val score: Double = 0.0,
    val createdAt: Instant = Instant.now()
)

interface MemoryStore {
    fun store(entry: MemoryEntry)
    fun search(query: String, topK: Int = 5): List<MemoryEntry>
    fun getByRunId(runId: String): List<MemoryEntry>
    fun clear()
}
