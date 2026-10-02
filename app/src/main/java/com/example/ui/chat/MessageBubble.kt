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

private val timeFormatThreadLocal = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat {
        return SimpleDateFormat("h:mm a", Locale.getDefault())
    }
}

private fun formatMessageTime(timestamp: Long): String {
    return timeFormatThreadLocal.get()?.format(Date(timestamp)) ?: ""
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
    gallerySize: String = "medium",
    onImageClickWithList: ((String, List<String>) -> Unit)? = null,
    onSwipeToReply: (() -> Unit)? = null,
    onOpenTheaterVideo: ((String) -> Unit)? = null,
    voicePlaybackSpeed: Float = 1.0f,
    onToggleVoiceSpeed: (() -> Unit)? = null,
    isHighlighted: Boolean = false,
    onReplyQuoteClick: ((replyToMessageId: String?) -> Unit)? = null,
    isPrivateMode: Boolean = false
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
            if (isDark) Color(0xFF2E2F33) else Color(0xFFE5E7EB)
        } else {
            if (isDark) Color(0xFF1E1F22) else Color(0xFFF3F4F6)
        }
    } else {
        if (isFromMe) {
            DayBluePrimary
        } else {
            if (isDark) Color(0xFF1E2638) else Color(0xFFF1F5FB)
        }
    }

    val textColor = if (isPrivateMode) {
        if (isDark) Color(0xFFECECEC) else Color(0xFF111827)
    } else {
        if (isFromMe) {
            Color.White
        } else {
            if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
        }
    }

    val timeColor = if (isPrivateMode) {
        textColor.copy(alpha = 0.65f)
    } else {
        if (isFromMe) Color.White.copy(alpha = 0.85f)
        else DayBlueSecondary
    }

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var showBurstHeart by remember { mutableStateOf(false) }
    val heartScale = remember { Animatable(0f) }
    val heartAlpha = remember { Animatable(0f) }

    // Swipe-to-reply interactive state
    val swipeOffset = remember { Animatable(0f) }
    val replyIconAlpha by remember {
        derivedStateOf { (abs(swipeOffset.value) / 45f).coerceIn(0f, 1f) }
    }

    fun triggerHeartBurst() {
        if (isPrivateMode) return
        scope.launch {
            try {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            } catch (_: Exception) {}
            onReactionClick("❤️")
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.5.dp),
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!isFromMe && onSwipeToReply != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Reply,
                contentDescription = "Swipe to reply",
                tint = if (isPrivateMode) (if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)) else RoseGoldPrimary.copy(alpha = replyIconAlpha),
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = replyIconAlpha
                        scaleY = replyIconAlpha
                    }
                    .padding(end = 4.dp)
            )
        }

        Column(
            horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start,
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset(swipeOffset.value.roundToInt(), 0) }
                .pointerInput(message.id) {
                    if (onSwipeToReply != null) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (abs(swipeOffset.value) > 42f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSwipeToReply()
                                }
                                scope.launch {
                                    swipeOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                }
                            },
                            onDragCancel = {
                                scope.launch { swipeOffset.animateTo(0f) }
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                val target = if (isFromMe) {
                                    (swipeOffset.value + dragAmount).coerceIn(-65f, 0f)
                                } else {
                                    (swipeOffset.value + dragAmount).coerceIn(0f, 65f)
                                }
                                scope.launch { swipeOffset.snapTo(target) }
                            }
                        )
                    }
                }
        ) {
            val bubbleMinWidth = when (message.getTypedType()) {
                MessageType.IMAGE, MessageType.AUDIO -> 260.dp
                else -> 60.dp
            }
            val bubbleMaxWidth = when (message.getTypedType()) {
                MessageType.IMAGE, MessageType.AUDIO -> 310.dp
                else -> 295.dp
            }

            Box(
                modifier = Modifier
                    .widthIn(min = bubbleMinWidth, max = bubbleMaxWidth)
                    .then(
                        if (isPrivateMode) {
                            Modifier.shadow(0.5.dp, bubbleShape)
                        } else {
                            if (isFromMe) Modifier.appGradientShadow(bubbleShape)
                            else Modifier.shadow(0.8.dp, bubbleShape)
                        }
                    )
                    .clip(bubbleShape)
                    .then(
                        if (isHighlighted) Modifier.border(BorderStroke(2.dp, if (isPrivateMode) (if (isDark) Color(0xFF6B7280) else Color(0xFF9CA3AF)) else MaterialTheme.colorScheme.primary), bubbleShape)
                        else if (isPrivateMode) Modifier.border(
                            BorderStroke(0.6.dp, if (isDark) Color(0xFF38393E) else Color(0xFFE5E7EB)),
                            bubbleShape
                        )
                        else if (!isFromMe) Modifier.border(
                            BorderStroke(0.5.dp, if (isDark) Color(0xFF2A364F) else Color(0xFFE2E8F0)),
                            bubbleShape
                        )
                        else Modifier
                    )
                    .background(
                        if (isPrivateMode) androidx.compose.ui.graphics.SolidColor(bubbleBg)
                        else if (isFromMe) appHorizontalGradient()
                        else androidx.compose.ui.graphics.SolidColor(if (isDark) Color(0xFF1E2638) else Color(0xFFF1F5FB))
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
                            onLongPress = {
                                onLongClick()
                            },
                            onDoubleTap = {
                                triggerHeartBurst()
                            }
                        )
                    }
                    .testTag("message_bubble_${message.id}")
            ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
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
                                    .background(if (isPrivateMode) (if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)) else (if (isFromMe) Color.White else MaterialTheme.colorScheme.primary), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = message.replyToSenderName ?: "Partner",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor.copy(alpha = 0.9f)
                                )
                                Text(
                                    text = message.replyToText,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    color = textColor.copy(alpha = 0.75f)
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
                                modifier = Modifier.padding(bottom = 6.dp)
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
                            IconButton(
                                onClick = onPlayAudio,
                                modifier = Modifier
                                    .size(42.dp)
                                    .then(
                                        if (isPrivateMode) {
                                            Modifier.background(
                                                (if (isDark) Color(0xFFECECEC) else Color(0xFF1F2937)).copy(alpha = 0.2f),
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

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                WaveformView(
                                    amplitudes = message.waveform,
                                    progress = audioProgress(),
                                    activeColor = if (isPrivateMode) textColor 
                                                  else if (isFromMe) Color.White 
                                                  else MaterialTheme.colorScheme.primary,
                                    inactiveColor = if (isPrivateMode) textColor.copy(alpha = 0.35f) 
                                                    else if (isFromMe) Color.White.copy(alpha = 0.5f) 
                                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                    height = 40.dp
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
                                                    else (if (isDark) Color(0xFFCBD5E1) else Color(0xFF0F172A))
                                        )
                                        if (isPlayingAudio && onToggleVoiceSpeed != null) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = (if (isPrivateMode) textColor else if (isFromMe) Color.White else DayBluePrimary).copy(alpha = 0.2f),
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
                                                    color = if (isPrivateMode) textColor else if (isFromMe) Color.White else DayBluePrimary,
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
                                            when (message.getTypedStatus()) {
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
                    }
                    MessageType.DOCUMENT -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = textColor,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = message.mediaName ?: "Document",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textColor
                                )
                                Text(
                                    text = "${(message.mediaSize / 1024)} KB",
                                    fontSize = 10.sp,
                                    color = textColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    else -> {}
                }

                // Image Message Footer: "Sent a photo" on left, Time & Status on right
                if (message.getTypedType() == MessageType.IMAGE) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp, start = 2.dp, end = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (message.text.isNotBlank()) message.text else "Sent a photo",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = if (isPrivateMode) textColor 
                                    else if (isFromMe) Color.White 
                                    else (if (isDark) Color(0xFFCBD5E1) else Color(0xFF0F172A))
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
                                when (message.getTypedStatus()) {
                                    MessageStatus.SENDING -> Icon(Icons.Default.AccessTime, "Sending", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(11.dp))
                                    MessageStatus.SENT -> Icon(Icons.Default.Check, "Sent", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                                    MessageStatus.DELIVERED -> Icon(Icons.Default.DoneAll, "Delivered", tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(13.dp))
                                    MessageStatus.READ -> Icon(Icons.Default.DoneAll, "Read", tint = Color.White, modifier = Modifier.size(13.dp))
                                }
                            }
                        }
                    }
                }

                // Text Content & Inline YouTube / Link Preview
                if (message.text.isNotEmpty() && message.getTypedType() != MessageType.AUDIO && message.getTypedType() != MessageType.IMAGE) {
                    Text(
                        text = message.text,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = if (message.isDeleted) FontStyle.Italic else FontStyle.Normal
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
                            onOpenTheater = { vid -> onOpenTheaterVideo?.invoke(vid) }
                        )
                    } else if (genericUrl != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinkPreviewCard(url = genericUrl, textColor = textColor)
                    }
                }

                // Bubble Footer for non-audio, non-image messages
                if (message.getTypedType() != MessageType.AUDIO && message.getTypedType() != MessageType.IMAGE) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 2.dp),
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

                        Text(
                            text = formatMessageTime(message.timestamp),
                            fontSize = 10.sp,
                            color = timeColor
                        )

                        if (isFromMe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            when (message.getTypedStatus()) {
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

        // Emoji reactions pill below bubble
        if (message.reactions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .offset(y = (-6).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val emojiCounts = message.reactions.values.groupingBy { it }.eachCount()
                for ((emoji, count) in emojiCounts) {
                    Text(
                        text = "$emoji $count",
                        fontSize = 11.sp,
                        modifier = Modifier.clickable { onReactionClick(emoji) }
                    )
                }
            }
        }

        }

        if (isFromMe && onSwipeToReply != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Reply,
                contentDescription = "Swipe to reply",
                tint = if (isPrivateMode) (if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)) else RoseGoldPrimary.copy(alpha = replyIconAlpha),
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        scaleX = replyIconAlpha
                        scaleY = replyIconAlpha
                    }
                    .padding(start = 4.dp)
            )
        }
    }
}
