package com.example.ui.chat

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Message
import com.example.ui.components.WaveformView
import com.example.ui.theme.HeartRed
import com.example.ui.theme.RoseGoldPrimary
import com.example.ui.theme.TrueDarkSurface
import com.example.ui.theme.appGradientShadow
import com.example.ui.theme.appHorizontalGradient
import kotlin.math.roundToInt

@Composable
fun MessageComposer(
    text: String,
    onTextChanged: (String) -> Unit,
    onSendText: () -> Unit,
    replyingTo: Message?,
    onDismissReply: () -> Unit,
    isRecordingVoice: Boolean,
    recordingDurationSec: Int,
    recordingAmplitudes: List<Float>,
    onStartVoiceRecord: () -> Unit,
    onStopAndSendVoiceRecord: () -> Unit,
    onCancelVoiceRecord: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickAttachment: () -> Unit,
    placeholder: String = "Message your love...",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var showEmojiQuickBar by remember { mutableStateOf(false) }
    var isLockedRecording by remember { mutableStateOf(false) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    // Reset lock & drag offsets when recording ends
    LaunchedEffect(isRecordingVoice) {
        if (!isRecordingVoice) {
            isLockedRecording = false
            dragOffsetX = 0f
            dragOffsetY = 0f
        }
    }

    // Pulsing animations for active recording (Telegram/WhatsApp style)
    val infiniteTransition = rememberInfiniteTransition(label = "recording_fx")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse_alpha"
    )
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -14f,
        animationSpec = infiniteRepeatable(tween(750, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer_offset"
    )

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val barBg = if (isDark) TrueDarkSurface else Color.White

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(barBg)
    ) {
        // Reply bar preview
        AnimatedVisibility(visible = replyingTo != null) {
            if (replyingTo != null) {
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(32.dp)
                                .background(RoseGoldPrimary, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Replying to ${replyingTo.senderName ?: "Partner"}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = RoseGoldPrimary
                            )
                            Text(
                                text = replyingTo.text,
                                fontSize = 12.sp,
                                maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = onDismissReply,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel reply",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Composer Input / Recording Action Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT / CENTER AREA
            if (isRecordingVoice) {
                if (isLockedRecording) {
                    // --- Case 1: Locked Hands-Free Recording Mode ---
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            isLockedRecording = false
                            onCancelVoiceRecord()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("composer_cancel_record_button")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(HeartRed.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Cancel voice note",
                                tint = HeartRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pulsing red recording dot
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .scale(1f + pulseAlpha * 0.2f)
                                .background(HeartRed.copy(alpha = pulseAlpha), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${recordingDurationSec / 60}:%02d".format(recordingDurationSec % 60),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        WaveformView(
                            amplitudes = recordingAmplitudes,
                            progress = 1f,
                            activeColor = HeartRed,
                            height = 20.dp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Locked Indicator Icon
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Hands-free locked",
                            tint = RoseGoldPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    // --- Case 2: Active Hold-to-Record Mode with Slide-to-Cancel & Slide-to-Lock ---
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Transparent)
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(24.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pulsing record indicator
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(HeartRed.copy(alpha = pulseAlpha), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${recordingDurationSec / 60}:%02d".format(recordingDurationSec % 60),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )

                        val dragProgress = (dragOffsetX / -150f).coerceIn(0f, 1f)
                        
                        Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterEnd) {
                            if (dragProgress < 1f) {
                                WaveformView(
                                    amplitudes = recordingAmplitudes,
                                    progress = 1f,
                                    activeColor = HeartRed.copy(alpha = 1f - dragProgress),
                                    height = 20.dp,
                                    modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = 1f - dragProgress }
                                )
                            }
                        }

                        val isNearCancel = dragOffsetX < -100f
                        if (isNearCancel) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = HeartRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Release to cancel",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = HeartRed
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.offset { IntOffset((dragOffsetX * 0.4f).roundToInt(), 0) }
                            ) {
                                Text(
                                    text = "‹‹‹",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.offset { IntOffset(shimmerOffset.roundToInt(), 0) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Slide to cancel",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            } else {
                // --- Normal Text Input & Media Tools ---
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Text Input Field in Center
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = onTextChanged,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("composer_text_input")
                        )
                    }

                    // Attachment Icon on Right side
                    IconButton(
                        onClick = onPickAttachment,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("composer_attach_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AttachFile,
                            contentDescription = "Attach file or photo",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Real-time Camera Snap Icon on Right side
                    IconButton(
                        onClick = onTakePhoto,
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("composer_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "Real-time camera snap",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // RIGHT ACTION BUTTON (Send Text / Send Voice / Hold-to-Record Mic)
            Box(
                contentAlignment = Alignment.BottomCenter
            ) {
                // Floating Lock indicator shown while holding Mic
                if (isRecordingVoice && !isLockedRecording) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .offset { IntOffset(0, -68.dp.roundToPx() + (dragOffsetY * 0.35f).roundToInt()) }
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (dragOffsetY < -50f) RoseGoldPrimary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (dragOffsetY < -50f) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = "Slide up to lock",
                            tint = if (dragOffsetY < -50f) RoseGoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = null,
                            tint = if (dragOffsetY < -50f) RoseGoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                AnimatedContent(
                    targetState = when {
                        text.isNotBlank() -> "SEND_TEXT"
                        isRecordingVoice && isLockedRecording -> "SEND_VOICE"
                        else -> "MIC"
                    },
                    transitionSpec = {
                        (scaleIn(initialScale = 0.8f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.8f) + fadeOut())
                    },
                    label = "right_action_btn"
                ) { state ->
                    when (state) {
                        "SEND_TEXT" -> {
                            // Case A: Send Text Button
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .appGradientShadow(CircleShape)
                                    .clip(CircleShape)
                                    .background(appHorizontalGradient())
                                    .clickable { 
                                        onSendText() 
                                        showEmojiQuickBar = false
                                    }
                                    .testTag("composer_send_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Send message",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        "SEND_VOICE" -> {
                            // Case B: Send Locked Voice Note Button
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .appGradientShadow(CircleShape)
                                    .clip(CircleShape)
                                    .background(appHorizontalGradient())
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isLockedRecording = false
                                        onStopAndSendVoiceRecord()
                                    }
                                    .testTag("composer_send_locked_voice_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Send voice note",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        "MIC" -> {
                            // Case C: Persistent Hold-to-Record Mic Button with Slide-Up-to-Lock
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .then(
                                        if (isRecordingVoice) {
                                            Modifier
                                                .appGradientShadow(CircleShape)
                                                .clip(CircleShape)
                                                .background(appHorizontalGradient())
                                        } else {
                                            Modifier
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer)
                                        }
                                    )
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            down.consume()
                                            val startTime = System.currentTimeMillis()
                                            dragOffsetX = 0f
                                            dragOffsetY = 0f
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onStartVoiceRecord()

                                            var hasTriggeredCancelHaptic = false
                                            var hasLocked = false

                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id }
                                                if (change == null || !change.pressed) {
                                                    break
                                                }
                                                change.consume()
                                                
                                                val delta = change.position - down.position
                                                dragOffsetX = delta.x.coerceIn(-240f, 0f)
                                                dragOffsetY = delta.y.coerceIn(-180f, 0f)

                                                // Slide UP to lock hands-free recording
                                                if (dragOffsetY < -65f && !hasLocked) {
                                                    hasLocked = true
                                                    isLockedRecording = true
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    break
                                                }

                                                if (dragOffsetX < -100f && !hasTriggeredCancelHaptic) {
                                                    hasTriggeredCancelHaptic = true
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                } else if (dragOffsetX >= -100f) {
                                                    hasTriggeredCancelHaptic = false
                                                }
                                            }

                                            // If locked, hands-free recording remains active on finger release
                                            if (!hasLocked && !isLockedRecording) {
                                                val elapsed = System.currentTimeMillis() - startTime
                                                if (dragOffsetX < -100f) {
                                                    // Swiped left to cancel
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onCancelVoiceRecord()
                                                } else if (elapsed < 500L) {
                                                    // Tapped too quickly: cancel and instruct
                                                    onCancelVoiceRecord()
                                                    Toast.makeText(context, "Hold to record, release to send", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    // Normal release: stop and send audio note
                                                    onStopAndSendVoiceRecord()
                                                }
                                            }
                                        }
                                    }
                                    .testTag("composer_voice_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Hold to record voice note",
                                    tint = if (isRecordingVoice) Color.White else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // WhatsApp-Style Centered Emoji Tab Button Below Typing Input
        if (!isRecordingVoice) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (showEmojiQuickBar) RoseGoldPrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, if (showEmojiQuickBar) RoseGoldPrimary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .clickable { showEmojiQuickBar = !showEmojiQuickBar }
                        .testTag("composer_emoji_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (showEmojiQuickBar) Icons.Default.Keyboard else Icons.Outlined.Mood,
                            contentDescription = "Toggle emoji bar",
                            tint = if (showEmojiQuickBar) RoseGoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (showEmojiQuickBar) "Keyboard" else "Emojis",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (showEmojiQuickBar) RoseGoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Quick Love Emojis Strip Below the Emoji Tab Button
        AnimatedVisibility(visible = showEmojiQuickBar && !isRecordingVoice) {
            Surface(
                color = Color.Transparent,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val quickEmojis = listOf(
                        listOf("❤️", "🩷", "🧡", "💛", "💚", "🩵", "💙", "💜"),
                        listOf("🤎", "🖤", "🤍", "💖", "💗", "💓", "💞", "💕"),
                        listOf("💘", "💝", "💟", "🥰", "😍", "😘", "😚", "😻"),
                        listOf("💋", "🫂", "🤗", "🫶", "💌", "🌹", "🧸", "✨")
                    )
                    
                    quickEmojis.forEach { rowEmojis ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            rowEmojis.forEach { emoji ->
                                Text(
                                    text = emoji,
                                    fontSize = 24.sp,
                                    modifier = Modifier
                                        .clickable {
                                            onTextChanged(text + emoji)
                                        }
                                        .padding(3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
