package edu.uestc.eams.helper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 可选主题。 */
enum class AppTheme(val displayName: String, val key: String) {
    BLUE("天空蓝", "blue"),
    PINK("樱花粉", "pink"),
    RED("活力红", "red"),
    GREEN("清新绿", "green"),
    PURPLE("梦幻紫", "purple"),
    ORANGE("暖阳橙", "orange"),
    TEAL("青碧色", "teal"),
}

private val LightBlue = Color(0xFF5BA3D9)
private val PaleBlue = Color(0xFFE8F4FB)
private val IceBlue = Color(0xFFF7FBFE)
private val DeepBlueText = Color(0xFF1A4A6E)
private val White = Color(0xFFFFFFFF)

private val LightColors =
    lightColorScheme(
        primary = LightBlue,
        onPrimary = White,
        primaryContainer = PaleBlue,
        onPrimaryContainer = DeepBlueText,
        secondary = LightBlue,
        onSecondary = White,
        secondaryContainer = PaleBlue,
        onSecondaryContainer = DeepBlueText,
        tertiary = LightBlue,
        onTertiary = White,
        tertiaryContainer = PaleBlue,
        onTertiaryContainer = DeepBlueText,
        background = IceBlue,
        onBackground = DeepBlueText,
        surface = White,
        onSurface = DeepBlueText,
        surfaceVariant = PaleBlue,
        onSurfaceVariant = Color(0xFF4A6578),
        surfaceTint = Color.Transparent,
        surfaceBright = White,
        surfaceDim = PaleBlue,
        surfaceContainerLowest = White,
        surfaceContainerLow = White,
        surfaceContainer = White,
        surfaceContainerHigh = PaleBlue,
        surfaceContainerHighest = PaleBlue,
        outline = Color(0xFFC5D6E4),
        outlineVariant = Color(0xFFE2EEF6),
        inverseSurface = DeepBlueText,
        inverseOnSurface = IceBlue,
        inversePrimary = PaleBlue,
        error = Color(0xFFB3261E),
        onError = White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        scrim = Color(0x99000000),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF8EC4E8),
        onPrimary = Color(0xFF0C2E45),
        primaryContainer = Color(0xFF2A4A63),
        onPrimaryContainer = PaleBlue,
        secondary = Color(0xFF8EC4E8),
        onSecondary = Color(0xFF0C2E45),
        secondaryContainer = Color(0xFF2A4A63),
        onSecondaryContainer = PaleBlue,
        tertiary = Color(0xFF8EC4E8),
        onTertiary = Color(0xFF0C2E45),
        tertiaryContainer = Color(0xFF2A4A63),
        onTertiaryContainer = PaleBlue,
        background = Color(0xFF12171C),
        onBackground = Color(0xFFE6EEF4),
        surface = Color(0xFF1A2228),
        onSurface = Color(0xFFE6EEF4),
        surfaceVariant = Color(0xFF243039),
        onSurfaceVariant = Color(0xFFB0C4D2),
        surfaceTint = Color.Transparent,
        surfaceBright = Color(0xFF243039),
        surfaceDim = Color(0xFF12171C),
        surfaceContainerLowest = Color(0xFF12171C),
        surfaceContainerLow = Color(0xFF1A2228),
        surfaceContainer = Color(0xFF1A2228),
        surfaceContainerHigh = Color(0xFF243039),
        surfaceContainerHighest = Color(0xFF2A4A63),
        outline = Color(0xFF4A6578),
        outlineVariant = Color(0xFF2E3C46),
        inverseSurface = IceBlue,
        inverseOnSurface = DeepBlueText,
        inversePrimary = LightBlue,
        error = Color(0xFFF2B8B5),
        onError = Color(0xFF601410),
        errorContainer = Color(0xFF8C1D18),
        onErrorContainer = Color(0xFFFFDAD6),
        scrim = Color(0x99000000),
    )

private fun appColorScheme(themeKey: String, dark: Boolean): ColorScheme {
    if (themeKey == AppTheme.BLUE.key) {
        return if (dark) DarkColors else LightColors
    }
    val base =
        when (themeKey) {
            AppTheme.PINK.key -> Color(0xFFD77AA0)
            AppTheme.RED.key -> Color(0xFFC86A63)
            AppTheme.GREEN.key -> Color(0xFF5E9B7B)
            AppTheme.PURPLE.key -> Color(0xFF7F6FB8)
            AppTheme.ORANGE.key -> Color(0xFFD99A5B)
            AppTheme.TEAL.key -> Color(0xFF4F9E97)
            else -> LightBlue
        }
    return buildGeneratedScheme(base, dark)
}

