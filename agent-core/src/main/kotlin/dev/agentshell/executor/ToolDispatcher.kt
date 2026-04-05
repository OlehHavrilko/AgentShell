package dev.agentshell.executor

import dev.agentshell.domain.ErrorCode
import dev.agentshell.domain.RiskLevel
import dev.agentshell.domain.SandboxDefaults
import dev.agentshell.domain.SandboxPolicy
import dev.agentshell.domain.ToolCall
import dev.agentshell.domain.ToolContract
import dev.agentshell.domain.ToolResult

/**
 * Routes a ToolCall to the correct ToolExecutor after schema validation.
 * Apply idempotency and risk checks before calling dispatch().
 */
class ToolDispatcher(executors: List<ToolExecutor> = defaultExecutors()) {

    private val registry: MutableMap<String, ToolExecutor> = executors.associateBy { it.toolName }.toMutableMap()

    /** Dynamically register an executor (e.g. from MCP). */
    fun register(executor: ToolExecutor) {
        registry[executor.toolName] = executor
    }

    /** Returns all registered tool names. */
    fun registeredTools(): Set<String> = registry.keys

    fun dispatch(call: ToolCall, contract: ToolContract, policy: SandboxPolicy = policyFor(contract)): ToolResult {
        SchemaValidator.validate(call)?.let { return it }

        val executor = registry[contract.name]
            ?: return ToolResult(
                callId = call.callId,
                success = false,
                errorCode = ErrorCode.ERR_PLUGIN_INCOMPATIBLE,
                errorDetail = "No executor registered for tool '${contract.name}'",
            )

        return try {
            executor.execute(call, contract, policy)
        } catch (e: Exception) {
            ToolResult(
                callId = call.callId,
                success = false,
                errorCode = ErrorCode.ERR_SANDBOX_KILLED,
                errorDetail = "Executor threw unexpected exception: ${e.message}",
            )
        }
    }

    companion object {
        fun defaultExecutors(): List<ToolExecutor> = listOf(
            ShellToolExecutor(),
            FileToolExecutor(),
            GitToolExecutor(),
        )

        fun policyFor(contract: ToolContract): SandboxPolicy {
            return when {
                contract.name == "git_push" || contract.riskLevel == RiskLevel.HIGH -> SandboxDefaults.gitDefault
                else -> SandboxDefaults.shellDefault
            }
        }
    }
}
