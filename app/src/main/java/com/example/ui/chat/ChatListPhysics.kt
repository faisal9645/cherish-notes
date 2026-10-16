package com.example.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The message list's springs (the iMessage feel): while the chat scrolls, each message trails a hair
 * behind and then settles, more the farther it is from the finger, as if the messages were joined by
 * springs. Only drawing reads it, so scrolling never recomposes; with system animations off it's
 * still.
 */
@Stable
class ChatListPhysics internal constructor(
    private val scope: CoroutineScope,
    private val enabled: Boolean,
    private val maxLagPx: Float,
) {
    /** How far a message at the far end of the list trails behind the scroll, in px (signed). */
    private val lag = Animatable(0f)

    /** Where the finger is (or last was) in the list, in px from its top; NaN before any touch. */
    internal var anchorY by mutableFloatStateOf(Float.NaN)

    /** The list's height in px. */
    var viewportHeight by mutableFloatStateOf(0f)

    /** Gives the list's scroll to the springs (put on the LazyColumn, before its own scrolling). */
    val nestedScroll: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (enabled && consumed.y != 0f) pull(-consumed.y * LAG_PER_PX)
            return Offset.Zero
        }
    }

    private fun pull(by: Float) {
        scope.launch {
            // The springs keep their swing between pulls, so a fling and its settling are one motion
            val velocity = lag.velocity
            lag.snapTo((lag.value + by).coerceIn(-maxLagPx, maxLagPx))
            lag.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = 300f), initialVelocity = velocity)
        }
    }

    /** How far a message centred at [centerY] (px from the list's top) is displaced right now. */
    fun offsetFor(centerY: Float): Float {
        val height = viewportHeight
        if (!enabled || height <= 0f) return 0f
        val anchor = if (anchorY.isNaN()) height else anchorY
        return lag.value * (abs(centerY - anchor) / height).coerceAtMost(1f)
    }

    /** The messages squash a little at speed (up to 3%), like jelly, and round out as they settle. */
    fun squashY(): Float =
        if (!enabled || maxLagPx <= 0f) 1f else 1f - (abs(lag.value) / maxLagPx).coerceIn(0f, 1f) * 0.03f

    private companion object {
        /** How much each px of scroll pulls the far messages behind (balanced by the spring). */
        const val LAG_PER_PX = 0.4f
    }
}

@Composable
fun rememberChatListPhysics(): ChatListPhysics {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val maxLagPx = with(LocalDensity.current) { 22.dp.toPx() }
    return remember(scope) { ChatListPhysics(scope, enabled = !context.areAnimationsDisabled(), maxLagPx = maxLagPx) }
}

/** On the message list: remembers where the finger is, without taking any touches from the list. */
fun Modifier.chatListTouchAnchor(physics: ChatListPhysics): Modifier = pointerInput(physics) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        physics.anchorY = down.position.y
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            physics.anchorY = change.position.y
            if (!change.pressed) break
        }
    }
}

/** On each message row: trails, squashes and settles with the list's springs. */
fun Modifier.chatRowSpring(physics: ChatListPhysics): Modifier = composed {
    val center = remember { mutableFloatStateOf(0f) }
    this
        .onPlaced { coordinates ->
            center.floatValue = coordinates.positionInParent().y + coordinates.size.height / 2f
        }
        .graphicsLayer {
            translationY = physics.offsetFor(center.floatValue)
            scaleY = physics.squashY()
        }
}
