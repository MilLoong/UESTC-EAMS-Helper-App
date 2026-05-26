package edu.uestc.eams.helper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF5B7CFF),
        secondary = Color(0xFF6BCB9A),
        surface = Color(0xFFF4F6FB),
        background = Color(0xFFECEFF6),
        onSurface = Color(0xFF1A1D26),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF8BA4FF),
        secondary = Color(0xFF7AD4A8),
        surface = Color(0xFF1E2230),
        background = Color(0xFF141820),
    )

@Composable
fun UestcHelperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
