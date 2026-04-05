package dev.agentshell.runtime

import dev.agentshell.state.StateStore
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Sends periodic heartbeat updates for an active run.
 * Stops automatically when cancel() is called or the JVM shuts down.
 */
class HeartbeatService(
    private val stateStore: StateStore,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(HeartbeatService::class.java)
    private val scheduler = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "agentshell-heartbeat").also { it.isDaemon = true }
    }
    private var future: ScheduledFuture<*>? = null

    fun start(runId: String) {
        stop()
        log.debug("Heartbeat started for runId={}", runId)
        future = scheduler.scheduleAtFixedRate(
            {
                try {
                    stateStore.updateHeartbeat(runId, clock.millis())
                    log.debug("Heartbeat updated for runId={}", runId)
                } catch (e: Exception) {
                    log.warn("Heartbeat update failed for runId={}: {}", runId, e.message)
                }
            },
            intervalMs, intervalMs, TimeUnit.MILLISECONDS,
        )
    }

    fun stop() {
        future?.cancel(false)
        future = null
    }

    fun shutdown() {
        stop()
        scheduler.shutdownNow()
    }

    companion object {
        const val DEFAULT_INTERVAL_MS = 10_000L
    }
}
