package com.example.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.DefaultShadowColor
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/** A neutral soft shadow: about as dark as Material's, spread much wider. */
private val SoftShadowColor = Color.Black.copy(alpha = 0.12f)

/**
 * A soft, diffuse shadow in place of Material's tight elevation shadow: blurred wider and set a little
 * below, so the element looks like it floats. Same parameters as Modifier.shadow (including clipping
 * to [shape] by default); the colour stays the same neutral, or the tint passed in.
 */
fun Modifier.softShadow(
    elevation: Dp,
    shape: Shape = RectangleShape,
    clip: Boolean = elevation > 0.dp,
    ambientColor: Color = DefaultShadowColor,
    spotColor: Color = DefaultShadowColor,
): Modifier {
    val clipped = if (clip) Modifier.clip(shape) else Modifier
    if (elevation <= 0.dp) return this.then(clipped)
    val color = when {
        spotColor != DefaultShadowColor -> spotColor
        ambientColor != DefaultShadowColor -> ambientColor
        else -> SoftShadowColor
    }
    return this
        .dropShadow(
            shape,
            Shadow(radius = elevation * 1.6f, color = color, offset = DpOffset(0.dp, elevation * 0.45f))
        )
        .then(clipped)
}