private fun buildGeneratedScheme(base: Color, dark: Boolean): ColorScheme {
    val primary = if (dark) mix(base, White, 0.34f) else base
    val onPrimary = if (dark) mix(base, BLACK, 0.28f) else White
    val primaryContainer =
        if (dark) mix(base, BLACK, 0.60f) else mix(base, White, 0.86f)
    val onPrimaryContainer =
        if (dark) mix(base, White, 0.88f) else mix(base, BLACK, 0.60f)
    val background = if (dark) mix(base, BLACK, 0.88f) else mix(base, White, 0.93f)
    val onBackground = if (dark) mix(base, White, 0.88f) else mix(base, BLACK, 0.72f)
    val surface = if (dark) mix(base, BLACK, 0.79f) else White
    val onSurface = if (dark) mix(base, White, 0.88f) else mix(base, BLACK, 0.72f)
    val surfaceVariant = if (dark) mix(base, BLACK, 0.72f) else mix(base, White, 0.88f)
    val onSurfaceVariant = if (dark) mix(base, White, 0.72f) else mix(base, BLACK, 0.52f)
    val outline = if (dark) mix(base, BLACK, 0.55f) else mix(base, White, 0.62f)
    val outlineVariant = if (dark) mix(base, BLACK, 0.62f) else mix(base, White, 0.82f)
    val error = if (dark) Color(0xFFF2B8B5) else Color(0xFFB3261E)
    val errorContainer = if (dark) Color(0xFF8C1D18) else Color(0xFFFFDAD6)
    val onErrorContainer = if (dark) Color(0xFFFFDAD6) else Color(0xFF410002)
    return if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = primary,
            onSecondary = onPrimary,
            secondaryContainer = primaryContainer,
            onSecondaryContainer = onPrimaryContainer,
            tertiary = primary,
            onTertiary = onPrimary,
            tertiaryContainer = primaryContainer,
            onTertiaryContainer = onPrimaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceTint = Color.Transparent,
            surfaceBright = surface,
            surfaceDim = background,
            outline = outline,
            outlineVariant = outlineVariant,
            inverseSurface = mix(base, White, 0.88f),
            inverseOnSurface = mix(base, BLACK, 0.79f),
            inversePrimary = primaryContainer,
            error = error,
            onError = White,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            scrim = Color(0x99000000),
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = primary,
            onSecondary = onPrimary,
            secondaryContainer = primaryContainer,
            onSecondaryContainer = onPrimaryContainer,
            tertiary = primary,
            onTertiary = onPrimary,
            tertiaryContainer = primaryContainer,
            onTertiaryContainer = onPrimaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceTint = Color.Transparent,
            surfaceBright = surface,
            surfaceDim = surfaceVariant,
            surfaceContainerLowest = surface,
            surfaceContainerLow = surface,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceVariant,
            surfaceContainerHighest = surfaceVariant,
            outline = outline,
            outlineVariant = outlineVariant,
            inverseSurface = mix(base, BLACK, 0.72f),
            inverseOnSurface = mix(base, White, 0.93f),
            inversePrimary = primaryContainer,
            error = error,
            onError = White,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            scrim = Color(0x99000000),
        )
    }
}

private val BLACK = Color(0xFF000000)

private fun mix(a: Color, b: Color, t: Float): Color {
    val x = t.coerceIn(0f, 1f)
    fun blend(u: Float, v: Float): Int = (u * (1f - x) + v * x).toInt()
    return Color(
        blend(a.red * 255f, b.red * 255f) / 255f,
        blend(a.green * 255f, b.green * 255f) / 255f,
        blend(a.blue * 255f, b.blue * 255f) / 255f,
        a.alpha * (1f - x) + b.alpha * x,
    )
}

@Composable
fun UestcHelperTheme(
    themeKey: String = AppTheme.BLUE.key,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = appColorScheme(themeKey, darkTheme)
    MaterialTheme(
        colorScheme = scheme,
        shapes = AppShapes,
        content = {
            CompositionLocalProvider(LocalAbsoluteTonalElevation provides 0.dp) {
                content()
            }
        },
    )
}
