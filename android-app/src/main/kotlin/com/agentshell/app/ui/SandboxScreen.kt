package com.agentshell.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentshell.app.service.SandboxMode
import com.agentshell.app.service.SandboxState
import com.agentshell.app.viewmodel.SandboxViewModel

private val TerminalBg = Color(0xFF1E1E2E)
private val TerminalFg = Color(0xFFCDD6F4)
private val TerminalGreen = Color(0xFF50FA7B)
private val TerminalRed = Color(0xFFFF5555)
private val TerminalYellow = Color(0xFFFFB86C)
private val TerminalBlue = Color(0xFF89B4FA)
private val TerminalComment = Color(0xFF6272A4)
private val TerminalInput = Color(0xFF313244)

@Composable
fun SandboxScreen(vm: SandboxViewModel = viewModel()) {
    val state by vm.sandboxState.collectAsState()
    val mode by vm.currentMode.collectAsState()
    val lines by vm.terminalLines.collectAsState()

    Column(Modifier.fillMaxSize()) {
        // ── Sticky header ────────────────────────────────────────────────────
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sandbox", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))

                // Mode toggle (only when not running)
                Text(
                    if (mode == SandboxMode.PROOT) "Proot" else "Termux",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                Switch(
                    checked = mode == SandboxMode.PROOT,
                    onCheckedChange = { vm.toggleMode(it) },
                    enabled = state is SandboxState.Idle || state is SandboxState.Error,
                )
                Spacer(Modifier.width(8.dp))

                when {
                    state is SandboxState.Running -> {
                        IconButton(onClick = { vm.clearTerminal() }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear terminal")
                        }
                        IconButton(onClick = { vm.restart() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Restart")
                        }
                        OutlinedButton(onClick = { vm.stop() }) { Text("Stop") }
                    }
                    state is SandboxState.Starting || state is SandboxState.Stopping -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    else -> {
                        Button(onClick = { vm.start() }) { Text("Start") }
                    }
                }
            }
        }

        // Termux not installed warning
        if (mode == SandboxMode.TERMUX && !vm.isTermuxAvailable) {
            Surface(color = MaterialTheme.colorScheme.errorContainer) {
                Text(
                    "Termux is not installed. Install it from F-Droid, then switch back here.",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // ── Main content ─────────────────────────────────────────────────────
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state is SandboxState.Running) {
                TerminalView(
                    lines = lines,
                    onCommand = { vm.sendInput(it) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val msg = when (state) {
                        is SandboxState.Idle -> "Sandbox stopped.\nPress Start to launch Alpine Linux."
                        is SandboxState.Starting -> "Starting…"
                        is SandboxState.Stopping -> "Stopping…"
                        is SandboxState.Error ->
                            "Error: ${(state as SandboxState.Error).cause.message}"
                        else -> ""
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (state is SandboxState.Starting || state is SandboxState.Stopping) {
                            CircularProgressIndicator()
                        }
                        Text(
                            msg,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ── Terminal view ──────────────────────────────────────────────────────────────

@Composable
fun TerminalView(
    lines: List<String>,
    onCommand: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    Column(modifier.background(TerminalBg)) {
        // Output area
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            items(lines) { line -> TerminalLine(line) }
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TerminalInput)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$ ",
                color = TerminalGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = TerminalFg
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TerminalGreen,
                    unfocusedBorderColor = TerminalComment,
                    focusedTextColor = TerminalFg,
                    unfocusedTextColor = TerminalFg,
                    cursorColor = TerminalGreen,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (input.isNotBlank()) {
                        onCommand(input.trim())
                        input = ""
                    }
                }),
                placeholder = {
                    Text(
                        "type command…",
                        color = TerminalComment,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                }
            )
        }
    }
}

@Composable
private fun TerminalLine(line: String) {
    val annotated = buildAnnotatedString {
        val style = when {
            line.startsWith("+") -> SpanStyle(color = TerminalGreen)
            line.startsWith("-") && !line.startsWith("--") -> SpanStyle(color = TerminalRed)
            line.startsWith("error", ignoreCase = true) ||
                    line.startsWith("fatal", ignoreCase = true) -> SpanStyle(color = TerminalRed)
            line.startsWith("warn", ignoreCase = true) -> SpanStyle(color = TerminalYellow)
            line.startsWith("$") -> SpanStyle(color = TerminalBlue)
            line.startsWith("#") -> SpanStyle(color = TerminalComment)
            line.startsWith("[sandbox process") -> SpanStyle(color = TerminalComment)
            else -> SpanStyle(color = TerminalFg)
        }
        withStyle(style) { append(line) }
    }
    Text(
        annotated,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            lineHeight = 16.sp
        ),
        modifier = Modifier.padding(vertical = 1.dp)
    )
}
