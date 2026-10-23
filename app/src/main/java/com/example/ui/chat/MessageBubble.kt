package com.example.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.zIndex
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Message
import com.example.data.model.MessageStatus
import com.example.data.model.MessageType
import com.example.ui.components.WaveformView
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import kotlin.math.abs
import kotlin.math.roundToInt

import com.example.util.ChatTimeFormatter
import com.example.ui.components.CherishToast
import com.example.ui.components.pressScaleTarget
import com.example.ui.components.springClickable
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.PhoneMissed

private fun formatMessageTime(rawTimestamp: Long): String {
    return ChatTimeFormatter.formatMessageTime(rawTimestamp)
}

// An album's own text ("Sent 3 photos"), compiled once rather than in every bubble
private val SentPhotosCaption = Regex("""Sent \d+ photos?""")

/** How a bubble is painted: see [rememberBubbleFill]. */
internal class BubbleFill(val brush: Brush, val edge: Color?)

/**
 * The chat's bubbles in plain, solid colours (no glass): mine in the Notes app's original blue
 * gradient, the same in day and dark (see [notesGradient]); my partner's solid mist or night with
 * a fine edge.
 */
@Composable
internal fun rememberBubbleFill(isFromMe: Boolean, isDark: Boolean): BubbleFill {
    val black = com.example.ui.theme.LocalAmoledBlack.current
    val received = if (isDark) darkSurface(NavyCard) else MistBlue
    val receivedEdge = if (isDark) darkSurface(NavyRaised, Neutral800) else Gray200
    return remember(isFromMe, black, isDark, received, receivedEdge) {
        if (isFromMe) BubbleFill(notesGradient(black = black, isDark = isDark), edge = null)
        else BubbleFill(SolidColor(received), receivedEdge)
    }
}

