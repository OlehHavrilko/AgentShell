package dev.agentshell.executor

import dev.agentshell.domain.SandboxPolicy
import dev.agentshell.domain.ToolCall
import dev.agentshell.domain.ToolContract
import dev.agentshell.domain.ToolResult

interface ToolExecutor {
    val toolName: String
    fun execute(call: ToolCall, contract: ToolContract, policy: SandboxPolicy): ToolResult
}
