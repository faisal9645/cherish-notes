package com.example.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.example.ui.theme.HeartRed
import com.example.ui.theme.RoseGoldPrimary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class FocusAction(
    val label: String,
    val icon: ImageVector,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

private val QuickReactions = listOf("❤️", "🥰", "😘", "🔥", "🥺", "👍")
private val ReactionSize = 42.dp
private val ReactionBarHeight = 52.dp
private val MenuWidth = 232.dp
private val MenuRowHeight = 46.dp

/**
 * Long-press focus view: the chat dims, the pressed message lifts in place, quick reactions pop in
 * above it and the actions appear below it. The message only moves as far as needed to fit both,
 * and shrinks if it is taller than the room left.
 *
 * [anchorBounds] is the message row in root coordinates. [progress] runs 0 -> 1 on open and back
 * to 0 before [onDismissed]; the caller can use it to blur the chat behind.
 */
@Composable
fun MessageFocusOverlay(
    isFromMe: Boolean,
    anchorBounds: Rect,
    progress: Animatable<Float, AnimationVector1D>,
    myReaction: String?,
    isPrivateMode: Boolean,
    actions: List<FocusAction>,
    onReaction: (String) -> Unit,
    onMoreReactions: () -> Unit,
    onDismissed: () -> Unit,
    bubble: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    var rootOffset by remember { mutableStateOf<Offset?>(null) }
    var closing by remember { mutableStateOf(false) }

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            progress.animateTo(0f, tween(170, easing = FastOutLinearInEasing))
            onDismissed()
        }
    }

    LaunchedEffect(Unit) {
        progress.snapTo(0f)
        progress.animateTo(1f, spring(dampingRatio = 0.74f, stiffness = 480f))
    }
    BackHandler { close() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOffset = it.positionInRoot() }
            .testTag("message_focus_overlay")
    ) {
        // Scrim: tap anywhere outside the message, reactions or menu to close
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Color.Black, alpha = 0.42f * progress.value.coerceIn(0f, 1f)) }
                .pointerInput(Unit) { detectTapGestures { close() } }
        )

        val origin = rootOffset ?: return@BoxWithConstraints
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val insets = WindowInsets.safeDrawing
        with(density) {
            val edge = 12.dp.toPx()
            val gap = 10.dp.toPx()
            val safeTop = insets.getTop(this) + 8.dp.toPx()
            val safeBottom = insets.getBottom(this) + 8.dp.toPx()
            val barH = ReactionBarHeight.toPx()
            val barW = (ReactionSize * QuickReactions.size + 48.dp).toPx()
            val menuW = MenuWidth.toPx()
            val menuH = MenuRowHeight.toPx() * actions.size + 12.dp.toPx()

            val anchor = anchorBounds.translate(-origin)
            val topLimit = safeTop + barH + gap
            val bottomLimit = screenH - safeBottom - menuH - gap
            val fitScale = if (anchor.height > 0f) ((bottomLimit - topLimit) / anchor.height).coerceIn(0.35f, 1f) else 1f
            val targetTop = anchor.top.coerceIn(topLimit, (bottomLimit - anchor.height * fitScale).coerceAtLeast(topLimit))
            val liftScale = fitScale * 1.03f
            fun bubbleTop(p: Float) = lerp(anchor.top, targetTop, p)
            fun bubbleScale(p: Float) = lerp(1f, liftScale, p)

            val sideX = { width: Float ->
                val x = if (isFromMe) anchor.right - width else anchor.left
                x.coerceIn(edge, (screenW - width - edge).coerceAtLeast(edge))
            }
            val barX = sideX(barW)
            val menuX = sideX(menuW)
            val sideOrigin = if (isFromMe) 1f else 0f

            // The pressed message, lifted
            Box(
                modifier = Modifier
                    .offset { IntOffset(anchor.left.roundToInt(), bubbleTop(progress.value).roundToInt()) }
                    .width(anchor.width.toDp())
                    .graphicsLayer {
                        val s = bubbleScale(progress.value)
                        scaleX = s
                        scaleY = s
                        transformOrigin = TransformOrigin(sideOrigin, 0f)
                    }
            ) {
                bubble()
            }

            // Quick reactions above it, popping in one after another
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .offset { IntOffset(barX.roundToInt(), (bubbleTop(progress.value) - gap - barH).roundToInt()) }
                    .height(ReactionBarHeight)
                    .graphicsLayer {
                        val p = progress.value
                        alpha = p.coerceIn(0f, 1f)
                        scaleX = 0.5f + 0.5f * p
                        scaleY = 0.5f + 0.5f * p
                        transformOrigin = TransformOrigin(sideOrigin, 1f)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    QuickReactions.forEachIndexed { index, emoji ->
                        val chosen = myReaction == emoji
                        Box(
                            modifier = Modifier
                                .size(ReactionSize)
                                .graphicsLayer {
                                    val local = ((progress.value - index * 0.06f) / 0.7f).coerceIn(0f, 1f)
                                    val pop = easeOutBack(local)
                                    scaleX = pop
                                    scaleY = pop
                                    alpha = local
                                }
                                .clip(CircleShape)
                                .background(
                                    when {
                                        !chosen -> Color.Transparent
                                        isPrivateMode -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                        else -> RoseGoldPrimary.copy(alpha = 0.18f)
                                    }
                                )
                                .clickable {
                                    view.chatHaptic(ChatHaptic.Tick)
                                    onReaction(emoji)
                                    close()
                                }
                                .testTag("focus_reaction_$emoji"),
                            contentAlignment = Alignment.Center
                        ) {
                            ChatEmoji(emoji = emoji, fontSize = 24.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                onMoreReactions()
                                close()
                            }
                            .testTag("focus_more_reactions"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "More reactions and actions",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Actions below it
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 10.dp,
                modifier = Modifier
                    .offset {
                        val p = progress.value
                        IntOffset(menuX.roundToInt(), (bubbleTop(p) + anchor.height * bubbleScale(p) + gap).roundToInt())
                    }
                    .width(MenuWidth)
                    .graphicsLayer {
                        val p = progress.value
                        alpha = p.coerceIn(0f, 1f)
                        scaleX = 0.7f + 0.3f * p
                        scaleY = 0.7f + 0.3f * p
                        transformOrigin = TransformOrigin(sideOrigin, 0f)
                    }
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    actions.forEach { action ->
                        val tint = if (action.isDestructive) HeartRed else MaterialTheme.colorScheme.onSurface
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(MenuRowHeight)
                                .clickable {
                                    action.onClick()
                                    close()
                                }
                                .padding(horizontal = 16.dp)
                                .testTag("focus_action_${action.label}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = action.label,
                                color = tint,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = action.icon,
                                contentDescription = null,
                                tint = if (action.isDestructive) HeartRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}
