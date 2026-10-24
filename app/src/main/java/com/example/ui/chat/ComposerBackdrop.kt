package com.example.ui.chat

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp

/** Records only wallpaper: message text and composer content never enter the blur. */
internal class ComposerBackdrop(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
}

internal val LocalComposerBackdrop = staticCompositionLocalOf<ComposerBackdrop?> { null }

@Composable
internal fun rememberComposerBackdrop(): ComposerBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { ComposerBackdrop(layer) }
}

internal fun Modifier.recordComposerBackdrop(backdrop: ComposerBackdrop?): Modifier =
    if (backdrop == null) this else this
        .onGloballyPositioned { backdrop.origin = it.positionInWindow() }
        .drawWithContent {
            backdrop.layer.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop.layer)
        }

/** Apply inside the existing pill clip, before its translucent tint and sharp content. */
@Composable
internal fun Modifier.composerBackdropBlur(backdrop: ComposerBackdrop?): Modifier {
    if (backdrop == null || Build.VERSION.SDK_INT < 31) return this
    val blurred = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    return this
        .onGloballyPositioned { origin = it.positionInWindow() }
        .drawWithContent {
            val offset = backdrop.origin - origin
            val radius = 16.dp.toPx()
            blurred.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
            blurred.record {
                translate(offset.x, offset.y) { drawLayer(backdrop.layer) }
            }
            drawLayer(blurred)
            drawContent()
        }
}
