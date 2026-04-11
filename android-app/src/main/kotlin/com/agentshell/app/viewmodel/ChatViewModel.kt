package com.agentshell.app.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agentshell.app.service.AgentMessage
import com.agentshell.app.service.AgentRuntimeService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(val role: String, val content: String)
data class ApprovalBanner(val approvalId: String, val impactPreview: String)

class ChatViewModel(private val app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("agentshell", Context.MODE_PRIVATE)

    private val _isProviderReady = MutableStateFlow(checkProviderReady(
        prefs.getString("selected_provider", "ollama") ?: "ollama"
    ))
    val isProviderReady = _isProviderReady.asStateFlow()

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
    private var pendingGoal: String? = null // Queue goal until service binds

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val svc = (binder as AgentRuntimeService.LocalBinder).service
            boundService = svc
            _serviceConnected.value = true
            observeEvents(svc)

            // If there's a queued goal from send() before binding, dispatch it now
            pendingGoal?.let { goal ->
                pendingGoal = null
                svc.startJvmAgent(goal = goal, providerId = _selectedProvider.value)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            boundService = null
            _serviceConnected.value = false
            eventJob?.cancel()
        }
    }

    private val prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key?.startsWith("prov_apikey_") == true || key == "selected_provider") {
            _isProviderReady.value = checkProviderReady(_selectedProvider.value)
        }
    }

    init {
        // Auto-create & connect to service so events are captured from app start
        val intent = Intent(app, AgentRuntimeService::class.java)
        app.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
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

    private fun checkProviderReady(id: String): Boolean = when (id) {
        "ollama" -> true
        else -> prefs.getString("prov_apikey_$id", "")?.isNotBlank() == true
    }

    private fun appendMessage(role: String, content: String) {
        _messages.value = _messages.value + ChatMessage(role, content)
    }

    fun clearMessages() {
        _messages.value = emptyList()
        _isLoading.value = false
        _pendingApproval.value = null
    }

    // ── Public API ─────────────────────────────────────────────────────────────
    fun send(text: String) {
        appendMessage("user", text)
        _isLoading.value = true

        val svc = boundService
        if (svc == null) {
            // Service not yet bound — queue the goal and start+bind the service.
            // The onServiceConnected callback will dispatch the queued goal once bound.
            pendingGoal = text
            val startIntent = Intent(app, AgentRuntimeService::class.java).apply {
                action = AgentRuntimeService.ACTION_START_JVM
                putExtra(AgentRuntimeService.EXTRA_GOAL, text)
                putExtra(AgentRuntimeService.EXTRA_PROVIDER, _selectedProvider.value)
            }
            app.startForegroundService(startIntent)
            app.bindService(
                Intent(app, AgentRuntimeService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE,
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
        _isProviderReady.value = checkProviderReady(id)
    }

    override fun onCleared() {
        eventJob?.cancel()
        runCatching { prefs.unregisterOnSharedPreferenceChangeListener(prefListener) }
        runCatching { app.unbindService(serviceConnection) }
        super.onCleared()
    }

    // ── ViewModel Factory ──────────────────────────────────────────────────────
    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatViewModel(app) as T
    }
}
