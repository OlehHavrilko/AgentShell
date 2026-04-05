package com.agentshell.app.service

import android.content.Context
import android.content.Intent
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "TermuxConnector"
private const val TERMUX_PACKAGE = "com.termux"
private const val SOCKET_NAME = "agentshell_socket"

class TermuxConnector(private val context: Context) {

    private var socket: LocalSocket? = null

    val isTermuxInstalled: Boolean
        get() = runCatching {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        }.getOrDefault(false)

    /** Copy agent-core JAR and startup script to Termux home, then launch server. */
    suspend fun install() = withContext(Dispatchers.IO) {
        val termuxHome = File("/data/data/$TERMUX_PACKAGE/files/home/agentshell")
        termuxHome.mkdirs()

        // Copy server script from assets
        context.assets.open("termux/agent_shell_server.sh").use { inp ->
            File(termuxHome, "agent_shell_server.sh").outputStream().use { inp.copyTo(it) }
        }
        File(termuxHome, "agent_shell_server.sh").setExecutable(true)
        Log.i(TAG, "Installed agent_shell_server.sh to $termuxHome")
    }

    /** Start the agent server inside Termux via am broadcast. */
    fun startServer() {
        val intent = Intent("com.termux.app.RUN_COMMAND").apply {
            setPackage(TERMUX_PACKAGE)
            putExtra("com.termux.app.RUN_COMMAND_PATH", "/data/data/$TERMUX_PACKAGE/files/home/agentshell/agent_shell_server.sh")
            putExtra("com.termux.app.RUN_COMMAND_BACKGROUND", true)
        }
        context.sendBroadcast(intent)
        Log.i(TAG, "Sent RUN_COMMAND to Termux")
    }

    /** Connect to the Unix-domain socket exposed by the agent server. */
    suspend fun connect(): McpChannel = withContext(Dispatchers.IO) {
        val localSocket = LocalSocket()
        localSocket.connect(LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.FILESYSTEM))
        socket = localSocket
        McpChannel(localSocket.inputStream, localSocket.outputStream)
    }

    fun disconnect() {
        runCatching { socket?.close() }
        socket = null
    }
}
