package com.agentshell.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentshell.app.db.AgentDatabase
import com.agentshell.app.db.RunEntity
import com.agentshell.app.viewmodel.RunsViewModel
import java.text.SimpleDateFormat
import java.util.*

enum class RunFilter(val label: String, val status: String?) {
    ALL("All", null),
    RUNNING("Running", "RUNNING"),
    COMPLETED("Completed", "COMPLETED"),
    FAILED("Failed", "FAILED"),
    CRASHED("Crashed", "CRASHED"),
}

@Composable
fun RunsScreen(
    onNavigateToRun: (String) -> Unit = {},
    vm: RunsViewModel = viewModel(
        factory = RunsViewModel.Factory(
            AgentDatabase.getInstance(LocalContext.current)
        )
    ),
) {
    val runs by vm.runs.collectAsState(initial = emptyList())
    var selectedFilter by remember { mutableStateOf(RunFilter.ALL) }

    val filteredRuns = runs.filter { run ->
        selectedFilter.status == null || run.status == selectedFilter.status
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
                "Runs",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Agent execution history",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Filter chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(RunFilter.entries.toList()) { filter ->
                val count = if (filter.status == null) {
                    runs.size
                } else {
                    runs.count { it.status == filter.status }
                }
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(filter.label)
                            Spacer(Modifier.width(4.dp))
                            Surface(
                                color = if (selectedFilter == filter) {
                                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(18.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        count.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selectedFilter == filter) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                }
                            }
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = when (filter) {
                                RunFilter.ALL -> Icons.Filled.List
                                RunFilter.RUNNING -> Icons.Filled.PlayArrow
                                RunFilter.COMPLETED -> Icons.Filled.CheckCircle
                                RunFilter.FAILED -> Icons.Filled.Error
                                RunFilter.CRASHED -> Icons.Filled.Warning
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
        }

        // Run list
        if (filteredRuns.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = 40.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
                            imageVector = Icons.Filled.PlayCircle,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Text(
                            "No runs yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Start a task from Chat to see it here",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(filteredRuns, key = { it.runId }) { run ->
                    RunCard(run = run, onClick = { onNavigateToRun(run.runId) })
                }
            }
        }
    }
}

@Composable
private fun RunCard(run: RunEntity, onClick: () -> Unit) {
    val fmt = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Title row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = run.preset.takeIf { it.isNotBlank() } ?: "Run ${run.runId.takeLast(8)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = run.runId.takeLast(12),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    )
                }

                // Status badge
                StatusBadge(status = run.status)
            }

            // Meta row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Time
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            fmt.format(Date(run.startTime)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    // Duration
                    run.endTime?.let { end ->
                        val durationMs = end - run.startTime
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.AvTimer,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                formatDuration(durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // Token cost
                if (run.tokenCost > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            "${"%.1f".format(run.tokenCost)} tok",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (color, label) = when (status) {
        "RUNNING" -> MaterialTheme.colorScheme.secondary to "Running"
        "COMPLETED" -> MaterialTheme.colorScheme.primary to "Done"
        "FAILED" -> MaterialTheme.colorScheme.error to "Failed"
        "CRASHED" -> MaterialTheme.colorScheme.error to "Crashed"
        else -> MaterialTheme.colorScheme.onSurfaceVariant to status
    }

    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = color,
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ),
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = color,
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}
