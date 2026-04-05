package com.agentshell.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentshell.app.viewmodel.ChatMessage
import com.agentshell.app.viewmodel.ChatViewModel

@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
    val messages by vm.messages.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val pendingApproval by vm.pendingApproval.collectAsState()
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        // Approval banner
        pendingApproval?.let { req ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("⚠ Approval Required", style = MaterialTheme.typography.titleSmall)
                    Text(req.impactPreview, style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.approve(req.approvalId) }) { Text("Approve") }
                        OutlinedButton(onClick = { vm.reject(req.approvalId) }) { Text("Reject") }
                    }
                }
            }
        }

        // Message list
        LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            items(messages) { msg -> ChatBubble(msg) }
        }

        // Input row
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = { Text("Enter command or goal…") },
                modifier = Modifier.weight(1f),
                singleLine = false,
                maxLines = 4
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        vm.send(inputText.trim())
                        inputText = ""
                    }
                },
                enabled = !isLoading
            ) { Text("Send") }
        }

        if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
fun ChatBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(msg.content, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
