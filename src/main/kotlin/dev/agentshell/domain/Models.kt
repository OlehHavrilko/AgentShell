package dev.agentshell.domain

import kotlinx.serialization.Serializable

@Serializable
enum class RiskLevel { LOW, MEDIUM, HIGH }

@Serializable
data class ToolContract(
    val name: String,
    val version: String,
    val description: String,
    val riskLevel: RiskLevel,
    val sandboxPolicy: String,
)

@Serializable
data class ToolCall(
    val callId: String,
    val toolName: String,
    val argumentsJson: String,
    val idempotencyKey: String? = null,
)

@Serializable
data class ToolResult(
    val callId: String,
    val success: Boolean,
    val outputJson: String? = null,
    val errorCode: ErrorCode? = null,
    val errorDetail: String? = null,
)

@Serializable
enum class RunStatus {
    CREATED, QUEUED, RUNNING, WAITING_APPROVAL, COMPLETED, FAILED, CANCELLED, CRASHED
}

@Serializable
data class Checkpoint(
    val stepIndex: Int,
    val contextSummary: String,
    val artifactRefs: List<String>,
)

@Serializable
data class Run(
    val runId: String,
    val agentId: String,
    val status: RunStatus,
    val heartbeatMs: Long,
    val checkpoint: Checkpoint? = null,
)

@Serializable
data class Step(
    val stepId: String,
    val runId: String,
    val index: Int,
    val toolName: String,
    val status: String,
)

@Serializable
data class ApprovalRequest(
    val approvalId: String,
    val runId: String,
    val stepId: String,
    val riskScore: Int,
    val impactPreview: String,
    val status: String = "PENDING",
)
