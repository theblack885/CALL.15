package com.aicallguard.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF07090F)
val Accent = Color(0xFF5CF2D6)
val Violet = Color(0xFF8B7CFF)

@Composable
fun GuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            secondary = Violet,
            background = Bg,
            surface = Color(0xFF0F1420),
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}
