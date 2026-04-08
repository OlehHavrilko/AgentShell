package com.agentshell.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentshell.app.llm.AndroidProviderCatalog
import com.agentshell.app.viewmodel.ChatMessage
import com.agentshell.app.viewmodel.ChatViewModel

private val QUICK_PROMPTS = listOf(
    "Summarize this project",
    "Review recent changes",
    "Start the sandbox and inspect the environment",
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
    val providers = AndroidProviderCatalog.supported
    val selectedProviderInfo = AndroidProviderCatalog.byId(selectedProvider)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        Surface(tonalElevation = 2.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        TextButton(onClick = { showProviderMenu = true }) {
                            Text(
                                selectedProviderInfo?.displayName ?: selectedProvider,
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select provider")
                        }
                        DropdownMenu(
                            expanded = showProviderMenu,
                            onDismissRequest = { showProviderMenu = false },
                        ) {
                            providers.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.displayName) },
                                    onClick = {
                                        vm.selectProvider(provider.id)
                                        showProviderMenu = false
                                    },
                                    leadingIcon = if (provider.id == selectedProvider) {
                                        { Text("✓") }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    StatusPill(
                        text = if (serviceConnected) "Service connected" else "Ready",
                        containerColor = if (serviceConnected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (serviceConnected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(
                        text = if (selectedProviderInfo?.isLocal == true) "Local provider" else "Cloud provider",
                        containerColor = if (selectedProviderInfo?.isLocal == true) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        },
                        contentColor = if (selectedProviderInfo?.isLocal == true) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        },
                    )
                    selectedProviderInfo?.takeIf { it.defaultModel.isNotBlank() }?.let {
                        StatusPill(
                            text = it.defaultModel,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        pendingApproval?.let { req ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Approval required", style = MaterialTheme.typography.titleMedium)
                    Text(req.impactPreview, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.approve(req.approvalId) }) { Text("Approve") }
                        OutlinedButton(onClick = { vm.reject(req.approvalId) }) { Text("Reject") }
                    }
                }
            }
        }

        if (messages.isEmpty()) {
            ChatWelcomeState(
                onPromptClick = { prompt ->
                    inputText = prompt
                },
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(messages) { msg -> ChatBubble(msg) }
            }
        }

        if (isLoading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        Surface(tonalElevation = 3.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (messages.isEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(QUICK_PROMPTS) { prompt ->
                            SuggestionChip(
                                onClick = { inputText = prompt },
                                label = { Text(prompt) },
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Describe the task, command, or goal…") },
                        modifier = Modifier.weight(1f),
                        singleLine = false,
                        maxLines = 4,
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val prompt = inputText.trim()
                            if (prompt.isNotEmpty()) {
                                vm.send(prompt)
                                inputText = ""
                            }
                        },
                        enabled = !isLoading && inputText.isNotBlank(),
                    ) {
                        Text("Run")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatWelcomeState(
    onPromptClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("AgentShell", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Run a quick task, inspect the project, or connect a local sandbox. Choose a provider and describe the outcome you want.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(QUICK_PROMPTS) { prompt ->
                        SuggestionChip(
                            onClick = { onPromptClick(prompt) },
                            label = { Text(prompt) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Surface(color = containerColor, shape = MaterialTheme.shapes.small) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
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
            contentAlignment = Alignment.Center,
        ) {
            Text(
                msg.content,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .widthIn(max = 360.dp),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isUser -> MaterialTheme.colorScheme.primaryContainer
                    isError -> MaterialTheme.colorScheme.errorContainer
                    isTool -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    when {
                        isUser -> "You"
                        isError -> "Error"
                        isTool -> "Tool output"
                        else -> "Agent"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(
                    msg.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        isError -> MaterialTheme.colorScheme.onErrorContainer
                        isTool -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}
