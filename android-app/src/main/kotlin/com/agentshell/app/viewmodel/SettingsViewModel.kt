package com.agentshell.app.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.agentshell.app.llm.AndroidProviderCatalog
import com.agentshell.app.service.AgentRuntimeService
import com.agentshell.app.utils.SecureProviderSecrets
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
    private val prefs = app.getSharedPreferences("agentshell", Context.MODE_PRIVATE)
    private val secureSecrets = SecureProviderSecrets(app)

    private val _sandboxEnabled = MutableStateFlow(prefs.getBoolean("sandbox_enabled", false))
    val sandboxEnabled = _sandboxEnabled.asStateFlow()

    private val _termuxEnabled = MutableStateFlow(prefs.getBoolean("termux_enabled", false))
    val termuxEnabled = _termuxEnabled.asStateFlow()

    private val _sandboxStatus = MutableStateFlow("Stopped")
    val sandboxStatus = _sandboxStatus.asStateFlow()

    val isTermuxInstalled: Boolean
        get() = com.agentshell.app.service.TermuxConnector(app).isTermuxInstalled

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
        app.stopService(intent)
        _sandboxStatus.value = "Stopped"
        _sandboxEnabled.value = false
        prefs.edit().putBoolean("sandbox_enabled", false).apply()
    }

    fun exportPackage(uri: android.net.Uri) {
        viewModelScope.launch {
            val exportedUri = com.agentshell.app.utils.PackageHelper.exportPackage(app)
            if (exportedUri != null) {
                // Copy to user-selected URI
                app.contentResolver.openInputStream(exportedUri)?.use { input ->
                    app.contentResolver.openOutputStream(uri)?.use { output ->
                        input.copyTo(output)
                    }
                }
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

    /** Select the active provider — synced with ChatViewModel via SharedPreferences. */
    fun selectProvider(id: String) {
        prefs.edit().putString("selected_provider", id).apply()
        _providers.value = _providers.value.map {
            if (it.id == id) it.copy(enabled = true) else it
        }
    }

    private fun buildDefaultProviders() = AndroidProviderCatalog.supported.map { provider ->
        val keyPresent = secureSecrets.getApiKey(provider.id).isNotBlank() ||
            prefs.getBoolean("prov_has_apikey_${provider.id}", false)
        ProviderUiState(
            id = provider.id,
            displayName = provider.displayName,
            isLocal = provider.isLocal,
            enabled = prefs.getBoolean("prov_enabled_${provider.id}", provider.isLocal),
            apiKey = if (provider.isLocal) "" else if (keyPresent) "********" else "",
            model = prefs.getString("prov_model_${provider.id}", provider.defaultModel) ?: provider.defaultModel,
            host = prefs.getString("prov_host_${provider.id}", provider.defaultHost) ?: provider.defaultHost,
        )
    }

    fun toggleProvider(id: String, enabled: Boolean) {
        prefs.edit().putBoolean("prov_enabled_$id", enabled).apply()
        _providers.value = _providers.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }

    fun saveProviderConfig(id: String, apiKey: String, model: String, host: String) {
        val normalizedKey = if (apiKey == "********") secureSecrets.getApiKey(id) else apiKey
        secureSecrets.setApiKey(id, normalizedKey)
        prefs.edit()
            .putBoolean("prov_has_apikey_$id", normalizedKey.isNotBlank())
            .putString("prov_model_$id", model)
            .putString("prov_host_$id", host)
            .apply()
        _providers.value = _providers.value.map {
            if (it.id == id) it.copy(apiKey = if (normalizedKey.isBlank()) "" else "********", model = model, host = host) else it
        }
    }

    // ── ViewModel Factory ──────────────────────────────────────────────────────
    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(app) as T
    }
}
