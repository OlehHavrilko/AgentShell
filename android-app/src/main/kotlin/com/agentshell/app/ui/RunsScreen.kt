package com.agentshell.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.agentshell.app.db.RunEntity
import com.agentshell.app.viewmodel.RunsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val STATUS_FILTERS = listOf("All", "RUNNING", "COMPLETED", "FAILED", "CRASHED")

@Composable
fun RunsScreen(navController: NavController, vm: RunsViewModel = viewModel(
    factory = RunsViewModel.Factory(LocalContext.current.applicationContext as android.app.Application)
)) {
    val runs by vm.runs.collectAsState()
    var activeFilter by remember { mutableStateOf("All") }

    val filteredRuns = remember(runs, activeFilter) {
        if (activeFilter == "All") runs else runs.filter { it.status == activeFilter }
    }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Runs", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Inspect agent executions, filter by status, and open detailed timelines.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(STATUS_FILTERS) { filter ->
                val count = if (filter == "All") runs.size else runs.count { it.status == filter }
                FilterChip(
                    selected = activeFilter == filter,
                    onClick = { activeFilter = filter },
                    label = { Text("$filter${if (count > 0) " ($count)" else ""}") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (filter) {
                            "COMPLETED" -> MaterialTheme.colorScheme.primaryContainer
                            "RUNNING" -> MaterialTheme.colorScheme.secondaryContainer
                            "FAILED", "CRASHED" -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        if (filteredRuns.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(modifier = Modifier.padding(20.dp)) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            if (runs.isEmpty()) "No runs yet"
                            else "No runs with status \"$activeFilter\"",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (runs.isEmpty()) "Start a task from the Chat screen to create your first run."
                            else "Switch filters or run a new task from Chat.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(filteredRuns) { run ->
                    RunCard(run) { navController.navigate("run_detail/${run.runId}") }
                }
            }
        }
    }
}

@Composable
fun RunCard(run: RunEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        run.preset ?: "Ad-hoc goal",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "Run ID …${run.runId.takeLast(10)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RunStatusChip(run.status)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetaPill("Started ${formatTimestamp(run.startTime)}")
                MetaPill("Tokens ${formatTokenCost(run.tokenCost)}")
            }

            Text(
                buildString {
                    append("Duration ")
                    append(formatDuration(run.startTime, run.endTime))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun RunStatusChip(status: String) {
    val containerColor = when (status) {
        "COMPLETED" -> MaterialTheme.colorScheme.primaryContainer
        "RUNNING" -> MaterialTheme.colorScheme.secondaryContainer
        "FAILED", "CRASHED" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = containerColor, shape = MaterialTheme.shapes.small) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun MetaPill(text: String) {
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

private fun formatTokenCost(tokenCost: Double): String = String.format(Locale.US, "%.0f", tokenCost)

private fun formatTimestamp(timestamp: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun formatDuration(startTime: Long, endTime: Long?): String {
    if (endTime == null) return "in progress"
    val seconds = ((endTime - startTime) / 1000).coerceAtLeast(0)
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
}
