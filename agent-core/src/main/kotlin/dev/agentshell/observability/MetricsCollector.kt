package dev.agentshell.observability

import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight in-memory metrics collector.
 *
 * All counters are atomic — safe for concurrent updates from multiple threads
 * (agentic loop, heartbeat, watchdog).
 *
 * Exposed via [MetricsServer] as Prometheus text format on `/metrics`.
 */
object MetricsCollector {

    // ── Runs ──────────────────────────────────────────────────────────────────
    val runsStarted   = AtomicLong(0)
    val runsCompleted = AtomicLong(0)
    val runsFailed    = AtomicLong(0)
    val runsCrashed   = AtomicLong(0)
    val runsResumed   = AtomicLong(0)

    // ── Approvals ─────────────────────────────────────────────────────────────
    val approvalsRequested = AtomicLong(0)
    val approvalsApproved  = AtomicLong(0)
    val approvalsRejected  = AtomicLong(0)
    val approvalsExpired   = AtomicLong(0)

    // ── Tool calls ────────────────────────────────────────────────────────────
    val toolCallsTotal    = AtomicLong(0)
    val toolCallsBlocked  = AtomicLong(0)
    val toolCallsFailed   = AtomicLong(0)

    // ── LLM tokens ───────────────────────────────────────────────────────────
    val tokensInput  = AtomicLong(0)
    val tokensOutput = AtomicLong(0)

    // ── Watchdog ──────────────────────────────────────────────────────────────
    val watchdogCrashes = AtomicLong(0)

    // ── Helpers ───────────────────────────────────────────────────────────────

    fun recordTokens(input: Int, output: Int) {
        tokensInput.addAndGet(input.toLong())
        tokensOutput.addAndGet(output.toLong())
    }

    /** Resets all counters — used in tests. */
    fun reset() {
        listOf(
            runsStarted, runsCompleted, runsFailed, runsCrashed, runsResumed,
            approvalsRequested, approvalsApproved, approvalsRejected, approvalsExpired,
            toolCallsTotal, toolCallsBlocked, toolCallsFailed,
            tokensInput, tokensOutput,
            watchdogCrashes,
        ).forEach { it.set(0) }
    }

    /**
     * Renders all metrics in Prometheus text exposition format.
     * https://prometheus.io/docs/instrumenting/exposition_formats/
     */
    fun toPrometheusText(): String = buildString {
        fun gauge(name: String, help: String, value: Long) {
            appendLine("# HELP agentshell_$name $help")
            appendLine("# TYPE agentshell_$name counter")
            appendLine("agentshell_$name $value")
        }
        gauge("runs_started_total",        "Total runs started",             runsStarted.get())
        gauge("runs_completed_total",       "Total runs completed",           runsCompleted.get())
        gauge("runs_failed_total",          "Total runs failed",              runsFailed.get())
        gauge("runs_crashed_total",         "Total runs crashed",             runsCrashed.get())
        gauge("runs_resumed_total",         "Total runs resumed",             runsResumed.get())
        gauge("approvals_requested_total",  "Approval requests sent",         approvalsRequested.get())
        gauge("approvals_approved_total",   "Approvals approved",             approvalsApproved.get())
        gauge("approvals_rejected_total",   "Approvals rejected",             approvalsRejected.get())
        gauge("approvals_expired_total",    "Approvals expired (TTL)",        approvalsExpired.get())
        gauge("tool_calls_total",           "Total tool calls dispatched",    toolCallsTotal.get())
        gauge("tool_calls_blocked_total",   "Tool calls blocked by rules",    toolCallsBlocked.get())
        gauge("tool_calls_failed_total",    "Tool calls that returned error", toolCallsFailed.get())
        gauge("tokens_input_total",         "Total LLM input tokens",         tokensInput.get())
        gauge("tokens_output_total",        "Total LLM output tokens",        tokensOutput.get())
        gauge("watchdog_crashes_total",     "Runs marked CRASHED by watchdog",watchdogCrashes.get())
    }
}
