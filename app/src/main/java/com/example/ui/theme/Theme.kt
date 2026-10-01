package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val PureWhiteColorScheme = lightColorScheme(
    primary = RoseGoldPrimary,
    onPrimary = RoseGoldOnPrimary,
    primaryContainer = RoseGoldContainer,
    onPrimaryContainer = OnRoseGoldContainer,
    secondary = ChampagneSecondary,
    onSecondary = ChampagneOnSecondary,
    secondaryContainer = ChampagneContainer,
    onSecondaryContainer = OnChampagneContainer,
    tertiary = AmethystTertiary,
    onTertiary = AmethystOnTertiary,
    tertiaryContainer = AmethystContainer,
    onTertiaryContainer = OnAmethystContainer,
    background = Color.White,
    onBackground = LightOnBackground,
    surface = Color.White,
    onSurface = LightOnSurface,
    surfaceVariant = SoftPinkSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = Color.Transparent,
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    outline = SoftBorderOutline,
    outlineVariant = SoftBorderOutlineVariant
)

private val TrueDarkColorScheme = darkColorScheme(
    primary = Color(0xFF1D4ED8), // Deeper Dark Royal Blue
    onPrimary = Color.White,
    primaryContainer = Color(0xFF0F172A), // Dark Slate Navy Container
    onPrimaryContainer = Color(0xFF93C5FD),
    secondary = Color(0xFF1E40AF), // Deep Accent Blue
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF172554),
    onSecondaryContainer = Color(0xFFBFDBFE),
    tertiary = Color(0xFF2563EB),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF0F172A),
    onTertiaryContainer = Color(0xFFDBEAFE),
    background = Color.Black, // Strict AMOLED pure black #000000
    onBackground = Color.White,
    surface = Color.Black, // Strict AMOLED pure black #000000
    onSurface = Color.White,
    surfaceVariant = Color(0xFF060A14), // Ultra-deep midnight black for inputs and cards
    onSurfaceVariant = Color(0xFFCBD5E1),
    surfaceTint = Color.Transparent,
    surfaceContainer = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainerLowest = Color.Black,
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
        2, 3 -> TrueDarkColorScheme
        else -> if (isSystemDark) TrueDarkColorScheme else PureWhiteColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
