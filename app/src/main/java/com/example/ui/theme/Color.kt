package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// Day Mode Blues (Vibrant electric royal blues matching screenshots)
val DayBluePrimary = Color(0xFF3048F5)
val DayBlueSecondary = Color(0xFF258BE8)
val DayBlueTertiary = Color(0xFF3630F2)

// Dark Mode Blues (Deeper / darker rich blues for night mode)
val DarkBluePrimary = Color(0xFF1D4ED8)
val DarkBlueSecondary = Color(0xFF1E40AF)
val DarkBlueTertiary = Color(0xFF172554)
val DarkBlueBubble = Color(0xFF1D4ED8)

@Composable
@ReadOnlyComposable
fun isAppInDark(): Boolean {
    return isSystemInDarkTheme() || MaterialTheme.colorScheme.background == Color.Black
}

// Electric Royal Blue Gradient matching screenshots (dynamic based on theme):
val AppGradientStart: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isAppInDark()) DarkBlueSecondary else DayBlueSecondary

val AppGradientMid: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isAppInDark()) DarkBluePrimary else DayBluePrimary

val AppGradientEnd: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isAppInDark()) DarkBlueTertiary else DayBlueTertiary

val AppGradientShadow: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isAppInDark()) Color(0x351D4ED8) else Color(0x353048F5)

@Composable
fun appHorizontalGradient(): Brush {
    val isDark = isAppInDark()
    val start = if (isDark) DarkBlueSecondary else DayBlueSecondary
    val mid = if (isDark) DarkBluePrimary else DayBluePrimary
    val end = if (isDark) DarkBlueTertiary else DayBlueTertiary
    return Brush.horizontalGradient(
        0.0f to start,
        0.55f to mid,
        1.0f to end
    )
}

@Composable
fun appVerticalGradient(): Brush {
    val isDark = isAppInDark()
    val start = if (isDark) DarkBlueSecondary else DayBlueSecondary
    val mid = if (isDark) DarkBluePrimary else DayBluePrimary
    val end = if (isDark) DarkBlueTertiary else DayBlueTertiary
    return Brush.verticalGradient(
        0.0f to start,
        0.55f to mid,
        1.0f to end
    )
}

@Composable
fun Modifier.appGradientShadow(shape: Shape = RoundedCornerShape(16.dp)): Modifier {
    val shadowColor = if (isAppInDark()) Color(0x351D4ED8) else Color(0x353048F5)
    return this.shadow(
        elevation = 8.dp,
        shape = shape,
        ambientColor = shadowColor,
        spotColor = shadowColor
    )
}

// Cherish & Notes Theme - Dynamic Blue & Clean Surface Palette
val RoseGoldPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isAppInDark()) DarkBluePrimary else DayBluePrimary

val RoseGoldOnPrimary = Color(0xFFFFFFFF)

val RoseGoldContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isAppInDark()) Color(0xFF0F172A) else Color(0xFFEFF4FF)

val OnRoseGoldContainer = Color(0xFF1E293B)

val ChampagneSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isAppInDark()) DarkBlueSecondary else DayBlueSecondary

val ChampagneOnSecondary = Color(0xFFFFFFFF)

val ChampagneContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isAppInDark()) Color(0xFF172554) else Color(0xFFE0E7FF)

val OnChampagneContainer = Color(0xFF0A1033)

val AmethystTertiary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isAppInDark()) DarkBlueTertiary else DayBlueTertiary

val AmethystOnTertiary = Color(0xFFFFFFFF)

val AmethystContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isAppInDark()) Color(0xFF0F172A) else Color(0xFFDBEAFE)

val OnAmethystContainer = Color(0xFF0F172A)

// Clean Pure White Background & Surface Palette
val WhiteBackground = Color(0xFFFFFFFF)
val WhiteSurface = Color(0xFFFFFFFF)
val SoftPinkSurfaceVariant = Color(0xFFF4F6FC)
val DarkOnBackground = Color(0xFF18181B)
val DarkAubergine = Color(0xFF18181B)
val DarkOnSurface = Color(0xFF18181B)
val DarkOnSurfaceVariant = Color(0xFF52609A)
val SoftBorderOutline = Color(0xFFE2E8F0)
val SoftBorderOutlineVariant = Color(0xFFEDF2F7)

// True AMOLED / Pure Black Dark Palette (strictly #000000 background and surface)
val TrueDarkBackground = Color(0xFF000000)
val TrueDarkSurface = Color(0xFF000000)
val TrueDarkSurfaceVariant = Color(0xFF060A14) // Ultra-deep midnight pure black tint
val TrueDarkOnBackground = Color(0xFFFFFFFF)
val TrueDarkOnSurface = Color(0xFFFFFFFF)
val TrueDarkOnSurfaceVariant = Color(0xFFCBD5E1) // High contrast clean slate
val TrueDarkOutline = Color(0xFF1E293B) // Dark subtle border
val TrueDarkOutlineVariant = Color(0xFF0A0F1D) // Ultra dark border

val DarkBluePrimaryContainer = Color(0xFF0F172A)
val DarkBlueSecondaryContainer = Color(0xFF172554)

// Light Palette
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF4F6FC)
val LightSurfaceTint = Color.Transparent
val LightOnBackground = Color(0xFF18181B)
val LightOnSurface = Color(0xFF18181B)
val LightOnSurfaceVariant = Color(0xFF52609A)
val LightOutline = Color(0xFFE2E8F0)
val LightOutlineVariant = Color(0xFFEDF2F7)

// Special Couple Accents
val HeartRed: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isAppInDark()) DarkBluePrimary else DayBluePrimary

val SoftBlush = Color(0xFFEEF5FF)
val DeepWine = Color(0xFF0E1342)
val OnlineGreen = Color(0xFF10B981) // vibrant active indicator
val GoldMilestone = Color(0xFFF59E0B)

val BubbleSent: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isAppInDark()) DarkBlueBubble else DayBluePrimary

val BubbleReceived: Color
    @Composable
    @ReadOnlyComposable
    get() = if (isAppInDark()) Color(0xFF131A2A) else Color(0xFFF1F5FB)