package com.agentshell.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MemoryScreen() {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(listOf<String>()) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Memory", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search memory…") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { /* TODO: query SqliteMemoryStore */ }) { Text("Search") }
        Spacer(Modifier.height(12.dp))
        LazyColumn {
            items(results) { entry ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(entry, Modifier.padding(12.dp))
                }
            }
        }
    }
}
