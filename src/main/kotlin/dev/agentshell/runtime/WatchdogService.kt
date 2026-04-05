package dev.agentshell.runtime

import dev.agentshell.audit.AuditEvent
import dev.agentshell.audit.AuditEventType
import dev.agentshell.audit.AuditTrail
import dev.agentshell.domain.RunStatus
import dev.agentshell.observability.MetricsCollector
import dev.agentshell.state.StateStore
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Detects stuck runs by periodically comparing heartbeat timestamps against
 * [stuckThresholdMs]. A run is considered stuck if it is RUNNING but its
 * heartbeat has not been updated within the threshold.
 *
 * On detecting a stuck run: marks it CRASHED + records a RUN_CRASHED audit event.
 */
class WatchdogService(
    private val stateStore: StateStore,
    private val stuckThresholdMs: Long = DEFAULT_STUCK_THRESHOLD_MS,
    private val checkIntervalMs: Long = DEFAULT_CHECK_INTERVAL_MS,
    private val auditTrail: AuditTrail? = null,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(WatchdogService::class.java)
    private var scheduler: ScheduledExecutorService? = null

    fun start() {
        scheduler = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "agentshell-watchdog").also { it.isDaemon = true }
        }
        scheduler!!.scheduleAtFixedRate(::check, checkIntervalMs, checkIntervalMs, TimeUnit.MILLISECONDS)
        log.info("WatchdogService started: checkInterval={}ms stuckThreshold={}ms", checkIntervalMs, stuckThresholdMs)
    }

    fun stop() {
        scheduler?.shutdownNow()
        scheduler = null
        log.info("WatchdogService stopped")
    }

    fun check() {
        val now = clock.millis()
        val runningRuns = stateStore.listRunsByStatus(setOf(RunStatus.RUNNING))
        for (run in runningRuns) {
            val age = now - run.heartbeatMs
            if (age > stuckThresholdMs) {
                log.warn(
                    "Watchdog: run {} is STUCK (heartbeat age={}ms > threshold={}ms) — marking CRASHED",
                    run.runId, age, stuckThresholdMs,
                )
                stateStore.updateStatus(run.runId, RunStatus.CRASHED)
                MetricsCollector.watchdogCrashes.incrementAndGet()
                auditTrail?.record(
                    AuditEvent(
                        eventType = AuditEventType.RUN_CRASHED,
                        runId = run.runId,
                        detail = "watchdog: heartbeat age=${age}ms exceeded threshold=${stuckThresholdMs}ms",
                    )
                )
            }
        }
    }

    companion object {
        const val DEFAULT_STUCK_THRESHOLD_MS = 120_000L  // 2 minutes
        const val DEFAULT_CHECK_INTERVAL_MS  =  30_000L  // 30 seconds
    }
}
