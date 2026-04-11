package com.agentshell.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SettingsSuggest
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.agentshell.app.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(navController: NavController? = null, vm: SettingsViewModel = viewModel(
    factory = SettingsViewModel.Factory(LocalContext.current.applicationContext as android.app.Application)
)) {
    val sandboxEnabled by vm.sandboxEnabled.collectAsState()
    val termuxEnabled by vm.termuxEnabled.collectAsState()
    val sandboxStatus by vm.sandboxStatus.collectAsState()
    val providers by vm.providers.collectAsState()

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
                "Chat stays as the main workspace. Advanced tools and runtime controls live here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection("General") {
            Text("Version 1.0.0", style = MaterialTheme.typography.bodyMedium)
            Text(
                "This build is currently focused on interface flow and device-side runtime integration.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsSection("Advanced tools") {
            Text(
                "These sections are available, but they are secondary to the chat workflow.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SettingsNavCard(
                title = "Runs history",
                subtitle = "Inspect agent runs and open detailed timelines.",
                icon = { Icon(Icons.Filled.List, contentDescription = null) },
                onClick = { navController?.navigate(NavRoute.Runs.route) },
            )
            SettingsNavCard(
                title = "Memory",
                subtitle = "Browse stored audit events and memory traces.",
                icon = { Icon(Icons.Filled.Memory, contentDescription = null) },
                onClick = { navController?.navigate(NavRoute.Memory.route) },
            )
            SettingsNavCard(
                title = "Sandbox",
                subtitle = "Control the embedded Linux environment and terminal session.",
                icon = { Icon(Icons.Filled.Build, contentDescription = null) },
                onClick = { navController?.navigate(NavRoute.Sandbox.route) },
            )
            SettingsNavCard(
                title = "Workflow Builder",
                subtitle = "Create manual multi-step tool flows for testing.",
                icon = { Icon(Icons.Filled.SettingsSuggest, contentDescription = null) },
                onClick = { navController?.navigate(NavRoute.WorkflowBuilder.route) },
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

        SettingsSection("Model providers") {
            val enabledCount = providers.count { it.enabled }
            SettingsNavCard(
                title = "LLM Providers",
                subtitle = "Manage the providers available to the Android runtime.",
                supporting = "$enabledCount active",
                onClick = { navController?.navigate(NavRoute.Providers.route) },
            )
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

@Composable
private fun SettingsNavCard(
    title: String,
    subtitle: String,
    supporting: String? = null,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        onClick = onClick,
    ) {
        Row(
            Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon?.invoke()
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    supporting?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = title)
        }
    }
}
