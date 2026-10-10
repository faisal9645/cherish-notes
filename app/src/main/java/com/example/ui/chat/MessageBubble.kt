package com.example.ui.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.material.icons.automirrored.filled.Reply
import kotlin.math.abs
import kotlin.math.roundToInt

import com.example.util.ChatTimeFormatter

private fun formatMessageTime(rawTimestamp: Long): String {
    return ChatTimeFormatter.formatMessageTime(rawTimestamp)
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
    gallerySize: String = "large",
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
    partnerName: String = "Partner"
) {
    val bubbleShape = if (isPrivateMode) {
        if (isFromMe) {
            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
        } else {
            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
        }
    } else {
        if (isFromMe) {
            RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
        } else {
            RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
        }
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val bubbleBg = if (isPrivateMode) {
        if (isFromMe) {
            if (isDark) darkTone(Color(0xFF2E2F33)) else Color(0xFFE5E7EB)
        } else {
            if (isDark) darkTone(Color(0xFF1E1F22)) else Color(0xFFF3F4F6)
        }
    } else {
        if (isFromMe) {
            darkTone(DayBluePrimary)
        } else {
            if (isDark) darkSurface(Color(0xFF1E2638)) else Color(0xFFF1F5FB)
        }
    }

    val textColor = if (isPrivateMode) {
        if (isDark) darkTone(Color(0xFFECECEC)) else Color(0xFF111827)
    } else {
        if (isFromMe) {
            Color.White
        } else {
            if (isDark) darkTone(Color(0xFFF8FAFC)) else Color(0xFF0F172A)
        }
    }

    val timeColor = if (isPrivateMode) {
        textColor.copy(alpha = 0.65f)
    } else {
        if (isFromMe) Color.White.copy(alpha = 0.85f)
        else darkTone(DayBlueSecondary)
    }

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val view = androidx.compose.ui.platform.LocalView.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var showBurstHeart by remember { mutableStateOf(false) }
    val heartScale = remember { Animatable(0f) }
    val heartAlpha = remember { Animatable(0f) }
    val heartBurst = rememberHeartBurstState()

    // Bubble sinks a little under the finger (more on long-press) and springs back on release.
    // The press shows after a short delay so a scroll starting on a bubble doesn't flicker it.
    var isPressed by remember { mutableStateOf(false) }
    var isLongPressed by remember { mutableStateOf(false) }
    val pressScale by animateFloatAsState(
        targetValue = when {
            isLongPressed -> 0.94f
            isPressed -> 0.97f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 700f),
        label = "bubble_press"
    )

    // Highlight animation when scrolling from a reply quote to this message
    val highlightPulse = remember(message.id) { Animatable(0f) }
    LaunchedEffect(isHighlighted) {
        if (isHighlighted) {
            highlightPulse.snapTo(1f)
            highlightPulse.animateTo(0f, tween(2200, easing = LinearOutSlowInEasing))
        } else {
            highlightPulse.snapTo(0f)
        }
    }
    val trackPress: suspend androidx.compose.foundation.gestures.PressGestureScope.(androidx.compose.ui.geometry.Offset) -> Unit = {
        val showPress = scope.launch {
            delay(70)
            isPressed = true
        }
        tryAwaitRelease()
        showPress.cancel()
        isPressed = false
        isLongPressed = false
    }
    val onBubbleLongPress: (androidx.compose.ui.geometry.Offset) -> Unit = {
        isLongPressed = true
        view.chatHaptic(ChatHaptic.LongPress)
        onLongClick()
    }

    // Swipe-to-reply interactive state with density-aware threshold
    val density = androidx.compose.ui.platform.LocalDensity.current
    val thresholdPx = remember(density) { with(density) { 42.dp.toPx() } }
    val maxDragPx = remember(density) { with(density) { 76.dp.toPx() } }

    val swipeOffset = remember { Animatable(0f) }

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
            launch { heartBurst.burst() }
            showBurstHeart = true
            heartScale.snapTo(0.2f)
            heartAlpha.snapTo(1f)
            launch {
                heartScale.animateTo(
                    targetValue = 1.6f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }
            launch {
                delay(380)
                heartAlpha.animateTo(0f, animationSpec = tween(250))
                showBurstHeart = false
            }
        }
    }

    val isYouTube = remember(message.text) {
        YouTubeHelper.extractVideoId(message.text) != null
    }

    // Round video notes get the circle layout; shared videos are cards in a normal bubble
    val isVideoNote = remember(message) { message.isCircularVideoNote() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 2.5.dp),
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
                .offset { androidx.compose.ui.unit.IntOffset(swipeOffset.value.roundToInt(), 0) }
        ) {
            Column(
                horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start,
                modifier = (if (isYouTube) Modifier.fillMaxWidth() else Modifier)
                    .pointerInput(message.id, onSwipeToReply) {
                        if (onSwipeToReply != null) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    hasTriggeredThresholdHaptic = false
                                },
                                onDragEnd = {
                                    if (abs(swipeOffset.value) >= thresholdPx) {
                                        try {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        } catch (_: Exception) {}
                                        onSwipeToReply()
                                    }
                                    hasTriggeredThresholdHaptic = false
                                    scope.launch {
                                        swipeOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                    }
                                },
                                onDragCancel = {
                                    hasTriggeredThresholdHaptic = false
                                    scope.launch {
                                        swipeOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    val current = swipeOffset.value
                                    // Swipe in both left and right directions allowed on any message
                                    val newTarget = (current + dragAmount).coerceIn(-maxDragPx, maxDragPx)
                                    
                                    if (abs(newTarget) >= thresholdPx && !hasTriggeredThresholdHaptic) {
                                        hasTriggeredThresholdHaptic = true
                                        try {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        } catch (_: Exception) {}
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
                val videoHighlightScale = if (highlightPulse.value > 0f) {
                    1f + 0.04f * kotlin.math.sin(highlightPulse.value * Math.PI.toFloat())
                } else 1f
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .graphicsLayer {
                            scaleX = pressScale * videoHighlightScale
                            scaleY = pressScale * videoHighlightScale
                        }
                        .heartBurst(heartBurst)
                        .clip(CircleShape)
                        .then(
                            if (highlightPulse.value > 0.04f) {
                                Modifier.border(BorderStroke(3.dp, RoseGoldPrimary.copy(alpha = highlightPulse.value)), CircleShape)
                            } else Modifier
                        )
                        .pointerInput(message.id) {
                            detectTapGestures(
                                onPress = trackPress,
                                onLongPress = onBubbleLongPress,
                                onDoubleTap = { triggerHeartBurst() }
                            )
                        }
                        .testTag("message_bubble_${message.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    if (videoUrl.isNotBlank()) {
                        com.example.ui.components.CircularVideoNoteView(
                            videoUrl = videoUrl,
                            durationSeconds = message.durationSeconds,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Telegram-Style Overlay Status Chip (Timestamp & ticks on bottom-right of circle)
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 10.dp, end = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                        ) {
                            if (message.isPinned) {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = "Pinned",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(10.dp).padding(end = 2.dp)
                                )
                            }
                            if (message.isStarred) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Starred",
                                    tint = GoldMilestone,
                                    modifier = Modifier.size(10.dp).padding(end = 2.dp)
                                )
                            }
                            Text(
                                text = formatMessageTime(message.timestamp),
                                fontSize = 10.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                            if (isFromMe) {
                                Spacer(modifier = Modifier.width(3.dp))
                                MessageStatusTicks(message.getTypedStatus()) { status ->
                                    when (status) {
                                        MessageStatus.SENDING -> Icon(Icons.Default.AccessTime, "Sending", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(10.dp))
                                        MessageStatus.SENT -> Icon(Icons.Default.Check, "Sent", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(10.dp))
                                        MessageStatus.DELIVERED -> Icon(Icons.Default.DoneAll, "Delivered", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(11.dp))
                                        MessageStatus.READ -> Icon(Icons.Default.DoneAll, "Read", tint = Color.White, modifier = Modifier.size(11.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Bursting Heart Dopamine Pop Animation on Double-Tap
                    if (showBurstHeart) {
                        Box(
                            modifier = Modifier.matchParentSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "💖",
                                fontSize = 44.sp,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = heartScale.value
                                    scaleY = heartScale.value
                                    alpha = heartAlpha.value
                                }
                            )
                        }
                    }
                }
            } else {
                val isGenericPhotoText = message.getTypedType() == MessageType.IMAGE && (
                    message.text.isBlank() || 
                    message.text.equals("Sent a photo", ignoreCase = true) || 
                    message.text.matches(Regex("""Sent \d+ photos?"""))
                )

                // A photo with nothing else in the bubble fills it edge to edge: no bubble colour
                // around it (it used to show in the corner the photo's rounding left open)
                val isPhotoOnly = message.getTypedType() == MessageType.IMAGE && isGenericPhotoText &&
                    message.replyToText.isNullOrEmpty()
                val cardShape = if (isPhotoOnly) RoundedCornerShape(14.dp) else bubbleShape

                val bubbleMinWidth = when (message.getTypedType()) {
                    MessageType.IMAGE -> when (gallerySize.lowercase()) {
                        "small" -> 160.dp
                        "medium" -> 220.dp
                        else -> 280.dp
                    }
                    MessageType.AUDIO -> 260.dp
                    MessageType.VIDEO -> 240.dp
                    MessageType.DOCUMENT -> 200.dp
                    else -> 60.dp
                }
                val bubbleMaxWidth = when (message.getTypedType()) {
                    MessageType.IMAGE -> when (gallerySize.lowercase()) {
                        "small" -> 180.dp
                        "medium" -> 245.dp
                        else -> 310.dp
                    }
                    MessageType.AUDIO -> 310.dp
                    MessageType.VIDEO -> 268.dp
                    MessageType.DOCUMENT -> 260.dp
                    else -> 295.dp
                }

                val bubbleHighlightScale = if (highlightPulse.value > 0f) {
                    1f + 0.045f * kotlin.math.sin(highlightPulse.value * Math.PI.toFloat())
                } else 1f

                Box(
                    modifier = (if (isYouTube) Modifier.fillMaxWidth() else Modifier.widthIn(min = bubbleMinWidth, max = bubbleMaxWidth))
                        .graphicsLayer {
                            scaleX = pressScale * bubbleHighlightScale
                            scaleY = pressScale * bubbleHighlightScale
                        }
                        .heartBurst(heartBurst)
                        .then(
                            if (isPhotoOnly) {
                                Modifier.shadow(1.dp, cardShape)
                            } else if (isPrivateMode) {
                                Modifier.shadow(0.5.dp, cardShape)
                            } else {
                                if (isFromMe) Modifier.appGradientShadow(cardShape)
                                else Modifier.shadow(0.8.dp, cardShape)
                            }
                        )
                        .clip(cardShape)
                        .then(
                            if (highlightPulse.value > 0.04f) {
                                val strokeColor = if (isPrivateMode) Color(0xFF9CA3AF) else RoseGoldPrimary
                                Modifier.border(
                                    BorderStroke(2.2.dp, strokeColor.copy(alpha = highlightPulse.value)),
                                    cardShape
                                )
                            } else if (isPhotoOnly) Modifier
                            else if (isPrivateMode) Modifier.border(
                                BorderStroke(0.6.dp, if (isDark) darkTone(Color(0xFF38393E)) else Color(0xFFE5E7EB)),
                                cardShape
                            )
                            else if (!isFromMe) Modifier.border(
                                BorderStroke(0.5.dp, if (isDark) darkSurface(Color(0xFF2A364F), Color(0xFF262626)) else Color(0xFFE2E8F0)),
                                cardShape
                            )
                            else Modifier
                        )
                        .background(
                            if (isPhotoOnly) androidx.compose.ui.graphics.SolidColor(Color.Transparent)
                            else if (isPrivateMode) androidx.compose.ui.graphics.SolidColor(bubbleBg)
                            else if (isFromMe) appHorizontalGradient()
                            else androidx.compose.ui.graphics.SolidColor(if (isDark) darkSurface(Color(0xFF1E2638)) else Color(0xFFF1F5FB))
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
                                    }
                                },
                                onPress = trackPress,
                                onLongPress = onBubbleLongPress,
                                onDoubleTap = {
                                    triggerHeartBurst()
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
                        else PaddingValues(horizontal = 10.dp, vertical = 6.dp)
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
                                    .background(if (isPrivateMode) (if (isDark) darkTone(Color(0xFF9CA3AF)) else Color(0xFF6B7280)) else (if (isFromMe) Color.White else MaterialTheme.colorScheme.primary), CircleShape)
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
                                    modifier = Modifier.fillMaxWidth(),
                                    previewUrls = remember(message) { mediaList.map(message::thumbnailFor) }
                                )

                                // When there is no text caption, overlay the time & read status pill on the photo
                                if (isGenericPhotoText) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.Black.copy(alpha = 0.58f),
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (message.isPinned) {
                                                Icon(
                                                    imageVector = Icons.Filled.PushPin,
                                                    contentDescription = "Pinned",
                                                    tint = Color.White.copy(alpha = 0.9f),
                                                    modifier = Modifier.size(11.dp).padding(end = 3.dp)
                                                )
                                            }
                                            if (message.isStarred) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = "Starred",
                                                    tint = GoldMilestone,
                                                    modifier = Modifier.size(11.dp).padding(end = 3.dp)
                                                )
                                            }
                                            Text(
                                                text = formatMessageTime(message.timestamp),
                                                fontSize = 10.sp,
                                                color = Color.White.copy(alpha = 0.95f),
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (isFromMe) {
                                                Spacer(modifier = Modifier.width(3.dp))
                                                MessageStatusTicks(message.getTypedStatus()) { status ->
                                                    when (status) {
                                                        MessageStatus.SENDING -> Icon(Icons.Default.AccessTime, "Sending", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(10.dp))
                                                        MessageStatus.SENT -> Icon(Icons.Default.Check, "Sent", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(11.dp))
                                                        MessageStatus.DELIVERED -> Icon(Icons.Default.DoneAll, "Delivered", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(12.dp))
                                                        MessageStatus.READ -> Icon(Icons.Default.DoneAll, "Read", tint = Color(0xFF60A5FA), modifier = Modifier.size(12.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    MessageType.VIDEO -> {
                        val videoUrl = message.mediaUrl ?: message.mediaUrls.firstOrNull()
                        if (!videoUrl.isNullOrBlank()) {
                            val openBigVideo = {
                                if (onImageClickWithList != null) {
                                    onImageClickWithList(videoUrl, listOf(videoUrl))
                                } else {
                                    onImageClick(videoUrl)
                                }
                            }
                            // A shared video: a rounded card with its first frame, plays here or full screen
                            com.example.ui.components.CircularVideoNoteView(
                                videoUrl = videoUrl,
                                durationSeconds = message.durationSeconds,
                                onExpandClick = openBigVideo,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .padding(vertical = 2.dp)
                                    .fillMaxWidth()
                                    .aspectRatio(4f / 3f)
                            )
                        }
                    }
                    MessageType.DOCUMENT -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = "Document",
                                modifier = Modifier.size(32.dp),
                                tint = RoseGoldPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = message.mediaName ?: "Document",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    MessageType.AUDIO -> {
                        val playButtonGradient = if (isPrivateMode) {
                            null
                        } else if (isFromMe) {
                            null
                        } else {
                            appVerticalGradient()
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            // Sent voice message: profile icon on the LEFT side
                            if (isFromMe && !isPrivateMode) {
                                com.example.ui.components.AvatarView(
                                    photoUrl = senderPhotoUrl,
                                    name = message.senderName,
                                    size = 50.dp,
                                    isOnline = false,
                                    showOnlineBadge = false
                                )
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
                                                    (if (isDark) darkTone(Color(0xFFECECEC)) else Color(0xFF1F2937)).copy(alpha = 0.2f),
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
                                        imageVector = if (isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlayingAudio) "Pause voice message" else "Play voice message",
                                        tint = if (isPrivateMode) textColor else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Voice note "not heard yet" dot (small): a small blue dot on voice notes she hasn't played yet, which goes away once played
                                if (!message.isAudioPlayed) {
                                    Box(
                                        modifier = Modifier
                                            .padding(top = 1.dp, end = 1.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF2563EB))
                                            .border(1.2.dp, if (isFromMe) Color.White else MaterialTheme.colorScheme.surface, CircleShape)
                                            .testTag("voice_note_unplayed_dot")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                WaveformView(
                                    amplitudes = message.waveform.map { it.toFloat() },
                                    progress = audioProgress(),
                                    isPlaying = isPlayingAudio,
                                    isRecording = false,
                                    activeColor = if (isPrivateMode) textColor 
                                                  else if (isFromMe) Color.White 
                                                  else MaterialTheme.colorScheme.primary,
                                    inactiveColor = if (isPrivateMode) textColor.copy(alpha = 0.35f) 
                                                    else if (isFromMe) Color.White.copy(alpha = 0.38f) 
                                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    onSeek = onSeekAudio,
                                    height = 36.dp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${message.durationSeconds} sec",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isPrivateMode) textColor.copy(alpha = 0.8f) 
                                                    else if (isFromMe) Color.White.copy(alpha = 0.9f) 
                                                    else (if (isDark) darkTone(Color(0xFFCBD5E1)) else Color(0xFF0F172A))
                                        )
                                        if (isPlayingAudio && onToggleVoiceSpeed != null) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = (if (isPrivateMode) textColor else if (isFromMe) Color.White else darkTone(DayBluePrimary)).copy(alpha = 0.2f),
                                                modifier = Modifier.clickable { onToggleVoiceSpeed() }
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
                                                    color = if (isPrivateMode) textColor else if (isFromMe) Color.White else darkTone(DayBluePrimary),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = formatMessageTime(message.timestamp),
                                            fontSize = 10.sp,
                                            color = timeColor
                                        )
                                        if (isFromMe) {
                                            Spacer(modifier = Modifier.width(3.dp))
                                            MessageStatusTicks(message.getTypedStatus()) { status ->
                                                when (status) {
                                                    MessageStatus.SENDING -> {
                                                        Icon(Icons.Default.AccessTime, "Sending", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(11.dp))
                                                    }
                                                    MessageStatus.SENT -> {
                                                        Icon(Icons.Default.Check, "Sent", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                                                    }
                                                    MessageStatus.DELIVERED -> {
                                                        Icon(Icons.Default.DoneAll, "Delivered", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(13.dp))
                                                    }
                                                    MessageStatus.READ -> {
                                                        Icon(Icons.Default.DoneAll, "Read", tint = Color.White, modifier = Modifier.size(13.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Opposite user voice message: profile icon on the RIGHT side
                            if (!isFromMe && !isPrivateMode) {
                                Spacer(modifier = Modifier.width(8.dp))
                                com.example.ui.components.AvatarView(
                                    photoUrl = senderPhotoUrl,
                                    name = message.senderName,
                                    size = 50.dp,
                                    isOnline = false,
                                    showOnlineBadge = false
                                )
                            }
                        }
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
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = if (isPrivateMode) textColor
                                    else if (isFromMe) Color.White 
                                    else (if (isDark) darkTone(Color(0xFFCBD5E1)) else Color(0xFF0F172A))
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.align(Alignment.End),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (message.isPinned) {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = "Pinned",
                                    tint = if (isPrivateMode) textColor.copy(alpha = 0.8f) else if (isFromMe) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp).padding(end = 4.dp)
                                )
                            }
                            if (message.isStarred) {
                                Icon(
                                    imageVector = Icons.Default.Star,
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
                            if (isFromMe) {
                                Spacer(modifier = Modifier.width(3.dp))
                                MessageStatusTicks(message.getTypedStatus()) { status ->
                                    when (status) {
                                        MessageStatus.SENDING -> Icon(Icons.Default.AccessTime, "Sending", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(11.dp))
                                        MessageStatus.SENT -> Icon(Icons.Default.Check, "Sent", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                                        MessageStatus.DELIVERED -> Icon(Icons.Default.DoneAll, "Delivered", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(13.dp))
                                        MessageStatus.READ -> Icon(Icons.Default.DoneAll, "Read", tint = Color.White, modifier = Modifier.size(13.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Text Content & Inline YouTube / Link Preview
                if (message.text.isNotEmpty() && message.getTypedType() != MessageType.AUDIO && message.getTypedType() != MessageType.IMAGE) {
                    EmojiText(
                        text = message.text,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = if (message.isDeleted) FontStyle.Italic else FontStyle.Normal,
                        largeWhenEmojiOnly = true
                    )

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

                // Bubble Footer for non-audio, non-image, non-video messages: the time on every message
                if (message.getTypedType() != MessageType.AUDIO && message.getTypedType() != MessageType.IMAGE && message.getTypedType() != MessageType.VIDEO) {
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
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = "Pinned",
                                tint = if (isPrivateMode) textColor.copy(alpha = 0.8f) else if (isFromMe) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(12.dp)
                                    .padding(end = 4.dp)
                            )
                        }

                        if (message.isStarred) {
                            Icon(
                                imageVector = Icons.Default.Star,
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
                            MessageStatusTicks(message.getTypedStatus()) { status ->
                                when (status) {
                                    MessageStatus.SENDING -> {
                                        Icon(
                                            imageVector = Icons.Default.AccessTime,
                                            contentDescription = "Sending",
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                    MessageStatus.SENT -> {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Sent",
                                            tint = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                    MessageStatus.DELIVERED -> {
                                        Icon(
                                            imageVector = Icons.Default.DoneAll,
                                            contentDescription = "Delivered",
                                            tint = Color.White.copy(alpha = 0.9f),
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    MessageStatus.READ -> {
                                        Icon(
                                            imageVector = Icons.Default.DoneAll,
                                            contentDescription = "Read",
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Bursting Heart Dopamine Pop Animation on Double-Tap
            if (showBurstHeart) {
                Box(
                    modifier = Modifier.matchParentSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "💖",
                        fontSize = 44.sp,
                        modifier = Modifier.graphicsLayer {
                            scaleX = heartScale.value
                            scaleY = heartScale.value
                            alpha = heartAlpha.value
                        }
                    )
                }
            }
            }
        }

        // Emoji reactions pill below bubble: pops in with the first reaction, bumps when reactions
        // change and counts roll to their new value. The last non-empty set is kept so the pill
        // still has content while it shrinks away.
        val lastReactions = remember { arrayOf(message.reactions) }
        if (message.reactions.isNotEmpty()) lastReactions[0] = message.reactions
        val pillBump = remember { Animatable(1f) }
        val isFirstReactionsRun = remember { booleanArrayOf(true) }
        val reactionsSignature = message.reactions.entries.sortedBy { it.key }.joinToString { "${it.key}:${it.value}" }
        LaunchedEffect(reactionsSignature) {
            if (isFirstReactionsRun[0]) {
                isFirstReactionsRun[0] = false
            } else if (message.reactions.isNotEmpty()) {
                pillBump.snapTo(1.22f)
                pillBump.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f))
            }
        }
        AnimatedVisibility(
            visible = message.reactions.isNotEmpty(),
            enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = 500f), initialScale = 0.3f) + fadeIn(tween(120)),
            exit = scaleOut(tween(150), targetScale = 0.5f) + fadeOut(tween(150))
        ) {
            Surface(
                modifier = Modifier
                    .offset(y = (-6).dp)
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
                        .padding(horizontal = 8.dp, vertical = 2.5.dp)
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
                                    Text(text = " $shownCount", fontSize = 12.5.sp)
                                }
                            }
                        }
                    }
                }
            }
            // Seen photo (small): a tiny round photo of your partner sits under the last message they've read and glides down as they read more, like Messenger
            if (isFromMe && isLastReadMessage) {
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
        isReached -> if (isPrivateMode) Color(0xFF4B5563) else RoseGoldPrimary
        isPrivateMode -> if (isDark) darkTone(Color(0xFF1F2937)) else Color(0xFFF3F4F6)
        isDark -> darkTone(Color(0xFF2C2227))
        else -> Color(0xFFFFF0F5)
    }

    val iconTint = when {
        isReached -> Color.White
        isPrivateMode -> if (isDark) Color.White else Color(0xFF374151)
        else -> RoseGoldPrimary
    }

    val borderColor = when {
        isReached -> if (isPrivateMode) Color(0xFF9CA3AF) else RoseGoldPrimary
        isPrivateMode -> if (isDark) darkTone(Color(0xFF4B5563)) else Color(0xFFD1D5DB)
        else -> RoseGoldPrimary.copy(alpha = 0.5f)
    }

    Box(
        modifier = modifier
            .size(36.dp)
            .graphicsLayer {
                val p = progress()
                val scale = (0.55f + (p * 0.45f)).coerceIn(0.55f, if (isReached) 1.15f else 1.0f)
                scaleX = scale
                scaleY = scale
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
            imageVector = Icons.AutoMirrored.Filled.Reply,
            contentDescription = "Swipe to reply",
            tint = iconTint,
            modifier = Modifier.size(20.dp)
        )
    }
}
