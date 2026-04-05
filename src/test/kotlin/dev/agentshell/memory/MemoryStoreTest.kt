package dev.agentshell.memory

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MemoryStoreTest {

    private lateinit var store: InMemoryMemoryStore

    @BeforeEach
    fun setup() {
        store = InMemoryMemoryStore()
    }

    private fun entry(text: String, runId: String = UUID.randomUUID().toString(), agentId: String = "test-agent") =
        MemoryEntry(id = UUID.randomUUID().toString(), runId = runId, agentId = agentId, text = text)

    @Test
    fun `store and retrieve by runId`() {
        val runId = "run-1"
        store.store(entry("shell command executed successfully", runId = runId))
        store.store(entry("file written to disk", runId = runId))
        store.store(entry("unrelated entry", runId = "other-run"))

        val result = store.getByRunId(runId)
        assertEquals(2, result.size)
    }

    @Test
    fun `search returns relevant entries`() {
        store.store(entry("agent executed shell command to list kotlin files"))
        store.store(entry("git commit message written for feature branch"))
        store.store(entry("file read operation completed successfully"))

        val results = store.search("shell command kotlin", topK = 3)
        assertTrue(results.isNotEmpty())
        assertTrue(results.first().score > 0.0)
    }

    @Test
    fun `search returns topK results`() {
        repeat(10) { i -> store.store(entry("command execution result number $i kotlin")) }
        val results = store.search("command kotlin", topK = 3)
        assertTrue(results.size <= 3)
    }

    @Test
    fun `search returns empty for empty store`() {
        val results = store.search("anything")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `clear removes all entries`() {
        store.store(entry("some text"))
        store.clear()
        assertTrue(store.search("some text").isEmpty())
    }
}