/** Double-tap heart burst effect: created and active ONLY when the user double-taps. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.HeartBurstPop(
    fontSize: androidx.compose.ui.unit.TextUnit = 36.sp,
    onFinished: () -> Unit
) {
    val heartScale = remember { Animatable(0.2f) }
    val heartAlpha = remember { Animatable(1f) }
    val burstState = rememberHeartBurstState()
    LaunchedEffect(Unit) {
        launch { burstState.burst() }
        launch {
            heartScale.animateTo(
                targetValue = 1.6f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        delay(380)
        heartAlpha.animateTo(0f, animationSpec = tween(250))
        onFinished()
    }
    Box(
        modifier = Modifier
            .matchParentSize()
            .heartBurst(burstState),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "💖",
            fontSize = fontSize,
            modifier = Modifier.graphicsLayer {
                scaleX = heartScale.value
                scaleY = heartScale.value
                alpha = heartAlpha.value
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isFromMe: Boolean,
    isPlayingAudio: Boolean,
    audioProgress: () -> Float,
    onPlayAudio: () -> Unit,
    onImageClick: (String) -> Unit,
    onLongClick: () -> Unit,
    onReactionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    gallerySize: String = "small",
    onImageClickWithList: ((String, List<String>) -> Unit)? = null,
    onSwipeToReply: (() -> Unit)? = null,
    onOpenTheaterVideo: ((String) -> Unit)? = null,
    voicePlaybackSpeed: Float = 1.0f,
    onToggleVoiceSpeed: (() -> Unit)? = null,
    isHighlighted: Boolean = false,
    onReplyQuoteClick: ((replyToMessageId: String?) -> Unit)? = null,
    isPrivateMode: Boolean = false,
    senderPhotoUrl: String? = null,
    onSeekAudio: ((Float) -> Unit)? = null,
    isLastReadMessage: Boolean = false,
    partnerPhotoUrl: String? = null,
    partnerName: String = "Partner",
    // Arrived while the chat is open (a big emoji then flies or pops in)
    isFreshArrival: Boolean = false,
    // My voice or video note that my partner is playing right now (false: they paused it), see
    // ListeningNow; its progress comes in [audioProgress]
    partnerHearing: Boolean? = null,
    isFocusOverlay: Boolean = false,
    onBubblePositioned: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null
) {
    // Private chat looks like the ChatGPT app: mine in a rounded blue bubble, my partner's as plain
    // text with a row of small actions under it
    val bubbleShape = if (isPrivateMode) {
        if (isFromMe) {
            RoundedCornerShape(24.dp)
        } else {
            RoundedCornerShape(4.dp)
        }
    } else {
        if (isFromMe) {
            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
        } else {
            RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
        }
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val isAmoledBlack = com.example.ui.theme.LocalAmoledBlack.current
    // The text size and font chosen in Settings
    val chatText = LocalChatTextStyle.current
    val fill = rememberBubbleFill(isFromMe, isDark)

    val bubbleBg = if (isPrivateMode) {
        if (isFromMe) {
            if (isAmoledBlack) Neutral800
            else if (isDark) Neutral750
            else PrivateBubbleLight
        } else {
            Color.Transparent
        }
    } else {
        if (isFromMe) {
            if (isDark) DarkBlueBubble else DayBluePrimary
        } else {
            if (isDark) Color(0xFF080B14) else MistBlue
        }
    }

    val textColor = if (isPrivateMode) {
        if (isDark) (if (isFromMe) Color.White else Neutral200) else InkBlack
    } else {
        if (isFromMe) {
            Color.White
        } else {
            if (isDark) darkTone(Color.White) else Slate900
        }
    }

    val timeColor = if (isPrivateMode) {
        textColor.copy(alpha = 0.5f)
    } else {
        if (isFromMe) Color.White.copy(alpha = 0.85f)
        else darkTone(DayBlueSecondary)
    }

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val view = androidx.compose.ui.platform.LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var showBurstHeart by remember { mutableStateOf(false) }
    // A tap on a text message plays its emojis again
    var emojiReplay by remember { mutableIntStateOf(0) }

    // Bubble cushion tip: tips a degree or two toward your finger like pressing one side of a cushion,
    // then springs flat with one small wobble.
    var isPressed by remember { mutableStateOf(false) }
    var isLongPressed by remember { mutableStateOf(false) }
    var bubbleCardSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val cushionTiltX = remember { Animatable(0f) }
    val cushionTiltY = remember { Animatable(0f) }
    val pressScale = animateFloatAsState(
        targetValue = when {
            isLongPressed -> 0.95f
            isPressed -> 0.985f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.42f, stiffness = 420f),
        label = "bubble_press"
    )

    // Highlight animation when scrolling from a reply quote to this message
    val highlightPulse = remember(message.id) { Animatable(0f) }
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            highlightPulse.animateTo(1f, tween(200))
        } else {
            if (highlightPulse.value > 0f) {
                highlightPulse.animateTo(0f, tween(1800, easing = LinearOutSlowInEasing))
            } else {
                highlightPulse.snapTo(0f)
            }
        }
    }
    val trackPress: suspend androidx.compose.foundation.gestures.PressGestureScope.(androidx.compose.ui.geometry.Offset) -> Unit = { touchOffset ->
        val showPress = scope.launch {
            delay(50)
            isPressed = true
            val width = bubbleCardSize.width.toFloat().coerceAtLeast(1f)
            val height = bubbleCardSize.height.toFloat().coerceAtLeast(1f)
            // Normalized touch offset from center: -1f (left/top) to +1f (right/bottom)
            val normX = ((touchOffset.x - width / 2f) / (width / 2f)).coerceIn(-1f, 1f)
            val normY = ((touchOffset.y - height / 2f) / (height / 2f)).coerceIn(-1f, 1f)
            // Tips 1 to 2 degrees toward the finger (pressing right tilts around Y; pressing bottom tilts around X)
            val targetRotY = normX * 1.8f
            val targetRotX = -normY * 1.8f
            launch {
                cushionTiltX.animateTo(targetRotX, spring(dampingRatio = 0.65f, stiffness = 550f))
            }
            launch {
                cushionTiltY.animateTo(targetRotY, spring(dampingRatio = 0.65f, stiffness = 550f))
            }
        }
        tryAwaitRelease()
        showPress.cancel()
        isPressed = false
        isLongPressed = false
        // Springs flat with one small wobble
        scope.launch {
            cushionTiltX.animateTo(0f, spring(dampingRatio = 0.38f, stiffness = 420f))
        }
        scope.launch {
            cushionTiltY.animateTo(0f, spring(dampingRatio = 0.38f, stiffness = 420f))
        }
    }
    val onBubbleLongPress: (androidx.compose.ui.geometry.Offset) -> Unit = {
        isLongPressed = true
        view.chatHaptic(ChatHaptic.LongPress)
        onLongClick()
    }

    // Swipe-to-reply interactive state with density-aware threshold (48dp detent threshold)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val thresholdPx = remember(density) { with(density) { 48.dp.toPx() } }
    val maxDragPx = remember(density) { with(density) { 88.dp.toPx() } }

    val swipeOffset = remember { Animatable(0f) }
    val magnetHop = remember { Animatable(0f) }

    val replyProgress by remember {
        derivedStateOf { (abs(swipeOffset.value) / thresholdPx).coerceIn(0f, 1f) }
    }
    val isReplyReached by remember {
        derivedStateOf { abs(swipeOffset.value) >= thresholdPx }
    }
    var hasTriggeredThresholdHaptic by remember { mutableStateOf(false) }
    // Which way the bubble is pulled: these change once per swipe, not every drag frame, so a
    // swipe doesn't rebuild the whole message (heavy for a YouTube or photo card) 60 times a second
    val isSwipedRight by remember { derivedStateOf { swipeOffset.value > 0f } }
    val isSwipedLeft by remember { derivedStateOf { swipeOffset.value < 0f } }

    fun triggerHeartBurst() {
        if (isPrivateMode) return
        scope.launch {
            context.vibrateHeartbeat()
            onReactionClick("❤️")
            showBurstHeart = true
        }
    }

    val isYouTube = remember(message.text) {
        YouTubeHelper.extractVideoId(message.text) != null
    }

    // Round video notes get the circle layout; shared videos are cards in a normal bubble
    val isVideoNote = remember(message) { message.isCircularVideoNote() }
    // Just 1-3 emojis: shown big with no bubble, like Telegram (not in private chat, which looks like ChatGPT)
    val bigEmojis = remember(message, isPrivateMode) { if (isPrivateMode) null else message.bigEmojis() }

    Box(
        modifier = if (isFocusOverlay) {
            modifier
        } else {
            modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp)
        },
        contentAlignment = if (isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        // Stationary background reply indicator on the left (revealed when swiping right on ANY message)
        if (onSwipeToReply != null && isSwipedRight) {
            ReplyIndicator(
                isReached = isReplyReached,
                progress = { replyProgress },
                isDark = isDark,
                isPrivateMode = isPrivateMode,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp)
            )
        }

        // Stationary background reply indicator on the right (revealed when swiping left on ANY message)
        if (onSwipeToReply != null && isSwipedLeft) {
            ReplyIndicator(
                isReached = isReplyReached,
                progress = { replyProgress },
                isDark = isDark,
                isPrivateMode = isPrivateMode,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start,
            modifier = (if (isYouTube) Modifier.fillMaxWidth() else Modifier)
                .offset { androidx.compose.ui.unit.IntOffset((swipeOffset.value + magnetHop.value).roundToInt(), 0) }
                // Swiping to reply, it leans a little (up to 2.5°) and stretches slightly in pull direction
                .graphicsLayer {
                    val rawOffset = swipeOffset.value + magnetHop.value
                    val pull = (rawOffset / maxDragPx).coerceIn(-1f, 1f)
                    if (pull != 0f) {
                        val anchorX = if (rawOffset >= 0f) 0f else 1f
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(anchorX, 0.5f)
                        rotationZ = 2.5f * pull
                        val pullAbs = abs(pull)
                        scaleX = 1f + 0.08f * pullAbs
                        scaleY = 1f - 0.025f * pullAbs
                    }
                }
        ) {
            Column(
                horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start,
                modifier = (if (isYouTube) Modifier.fillMaxWidth() else Modifier)
                    .then(
                        if (onBubblePositioned != null) {
                            Modifier.onGloballyPositioned { onBubblePositioned(it.boundsInRoot()) }
                        } else Modifier
                    )
                    .pointerInput(message.id, onSwipeToReply) {
                        if (onSwipeToReply != null) {
                            var accumulatedDragX = 0f
                            // Quadratic resistance: offset = x / (1 + |x| * k)
                            val quadraticK = 0.0035f
                            // The finger's speed, from its own path (the bubble under it moves)
                            val velocityTracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                            val flickSpeedPx = 900.dp.toPx()
                            val flickMinPx = 14.dp.toPx()
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    accumulatedDragX = 0f
                                    velocityTracker.resetTracking()
                                    hasTriggeredThresholdHaptic = false
                                },
                                onDragEnd = {
                                    val offset = swipeOffset.value
                                    val velocity = velocityTracker.calculateVelocity().x
                                    // A quick flick outwards replies too, even short of the mark
                                    val flicked = abs(velocity) >= flickSpeedPx && abs(offset) >= flickMinPx &&
                                        kotlin.math.sign(velocity) == kotlin.math.sign(offset)
                                    if (abs(offset) >= thresholdPx || flicked) {
                                        try {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        } catch (_: Exception) {}
                                        onSwipeToReply()
                                    }
                                    hasTriggeredThresholdHaptic = false
                                    // It springs back carrying the finger's speed (eased by the same
                                    // resistance the drag had), so letting go is one continuous motion
                                    val ease = 1f / (1f + abs(accumulatedDragX) * quadraticK)
                                    accumulatedDragX = 0f
                                    scope.launch {
                                        if (magnetHop.value != 0f) {
                                            launch { magnetHop.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 420f)) }
                                        }
                                        swipeOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                stiffness = Spring.StiffnessMediumLow,
                                                dampingRatio = Spring.DampingRatioMediumBouncy
                                            ),
                                            initialVelocity = velocity * ease * ease
                                        )
                                    }
                                },
                                onDragCancel = {
                                    hasTriggeredThresholdHaptic = false
                                    accumulatedDragX = 0f
                                    scope.launch {
                                        if (magnetHop.value != 0f) {
                                            launch { magnetHop.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 420f)) }
                                        }
                                        swipeOffset.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                stiffness = Spring.StiffnessMediumLow,
                                                dampingRatio = Spring.DampingRatioMediumBouncy
                                            )
                                        )
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    accumulatedDragX += dragAmount
                                    velocityTracker.addPosition(change.uptimeMillis, androidx.compose.ui.geometry.Offset(accumulatedDragX, 0f))
                                    val absX = abs(accumulatedDragX)
                                    val signX = kotlin.math.sign(accumulatedDragX)
                                    val dampedOffset = (absX / (1f + absX * quadraticK)) * signX
                                    val newTarget = dampedOffset.coerceIn(-maxDragPx, maxDragPx)

                                    if (abs(newTarget) >= thresholdPx && !hasTriggeredThresholdHaptic) {
                                        hasTriggeredThresholdHaptic = true
                                        try {
                                            // Crisp tactile tick right when detent threshold is breached
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                        } catch (_: Exception) {}
                                        // At the reply mark it hops slightly ahead with the haptic, as if a magnet caught it
                                        val hopDistance = with(density) { 10.dp.toPx() }
                                        scope.launch {
                                            magnetHop.snapTo(signX * hopDistance)
                                            magnetHop.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 420f))
                                        }
                                    } else if (abs(newTarget) < thresholdPx) {
                                        hasTriggeredThresholdHaptic = false
                                    }
                                    scope.launch { swipeOffset.snapTo(newTarget) }
                                }
                            )
                        }
                    }
            ) {
            if (isVideoNote) {
                // Telegram-Style Big Circular Video Note (standalone round circle without box card)
                val videoUrl = message.mediaUrl ?: message.mediaUrls.firstOrNull() ?: ""
                val openBigVideoNote = {
                    if (videoUrl.isNotBlank()) {
                        if (onImageClickWithList != null) {
                            onImageClickWithList(videoUrl, listOf(videoUrl))
                        } else {
                            onImageClick(videoUrl)
                        }
                    }
                }

                var isPlayingVideoNote by remember { mutableStateOf(false) }
                val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
                val restingCircleSize = 260.dp
                // Playing, it grows to the width of the screen, like Telegram: past the chat's side
                // margins (required size), centred on the screen
                val expandedCircleSize = screenWidth - 16.dp
                val bringPlayingNoteIntoView = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
                val animatedCircleSize by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (isPlayingVideoNote) expandedCircleSize else restingCircleSize,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                    ),
                    label = "circleVideoSize",
                    // Once grown, the chat scrolls so the whole circle is in view
                    finishedListener = {
                        if (isPlayingVideoNote) scope.launch { bringPlayingNoteIntoView.bringIntoView() }
                    }
                )

                Column(
                    horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start,
                    modifier = Modifier.wrapContentSize()
                ) {
                    Box(
                        modifier = Modifier
                            .requiredSize(animatedCircleSize)
                            .bringIntoViewRequester(bringPlayingNoteIntoView)
                            .onGloballyPositioned { bubbleCardSize = it.size }
                            .graphicsLayer {
                                val hp = highlightPulse.value
                                val vhs = if (hp > 0f) 1f + 0.04f * kotlin.math.sin(hp * Math.PI.toFloat()) else 1f
                                val ps = pressScale.value
                                scaleX = ps * vhs
                                scaleY = ps * vhs
                                cameraDistance = 16f * density.density
                                rotationX = cushionTiltX.value
                                rotationY = cushionTiltY.value
                            }
                            .then(
                                if (highlightPulse.value > 0.04f) {
                                    Modifier.border(BorderStroke(3.dp, RoseGoldPrimary.copy(alpha = highlightPulse.value)), CircleShape)
                                } else Modifier
                            )
                            .testTag("message_bubble_${message.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (videoUrl.isNotBlank()) {
                            com.example.ui.components.CircularVideoNoteView(
                                videoUrl = videoUrl,
                                durationSeconds = message.durationSeconds,
                                onExpandClick = openBigVideoNote,
                                onLongClick = { onBubbleLongPress(androidx.compose.ui.geometry.Offset.Zero) },
                                onDoubleClick = { triggerHeartBurst() },
                                onPlayingChanged = {
                                    isPlayingVideoNote = it
                                    // Watching my partner's with its sound: their phone shows "watching…"
                                    if (!isFromMe) VideoNoteWatching.report(message, it)
                                },
                                // In view, it plays by itself, muted, at this size, with no buttons
                                silentPreview = true,
                                posterUrl = remember(message) { message.thumbnailFor(videoUrl).takeIf { it != videoUrl && it.isNotBlank() } },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Bursting Heart Dopamine Pop Animation on Double-Tap
                        if (showBurstHeart) {
                            HeartBurstPop(fontSize = 36.sp, onFinished = { showBurstHeart = false })
                        }
                    }

                    // Align status chip closely against bottom edge of circular video
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .offset(y = (-10).dp)
                            .zIndex(1f)
                            .padding(horizontal = 6.dp)
                    ) {
                        // Its reactions on the circle's edge too, beside the time (not down below it)
                        if (isFromMe) {
                            ReactionsPill(message, isPrivateMode, onReactionClick, Modifier.padding(end = 4.dp))
                        }
                        // My partner watching it right now
                        if (partnerHearing != null && isFromMe) {
                            PartnerHearingChip(playing = partnerHearing)
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        FloatingTimeChip(message = message, isFromMe = isFromMe)
                        if (!isFromMe) {
                            ReactionsPill(message, isPrivateMode, onReactionClick, Modifier.padding(start = 4.dp))
                        }
                    }
                }
                DisposableEffect(message.id) {
                    // Scrolled away or gone while it played: no longer watching
                    onDispose { if (!isFromMe) VideoNoteWatching.report(message, false) }
                }
            } else if (bigEmojis != null) {
                // Telegram-style big emoji: no bubble, just the emojis, which come alive when they
                // arrive and again on a tap (see BigEmoji)
                var bigEmojiReplay by remember { mutableIntStateOf(0) }
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { bubbleCardSize = it.size }
                        .graphicsLayer {
                            val pulse = highlightPulse.value
                            val highlight = if (pulse > 0f) 1f + 0.06f * kotlin.math.sin(pulse * Math.PI.toFloat()) else 1f
                            val ps = pressScale.value
                            scaleX = ps * highlight
                            scaleY = ps * highlight
                            cameraDistance = 16f * density.density
                            rotationX = cushionTiltX.value
                            rotationY = cushionTiltY.value
                        }
                        .pointerInput(message.id) {
                            detectTapGestures(
                                onTap = { bigEmojiReplay++ },
                                onPress = trackPress,
                                onLongPress = onBubbleLongPress,
                                onDoubleTap = { triggerHeartBurst() }
                            )
                        }
                        .testTag("message_bubble_${message.id}")
                ) {
                    val isHug = bigEmojis.size == 1 && isHugScene(bigEmojis)
                    val isRecentIncomingHug = !isFromMe && isHug && (isFreshArrival || (System.currentTimeMillis() - message.timestamp < 120_000L && HugPlayedTracker.shouldPlay(message.id)))
                    BigEmoji(
                        emojis = bigEmojis,
                        isFromMe = isFromMe,
                        entrance = when {
                            isRecentIncomingHug -> BigEmojiEntrance.PopIn
                            !isFreshArrival -> BigEmojiEntrance.None
                            isFromMe -> BigEmojiEntrance.FlyFromComposer
                            else -> BigEmojiEntrance.PopIn
                        },
                        replay = bigEmojiReplay,
                        // A 🫂 held on the hug button plays as tight as it was sent
                        hugLevel = remember(message.effect) { message.hugSqueeze() },
                        footer = {
                            FloatingTimeChip(message = message, isFromMe = isFromMe, modifier = Modifier.offset(y = (-4).dp))
                        }
                    )

                    // Bursting Heart Dopamine Pop Animation on Double-Tap
                    if (showBurstHeart) {
                        HeartBurstPop(fontSize = 36.sp, onFinished = { showBurstHeart = false })
                    }
                }
            } else {
                val isGenericPhotoText = message.getTypedType() == MessageType.IMAGE && (
                    message.text.isBlank() ||
                    message.text.equals("Sent a photo", ignoreCase = true) ||
                    message.text.equals(GIF_CAPTION, ignoreCase = true) ||
                    message.text.matches(SentPhotosCaption)
                )

                // A photo with nothing else in the bubble fills it edge to edge: no bubble colour
                // around it (it used to show in the corner the photo's rounding left open)
                val isPhotoOnly = message.getTypedType() == MessageType.IMAGE && isGenericPhotoText &&
                    message.replyToText.isNullOrEmpty()
                val cardShape = if (isPhotoOnly) RoundedCornerShape(16.dp) else bubbleShape

                val bubbleMinWidth = when (message.getTypedType()) {
                    MessageType.IMAGE -> when (gallerySize.lowercase()) {
                        "small" -> 160.dp
                        "medium" -> 220.dp
                        else -> 280.dp
                    }
                    MessageType.AUDIO -> 275.dp
                    MessageType.CALL_RECORDING -> 250.dp
                    MessageType.VIDEO -> 240.dp
                    MessageType.DOCUMENT -> 240.dp
                    else -> 60.dp
                }
                val bubbleMaxWidth = when (message.getTypedType()) {
                    MessageType.IMAGE -> when (gallerySize.lowercase()) {
                        "small" -> 180.dp
                        "medium" -> 245.dp
                        else -> 310.dp
                    }
                    MessageType.AUDIO -> 325.dp
                    MessageType.CALL_RECORDING -> 300.dp
                    MessageType.VIDEO -> 268.dp
                    MessageType.DOCUMENT -> 285.dp
                    else -> if (isPrivateMode && !isFromMe) 340.dp else 295.dp
                }

                Box(
                    modifier = (if (isYouTube) Modifier.fillMaxWidth() else Modifier.widthIn(min = bubbleMinWidth, max = bubbleMaxWidth))
                        .onGloballyPositioned { bubbleCardSize = it.size }
                        .graphicsLayer {
                            val hp = highlightPulse.value
                            val bhs = if (hp > 0f) 1f + 0.045f * kotlin.math.sin(hp * Math.PI.toFloat()) else 1f
                            val ps = pressScale.value
                            scaleX = ps * bhs
                            scaleY = ps * bhs
                            cameraDistance = 16f * density.density
                            rotationX = cushionTiltX.value
                            rotationY = cushionTiltY.value
                        }
                        .then(
                            if (isPhotoOnly) {
                                Modifier.shadow(1.dp, cardShape)
                            } else if (isPrivateMode) {
                                Modifier
                            } else {
                                if (isFromMe) Modifier.appGradientShadow(cardShape)
                                else Modifier.shadow(0.8.dp, cardShape)
                            }
                        )
                        .clip(cardShape)
                        // A photo catches a soft sheen as the phone tilts (see tiltSheen)
                        .then(if (isPhotoOnly && !isPrivateMode) Modifier.tiltSheen() else Modifier)
                        .then(
                            if (highlightPulse.value > 0.04f) {
                                val strokeColor = if (isPrivateMode) Gray400 else RoseGoldPrimary
                                Modifier.border(
                                    BorderStroke(2.2.dp, strokeColor.copy(alpha = highlightPulse.value)),
                                    cardShape
                                )
                            } else if (isPhotoOnly) Modifier
                            else if (isPrivateMode) Modifier
                            // My partner's bubble has a fine edge; mine is just the blue
                            else fill.edge?.let { Modifier.border(BorderStroke(0.5.dp, it), cardShape) } ?: Modifier
                        )
                        .background(
                            if (isPhotoOnly) androidx.compose.ui.graphics.SolidColor(Color.Transparent)
                            else if (isPrivateMode) androidx.compose.ui.graphics.SolidColor(bubbleBg)
                            else fill.brush
                        )
                        .pointerInput(message.id) {
                            detectTapGestures(
                                onTap = {
                                    if (message.getTypedType() == MessageType.IMAGE) {
                                        val mediaList = message.getAllMediaUrls()
                                        if (mediaList.isNotEmpty()) {
                                            if (onImageClickWithList != null) {
                                                onImageClickWithList(mediaList[0], mediaList)
                                            } else {
                                                onImageClick(mediaList[0])
                                            }
                                        }
                                    } else if (message.getTypedType() == MessageType.DOCUMENT) {
                                        val docUrl = message.mediaUrl ?: message.mediaUrls.firstOrNull().orEmpty()
                                        val fileName = message.mediaName?.takeIf { it.isNotBlank() } ?: "Document"
                                        if (docUrl.isNotBlank()) {
                                            com.example.util.DocumentHelper.downloadDocument(context, docUrl, fileName)
                                        }
                                    } else {
                                        emojiReplay++
                                    }
                                },
                                onPress = trackPress,
                                onLongPress = onBubbleLongPress,
                                onDoubleTap = {
                                    if (!isPrivateMode) {
                                        triggerHeartBurst()
                                    }
                                }
                            )
                        }
                        .testTag("message_bubble_${message.id}")
                ) {
            Column(
                modifier = (if (isYouTube) Modifier.fillMaxWidth() else Modifier)
                    .padding(
                        if (isVideoNote) PaddingValues(0.dp) 
                        else if (isYouTube) PaddingValues(horizontal = 6.dp, vertical = 6.dp) 
                        else if (message.getTypedType() == MessageType.IMAGE) {
                            if (isGenericPhotoText && message.replyToText.isNullOrEmpty()) PaddingValues(0.dp)
                            else PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        }
                        else if (isPrivateMode && !isFromMe) PaddingValues(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 4.dp)
                        else if (isPrivateMode) PaddingValues(horizontal = 17.dp, vertical = 12.dp)
                        else PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    )
            ) {
                // Reply Quote Preview
                if (!message.replyToText.isNullOrEmpty()) {
                    Surface(
                        color = (if (isFromMe) Color.Black else MaterialTheme.colorScheme.surface).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                            .clickable {
                                onReplyQuoteClick?.invoke(message.replyToMessageId)
                            }
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(28.dp)
                                    .background(if (isPrivateMode) (if (isDark) darkTone(Gray400) else Gray500) else (if (isFromMe) Color.White else MaterialTheme.colorScheme.primary), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = message.replyToSenderName ?: "Partner",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor.copy(alpha = 0.9f)
                                )
                                EmojiText(
                                    text = message.replyToText ?: "",
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    color = textColor.copy(alpha = 0.75f),
                                    emojiScale = 1.15f
                                )
                            }
                        }
                    }
                }

                // Media Content
                when (message.getTypedType()) {
                    MessageType.IMAGE -> {
                        val mediaList = message.getAllMediaUrls()
                        if (mediaList.isNotEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                AdaptiveMediaGrid(
                                    urls = mediaList,
                                    gallerySize = gallerySize,
                                    onImageClick = { _, clickedUrl ->
                                        if (onImageClickWithList != null) {
                                            onImageClickWithList(clickedUrl, mediaList)
                                        } else {
                                            onImageClick(clickedUrl)
                                        }
                                    },
                                    onLongClick = {
                                        onBubbleLongPress(androidx.compose.ui.geometry.Offset.Zero)
                                    },
                                    onDoubleClick = {
                                        triggerHeartBurst()
                                    },
                                    onPress = trackPress,
                                    modifier = Modifier.fillMaxWidth(),
                                    previewUrls = remember(message) { mediaList.map(message::thumbnailFor) }
                                )

                                // When there is no text caption, overlay the time & read status pill on the photo
                                if (isGenericPhotoText) {
                                    val chip = timeChipColors()
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = chip.background,
                                        border = chip.border,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (message.isPinned) {
                                                Icon(
                                                    imageVector = Icons.Rounded.PushPin,
                                                    contentDescription = "Pinned",
                                                    tint = chip.content.copy(alpha = 0.9f),
                                                    modifier = Modifier.size(11.dp).padding(end = 4.dp)
                                                )
                                            }
                                            if (message.isStarred) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Star,
                                                    contentDescription = "Starred",
                                                    tint = GoldMilestone,
                                                    modifier = Modifier.size(11.dp).padding(end = 4.dp)
                                                )
                                            }
                                            Text(
                                                text = formatMessageTime(message.timestamp),
                                                fontSize = 10.sp,
                                                color = chip.content.copy(alpha = 0.95f),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    MessageType.VIDEO -> {
                        val videoUrl = message.mediaUrl ?: message.mediaUrls.firstOrNull()
                        if (!videoUrl.isNullOrBlank()) {
                            // Full screen grows out of this card (and shrinks back into it), like a photo
                            val videoAnchor = remember { MediaTileAnchor() }
                            val openBigVideo = {
                                videoAnchor.open()
                                if (onImageClickWithList != null) {
                                    onImageClickWithList(videoUrl, listOf(videoUrl))
                                } else {
                                    onImageClick(videoUrl)
                                }
                            }
                            val videoAspect = remember(videoUrl) {
                                val info = com.example.util.VideoThumbnailHelper.getVideoInfo(videoUrl)
                                info?.aspect?.coerceIn(0.72f, 1.9f) ?: (4f / 3f)
                            }
                            // A shared video: a rounded card with its first frame, plays here or full screen
                            com.example.ui.components.CircularVideoNoteView(
                                videoUrl = videoUrl,
                                durationSeconds = message.durationSeconds,
                                onExpandClick = openBigVideo,
                                shape = RoundedCornerShape(12.dp),
                                posterUrl = remember(message) { message.thumbnailFor(videoUrl).takeIf { it != videoUrl && it.isNotBlank() } },
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .fillMaxWidth()
                                    .aspectRatio(videoAspect)
                                    .mediaTileAnchor(videoAnchor)
                            )
                        }
                    }
                    MessageType.DOCUMENT -> {
                        val docUrl = message.mediaUrl ?: message.mediaUrls.firstOrNull().orEmpty()
                        val fileName = message.mediaName?.takeIf { it.isNotBlank() } ?: "Document"
                        val ext = fileName.substringAfterLast('.', "").uppercase().take(4)
                        val sizeText = com.example.util.DocumentHelper.formatFileSize(message.mediaSize)

                        val docIconBg = when (ext) {
                            "PDF" -> Red500
                            "DOC", "DOCX" -> if (isDark) DarkBluePrimary else Blue600
                            "XLS", "XLSX" -> Emerald500
                            "PPT", "PPTX" -> Color(0xFFF97316)
                            "ZIP", "RAR", "7Z" -> Color(0xFF8B5CF6)
                            else -> if (isPrivateMode) Gray500 else if (isDark) DarkBluePrimary else RoseGoldPrimary
                        }

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isPrivateMode) {
                                if (isDark) Neutral800 else Gray200
                            } else {
                                if (isFromMe) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.6f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (docUrl.isNotBlank()) {
                                        com.example.util.DocumentHelper.downloadDocument(context, docUrl, fileName)
                                    }
                                }
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(docIconBg),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (ext.isNotBlank() && ext.length <= 4) {
                                        Text(
                                            text = ext,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.InsertDriveFile,
                                            contentDescription = "Document",
                                            modifier = Modifier.size(24.dp),
                                            tint = Color.White
                                        )
                                    }
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fileName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            color = textColor
                                        ),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    if (sizeText.isNotBlank()) {
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = sizeText,
                                            fontSize = 11.sp,
                                            color = textColor.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        if (docUrl.isNotBlank()) {
                                            com.example.util.DocumentHelper.downloadDocument(context, docUrl, fileName)
                                        }
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDownward,
                                        contentDescription = "Download document",
                                        tint = if (isPrivateMode) (if (isDark) Color.White else Gray800) else (if (isFromMe) Color.White else MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                    MessageType.AUDIO -> {
                        val playButtonGradient = if (isPrivateMode) {
                            null
                        } else if (isFromMe) {
                            null
                        } else {
                            notesVerticalGradient()
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            // Sent voice message: profile icon on the LEFT side with sticky mic badge
                            if (isFromMe && !isPrivateMode) {
                                Box(
                                    modifier = Modifier.size(56.dp),
                                    contentAlignment = Alignment.BottomEnd
                                ) {
                                    com.example.ui.components.AvatarView(
                                        photoUrl = senderPhotoUrl,
                                        name = message.senderName,
                                        size = 56.dp,
                                        isOnline = false,
                                        showOnlineBadge = false
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White,
                                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .offset(x = 2.dp, y = 2.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.Mic,
                                                contentDescription = "Voice message",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Box(contentAlignment = Alignment.TopEnd) {
                                IconButton(
                                    onClick = onPlayAudio,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .then(
                                            if (isPrivateMode) {
                                                Modifier.background(
                                                    (if (isDark) darkTone(Neutral200) else Gray800).copy(alpha = 0.2f),
                                                    CircleShape
                                                )
                                            } else if (isFromMe) {
                                                Modifier.background(Color.White.copy(alpha = 0.25f), CircleShape)
                                            } else {
                                                Modifier.background(brush = playButtonGradient!!, shape = CircleShape)
                                            }
                                        )
                                ) {
                                    Icon(
                                        imageVector = if (isPlayingAudio) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = if (isPlayingAudio) "Pause voice message" else "Play voice message",
                                        tint = if (isPrivateMode) textColor else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                // Progress is read where it's drawn (the wave) or by second (the
                                // timer), so playing doesn't recompose this whole message 30 times a second
                                val latestAudioProgress by rememberUpdatedState(audioProgress)
                                val hasAudioProgress by remember { derivedStateOf { latestAudioProgress() > 0f } }
                                WaveformView(
                                    amplitudes = message.waveform.map { it.toFloat() },
                                    progressProvider = { latestAudioProgress() },
                                    // Moving as my partner hears it, too
                                    isPlaying = isPlayingAudio || partnerHearing == true,
                                    isRecording = false,
                                    activeColor = if (isPrivateMode) textColor 
                                                  else if (isFromMe) Color.White 
                                                  else MaterialTheme.colorScheme.primary,
                                    inactiveColor = if (isPrivateMode) textColor.copy(alpha = 0.35f) 
                                                    else if (isFromMe) Color.White.copy(alpha = 0.38f) 
                                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    onSeek = onSeekAudio,
                                    height = 36.dp,
                                    durationSeconds = message.durationSeconds
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                CompositionLocalProvider(
                                    LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth().height(18.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.height(18.dp)
                                        ) {
                                            val durationColor = if (isPrivateMode) textColor.copy(alpha = 0.8f)
                                                else if (isFromMe) Color.White.copy(alpha = 0.9f)
                                                else (if (isDark) darkTone(Slate300) else Slate900)
                                            val cachedTrackDuration = com.example.audio.VoicePlayerHelper.getTrackDuration(message.id)
                                            val parsedFromText = remember(message.text) {
                                                Regex("""\((\d+)\s*s\)""").find(message.text)?.groupValues?.get(1)?.toIntOrNull()
                                            }
                                            val effectiveTotal = if (message.durationSeconds > 0) message.durationSeconds
                                                else if (cachedTrackDuration > 0) cachedTrackDuration
                                                else if (parsedFromText != null && parsedFromText > 0) parsedFromText
                                                else if (message.waveform.isNotEmpty()) (message.waveform.size / 10).coerceAtLeast(1)
                                                else 0

                                            LaunchedEffect(message.id, message.mediaUrl, message.recordingPath) {
                                                if (message.durationSeconds <= 0 && cachedTrackDuration <= 0) {
                                                    val src = message.mediaUrl ?: message.recordingPath
                                                    if (!src.isNullOrBlank()) {
                                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                            com.example.audio.VoicePlayerHelper.extractAndCacheDuration(context, message.id, src)
                                                        }
                                                    }
                                                }
                                            }

                                            if (partnerHearing != null && isFromMe && !isPlayingAudio) {
                                                // My partner is hearing it right now
                                                PartnerHearingLabel(isVideo = false, playing = partnerHearing, color = durationColor)
                                            } else if (isPlayingAudio || hasAudioProgress) {
                                                // Where it's up to while it plays (and paused part way), counting up: "0:07 / 0:15"
                                                val total = if (effectiveTotal > 0) effectiveTotal else 1
                                                val elapsed by remember(total) {
                                                    derivedStateOf { (latestAudioProgress() * total).toInt().coerceIn(0, total) }
                                                }
                                                Text(
                                                    text = com.example.util.formatRecordingLength(elapsed) + " / " + com.example.util.formatRecordingLength(total),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = durationColor,
                                                    style = LocalTextStyle.current.steadyDigits
                                                )
                                            } else {
                                                Text(
                                                    text = if (effectiveTotal > 0) com.example.util.formatRecordingLength(effectiveTotal) else "Voice note",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = durationColor
                                                )
                                            }
                                            if ((isPlayingAudio || hasAudioProgress) && onToggleVoiceSpeed != null) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .pressScaleTarget()
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background((if (isPrivateMode) textColor else if (isFromMe) Color.White else darkTone(DayBluePrimary)).copy(alpha = 0.2f))
                                                        .springClickable { onToggleVoiceSpeed() }
                                                        .padding(horizontal = 4.dp, vertical = 1.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    val speedLabel = when (voicePlaybackSpeed) {
                                                        1.5f -> "1.5x"
                                                        2.0f -> "2x"
                                                        else -> "1x"
                                                    }
                                                    Text(
                                                        text = speedLabel,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isPrivateMode) textColor else if (isFromMe) Color.White else darkTone(DayBluePrimary)
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.height(18.dp)
                                        ) {
                                            Text(
                                                text = formatMessageTime(message.timestamp),
                                                fontSize = 10.sp,
                                                color = timeColor
                                            )

                                        }
                                    }
                                }
                            }

                            // Opposite user voice message: profile icon on the RIGHT side with sticky mic badge
                            if (!isFromMe && !isPrivateMode) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier.size(56.dp),
                                    contentAlignment = Alignment.BottomEnd
                                ) {
                                    com.example.ui.components.AvatarView(
                                        photoUrl = senderPhotoUrl,
                                        name = message.senderName,
                                        size = 56.dp,
                                        isOnline = false,
                                        showOnlineBadge = false
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White,
                                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.surface),
                                        modifier = Modifier
                                            .size(20.dp)
                                            .offset(x = 2.dp, y = 2.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.Mic,
                                                contentDescription = "Voice message",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    MessageType.CALL_RECORDING -> {
                        CallRecordingPlayerRow(
                            message = message,
                            colors = CallRecordingColors(
                                content = textColor,
                                button = when {
                                    isPrivateMode -> (if (isDark) darkTone(Neutral200) else Gray800).copy(alpha = 0.2f)
                                    isFromMe -> Color.White.copy(alpha = 0.25f)
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                buttonIcon = if (isPrivateMode) textColor else Color.White,
                                track = textColor.copy(alpha = 0.25f),
                                progress = if (isPrivateMode || isFromMe) textColor else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 2.dp),
                            trailing = {
                                Text(
                                    text = formatMessageTime(message.timestamp),
                                    fontSize = 10.sp,
                                    color = timeColor
                                )
                            }
                        )
                    }

                    else -> {}
                }

                // Image Message Footer: Only rendered when user entered an actual custom text caption
                if (message.getTypedType() == MessageType.IMAGE && !isGenericPhotoText) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, start = 4.dp, end = 4.dp, bottom = 2.dp)
                    ) {
                        EmojiText(
                            text = message.text,
                            style = chatText.applyTo(MaterialTheme.typography.bodyMedium, extraSp = -1),
                            fontWeight = FontWeight.Normal,
                            color = if (isPrivateMode) textColor
                                    else if (isFromMe) Color.White 
                                    else (if (isDark) darkTone(Slate300) else Slate900)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (message.isPinned) {
                                Icon(
                                    imageVector = Icons.Rounded.PushPin,
                                    contentDescription = "Pinned",
                                    tint = if (isPrivateMode) textColor.copy(alpha = 0.8f) else if (isFromMe) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp).padding(end = 4.dp)
                                )
                            }
                            if (message.isStarred) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = "Starred",
                                    tint = GoldMilestone,
                                    modifier = Modifier.size(12.dp).padding(end = 4.dp)
                                )
                            }
                            Text(
                                text = formatMessageTime(message.timestamp),
                                fontSize = 10.sp,
                                color = timeColor
                            )
                        }
                    }
                }

                // Text Content & Inline YouTube / Link Preview
                if (message.text.isNotEmpty() && message.getTypedType() != MessageType.AUDIO && message.getTypedType() != MessageType.IMAGE &&
                    message.getTypedType() != MessageType.CALL_RECORDING
                ) {
                    val isCallEvent = remember(message.text, message.callId) {
                        !message.callId.isNullOrBlank() ||
                        message.text.contains("Missed voice call", ignoreCase = true) ||
                        message.text.contains("Missed call", ignoreCase = true) ||
                        message.text.contains("Call declined", ignoreCase = true) ||
                        message.text.contains("Cancelled call", ignoreCase = true) ||
                        message.text.contains("Voice call", ignoreCase = true)
                    }

                    if (isCallEvent) {
                        val isDeclined = message.text.contains("declined", ignoreCase = true)
                        val isCancelled = message.text.contains("cancelled", ignoreCase = true)
                        val isMissed = message.text.contains("missed", ignoreCase = true)
                        val isCompleted = !isDeclined && !isCancelled && !isMissed
                        val iconColor = when {
                            isFromMe -> Color.White
                            isCompleted -> Emerald500
                            isCancelled -> Amber500
                            else -> Red500
                        }
                        val iconBg = when {
                            isFromMe -> Color.White.copy(alpha = 0.22f)
                            isCompleted -> Emerald500.copy(alpha = 0.15f)
                            isCancelled -> Amber500.copy(alpha = 0.15f)
                            else -> Red500.copy(alpha = 0.15f)
                        }
                        val icon = when {
                            isDeclined || isCancelled -> Icons.Rounded.CallEnd
                            isMissed -> Icons.AutoMirrored.Rounded.PhoneMissed
                            else -> Icons.Rounded.Call
                        }
                        val subtitleText = when {
                            isDeclined -> "Call declined"
                            isCancelled -> "Cancelled call"
                            isMissed -> "Missed voice call"
                            message.durationSeconds > 0 -> "Duration • ${message.durationSeconds / 60}m ${message.durationSeconds % 60}s"
                            else -> "Voice call completed"
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(iconBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = iconColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = message.text.removePrefix("📞 ").trim(),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor
                                )
                                Text(
                                    text = subtitleText,
                                    fontSize = 11.sp,
                                    color = textColor.copy(alpha = 0.75f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        // Direct "Call Back" action button
                        Surface(
                            onClick = {
                                com.example.call.CallActivity.startOutgoing(context)
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isFromMe) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Phone,
                                    contentDescription = null,
                                    tint = if (isFromMe) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Call Back",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFromMe) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    } else {
                        if (isPrivateMode) {
                            // ChatGPT's reading size in private chat (2sp bigger, following Settings)
                            EmojiText(
                                text = message.text,
                                color = textColor,
                                style = chatText.applyTo(MaterialTheme.typography.bodyLarge, extraSp = 2),
                                fontStyle = if (message.isDeleted) FontStyle.Italic else FontStyle.Normal,
                                largeWhenEmojiOnly = true
                            )
                        } else {
                            // Its emojis come alive as it arrives, and again on a tap: a heart beats, 🤗 hugs...
                            AnimatedEmojiText(
                                text = message.text,
                                color = textColor,
                                style = chatText.applyTo(MaterialTheme.typography.bodyMedium),
                                fontStyle = if (message.isDeleted) FontStyle.Italic else FontStyle.Normal,
                                playOnArrival = isFreshArrival,
                                replay = emojiReplay
                            )
                        }

                        val youtubeVideoId = remember(message.text) {
                            YouTubeHelper.extractVideoId(message.text)
                        }
                        val genericUrl = remember(message.text, youtubeVideoId) {
                            if (youtubeVideoId == null) GenericLinkHelper.extractUrl(message.text) else null
                        }

                        if (youtubeVideoId != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            InlineYouTubeCard(
                                videoId = youtubeVideoId,
                                onOpenTheater = { vid -> onOpenTheaterVideo?.invoke(vid) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else if (genericUrl != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LinkPreviewCard(url = genericUrl, textColor = textColor)
                        }
                    }
                }

                // Bubble Footer for non-audio, non-image, non-video messages: the time on every message
                // (none in private chat, which shows no times, like ChatGPT)
                if (!isPrivateMode && message.getTypedType() != MessageType.AUDIO && message.getTypedType() != MessageType.IMAGE &&
                    message.getTypedType() != MessageType.VIDEO && message.getTypedType() != MessageType.CALL_RECORDING
                ) {
                    // Tapping the time of my message shows when it was seen (or delivered/sent)
                    var showStatusDetail by remember(message.id) { mutableStateOf(false) }
                    LaunchedEffect(showStatusDetail) {
                        if (showStatusDetail) {
                            kotlinx.coroutines.delay(2500)
                            showStatusDetail = false
                        }
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 2.dp)
                            .then(
                                if (isFromMe) {
                                    Modifier.pointerInput(message.id) {
                                        detectTapGestures(onTap = { showStatusDetail = !showStatusDetail })
                                    }
                                } else Modifier
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.isPinned) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = "Pinned",
                                tint = if (isPrivateMode) textColor.copy(alpha = 0.8f) else if (isFromMe) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(12.dp)
                                    .padding(end = 4.dp)
                            )
                        }

                        if (message.isStarred) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = "Starred",
                                tint = GoldMilestone,
                                modifier = Modifier
                                    .size(12.dp)
                                    .padding(end = 4.dp)
                            )
                        }

                        if (message.isEdited) {
                            Text(
                                text = "edited • ",
                                fontSize = 10.sp,
                                color = textColor.copy(alpha = 0.6f)
                            )
                        }

                        AnimatedContent(
                            targetState = showStatusDetail,
                            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false) },
                            label = "status_detail"
                        ) { detail ->
                            Text(
                                text = if (detail) statusDetail(message) else formatMessageTime(message.timestamp),
                                fontSize = 10.sp,
                                color = timeColor
                            )
                        }

                        if (isFromMe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            val tickTint = if (isPrivateMode) {
                                if (isDark) Gray400 else Gray500
                            } else Color.White
                            MessageStatusTicks(message.getTypedStatus()) { status ->
                                when (status) {
                                    MessageStatus.SENDING -> {
                                        Icon(
                                            imageVector = Icons.Rounded.AccessTime,
                                            contentDescription = "Sending",
                                            tint = tickTint.copy(alpha = 0.6f),
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                    MessageStatus.SENT -> {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Sent",
                                            tint = tickTint.copy(alpha = 0.8f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    MessageStatus.DELIVERED -> {
                                        Icon(
                                            imageVector = Icons.Rounded.DoneAll,
                                            contentDescription = "Delivered",
                                            tint = tickTint.copy(alpha = 0.9f),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    MessageStatus.READ -> {
                                        Icon(
                                            imageVector = Icons.Rounded.DoneAll,
                                            contentDescription = "Read",
                                            tint = if (isPrivateMode && !isDark) Blue600 else Color(0xFF60A5FA),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                }

                // Under my partner's message in private chat, like ChatGPT's answers: copy, 👍, 👎,
                // read aloud, share, and more (the message menu)
                if (isPrivateMode && !isFromMe) {
                    PrivateMessageActions(
                        message = message,
                        isDark = isDark,
                        onReact = onReactionClick,
                        onMore = onLongClick
                    )
                }
            }

            // Bursting Heart Dopamine Pop Animation on Double-Tap
            if (showBurstHeart) {
                HeartBurstPop(fontSize = 44.sp, onFinished = { showBurstHeart = false })
            }
            }
        }

        // Emoji reactions pill below the bubble (a round video note has it beside its time instead)
        if (!isVideoNote) {
            ReactionsPill(
                message = message,
                isPrivateMode = isPrivateMode,
                onReactionClick = onReactionClick,
                modifier = Modifier.offset(y = (-6).dp)
            )
        }
            // Seen photo (small): a tiny round photo of your partner sits under the last message they've read and glides down as they read more, like Messenger
            // It leaves the message she'd read before and slides down into place under this one
            androidx.compose.animation.AnimatedVisibility(
                visible = !isPrivateMode && isFromMe && isLastReadMessage,
                enter = androidx.compose.animation.expandVertically(tween(280)) +
                    slideInVertically(tween(320)) { -it } + fadeIn(tween(260)),
                exit = androidx.compose.animation.shrinkVertically(tween(240)) + fadeOut(tween(180))
            ) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, end = 2.dp)
                        .testTag("messenger_seen_indicator")
                ) {
                    com.example.ui.components.AvatarView(
                        photoUrl = partnerPhotoUrl,
                        name = partnerName,
                        size = 14.5.dp,
                        showOnlineBadge = false
                    )
                }
            }
        }
    }
}
}

/**
 * A message's emoji reactions in a pill: pops in with the first reaction, bumps when they change,
 * and counts roll to their new value. The last non-empty set is kept, so the pill still has its
 * emojis while it shrinks away.
 */
