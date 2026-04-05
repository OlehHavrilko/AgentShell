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
import java.util.concurrent.TimeUnit

/**
 * Executes shell commands.
 * Expected args: { "command": "...", "workingDir": "..." (optional) }
 */
class ShellToolExecutor : ToolExecutor {

    override val toolName = "shell_exec"

    private val json = Json { ignoreUnknownKeys = true }

    override fun execute(call: ToolCall, contract: ToolContract, policy: SandboxPolicy): ToolResult {
        val args = json.parseToJsonElement(call.argumentsJson).jsonObject
        val command = args["command"]?.jsonPrimitive?.content
            ?: return ToolResult(
                callId = call.callId,
                success = false,
                errorCode = ErrorCode.ERR_SCHEMA_VIOLATION,
                errorDetail = "Missing required field: command",
            )

        val workingDir = args["workingDir"]?.jsonPrimitive?.content
            ?: System.getProperty("user.dir")

        SandboxGuard.checkPath(call, policy, workingDir)?.let { return it }

        return runProcess(call, policy, listOf("sh", "-c", command), File(workingDir))
    }

    private fun runProcess(
        call: ToolCall,
        policy: SandboxPolicy,
        cmd: List<String>,
        workDir: File,
    ): ToolResult {
        return try {
            val process = ProcessBuilder(cmd)
                .directory(workDir)
                .redirectErrorStream(true)
                .start()

            val finished = process.waitFor(policy.wallTimeMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                return ToolResult(
                    callId = call.callId,
                    success = false,
                    errorCode = ErrorCode.ERR_TIMEOUT,
                    errorDetail = "Command exceeded wall time limit of ${policy.wallTimeMs}ms",
                )
            }

            val rawOutput = process.inputStream.bufferedReader().readText()
            val output = SandboxGuard.limitOutput(rawOutput, policy)
            val exitCode = process.exitValue()

            if (exitCode != 0) {
                ToolResult(
                    callId = call.callId,
                    success = false,
                    errorCode = ErrorCode.ERR_SANDBOX_KILLED,
                    errorDetail = "Process exited with code $exitCode",
                    outputJson = buildOutputJson(exitCode, output),
                )
            } else {
                ToolResult(
                    callId = call.callId,
                    success = true,
                    outputJson = buildOutputJson(exitCode, output),
                )
            }
        } catch (e: Exception) {
            ToolResult(
                callId = call.callId,
                success = false,
                errorCode = ErrorCode.ERR_SANDBOX_KILLED,
                errorDetail = e.message,
            )
        }
    }

    private fun buildOutputJson(exitCode: Int, output: String): String {
        val escapedOutput = output
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return """{"exitCode":$exitCode,"output":"$escapedOutput"}"""
    }
}
