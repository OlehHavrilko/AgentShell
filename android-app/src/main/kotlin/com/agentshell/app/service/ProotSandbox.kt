package com.agentshell.app.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

/**
 * Manages the Proot-based Alpine Linux sandbox.
 *
 * Prerequisites:
 *  - assets/rootfs.tar.gz  (built externally with scripts/build_rootfs.sh)
 *  - jniLibs/{abi}/libproot.so  (compiled via scripts/build_proot.sh)
 *
 * Usage modes:
 *  - Terminal mode ([outputToFlow]=true): subscribe to [outputFlow] for raw output lines.
 *  - MCP mode: call [openMcpChannel] to get a JSON-line channel.
 *    These are mutually exclusive — do not mix in the same session.
 */
class ProotSandbox(private val context: Context) {

    private var process: Process? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Emits stdout lines when running in terminal (outputToFlow) mode.
    private val _output = MutableSharedFlow<String>(extraBufferCapacity = 2_000)
    val outputFlow: Flow<String> = _output.asSharedFlow()

    private val rootfsDir get() = File(context.filesDir, "prootfs")
    private val prootBin get() = File(context.applicationInfo.nativeLibraryDir, "libproot.so")

    val isRunning: Boolean get() = process?.isAlive == true

    /**
     * Extract rootfs (if needed) and launch the proot process.
     *
     * @param outputToFlow When true, stdout is forwarded to [outputFlow].
     *                     When false, the caller should obtain [openMcpChannel] instead.
     */
    suspend fun start(outputToFlow: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!rootfsDir.exists()) extractRootfs()
            launchProot()
            if (outputToFlow) startOutputReader()
        }
    }

    private fun extractRootfs() {
        Log.i(TAG, "Extracting Alpine rootfs…")
        rootfsDir.mkdirs()
        context.assets.open("rootfs.tar.gz").use { assetStream ->
            val tarGz = File(context.cacheDir, "rootfs.tar.gz")
            tarGz.outputStream().use { assetStream.copyTo(it) }
            Runtime.getRuntime().exec(
                arrayOf("tar", "-xzf", tarGz.absolutePath, "-C", rootfsDir.absolutePath)
            ).waitFor()
            tarGz.delete()
        }
        Log.i(TAG, "Rootfs extracted to ${rootfsDir.absolutePath}")
    }

    private fun launchProot() {
        if (!prootBin.exists()) error("libproot.so not found at ${prootBin.absolutePath}")
        val cmd = arrayOf(
            prootBin.absolutePath,
            "--rootfs=${rootfsDir.absolutePath}",
            "--bind=/dev", "--bind=/proc", "--bind=/sys",
            "--change-id=0:0",
            "/bin/sh", "-c", "exec /bin/sh"
        )
        process = ProcessBuilder(*cmd)
            .redirectErrorStream(true)
            .directory(rootfsDir)
            .start()
        Log.i(TAG, "Proot sandbox started")
    }

    /** Forward all stdout lines to [outputFlow]. Must NOT be called when using [openMcpChannel]. */
    private fun startOutputReader() {
        val p = process ?: return
        scope.launch {
            try {
                p.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line -> _output.tryEmit(line) }
                }
            } catch (_: Exception) {
                _output.tryEmit("[sandbox process exited]")
            }
        }
    }

    /**
     * Creates a [McpChannel] backed by the proot process streams.
     * Only valid when [outputToFlow] was NOT used. The caller owns the channel lifecycle.
     */
    fun openMcpChannel(): McpChannel {
        val p = process ?: error("Sandbox not started — call start() first")
        return McpChannel(p.inputStream, p.outputStream)
    }

    /** Send a shell command to stdin of the running sandbox. */
    suspend fun exec(command: String) = withContext(Dispatchers.IO) {
        val p = process ?: error("Sandbox not running")
        p.outputStream.write("$command\n".toByteArray())
        p.outputStream.flush()
    }

    fun stop() {
        scope.coroutineContext.cancelChildren()
        process?.destroy()
        process = null
        Log.i(TAG, "Proot sandbox stopped")
    }

    companion object { private const val TAG = "ProotSandbox" }
}
