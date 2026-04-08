package com.agentshell.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.agentshell.app.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(navController: NavController? = null, vm: SettingsViewModel = viewModel()) {
    val sandboxEnabled by vm.sandboxEnabled.collectAsState()
    val termuxEnabled by vm.termuxEnabled.collectAsState()
    val sandboxStatus by vm.sandboxStatus.collectAsState()

    // File picker for ZIP import
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { vm.importPackage(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Configure the local runtime, provider access, and package portability.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection("General") {
            Text("Version 1.0.0", style = MaterialTheme.typography.bodyMedium)
            Text(
                "This build is aimed at testing the agent runtime on-device before release hardening.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection("Alpine Sandbox") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Enable Alpine Linux")
                    Text(
                        sandboxStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = sandboxEnabled, onCheckedChange = { vm.setSandboxEnabled(it) })
            }
            Text(
                "Use the embedded sandbox when you want the app to run tools without relying on an external Termux session.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.startSandbox() }, enabled = !sandboxEnabled) { Text("Start") }
                OutlinedButton(onClick = { vm.stopSandbox() }, enabled = sandboxEnabled) { Text("Stop") }
            }
        }

        SettingsSection("External Termux") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Use Termux instead of Alpine")
                Switch(checked = termuxEnabled, onCheckedChange = { vm.setTermuxEnabled(it) })
            }
            Text(
                "Best for advanced shell access or when you want to manage the Linux environment yourself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!termuxEnabled) {
                OutlinedButton(onClick = { /* open Play Store */ }, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Install Termux")
                }
            }
        }

        SettingsSection("Export / Import") {
            Text(
                "Export a portable ZIP of the app package state or import one on another device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.exportPackage() }) { Text("Export ZIP") }
                OutlinedButton(onClick = { importLauncher.launch("application/zip") }) { Text("Import ZIP") }
            }
        }

        SettingsSection("Metrics") {
            Text(
                "Prometheus counters are available at :9091/metrics while the sandbox is running.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = { navController?.navigate("providers") },
        ) {
            Row(
                Modifier
                    .padding(14.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("LLM Providers", style = MaterialTheme.typography.titleMedium)
                    val enabledCount = vm.providers.collectAsState().value.count { it.enabled }
                    Text(
                        "$enabledCount active",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Manage the providers available to the Android runtime.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Go to providers")
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            content()
        }
    }
}
