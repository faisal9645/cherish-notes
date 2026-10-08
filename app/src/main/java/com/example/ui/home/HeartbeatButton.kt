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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.HeartRed
import kotlinx.coroutines.launch

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
    val haptic = LocalHapticFeedback.current
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
                .size(64.dp)
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
                .background(Brush.linearGradient(listOf(Color(0xFFFF6B8B), HeartRed)))
                .border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                .combinedClickable(
                    onClickLabel = "Heartbeat Touch",
                    onClick = onOpenHeartbeatTouch,
                    onLongClickLabel = "Send a heartbeat",
                    onLongClick = {
                        if (onSendThinkingOfYou()) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                sent.snapTo(0f)
                                sent.animateTo(1f, tween(1_100, easing = FastOutSlowInEasing))
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
                modifier = Modifier.size(30.dp)
            )
        }
        // A small heart floats up: the heartbeat was sent
        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            tint = HeartRed,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer {
                    val t = sent.value
                    alpha = if (t <= 0f || t >= 1f) 0f else 1f - t
                    translationY = -t * 70.dp.toPx()
                    translationX = kotlin.math.sin(t * 9f) * 5.dp.toPx()
                }
        )
    }
}
