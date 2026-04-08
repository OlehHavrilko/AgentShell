package com.agentshell.app.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agentshell.app.llm.AndroidProviderCatalog
import com.agentshell.app.service.AgentRuntimeService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProviderUiState(
    val id: String,
    val displayName: String,
    val isLocal: Boolean,
    val enabled: Boolean,
    val apiKey: String,
    val model: String,
    val host: String,
)

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
            val uri = com.agentshell.app.utils.PackageHelper.exportPackage(app)
            if (uri != null) {
                // Share the exported ZIP via system intent
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                app.startActivity(shareIntent)
            }
        }
    }

    fun importPackage(uri: android.net.Uri) {
        viewModelScope.launch {
            com.agentshell.app.utils.PackageHelper.importPackage(app, uri)
        }
    }

    private val _providers = MutableStateFlow<List<ProviderUiState>>(buildDefaultProviders())
    val providers = _providers.asStateFlow()

    private fun buildDefaultProviders() = AndroidProviderCatalog.supported.map { provider ->
        ProviderUiState(
            id = provider.id,
            displayName = provider.displayName,
            isLocal = provider.isLocal,
            enabled = prefs.getBoolean("prov_enabled_${provider.id}", provider.id == "ollama"),
            apiKey = if (provider.isLocal) "" else prefs.getString("prov_apikey_${provider.id}", "") ?: "",
            model = prefs.getString("prov_model_${provider.id}", provider.defaultModel) ?: provider.defaultModel,
            host = prefs.getString("prov_host_${provider.id}", provider.defaultHost) ?: provider.defaultHost,
        )
    }

    fun toggleProvider(id: String, enabled: Boolean) {
        prefs.edit().putBoolean("prov_enabled_$id", enabled).apply()
        _providers.value = _providers.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }

    fun saveProviderConfig(id: String, apiKey: String, model: String, host: String) {
        prefs.edit()
            .putString("prov_apikey_$id", apiKey)
            .putString("prov_model_$id", model)
            .putString("prov_host_$id", host)
            .apply()
        _providers.value = _providers.value.map {
            if (it.id == id) it.copy(apiKey = apiKey, model = model, host = host) else it
        }
    }
}
