package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

// Normal Dark Mode: Dark background (#0B0F19), deep blue accents, no rose/pink, highly readable text
private val NormalDarkColorScheme = darkColorScheme(
    primary = DarkBluePrimary, // Deep Dark Blue (0xFF1D4ED8)
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E293B), // Dark Slate Navy Container
    onPrimaryContainer = Color(0xFF93C5FD),
    secondary = DarkBlueSecondary, // Deep Accent Blue (0xFF1E40AF)
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF172554),
    onSecondaryContainer = Color(0xFFBFDBFE),
    tertiary = DarkBlueTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF0F172A),
    onTertiaryContainer = Color(0xFFDBEAFE),
    background = Color(0xFF0B0F19), // Dark background
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF111827), // Very dark surface
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1E293B), // Dark cards / containers
    onSurfaceVariant = Color(0xFFCBD5E1),
    surfaceTint = Color.Transparent,
    surfaceContainer = Color(0xFF111827),
    surfaceContainerLow = Color(0xFF0B0F19),
    surfaceContainerLowest = Color(0xFF080C14),
    surfaceContainerHigh = Color(0xFF1E293B),
    surfaceContainerHighest = Color(0xFF334155),
    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B)
)

// AMOLED Mode: Strict #000000 pure black background & surface, deep blue accents, no rose/pink, crisp white text
private val TrueAmoledColorScheme = darkColorScheme(
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
        2 -> NormalDarkColorScheme
        3 -> TrueAmoledColorScheme
        else -> if (isSystemDark) NormalDarkColorScheme else PureWhiteColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
