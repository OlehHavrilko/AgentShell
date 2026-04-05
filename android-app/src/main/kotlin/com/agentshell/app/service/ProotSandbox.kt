package com.agentshell.app.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Manages the Proot-based Alpine Linux sandbox.
 *
 * Prerequisites:
 *  - assets/rootfs.tar.gz  (built externally with build_rootfs.sh)
 *  - jniLibs/{abi}/libproot.so  (prebuilt proot binaries)
 */
class ProotSandbox(private val context: Context) {

    private var process: Process? = null
    private val rootfsDir get() = File(context.filesDir, "prootfs")
    private val prootBin get() = File(context.applicationInfo.nativeLibraryDir, "libproot.so")

    val isRunning: Boolean get() = process?.isAlive == true

    /** Extract rootfs from assets if not already done, then start proot. */
    suspend fun start(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!rootfsDir.exists()) extractRootfs()
            launchProot()
        }
    }

    private fun extractRootfs() {
        Log.i(TAG, "Extracting Alpine rootfs…")
        rootfsDir.mkdirs()
        context.assets.open("rootfs.tar.gz").use { assetStream ->
            val tarGz = File(context.cacheDir, "rootfs.tar.gz")
            tarGz.outputStream().use { assetStream.copyTo(it) }
            // Use system tar to extract (available on Android 8+)
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
        Log.i(TAG, "Proot sandbox started (PID not directly accessible via ProcessBuilder)")
    }

    /** Send a shell command to the running sandbox. Returns stdout. */
    suspend fun exec(command: String): String = withContext(Dispatchers.IO) {
        val p = process ?: error("Sandbox not running")
        p.outputStream.write("$command\n".toByteArray())
        p.outputStream.flush()
        // For real streaming output use McpChannel or a dedicated reader thread
        ""
    }

    fun stop() {
        process?.destroy()
        process = null
        Log.i(TAG, "Proot sandbox stopped")
    }

    companion object { private const val TAG = "ProotSandbox" }
}
