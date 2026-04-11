package com.agentshell.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.agentshell.app.R

sealed class DrawerItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    object Chat : DrawerItem("chat", "Chat", Icons.Filled.Chat)
    object Runs : DrawerItem("runs", "Runs", Icons.Filled.Assignment)
    object Sandbox : DrawerItem("sandbox", "Sandbox", Icons.Filled.Terminal)
    object Settings : DrawerItem("settings", "Settings", Icons.Filled.Settings)
}

val drawerItems = listOf(
    DrawerItem.Chat,
    DrawerItem.Runs,
    DrawerItem.Sandbox,
    DrawerItem.Settings,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    var currentRoute by remember { mutableStateOf(DrawerItem.Chat.route) }

    ModalNavigationDrawer(
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
            ) {
                // Header
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                ) {
                    Text(
                        "AgentShell",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Autonomous agent runtime",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                // Navigation items
                drawerItems.forEach { item ->
                    NavigationDrawerItem(
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        },
                        selected = currentRoute == item.route,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(0) { inclusive = false }
                                launchSingleTop = true
                            }
                            currentRoute = item.route
                            navController.popBackStack(item.route, inclusive = false)
                        },
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                    )
                }

                Spacer(Modifier.weight(1f))

                // Footer — version
                Text(
                    text = "v1.0.0",
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        content = {
            Scaffold(
                topBar = {
                    if (currentRoute != DrawerItem.Chat.route) {
                        TopAppBar(
                            title = {
                                Text(
                                    text = drawerItems.find { it.route == currentRoute }?.label ?: "",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { navController.popBackStack() }) {
                                    Icon(
                                        imageVector = Icons.Filled.Menu,
                                        contentDescription = "Open menu",
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                    }
                },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = DrawerItem.Chat.route,
                    modifier = Modifier.padding(innerPadding),
                ) {
                    composable(DrawerItem.Chat.route) {
                        ChatScreen(
                            onOpenDrawer = { navController.navigate(DrawerItem.Settings.route) }
                        )
                    }
                    composable(DrawerItem.Runs.route) {
                        RunsScreen(
                            onNavigateToRun = { runId ->
                                navController.navigate("run_detail/$runId")
                            }
                        )
                    }
                    composable(DrawerItem.Sandbox.route) { SandboxScreen() }
                    composable(DrawerItem.Settings.route) {
                        SettingsScreen(
                            onNavigateToRuns = { navController.navigate(DrawerItem.Runs.route) },
                            onNavigateToMemory = { navController.navigate("memory") },
                            onNavigateToWorkflow = { navController.navigate("workflow_builder") },
                            onNavigateToProviders = { navController.navigate("providers") },
                        )
                    }
                    composable("run_detail/{runId}") { backStackEntry ->
                        val runId = backStackEntry.arguments?.getString("runId") ?: ""
                        RunDetailScreen(runId = runId)
                    }
                    composable("memory") { MemoryScreen() }
                    composable("workflow_builder") { WorkflowBuilderScreen() }
                    composable("providers") { ProvidersScreen() }
                }
            }
        },
    )
}
