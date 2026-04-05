package dev.agentshell.runtime

import dev.agentshell.audit.AuditEvent
import dev.agentshell.audit.AuditEventType
import dev.agentshell.audit.AuditTrail
import dev.agentshell.llm.ContextBudgetManager
import dev.agentshell.llm.LlmGateway
import dev.agentshell.llm.LlmMessage
import dev.agentshell.llm.LlmResponse
import dev.agentshell.llm.LlmTool
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
    private val gateway: LlmGateway? = null,
) {
    private val log = LoggerFactory.getLogger(AgentRunner::class.java)

    data class RunConfig(
        val agentId: String,
        val steps: List<StepDef>,
        val riskThreshold: Int = 60,
        val isNewRepo: Boolean = false,
    )

    data class AgenticConfig(
        val agentId: String,
        val goal: String,
        val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
        val riskThreshold: Int = 60,
        val maxIterations: Int = 50,
        val maxInputTokens: Int = 100_000,
    )

    data class StepDef(
        val call: ToolCall,
        val contract: ToolContract,
    )

    data class RunOutcome(
        val runId: String,
        val status: RunStatus,
        val results: List<ToolResult>,
        val finalMessage: String? = null,
    )

    /**
     * Agentic execution mode: the LLM drives the loop, choosing tools until it decides to stop.
     *
     * Loop:
     * 1. Send conversation (system + history + goal) to LLM
     * 2. If LLM returns tool_calls → execute each → append results → go to 1
     * 3. If LLM returns END_TURN → done
     *
     * Requires [gateway] to be non-null on this AgentRunner instance.
     */
    fun executeAgentic(config: AgenticConfig): RunOutcome {
        requireNotNull(gateway) { "LlmGateway is required for agentic mode. Set gateway= in AgentRunner constructor." }

        // Create run record in state store
        val run = dev.agentshell.domain.Run(
            runId = UUID.randomUUID().toString(),
            agentId = config.agentId,
            status = RunStatus.RUNNING,
            heartbeatMs = System.currentTimeMillis(),
            checkpoint = dev.agentshell.domain.Checkpoint(stepIndex = -1, contextSummary = "agentic", artifactRefs = emptyList()),
        )
        stateStore.saveRun(run)
        val runId = run.runId

        log.info("Starting agentic run={} agentId={} goal={}", runId, config.agentId, config.goal.take(80))
        audit(AuditEvent(AuditEventType.RUN_STARTED, runId, detail = "agentId=${config.agentId} mode=AGENTIC"))

        heartbeat.start(runId)
        val budget = ContextBudgetManager(config.maxInputTokens)
        val conversation = mutableListOf(LlmMessage(LlmMessage.Role.user, config.goal))
        val results = mutableListOf<ToolResult>()

        val tools = dispatcher.registeredTools().map { name -> llmToolFor(name) }

        try {
            var iterations = 0

            while (iterations < config.maxIterations) {
                iterations++
                val trimmed = budget.trimToFit(conversation)
                val response = gateway.complete(trimmed, tools, config.systemPrompt)
                budget.recordUsage(response.inputTokens, response.outputTokens)

                log.debug("LLM turn {}: stopReason={} toolCalls={}", iterations, response.stopReason, response.toolCalls.size)
                audit(AuditEvent(AuditEventType.STEP_STARTED, runId, detail = "llmTurn=$iterations stopReason=${response.stopReason}"))

                if (response.stopReason == LlmResponse.StopReason.END_TURN || response.toolCalls.isEmpty()) {
                    // LLM is done
                    if (response.content != null) {
                        conversation.add(LlmMessage(LlmMessage.Role.assistant, response.content))
                    }
                    stateStore.updateStatus(runId, RunStatus.COMPLETED)
                    audit(AuditEvent(AuditEventType.RUN_COMPLETED, runId, detail = "iterations=$iterations"))
                    log.info("Agentic run {} COMPLETED after {} iterations", runId, iterations)
                    return RunOutcome(runId, RunStatus.COMPLETED, results, finalMessage = response.content)
                }

                // Append the assistant message with tool calls
                conversation.add(LlmMessage(LlmMessage.Role.assistant, response.content, response.toolCalls))

                // Execute each tool call
                for (tc in response.toolCalls) {
                    val stepId = UUID.randomUUID().toString()
                    audit(AuditEvent(AuditEventType.STEP_STARTED, runId, stepId = stepId, toolName = tc.toolName, argsJson = tc.argumentsJson.take(500)))

                    val riskScore = riskScorer.score(tc.toolName, tc.argumentsJson, false)
                    if (riskScorer.requiresApproval(riskScore, config.riskThreshold)) {
                        val approval = ApprovalRequest(
                            approvalId = UUID.randomUUID().toString(),
                            runId = runId,
                            stepId = stepId,
                            riskScore = riskScore,
                            impactPreview = "tool=${tc.toolName} args=${tc.argumentsJson.take(200)}",
                        )
                        approvalGate.request(approval)
                        stateStore.updateStatus(runId, RunStatus.WAITING_APPROVAL)
                        audit(AuditEvent(AuditEventType.APPROVAL_REQUESTED, runId, stepId = stepId, detail = "approvalId=${approval.approvalId} score=$riskScore"))
                        log.warn("Agentic run {} paused — approval required for {} score={}", runId, tc.toolName, riskScore)
                        return RunOutcome(runId, RunStatus.WAITING_APPROVAL, results)
                    }

                    val call = ToolCall(callId = tc.id, toolName = tc.toolName, argumentsJson = tc.argumentsJson)
                    val contract = contractForTool(tc.toolName)
                    val result = executeWithRetry(call, contract, ToolDispatcher.policyFor(contract), dispatcher)
                    results.add(result)

                    val toolContent = result.outputJson ?: result.errorDetail ?: "no output"
                    conversation.add(LlmMessage(LlmMessage.Role.tool, toolContent, toolCallId = tc.id))

                    if (result.success) {
                        audit(AuditEvent(AuditEventType.STEP_COMPLETED, runId, stepId = stepId, toolName = tc.toolName, resultJson = result.outputJson?.take(500)))
                    } else {
                        audit(AuditEvent(AuditEventType.STEP_FAILED, runId, stepId = stepId, toolName = tc.toolName, errorCode = result.errorCode?.name, detail = result.errorDetail))
                        log.warn("Tool {} failed in agentic run — LLM will see the error and decide", tc.toolName)
                    }
                }

                stateStore.updateCheckpoint(runId, Checkpoint(stepIndex = iterations, contextSummary = "agentic iteration $iterations", artifactRefs = emptyList()))
            }

            // Exceeded max iterations
            log.warn("Agentic run {} exceeded maxIterations={}", runId, config.maxIterations)
            stateStore.updateStatus(runId, RunStatus.FAILED)
            audit(AuditEvent(AuditEventType.RUN_FAILED, runId, detail = "maxIterations=${config.maxIterations} exceeded"))
            return RunOutcome(runId, RunStatus.FAILED, results)

        } catch (e: Exception) {
            log.error("Agentic run {} CRASHED: {}", runId, e.message, e)
            stateStore.updateStatus(runId, RunStatus.CRASHED)
            audit(AuditEvent(AuditEventType.RUN_CRASHED, runId, detail = e.message))
            return RunOutcome(runId, RunStatus.CRASHED, results)
        } finally {
            heartbeat.stop()
        }
    }

    private fun llmToolFor(toolName: String): LlmTool = LlmTool(
        name = toolName,
        description = toolDescriptions[toolName] ?: toolName,
        parametersJson = toolSchemas[toolName] ?: GENERIC_SCHEMA,
    )

    private fun contractForTool(toolName: String): ToolContract = ToolContract(
        name = toolName,
        version = "1.0",
        description = toolDescriptions[toolName] ?: toolName,
        riskLevel = when {
            toolName.contains("push") || toolName.contains("delete") -> dev.agentshell.domain.RiskLevel.HIGH
            toolName.startsWith("mcp_") -> dev.agentshell.domain.RiskLevel.MEDIUM
            else -> dev.agentshell.domain.RiskLevel.MEDIUM
        },
        sandboxPolicy = "shell_default",
    )

    companion object {
        const val DEFAULT_SYSTEM_PROMPT = """You are AgentShell, an autonomous software engineering agent.
You have access to shell execution, file operations, and git tools.
Be methodical: think through the task, use tools step by step, verify results.
When the task is complete, respond with a brief summary of what you did."""

        private val toolDescriptions = mapOf(
            "shell_exec" to "Execute a shell command. Args: command (string)",
            "file_write" to "Read, write, or append to files. Args: operation (read|write|append), path, content (for write/append)",
            "git_exec" to "Run a git subcommand. Args: subcommand (status|add|commit|diff|log|push), args",
        )

        private val toolSchemas = mapOf(
            "shell_exec" to """{"type":"object","properties":{"command":{"type":"string","description":"Shell command to run"}},"required":["command"]}""",
            "file_write" to """{"type":"object","properties":{"operation":{"type":"string","enum":["read","write","append"]},"path":{"type":"string"},"content":{"type":"string"}},"required":["operation","path"]}""",
            "git_exec" to """{"type":"object","properties":{"subcommand":{"type":"string"},"args":{"type":"string"}},"required":["subcommand"]}""",
        )

        private const val GENERIC_SCHEMA = """{"type":"object","properties":{},"additionalProperties":true}"""
    }

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
