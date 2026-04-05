package com.agentshell.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.agentshell.app.db.RunEntity
import com.agentshell.app.viewmodel.RunsViewModel

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
