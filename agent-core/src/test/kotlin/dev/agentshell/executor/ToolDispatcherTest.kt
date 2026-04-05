package dev.agentshell.executor

import dev.agentshell.domain.ErrorCode
import dev.agentshell.domain.RiskLevel
import dev.agentshell.domain.SandboxDefaults
import dev.agentshell.domain.ToolCall
import dev.agentshell.domain.ToolContract
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ToolDispatcherTest {

    private val dispatcher = ToolDispatcher()

    private fun shellContract() = ToolContract(
        name = "shell_exec", version = "1.0", description = "shell",
        riskLevel = RiskLevel.MEDIUM, sandboxPolicy = "shell_default",
    )

    private fun fileContract() = ToolContract(
        name = "file_write", version = "1.0", description = "file",
        riskLevel = RiskLevel.MEDIUM, sandboxPolicy = "shell_default",
    )

    private fun call(id: String, args: String) = ToolCall(callId = id, toolName = "shell_exec", argumentsJson = args)

    // --- SchemaValidator ---

    @Test fun `invalid json fails schema validation`() {
        val result = dispatcher.dispatch(
            call("c1", "not-json"),
            shellContract(),
            SandboxDefaults.shellDefault,
        )
        assertFalse(result.success)
        assertEquals(ErrorCode.ERR_SCHEMA_VIOLATION, result.errorCode)
    }

    @Test fun `blank args fails schema validation`() {
        val result = dispatcher.dispatch(
            call("c2", "   "),
            shellContract(),
            SandboxDefaults.shellDefault,
        )
        assertFalse(result.success)
        assertEquals(ErrorCode.ERR_SCHEMA_VIOLATION, result.errorCode)
    }

    @Test fun `json array fails schema validation`() {
        val result = dispatcher.dispatch(
            call("c3", "[1,2,3]"),
            shellContract(),
            SandboxDefaults.shellDefault,
        )
        assertFalse(result.success)
        assertEquals(ErrorCode.ERR_SCHEMA_VIOLATION, result.errorCode)
    }

    // --- ShellToolExecutor ---

    @Test fun `shell echo succeeds`() {
        val policy = SandboxDefaults.shellDefault.copy(allowedPaths = emptyList())
        val result = dispatcher.dispatch(
            ToolCall("c10", "shell_exec", """{"command":"echo hello"}"""),
            shellContract(),
            policy,
        )
        assertTrue(result.success)
        assertNotNull(result.outputJson)
        assertTrue(result.outputJson!!.contains("hello"))
    }

    @Test fun `shell missing command field fails`() {
        val policy = SandboxDefaults.shellDefault.copy(allowedPaths = emptyList())
        val result = dispatcher.dispatch(
            ToolCall("c11", "shell_exec", """{"workingDir":"/tmp"}"""),
            shellContract(),
            policy,
        )
        assertFalse(result.success)
        assertEquals(ErrorCode.ERR_SCHEMA_VIOLATION, result.errorCode)
    }

    @Test fun `shell non-zero exit fails result`() {
        val policy = SandboxDefaults.shellDefault.copy(allowedPaths = emptyList())
        val result = dispatcher.dispatch(
            ToolCall("c12", "shell_exec", """{"command":"exit 1"}"""),
            shellContract(),
            policy,
        )
        assertFalse(result.success)
        assertEquals(ErrorCode.ERR_SANDBOX_KILLED, result.errorCode)
    }

    // --- FileToolExecutor ---

    @Test fun `file write then read succeeds`(@TempDir tmpDir: File) {
        val path = File(tmpDir, "test.txt").absolutePath
        val policy = SandboxDefaults.shellDefault.copy(allowedPaths = listOf(tmpDir.absolutePath + "/**"))

        val writeResult = dispatcher.dispatch(
            ToolCall("c20", "file_write", """{"operation":"write","path":"$path","content":"hello world"}"""),
            fileContract(),
            policy,
        )
        assertTrue(writeResult.success)

        val readResult = dispatcher.dispatch(
            ToolCall("c21", "file_write", """{"operation":"read","path":"$path"}"""),
            fileContract(),
            policy,
        )
        assertTrue(readResult.success)
        assertTrue(readResult.outputJson!!.contains("hello world"))
    }

    @Test fun `file read outside sandbox fails`(@TempDir tmpDir: File) {
        val policy = SandboxDefaults.shellDefault.copy(allowedPaths = listOf(tmpDir.absolutePath + "/**"))
        val result = dispatcher.dispatch(
            ToolCall("c22", "file_write", """{"operation":"read","path":"/etc/passwd"}"""),
            fileContract(),
            policy,
        )
        assertFalse(result.success)
        assertEquals(ErrorCode.ERR_PATH_VIOLATION, result.errorCode)
    }

    @Test fun `file unknown operation fails`(@TempDir tmpDir: File) {
        val path = File(tmpDir, "x.txt").absolutePath
        val policy = SandboxDefaults.shellDefault.copy(allowedPaths = listOf(tmpDir.absolutePath + "/**"))
        val result = dispatcher.dispatch(
            ToolCall("c23", "file_write", """{"operation":"delete","path":"$path"}"""),
            fileContract(),
            policy,
        )
        assertFalse(result.success)
        assertEquals(ErrorCode.ERR_SCHEMA_VIOLATION, result.errorCode)
    }

    // --- Unknown tool ---

    @Test fun `unknown tool name returns plugin incompatible error`() {
        val contract = ToolContract(
            name = "unknown_tool", version = "1.0", description = "?",
            riskLevel = RiskLevel.LOW, sandboxPolicy = "shell_default",
        )
        val result = dispatcher.dispatch(
            ToolCall("c30", "unknown_tool", """{"x":1}"""),
            contract,
            SandboxDefaults.shellDefault,
        )
        assertFalse(result.success)
        assertEquals(ErrorCode.ERR_PLUGIN_INCOMPATIBLE, result.errorCode)
    }
}
