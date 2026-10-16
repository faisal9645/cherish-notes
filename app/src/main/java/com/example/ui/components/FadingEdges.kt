package com.example.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Content scrolling past the top or bottom of a scroll area fades out over the last few dp instead of
 * being cut by a hard line. An edge fades only while there's more content beyond it, easing in over
 * the first few dp of scrolling, so a list at rest looks exactly as before. Transparency only: no
 * colour is added. Scroll positions are read while drawing, so scrolling doesn't recompose.
 */
private fun Modifier.fadingEdges(
    top: Dp,
    bottom: Dp,
    topStrength: Density.(fadePx: Float) -> Float,
    bottomStrength: Density.(fadePx: Float) -> Float,
): Modifier = this
    // The fade needs its own layer, but only while an edge is fading
    .graphicsLayer {
        val fading = topStrength(top.toPx()) > 0f || bottomStrength(bottom.toPx()) > 0f
        compositingStrategy = if (fading) CompositingStrategy.Offscreen else CompositingStrategy.Auto
    }
    .drawWithContent {
        drawContent()
        val topPx = top.toPx()
        val bottomPx = bottom.toPx()
        val t = if (topPx > 0f) topStrength(topPx).coerceIn(0f, 1f) else 0f
        val b = if (bottomPx > 0f) bottomStrength(bottomPx).coerceIn(0f, 1f) else 0f
        if (t > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 1f - t),
                    1f to Color.Black,
                    startY = 0f,
                    endY = topPx
                ),
                size = Size(size.width, topPx),
                blendMode = BlendMode.DstIn
            )
        }
        if (b > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Black,
                    1f to Color.Black.copy(alpha = 1f - b),
                    startY = size.height - bottomPx,
                    endY = size.height
                ),
                topLeft = Offset(0f, size.height - bottomPx),
                size = Size(size.width, bottomPx),
                blendMode = BlendMode.DstIn
            )
        }
    }

/** Fading edges for a Column scrolled with verticalScroll([state]); goes just before verticalScroll. */
fun Modifier.scrollFadingEdges(state: ScrollState, top: Dp = 18.dp, bottom: Dp = 24.dp): Modifier =
    fadingEdges(
        top = top,
        bottom = bottom,
        topStrength = { px -> state.value / px },
        bottomStrength = { px -> (state.maxValue - state.value) / px }
    )

/** Fading edges for a LazyColumn scrolled by [state]. */
fun Modifier.lazyFadingEdges(state: LazyListState, top: Dp = 18.dp, bottom: Dp = 24.dp): Modifier =
    fadingEdges(
        top = top,
        bottom = bottom,
        topStrength = { px ->
            if (state.firstVisibleItemIndex > 0) 1f else state.firstVisibleItemScrollOffset / px
        },
        bottomStrength = { px ->
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            when {
                last == null -> 0f
                last.index < info.totalItemsCount - 1 -> 1f
                else -> (last.offset + last.size - (info.viewportEndOffset - info.afterContentPadding)) / px
            }
        }
    )

/**
 * The chat's message list (newest at the bottom): older messages fade out at the top, under the
 * header, while there are more above. The bottom, by the message box, stays sharp.
 */
fun Modifier.chatTopFadingEdge(state: LazyListState, top: Dp = 18.dp): Modifier =
    fadingEdges(
        top = top,
        bottom = 0.dp,
        topStrength = { if (state.canScrollForward) 1f else 0f },
        bottomStrength = { 0f }
    )
