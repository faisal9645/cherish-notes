package com.example.ui.chat

import com.example.ui.theme.darkTone

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Reply
import com.example.data.model.Message
import com.example.ui.components.WaveformView
import com.example.ui.theme.DarkBluePrimary
import com.example.ui.theme.DayBluePrimary
import com.example.ui.theme.HeartRed
import com.example.ui.theme.appGradientShadow
import com.example.ui.theme.appHorizontalGradient
import com.example.ui.theme.darkSurface
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.PI
import com.example.ui.theme.RoseGoldPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageComposer(
    text: String,
    onTextChanged: (String) -> Unit,
    onSendText: () -> Unit,
    replyingTo: Message?,
    isRecordingVoice: Boolean,
    recordingDurationSec: Int,
    recordingAmplitudes: List<Float>,
    onStartVoiceRecord: () -> Unit,
    onStopAndSendVoiceRecord: () -> Unit,
    onCancelVoiceRecord: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickAttachment: () -> Unit,
    onRecordVideoNote: () -> Unit = {},
    // Long-press on send: send this text later (e.g. "Good morning" at 7 AM)
    onScheduleText: (() -> Unit)? = null,
    myPhotoUrl: String? = null,
    myName: String = "Me",
    placeholder: String = "Message your love...",
    isPrivateMode: Boolean = false,
    onEmergencyExit: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var showEmojiPanel by remember { mutableStateOf(false) }
    var isLockedRecording by remember { mutableStateOf(false) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isVideoMode by remember { mutableStateOf(false) }
    var showModeSwitchHint by remember { mutableStateOf(false) }
    var hintSessionId by remember { mutableIntStateOf(0) }

    // Telegram-style hint: briefly display on initial open to inform user of tap-to-switch
    LaunchedEffect(Unit) {
        delay(700L)
        showModeSwitchHint = true
        delay(3200L)
        showModeSwitchHint = false
    }

    // Auto-dismiss tooltip after 2.5 seconds when user toggles
    LaunchedEffect(hintSessionId) {
        if (hintSessionId > 0 && showModeSwitchHint) {
            delay(2500L)
            showModeSwitchHint = false
        }
    }

    // The emoji board and the keyboard take turns in the same space under the message box. The
    // board is as tall as the keyboard, and while one slides away the other fills exactly the
    // space it leaves, so the message box stays put when switching between them.
    val density = LocalDensity.current
    val keyboardInsets = WindowInsets.ime.exclude(WindowInsets.navigationBars)
    val keyboardTargetInsets = WindowInsets.imeAnimationTarget.exclude(WindowInsets.navigationBars)
    var keyboardHeightPx by rememberSaveable { mutableIntStateOf(0) }
    // 0 = board hidden, 1 = board at full height (before subtracting the keyboard)
    val emojiBoardOpen = remember { Animatable(0f) }
    val isEmojiBoardShown by remember { derivedStateOf { showEmojiPanel || emojiBoardOpen.value > 0f } }

    // The keyboard's full height, taken each time it finishes opening
    LaunchedEffect(Unit) {
        snapshotFlow {
            val now = keyboardInsets.getBottom(density)
            if (now > 0 && now == keyboardTargetInsets.getBottom(density)) now else 0
        }.collect { settled -> if (settled > 0) keyboardHeightPx = settled }
    }

    /** The board shrinks away under the rising keyboard, then goes. */
    fun giveWayToKeyboard() {
        showEmojiPanel = false
        scope.launch {
            val keyboardOpened = withTimeoutOrNull(800) {
                snapshotFlow {
                    val now = keyboardInsets.getBottom(density)
                    now > 0 && now == keyboardTargetInsets.getBottom(density)
                }.first { it }
            } != null
            if (showEmojiPanel) return@launch
            if (keyboardOpened) emojiBoardOpen.snapTo(0f) else emojiBoardOpen.animateTo(0f, tween(180))
        }
    }

    fun openEmojiBoard() {
        val keyboardNow = keyboardInsets.getBottom(density)
        if (keyboardNow > 0) keyboardHeightPx = keyboardNow
        showEmojiPanel = true
        keyboardController?.hide()
        scope.launch {
            // Taking over from the keyboard: full height at once, uncovered as the keyboard slides down
            if (keyboardNow > 0) emojiBoardOpen.snapTo(1f)
            else emojiBoardOpen.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
        }
    }

    fun switchToKeyboard() {
        focusRequester.requestFocus()
        keyboardController?.show()
        giveWayToKeyboard()
    }

    fun closeEmojiBoard() {
        if (!showEmojiPanel) return
        showEmojiPanel = false
        scope.launch { emojiBoardOpen.animateTo(0f, tween(200, easing = FastOutSlowInEasing)) }
    }

    // Tapping the message box while the board is open brings the keyboard back in its place
    LaunchedEffect(Unit) {
        snapshotFlow { keyboardTargetInsets.getBottom(density) > 0 }
            .collect { keyboardComing -> if (keyboardComing && showEmojiPanel) giveWayToKeyboard() }
    }

    // Back closes the emoji board first, before anything else on the chat screen
    BackHandler(enabled = showEmojiPanel) { closeEmojiBoard() }

    // When replyingTo message is set (glide/swipe to reply), open keyboard and focus input automatically
    LaunchedEffect(replyingTo) {
        if (replyingTo != null) {
            kotlinx.coroutines.delay(60)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
            if (showEmojiPanel) giveWayToKeyboard()
            kotlinx.coroutines.delay(120)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
    }

    // Reset lock & drag offsets when recording ends
    LaunchedEffect(isRecordingVoice) {
        if (!isRecordingVoice) {
            isLockedRecording = false
            dragOffsetX = 0f
            dragOffsetY = 0f
        } else {
            closeEmojiBoard()
        }
    }

    // Tiny bounce animation when tapping send
    val sendBounce = remember { Animatable(1f) }

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
        if (isDark) darkTone(Color(0xFF212124)) else Color(0xFFF3F4F6)
    } else {
        if (isDark) darkSurface(Color(0xFF1E2638)) else Color.White
    }
    val pillBorder = if (isPrivateMode) {
        if (isDark) darkTone(Color(0xFF2E2F33)) else Color(0xFFE5E7EB)
    } else {
        if (isDark) darkSurface(Color(0xFF2A364F), Color(0xFF262626)) else Color(0xFFE2E8F0)
    }

    // Comprehensive emoji categories like WhatsApp / Telegram
    val emojiTabs = remember { listOf("💕", "😊", "👤", "🐾", "🍔", "⚽", "💡", "🏳️") }
    val emojiCategories = remember {
        listOf(
            // 0: Couple / Love & Deep Intimacy (lying on chest, forehead kiss, bed cuddles, kisses & romance)
            listOf(
                // Forehead kisses, tender kisses, lying on chest, deep cuddles, tongue
                "👅", "😚", "🫂", "🛌", "🛏️", "💋", "😘", "😙", "😗", "💏", "👩‍❤️‍💋‍👨",
                "🧑‍❤️‍💋‍🧑", "👩‍❤️‍💋‍👩", "👨‍❤️‍💋‍👨", "👄", "🫦", "🫠", "🥰", "😍", "🤱", "💆",
                "💆‍♂️", "💆‍♀️", "😴", "💤", "🥱", "🥺", "🥹", "🤤", "🥵", "😳",
                "🫣", "🤭", "🤫", "🫶🏻", "🫰🏻", "🫀", "🤲🏻", "🫳🏻", "🫴🏻", "🤝🏻",
                "👫", "🧑‍🤝‍🧑", "💑", "👩‍❤️‍👨", "🧑‍❤️‍🧑", "👩‍❤️‍👩", "👨‍❤️‍👨", "👭", "👬", "👣",
                // Romantic date nights, bath, intimacy & gifts
                "🛁", "🧖", "🧖‍♀️", "🧖‍♂️", "🕯️", "🍷", "🥂", "🍾", "🍫", "🍓",
                "🍒", "🧁", "🍯", "🧸", "🏩", "💒", "💍", "💌", "🔐", "🗝️",
                "🔒", "♾️", "🕊️", "🌙", "🪐", "🌟", "✨", "💫", "🪽", "🪶",
                "🎀", "🪞", "🪔", "🌹", "🥀", "💐", "🌷", "🌸", "🌺", "🌼",
                // Passionate hearts & love symbols
                "❤️‍🔥", "❤️‍🩹", "❤️", "🩷", "💖", "💗", "💓", "💞", "💕", "💘",
                "💝", "❣️", "💟", "♥️", "🤍", "💜", "🩵", "💙", "💚", "💛",
                "🧡", "🤎", "🖤", "🔥", "😻", "😽", "🙈", "🙉"
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
                "👋🏻", "🤚🏻", "🖐🏻", "✋🏻", "🖖🏻", "🫱🏻", "🫲🏻", "🫳🏻", "🫴🏻", "👌🏻",
                "🤌🏻", "🤏🏻", "✌🏻", "🤞🏻", "🫰🏻", "🤟🏻", "🤘🏻", "🤙🏻", "👈🏻", "👉🏻",
                "👆🏻", "🖕🏻", "👇🏻", "☝🏻", "🫵🏻", "👍🏻", "👎🏻", "✊🏻", "👊🏻", "🤛🏻",
                "🤜🏻", "👏🏻", "🙌🏻", "🫶🏻", "👐🏻", "🤲🏻", "🤝🏻", "🙏🏻", "✍🏻", "💅🏻",
                "🤳🏻", "💪🏻", "🦾", "🦿", "🦵🏻", "🦶🏻", "👂🏻", "🦻🏻", "👃🏻", "🧠",
                "👶🏻", "🧒🏻", "👦🏻", "👧🏻", "🧑🏻", "👱🏻", "👨🏻", "🧔🏻", "👩🏻", "🧓🏻",
                "👴🏻", "👵🏻", "🙍🏻", "🙎🏻", "🙅🏻", "🙆🏻", "💁🏻", "🙋🏻", "🧏🏻", "🙇🏻",
                "🤦🏻", "🤷🏻", "👮🏻", "🕵🏻", "💂🏻", "🥷🏻", "👷🏻", "🫅🏻", "🤴🏻", "👸🏻"
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
        // 23. WhatsApp-Style Composer Row:
        // [ Reply Bar ]
        // [ 🙂 | Message your love... | 📎 | 📷 | 📹 ]    [ 🎤 / ✈️ ]
        Row(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                // Main Input Pill / Active Recording Bar
                if (isRecordingVoice) {
                    // ---- WhatsApp-Style Recording Bar ----
                    if (isLockedRecording) {
                        // LOCKED hands-free mode: [ Avatar | 🔴 timer | waveform | 🔒 ] [ 🗑 ]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(26.dp))
                                .background(if (isDark) darkSurface(Color(0xFF1E2638)) else Color.White)
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

                        // Profile icon on the left side during recording
                        com.example.ui.components.AvatarView(
                            photoUrl = myPhotoUrl,
                            name = myName,
                            size = 28.dp,
                            isOnline = false,
                            showOnlineBadge = false
                        )

                        Spacer(modifier = Modifier.width(6.dp))

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
                            amplitudes = recordingAmplitudes.takeLast(45),
                            progress = 1f,
                            isRecording = true,
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
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(if (isDark) darkSurface(Color(0xFF1E2638)) else Color.White)
                            .border(BorderStroke(1.dp, pillBorder), RoundedCornerShape(26.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Profile icon on the left side during recording
                        com.example.ui.components.AvatarView(
                            photoUrl = myPhotoUrl,
                            name = myName,
                            size = 28.dp,
                            isOnline = false,
                            showOnlineBadge = false
                        )

                        Spacer(modifier = Modifier.width(6.dp))

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
                            amplitudes = recordingAmplitudes.takeLast(45),
                            progress = 1f,
                            isRecording = true,
                            activeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            height = 26.dp,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Slide-to-cancel with Rubber-band Magnetism & Opening Trash Can Lid
                        val isNearTrash = dragOffsetX < -55f
                        val isAtTrash = dragOffsetX < -82f
                        val trashLidAngle = if (isNearTrash) {
                            ((-dragOffsetX - 55f) / 30f).coerceIn(0f, 1f) * -35f
                        } else 0f
                        val trashMagneticPull = if (isNearTrash) {
                            (dragOffsetX * 0.28f)
                        } else 0f

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .offset { IntOffset(trashMagneticPull.roundToInt(), 0) }
                        ) {
                            // Animated Trash Can with magnetic tilt & pop-open lid
                            Box(
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .graphicsLayer {
                                        if (isAtTrash) {
                                            rotationZ = sin(pulseAlpha * PI.toFloat() * 2f) * 6f
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isAtTrash) Icons.Default.DeleteForever else Icons.Default.Delete,
                                    contentDescription = "Cancel recording",
                                    tint = if (isAtTrash) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(17.dp)
                                        .graphicsLayer {
                                            rotationZ = trashLidAngle
                                        }
                                )
                            }

                            if (!isAtTrash) {
                                // Animated rubber-band chevrons
                                Text(
                                    text = "‹‹",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isNearTrash) MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f + pulseAlpha * 0.3f),
                                    modifier = Modifier.offset { IntOffset(shimmerOffset.roundToInt(), 0) }
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (isNearTrash) "Release to cancel" else "Slide to cancel",
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isNearTrash) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isNearTrash) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            } else {
                                Text(
                                    text = "Release to delete 🗑️",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
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
                        .fillMaxWidth()
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
                            onClick = { if (showEmojiPanel) switchToKeyboard() else openEmojiBoard() },
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
                                    if (isDark) darkTone(Color(0xFF9CA3AF)) else Color(0xFF6B7280)
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
                                // The app's typeface, like the messages
                                fontFamily = LocalTextStyle.current.fontFamily,
                                color = if (isPrivateMode) {
                                    if (isDark) darkTone(Color(0xFFECECEC)) else Color(0xFF111827)
                                } else {
                                    if (isDark) Color.White else Color(0xFF0F172A)
                                },
                                fontSize = 14.sp,
                                lineHeight = 18.sp
                            ),
                            cursorBrush = SolidColor(if (isPrivateMode) (if (isDark) darkTone(Color(0xFFD1D5DB)) else Color(0xFF374151)) else DayBluePrimary),
                            maxLines = 5,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
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
        }

            Spacer(modifier = Modifier.width(6.dp))

            // Right-Side Action Circle: 🎤 Mic button (transitions to ✈️ Send button)
            // Fixed 48.dp width prevents the voice/mic icon from jumping left when the wider tooltip appears!
            Box(
                modifier = Modifier.width(48.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
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

                // Send button morph (small): the mic smoothly turns into a send arrow when you type, with a tiny bounce when you tap send
                AnimatedContent(
                    targetState = when {
                        text.isNotBlank() -> "SEND_TEXT"
                        isRecordingVoice && isLockedRecording -> "SEND_VOICE"
                        isVideoMode -> "VIDEO_NOTE"
                        else -> "MIC"
                    },
                    transitionSpec = {
                        (scaleIn(initialScale = 0.62f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) +
                         fadeIn(tween(180))) togetherWith
                        (scaleOut(targetScale = 0.62f, animationSpec = tween(120)) +
                         fadeOut(tween(120)))
                    },
                    label = "right_action_btn"
                ) { state ->
                    when (state) {
                        "SEND_TEXT" -> {
                            val sendBg = if (isPrivateMode) {
                                if (isDark) darkTone(Color(0xFFECECEC)) else Color(0xFF1F2937)
                            } else null
                            val sendTint = if (isPrivateMode) {
                                if (isDark) darkTone(Color(0xFF111827)) else Color.White
                            } else Color.White

                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .graphicsLayer {
                                        scaleX = sendBounce.value
                                        scaleY = sendBounce.value
                                    }
                                    .then(
                                        if (isPrivateMode) Modifier.clip(CircleShape).background(sendBg!!)
                                        else Modifier
                                            .appGradientShadow(CircleShape)
                                            .clip(CircleShape)
                                            .background(appHorizontalGradient())
                                    )
                                    .bounceClick(
                                        onLongClick = onScheduleText?.let { schedule ->
                                            {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                schedule()
                                            }
                                        }
                                    ) {
                                        scope.launch {
                                            sendBounce.animateTo(0.78f, tween(65, easing = FastOutSlowInEasing))
                                            sendBounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                                        }
                                        onSendText()
                                        closeEmojiBoard()
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
                                if (isDark) darkTone(Color(0xFFECECEC)) else Color(0xFF1F2937)
                            } else null
                            val sendTint = if (isPrivateMode) {
                                if (isDark) darkTone(Color(0xFF111827)) else Color.White
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
                        "VIDEO_NOTE" -> {
                            val videoBg = if (isPrivateMode) {
                                if (isDark) darkTone(Color(0xFF2E2F33)) else Color(0xFFE5E7EB)
                            } else null
                            val videoTint = if (isPrivateMode) {
                                if (isDark) darkTone(Color(0xFFD1D5DB)) else Color(0xFF374151)
                            } else Color.White

                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .then(
                                            if (isPrivateMode) Modifier.clip(CircleShape).background(videoBg ?: Color.Gray)
                                            else Modifier
                                                .appGradientShadow(CircleShape)
                                                .clip(CircleShape)
                                                .background(appHorizontalGradient())
                                        )
                                        .pointerInput(Unit) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                down.consume()
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
                                                    try { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
                                                    isVideoMode = false
                                                    showModeSwitchHint = true
                                                    hintSessionId++
                                                    return@awaitEachGesture
                                                }

                                                // Confirmed hold: record circular video note
                                                try { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Exception) {}
                                                onRecordVideoNote()
                                            }
                                        }
                                        .testTag("composer_video_note_action_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Videocam,
                                        contentDescription = "Circular Video Note (tap to switch to Mic, hold to record)",
                                        tint = videoTint,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }

                                // Secondary mode badge: Mic (tap switches to audio note)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .offset(x = 1.dp, y = 1.dp)
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0F172A))
                                        .border(1.2.dp, RoseGoldPrimary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                        "MIC" -> {
                            val micBg = if (isPrivateMode) {
                                if (isRecordingVoice) {
                                    if (isDark) darkTone(Color(0xFFE5E7EB)) else Color(0xFF374151)
                                } else {
                                    if (isDark) darkTone(Color(0xFF2E2F33)) else Color(0xFFE5E7EB)
                                }
                            } else if (isRecordingVoice) Color(0xFFE11D48) else null
                            val micTint = if (isPrivateMode) {
                                if (isRecordingVoice) {
                                    if (isDark) darkTone(Color(0xFF111827)) else Color.White
                                } else {
                                    if (isDark) darkTone(Color(0xFFD1D5DB)) else Color(0xFF374151)
                                }
                            } else Color.White

                            Box(contentAlignment = Alignment.Center) {
                                // Live Concentric Breathing Rings scaled to real voice amplitude
                                if (isRecordingVoice && !isLockedRecording) {
                                    val liveAmp = (recordingAmplitudes.lastOrNull() ?: 0.15f).coerceIn(0.1f, 1f)
                                    val outerRingDp = (52 + liveAmp * 42f + pulseAlpha * 14f).dp
                                    val innerRingDp = (52 + liveAmp * 22f + pulseAlpha * 8f).dp
                                    Box(
                                        modifier = Modifier
                                            .size(outerRingDp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isPrivateMode) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                            )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(innerRingDp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isPrivateMode) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
                                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                                            )
                                    )
                                }

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
                                                    try { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) } catch (_: Exception) {}
                                                    isVideoMode = true
                                                    showModeSwitchHint = true
                                                    hintSessionId++
                                                    return@awaitEachGesture
                                                }

                                                // Confirmed hold — start recording
                                                val startTime = System.currentTimeMillis()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onStartVoiceRecord()

                                                var hasLocked = false
                                                var crossedTrashThreshold = false

                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == down.id }
                                                    if (change == null || !change.pressed) break
                                                    change.consume()

                                                    val delta = change.position - down.position
                                                    val rawDx = delta.x.coerceAtMost(0f)
                                                    // Progressive rubber-band non-linear damping
                                                    val dampedDx = if (rawDx >= -70f) {
                                                        rawDx
                                                    } else {
                                                        val excess = -rawDx - 70f
                                                        -70f - (160f * (excess / (excess + 80f)))
                                                    }
                                                    dragOffsetX = dampedDx
                                                    dragOffsetY = delta.y.coerceIn(-180f, 0f)

                                                    if (dragOffsetX < -82f && !crossedTrashThreshold) {
                                                        crossedTrashThreshold = true
                                                        try { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Exception) {}
                                                    } else if (dragOffsetX >= -82f) {
                                                        crossedTrashThreshold = false
                                                    }

                                                    if (dragOffsetY < -55f && !hasLocked) {
                                                        hasLocked = true
                                                        isLockedRecording = true
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        break
                                                    }
                                                }

                                                if (!hasLocked && !isLockedRecording) {
                                                    if (dragOffsetX < -82f) {
                                                        try {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        } catch (_: Exception) {}
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

                                // Secondary mode badge: Videocam (tap switches to video note)
                                if (!isRecordingVoice) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .offset(x = 1.dp, y = 1.dp)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0F172A))
                                            .border(1.2.dp, RoseGoldPrimary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Videocam,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 22. WhatsApp/Telegram-Style Tabbed Emoji Panel, in the keyboard's place
        if (isEmojiBoardShown && !isRecordingVoice) {
            val boardHeightPx = if (keyboardHeightPx > 0) keyboardHeightPx else with(density) { 280.dp.roundToPx() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        // Only the part the keyboard isn't covering takes space; read here, so the
                        // keyboard's slide moves the board without recomposing the composer
                        val shownPx = (boardHeightPx * emojiBoardOpen.value - keyboardInsets.getBottom(this))
                            .roundToInt()
                            .coerceIn(0, boardHeightPx)
                        val board = measurable.measure(constraints.copy(minHeight = boardHeightPx, maxHeight = boardHeightPx))
                        layout(board.width, shownPx) { board.place(0, 0) }
                    }
            ) {
            Surface(
                color = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFF8FAFC),
                border = BorderStroke(1.dp, pillBorder),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Category Tab Row (bottom-style like WhatsApp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isDark) darkTone(Color(0xFF1A1C22)) else Color(0xFFF1F3F5))
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
                            onClick = { closeEmojiBoard() },
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
                                    Text(text = emoji, fontSize = 34.sp)
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

/**
 * The message being replied to, as its own card: tinted and lifted (shadow) so it reads as
 * separate from the message box it floats above. The close button is a full-size touch target.
 */
@Composable
fun ReplyPreviewCard(
    reply: Message,
    isPrivateMode: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val accent = if (isPrivateMode) (if (isDark) darkTone(Color(0xFF9CA3AF)) else Color(0xFF6B7280)) else MaterialTheme.colorScheme.primary
    val cardColor = when {
        isPrivateMode -> if (isDark) darkTone(Color(0xFF26272B)) else Color(0xFFF3F4F6)
        isDark -> darkSurface(Color(0xFF263049), Color(0xFF181818))
        else -> androidx.compose.ui.graphics.lerp(Color.White, accent, 0.07f)
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardColor,
        border = BorderStroke(1.dp, accent.copy(alpha = if (isDark) 0.35f else 0.22f)),
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(26.dp)
                    .background(accent, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Reply,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Replying to ${reply.senderName ?: "Partner"}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPrivateMode) (if (isDark) darkTone(Color(0xFFECECEC)) else Color(0xFF111827)) else accent,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = reply.text,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("composer_cancel_reply")
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

@Composable
fun Modifier.bounceClick(
    scaleDown: Float = 0.88f,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
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
        .pointerInput(onLongClick != null) {
            detectTapGestures(
                onPress = {
                    isPressed = true
                    tryAwaitRelease()
                    isPressed = false
                },
                onLongPress = if (onLongClick != null) { _ -> currentOnLongClick?.invoke() } else null,
                onTap = { currentOnClick() }
            )
        }
}
