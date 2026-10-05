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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Message
import com.example.ui.components.WaveformView
import com.example.ui.theme.DarkBluePrimary
import com.example.ui.theme.DayBluePrimary
import com.example.ui.theme.HeartRed
import com.example.ui.theme.appGradientShadow
import com.example.ui.theme.appHorizontalGradient
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    myPhotoUrl: String? = null,
    myName: String = "Me",
    placeholder: String = "Message your love...",
    isPrivateMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var showEmojiPanel by remember { mutableStateOf(false) }
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

    // Pulsing animations for active recording
    val infiniteTransition = rememberInfiniteTransition(label = "recording_fx")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
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
    val barBg = MaterialTheme.colorScheme.surface
    val pillBg = if (isPrivateMode) {
        if (isDark) Color(0xFF212124) else Color(0xFFF3F4F6)
    } else {
        if (isDark) Color(0xFF1E2638) else Color.White
    }
    val pillBorder = if (isPrivateMode) {
        if (isDark) Color(0xFF2E2F33) else Color(0xFFE5E7EB)
    } else {
        if (isDark) Color(0xFF2A364F) else Color(0xFFE2E8F0)
    }

    // Comprehensive emoji categories like WhatsApp / Telegram
    val emojiTabs = remember { listOf("💕", "😊", "👤", "🐾", "🍔", "⚽", "💡", "🏳️") }
    val emojiCategories = remember {
        listOf(
            // 0: Couple / Love (always first)
            listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
                "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "♥️",
                "🥰", "😍", "😘", "😚", "😻", "💋", "💌", "💍", "💐", "🌹",
                "🫶", "🫂", "🤗", "👩‍❤️‍👨", "💑", "👩‍❤️‍💋‍👨", "👫", "🧸", "✨", "💫"
            ),
            // 1: Smileys & Faces  
            listOf(
                "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃",
                "😉", "😊", "😇", "😍", "🤩", "😘", "😗", "😚", "😙", "🥲",
                "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔",
                "🫡", "🤐", "🤨", "😐", "😑", "😶", "🫥", "😏", "😒", "🙄",
                "😬", "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕",
                "🤢", "🤮", "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳", "🥸",
                "😎", "🤓", "🧐", "😕", "🫤", "😟", "🙁", "😮", "😯", "😲",
                "😳", "🥺", "🥹", "😦", "😧", "😨", "😰", "😥", "😢", "😭",
                "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤", "😡",
                "😠", "🤬", "😈", "👿", "💀", "☠️", "💩", "🤡", "👹", "👺",
                "👻", "👽", "👾", "🤖", "😺", "😸", "😹", "😻", "😼", "😽",
                "🙀", "😿", "😾", "🙈", "🙉", "🙊"
            ),
            // 2: People & Hands
            listOf(
                "👋", "🤚", "🖐️", "✋", "🖖", "🫱", "🫲", "🫳", "🫴", "👌",
                "🤌", "🤏", "✌️", "🤞", "🫰", "🤟", "🤘", "🤙", "👈", "👉",
                "👆", "🖕", "👇", "☝️", "🫵", "👍", "👎", "✊", "👊", "🤛",
                "🤜", "👏", "🙌", "🫶", "👐", "🤲", "🤝", "🙏", "✍️", "💅",
                "🤳", "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃", "🧠",
                "👶", "🧒", "👦", "👧", "🧑", "👱", "👨", "🧔", "👩", "🧓",
                "👴", "👵", "🙍", "🙎", "🙅", "🙆", "💁", "🙋", "🧏", "🙇",
                "🤦", "🤷", "👮", "🕵️", "💂", "🥷", "👷", "🫅", "🤴", "👸"
            ),
            // 3: Animals & Nature
            listOf(
                "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐻‍❄️", "🐨",
                "🐯", "🦁", "🐮", "🐷", "🐽", "🐸", "🐵", "🙈", "🙉", "🙊",
                "🐒", "🐔", "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉",
                "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🪱", "🐛", "🦋", "🐌",
                "🐞", "🐜", "🪰", "🪲", "🪳", "🦟", "🦗", "🕷️", "🦂", "🐢",
                "🐍", "🦎", "🦖", "🦕", "🐙", "🦑", "🦐", "🦞", "🦀", "🐡",
                "🐠", "🐟", "🐬", "🐳", "🐋", "🦈", "🪸", "🐊", "🐅", "🐆",
                "🌸", "💮", "🏵️", "🌹", "🥀", "🌺", "🌻", "🌼", "🌷", "🌱",
                "🪴", "🌲", "🌳", "🌴", "🌵", "🌾", "🌿", "☘️", "🍀", "🍁"
            ),
            // 4: Food & Drink
            listOf(
                "🍇", "🍈", "🍉", "🍊", "🍋", "🍌", "🍍", "🥭", "🍎", "🍏",
                "🍐", "🍑", "🍒", "🍓", "🫐", "🥝", "🍅", "🫒", "🥥", "🥑",
                "🍆", "🥔", "🥕", "🌽", "🌶️", "🫑", "🥒", "🥬", "🥦", "🧄",
                "🍞", "🥐", "🥖", "🫓", "🥨", "🥯", "🥞", "🧇", "🧀", "🍖",
                "🍗", "🥩", "🥓", "🍔", "🍟", "🍕", "🌭", "🥪", "🌮", "🌯",
                "🫔", "🥙", "🧆", "🥚", "🍳", "🥘", "🍲", "🫕", "🥣", "🥗",
                "🍿", "🧈", "🧂", "🥫", "🍱", "🍘", "🍙", "🍚", "🍛", "🍜",
                "🍝", "🍠", "🍢", "🍣", "🍤", "🍥", "🥮", "🍡", "🥟", "🥠",
                "☕", "🍵", "🫖", "🍶", "🍾", "🍷", "🍸", "🍹", "🍺", "🥂"
            ),
            // 5: Activities & Sports
            listOf(
                "⚽", "🏀", "🏈", "⚾", "🥎", "🎾", "🏐", "🏉", "🥏", "🎱",
                "🪀", "🏓", "🏸", "🏒", "🏑", "🥍", "🏏", "🪃", "🥅", "⛳",
                "🪁", "🏹", "🎣", "🤿", "🥊", "🥋", "🎽", "🛹", "🛼", "🛷",
                "⛸️", "🥌", "🎿", "⛷️", "🏂", "🪂", "🏋️", "🤼", "🤸", "🤺",
                "⛹️", "🏇", "🧘", "🏄", "🏊", "🤽", "🚣", "🧗", "🚴", "🏆",
                "🥇", "🥈", "🥉", "🏅", "🎖️", "🏵️", "🎗️", "🎫", "🎟️", "🎪",
                "🎭", "🎨", "🎬", "🎤", "🎧", "🎼", "🎹", "🥁", "🪘", "🎷",
                "🎺", "🪗", "🎸", "🎻", "🪕", "🎲", "♟️", "🎯", "🎳", "🎮"
            ),
            // 6: Objects
            listOf(
                "⌚", "📱", "📲", "💻", "⌨️", "🖥️", "🖨️", "🖱️", "🖲️", "🕹️",
                "🗜️", "💽", "💾", "💿", "📀", "📼", "📷", "📸", "📹", "🎥",
                "📽️", "🎞️", "📞", "☎️", "📟", "📠", "📺", "📻", "🎙️", "🎚️",
                "🎛️", "🧭", "⏱️", "⏲️", "⏰", "🕰️", "⌛", "⏳", "📡", "🔋",
                "💡", "🔦", "🕯️", "🪔", "🧯", "🗑️", "🛒", "🚬", "⚰️", "🪦",
                "🔑", "🗝️", "🔐", "🔒", "🔓", "❤️‍🔥", "🪄", "🔮", "🧿", "🪬",
                "🎀", "🎁", "🎈", "🎊", "🎉", "🎎", "🏮", "🎐", "🧧", "✉️"
            ),
            // 7: Symbols & Flags
            listOf(
                "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
                "❤️‍🔥", "❤️‍🩹", "♥️", "💘", "💝", "💖", "💗", "💓", "💞", "💕",
                "❣️", "💟", "☮️", "✝️", "☪️", "🕉️", "☸️", "✡️", "🔯", "🕎",
                "☯️", "☦️", "🛐", "⛎", "♈", "♉", "♊", "♋", "♌", "♍",
                "♎", "♏", "♐", "♑", "♒", "♓", "🆔", "⚛️", "🉑", "☢️",
                "☣️", "📴", "📳", "🈶", "🈚", "🈸", "🈺", "🈷️", "✴️", "🆚",
                "💮", "🉐", "㊙️", "㊗️", "🈴", "🈵", "🈹", "🈲", "🅰️", "🅱️",
                "🆎", "🆑", "🅾️", "🆘", "❌", "⭕", "🛑", "⛔", "📛", "🚫",
                "✅", "☑️", "✔️", "❎", "➕", "➖", "➗", "➰", "➿", "〽️"
            )
        )
    }
    val emojiPagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0, pageCount = { emojiCategories.size })

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(barBg)
    ) {
        // Reply bar preview
        AnimatedVisibility(
            visible = replyingTo != null,
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
        ) {
            if (replyingTo != null) {
                Surface(
                    color = pillBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 66.dp, top = 6.dp, bottom = 0.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(32.dp)
                                .background(if (isPrivateMode) (if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)) else MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Replying to ${replyingTo.senderName ?: "Partner"}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isPrivateMode) (if (isDark) Color(0xFFECECEC) else Color(0xFF111827)) else MaterialTheme.colorScheme.primary
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

        // 23. WhatsApp-Style Composer Row:
        // [ 🙂 | Message your love... | 📎 | 📷 ]    [ 🎤 / ✈️ ]
        Row(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Main Input Pill / Active Recording Bar
            if (isRecordingVoice) {
                // ---- WhatsApp-Style Recording Bar ----
                if (isLockedRecording) {
                    // LOCKED hands-free mode: [ Avatar | 🔴 timer | waveform | 🔒 ] [ 🗑 ]
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(if (isDark) Color(0xFF1E2638) else Color.White)
                            .border(BorderStroke(1.dp, pillBorder), RoundedCornerShape(26.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Trash to cancel
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isLockedRecording = false
                                onCancelVoiceRecord()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Cancel voice note",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Pulsing red dot
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .graphicsLayer { scaleX = 1f + pulseAlpha * 0.3f; scaleY = 1f + pulseAlpha * 0.3f }
                                .background(Color(0xFFE11D48), CircleShape)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Timer
                        Text(
                            text = "%d:%02d".format(recordingDurationSec / 60, recordingDurationSec % 60),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // Live waveform
                        WaveformView(
                            amplitudes = recordingAmplitudes,
                            progress = 1f,
                            activeColor = MaterialTheme.colorScheme.primary,
                            height = 28.dp,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Lock icon
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked recording",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))
                    }
                } else {
                    // HOLD mode: [ Avatar | 🔴 timer | waveform grows | ‹‹ Slide to cancel ]
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(if (isDark) Color(0xFF1E2638) else Color.White)
                            .border(BorderStroke(1.dp, pillBorder), RoundedCornerShape(26.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Removed profile avatar during active recording as per requirements

                        // Pulsing red dot
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .graphicsLayer { scaleX = 1f + pulseAlpha * 0.3f; scaleY = 1f + pulseAlpha * 0.3f }
                                .background(Color(0xFFE11D48).copy(alpha = 0.9f + pulseAlpha * 0.1f), CircleShape)
                        )

                        Spacer(modifier = Modifier.width(5.dp))

                        // Timer
                        Text(
                            text = "%d:%02d".format(recordingDurationSec / 60, recordingDurationSec % 60),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Growing waveform (takes available space)
                        WaveformView(
                            amplitudes = recordingAmplitudes,
                            progress = 1f,
                            activeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            height = 26.dp,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Slide-to-cancel hint or "release to cancel" flash
                        if (dragOffsetX < -80f) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.offset { IntOffset((dragOffsetX * 0.4f).roundToInt(), 0) }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .offset { IntOffset((dragOffsetX * 0.35f).roundToInt(), 0) }
                            ) {
                                // Animated chevrons
                                Text(
                                    text = "‹‹",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f + pulseAlpha * 0.3f),
                                    modifier = Modifier.offset { IntOffset(shimmerOffset.roundToInt(), 0) }
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Cancel",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }
            } else {
                // WhatsApp-Style Text Input Pill:
                // [ 🙂 | Message your love... | 📎 | 📷 ]
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(pillBg)
                        .border(BorderStroke(1.dp, pillBorder), RoundedCornerShape(26.dp))
                        .padding(start = if (isPrivateMode) 14.dp else 4.dp, end = if (isPrivateMode) 10.dp else 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isPrivateMode) {
                        // Far Left: 🙂 Emoji button
                        IconButton(
                            onClick = {
                                if (showEmojiPanel) {
                                    showEmojiPanel = false
                                } else {
                                    keyboardController?.hide()
                                    showEmojiPanel = true
                                }
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("composer_emoji_button")
                        ) {
                            Icon(
                                imageVector = if (showEmojiPanel) Icons.Default.Keyboard else Icons.Outlined.Mood,
                                contentDescription = "Toggle emoji panel",
                                tint = if (showEmojiPanel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Center: Auto-expanding text input
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                text = placeholder,
                                color = if (isPrivateMode) {
                                    if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)
                                } else {
                                    Color(0xFF64748B)
                                },
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = onTextChanged,
                            textStyle = TextStyle(
                                color = if (isPrivateMode) {
                                    if (isDark) Color(0xFFECECEC) else Color(0xFF111827)
                                } else {
                                    if (isDark) Color.White else Color(0xFF0F172A)
                                },
                                fontSize = 14.sp,
                                lineHeight = 18.sp
                            ),
                            cursorBrush = SolidColor(if (isPrivateMode) (if (isDark) Color(0xFFD1D5DB) else Color(0xFF374151)) else DayBluePrimary),
                            maxLines = 5,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("composer_text_input")
                        )
                    }

                    if (!isPrivateMode) {
                        // Right inside pill: 📎 Attachment button
                        IconButton(
                            onClick = onPickAttachment,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("composer_attach_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AttachFile,
                                contentDescription = "Attach file or photo",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Right inside pill: 📷 Real-time Camera button
                        IconButton(
                            onClick = onTakePhoto,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("composer_camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Camera",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Right-Side Action Circle: 🎤 Mic button (transitions to ✈️ Send button)
            Box(contentAlignment = Alignment.BottomCenter) {
                // Lock indicator shown while holding Mic
                if (isRecordingVoice && !isLockedRecording) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .offset { IntOffset(0, -64.dp.roundToPx() + (dragOffsetY * 0.3f).roundToInt()) }
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (dragOffsetY < -45f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (dragOffsetY < -45f) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = "Lock",
                            tint = if (dragOffsetY < -45f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = null,
                            tint = if (dragOffsetY < -45f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                            val sendBg = if (isPrivateMode) {
                                if (isDark) Color(0xFFECECEC) else Color(0xFF1F2937)
                            } else null
                            val sendTint = if (isPrivateMode) {
                                if (isDark) Color(0xFF111827) else Color.White
                            } else Color.White

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .then(
                                        if (isPrivateMode) Modifier.clip(CircleShape).background(sendBg!!)
                                        else Modifier
                                            .appGradientShadow(CircleShape)
                                            .clip(CircleShape)
                                            .background(appHorizontalGradient())
                                    )
                                    .bounceClick {
                                        onSendText()
                                        showEmojiPanel = false
                                    }
                                    .testTag("composer_send_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPrivateMode) Icons.Default.ArrowUpward else Icons.Default.Send,
                                    contentDescription = "Send",
                                    tint = sendTint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        "SEND_VOICE" -> {
                            val sendBg = if (isPrivateMode) {
                                if (isDark) Color(0xFFECECEC) else Color(0xFF1F2937)
                            } else null
                            val sendTint = if (isPrivateMode) {
                                if (isDark) Color(0xFF111827) else Color.White
                            } else Color.White

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .then(
                                        if (isPrivateMode) Modifier.clip(CircleShape).background(sendBg!!)
                                        else Modifier
                                            .appGradientShadow(CircleShape)
                                            .clip(CircleShape)
                                            .background(appHorizontalGradient())
                                    )
                                    .bounceClick {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isLockedRecording = false
                                        onStopAndSendVoiceRecord()
                                    }
                                    .testTag("composer_send_locked_voice_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPrivateMode) Icons.Default.ArrowUpward else Icons.Default.Send,
                                    contentDescription = "Send voice note",
                                    tint = sendTint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        "MIC" -> {
                            val micBg = if (isPrivateMode) {
                                if (isRecordingVoice) {
                                    if (isDark) Color(0xFFE5E7EB) else Color(0xFF374151)
                                } else {
                                    if (isDark) Color(0xFF2E2F33) else Color(0xFFE5E7EB)
                                }
                            } else if (isRecordingVoice) Color(0xFFE11D48) else null
                            val micTint = if (isPrivateMode) {
                                if (isRecordingVoice) {
                                    if (isDark) Color(0xFF111827) else Color.White
                                } else {
                                    if (isDark) Color(0xFFD1D5DB) else Color(0xFF374151)
                                }
                            } else Color.White

                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .then(
                                        if (isRecordingVoice && !isPrivateMode) {
                                            // Pulses but keeps the original pink gradient
                                            Modifier
                                                .graphicsLayer {
                                                    scaleX = 1f + pulseAlpha * 0.1f
                                                    scaleY = 1f + pulseAlpha * 0.1f
                                                }
                                                .appGradientShadow(CircleShape)
                                                .clip(CircleShape)
                                                .background(appHorizontalGradient())
                                        } else if (isPrivateMode) {
                                            Modifier.clip(CircleShape).background(micBg ?: Color.Gray)
                                        } else {
                                            Modifier
                                                .appGradientShadow(CircleShape)
                                                .clip(CircleShape)
                                                .background(appHorizontalGradient())
                                        }
                                    )
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            down.consume()
                                            dragOffsetX = 0f
                                            dragOffsetY = 0f

                                            // Issue 14: Wait up to 220ms before starting recording.
                                            // If the finger lifts in that time it's a tap, not a hold.
                                            // This prevents false starts and the cancel/restart flicker.
                                            var liftedEarly = false
                                            withTimeoutOrNull(220L) {
                                                while (true) {
                                                    val ev = awaitPointerEvent()
                                                    val ch = ev.changes.firstOrNull { it.id == down.id }
                                                    if (ch == null || !ch.pressed) {
                                                        liftedEarly = true
                                                        break
                                                    }
                                                }
                                            }

                                            if (liftedEarly) {
                                                // Quick tap — show hint, do not record
                                                Toast.makeText(context, "Hold to record, release to send", Toast.LENGTH_SHORT).show()
                                                return@awaitEachGesture
                                            }

                                            // Confirmed hold — start recording
                                            val startTime = System.currentTimeMillis()
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onStartVoiceRecord()

                                            var hasLocked = false

                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id }
                                                if (change == null || !change.pressed) break
                                                change.consume()

                                                val delta = change.position - down.position
                                                dragOffsetX = delta.x.coerceIn(-240f, 0f)
                                                dragOffsetY = delta.y.coerceIn(-180f, 0f)

                                                if (dragOffsetY < -55f && !hasLocked) {
                                                    hasLocked = true
                                                    isLockedRecording = true
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    break
                                                }
                                            }

                                            if (!hasLocked && !isLockedRecording) {
                                                if (dragOffsetX < -90f) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    onCancelVoiceRecord()
                                                } else {
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
                                    tint = micTint,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 22. WhatsApp/Telegram-Style Tabbed Emoji Panel
        AnimatedVisibility(
            visible = showEmojiPanel && !isRecordingVoice,
            enter = expandVertically(animationSpec = tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(animationSpec = tween(160)) + fadeOut(tween(160))
        ) {
            Surface(
                color = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, pillBorder),
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Category Tab Row (bottom-style like WhatsApp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isDark) Color(0xFF1A1C22) else Color(0xFFF1F3F5))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        emojiTabs.forEachIndexed { index, tabEmoji ->
                            val isSelected = emojiPagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        scope.launch {
                                            emojiPagerState.animateScrollToPage(index)
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tabEmoji,
                                    fontSize = if (isSelected) 20.sp else 17.sp
                                )
                            }
                        }
                        
                        // Close button at end
                        IconButton(
                            onClick = { showEmojiPanel = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Emoji pager for smooth left/right gliding between categories
                    androidx.compose.foundation.pager.HorizontalPager(
                        state = emojiPagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) { pageIndex ->
                        val currentEmojis = emojiCategories.getOrElse(pageIndex) { emptyList() }
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(8),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(currentEmojis, key = { "${pageIndex}_$it" }) { emoji ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onTextChanged(text + emoji)
                                        }
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = emoji, fontSize = 24.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Modifier.bounceClick(
    scaleDown: Float = 0.88f,
    onClick: () -> Unit
): Modifier {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "bounceClick"
    )
    
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                },
                onTap = { onClick() }
            )
        }
}
