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

private val OledDarkColorScheme = darkColorScheme(
    primary = Color(0xFFC7789A), // Dimmed Rose Gold
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF101840), // Very dark blue
    onPrimaryContainer = Color(0xFFA0B4EF), // Muted light blue
    secondary = Color(0xFFA69A73), // Dimmed Champagne
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF08254A), // Very dark secondary
    onSecondaryContainer = Color(0xFFA5C5E6),
    tertiary = Color(0xFF7E6AA3), // Dimmed Amethyst
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF100E4A),
    onTertiaryContainer = Color(0xFFB5B3E6),
    background = Color.Black,
    onBackground = Color(0xFFD4D6DD), // Slightly dimmed text
    surface = Color.Black,
    onSurface = Color(0xFFD4D6DD),
    surfaceVariant = Color(0xFF0A0C14), // Almost black for bubbles
    onSurfaceVariant = Color(0xFFB0B3BC),
    surfaceTint = Color.Transparent,
    surfaceContainer = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerHigh = Color(0xFF08090C),
    surfaceContainerHighest = Color(0xFF12141A),
    outline = Color(0xFF2C2F3B),
    outlineVariant = Color(0xFF1A1C25)
)

@Composable
fun CherishTheme(
    themeMode: Int = 0,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val colorScheme = when (themeMode) {
        1 -> PureWhiteColorScheme
        2 -> TrueDarkColorScheme
        3 -> OledDarkColorScheme
        else -> if (isSystemDark) TrueDarkColorScheme else PureWhiteColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
