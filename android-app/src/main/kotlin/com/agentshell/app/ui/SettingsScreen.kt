package com.agentshell.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentshell.app.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val sandboxEnabled by vm.sandboxEnabled.collectAsState()
    val termuxEnabled by vm.termuxEnabled.collectAsState()
    val sandboxStatus by vm.sandboxStatus.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        // General
        SettingsSection("General") {
            Text("Version 1.0.0", style = MaterialTheme.typography.bodyMedium)
        }

        // Sandbox
        SettingsSection("Alpine Sandbox") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Enable Alpine Linux")
                    Text(sandboxStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = sandboxEnabled, onCheckedChange = { vm.setSandboxEnabled(it) })
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.startSandbox() }, enabled = !sandboxEnabled) { Text("Start") }
                OutlinedButton(onClick = { vm.stopSandbox() }, enabled = sandboxEnabled) { Text("Stop") }
            }
        }

        // Termux
        SettingsSection("External Termux") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Use Termux instead of Alpine")
                Switch(checked = termuxEnabled, onCheckedChange = { vm.setTermuxEnabled(it) })
            }
            if (!termuxEnabled) {
                OutlinedButton(onClick = { /* open Play Store */ }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Install Termux")
                }
            }
        }

        // Export / Import
        SettingsSection("Export / Import") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.exportPackage() }) { Text("Export ZIP") }
                OutlinedButton(onClick = { vm.importPackage() }) { Text("Import ZIP") }
            }
        }

        // Metrics
        SettingsSection("Metrics") {
            Text("Prometheus counters available at :9091/metrics when sandbox is running.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}
