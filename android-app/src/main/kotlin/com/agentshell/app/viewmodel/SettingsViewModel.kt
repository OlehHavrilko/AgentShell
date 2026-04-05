package com.agentshell.app.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agentshell.app.service.AgentRuntimeService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("agentshell", android.content.Context.MODE_PRIVATE)

    private val _sandboxEnabled = MutableStateFlow(prefs.getBoolean("sandbox_enabled", false))
    val sandboxEnabled = _sandboxEnabled.asStateFlow()

    private val _termuxEnabled = MutableStateFlow(prefs.getBoolean("termux_enabled", false))
    val termuxEnabled = _termuxEnabled.asStateFlow()

    private val _sandboxStatus = MutableStateFlow("Stopped")
    val sandboxStatus = _sandboxStatus.asStateFlow()

    fun setSandboxEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("sandbox_enabled", enabled).apply()
        _sandboxEnabled.value = enabled
    }

    fun setTermuxEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("termux_enabled", enabled).apply()
        _termuxEnabled.value = enabled
    }

    fun startSandbox() {
        val intent = Intent(app, AgentRuntimeService::class.java).apply {
            action = if (_termuxEnabled.value) AgentRuntimeService.ACTION_START_TERMUX
                     else AgentRuntimeService.ACTION_START_EMBEDDED
        }
        app.startForegroundService(intent)
        _sandboxStatus.value = "Starting…"
        _sandboxEnabled.value = true
        prefs.edit().putBoolean("sandbox_enabled", true).apply()
    }

    fun stopSandbox() {
        val intent = Intent(app, AgentRuntimeService::class.java).apply { action = AgentRuntimeService.ACTION_STOP }
        app.startService(intent)
        _sandboxStatus.value = "Stopped"
        _sandboxEnabled.value = false
        prefs.edit().putBoolean("sandbox_enabled", false).apply()
    }

    fun exportPackage() {
        viewModelScope.launch {
            // TODO: wire to PackageHelper.exportPackage()
        }
    }

    fun importPackage() {
        viewModelScope.launch {
            // TODO: open file picker and call PackageHelper.importPackage()
        }
    }
}
