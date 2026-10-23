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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.example.ui.components.softShadow

// Day Mode Blues (Vibrant electric royal blues matching screenshots)
val DayBluePrimary = Color(0xFF3048F5)
val DayBlueSecondary = Color(0xFF258BE8)
val DayBlueTertiary = Color(0xFF3630F2)

// Dark Mode Blues (Deep, rich dark midnight blues - sleek, calm, and not bright)
val DarkBluePrimary = Color(0xFF133285)
val DarkBlueSecondary = Color(0xFF0F2668)
val DarkBlueTertiary = Color(0xFF081232)
val DarkBlueBubble = Color(0xFF112970)

// Black (AMOLED) theme: graphite instead of blue for bubbles, buttons and headers, grey accents
val BlackGradientStart = Color(0xFF3A3A3C)
val BlackGradientMid = Color(0xFF2C2C2E)
val BlackGradientEnd = Color(0xFF1C1C1E)
val BlackAccent = Color(0xFF8E8E93)

@Composable
@ReadOnlyComposable
fun isAppInDark(): Boolean {
    return isSystemInDarkTheme() ||
            MaterialTheme.colorScheme.background == Color.Black ||
            MaterialTheme.colorScheme.background == Color(0xFF0B0F19) ||
            MaterialTheme.colorScheme.surface.luminance() < 0.5f
}

// Electric Royal Blue Gradient matching screenshots (dynamic based on theme):
val AppGradientStart: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalAmoledBlack.current) BlackGradientStart else if (isAppInDark()) DarkBlueSecondary else DayBlueSecondary

val AppGradientMid: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalAmoledBlack.current) BlackGradientMid else if (isAppInDark()) DarkBluePrimary else DayBluePrimary

val AppGradientEnd: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalAmoledBlack.current) BlackGradientEnd else if (isAppInDark()) DarkBlueTertiary else DayBlueTertiary

val AppGradientShadow: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalAmoledBlack.current) Color(0x35000000) else if (isAppInDark()) Color(0x350A1A4A) else Color(0x353048F5)

@Composable
fun appHorizontalGradient(): Brush {
    val isDark = isAppInDark()
    val black = LocalAmoledBlack.current
    val start = if (black) BlackGradientStart else if (isDark) DarkBlueSecondary else DayBlueSecondary
    val mid = if (black) BlackGradientMid else if (isDark) DarkBluePrimary else DayBluePrimary
    val end = if (black) BlackGradientEnd else if (isDark) DarkBlueTertiary else DayBlueTertiary
    return Brush.horizontalGradient(
        0.0f to start,
        0.55f to mid,
        1.0f to end
    )
}

@Composable
fun appVerticalGradient(): Brush {
    val isDark = isAppInDark()
    val black = LocalAmoledBlack.current
    val start = if (black) BlackGradientStart else if (isDark) DarkBlueSecondary else DayBlueSecondary
    val mid = if (black) BlackGradientMid else if (isDark) DarkBluePrimary else DayBluePrimary
    val end = if (black) BlackGradientEnd else if (isDark) DarkBlueTertiary else DayBlueTertiary
    return Brush.verticalGradient(
        0.0f to start,
        0.55f to mid,
        1.0f to end
    )
}

/**
 * The blue gradient for cards, bubbles, and buttons.
 * In day mode: electric royal blue.
 * In dark mode: deep, dark midnight blue (not bright).
 * In black AMOLED mode: sleek graphite.
 */
fun notesGradient(black: Boolean, isDark: Boolean = false, vertical: Boolean = false): Brush {
    val start = if (black) BlackGradientStart else if (isDark) DarkBlueSecondary else DayBlueSecondary
    val mid = if (black) BlackGradientMid else if (isDark) DarkBluePrimary else DayBluePrimary
    val end = if (black) BlackGradientEnd else if (isDark) DarkBlueTertiary else DayBlueTertiary
    val stops = arrayOf(
        0.0f to start,
        0.55f to mid,
        1.0f to end
    )
    return if (vertical) Brush.verticalGradient(*stops) else Brush.horizontalGradient(*stops)
}

@Composable
fun notesHorizontalGradient(): Brush = notesGradient(
    black = LocalAmoledBlack.current,
    isDark = isAppInDark(),
    vertical = false
)

@Composable
fun notesVerticalGradient(): Brush = notesGradient(
    black = LocalAmoledBlack.current,
    isDark = isAppInDark(),
    vertical = true
)

@Composable
fun Modifier.appGradientShadow(shape: Shape = RoundedCornerShape(16.dp)): Modifier {
    val shadowColor = if (LocalAmoledBlack.current) Color(0x35000000) else if (isAppInDark()) Color(0x350A1A4A) else Color(0x353048F5)
    return this.softShadow(
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
    get() = if (LocalAmoledBlack.current) BlackAccent else if (isAppInDark()) DarkBluePrimary else DayBluePrimary

val RoseGoldOnPrimary = Color(0xFFFFFFFF)

val RoseGoldContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalAmoledBlack.current) Color(0xFF1A1A1A) else if (isAppInDark()) Color(0xFF0F172A) else Color(0xFFEFF4FF)

val OnRoseGoldContainer = Color(0xFF1E293B)

val ChampagneSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalAmoledBlack.current) BlackAccent else if (isAppInDark()) DarkBlueSecondary else DayBlueSecondary

