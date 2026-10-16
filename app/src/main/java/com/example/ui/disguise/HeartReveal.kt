package com.example.ui.disguise

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.hypot
import kotlin.math.max

/**
 * Opening the secret space from Notes: a heart-shaped window opens in the Notes screen at the + button
 * that was held and grows until the chat underneath fills the screen. Hiding never animates.
 */
object HeartReveal {
    /** How long the heart takes to open from the button to the whole screen. */
    const val DURATION_MS = 560
}

// A heart one unit wide (0.92 tall), drawn from its top notch. It grows around ANCHOR, the centre of
// the biggest circle that fits inside it (INSIDE_RADIUS of its width), so a heart this many times
// wider than the distance to the farthest screen corner covers the whole screen.
private const val ANCHOR_X = 0.5f
private const val ANCHOR_Y = 0.54f
private const val INSIDE_RADIUS = 0.27f
private val HEART: List<FloatArray> = listOf(
    floatArrayOf(0.5f, 0.10f, 0.36f, 0.0f, 0.24f, 0.0f),
    floatArrayOf(0.09f, 0.0f, 0.0f, 0.12f, 0.0f, 0.29f),
    floatArrayOf(0.0f, 0.52f, 0.27f, 0.71f, 0.5f, 0.92f),
    floatArrayOf(0.73f, 0.71f, 1.0f, 0.52f, 1.0f, 0.29f),
    floatArrayOf(1.0f, 0.12f, 0.91f, 0.0f, 0.76f, 0.0f),
    floatArrayOf(0.64f, 0.0f, 0.5f, 0.10f, 0.5f, 0.26f),
)

private fun heartPath(path: Path, center: Offset, width: Float) {
    path.reset()
    fun x(u: Float) = center.x + (u - ANCHOR_X) * width
    fun y(v: Float) = center.y + (v - ANCHOR_Y) * width
    path.moveTo(x(0.5f), y(0.26f))
    for (c in HEART) path.cubicTo(x(c[0]), y(c[1]), x(c[2]), y(c[3]), x(c[4]), y(c[5]))
    path.close()
}

/** Width at which the heart, anchored at [origin], covers every corner of [size]. */
private fun coveringWidth(origin: Offset, size: Size): Float {
    val far = max(
        max(hypot(origin.x, origin.y), hypot(size.width - origin.x, origin.y)),
        max(hypot(origin.x, size.height - origin.y), hypot(size.width - origin.x, size.height - origin.y))
    )
    return far / INSIDE_RADIUS
}

/**
 * Cuts the growing heart out of this layer so what's underneath shows through: nothing at
 * [progress] 0, the whole screen at 1. The heart starts small at [origin] and speeds up as it grows
 * (cubic), so it's seen opening out of the button before it sweeps across. [progress] is read only
 * while drawing, so the reveal redraws without recomposing Notes.
 */
fun Modifier.heartReveal(origin: Offset?, progress: () -> Float): Modifier {
    if (origin == null) return this
    val path = Path()
    return this
        // The cut needs its own layer, but only while the heart is open
        .graphicsLayer {
            compositingStrategy = if (progress() > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
        }
        .drawWithContent {
            val p = progress().coerceIn(0f, 1f)
            if (p >= 1f) return@drawWithContent
            drawContent()
            if (p <= 0f) return@drawWithContent
            heartPath(path, origin, coveringWidth(origin, size) * p * p * p)
            drawPath(path, Color.Black, blendMode = BlendMode.Clear)
        }
}
