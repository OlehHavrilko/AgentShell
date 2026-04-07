package dev.agentshell.memory

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * In-memory MemoryStore using TF-IDF cosine similarity for semantic search.
 * Supports optional temporal decay per [MemoryEntry.decayHalfLifeMs].
 */
class InMemoryMemoryStore : MemoryStore {

    private val entries = mutableListOf<MemoryEntry>()

    override fun store(entry: MemoryEntry) {
        synchronized(entries) { entries.add(entry) }
    }

    override fun search(query: String, topK: Int): List<MemoryEntry> {
        val queryTokens = tokenize(query)
        if (queryTokens.isEmpty()) return emptyList()

        val corpus = synchronized(entries) { entries.toList() }
        if (corpus.isEmpty()) return emptyList()

        val allDocs = corpus.map { tokenize(it.text) }
        val idf = computeIdf(allDocs)
        val queryVec = tfidfVector(queryTokens, idf, allDocs)
        val now = System.currentTimeMillis()

        return corpus
            .mapIndexed { i, entry ->
                val docVec = tfidfVector(allDocs[i], idf, allDocs)
                val similarity = cosineSimilarity(queryVec, docVec)
                val decayFactor = computeDecay(entry, now)
                entry.copy(score = similarity * decayFactor)
            }
            .filter { it.score > 0.0 }
            .sortedByDescending { it.score }
            .take(topK)
    }

    override fun searchByTag(tag: String, topK: Int): List<MemoryEntry> =
        synchronized(entries) {
            entries.filter { tag in it.tags }
                .sortedByDescending { it.createdAt }
                .take(topK)
        }

    override fun getAll(): List<MemoryEntry> =
        synchronized(entries) { entries.toList() }

    override fun getByRunId(runId: String): List<MemoryEntry> =
        synchronized(entries) { entries.filter { it.runId == runId } }

    override fun clear() = synchronized(entries) { entries.clear() }

    // ── Temporal decay ────────────────────────────────────────────────────────

    private fun computeDecay(entry: MemoryEntry, nowMs: Long): Double {
        if (entry.decayHalfLifeMs == Long.MAX_VALUE) return 1.0
        val ageMs = (nowMs - entry.createdAt.toEpochMilli()).coerceAtLeast(0)
        return exp(-ln(2.0) * ageMs / entry.decayHalfLifeMs)
    }

    // ── TF-IDF helpers ─────────────────────────────────────────────────────────

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^a-z0-9\\s_]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 }

    private fun computeIdf(docs: List<List<String>>): Map<String, Double> {
        val n = docs.size.toDouble()
        val df = mutableMapOf<String, Int>()
        for (doc in docs) doc.toSet().forEach { df[it] = (df[it] ?: 0) + 1 }
        return df.mapValues { (_, count) -> Math.log((n + 1) / (count + 1)) + 1.0 }
    }

    private fun tfidfVector(tokens: List<String>, idf: Map<String, Double>, allDocs: List<List<String>>): Map<String, Double> {
        val tf = tokens.groupingBy { it }.eachCount()
        val maxTf = tf.values.maxOrNull()?.toDouble() ?: 1.0
        return tf.entries.associate { (term, count) ->
            term to (count / maxTf) * (idf[term] ?: 1.0)
        }
    }

    private fun cosineSimilarity(a: Map<String, Double>, b: Map<String, Double>): Double {
        val dot = a.entries.sumOf { (k, v) -> v * (b[k] ?: 0.0) }
        val normA = sqrt(a.values.sumOf { it * it })
        val normB = sqrt(b.values.sumOf { it * it })
        return if (normA == 0.0 || normB == 0.0) 0.0 else dot / (normA * normB)
    }
}
