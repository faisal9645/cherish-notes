package com.example.ui.disguise

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.hypot
import kotlin.math.max

/**
 * Opening the secret space from Notes: a circular window opens in the Notes screen at the + button
 * that was held and expands until the Cherish app underneath fills the screen. Hiding never animates.
 */
object HeartReveal {
    /** How long the circle takes to open from the button to the whole screen. */
    const val DURATION_MS = 520
}

typealias CircleReveal = HeartReveal

/** Radius at which the circle, anchored at [origin], covers every corner of [size]. */
private fun coveringRadius(origin: Offset, size: Size): Float {
    return max(
        max(hypot(origin.x, origin.y), hypot(size.width - origin.x, origin.y)),
        max(hypot(origin.x, size.height - origin.y), hypot(size.width - origin.x, size.height - origin.y))
    )
}

/**
 * Cuts the growing circle out of this layer so what's underneath shows through: nothing at
 * [progress] 0, the whole screen at 1. The circle starts small at [origin] (the + icon)
 * and smoothly expands outward with hermite smoothstep acceleration.
 * [progress] is read only while drawing, so the reveal redraws without recomposing Notes.
 */
fun Modifier.circleReveal(origin: Offset?, progress: () -> Float): Modifier {
    if (origin == null) return this
    return this
        // The cut needs its own layer, but only while the circle is open
        .graphicsLayer {
            compositingStrategy = if (progress() > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        }
        .drawWithContent {
            val p = progress().coerceIn(0f, 1f)
            if (p >= 1f) return@drawWithContent
            drawContent()
            if (p <= 0f) return@drawWithContent
            val maxR = coveringRadius(origin, size)
            val currentRadius = maxR * (p * p * (3f - 2f * p))
            drawCircle(
                color = Color.Black,
                radius = currentRadius,
                center = origin,
                blendMode = BlendMode.Clear
            )
        }
}

/** Alias to ensure backward-compatibility with any existing call site */
fun Modifier.heartReveal(origin: Offset?, progress: () -> Float): Modifier = circleReveal(origin, progress)
