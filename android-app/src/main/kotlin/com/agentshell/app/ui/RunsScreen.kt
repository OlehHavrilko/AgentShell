package com.agentshell.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.agentshell.app.db.RunEntity
import com.agentshell.app.viewmodel.RunsViewModel

private val STATUS_FILTERS = listOf("All", "RUNNING", "COMPLETED", "FAILED", "CRASHED")

@Composable
fun RunsScreen(navController: NavController, vm: RunsViewModel = viewModel()) {
    val runs by vm.runs.collectAsState()
    var activeFilter by remember { mutableStateOf("All") }

    val filteredRuns = remember(runs, activeFilter) {
        if (activeFilter == "All") runs
        else runs.filter { it.status == activeFilter }
    }

    Column(Modifier.fillMaxSize()) {
        // ── Header ──────────────────────────────────────────────────────────
        Text(
            "Runs",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
        )

        // ── Filter pills ─────────────────────────────────────────────────────
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(STATUS_FILTERS) { filter ->
                val count = if (filter == "All") runs.size
                else runs.count { it.status == filter }

                FilterChip(
                    selected = activeFilter == filter,
                    onClick = { activeFilter = filter },
                    label = {
                        Text("$filter${if (count > 0) " ($count)" else ""}")
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (filter) {
                            "COMPLETED" -> MaterialTheme.colorScheme.primaryContainer
                            "RUNNING" -> MaterialTheme.colorScheme.secondaryContainer
                            "FAILED", "CRASHED" -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // ── List ─────────────────────────────────────────────────────────────
        if (filteredRuns.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (runs.isEmpty()) "No runs yet. Start one from the Chat screen."
                    else "No runs with status \"$activeFilter\".",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
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
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(run.runId.takeLast(8), style = MaterialTheme.typography.titleSmall)
                RunStatusChip(run.status)
            }
            run.preset?.let { Text("Preset: $it", style = MaterialTheme.typography.bodySmall) }
            Text("Tokens: ${run.tokenCost}", style = MaterialTheme.typography.bodySmall)
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
            status,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun RunsScreen(navController: NavController, vm: RunsViewModel = viewModel()) {
    val runs by vm.runs.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Text(
            "Runs",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp)
        )
        if (runs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No runs yet. Start one from the Chat screen.")
            }
        } else {
            LazyColumn {
                items(runs) { run -> RunCard(run) { navController.navigate("run_detail/${run.runId}") } }
            }
        }
    }
}

@Composable
fun RunCard(run: RunEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(run.runId.takeLast(8), style = MaterialTheme.typography.titleSmall)
                RunStatusChip(run.status)
            }
            run.preset?.let { Text("Preset: $it", style = MaterialTheme.typography.bodySmall) }
            Text("Tokens: ${run.tokenCost}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun RunStatusChip(status: String) {
    val color = when (status) {
        "COMPLETED" -> MaterialTheme.colorScheme.primaryContainer
        "RUNNING" -> MaterialTheme.colorScheme.secondaryContainer
        "FAILED", "CRASHED" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(color = color, shape = MaterialTheme.shapes.small) {
        Text(status, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
    }
}
