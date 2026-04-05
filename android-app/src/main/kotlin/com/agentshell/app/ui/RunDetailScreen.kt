package com.agentshell.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentshell.app.db.AgentDatabase
import com.agentshell.app.db.StepEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RunDetailViewModel(private val db: AgentDatabase) : ViewModel() {
    private val _steps = MutableStateFlow<List<StepEntity>>(emptyList())
    val steps = _steps.asStateFlow()

    fun loadSteps(runId: String) {
        viewModelScope.launch {
            db.stepDao().stepsForRun(runId).collect { _steps.value = it }
        }
    }
}

@Composable
fun RunDetailScreen(runId: String) {
    // In a real app, get VM via Hilt or a factory
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Run: …${runId.takeLast(8)}", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Steps timeline coming soon.", style = MaterialTheme.typography.bodyMedium)
    }
}
