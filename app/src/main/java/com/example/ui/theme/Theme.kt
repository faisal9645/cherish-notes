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
    surfaceContainerHigh = Color(0xFFFAFBFE),
    surfaceContainerHighest = Color(0xFFF1F5FB),
    outline = SoftBorderOutline,
    outlineVariant = SoftBorderOutlineVariant
)

private val TrueDarkColorScheme = darkColorScheme(
    primary = RoseGoldPrimary,
    onPrimary = RoseGoldOnPrimary,
    primaryContainer = Color(0xFF152060),
    onPrimaryContainer = Color(0xFFD6E0FF),
    secondary = ChampagneSecondary,
    onSecondary = ChampagneOnSecondary,
    secondaryContainer = Color(0xFF0B3A6E),
    onSecondaryContainer = Color(0xFFD3E8FF),
    tertiary = AmethystTertiary,
    onTertiary = AmethystOnTertiary,
    tertiaryContainer = Color(0xFF181570),
    onTertiaryContainer = Color(0xFFE2E0FF),
    background = TrueDarkBackground,
    onBackground = TrueDarkOnBackground,
    surface = TrueDarkSurface,
    onSurface = TrueDarkOnSurface,
    surfaceVariant = TrueDarkSurfaceVariant,
    onSurfaceVariant = TrueDarkOnSurfaceVariant,
    surfaceTint = Color.Transparent,
    surfaceContainer = TrueDarkSurface,
    surfaceContainerLow = TrueDarkBackground,
    surfaceContainerLowest = TrueDarkBackground,
    surfaceContainerHigh = TrueDarkSurfaceVariant,
    surfaceContainerHighest = Color(0xFF2C2D33),
    outline = TrueDarkOutline,
    outlineVariant = TrueDarkOutlineVariant
)

@Composable
fun CherishTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TrueDarkColorScheme else PureWhiteColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
