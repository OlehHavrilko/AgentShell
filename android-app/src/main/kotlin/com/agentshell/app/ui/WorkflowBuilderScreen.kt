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
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Workflow Builder", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Supported steps: shell_exec, git_exec, file_write. For shell/git you can enter plain text; for file_write JSON is recommended.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (steps.isEmpty()) {
                Text("No steps yet. Tap + to add a step.", style = MaterialTheme.typography.bodyMedium)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(steps) { index, step ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("Step ${index + 1}: ${step.type}", style = MaterialTheme.typography.titleSmall)
                                Text(step.params, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { steps = steps.toMutableList().also { it.removeAt(index) } }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove")
                            }
                        }
                    }
                }
            }
            if (steps.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    val intent = Intent(context, AgentRuntimeService::class.java).apply {
                        action = AgentRuntimeService.ACTION_START_WORKFLOW
                        putExtra(AgentRuntimeService.EXTRA_WORKFLOW_JSON, workflowJson.encodeToString(steps))
                    }
                    ContextCompat.startForegroundService(context, intent)
                }, Modifier.fillMaxWidth()) {
                    Text("Save And Run Workflow")
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
