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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.material3.IconButton
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Color
import com.agentshell.app.llm.AndroidProviderCatalog
import com.agentshell.app.viewmodel.ChatMessage
import com.agentshell.app.viewmodel.ChatViewModel

private val QUICK_PROMPTS = listOf(
    "Summarize this project",
    "Review recent changes",
    "Start the sandbox and inspect the environment",
)

@Composable
fun ChatScreen(
    onOpenSettings: () -> Unit = {},
    vm: ChatViewModel = viewModel(),
) {
    val messages by vm.messages.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val pendingApproval by vm.pendingApproval.collectAsState()
    val selectedProvider by vm.selectedProvider.collectAsState()
    val serviceConnected by vm.serviceConnected.collectAsState()
    val isProviderReady by vm.isProviderReady.collectAsState()
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
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("AgentShell", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Chat-first workspace for agent tasks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (messages.isNotEmpty()) {
                        IconButton(onClick = { vm.clearMessages() }) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = "Clear conversation",
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Open settings",
                        )
                    }
                }

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

        if (!isProviderReady) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "⚡ ${selectedProviderInfo?.displayName ?: selectedProvider} needs an API key to work.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onOpenSettings) { Text("Configure") }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    val isSystem = msg.role == "system"
    val isTool = msg.role == "tool"
    val isError = msg.role == "error"
    val clipboardManager = LocalClipboardManager.current

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
                .widthIn(max = 360.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { clipboardManager.setText(AnnotatedString(msg.content)) },
                ),
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
                if (msg.role == "assistant") {
                    MarkdownText(
                        text = msg.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
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
}

/**
 * Renders a minimal subset of Markdown as an AnnotatedString.
 * Handles: **bold**, *italic*, `inline code`, # headings (h1-h3), and - bullet list items.
 * Code blocks (```...```) are rendered as a Surface with monospace text.
 * Everything else is left as-is.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val codeBlockColor = MaterialTheme.colorScheme.surfaceVariant
    val codeTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    val codeBlockRegex = Regex("```[\\w]*\\n?([\\s\\S]*?)```", RegexOption.MULTILINE)
    val segments = mutableListOf<@Composable () -> Unit>()
    var lastEnd = 0
    for (match in codeBlockRegex.findAll(text)) {
        if (match.range.first > lastEnd) {
            val chunk = text.substring(lastEnd, match.range.first)
            val annotated = buildInlineAnnotatedString(chunk, style, color)
            segments.add { Text(annotated, modifier = modifier, style = style) }
        }
        val code = match.groupValues[1].trimEnd('\n')
        segments.add {
            Surface(
                color = codeBlockColor,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Text(
                    code,
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = codeTextColor,
                    ),
                )
            }
        }
        lastEnd = match.range.last + 1
    }
    if (lastEnd < text.length) {
        val chunk = text.substring(lastEnd)
        val annotated = buildInlineAnnotatedString(chunk, style, color)
        segments.add { Text(annotated, modifier = modifier, style = style) }
    }
    if (segments.isEmpty()) {
        val annotated = buildInlineAnnotatedString(text, style, color)
        Text(annotated, modifier = modifier, style = style)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            segments.forEach { it() }
        }
    }
}

private fun buildInlineAnnotatedString(
    text: String,
    style: TextStyle,
    defaultColor: Color,
): AnnotatedString {
    return buildAnnotatedString {
        val lines = text.split('\n')
        lines.forEachIndexed { lineIdx, rawLine ->
            val line = rawLine
            when {
                line.startsWith("### ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = style.fontSize * 1.1f, color = defaultColor)) {
                        append(line.removePrefix("### "))
                    }
                }
                line.startsWith("## ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = style.fontSize * 1.2f, color = defaultColor)) {
                        append(line.removePrefix("## "))
                    }
                }
                line.startsWith("# ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = style.fontSize * 1.35f, color = defaultColor)) {
                        append(line.removePrefix("# "))
                    }
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    append("• ")
                    appendInlineFormatted(line.substring(2), style, defaultColor)
                }
                line.matches(Regex("^\\d+\\. .*")) -> {
                    val dotIdx = line.indexOf(". ")
                    append(line.substring(0, dotIdx + 2))
                    appendInlineFormatted(line.substring(dotIdx + 2), style, defaultColor)
                }
                else -> appendInlineFormatted(line, style, defaultColor)
            }
            if (lineIdx < lines.size - 1) append('\n')
        }
    }
}

private fun AnnotatedString.Builder.appendInlineFormatted(
    text: String,
    style: TextStyle,
    defaultColor: Color,
) {
    val inlineRegex = Regex("(\\*\\*(.+?)\\*\\*)|(\\*(.+?)\\*)|(`(.+?)`)")
    var last = 0
    for (match in inlineRegex.findAll(text)) {
        if (match.range.first > last) {
            withStyle(SpanStyle(color = defaultColor)) { append(text.substring(last, match.range.first)) }
        }
        when {
            match.groupValues[1].isNotEmpty() ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor)) { append(match.groupValues[2]) }
            match.groupValues[3].isNotEmpty() ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = defaultColor)) { append(match.groupValues[4]) }
            match.groupValues[5].isNotEmpty() ->
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = androidx.compose.ui.graphics.Color(0x22888888), color = defaultColor)) { append(match.groupValues[6]) }
        }
        last = match.range.last + 1
    }
    if (last < text.length) {
        withStyle(SpanStyle(color = defaultColor)) { append(text.substring(last)) }
    }
}
