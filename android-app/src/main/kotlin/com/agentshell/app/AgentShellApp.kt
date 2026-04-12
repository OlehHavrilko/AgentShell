package com.agentshell.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.agentshell.app.utils.SecureProviderSecrets

class AgentShellApp : Application() {
    companion object {
        const val NOTIFICATION_CHANNEL_ID = "agent_runtime_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        migrateProviderKeysToSecureStorage()
    }

    private fun migrateProviderKeysToSecureStorage() {
        val plainPrefs = getSharedPreferences("agentshell", MODE_PRIVATE)
        SecureProviderSecrets(this).migrateLegacyPlaintext(plainPrefs)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_description)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
