package com.agentshell.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
    object Providers : NavRoute("providers", "LLM Providers")
    object WorkflowBuilder : NavRoute("workflow_builder", "Workflow Builder")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NavRoute.Chat.route
    val topBarTitle = when {
        currentRoute == NavRoute.Runs.route -> NavRoute.Runs.label
        currentRoute == NavRoute.Memory.route -> NavRoute.Memory.label
        currentRoute == NavRoute.Sandbox.route -> NavRoute.Sandbox.label
        currentRoute == NavRoute.Settings.route -> NavRoute.Settings.label
        currentRoute == NavRoute.Providers.route -> NavRoute.Providers.label
        currentRoute == NavRoute.WorkflowBuilder.route -> NavRoute.WorkflowBuilder.label
        currentRoute.startsWith("run_detail") -> "Run details"
        else -> null
    }

    Scaffold(
        topBar = {
            if (topBarTitle != null) {
                TopAppBar(
                    title = {
                        Text(
                            text = topBarTitle,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavRoute.Chat.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(NavRoute.Chat.route) {
                ChatScreen(onOpenSettings = { navController.navigate(NavRoute.Settings.route) })
            }
            composable(NavRoute.Runs.route) { RunsScreen(navController) }
            composable(NavRoute.Memory.route) { MemoryScreen() }
            composable(NavRoute.Sandbox.route) { SandboxScreen() }
            composable(NavRoute.Settings.route) { SettingsScreen(navController) }
            composable(NavRoute.Providers.route) { ProvidersScreen() }
            composable(NavRoute.WorkflowBuilder.route) { WorkflowBuilderScreen() }
            composable("run_detail/{runId}") { backStackEntry ->
                RunDetailScreen(runId = backStackEntry.arguments?.getString("runId") ?: "")
            }
        }
    }
}
