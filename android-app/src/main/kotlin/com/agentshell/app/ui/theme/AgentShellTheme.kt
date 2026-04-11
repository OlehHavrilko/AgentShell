package com.agentshell.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = BluePrimary,
    onPrimary = DarkSurface,
    primaryContainer = BluePrimaryDark,
    onPrimaryContainer = BluePrimaryLight,

    secondary = GreenSuccess,
    onSecondary = DarkSurface,
    secondaryContainer = GreenSuccessDark,
    onSecondaryContainer = GreenSuccessLight,

    tertiary = PurpleAccent,
    onTertiary = DarkSurface,
    tertiaryContainer = PurpleAccentDark,
    onTertiaryContainer = PurpleAccentLight,

    error = RedError,
    onError = DarkSurface,
    errorContainer = RedErrorDark,
    onErrorContainer = RedErrorLight,

    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
)

private val LightColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = LightSurface,
    primaryContainer = BluePrimaryLight,
    onPrimaryContainer = BluePrimaryDark,

    secondary = GreenSuccess,
    onSecondary = LightSurface,
    secondaryContainer = GreenSuccessLight,
    onSecondaryContainer = GreenSuccessDark,

    tertiary = PurpleAccent,
    onTertiary = LightSurface,
    tertiaryContainer = PurpleAccentLight,
    onTertiaryContainer = PurpleAccentDark,

    error = RedError,
    onError = LightSurface,
    errorContainer = RedErrorDark,
    onErrorContainer = RedErrorLight,

    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
)

@Composable
fun AgentShellTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
