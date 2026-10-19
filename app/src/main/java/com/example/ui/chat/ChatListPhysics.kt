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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
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
    private val scope: CoroutineScope? = null,
    private val enabled: Boolean = false,
    private val maxLagPx: Float = 0f,
    private val calm: Boolean = false,
) {
    internal var anchorY by mutableFloatStateOf(Float.NaN)
    var viewportHeight by mutableFloatStateOf(0f)

    val nestedScroll: NestedScrollConnection = object : NestedScrollConnection {}

    fun offsetFor(centerY: Float): Float = 0f

    fun squashY(): Float = 1f
}

@Composable
fun rememberChatListPhysics(calm: Boolean = false): ChatListPhysics {
    return remember { ChatListPhysics() }
}

/** On the message list: neutral touch anchor. */
fun Modifier.chatListTouchAnchor(physics: ChatListPhysics? = null): Modifier = this

/**
 * On each message row: returns this directly to remove rubber/spring effect while scrolling.
 */
fun Modifier.chatRowSpring(physics: ChatListPhysics? = null): Modifier = this

