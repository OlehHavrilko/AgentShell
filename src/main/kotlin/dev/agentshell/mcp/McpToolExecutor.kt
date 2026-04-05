package dev.agentshell.mcp

import dev.agentshell.domain.ErrorCode
import dev.agentshell.domain.SandboxPolicy
import dev.agentshell.domain.ToolCall
import dev.agentshell.domain.ToolContract
import dev.agentshell.domain.ToolResult
import dev.agentshell.executor.ToolExecutor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory

/**
 * ToolExecutor that delegates execution of a single named tool to an MCP server.
 * One instance is created per MCP tool when [McpClient] connects.
 */
class McpToolExecutor(
    private val client: McpClient,
    private val mcpToolName: String,
) : ToolExecutor {

    private val log = LoggerFactory.getLogger(McpToolExecutor::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    override val toolName: String = "mcp_$mcpToolName"

    override fun execute(call: ToolCall, contract: ToolContract, policy: SandboxPolicy): ToolResult {
        log.info("MCP tool call: {} args={}", mcpToolName, call.argumentsJson.take(120))
        return try {
            val result = client.callTool(mcpToolName, call.argumentsJson)
            val outputJson = buildJsonObject { put("output", JsonPrimitive(result.content)) }.toString()
            if (result.success) {
                ToolResult(callId = call.callId, success = true, outputJson = outputJson)
            } else {
                ToolResult(callId = call.callId, success = false, errorCode = ErrorCode.EXECUTION_FAILED, errorDetail = result.content)
            }
        } catch (e: Exception) {
            log.error("MCP tool execution error: {}", e.message, e)
            ToolResult(callId = call.callId, success = false, errorCode = ErrorCode.EXECUTION_FAILED, errorDetail = e.message)
        }
    }
}
