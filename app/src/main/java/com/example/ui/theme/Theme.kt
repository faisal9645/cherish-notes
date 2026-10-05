package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
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

// Dark Mode (AMOLED-Style): Strict #000000 pure black background & surface, deep blue accents, no rose/pink, crisp white text
private val DarkColorScheme = darkColorScheme(
    primary = DarkBluePrimary, // Deep Dark Blue (0xFF1D4ED8)
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0F172A),
    onPrimaryContainer = Color(0xFF93C5FD),
    secondary = DarkBlueSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF172554),
    onSecondaryContainer = Color(0xFFBFDBFE),
    tertiary = DarkBlueTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF0F172A),
    onTertiaryContainer = Color(0xFFDBEAFE),
    background = Color(0xFF000000), // Strict AMOLED pure black #000000
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000), // Strict AMOLED pure black #000000
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF0A0E1A), // Ultra-deep midnight black for inputs and cards
    onSurfaceVariant = Color(0xFFE2E8F0),
    surfaceTint = Color.Transparent,
    surfaceContainer = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF000000),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerHigh = Color(0xFF070B16),
    surfaceContainerHighest = Color(0xFF0C1220),
    outline = Color(0xFF1E293B),
    outlineVariant = Color(0xFF0A0F1D)
)

@Composable
fun CherishTheme(
    themeMode: Int = 0,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val colorScheme = when (themeMode) {
        1 -> PureWhiteColorScheme
        2, 3 -> DarkColorScheme
        else -> if (isSystemDark) DarkColorScheme else PureWhiteColorScheme
    }

    val currentDensity = LocalDensity.current
    val cappedDensity = Density(
        density = currentDensity.density,
        fontScale = currentDensity.fontScale.coerceAtMost(1.0f)
    )

    CompositionLocalProvider(LocalDensity provides cappedDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
