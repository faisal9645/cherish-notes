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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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

    val micScale by animateFloatAsState(
        targetValue = if (isRecordingVoice && !isLockedRecording) 1.25f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "mic_scale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        // Reply bar preview
        AnimatedVisibility(visible = replyingTo != null) {
            if (replyingTo != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
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

        // Quick Love Emojis Strip
        AnimatedVisibility(visible = showEmojiQuickBar) {
            Surface(
                color = Color.White,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val quickEmojis = listOf("❤️", "🥰", "😘", "💖", "✨", "🥺", "🌸", "🌹")
                    quickEmojis.forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 22.sp,
                            modifier = Modifier
                                .clickable {
                                    onTextChanged(text + emoji)
                                }
                                .padding(4.dp)
                        )
                    }
                }
            }
        }

        // Composer Input / Recording Action Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isRecordingVoice) {
                // ==========================================
                // TELEGRAM / WHATSAPP RECORDING INTERFACE
                // ==========================================
                if (isLockedRecording) {
                    // --- Case 1: Locked Hands-Free Recording Mode ---
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onCancelVoiceRecord()
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("composer_cancel_record_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Cancel voice note",
                            tint = HeartRed,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
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
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Send Button for locked mode
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .appGradientShadow(CircleShape)
                            .clip(CircleShape)
                            .background(appHorizontalGradient())
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                } else {
                    // --- Case 2: Active Hold-to-Record Mode with Slide-to-Cancel & Slide-up-to-Lock ---
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f))
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

                        Spacer(modifier = Modifier.weight(1f))

                        val isNearCancel = dragOffsetX < -160f
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
                                modifier = Modifier.offset { IntOffset((dragOffsetX * 0.35f).roundToInt(), 0) }
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

                    Spacer(modifier = Modifier.width(8.dp))

                    // Elevated Animated Mic Button with Lock Pill overhead
                    Box(contentAlignment = Alignment.Center) {
                        // Lock pill overhead
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .offset(y = (-56).dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(BorderStroke(1.dp, Color(0xFFE8E8EC)), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = RoseGoldPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = RoseGoldPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // The Pulsing Mic Button being held
                        Box(
                            modifier = Modifier
                                .scale(micScale)
                                .size(48.dp)
                                .appGradientShadow(CircleShape)
                                .clip(CircleShape)
                                .background(appHorizontalGradient())
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        down.consume()
                                        val startTime = System.currentTimeMillis()
                                        dragOffsetX = 0f
                                        dragOffsetY = 0f

                                        var cancelled = false

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                            if (change == null || !change.pressed) {
                                                break
                                            }
                                            val delta = change.position - down.position
                                            dragOffsetX = delta.x.coerceAtMost(0f)
                                            dragOffsetY = delta.y.coerceAtMost(0f)

                                            if (dragOffsetY < -140f && !isLockedRecording) {
                                                isLockedRecording = true
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                break
                                            }

                                            if (dragOffsetX < -180f && !cancelled) {
                                                cancelled = true
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        }

                                        val elapsed = System.currentTimeMillis() - startTime
                                        if (isLockedRecording) {
                                            // User locked by sliding up
                                        } else if (cancelled || dragOffsetX < -180f) {
                                            onCancelVoiceRecord()
                                        } else if (elapsed < 400L) {
                                            onCancelVoiceRecord()
                                            Toast.makeText(context, "Hold to record, release to send", Toast.LENGTH_SHORT).show()
                                        } else {
                                            onStopAndSendVoiceRecord()
                                        }
                                    }
                                }
                                .testTag("composer_holding_mic_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Holding to record",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            } else {
                // ==========================================
                // NORMAL TEXT INPUT & ATTACHMENTS INTERFACE
                // ==========================================
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Emojis toggle button on Left
                    IconButton(
                        onClick = { showEmojiQuickBar = !showEmojiQuickBar },
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("composer_emoji_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Mood,
                            contentDescription = "Quick emojis",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Text Input Field in Center
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp, vertical = 8.dp),
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

                Spacer(modifier = Modifier.width(6.dp))

                // Send or Voice Mic action button
                if (text.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .appGradientShadow(CircleShape)
                            .clip(CircleShape)
                            .background(appHorizontalGradient())
                            .clickable { onSendText() }
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
                } else {
                    // Hold to Record Mic Button
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()
                                    val startTime = System.currentTimeMillis()
                                    dragOffsetX = 0f
                                    dragOffsetY = 0f
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onStartVoiceRecord()

                                    var cancelled = false

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change == null || !change.pressed) {
                                            break
                                        }
                                        val delta = change.position - down.position
                                        dragOffsetX = delta.x.coerceAtMost(0f)
                                        dragOffsetY = delta.y.coerceAtMost(0f)

                                        if (dragOffsetY < -140f && !isLockedRecording) {
                                            isLockedRecording = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            break
                                        }

                                        if (dragOffsetX < -180f && !cancelled) {
                                            cancelled = true
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }

                                    val elapsed = System.currentTimeMillis() - startTime
                                    if (isLockedRecording) {
                                        // User locked by sliding up
                                    } else if (cancelled || dragOffsetX < -180f) {
                                        onCancelVoiceRecord()
                                    } else if (elapsed < 400L) {
                                        onCancelVoiceRecord()
                                        Toast.makeText(context, "Hold to record, release to send", Toast.LENGTH_SHORT).show()
                                    } else {
                                        onStopAndSendVoiceRecord()
                                    }
                                }
                            }
                            .testTag("composer_voice_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Hold to record voice note",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
