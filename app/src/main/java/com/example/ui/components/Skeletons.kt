package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Loading placeholders: grey shapes of the content that's coming, with a soft light sweeping
 * across them. One sweep drives a whole placeholder group and is only read while drawing, so a grid
 * of tiles costs about the same as one. The sweep stops behind Notes and in the background.
 */
@Composable
fun rememberSkeletonSweep(): State<Float> {
    if (!rememberAppIsResumed()) return remember { mutableFloatStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "skeleton")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "skeleton_sweep"
    )
}

/** One placeholder shape. */
@Composable
fun SkeletonBlock(sweep: State<Float>, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    val ink = MaterialTheme.colorScheme.onSurface
    val base = ink.copy(alpha = 0.07f)
    val highlight = ink.copy(alpha = 0.06f)
    Box(
        modifier = modifier
            .clip(shape)
            .drawBehind {
                drawRect(base)
                val band = size.width * 0.6f
                val x = -band + (size.width + 2 * band) * sweep.value
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, highlight, Color.Transparent),
                        start = Offset(x - band / 2, 0f),
                        end = Offset(x + band / 2, size.height / 3)
                    )
                )
            }
    )
}

/** A grid of photo tiles, shaped like the gallery's media grid. */
@Composable
fun SkeletonPhotoGrid(columns: Int = 3, rows: Int = 5, spacing: Dp = 4.dp, modifier: Modifier = Modifier) {
    val sweep = rememberSkeletonSweep()
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Loading" }
    ) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                repeat(columns) {
                    SkeletonBlock(sweep, Modifier.weight(1f).aspectRatio(1f), RoundedCornerShape(4.dp))
                }
            }
        }
    }
}

/** List rows: a rounded thumbnail and two lines of text, like the gallery's links and voice notes. */
@Composable
fun SkeletonListRows(count: Int = 6, modifier: Modifier = Modifier) {
    val sweep = rememberSkeletonSweep()
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { contentDescription = "Loading" }
    ) {
        repeat(count) { index ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(sweep, Modifier.size(44.dp), RoundedCornerShape(12.dp))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    // Lines of varied length read as text rather than bars
                    SkeletonBlock(sweep, Modifier.fillMaxWidth(if (index % 2 == 0) 0.7f else 0.55f).height(12.dp))
                    SkeletonBlock(sweep, Modifier.fillMaxWidth(if (index % 2 == 0) 0.4f else 0.5f).height(10.dp))
                }
            }
        }
    }
}

/** Card placeholders: a title line, two lines of text and a footer, in rounded cards. */
@Composable
fun SkeletonCards(count: Int = 3, modifier: Modifier = Modifier) {
    val sweep = rememberSkeletonSweep()
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Loading" }
    ) {
        repeat(count) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f))
                    .padding(18.dp)
            ) {
                SkeletonBlock(sweep, Modifier.fillMaxWidth(0.45f).height(14.dp))
                SkeletonBlock(sweep, Modifier.fillMaxWidth().height(10.dp))
                SkeletonBlock(sweep, Modifier.fillMaxWidth(0.8f).height(10.dp))
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkeletonBlock(sweep, Modifier.size(24.dp), CircleShape)
                    Spacer(Modifier.width(8.dp))
                    SkeletonBlock(sweep, Modifier.width(80.dp).height(10.dp))
                }
            }
        }
    }
}

/** Chat bubbles on alternating sides, for older messages that are still loading. */
@Composable
fun SkeletonChatBubbles(modifier: Modifier = Modifier) {
    val sweep = rememberSkeletonSweep()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { contentDescription = "Loading earlier messages" }
    ) {
        listOf(0.62f to false, 0.45f to true, 0.7f to false).forEach { (width, mine) ->
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                SkeletonBlock(
                    sweep,
                    Modifier.fillMaxWidth(width).height(38.dp),
                    if (mine) RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
                    else RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
                )
            }
        }
    }
}
