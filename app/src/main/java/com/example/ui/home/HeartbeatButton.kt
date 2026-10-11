package com.example.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The heartbeat colours, also used by Heartbeat Touch and the Both of us card: pink and the app's
 * own blue, the same in every theme.
 */
val HeartbeatPink = Color(0xFFFF4F9A) // neon rose
val HeartbeatBlue = com.example.ui.theme.DayBluePrimary

/**
 * The heart at the bottom of Love & Us: a tap opens Heartbeat Touch; a long press sends a
 * "thinking of you" heartbeat to the partner's phone (a small heart floats up when it's sent).
 * It beats gently while the partner is holding their side of Heartbeat Touch.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeartbeatButton(
    isPartnerTouching: Boolean,
    onOpenHeartbeatTouch: () -> Unit,
    onSendThinkingOfYou: () -> Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val sent = remember { Animatable(0f) }
    // Beats only while the partner is touching (no animation running the rest of the time)
    val beat = remember { Animatable(1f) }
    LaunchedEffect(isPartnerTouching) {
        if (isPartnerTouching) {
            while (true) {
                beat.animateTo(1.12f, tween(420, easing = FastOutSlowInEasing))
                beat.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            }
        } else {
            beat.animateTo(1f, tween(200))
        }
    }

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer {
                    val t = sent.value
                    val bump = when {
                        t <= 0f || t >= 0.3f -> 0f
                        t < 0.12f -> t / 0.12f
                        else -> (0.3f - t) / 0.18f
                    } * 0.15f
                    scaleX = beat.value + bump
                    scaleY = beat.value + bump
                }
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                // Pink to the app's blue, in every theme
                .background(Brush.linearGradient(listOf(HeartbeatPink, HeartbeatBlue)))
                .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                .combinedClickable(
                    onClickLabel = "Heartbeat Touch",
                    onClick = onOpenHeartbeatTouch,
                    onLongClickLabel = "Send a heartbeat",
                    // The heartbeat itself is the feedback (no extra click buzz before it)
                    hapticFeedbackEnabled = false,
                    onLongClick = {
                        // Sending also plays the thump-thump on this phone
                        if (onSendThinkingOfYou()) {
                            scope.launch {
                                sent.snapTo(0f)
                                sent.animateTo(1f, tween(1_600, easing = FastOutSlowInEasing))
                                sent.snapTo(0f)
                            }
                        }
                    }
                )
                .testTag("love_heartbeat_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = "Heartbeat Touch",
                tint = Color.White,
                modifier = Modifier.size(34.dp)
            )
        }

        // Expanding romantic pulse ring when heartbeat is sent
        if (sent.value > 0f) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer {
                        val t = sent.value
                        scaleX = 1f + t * 0.8f
                        scaleY = 1f + t * 0.8f
                        alpha = ((1f - t) * 0.8f).coerceIn(0f, 1f)
                    }
                    .border(2.5.dp, HeartbeatPink, CircleShape)
            )
        }

        // A vibrant bouquet of glowing hearts floats upward with sway & scale
        FloatingHeartSpecs.forEach { spec ->
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = spec.color,
                modifier = Modifier
                    .size(spec.size)
                    .graphicsLayer {
                        val t = sent.value
                        if (t <= spec.delay || t >= 1f) {
                            alpha = 0f
                        } else {
                            val progress = ((t - spec.delay) / (1f - spec.delay)).coerceIn(0f, 1f)
                            // Quick pop-in, then smooth float, then graceful fade out
                            alpha = when {
                                progress < 0.18f -> (progress / 0.18f)
                                progress > 0.65f -> ((1f - progress) / 0.35f).coerceIn(0f, 1f)
                                else -> 1f
                            }
                            // Scale: pops out lively and gently settles
                            val scale = when {
                                progress < 0.22f -> 0.35f + (progress / 0.22f) * 0.85f
                                progress > 0.75f -> 1.2f - ((progress - 0.75f) / 0.25f) * 0.4f
                                else -> 1.2f
                            }
                            scaleX = scale
                            scaleY = scale

                            // Soaring upward rise
                            translationY = -progress * spec.targetY.dp.toPx()

                            // Romantic floating sway
                            val sway = kotlin.math.sin(progress * spec.swayFreq) * 10.dp.toPx()
                            translationX = (progress * spec.targetX.dp.toPx()) + sway
                        }
                    }
            )
        }
    }
}

private data class HeartSpec(
    val size: androidx.compose.ui.unit.Dp,
    val color: Color,
    val targetY: Float,
    val targetX: Float,
    val swayFreq: Float,
    val delay: Float
)

private val FloatingHeartSpecs = listOf(
    // Prominent hero heart in the center
    HeartSpec(size = 32.dp, color = HeartbeatPink, targetY = 160f, targetX = 0f, swayFreq = 6f, delay = 0f),
    // Flanking large & medium hearts
    HeartSpec(size = 26.dp, color = Color(0xFFFF2D55), targetY = 175f, targetX = -28f, swayFreq = 8f, delay = 0.05f),
    HeartSpec(size = 26.dp, color = Color(0xFFFF3366), targetY = 180f, targetX = 28f, swayFreq = 7f, delay = 0.07f),
    // Drifting accent hearts
    HeartSpec(size = 20.dp, color = Color(0xFFFF6584), targetY = 190f, targetX = -48f, swayFreq = 9f, delay = 0.12f),
    HeartSpec(size = 20.dp, color = Color(0xFFFF4F9A), targetY = 195f, targetX = 46f, swayFreq = 9f, delay = 0.15f),
    // High-flying sparkling mini hearts
    HeartSpec(size = 16.dp, color = Color(0xFFFF7597), targetY = 210f, targetX = -18f, swayFreq = 11f, delay = 0.20f),
    HeartSpec(size = 16.dp, color = Color(0xFFFF2D55), targetY = 215f, targetX = 20f, swayFreq = 10f, delay = 0.22f)
)
