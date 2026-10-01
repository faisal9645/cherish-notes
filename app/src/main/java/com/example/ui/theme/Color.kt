package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// Electric Royal Blue Gradient matching screenshots:
val AppGradientStart = Color(0xFF258BE8) // Electric Azure Blue
val AppGradientMid = Color(0xFF3048F5)   // Vibrant Royal Blue
val AppGradientEnd = Color(0xFF3630F2)   // Deep Electric Blue
val AppGradientShadow = Color(0x353048F5)

val AppGradientColors = listOf(
    AppGradientStart,
    AppGradientMid,
    AppGradientEnd
)

val AppGradientStops = arrayOf(
    0.0f to AppGradientStart,
    0.55f to AppGradientMid,
    1.0f to AppGradientEnd
)

fun appHorizontalGradient(): Brush = Brush.horizontalGradient(
    colorStops = AppGradientStops
)

fun appVerticalGradient(): Brush = Brush.verticalGradient(
    colorStops = AppGradientStops
)

fun Modifier.appGradientShadow(shape: Shape = RoundedCornerShape(16.dp)): Modifier =
    this.shadow(
        elevation = 8.dp,
        shape = shape,
        ambientColor = AppGradientShadow,
        spotColor = AppGradientShadow
    )

// Cherish & Notes Theme - Vibrant Blue & Modern Clean Surface Palette
val RoseGoldPrimary = Color(0xFF3048F5) // Vibrant Electric Blue matching screenshot
val RoseGoldOnPrimary = Color(0xFFFFFFFF)
val RoseGoldContainer = Color(0xFFEFF4FF)
val OnRoseGoldContainer = Color(0xFF1E293B)

val ChampagneSecondary = Color(0xFF258BE8)
val ChampagneOnSecondary = Color(0xFFFFFFFF)
val ChampagneContainer = Color(0xFFE0E7FF)
val OnChampagneContainer = Color(0xFF0A1033)

val AmethystTertiary = Color(0xFF3630F2)
val AmethystOnTertiary = Color(0xFFFFFFFF)
val AmethystContainer = Color(0xFFDBEAFE)
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

// Deep Blue / Dark Blue Accents for Night Mode (deeper, darker blues)
val DarkBluePrimary = Color(0xFF3048F5)
val DarkBluePrimaryContainer = Color(0xFF0F172A)
val DarkBlueSecondary = Color(0xFF258BE8)
val DarkBlueSecondaryContainer = Color(0xFF172554)
val DarkBlueTertiary = Color(0xFF3630F2)

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
val HeartRed = Color(0xFF3048F5)
val SoftBlush = Color(0xFFEEF5FF)
val DeepWine = Color(0xFF0E1342)
val OnlineGreen = Color(0xFF10B981) // vibrant active indicator
val GoldMilestone = Color(0xFFF59E0B)
val BubbleSent = Color(0xFF3048F5)
val BubbleReceived = Color(0xFFF1F5FB)