@Composable
private fun ReactionsPill(
    message: Message,
    isPrivateMode: Boolean,
    onReactionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = androidx.compose.ui.platform.LocalView.current
    val lastReactions = remember { arrayOf(message.reactions) }
    if (message.reactions.isNotEmpty()) lastReactions[0] = message.reactions
    val pillBump = remember { Animatable(1f) }
    // A reaction just added throws a few tiny hearts off the pill
    val pillHearts = rememberHeartBurstState()
    val lastReactionCount = remember { intArrayOf(message.reactions.size) }
    val isFirstReactionsRun = remember { booleanArrayOf(true) }
    val reactionsSignature = message.reactions.entries.sortedBy { it.key }.joinToString { "${it.key}:${it.value}" }
    LaunchedEffect(reactionsSignature) {
        val added = message.reactions.size > lastReactionCount[0]
        lastReactionCount[0] = message.reactions.size
        if (isFirstReactionsRun[0]) {
            isFirstReactionsRun[0] = false
        } else if (message.reactions.isNotEmpty()) {
            if (added) launch { pillHearts.burst(scale = 0.45f, count = 6) }
            pillBump.snapTo(1.22f)
            pillBump.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f))
        }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = !isPrivateMode && message.reactions.isNotEmpty(),
        enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = 500f), initialScale = 0.3f) + fadeIn(tween(120)),
        exit = scaleOut(tween(150), targetScale = 0.5f) + fadeOut(tween(150))
    ) {
        Surface(
            modifier = modifier
                // Before the pill's own clip, so its hearts fly past its edge
                .heartBurst(pillHearts)
                .graphicsLayer {
                    scaleX = pillBump.value
                    scaleY = pillBump.value
                }
                .testTag("reactions_pill_${message.id}"),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            shadowElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val emojiCounts = lastReactions[0].values.groupingBy { it }.eachCount()
                for ((emoji, count) in emojiCounts) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable {
                                view.chatHaptic(ChatHaptic.Tick)
                                onReactionClick(emoji)
                            }
                            .padding(horizontal = 2.dp)
                            .testTag("reaction_badge_${message.id}_$emoji")
                    ) {
                        ChatEmoji(emoji = emoji, fontSize = 15.sp)
                        if (count > 1) {
                            AnimatedContent(
                                targetState = count,
                                transitionSpec = {
                                    val up = targetState > initialState
                                    (slideInVertically { if (up) it else -it } + fadeIn())
                                        .togetherWith(slideOutVertically { if (up) -it else it } + fadeOut())
                                        .using(SizeTransform(clip = false))
                                },
                                label = "reaction_count"
                            ) { shownCount ->
                                Text(text = " $shownCount", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** How dark the time's pill is at night: a soft grey. */
internal const val TIME_CHIP_ALPHA = 0.55f

/** A time pill's colours, and the chips beside it: [read] is the read ticks where they're coloured. */
@Immutable
internal class TimeChipColors(val background: Color, val content: Color, val border: BorderStroke?, val read: Color)

/** White with a soft border by day (never grey), a soft dark grey at night. */
@Composable
internal fun timeChipColors(): TimeChipColors {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.surface.luminance() < 0.5f) {
        TimeChipColors(Color.Black.copy(alpha = TIME_CHIP_ALPHA), Color.White, border = null, read = Color(0xFF60A5FA))
    } else {
        TimeChipColors(
            background = scheme.surface.copy(alpha = 0.90f),
            content = scheme.onSurface,
            border = BorderStroke(0.7.dp, scheme.outlineVariant.copy(alpha = 0.6f)),
            read = Blue600
        )
    }
}

/** The time (with pin, star and ticks) in a small pill: under a round video note or a big emoji. */
@Composable
private fun FloatingTimeChip(message: Message, isFromMe: Boolean, modifier: Modifier = Modifier) {
    val chip = timeChipColors()
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = chip.background,
        border = chip.border,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            if (message.isPinned) {
                Icon(
                    imageVector = Icons.Rounded.PushPin,
                    contentDescription = "Pinned",
                    tint = chip.content.copy(alpha = 0.85f),
                    modifier = Modifier.size(10.dp).padding(end = 2.dp)
                )
            }
            if (message.isStarred) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = "Starred",
                    tint = GoldMilestone,
                    modifier = Modifier.size(10.dp).padding(end = 2.dp)
                )
            }
            Text(
                text = formatMessageTime(message.timestamp),
                fontSize = 10.sp,
                color = chip.content,
                fontWeight = FontWeight.Medium
            )
            if (isFromMe) {
                Spacer(modifier = Modifier.width(3.dp))
                MessageStatusTicks(message.getTypedStatus()) { status ->
                    when (status) {
                        MessageStatus.SENDING -> Icon(Icons.Rounded.AccessTime, "Sending", tint = chip.content.copy(alpha = 0.6f), modifier = Modifier.size(10.dp))
                        MessageStatus.SENT -> Icon(Icons.Rounded.Check, "Sent", tint = chip.content.copy(alpha = 0.8f), modifier = Modifier.size(10.dp))
                        MessageStatus.DELIVERED -> Icon(Icons.Rounded.DoneAll, "Delivered", tint = chip.content.copy(alpha = 0.9f), modifier = Modifier.size(11.dp))
                        MessageStatus.READ -> Icon(Icons.Rounded.DoneAll, "Read", tint = chip.read, modifier = Modifier.size(11.dp))
                    }
                }
            }
        }
    }
}

