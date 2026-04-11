package com.agentshell.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agentshell.app.service.SandboxMode
import com.agentshell.app.service.SandboxState
import com.agentshell.app.viewmodel.SandboxViewModel
import com.agentshell.app.ui.theme.*

@Composable
fun SandboxScreen(vm: SandboxViewModel = viewModel(
    factory = SandboxViewModel.Factory(LocalContext.current.applicationContext as android.app.Application)
)) {
    val state by vm.sandboxState.collectAsState()
    val mode by vm.currentMode.collectAsState()
    val lines by vm.terminalLines.collectAsState()
    val isDark = isSystemInDarkTheme()

    Column(Modifier.fillMaxSize()) {
        // ── Header ────────────────────────────────────────────────────────
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Title row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Sandbox",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))

                    // Mode indicator
                    Surface(
                        color = if (mode == SandboxMode.PROOT) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text(
                            text = if (mode == SandboxMode.PROOT) "Alpine" else "Termux",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (mode == SandboxMode.PROOT) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            },
                        )
                    }
                }

                // Controls row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Mode toggle (only when not running)
                    if (state is SandboxState.Idle || state is SandboxState.Error) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Proot",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (mode == SandboxMode.PROOT) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                            Switch(
                                checked = mode == SandboxMode.TERMUX,
                                onCheckedChange = { vm.toggleMode(!it) },
                            )
                            Text(
                                "Termux",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (mode == SandboxMode.TERMUX) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    } else {
                        Spacer(Modifier.width(80.dp))
                    }

                    // Action buttons
                    when {
                        state is SandboxState.Running -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { vm.clearTerminal() }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Clear")
                                }
                                IconButton(onClick = { vm.restart() }) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Restart")
                                }
                                FilledTonalButton(onClick = { vm.stop() }) {
                                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Stop")
                                }
                            }
                        }
                        state is SandboxState.Starting || state is SandboxState.Stopping -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (state is SandboxState.Starting) "Starting…" else "Stopping…",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        else -> {
                            Button(onClick = { vm.start() }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Start")
                            }
                        }
                    }
                }
            }
        }

        // Termux not installed warning
        if (mode == SandboxMode.TERMUX && !vm.isTermuxAvailable) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "Termux is not installed. Install from F-Droid to use this mode.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        // ── Main content ─────────────────────────────────────────────────
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state is SandboxState.Running) {
                TerminalView(
                    lines = lines,
                    onCommand = { vm.sendInput(it) },
                    isDark = isDark,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = when (state) {
                                is SandboxState.Idle -> Icons.Filled.PowerSettingsNew
                                is SandboxState.Error -> Icons.Filled.Error
                                else -> Icons.Outlined.Schedule
                            },
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        )
                        Text(
                            text = when (state) {
                                is SandboxState.Idle -> "Sandbox stopped\nPress Start to launch Alpine Linux"
                                is SandboxState.Error -> "Error: ${(state as SandboxState.Error).cause.message}"
                                else -> ""
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val bgColor = if (isDark) TerminalBg else Color(0xFFFAFAFA)
    val fgColor = if (isDark) TerminalFg else Color(0xFF1E1E1E)
    val greenColor = if (isDark) TerminalGreen else Color(0xFF16A34A)
    val redColor = if (isDark) TerminalRed else Color(0xFFDC2626)
    val yellowColor = if (isDark) TerminalYellow else Color(0xFFD97706)
    val blueColor = if (isDark) TerminalBlue else Color(0xFF2563EB)
    val commentColor = if (isDark) TerminalComment else Color(0xFF9CA3AF)
    val inputBgColor = if (isDark) TerminalInput else Color(0xFFF0F0F0)

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    Column(modifier.background(bgColor)) {
        // Output area
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            items(lines) { line ->
                TerminalLine(
                    line = line,
                    fg = fgColor,
                    green = greenColor,
                    red = redColor,
                    yellow = yellowColor,
                    blue = blueColor,
                    comment = commentColor,
                )
            }
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(inputBgColor)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$ ",
                color = greenColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = fgColor,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = greenColor,
                    unfocusedBorderColor = commentColor,
                    focusedTextColor = fgColor,
                    unfocusedTextColor = fgColor,
                    cursorColor = greenColor,
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
                        color = commentColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                    )
                },
            )
        }
    }
}

@Composable
private fun TerminalLine(
    line: String,
    fg: Color,
    green: Color,
    red: Color,
    yellow: Color,
    blue: Color,
    comment: Color,
) {
    val annotated = buildAnnotatedString {
        val style = when {
            line.startsWith("+") -> SpanStyle(color = green)
            line.startsWith("-") && !line.startsWith("--") -> SpanStyle(color = red)
            line.startsWith("error", ignoreCase = true) ||
                    line.startsWith("fatal", ignoreCase = true) -> SpanStyle(color = red)
            line.startsWith("warn", ignoreCase = true) -> SpanStyle(color = yellow)
            line.startsWith("$") -> SpanStyle(color = blue)
            line.startsWith("#") -> SpanStyle(color = comment)
            line.startsWith("[sandbox process") -> SpanStyle(color = comment)
            else -> SpanStyle(color = fg)
        }
        withStyle(style) { append(line) }
    }
    Text(
        annotated,
        style = androidx.compose.ui.text.TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = fg,
        ),
        modifier = Modifier.padding(vertical = 1.dp),
    )
}
