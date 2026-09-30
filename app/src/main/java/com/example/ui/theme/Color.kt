package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// User Specified Gradient:
// background: linear-gradient(90deg, #258BE8 0%, #3048F5 55%, #3630F2 100%);
// box-shadow: 0 8px 18px rgba(48, 72, 245, 0.25);
val AppGradientStart = Color(0xFF258BE8)
val AppGradientMid = Color(0xFF3048F5)
val AppGradientEnd = Color(0xFF3630F2)
val AppGradientShadow = Color(0x403048F5) // rgba(48, 72, 245, 0.25)

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

// Cherish & Notes Theme - Electric Blue Gradient & Modern Clean Surface Palette
val RoseGoldPrimary = Color(0xFF3048F5)
val RoseGoldOnPrimary = Color(0xFFFFFFFF)
val RoseGoldContainer = Color(0xFFE8EEFF)
val OnRoseGoldContainer = Color(0xFF101C6B)

val ChampagneSecondary = Color(0xFF258BE8)
val ChampagneOnSecondary = Color(0xFFFFFFFF)
val ChampagneContainer = Color(0xFFE3F2FD)
val OnChampagneContainer = Color(0xFF042852)

val AmethystTertiary = Color(0xFF3630F2)
val AmethystOnTertiary = Color(0xFFFFFFFF)
val AmethystContainer = Color(0xFFECEBFF)
val OnAmethystContainer = Color(0xFF130E66)

// Clean Pure White Background & Surface Palette
val WhiteBackground = Color(0xFFFFFFFF)
val WhiteSurface = Color(0xFFFFFFFF)
val SoftPinkSurfaceVariant = Color(0xFFF1F5FB)
val DarkOnBackground = Color(0xFF18181B)
val DarkAubergine = Color(0xFF18181B)
val DarkOnSurface = Color(0xFF18181B)
val DarkOnSurfaceVariant = Color(0xFF52525B)
val SoftBorderOutline = Color(0xFFE2E8F0)
val SoftBorderOutlineVariant = Color(0xFFEDF2F7)

// Dark Palette mapped to True Dark Aesthetic
val TrueDarkBackground = Color(0xFF000000)
val TrueDarkSurface = Color(0xFF121212)
val TrueDarkSurfaceVariant = Color(0xFF1E1E1E)
val TrueDarkOnBackground = Color(0xFFFFFFFF)
val TrueDarkOnSurface = Color(0xFFFFFFFF)
val TrueDarkOnSurfaceVariant = Color(0xFFA0A0A5)
val TrueDarkOutline = Color(0xFF333333)
val TrueDarkOutlineVariant = Color(0xFF222222)

// Light Palette
val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF1F5FB)
val LightSurfaceTint = Color.Transparent
val LightOnBackground = Color(0xFF18181B)
val LightOnSurface = Color(0xFF18181B)
val LightOnSurfaceVariant = Color(0xFF52525B)
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

