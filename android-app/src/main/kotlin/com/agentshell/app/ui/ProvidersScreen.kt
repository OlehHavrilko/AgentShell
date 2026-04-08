package com.agentshell.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentshell.app.viewmodel.ProviderUiState
import com.agentshell.app.viewmodel.SettingsViewModel

@Composable
fun ProvidersScreen(vm: SettingsViewModel = viewModel()) {
    val providers by vm.providers.collectAsState()
    var editingProvider by remember { mutableStateOf<ProviderUiState?>(null) }

    editingProvider?.let { prov ->
        ProviderConfigDialog(
            provider = prov,
            onSave = { apiKey, model, host ->
                vm.saveProviderConfig(prov.id, apiKey, model, host)
                editingProvider = null
            },
            onDismiss = { editingProvider = null }
        )
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "LLM Providers",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                "These are the providers currently wired into the Android runtime. Configure keys, models, and local endpoints here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("💡 Free tiers available", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Groq, DeepSeek, and OpenRouter all offer free API usage. Tap the ✏ edit button to add your key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        LazyColumn {
            items(providers) { prov ->
                ProviderRow(
                    provider = prov,
                    onToggle = { vm.toggleProvider(prov.id, it) },
                    onEdit = { editingProvider = prov }
                )
            }
        }
    }
}

@Composable
fun ProviderRow(provider: ProviderUiState, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(
                    imageVector = if (provider.isLocal) Icons.Filled.Computer else Icons.Filled.Cloud,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 12.dp),
                    tint = if (provider.enabled) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column {
                    Text(provider.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(
                        provider.id,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (provider.model.isNotBlank()) {
                        Text(
                            provider.model,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    if (!provider.isLocal && provider.apiKey.isBlank()) {
                        Text(
                            "⚠ No API key — configure to use",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Configure")
                }
                Switch(checked = provider.enabled, onCheckedChange = onToggle)
            }
        }
    }
}

@Composable
fun ProviderConfigDialog(
    provider: ProviderUiState,
    onSave: (apiKey: String, model: String, host: String) -> Unit,
    onDismiss: () -> Unit
) {
    var apiKey by remember { mutableStateOf(provider.apiKey) }
    var model by remember { mutableStateOf(provider.model) }
    var host by remember { mutableStateOf(provider.host) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure ${provider.displayName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!provider.isLocal) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (provider.isLocal || provider.id == "ollama") {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host / URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                if (provider.isLocal && provider.id != "ollama") {
                    Text(
                        "💡 Place the model file (*.gguf) in the app files directory",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(apiKey, model, host) }) { Text("Save") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
