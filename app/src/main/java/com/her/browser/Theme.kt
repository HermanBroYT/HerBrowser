package com.her.browser

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object C {
    val Violet = Color(0xFF7C4DFF)
    val Pink = Color(0xFFE040A0)
    val Ink = Color(0xFF1B1446)
    val Muted = Color(0xFF8A84AD)
    val Tint = Color(0xFFE9E1FF)
    val Bg = Brush.verticalGradient(
        listOf(Color(0xFFEFE9FF), Color(0xFFF7F3FF), Color(0xFFFFF3FA))
    )
    val Brand = Brush.linearGradient(listOf(Color(0xFF8E5CF7), Color(0xFFE04CC0)))
}

@Composable
fun HerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = C.Violet,
            onPrimary = Color.White,
            surface = Color.White,
            background = Color(0xFFF7F3FF),
        ),
        content = content
    )
}
