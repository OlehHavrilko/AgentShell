package com.agentshell.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatMessage(val role: String, val content: String)
data class ApprovalBanner(val approvalId: String, val impactPreview: String)

class ChatViewModel : ViewModel() {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _pendingApproval = MutableStateFlow<ApprovalBanner?>(null)
    val pendingApproval = _pendingApproval.asStateFlow()

    fun send(text: String) {
        _messages.value = _messages.value + ChatMessage("user", text)
        _isLoading.value = true
        viewModelScope.launch {
            // TODO: wire to AgentRuntimeService → McpChannel → agent-core AgentRunner
            // Placeholder response
            kotlinx.coroutines.delay(500)
            _messages.value = _messages.value + ChatMessage("assistant", "Received: $text\n[Connect AgentRuntimeService for real execution]")
            _isLoading.value = false
        }
    }

    fun approve(approvalId: String) {
        _pendingApproval.value = null
        // TODO: send approval via McpChannel
    }

    fun reject(approvalId: String) {
        _pendingApproval.value = null
        // TODO: send rejection via McpChannel
    }
}
