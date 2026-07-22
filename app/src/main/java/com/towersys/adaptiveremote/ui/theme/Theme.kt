package com.towersys.adaptiveremote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FB7FF),
    onPrimary = Color(0xFF002F64),
    secondary = Color(0xFFBEC7DC),
    background = Color(0xFF090B10),
    surface = Color(0xFF11141B),
    surfaceContainer = Color(0xFF171B24),
    surfaceContainerLow = Color(0xFF12161E),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF315E96),
    secondary = Color(0xFF566179),
    background = Color(0xFFF9F9FF),
    surface = Color(0xFFF9F9FF),
)

@Composable
fun AdaptiveRemoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

