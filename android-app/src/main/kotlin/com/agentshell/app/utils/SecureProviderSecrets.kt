package com.agentshell.app.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureProviderSecrets(context: Context) {
    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "agentshell_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getApiKey(providerId: String): String =
        securePrefs.getString("prov_apikey_$providerId", "") ?: ""

    fun setApiKey(providerId: String, apiKey: String) {
        securePrefs.edit().putString("prov_apikey_$providerId", apiKey).apply()
    }

    fun migrateLegacyPlaintext(plainPrefs: SharedPreferences) {
        val providers = listOf("openai", "groq", "deepseek", "mistral", "openrouter")
        providers.forEach { id ->
            val legacyKey = plainPrefs.getString("prov_apikey_$id", "") ?: ""
            if (legacyKey.isNotBlank() && getApiKey(id).isBlank()) {
                setApiKey(id, legacyKey)
                plainPrefs.edit().remove("prov_apikey_$id").apply()
            }
            if (getApiKey(id).isNotBlank()) {
                plainPrefs.edit().putBoolean("prov_has_apikey_$id", true).apply()
            }
        }
    }
}
