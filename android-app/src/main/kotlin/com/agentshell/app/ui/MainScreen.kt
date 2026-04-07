package com.agentshell.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

sealed class NavRoute(val route: String, val label: String) {
    object Chat : NavRoute("chat", "Chat")
    object Runs : NavRoute("runs", "Runs")
    object Memory : NavRoute("memory", "Memory")
    object Sandbox : NavRoute("sandbox", "Sandbox")
    object Settings : NavRoute("settings", "Settings")
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val bottomItems = listOf(
        NavRoute.Chat,
        NavRoute.Runs,
        NavRoute.Memory,
        NavRoute.Sandbox,
        NavRoute.Settings,
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                bottomItems.forEach { screen ->
                    NavigationBarItem(
                        icon = {
                            when (screen) {
                                is NavRoute.Chat -> Icon(Icons.Filled.Chat, screen.label)
                                is NavRoute.Runs -> Icon(Icons.Filled.List, screen.label)
                                is NavRoute.Memory -> Icon(Icons.Filled.Memory, screen.label)
                                is NavRoute.Sandbox -> Icon(Icons.Filled.Build, screen.label)
                                is NavRoute.Settings -> Icon(Icons.Filled.Settings, screen.label)
                            }
                        },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController,
            startDestination = NavRoute.Chat.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(NavRoute.Chat.route) { ChatScreen() }
            composable(NavRoute.Runs.route) { RunsScreen(navController) }
            composable(NavRoute.Memory.route) { MemoryScreen() }
            composable(NavRoute.Sandbox.route) { SandboxScreen() }
            composable(NavRoute.Settings.route) { SettingsScreen(navController) }
            composable("providers") { ProvidersScreen() }
            composable("workflow_builder") { WorkflowBuilderScreen() }
            composable("run_detail/{runId}") { backStackEntry ->
                RunDetailScreen(runId = backStackEntry.arguments?.getString("runId") ?: "")
            }
        }
    }
}