val ChampagneOnSecondary = Color(0xFFFFFFFF)

val ChampagneContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalAmoledBlack.current) Color(0xFF1A1A1A) else if (isAppInDark()) Color(0xFF172554) else Color(0xFFE0E7FF)

val OnChampagneContainer = Color(0xFF0A1033)

val AmethystTertiary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalAmoledBlack.current) BlackAccent else if (isAppInDark()) DarkBlueTertiary else DayBlueTertiary

val AmethystOnTertiary = Color(0xFFFFFFFF)

val AmethystContainer: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalAmoledBlack.current) Color(0xFF141414) else if (isAppInDark()) Color(0xFF0F172A) else Color(0xFFDBEAFE)

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
val TrueDarkSurfaceVariant = Color(0xFF000000) // Strict AMOLED pure black
val TrueDarkOnBackground = Color(0xFFFFFFFF)
val TrueDarkOnSurface = Color(0xFFFFFFFF)
val TrueDarkOnSurfaceVariant = Color(0xFFCBD5E1) // High contrast clean slate
val TrueDarkOutline = Color(0xFF262626) // Subtle border on pure black
val TrueDarkOutlineVariant = Color(0xFF171717) // Ultra dark border

val DarkBluePrimaryContainer = Color(0xFF141414)
val DarkBlueSecondaryContainer = Color(0xFF141414)

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
    get() = if (LocalAmoledBlack.current) BlackAccent else if (isAppInDark()) DarkBluePrimary else DayBluePrimary

val SoftBlush = Color(0xFFEEF5FF)
val DeepWine = Color(0xFF0E1342)
val OnlineGreen = Color(0xFF10B981) // vibrant active indicator
val GoldMilestone = Color(0xFFF59E0B)

val BubbleSent: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalAmoledBlack.current) BlackGradientMid else if (isAppInDark()) DarkBlueBubble else DayBluePrimary

val BubbleReceived: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalAmoledBlack.current) Color(0xFF0A0A0A) else if (isAppInDark()) Color(0xFF080B14) else MistBlue

// The secret app's shared palette. Screens use these names instead of their own hex codes, so the
// same grey, green or pink is the same everywhere (colours that only looked alike were merged).
// A screen's own one-off accent can stay a hex code; a colour used in several places gets a name.

// Neutral greys (the private chat's ink and paper)
val InkBlack = Color(0xFF0D0D0D)
val Neutral900 = Color(0xFF171717)
val Neutral800 = Color(0xFF262626)
val Neutral750 = Color(0xFF2F2F2F)
val Neutral700 = Color(0xFF3A3A3A)
val Neutral300 = Color(0xFFE0E0E0)
val Neutral200 = Color(0xFFECECEC)

// Cool greys: secondary text, dividers, quiet icons
val Gray200 = Color(0xFFE5E7EB)
val Gray400 = Color(0xFF9CA3AF)
val Gray500 = Color(0xFF6B7280)
val Gray600 = Color(0xFF4B5563)
val Gray700 = Color(0xFF374151)
val Gray800 = Color(0xFF1F2937)

// Slate: text and borders with a hint of blue
val Slate300 = Color(0xFFCBD5E1)
val Slate400 = Color(0xFF94A3B8)
val Slate500 = Color(0xFF64748B)
val Slate900 = Color(0xFF0F172A)

// Night surfaces: dark obsidian blackish surfaces for the Dark theme, graphite for overlays
val NavyDeep = Color(0xFF04060C)
val NavyCard = Color(0xFF0A0D16)
val NavyRaised = Color(0xFF121622)
val Graphite850 = Color(0xFF0E1015)
val Graphite800 = Color(0xFF14161C)
val Graphite700 = Color(0xFF1C1E26)

// Blues and indigo
val MistBlue = Color(0xFFF1F5FB)
val Blue300 = Color(0xFF93C5FD)
val Blue500 = Color(0xFF3B82F6)
val Blue600 = Color(0xFF2563EB)
val Blue900 = Color(0xFF1E3A8A)
val Indigo950 = Color(0xFF1E1B4B)

// Success, errors and highlights
val Emerald500 = Color(0xFF10B981)
val Green800 = Color(0xFF2E7D32)
val Green50 = Color(0xFFE8F5E9)
val Red500 = Color(0xFFEF4444)
val Red700 = Color(0xFFD32F2F)
val Amber500 = Color(0xFFF59E0B)
val Amber600 = Color(0xFFFFB300)
val Butter = Color(0xFFFFF3C4)

// Love accents: pinks and purples
val HeartPink = Color(0xFFFF2D55)
val LovePink = Color(0xFFFF4F9A)
val Pink500 = Color(0xFFEC4899)
val Rose600 = Color(0xFFE11D48)
val Lavender50 = Color(0xFFF1EEF8)
val Lavender200 = Color(0xFFCFC6E6)
val Violet600 = Color(0xFF8E2DE2)