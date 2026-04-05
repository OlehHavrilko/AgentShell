package dev.agentshell.executor

import dev.agentshell.domain.ErrorCode
import dev.agentshell.domain.SandboxPolicy
import dev.agentshell.domain.ToolCall
import dev.agentshell.domain.ToolResult

/**
 * Enforces SandboxPolicy constraints before and after tool execution.
 */
object SandboxGuard {

    /**
     * Checks whether [path] is permitted by [policy]. Returns an error ToolResult if not.
     */
    fun checkPath(call: ToolCall, policy: SandboxPolicy, path: String): ToolResult? {
        if (policy.allowedPaths.isEmpty()) return null
        val resolved = java.nio.file.Paths.get(path).normalize().toAbsolutePath()
        val allowed = policy.allowedPaths.any { pattern ->
            val base = expandEnvVars(pattern.removeSuffix("/**").removeSuffix("/*"))
            resolved.startsWith(java.nio.file.Paths.get(base).normalize().toAbsolutePath())
        }
        if (!allowed) {
            return ToolResult(
                callId = call.callId,
                success = false,
                errorCode = ErrorCode.ERR_PATH_VIOLATION,
                errorDetail = "Path '$path' not in allowed sandbox paths: ${policy.allowedPaths}",
            )
        }
        return null
    }

    /**
     * Checks whether network access to [host] is permitted by [policy]. Returns an error ToolResult if not.
     */
    fun checkNetwork(call: ToolCall, policy: SandboxPolicy, host: String): ToolResult? {
        if (!policy.network) {
            return ToolResult(
                callId = call.callId,
                success = false,
                errorCode = ErrorCode.ERR_SANDBOX_KILLED,
                errorDetail = "Network access is disabled by sandbox policy '${policy.name}'",
            )
        }
        if (policy.allowedHosts.isNotEmpty() && !policy.allowedHosts.any { host.endsWith(it) }) {
            return ToolResult(
                callId = call.callId,
                success = false,
                errorCode = ErrorCode.ERR_SANDBOX_KILLED,
                errorDetail = "Host '$host' is not in allowed hosts: ${policy.allowedHosts}",
            )
        }
        return null
    }

    /** Truncates output to policy.outputBytes limit. */
    fun limitOutput(output: String, policy: SandboxPolicy): String {
        val bytes = output.toByteArray(Charsets.UTF_8)
        if (bytes.size <= policy.outputBytes) return output
        val truncated = String(bytes, 0, policy.outputBytes.toInt(), Charsets.UTF_8)
        return "$truncated\n[OUTPUT TRUNCATED at ${policy.outputBytes} bytes]"
    }

    fun expandEnvVars(path: String): String {
        return path
            .replace("\$REPO_ROOT", System.getProperty("agentshell.repoRoot") ?: System.getProperty("user.dir") ?: ".")
            .replace("\$HOME", System.getProperty("user.home") ?: "~")
    }
}
