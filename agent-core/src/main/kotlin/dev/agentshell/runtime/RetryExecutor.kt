package dev.agentshell.runtime

import dev.agentshell.domain.SandboxPolicy
import dev.agentshell.domain.ToolCall
import dev.agentshell.domain.ToolContract
import dev.agentshell.domain.ToolResult
import dev.agentshell.executor.ToolDispatcher
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("dev.agentshell.runtime.RetryExecutor")

/**
 * Executes a ToolCall with retry logic for retryable errors.
 * Non-retryable errors are returned immediately.
 */
fun executeWithRetry(
    call: ToolCall,
    contract: ToolContract,
    policy: SandboxPolicy,
    dispatcher: ToolDispatcher,
    maxAttempts: Int = 3,
    backoffMs: Long = 500,
): ToolResult {
    var lastResult: ToolResult? = null
    repeat(maxAttempts) { attempt ->
        val result = dispatcher.dispatch(call, contract, policy)
        if (result.success) return result
        lastResult = result
        val retryable = result.errorCode?.retryable ?: false
        if (!retryable) {
            log.warn(
                "Non-retryable error on callId={} tool={} errorCode={}",
                call.callId, call.toolName, result.errorCode,
            )
            return result
        }
        if (attempt < maxAttempts - 1) {
            val delay = backoffMs * (1L shl attempt) // exponential: 500, 1000, 2000
            log.warn(
                "Retryable error on callId={} attempt={}/{} errorCode={} retrying in {}ms",
                call.callId, attempt + 1, maxAttempts, result.errorCode, delay,
            )
            Thread.sleep(delay)
        }
    }
    log.error("All {} attempts exhausted for callId={} tool={}", maxAttempts, call.callId, call.toolName)
    return lastResult!!
}
