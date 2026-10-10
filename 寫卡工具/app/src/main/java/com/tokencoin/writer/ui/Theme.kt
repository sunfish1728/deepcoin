package com.tokencoin.writer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Gold = Color(0xFFB98900)
private val GoldLight = Color(0xFFFFD54F)

private val LightColors = lightColorScheme(
    primary = Gold,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE8A3),
    onPrimaryContainer = Color(0xFF3A2A00),
    secondary = Color(0xFF6B5D3F),
)

private val DarkColors = darkColorScheme(
    primary = GoldLight,
    onPrimary = Color(0xFF3A2A00),
    primaryContainer = Color(0xFF5A4300),
    onPrimaryContainer = Color(0xFFFFE8A3),
    secondary = Color(0xFFD6C5A0),
)

@Composable
fun TokenCoinTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
