package com.agentshell.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
    object Settings : NavRoute("settings", "Settings")
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val items = listOf(NavRoute.Chat, NavRoute.Runs, NavRoute.Memory, NavRoute.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = {
                            when (screen) {
                                is NavRoute.Chat -> Icon(Icons.Filled.Chat, contentDescription = screen.label)
                                is NavRoute.Runs -> Icon(Icons.Filled.List, contentDescription = screen.label)
                                is NavRoute.Memory -> Icon(Icons.Filled.Memory, contentDescription = screen.label)
                                is NavRoute.Settings -> Icon(Icons.Filled.Settings, contentDescription = screen.label)
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
        NavHost(navController, startDestination = NavRoute.Chat.route, Modifier.padding(innerPadding)) {
            composable(NavRoute.Chat.route) { ChatScreen() }
            composable(NavRoute.Runs.route) { RunsScreen(navController) }
            composable(NavRoute.Memory.route) { MemoryScreen() }
            composable(NavRoute.Settings.route) { SettingsScreen() }
            composable("run_detail/{runId}") { backStackEntry ->
                RunDetailScreen(runId = backStackEntry.arguments?.getString("runId") ?: "")
            }
        }
    }
}
