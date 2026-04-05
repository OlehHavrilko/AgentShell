package dev.agentshell.report

import dev.agentshell.audit.AuditEventType
import dev.agentshell.audit.AuditTrail
import dev.agentshell.domain.Run
import dev.agentshell.state.StateStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Builds a structured [RunReport] for a given runId by joining:
 *   - [StateStore] — run metadata, status, checkpoint
 *   - [AuditTrail] — ordered event timeline
 *
 * The report is serialisable to JSON via [toJson] and can be served
 * over HTTP (MetricsServer) or written to disk for debugging.
 */
class RunReportGenerator(
    private val stateStore: StateStore,
    private val auditTrail: AuditTrail,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun generate(runId: String): RunReport {
        val run = stateStore.getRun(runId) ?: error("run not found: $runId")
        val events = auditTrail.queryByRun(runId).sortedBy { it.timestampMs }

        val steps = events
            .filter { it.eventType in setOf(AuditEventType.STEP_STARTED, AuditEventType.STEP_COMPLETED, AuditEventType.STEP_FAILED) }
            .groupBy { it.stepId }
            .map { (stepId, stepEvents) ->
                val started   = stepEvents.firstOrNull { it.eventType == AuditEventType.STEP_STARTED }
                val completed = stepEvents.firstOrNull { it.eventType == AuditEventType.STEP_COMPLETED }
                val failed    = stepEvents.firstOrNull { it.eventType == AuditEventType.STEP_FAILED }
                val terminal  = completed ?: failed
                StepReport(
                    stepId      = stepId ?: "unknown",
                    toolName    = started?.toolName ?: terminal?.toolName ?: "unknown",
                    status      = when {
                        completed != null -> "COMPLETED"
                        failed    != null -> "FAILED"
                        else              -> "STARTED"
                    },
                    startedAtMs  = started?.timestampMs,
                    finishedAtMs = terminal?.timestampMs,
                    durationMs   = if (started != null && terminal != null)
                        terminal.timestampMs - started.timestampMs else null,
                    argsJson    = started?.argsJson,
                    resultJson  = completed?.resultJson,
                    errorCode   = failed?.errorCode,
                    detail      = (failed ?: completed)?.detail,
                )
            }

        val startedAt  = events.firstOrNull { it.eventType == AuditEventType.RUN_STARTED }?.timestampMs
        val finishedAt = events.firstOrNull {
            it.eventType in setOf(AuditEventType.RUN_COMPLETED, AuditEventType.RUN_FAILED, AuditEventType.RUN_CRASHED)
        }?.timestampMs

        return RunReport(
            runId       = run.runId,
            agentId     = run.agentId,
            status      = run.status.name,
            startedAtMs = startedAt,
            finishedAtMs= finishedAt,
            durationMs  = if (startedAt != null && finishedAt != null) finishedAt - startedAt else null,
            stepCount   = steps.size,
            failedSteps = steps.count { it.status == "FAILED" },
            steps       = steps,
            events      = events.map { e ->
                EventEntry(
                    eventType   = e.eventType.name,
                    timestampMs = e.timestampMs,
                    stepId      = e.stepId,
                    toolName    = e.toolName,
                    detail      = e.detail,
                    errorCode   = e.errorCode,
                )
            },
        )
    }

    fun generateJson(runId: String): String = json.encodeToString(generate(runId))

    /** Returns a one-line summary suitable for Prometheus labels / logs. */
    fun summary(runId: String): String {
        val r = generate(runId)
        return "run=${r.runId} agent=${r.agentId} status=${r.status} steps=${r.stepCount} failed=${r.failedSteps} duration=${r.durationMs}ms"
    }
}

// ─── Report data model ───────────────────────────────────────────────────────

@Serializable
data class RunReport(
    val runId: String,
    val agentId: String,
    val status: String,
    val startedAtMs: Long?,
    val finishedAtMs: Long?,
    val durationMs: Long?,
    val stepCount: Int,
    val failedSteps: Int,
    val steps: List<StepReport>,
    val events: List<EventEntry>,
)

@Serializable
data class StepReport(
    val stepId: String,
    val toolName: String,
    val status: String,
    val startedAtMs: Long?,
    val finishedAtMs: Long?,
    val durationMs: Long?,
    val argsJson: String?,
    val resultJson: String?,
    val errorCode: String?,
    val detail: String?,
)

@Serializable
data class EventEntry(
    val eventType: String,
    val timestampMs: Long,
    val stepId: String?,
    val toolName: String?,
    val detail: String?,
    val errorCode: String?,
)
