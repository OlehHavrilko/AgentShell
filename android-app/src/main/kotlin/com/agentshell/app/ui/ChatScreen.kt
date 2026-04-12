package com.agentshell.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onOpenDrawer: () -> Unit = {},
    vm: ChatViewModel = viewModel(
        factory = ChatViewModel.Factory(
            LocalContext.current.applicationContext as android.app.Application
        )
    ),
) {
    val messages by vm.messages.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val pendingApproval by vm.pendingApproval.collectAsState()
    val selectedProvider by vm.selectedProvider.collectAsState()
    val isProviderReady by vm.isProviderReady.collectAsState()
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val selectedProviderInfo = AndroidProviderCatalog.byId(selectedProvider)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Filled.Menu, contentDescription = "Open menu")
            }
            Text(
                text = selectedProviderInfo?.displayName ?: "Provider",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = vm::clearMessages, enabled = messages.isNotEmpty()) {
                Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear chat")
            }
        }

        // ── Messages area ─────────────────────────────────────────────────
        if (messages.isEmpty()) {
            ChatWelcomeState(
                providerName = selectedProviderInfo?.displayName ?: selectedProvider,
                isProviderReady = isProviderReady,
                onPromptClick = { prompt ->
                    inputText = prompt
                },
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(messages) { msg -> ChatBubble(msg) }
                if (isLoading) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Start,
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                ),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    repeat(3) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                                    shape = RoundedCornerShape(50),
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Approval banner ──────────────────────────────────────────────
        pendingApproval?.let { req ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Approval required",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    Text(
                        req.impactPreview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { vm.reject(req.approvalId) }) {
                            Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Reject")
                        }
                        Button(onClick = { vm.approve(req.approvalId) }) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Approve")
                        }
                    }
                }
            }
        }

        // ── Input bar ────────────────────────────────────────────────────
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Quick prompts when conversation is empty
                if (messages.isEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(QUICK_PROMPTS) { prompt ->
                            SuggestionChip(
                                onClick = { inputText = prompt },
                                label = { Text(prompt, style = MaterialTheme.typography.labelMedium) },
                            )
                        }
                    }
                }

                // Provider pill + input + send
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Provider selector
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable { providerMenuExpanded = true },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = selectedProviderInfo?.displayName?.take(3)?.uppercase() ?: "???",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    imageVector = Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = providerMenuExpanded,
                            onDismissRequest = { providerMenuExpanded = false },
                        ) {
                            AndroidProviderCatalog.supported.forEach { provider ->
                                DropdownMenuItem(
                                    text = { Text(provider.displayName) },
                                    onClick = {
                                        vm.selectProvider(provider.id)
                                        providerMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Describe the task…") },
                        modifier = Modifier.weight(1f),
                        singleLine = false,
                        maxLines = 4,
                        shape = RoundedCornerShape(16.dp),
                    )

                    Spacer(Modifier.width(8.dp))

                    if (isLoading) {
                        FilledIconButton(onClick = vm::stopCurrentRun) {
                            Icon(Icons.Filled.Stop, contentDescription = "Stop")
                        }
                    } else {
                        FilledIconButton(
                            onClick = {
                                val prompt = inputText.trim()
                                if (prompt.isNotEmpty()) {
                                    vm.send(prompt)
                                    inputText = ""
                                }
                            },
                            enabled = inputText.isNotBlank(),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatWelcomeState(
    providerName: String,
    isProviderReady: Boolean,
    onPromptClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Logo / title
            Text(
                "AgentShell",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                "Run tasks, inspect code, or connect to a sandbox.\nChoose a provider and describe the outcome you want.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            // Provider status
            Surface(
                color = if (isProviderReady) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                },
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = if (isProviderReady) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isProviderReady) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = if (isProviderReady) "$providerName ready" else "$providerName needs API key",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isProviderReady) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            // Quick prompts
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(QUICK_PROMPTS) { prompt ->
                    FilterChip(
                        selected = false,
                        onClick = { onPromptClick(prompt) },
                        label = { Text(prompt) },
                        leadingIcon = {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    msg.content,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        val bubbleShape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = if (isUser) 16.dp else 4.dp,
            bottomEnd = if (isUser) 4.dp else 16.dp,
        )

        Card(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(bubbleShape),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isUser -> MaterialTheme.colorScheme.primaryContainer
                    isError -> MaterialTheme.colorScheme.errorContainer
                    isTool -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Role label
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        when {
                            isUser -> "You"
                            isError -> "Error"
                            isTool -> "Tool"
                            else -> "Agent"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            isUser -> MaterialTheme.colorScheme.onPrimaryContainer
                            isError -> MaterialTheme.colorScheme.onErrorContainer
                            isTool -> MaterialTheme.colorScheme.onTertiaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                // Content
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
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
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
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Text(
                    code,
                    modifier = Modifier.padding(12.dp),
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
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            segments.forEach { it() }
        }
    }
}

private fun buildInlineAnnotatedString(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
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
                    appendInlineFormatted(line.substring(2), defaultColor)
                }
                line.matches(Regex("^\\d+\\. .*")) -> {
                    val dotIdx = line.indexOf(". ")
                    append(line.substring(0, dotIdx + 2))
                    appendInlineFormatted(line.substring(dotIdx + 2), defaultColor)
                }
                else -> appendInlineFormatted(line, defaultColor)
            }
            if (lineIdx < lines.size - 1) append('\n')
        }
    }
}

private fun AnnotatedString.Builder.appendInlineFormatted(
    text: String,
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
                withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = defaultColor)) { append(match.groupValues[4]) }
            match.groupValues[5].isNotEmpty() ->
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22888888), color = defaultColor)) { append(match.groupValues[6]) }
        }
        last = match.range.last + 1
    }
    if (last < text.length) {
        withStyle(SpanStyle(color = defaultColor)) { append(text.substring(last)) }
    }
}
