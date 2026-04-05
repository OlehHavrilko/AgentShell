package com.agentshell.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.agentshell.app.AgentShellApp
import com.agentshell.app.R
import com.agentshell.app.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

private const val TAG = "AgentRuntimeService"
private const val NOTIFICATION_ID = 1

class AgentRuntimeService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _events = MutableSharedFlow<AgentMessage>(extraBufferCapacity = 200)

    private var prootSandbox: ProotSandbox? = null
    private var termuxConnector: TermuxConnector? = null
    private var mcpChannel: McpChannel? = null

    inner class LocalBinder : Binder() {
        val service get() = this@AgentRuntimeService
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification("Agent runtime starting…"))
        Log.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_EMBEDDED -> startEmbedded()
            ACTION_START_TERMUX -> startExternal()
            ACTION_STOP -> stopSelf()
        }
        return START_STICKY
    }

    private fun startEmbedded() {
        val sandbox = ProotSandbox(this).also { prootSandbox = it }
        scope.launch {
            sandbox.start()
                .onSuccess {
                    val ch = McpChannel(sandbox.process!!.inputStream, sandbox.process!!.outputStream)
                    mcpChannel = ch
                    forwardEvents(ch)
                    updateNotification("Alpine sandbox running")
                }
                .onFailure { Log.e(TAG, "Failed to start sandbox", it) }
        }
    }

    private fun startExternal() {
        val connector = TermuxConnector(this).also { termuxConnector = it }
        scope.launch {
            runCatching {
                connector.install()
                connector.startServer()
                delay(2_000) // wait for server to start
                val ch = connector.connect()
                mcpChannel = ch
                forwardEvents(ch)
                updateNotification("Termux agent running")
            }.onFailure { Log.e(TAG, "Termux connection failed", it) }
        }
    }

    private fun forwardEvents(channel: McpChannel) = scope.launch {
        channel.receive().collect { msg -> _events.tryEmit(msg) }
    }

    fun send(msg: AgentMessage) = scope.launch { mcpChannel?.send(msg) }

    fun events(): Flow<AgentMessage> = _events.asSharedFlow()

    fun stopRuntime() {
        prootSandbox?.stop()
        termuxConnector?.disconnect()
        mcpChannel?.close()
        mcpChannel = null
        updateNotification("Agent runtime stopped")
    }

    override fun onDestroy() {
        stopRuntime()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, AgentShellApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("AgentShell")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(android.app.NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_START_EMBEDDED = "com.agentshell.action.START_EMBEDDED"
        const val ACTION_START_TERMUX = "com.agentshell.action.START_TERMUX"
        const val ACTION_STOP = "com.agentshell.action.STOP"
    }
}

// Extension to expose process from ProotSandbox for McpChannel wiring
val ProotSandbox.process: Process? get() {
    return try {
        val field = this.javaClass.getDeclaredField("process")
        field.isAccessible = true
        field.get(this) as? Process
    } catch (e: Exception) { null }
}
