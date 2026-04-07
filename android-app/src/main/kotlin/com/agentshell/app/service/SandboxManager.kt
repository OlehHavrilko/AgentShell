package com.agentshell.app.service

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SandboxMode { PROOT, TERMUX }

sealed class SandboxState {
    object Idle : SandboxState()
    object Starting : SandboxState()
    data class Running(val mode: SandboxMode) : SandboxState()
    object Stopping : SandboxState()
    data class Error(val cause: Throwable) : SandboxState()
}

/**
 * Manages sandbox lifecycle and exposes [state] + the active [McpChannel].
 * Coordinates [ProotSandbox] (embedded Alpine) and [TermuxConnector] (external) modes.
 */
class SandboxManager(
    private val proot: ProotSandbox,
    private val termux: TermuxConnector,
) {
    private val _state = MutableStateFlow<SandboxState>(SandboxState.Idle)
    val state: StateFlow<SandboxState> = _state.asStateFlow()

    var activeChannel: McpChannel? = null
        private set

    suspend fun start(mode: SandboxMode) {
        if (_state.value is SandboxState.Running) return
        _state.value = SandboxState.Starting
        runCatching {
            val channel = when (mode) {
                SandboxMode.PROOT -> {
                    proot.start(outputToFlow = true).getOrThrow()
                    proot.openMcpChannel()
                }
                SandboxMode.TERMUX -> {
                    require(termux.isTermuxInstalled) { "Termux is not installed on this device" }
                    termux.install()
                    termux.startServer()
                    delay(2_000)
                    termux.connect()
                }
            }
            activeChannel = channel
            _state.value = SandboxState.Running(mode)
        }.onFailure { e ->
            _state.value = SandboxState.Error(e)
        }
    }

    suspend fun stop() {
        _state.value = SandboxState.Stopping
        runCatching { activeChannel?.close() }
        activeChannel = null
        runCatching { proot.stop() }
        runCatching { termux.disconnect() }
        _state.value = SandboxState.Idle
    }

    suspend fun restart(mode: SandboxMode) {
        stop()
        start(mode)
    }
}
