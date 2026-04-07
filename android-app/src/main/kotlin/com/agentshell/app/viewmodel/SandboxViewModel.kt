package com.agentshell.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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
        // Accumulate up to 500 lines; older lines are dropped to avoid memory pressure.
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
        // Echo the command to terminal output
        _terminalLines.value = _terminalLines.value + "$ $command"
        proot.exec(command)
    }

    override fun onCleared() {
        viewModelScope.launch { manager.stop() }
        super.onCleared()
    }
}
