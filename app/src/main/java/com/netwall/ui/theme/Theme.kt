package com.netwall.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Strict black & white identity. App icons provide the only color on screen.
private val LightColors = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color.Black,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF333333),
    onSecondary = Color.White,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF555555),
    background = Color.White,
    onBackground = Color.Black,
    outline = Color(0xFFE0E0E0),
    outlineVariant = Color(0xFFEEEEEE),
)

private val DarkColors = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color.White,
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFFCCCCCC),
    onSecondary = Color.Black,
    surface = Color(0xFF141414),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFAAAAAA),
    background = Color(0xFF0A0A0A),
    onBackground = Color(0xFFF5F5F5),
    outline = Color(0xFF2A2A2A),
    outlineVariant = Color(0xFF1E1E1E),
)

@Composable
fun NetWallTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
