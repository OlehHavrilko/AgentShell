package com.agentshell.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.agentshell.app.service.AgentRuntimeService
import com.agentshell.app.workflow.WorkflowDraftStep
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val workflowJson = Json { prettyPrint = false }

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
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add step")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Workflow Builder", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Create a simple tool sequence and run it through the Android agent service.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Supported steps", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "shell_exec, git_exec, file_write. For shell and git you can use plain text; for file_write JSON is recommended.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (steps.isEmpty()) {
                Card {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("No steps yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap + to add the first tool call in the workflow.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        "${steps.size} steps ready",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f, fill = steps.isNotEmpty()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(steps) { index, step ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Step ${index + 1}: ${step.type}", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    step.params.ifBlank { "(empty params)" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { steps = steps.toMutableList().also { it.removeAt(index) } }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }
            if (steps.isNotEmpty()) {
                Button(
                    onClick = {
                        val intent = Intent(context, AgentRuntimeService::class.java).apply {
                            action = AgentRuntimeService.ACTION_START_WORKFLOW
                            putExtra(AgentRuntimeService.EXTRA_WORKFLOW_JSON, workflowJson.encodeToString(steps))
                        }
                        ContextCompat.startForegroundService(context, intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Run Workflow")
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
        title = { Text("Add Step") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Step type:")
                stepTypes.forEach { type ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = selectedType == type, onClick = { selectedType = type })
                        Text(type)
                    }
                }
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
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(WorkflowDraftStep(selectedType, params)) }) { Text("Add") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
