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

@Composable
fun CherishTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Exclusively use PureWhiteColorScheme to guarantee a modern, pure white background
    MaterialTheme(
        colorScheme = PureWhiteColorScheme,
        typography = Typography,
        content = content
    )
}
