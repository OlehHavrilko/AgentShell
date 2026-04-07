package com.agentshell.app.viewmodel

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    private fun buildDefaultProviders() = listOf(
        ProviderUiState("openai", "OpenAI", false, prefs.getBoolean("prov_enabled_openai", false), prefs.getString("prov_apikey_openai", "") ?: "", prefs.getString("prov_model_openai", "gpt-4o-mini") ?: "", ""),
        ProviderUiState("anthropic", "Anthropic (Claude)", false, prefs.getBoolean("prov_enabled_anthropic", false), prefs.getString("prov_apikey_anthropic", "") ?: "", prefs.getString("prov_model_anthropic", "claude-3-5-sonnet-20241022") ?: "", ""),
        ProviderUiState("ollama", "Ollama (Local)", true, prefs.getBoolean("prov_enabled_ollama", true), "", prefs.getString("prov_model_ollama", "llama3.2") ?: "", prefs.getString("prov_host_ollama", "http://localhost:11434") ?: ""),
        ProviderUiState("groq", "Groq (Ultra-fast)", false, prefs.getBoolean("prov_enabled_groq", false), prefs.getString("prov_apikey_groq", "") ?: "", prefs.getString("prov_model_groq", "llama-3.3-70b-versatile") ?: "", ""),
        ProviderUiState("mistral", "Mistral AI", false, prefs.getBoolean("prov_enabled_mistral", false), prefs.getString("prov_apikey_mistral", "") ?: "", prefs.getString("prov_model_mistral", "mistral-large-latest") ?: "", ""),
        ProviderUiState("deepseek", "DeepSeek", false, prefs.getBoolean("prov_enabled_deepseek", false), prefs.getString("prov_apikey_deepseek", "") ?: "", prefs.getString("prov_model_deepseek", "deepseek-chat") ?: "", ""),
        ProviderUiState("huggingface", "Hugging Face", false, prefs.getBoolean("prov_enabled_huggingface", false), prefs.getString("prov_apikey_huggingface", "") ?: "", prefs.getString("prov_model_huggingface", "meta-llama/Meta-Llama-3.2-3B-Instruct") ?: "", ""),
        ProviderUiState("cohere", "Cohere", false, prefs.getBoolean("prov_enabled_cohere", false), prefs.getString("prov_apikey_cohere", "") ?: "", prefs.getString("prov_model_cohere", "command-r-plus") ?: "", ""),
        ProviderUiState("llama_cpp", "Llama.cpp (Local)", true, prefs.getBoolean("prov_enabled_llama_cpp", false), "", prefs.getString("prov_model_llama_cpp", "") ?: "", ""),
        ProviderUiState("openrouter", "OpenRouter", false, prefs.getBoolean("prov_enabled_openrouter", false), prefs.getString("prov_apikey_openrouter", "") ?: "", prefs.getString("prov_model_openrouter", "anthropic/claude-3.5-sonnet") ?: "", ""),
        ProviderUiState("gemini", "Google Gemini", false, prefs.getBoolean("prov_enabled_gemini", false), prefs.getString("prov_apikey_gemini", "") ?: "", prefs.getString("prov_model_gemini", "gemini-1.5-flash") ?: "", ""),
    )

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
