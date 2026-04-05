package dev.agentshell.runtime

import dev.agentshell.domain.Run
import dev.agentshell.domain.RunStatus
import dev.agentshell.state.InMemoryStateStore
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WatchdogServiceTest {

    private fun makeRun(agentId: String, status: RunStatus, heartbeatMs: Long): Run =
        Run(runId = UUID.randomUUID().toString(), agentId = agentId, status = status, heartbeatMs = heartbeatMs)

    @Test
    fun `stuck run is marked CRASHED`() {
        val store = InMemoryStateStore()
        val watchdog = WatchdogService(
            stateStore = store,
            stuckThresholdMs = 100L,
            checkIntervalMs = 60_000L,
        )

        // Heartbeat in the past (500ms ago)
        val run = makeRun("stuck-agent", RunStatus.RUNNING, System.currentTimeMillis() - 500)
        store.saveRun(run)

        watchdog.check()

        val updated = store.findRunByAgent("stuck-agent", setOf(RunStatus.CRASHED))
        assertEquals(RunStatus.CRASHED, updated?.status)
    }

    @Test
    fun `recent run is not touched`() {
        val store = InMemoryStateStore()
        val watchdog = WatchdogService(
            stateStore = store,
            stuckThresholdMs = 60_000L,
            checkIntervalMs = 60_000L,
        )

        val run = makeRun("fresh-agent", RunStatus.RUNNING, System.currentTimeMillis())
        store.saveRun(run)

        watchdog.check()

        val updated = store.findRunByAgent("fresh-agent", setOf(RunStatus.RUNNING))
        assertEquals(RunStatus.RUNNING, updated?.status)
    }

    @Test
    fun `completed run is not touched`() {
        val store = InMemoryStateStore()
        val watchdog = WatchdogService(
            stateStore = store,
            stuckThresholdMs = 100L,
            checkIntervalMs = 60_000L,
        )

        val run = makeRun("done-agent", RunStatus.COMPLETED, System.currentTimeMillis() - 500)
        store.saveRun(run)

        watchdog.check()

        val updated = store.findRunByAgent("done-agent", setOf(RunStatus.COMPLETED))
        assertEquals(RunStatus.COMPLETED, updated?.status)
    }

    @Test
    fun `watchdog starts and stops cleanly`() {
        val store = InMemoryStateStore()
        val watchdog = WatchdogService(
            stateStore = store,
            stuckThresholdMs = 60_000L,
            checkIntervalMs = 50L,
        )
        watchdog.start()
        Thread.sleep(120)
        watchdog.stop()
        assertTrue(true) // no exception = pass
    }
}
