package com.agentshell.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentshell.app.db.AgentDatabase
import com.agentshell.app.db.StepEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class RunDetailViewModel(private val db: AgentDatabase, private val runId: String) : ViewModel() {

    private val _steps = MutableStateFlow<List<StepEntity>>(emptyList())
    val steps = _steps.asStateFlow()

    init {
        viewModelScope.launch {
            db.stepDao().stepsForRun(runId).collect { _steps.value = it }
        }
    }

    class Factory(private val db: AgentDatabase, private val runId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            RunDetailViewModel(db, runId) as T
    }
}

@Composable
fun RunDetailScreen(runId: String) {
    val context = LocalContext.current
    val db = AgentDatabase.getInstance(context)
    val vm: RunDetailViewModel = viewModel(factory = RunDetailViewModel.Factory(db, runId))
    val steps by vm.steps.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Run details", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Run …${runId.takeLast(12)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (steps.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("No steps recorded", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "This run does not have timeline entries yet. New runs will populate this screen as tools execute.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailPill("${steps.size} steps")
                DetailPill(
                    steps.count { it.status == "COMPLETED" }.toString() + " completed",
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(steps) { step -> StepCard(step) }
            }
        }
    }
}

@Composable
private fun StepCard(step: StepEntity) {
    val fmt = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val statusColor = when (step.status) {
        "COMPLETED" -> MaterialTheme.colorScheme.primaryContainer
        "FAILED" -> MaterialTheme.colorScheme.errorContainer
        "RUNNING" -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "#${step.stepIndex + 1} ${step.toolId}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Surface(color = statusColor, shape = MaterialTheme.shapes.small) {
                    Text(
                        step.status,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }

            Text(
                "Started: ${fmt.format(Date(step.startTime))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            step.endTime?.let {
                Text(
                    "Duration: ${formatStepDuration(step.startTime, it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            step.input?.takeIf { it.isNotBlank() }?.let { input ->
                Text("Input", style = MaterialTheme.typography.labelSmall)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        "▶ ${input.take(200)}",
                        modifier = Modifier.padding(6.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            step.output?.takeIf { it.isNotBlank() }?.let { output ->
                Text("Output", style = MaterialTheme.typography.labelSmall)
                Surface(
                    color = if (step.status == "FAILED")
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.extraSmall,
                ) {
                    Text(
                        output.take(300),
                        modifier = Modifier.padding(6.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatStepDuration(startTime: Long, endTime: Long): String {
    val durationMs = (endTime - startTime).coerceAtLeast(0)
    return if (durationMs < 1_000) {
        "${durationMs} ms"
    } else {
        val seconds = durationMs / 1_000
        "${seconds}s ${durationMs % 1_000}ms"
    }
}
