package dev.agentshell.audit

import kotlinx.serialization.Serializable

enum class AuditEventType {
    RUN_STARTED, RUN_RESUMED, RUN_COMPLETED, RUN_FAILED, RUN_CRASHED,
    STEP_STARTED, STEP_COMPLETED, STEP_FAILED,
    APPROVAL_REQUESTED, APPROVAL_DECIDED,
}

@Serializable
data class AuditEvent(
    val eventType: AuditEventType,
    val runId: String,
    val stepId: String? = null,
    val toolName: String? = null,
    val argsJson: String? = null,
    val resultJson: String? = null,
    val errorCode: String? = null,
    val detail: String? = null,
    val timestampMs: Long = System.currentTimeMillis(),
)

interface AuditTrail {
    fun record(event: AuditEvent)
    fun queryByRun(runId: String): List<AuditEvent>
}