/** When my message was seen, or how far it got: shown for a moment after tapping its time. */
private fun statusDetail(message: Message): String = when (message.getTypedStatus()) {
    MessageStatus.READ -> "Seen " + formatMessageTime(message.readTimestamp ?: message.timestamp)
    MessageStatus.DELIVERED -> "Delivered"
    MessageStatus.SENT -> "Sent " + formatMessageTime(message.timestamp)
    MessageStatus.SENDING -> "Sending…"
}

/** Delivery ticks that pop in when the status changes (sent -> delivered -> read). */
@Composable
private fun MessageStatusTicks(
    status: MessageStatus,
    content: @Composable (MessageStatus) -> Unit
) {
    AnimatedContent(
        targetState = status,
        transitionSpec = {
            (scaleIn(spring(dampingRatio = 0.45f, stiffness = 650f), initialScale = 0.3f) + fadeIn(tween(120)))
                .togetherWith(fadeOut(tween(90)))
                .using(SizeTransform(clip = false))
        },
        label = "status_ticks"
    ) { shownStatus ->
        content(shownStatus)
    }
}

@Composable
private fun ReplyIndicator(
    isReached: Boolean,
    // Read while drawing, so the arrow grows with the swipe without recomposing anything
    progress: () -> Float,
    isDark: Boolean,
    isPrivateMode: Boolean,
    modifier: Modifier = Modifier
) {

    val backgroundColor = when {
        isReached -> if (isPrivateMode) Gray600 else RoseGoldPrimary
        isPrivateMode -> if (isDark) darkTone(Gray800) else Neutral200
        isDark -> Color(0xFF0E121E)
        else -> Color.White
    }

    val iconTint = when {
        isReached -> Color.White
        isPrivateMode -> if (isDark) Color.White else Gray700
        else -> RoseGoldPrimary
    }

    val borderColor = when {
        isReached -> if (isPrivateMode) Gray400 else RoseGoldPrimary
        isPrivateMode -> if (isDark) darkTone(Gray600) else Color(0xFFD1D5DB)
        else -> RoseGoldPrimary.copy(alpha = 0.5f)
    }

    val snapScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isReached) 1.18f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "reply_snap"
    )

    Box(
        modifier = modifier
            .size(36.dp)
            .graphicsLayer {
                val p = progress()
                val scale = ((0.55f + (p * 0.45f)).coerceIn(0.55f, 1.0f)) * snapScale
                scaleX = scale
                scaleY = scale
                // The arrow turns into place as the swipe fills, on an elastic curve
                rotationZ = -50f * (1f - p) * (1f - p)
                this.alpha = p.coerceIn(0.2f, 1f)
            }
            .shadow(
                elevation = if (isReached) 4.dp else 2.dp,
                shape = CircleShape,
                clip = false
            )
            .background(
                color = backgroundColor,
                shape = CircleShape
            )
            .border(
                width = if (isReached) 1.5.dp else 1.dp,
                color = borderColor,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.Reply,
            contentDescription = "Swipe to reply",
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** My bubble in private chat, dark and light: sleek ChatGPT tones. */
internal val PrivateBubbleDark = Neutral750
internal val PrivateBubbleLight = Neutral200

/** The small icons under my partner's message in private chat: copy, 👍, 👎, read aloud, share, more. */
@Composable
private fun PrivateMessageActions(
    message: Message,
    isDark: Boolean,
    onReact: (String) -> Unit,
    onMore: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tint = if (isDark) Color(0xFFB4B4B4) else Color(0xFF5D5D5D)
    val text = message.text
    // Only I can react to my partner's message, so these are mine
    val reacted = message.reactions.values
    Row(
        // The first icon lines up with the text, as in ChatGPT
        modifier = Modifier
            .padding(top = 12.dp, bottom = 6.dp)
            .offset(x = (-8).dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (text.isNotBlank()) {
            PrivateAction(Icons.Rounded.ContentCopy, "Copy", tint) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("Message", text))
                CherishToast.show("Copied")
            }
        }
        PrivateAction(if ("👍" in reacted) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp, "Like", tint) { onReact("👍") }
        PrivateAction(if ("👎" in reacted) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown, "Dislike", tint) { onReact("👎") }
        if (text.isNotBlank()) {
            PrivateAction(Icons.AutoMirrored.Rounded.VolumeUp, "Read aloud", tint) { ReadAloud.toggle(context, text) }
            PrivateAction(Icons.Rounded.Share, "Share", tint) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(send, null))
            }
        }
        PrivateAction(Icons.Rounded.MoreVert, "More", tint, onClick = onMore)
    }
}

@Composable
private fun PrivateAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .pressScaleTarget()
            .clip(CircleShape)
            .springClickable(onClick = onClick)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
    }
}
