package com.agentshell.app.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agentshell.app.service.AgentMessage
import com.agentshell.app.service.AgentRuntimeService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(val role: String, val content: String)
data class ApprovalBanner(val approvalId: String, val impactPreview: String)

class ChatViewModel(private val app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("agentshell", Context.MODE_PRIVATE)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _pendingApproval = MutableStateFlow<ApprovalBanner?>(null)
    val pendingApproval = _pendingApproval.asStateFlow()

    private val _selectedProvider = MutableStateFlow(
        prefs.getString("selected_provider", "ollama") ?: "ollama"
    )
    val selectedProvider = _selectedProvider.asStateFlow()

    private val _serviceConnected = MutableStateFlow(false)
    val serviceConnected = _serviceConnected.asStateFlow()

    // ── Service binding ────────────────────────────────────────────────────────
    private var boundService: AgentRuntimeService? = null
    private var eventJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val svc = (binder as AgentRuntimeService.LocalBinder).service
            boundService = svc
            _serviceConnected.value = true
            observeEvents(svc)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            boundService = null
            _serviceConnected.value = false
            eventJob?.cancel()
        }
    }

    init {
        // Connect to already-running service (do not auto-create)
        val intent = Intent(app, AgentRuntimeService::class.java)
        app.bindService(intent, serviceConnection, 0)
    }

    private fun observeEvents(svc: AgentRuntimeService) {
        eventJob?.cancel()
        eventJob = viewModelScope.launch {
            svc.events().collect { msg -> handleIncoming(msg) }
        }
    }

    private fun handleIncoming(msg: AgentMessage) {
        when (msg.type) {
            "chat_response" -> {
                appendMessage("assistant", msg.payload ?: "")
                _isLoading.value = false
            }
            "tool_start" -> appendMessage("tool", "▶ ${msg.payload}")
            "tool_result" -> appendMessage("tool", "  ${msg.payload}")
            "tool_error" -> appendMessage("error", "✗ ${msg.payload}")
            "run_started" -> appendMessage("system", "Run started: ${msg.runId?.takeLast(8)}")
            "run_complete" -> {
                appendMessage("system", "✓ Run ${msg.runId?.takeLast(8)} complete")
                _isLoading.value = false
            }
            "approval_request" -> {
                val parts = msg.payload?.split("|") ?: return
                if (parts.size >= 2) {
                    _pendingApproval.value = ApprovalBanner(parts[0], parts[1])
                }
            }
            "error" -> {
                appendMessage("error", "Error: ${msg.payload}")
                _isLoading.value = false
            }
        }
    }

    private fun appendMessage(role: String, content: String) {
        _messages.value = _messages.value + ChatMessage(role, content)
    }

    // ── Public API ─────────────────────────────────────────────────────────────
    fun send(text: String) {
        appendMessage("user", text)
        _isLoading.value = true

        val svc = boundService
        if (svc == null) {
            // Auto-start service in JVM mode and bind
            val startIntent = Intent(app, AgentRuntimeService::class.java).apply {
                action = AgentRuntimeService.ACTION_START_JVM
                putExtra(AgentRuntimeService.EXTRA_GOAL, text)
                putExtra(AgentRuntimeService.EXTRA_PROVIDER, _selectedProvider.value)
            }
            app.startForegroundService(startIntent)
            // Also (re-)bind to catch future events
            app.bindService(
                Intent(app, AgentRuntimeService::class.java),
                serviceConnection,
                0
            )
            return
        }

        // Service already bound — send via JVM runner
        svc.startJvmAgent(goal = text, providerId = _selectedProvider.value)
    }

    fun approve(approvalId: String) {
        _pendingApproval.value = null
        boundService?.send(AgentMessage(type = "approve", payload = approvalId))
    }

    fun reject(approvalId: String) {
        _pendingApproval.value = null
        boundService?.send(AgentMessage(type = "reject", payload = approvalId))
    }

    fun selectProvider(id: String) {
        prefs.edit().putString("selected_provider", id).apply()
        _selectedProvider.value = id
    }

    override fun onCleared() {
        eventJob?.cancel()
        runCatching { app.unbindService(serviceConnection) }
        super.onCleared()
    }
}
