package dev.agentshell.executor

import dev.agentshell.domain.ErrorCode
import dev.agentshell.domain.SandboxPolicy
import dev.agentshell.domain.ToolCall
import dev.agentshell.domain.ToolContract
import dev.agentshell.domain.ToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Executes git commands via subprocess.
 * Expected args: { "subcommand": "status|log|diff|add|commit|push|pull|clone", "args": ["..."], "workingDir": "..." }
 */
class GitToolExecutor : ToolExecutor {

    override val toolName = "git_push"

    private val json = Json { ignoreUnknownKeys = true }

    private val allowedSubcommands = setOf(
        "status", "log", "diff", "add", "commit", "push", "pull", "clone", "fetch", "checkout", "branch",
    )

    override fun execute(call: ToolCall, contract: ToolContract, policy: SandboxPolicy): ToolResult {
        val args = json.parseToJsonElement(call.argumentsJson).jsonObject
        val subcommand = args["subcommand"]?.jsonPrimitive?.content
            ?: return ToolResult(
                callId = call.callId,
                success = false,
                errorCode = ErrorCode.ERR_SCHEMA_VIOLATION,
                errorDetail = "Missing required field: subcommand",
            )

        if (subcommand !in allowedSubcommands) {
            return ToolResult(
                callId = call.callId,
                success = false,
                errorCode = ErrorCode.ERR_SCHEMA_VIOLATION,
                errorDetail = "Unknown git subcommand '$subcommand'. Allowed: $allowedSubcommands",
            )
        }

        val extraArgs = args["args"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val workingDir = args["workingDir"]?.jsonPrimitive?.content ?: System.getProperty("user.dir")

        // Check network for push/pull/clone/fetch
        if (subcommand in setOf("push", "pull", "clone", "fetch")) {
            val remoteUrl = extraArgs.firstOrNull { it.startsWith("http") || it.startsWith("git@") }
            val host = remoteUrl?.let { extractHost(it) } ?: "unknown"
            SandboxGuard.checkNetwork(call, policy, host)?.let { return it }
        }

        SandboxGuard.checkPath(call, policy, workingDir)?.let { return it }

        val cmd = listOf("git", subcommand) + extraArgs
        return runGit(call, policy, cmd, File(workingDir))
    }

    private fun runGit(
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
                    errorDetail = "Git command exceeded wall time limit of ${policy.wallTimeMs}ms",
                )
            }

            val rawOutput = process.inputStream.bufferedReader().readText()
            val output = SandboxGuard.limitOutput(rawOutput, policy)
            val exitCode = process.exitValue()
            val escaped = output.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r")

            if (exitCode != 0) {
                ToolResult(
                    callId = call.callId,
                    success = false,
                    errorCode = ErrorCode.ERR_SANDBOX_KILLED,
                    errorDetail = "git exited with code $exitCode: $output",
                    outputJson = """{"exitCode":$exitCode,"output":"$escaped"}""",
                )
            } else {
                ToolResult(
                    callId = call.callId,
                    success = true,
                    outputJson = """{"exitCode":$exitCode,"output":"$escaped"}""",
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

    private fun extractHost(url: String): String {
        return try {
            when {
                url.startsWith("git@") -> url.removePrefix("git@").substringBefore(":")
                else -> java.net.URI(url).host ?: "unknown"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }
}
