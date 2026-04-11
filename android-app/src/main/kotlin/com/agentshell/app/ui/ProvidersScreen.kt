package com.agentshell.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentshell.app.viewmodel.ProviderUiState
import com.agentshell.app.viewmodel.SettingsViewModel

@Composable
fun ProvidersScreen(vm: SettingsViewModel = viewModel(
    factory = SettingsViewModel.Factory(LocalContext.current.applicationContext as android.app.Application)
)) {
    val providers by vm.providers.collectAsState()
    var editingProvider by remember { mutableStateOf<ProviderUiState?>(null) }
    val activeCount = providers.count { it.enabled && (it.isLocal || it.apiKey.isNotBlank()) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Providers",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "$activeCount of ${providers.size} configured and ready",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Tip card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
            ),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Filled.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Free tiers available",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        "Groq, DeepSeek, and OpenRouter all offer free API tiers. Tap edit to configure.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }

        // Provider list
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(providers) { prov ->
                ProviderRow(
                    provider = prov,
                    onToggle = { vm.toggleProvider(prov.id, it) },
                    onEdit = { editingProvider = prov },
                )
            }
        }
    }
}

@Composable
fun ProviderRow(provider: ProviderUiState, onToggle: (Boolean) -> Unit, onEdit: () -> Unit) {
    val isReady = provider.enabled && (provider.isLocal || provider.apiKey.isNotBlank())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                // Icon badge
                Surface(
                    color = if (provider.isLocal) {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(
                        imageVector = if (provider.isLocal) Icons.Filled.Computer else Icons.Filled.Cloud,
                        contentDescription = null,
                        modifier = Modifier.padding(10.dp),
                        tint = if (provider.isLocal) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        },
                    )
                }

                Spacer(Modifier.width(12.dp))

                // Details
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            provider.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (isReady) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                    if (provider.model.isNotBlank()) {
                        Text(
                            provider.model,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!provider.isLocal && provider.apiKey.isBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "No API key",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            // Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Configure",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Edit")
                }
                Switch(
                    checked = provider.enabled,
                    onCheckedChange = onToggle,
                )
            }
        }
    }
}

@Composable
fun ProviderConfigDialog(
    provider: ProviderUiState,
    onSave: (apiKey: String, model: String, host: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var apiKey by remember { mutableStateOf(provider.apiKey) }
    var model by remember { mutableStateOf(provider.model) }
    var host by remember { mutableStateOf(provider.host) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (provider.isLocal) Icons.Filled.Computer else Icons.Filled.Cloud,
                contentDescription = null,
            )
        },
        title = { Text("Configure ${provider.displayName}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (!provider.isLocal) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (provider.isLocal || provider.id == "ollama") {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("Host / URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                if (provider.isLocal && provider.id != "ollama") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Place the model file (*.gguf) in the app files directory",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(apiKey, model, host) }) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Save")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
