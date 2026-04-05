package dev.agentshell.runtime

import dev.agentshell.audit.AuditEvent
import dev.agentshell.audit.AuditEventType
import dev.agentshell.audit.AuditTrail
import dev.agentshell.domain.ApprovalRequest
import dev.agentshell.domain.Checkpoint
import dev.agentshell.domain.ErrorCode
import dev.agentshell.domain.RunStatus
import dev.agentshell.domain.Step
import dev.agentshell.domain.ToolCall
import dev.agentshell.domain.ToolContract
import dev.agentshell.domain.ToolResult
import dev.agentshell.executor.ToolDispatcher
import dev.agentshell.runtime.IdempotencyService
import dev.agentshell.runtime.IdempotencyStatus
import dev.agentshell.state.StateStore
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Orchestrates a full agent run: start/resume, step execution with idempotency,
 * risk scoring, approval gating, retry, heartbeat, and checkpoint saving.
 */
class AgentRunner(
    private val stateStore: StateStore,
    private val dispatcher: ToolDispatcher = ToolDispatcher(),
    private val riskScorer: RiskScorer = RiskScorer(),
    private val approvalGate: ApprovalGate = ApprovalGate(),
    private val idempotency: IdempotencyService = IdempotencyService(),
    private val heartbeat: HeartbeatService = HeartbeatService(stateStore),
    private val runtime: AgentRuntime = AgentRuntime(stateStore),
    private val auditTrail: AuditTrail? = null,
) {
    private val log = LoggerFactory.getLogger(AgentRunner::class.java)

    data class RunConfig(
        val agentId: String,
        val steps: List<StepDef>,
        val riskThreshold: Int = 60,
        val isNewRepo: Boolean = false,
    )

    data class StepDef(
        val call: ToolCall,
        val contract: ToolContract,
    )

    data class RunOutcome(
        val runId: String,
        val status: RunStatus,
        val results: List<ToolResult>,
    )

    private fun audit(event: AuditEvent) = auditTrail?.record(event)

    fun execute(config: RunConfig): RunOutcome {
        val decision = runtime.startOrResume(config.agentId)
        val runId: String
        val fromStep: Int

        when (decision) {
            is RuntimeDecision.WaitingApproval -> {
                log.info("Run {} is waiting for approval — skipping", decision.runId)
                return RunOutcome(decision.runId, RunStatus.WAITING_APPROVAL, emptyList())
            }
            is RuntimeDecision.StartNew -> {
                runId = decision.runId
                fromStep = decision.fromStep
                log.info("Starting new run={} agentId={} steps={}", runId, config.agentId, config.steps.size)
                audit(AuditEvent(AuditEventType.RUN_STARTED, runId, detail = "agentId=${config.agentId} steps=${config.steps.size}"))
            }
            is RuntimeDecision.Resume -> {
                runId = decision.runId
                fromStep = decision.fromStep
                log.info("Resuming run={} agentId={} fromStep={}", runId, config.agentId, fromStep)
                audit(AuditEvent(AuditEventType.RUN_RESUMED, runId, detail = "fromStep=$fromStep"))
            }
        }

        heartbeat.start(runId)
        val results = mutableListOf<ToolResult>()

        try {
            for (index in fromStep until config.steps.size) {
                val stepDef = config.steps[index]
                val stepId = UUID.randomUUID().toString()

                log.info("Step {}/{} runId={} tool={}", index + 1, config.steps.size, runId, stepDef.call.toolName)
                audit(AuditEvent(AuditEventType.STEP_STARTED, runId, stepId = stepId, toolName = stepDef.call.toolName, argsJson = stepDef.call.argumentsJson.take(500)))

                // Risk check
                val riskScore = riskScorer.score(stepDef.call.toolName, stepDef.call.argumentsJson, config.isNewRepo)
                if (riskScorer.requiresApproval(riskScore, config.riskThreshold)) {
                    val approval = ApprovalRequest(
                        approvalId = UUID.randomUUID().toString(),
                        runId = runId,
                        stepId = stepId,
                        riskScore = riskScore,
                        impactPreview = "tool=${stepDef.call.toolName} args=${stepDef.call.argumentsJson.take(200)}",
                    )
                    approvalGate.request(approval)
                    stateStore.updateStatus(runId, RunStatus.WAITING_APPROVAL)
                    log.warn("Step {} requires approval (score={}) approvalId={}", index, riskScore, approval.approvalId)
                    audit(AuditEvent(AuditEventType.APPROVAL_REQUESTED, runId, stepId = stepId, detail = "approvalId=${approval.approvalId} score=$riskScore"))
                    return RunOutcome(runId, RunStatus.WAITING_APPROVAL, results)
                }

                // Idempotency check
                val idemKey = stepDef.call.idempotencyKey
                if (idemKey != null) {
                    val existing = idempotency.get(idemKey)
                    if (existing != null) {
                        when (existing.status) {
                            IdempotencyStatus.COMPLETED -> {
                                log.info("Step {} skipped (idempotency hit) key={}", index, idemKey)
                                existing.resultJson?.let {
                                    results.add(ToolResult(callId = stepDef.call.callId, success = true, outputJson = it))
                                }
                                continue
                            }
                            IdempotencyStatus.IN_PROGRESS -> {
                                log.warn("Step {} idempotency conflict key={}", index, idemKey)
                                val conflictResult = ToolResult(
                                    callId = stepDef.call.callId,
                                    success = false,
                                    errorCode = ErrorCode.ERR_IDEMPOTENCY_CONFLICT,
                                    errorDetail = "Another execution with key=$idemKey is in progress",
                                )
                                results.add(conflictResult)
                                stateStore.updateStatus(runId, RunStatus.FAILED)
                                return RunOutcome(runId, RunStatus.FAILED, results)
                            }
                            IdempotencyStatus.FAILED -> { /* previous attempt failed — retry */ }
                        }
                    }
                    idempotency.begin(idemKey)
                }

                // Execute with retry
                val policy = ToolDispatcher.policyFor(stepDef.contract)
                val result = executeWithRetry(stepDef.call, stepDef.contract, policy, dispatcher)
                results.add(result)

                // Update idempotency
                if (idemKey != null) {
                    if (result.success) idempotency.complete(idemKey, result.outputJson)
                    else idempotency.fail(idemKey)
                }

                if (!result.success) {
                    log.error("Step {} FAILED runId={} errorCode={} detail={}", index, runId, result.errorCode, result.errorDetail)
                    audit(AuditEvent(AuditEventType.STEP_FAILED, runId, stepId = stepId, toolName = stepDef.call.toolName, errorCode = result.errorCode?.name, detail = result.errorDetail))
                    stateStore.updateStatus(runId, RunStatus.FAILED)
                    return RunOutcome(runId, RunStatus.FAILED, results)
                }

                // Save checkpoint after each successful step
                stateStore.updateCheckpoint(
                    runId,
                    Checkpoint(stepIndex = index, contextSummary = "completed step $index", artifactRefs = emptyList()),
                )
                audit(AuditEvent(AuditEventType.STEP_COMPLETED, runId, stepId = stepId, toolName = stepDef.call.toolName, resultJson = result.outputJson?.take(500)))
                log.info("Step {} completed runId={}", index, runId)
            }

            stateStore.updateStatus(runId, RunStatus.COMPLETED)
            log.info("Run {} COMPLETED ({} steps)", runId, results.size)
            audit(AuditEvent(AuditEventType.RUN_COMPLETED, runId, detail = "steps=${results.size}"))
            return RunOutcome(runId, RunStatus.COMPLETED, results)

        } catch (e: Exception) {
            log.error("Run {} CRASHED: {}", runId, e.message, e)
            stateStore.updateStatus(runId, RunStatus.CRASHED)
            audit(AuditEvent(AuditEventType.RUN_CRASHED, runId, detail = e.message))
            return RunOutcome(runId, RunStatus.CRASHED, results)
        } finally {
            heartbeat.stop()
        }
    }
}
