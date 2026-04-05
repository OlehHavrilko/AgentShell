package dev.agentshell.runtime

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

enum class IdempotencyStatus { IN_PROGRESS, COMPLETED, FAILED }

data class IdempotencyEntry(
    val key: String,
    val status: IdempotencyStatus,
    val resultJson: String? = null,
    val createdAtMs: Long,
    val expiresAtMs: Long,
)

class IdempotencyService(
    private val ttlMs: Long = 24 * 60 * 60 * 1000L,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val entries = ConcurrentHashMap<String, IdempotencyEntry>()

    fun begin(key: String): IdempotencyEntry {
        val now = clock.millis()
        prune(now)
        val existing = entries[key]
        if (existing != null) {
            return existing
        }
        val created = IdempotencyEntry(
            key = key,
            status = IdempotencyStatus.IN_PROGRESS,
            createdAtMs = now,
            expiresAtMs = now + ttlMs,
        )
        entries[key] = created
        return created
    }

    fun complete(key: String, resultJson: String?): IdempotencyEntry {
        val current = entries[key] ?: error("idempotency key not started: $key")
        val completed = current.copy(status = IdempotencyStatus.COMPLETED, resultJson = resultJson)
        entries[key] = completed
        return completed
    }

    fun fail(key: String): IdempotencyEntry {
        val current = entries[key] ?: error("idempotency key not started: $key")
        val failed = current.copy(status = IdempotencyStatus.FAILED)
        entries[key] = failed
        return failed
    }

    fun get(key: String): IdempotencyEntry? = entries[key]

    private fun prune(now: Long) {
        entries.entries.removeIf { (_, value) -> value.expiresAtMs <= now }
    }
}
