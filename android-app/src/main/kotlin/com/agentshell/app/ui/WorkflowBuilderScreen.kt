package com.agentshell.app.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.agentshell.app.service.AgentRuntimeService
import com.agentshell.app.workflow.WorkflowDraftStep
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val workflowJson = Json { prettyPrint = false }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkflowBuilderScreen() {
    val context = LocalContext.current
    var steps by remember { mutableStateOf(listOf<WorkflowDraftStep>()) }
    var showAddDialog by remember { mutableStateOf(false) }

    if (showAddDialog) {
        AddStepDialog(
            onAdd = { step -> steps = steps + step; showAddDialog = false },
            onDismiss = { showAddDialog = false }
        )
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Add Step") },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Workflow Builder",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Create a scripted pipeline and run it through the agent service",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Info card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Supported: shell_exec, git_exec, file_write. Use JSON args for shell/git, plain text for file_write.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Step count
            if (steps.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Build,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            "${steps.size} steps",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            // Step list
            if (steps.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(top = 40.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Filled.Build,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                            Text(
                                "No steps yet",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Tap + to add the first tool call",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(steps) { index, step ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp)),
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
                                verticalAlignment = Alignment.Top,
                            ) {
                                // Step number badge
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Text(
                                        "${index + 1}",
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }

                                Spacer(Modifier.width(10.dp))

                                // Step details
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        step.type,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        step.params.ifBlank { "(empty)" },
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 3,
                                    )
                                }

                                // Delete button
                                IconButton(
                                    onClick = { steps = steps.toMutableList().also { it.removeAt(index) } },
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }

                // Run button
                Button(
                    onClick = {
                        val intent = Intent(context, AgentRuntimeService::class.java).apply {
                            action = AgentRuntimeService.ACTION_START_WORKFLOW
                            putExtra(AgentRuntimeService.EXTRA_WORKFLOW_JSON, workflowJson.encodeToString(steps))
                        }
                        ContextCompat.startForegroundService(context, intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = steps.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Run Workflow (${steps.size} steps)")
                }
            }
        }
    }
}

@Composable
fun AddStepDialog(onAdd: (WorkflowDraftStep) -> Unit, onDismiss: () -> Unit) {
    val stepTypes = listOf("shell_exec", "git_exec", "file_write")
    var selectedType by remember { mutableStateOf(stepTypes[0]) }
    var params by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Filled.AddCircle, contentDescription = null)
        },
        title = { Text("Add Step") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Type selector
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Step type:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    stepTypes.forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedType = type }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selectedType == type, onClick = { selectedType = type })
                            Spacer(Modifier.width(8.dp))
                            Text(type, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

                // Params input
                OutlinedTextField(
                    value = params,
                    onValueChange = { params = it },
                    label = {
                        Text(
                            when (selectedType) {
                                "shell_exec" -> "Command or JSON args"
                                "git_exec" -> "Git subcommand or JSON args"
                                else -> "JSON args or file content"
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(WorkflowDraftStep(selectedType, params)) },
                enabled = params.isNotBlank(),
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
