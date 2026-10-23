package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

// Day Mode: Pure white surface, electric day blue accents
private val PureWhiteColorScheme = lightColorScheme(
    primary = DayBluePrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFF4FF),
    onPrimaryContainer = Color(0xFF1E293B),
    secondary = DayBlueSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF0A1033),
    tertiary = DayBlueTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDBEAFE),
    onTertiaryContainer = Color(0xFF0F172A),
    background = Color.White,
    onBackground = LightOnBackground,
    surface = Color.White,
    onSurface = LightOnSurface,
    surfaceVariant = Color(0xFFF1F5F9), // Clean light slate
    onSurfaceVariant = Color(0xFF475569),
    surfaceTint = Color.Transparent,
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerHigh = Color(0xFFF8FAFC),
    surfaceContainerHighest = Color(0xFFF1F5F9),
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFEDF2F7)
)

// Dark Mode (AMOLED-Style with Blue Theme): Strict #000000 pure black background & surface, deep blue accents, crisp white text
private val DarkColorScheme = darkColorScheme(
    primary = DarkBluePrimary, // Deep Dark Blue
    onPrimary = Color.White,
    primaryContainer = Color(0xFF263653), // Deep dark blue container
    onPrimaryContainer = Color(0xFFBFDBFE), // Soft light blue text
    secondary = DarkBlueSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF263653), // Rich dark blue container
    onSecondaryContainer = Color(0xFFDBEAFE),
    tertiary = DarkBlueTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF263653),
    onTertiaryContainer = Color(0xFFDBEAFE),
    background = Color(0xFF000000), // Strict AMOLED pure black #000000
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000), // Strict AMOLED pure black #000000
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF000000), // Strict AMOLED pure black
    onSurfaceVariant = Color(0xFFE2E8F0),
    surfaceTint = Color.Transparent,
    surfaceContainer = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerHigh = Color(0xFF263653),
    surfaceContainerHighest = Color(0xFF30415F),
    outline = Color(0xFF526B96),
    outlineVariant = Color(0xFF526B96).copy(alpha = 0.45f)
)

// AMOLED keeps the same midnight-blue controls and pure black background.
private val AmoledBlackColorScheme = DarkColorScheme.copy()

/** True when the explicit AMOLED theme is selected. */
val LocalAmoledBlack = staticCompositionLocalOf { false }

/** Dark and AMOLED share blue accents; semantic colours stay unchanged. */
@Composable
fun darkTone(color: Color): Color = color

/** The neutral grey as light as this colour, when it's a blue/navy tone; other colours unchanged. */
fun Color.withoutBlue(): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    if (hsv[1] < 0.12f || hsv[0] !in 185f..265f) return this
    val grey = (0.299f * red + 0.587f * green + 0.114f * blue).coerceIn(0f, 1f)
    return Color(grey, grey, grey, alpha)
}

/** Both dark themes use the same navy card palette; black screen backgrounds are separate. */
@Composable
fun darkSurface(navy: Color, black: Color = Color(0xFF0A0A0A)): Color =
    navy

@Composable
fun CherishTheme(
    themeMode: Int = 0,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        1 -> false
        2, 3 -> true
        else -> isSystemDark
    }
    val colorScheme = when (themeMode) {
        1 -> PureWhiteColorScheme
        2 -> DarkColorScheme
        3 -> AmoledBlackColorScheme
        else -> if (isSystemDark) DarkColorScheme else PureWhiteColorScheme
    }

    val currentDensity = LocalDensity.current
    val cappedDensity = Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale.coerceAtMost(1.0f)
    )

    CompositionLocalProvider(LocalDensity provides cappedDensity, LocalAmoledBlack provides (themeMode == 3)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
