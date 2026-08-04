package com.movedados.witon.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// O Wi Ton e dark-first: a captura acontece com a camera aberta e tela clara atrapalha.
private val WiTonColors = darkColorScheme(
    primary = BrandRed,
    onPrimary = Color.White,
    secondary = StatusBlue,
    background = Slate900,
    onBackground = Color.White,
    surface = Slate800,
    onSurface = Color.White,
    surfaceVariant = Slate700,
    onSurfaceVariant = Slate300,
    outline = Slate600,
    error = StatusRed,
    onError = Color.White
)

@Composable
fun WiTonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = WiTonColors,
        typography = WiTonTypography,
        content = content
    )
}
