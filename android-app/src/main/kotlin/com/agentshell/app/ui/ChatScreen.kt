package com.agentshell.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentshell.app.viewmodel.ChatMessage
import com.agentshell.app.viewmodel.ChatViewModel

private val LOCAL_PROVIDERS = setOf("ollama", "llama_cpp")

private val ALL_PROVIDERS = listOf(
    "ollama" to "Ollama (Local)",
    "openai" to "OpenAI",
    "groq" to "Groq",
    "deepseek" to "DeepSeek",
    "mistral" to "Mistral",
    "openrouter" to "OpenRouter",
    "anthropic" to "Anthropic",
    "gemini" to "Gemini",
    "cohere" to "Cohere",
    "llama_cpp" to "Llama.cpp (Local)",
)

@Composable
fun ChatScreen(vm: ChatViewModel = viewModel()) {
    val messages by vm.messages.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val pendingApproval by vm.pendingApproval.collectAsState()
    val selectedProvider by vm.selectedProvider.collectAsState()
    val serviceConnected by vm.serviceConnected.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var showProviderMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        // ── Provider selector + status bar ───────────────────────────────────
        Surface(tonalElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedProvider in LOCAL_PROVIDERS) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            "OFFLINE",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }

                Box {
                    TextButton(onClick = { showProviderMenu = true }) {
                        Text(
                            ALL_PROVIDERS.find { it.first == selectedProvider }?.second
                                ?: selectedProvider,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select provider")
                    }
                    DropdownMenu(
                        expanded = showProviderMenu,
                        onDismissRequest = { showProviderMenu = false }
                    ) {
                        ALL_PROVIDERS.forEach { (id, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = { vm.selectProvider(id); showProviderMenu = false },
                                leadingIcon = if (id == selectedProvider) {
                                    { Text("✓") }
                                } else null
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                Surface(
                    color = if (serviceConnected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        if (serviceConnected) "connected" else "idle",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = if (serviceConnected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ── Approval banner ──────────────────────────────────────────────────
        pendingApproval?.let { req ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
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

        // ── Message list ─────────────────────────────────────────────────────
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            items(messages) { msg -> ChatBubble(msg) }
        }

        if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())

        // ── Input row ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
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
    }
}

@Composable
fun ChatBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    val isSystem = msg.role == "system"
    val isTool = msg.role == "tool"
    val isError = msg.role == "error"

    if (isSystem) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                msg.content,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = when {
            isUser -> Arrangement.End
            isTool -> Arrangement.Start
            else -> Arrangement.Start
        }
    ) {
        Card(
            modifier = Modifier.widthIn(max = 300.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isUser -> MaterialTheme.colorScheme.primaryContainer
                    isError -> MaterialTheme.colorScheme.errorContainer
                    isTool -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Text(
                msg.content,
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    isError -> MaterialTheme.colorScheme.onErrorContainer
                    isTool -> MaterialTheme.colorScheme.onTertiaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}
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
