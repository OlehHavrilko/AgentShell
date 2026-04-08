package com.agentshell.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AgentShellDarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF87B0FF),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF0B1B3C),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF17315D),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFD9E2FF),
    secondary = androidx.compose.ui.graphics.Color(0xFF9FD49A),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF11380E),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF245023),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFBBF1B4),
    tertiary = androidx.compose.ui.graphics.Color(0xFFCAB7FF),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF2E205B),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF45367A),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFE7DEFF),
    error = androidx.compose.ui.graphics.Color(0xFFFFB4AB),
    errorContainer = androidx.compose.ui.graphics.Color(0xFF93000A),
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6),
    background = androidx.compose.ui.graphics.Color(0xFF111318),
    onBackground = androidx.compose.ui.graphics.Color(0xFFE2E2E9),
    surface = androidx.compose.ui.graphics.Color(0xFF111318),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE2E2E9),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF43474E),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFC3C7D0),
    outline = androidx.compose.ui.graphics.Color(0xFF8D9199),
)

private val AgentShellLightColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF365DA8),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFD9E2FF),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF001944),
    secondary = androidx.compose.ui.graphics.Color(0xFF456743),
    onSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFC7EEC0),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF022108),
    tertiary = androidx.compose.ui.graphics.Color(0xFF5F4D91),
    onTertiary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFE7DEFF),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF1A0A49),
)

@Composable
fun AgentShellTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) AgentShellDarkColors else AgentShellLightColors,
        content = content,
    )
}
