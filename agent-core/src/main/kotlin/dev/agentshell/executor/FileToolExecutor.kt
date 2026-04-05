package dev.agentshell.executor

import dev.agentshell.domain.ErrorCode
import dev.agentshell.domain.SandboxPolicy
import dev.agentshell.domain.ToolCall
import dev.agentshell.domain.ToolContract
import dev.agentshell.domain.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Executes file operations: read, write, append.
 * Expected args:
 *   read:   { "operation": "read",   "path": "..." }
 *   write:  { "operation": "write",  "path": "...", "content": "..." }
 *   append: { "operation": "append", "path": "...", "content": "..." }
 */
class FileToolExecutor : ToolExecutor {

    override val toolName = "file_write"

    private val json = Json { ignoreUnknownKeys = true }

    override fun execute(call: ToolCall, contract: ToolContract, policy: SandboxPolicy): ToolResult {
        val args = json.parseToJsonElement(call.argumentsJson).jsonObject
        val operation = args["operation"]?.jsonPrimitive?.content
            ?: return ToolResult(
                callId = call.callId,
                success = false,
                errorCode = ErrorCode.ERR_SCHEMA_VIOLATION,
                errorDetail = "Missing required field: operation (read|write|append)",
            )
        val path = args["path"]?.jsonPrimitive?.content
            ?: return ToolResult(
                callId = call.callId,
                success = false,
                errorCode = ErrorCode.ERR_SCHEMA_VIOLATION,
                errorDetail = "Missing required field: path",
            )

        SandboxGuard.checkPath(call, policy, path)?.let { return it }

        return when (operation) {
            "read" -> readFile(call, policy, path)
            "write" -> {
                val content = args["content"]?.jsonPrimitive?.content
                    ?: return ToolResult(
                        callId = call.callId,
                        success = false,
                        errorCode = ErrorCode.ERR_SCHEMA_VIOLATION,
                        errorDetail = "Missing required field: content",
                    )
                writeFile(call, path, content, append = false)
            }
            "append" -> {
                val content = args["content"]?.jsonPrimitive?.content
                    ?: return ToolResult(
                        callId = call.callId,
                        success = false,
                        errorCode = ErrorCode.ERR_SCHEMA_VIOLATION,
                        errorDetail = "Missing required field: content",
                    )
                writeFile(call, path, content, append = true)
            }
            else -> ToolResult(
                callId = call.callId,
                success = false,
                errorCode = ErrorCode.ERR_SCHEMA_VIOLATION,
                errorDetail = "Unknown operation '$operation'. Supported: read, write, append",
            )
        }
    }

    private fun readFile(call: ToolCall, policy: SandboxPolicy, path: String): ToolResult {
        return try {
            val file = File(path)
            if (!file.exists()) {
                return ToolResult(
                    callId = call.callId,
                    success = false,
                    errorCode = ErrorCode.ERR_SCHEMA_VIOLATION,
                    errorDetail = "File not found: $path",
                )
            }
            val raw = file.readText(Charsets.UTF_8)
            val content = SandboxGuard.limitOutput(raw, policy)
            ToolResult(
                callId = call.callId,
                success = true,
                outputJson = """{"path":${jsonString(path)},"content":${jsonString(content)},"bytes":${raw.toByteArray().size}}""",
            )
        } catch (e: Exception) {
            ToolResult(
                callId = call.callId,
                success = false,
                errorCode = ErrorCode.ERR_SANDBOX_KILLED,
                errorDetail = "Read failed: ${e.message}",
            )
        }
    }

    private fun writeFile(call: ToolCall, path: String, content: String, append: Boolean): ToolResult {
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            if (append) file.appendText(content, Charsets.UTF_8)
            else file.writeText(content, Charsets.UTF_8)
            ToolResult(
                callId = call.callId,
                success = true,
                outputJson = """{"path":${jsonString(path)},"bytes":${content.toByteArray().size},"append":$append}""",
            )
        } catch (e: Exception) {
            ToolResult(
                callId = call.callId,
                success = false,
                errorCode = ErrorCode.ERR_SANDBOX_KILLED,
                errorDetail = "Write failed: ${e.message}",
            )
        }
    }

    private fun jsonString(s: String): String {
        val escaped = s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
        return "\"$escaped\""
    }
}
