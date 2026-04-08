package com.agentshell.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentshell.app.db.AgentDatabase
import com.agentshell.app.db.AuditEntity
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MemoryViewModel(private val db: AgentDatabase) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<AuditEntity>>(emptyList())
    val results: StateFlow<List<AuditEntity>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun onQueryChange(q: String) { _query.value = q }

    fun search() {
        val q = _query.value.trim()
        viewModelScope.launch {
            _isSearching.value = true
            if (q.isBlank()) {
                db.auditDao().recentEvents(50).first().also { _results.value = it }
            } else {
                db.auditDao().searchEvents(q).first().also { _results.value = it }
            }
            _isSearching.value = false
        }
    }

    // Load recent events on init
    init {
        viewModelScope.launch {
            db.auditDao().recentEvents(50).collect { events -> _results.value = events }
        }
    }

    class Factory(private val db: AgentDatabase) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MemoryViewModel(db) as T
    }
}

@Composable
fun MemoryScreen() {
    val context = LocalContext.current
    val db = AgentDatabase.getInstance(context)
    val vm: MemoryViewModel = viewModel(factory = MemoryViewModel.Factory(db))

    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val isSearching by vm.isSearching.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Memory", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Search recent audit entries and memory traces captured from agent runs.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { vm.onQueryChange(it) },
            label = { Text("Search memory") },
            placeholder = { Text("Try run id, tool name, or event type") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { vm.search() }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search() }),
        )

        if (isSearching) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        if (results.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = 20.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Card {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("No memory entries found", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Entries are indexed after each agent run. Start a run from Chat or broaden the search query.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = "${results.size} entries",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(results) { entry -> MemoryEntryCard(entry) }
            }
        }
    }
}

@Composable
private fun MemoryEntryCard(entry: AuditEntity) {
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(entry.type, style = MaterialTheme.typography.labelSmall)
                Text(
                    fmt.format(Date(entry.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            entry.payload?.takeIf { it.isNotBlank() }?.let { payload ->
                Text(payload.take(250), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
