package com.agentshell.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agentshell.app.service.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SandboxViewModel(app: Application) : AndroidViewModel(app) {

    private val proot = ProotSandbox(app)
    private val termux = TermuxConnector(app)
    private val manager = SandboxManager(proot, termux)

    val sandboxState: StateFlow<SandboxState> = manager.state

    private val _terminalLines = MutableStateFlow<List<String>>(emptyList())
    val terminalLines: StateFlow<List<String>> = _terminalLines.asStateFlow()

    private val _currentMode = MutableStateFlow(SandboxMode.PROOT)
    val currentMode: StateFlow<SandboxMode> = _currentMode.asStateFlow()

    val isTermuxAvailable: Boolean get() = termux.isTermuxInstalled

    init {
        // Collect terminal output from the proot sandbox directly.
        // Lines are accumulated and capped at 500 to avoid memory pressure.
        viewModelScope.launch {
            proot.outputFlow.collect { line ->
                _terminalLines.value = (_terminalLines.value + line).takeLast(500)
            }
        }
    }

    fun start() = viewModelScope.launch { manager.start(_currentMode.value) }

    fun stop() = viewModelScope.launch { manager.stop() }

    fun restart() = viewModelScope.launch { manager.restart(_currentMode.value) }

    fun clearTerminal() { _terminalLines.value = emptyList() }

    fun toggleMode(useProot: Boolean) {
        _currentMode.value = if (useProot) SandboxMode.PROOT else SandboxMode.TERMUX
    }

    fun sendInput(command: String) = viewModelScope.launch {
        _terminalLines.value = _terminalLines.value + "$ $command"
        when (manager.state.value) {
            is SandboxState.Running -> {
                val state = manager.state.value as SandboxState.Running
                when (state.mode) {
                    SandboxMode.PROOT -> proot.exec(command)
                    SandboxMode.TERMUX -> {
                        // Termux doesn't support exec via connector, send via MCP channel
                        manager.activeChannel?.send(AgentMessage(type = "command", payload = command))
                    }
                }
            }
            else -> {
                _terminalLines.value = _terminalLines.value + "[error] Sandbox not running"
            }
        }
    }

    override fun onCleared() {
        viewModelScope.launch { manager.stop() }
        super.onCleared()
    }

    // ── ViewModel Factory ──────────────────────────────────────────────────────
    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SandboxViewModel(app) as T
    }
}
