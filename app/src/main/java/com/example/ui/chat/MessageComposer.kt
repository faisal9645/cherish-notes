package com.example.ui.chat

import com.example.ui.theme.darkTone

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.em
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
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
import com.example.ui.theme.darkSurface
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.PI
import com.example.ui.theme.RoseGoldPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.example.ui.theme.Blue500
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Graphite700
import com.example.ui.theme.Graphite850
import com.example.ui.theme.Gray200
import com.example.ui.theme.Gray700
import com.example.ui.theme.Gray800
import com.example.ui.theme.InkBlack
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyRaised
import com.example.ui.theme.Neutral200
import com.example.ui.theme.Neutral300
import com.example.ui.theme.Neutral700
import com.example.ui.theme.Neutral750
import com.example.ui.theme.Neutral800
import com.example.ui.theme.Neutral900
import com.example.ui.theme.Pink500
import com.example.ui.theme.Rose600
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate900
import com.example.ui.components.CherishToast
import com.example.ui.components.pressScaleTarget
import com.example.ui.components.springClickable
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import com.example.ui.theme.steadyDigits

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
    // The 🫂 button (with nothing typed): hold to squeeze, let go to send that tight, see HugSqueeze
    onSendHug: ((level: Float) -> Unit)? = null,
    myPhotoUrl: String? = null,
    myName: String = "Me",
    placeholder: String = "Message your love...",
    isPrivateMode: Boolean = false,
    onEmergencyExit: (() -> Unit)? = null,
    onInputFocused: () -> Unit = {},
    onPickDocument: (() -> Unit)? = null,
    onPickVideo: (() -> Unit)? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    // What I type looks like what I'll send: the chat's text size and font from Settings
    val chatText = LocalChatTextStyle.current

    var showEmojiPanel by remember { mutableStateOf(false) }
    var isLockedRecording by remember { mutableStateOf(false) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var isVideoMode by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val keyboardInsets = WindowInsets.ime.exclude(WindowInsets.navigationBars)
    val keyboardTargetInsets = WindowInsets.imeAnimationTarget.exclude(WindowInsets.navigationBars)
    var keyboardHeightPx by rememberSaveable { mutableIntStateOf(0) }
    val emojiBoardOpen = remember { Animatable(0f) }
    val isEmojiBoardShown by remember { derivedStateOf { showEmojiPanel || emojiBoardOpen.value > 0f } }

    val effectivePlaceholder = if (isPrivateMode) "Reply to ChatGPT" else placeholder

    // The keyboard's full height, taken each time it finishes opening
    LaunchedEffect(Unit) {
        snapshotFlow {
            val now = keyboardInsets.getBottom(density)
            if (now > 0 && now == keyboardTargetInsets.getBottom(density)) now else 0
        }.collect { settled -> if (settled > 0) keyboardHeightPx = settled }
    }

    fun giveWayToKeyboard() {
        showEmojiPanel = false
        if (!isPrivateMode) {
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
    }

    fun openEmojiBoard() {
        onInputFocused()
        if (isPrivateMode) {
            keyboardController?.hide()
            focusManager.clearFocus()
            showEmojiPanel = true
        } else {
            val keyboardNow = keyboardInsets.getBottom(density)
            if (keyboardNow > 0) keyboardHeightPx = keyboardNow
            showEmojiPanel = true
            keyboardController?.hide()
            scope.launch {
                if (keyboardNow > 0) emojiBoardOpen.snapTo(1f)
                else emojiBoardOpen.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
            }
        }
    }

    fun switchToKeyboard() {
        showEmojiPanel = false
        onInputFocused()
        focusRequester.requestFocus()
        keyboardController?.show()
        if (!isPrivateMode) {
            giveWayToKeyboard()
        }
    }

    fun closeEmojiBoard() {
        if (!showEmojiPanel && emojiBoardOpen.value == 0f) return
        showEmojiPanel = false
        if (!isPrivateMode) {
            scope.launch { emojiBoardOpen.animateTo(0f, tween(200, easing = FastOutSlowInEasing)) }
        }
    }

    // Tapping the message box while the panel is open closes the panel (or gives way to keyboard in normal mode)
    LaunchedEffect(Unit) {
        snapshotFlow { keyboardTargetInsets.getBottom(density) > 0 }
            .collect { keyboardComing ->
                if (keyboardComing && (showEmojiPanel || emojiBoardOpen.value > 0f)) {
                    if (isPrivateMode) closeEmojiBoard() else giveWayToKeyboard()
                }
            }
    }

    // Back closes the emoji board first, before anything else on the chat screen
    BackHandler(enabled = isEmojiBoardShown) { closeEmojiBoard() }

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

    // Message Release "Paper Plane" Spring Drag & Micro-Particle Burst Physics
    val sendBounce = remember { Animatable(1f) }
    val sendParticleProgress = remember { Animatable(0f) }
    var activeSendParticles by remember {
        mutableStateOf<List<SendBurstParticle>>(emptyList())
    }
    val waveformImplosion = remember { Animatable(1f) }

    fun triggerSendLaunchPhysics(onComplete: () -> Unit) {
        activeSendParticles = listOf(
            SendBurstParticle(offsetX = -6f, driftX = -10f, size = 3.6f, color = Color(0xFFFF6B95)),
            SendBurstParticle(offsetX = 3f, driftX = 7f, size = 2.8f, color = Color.White),
            SendBurstParticle(offsetX = -2f, driftX = -4f, size = 4.0f, color = Color(0xFF60A5FA)),
            SendBurstParticle(offsetX = 7f, driftX = 11f, size = 3.2f, color = Color(0xFFFFD1DC))
        )
        scope.launch {
            // Send button tap compression (scale: 0.88f)
            sendBounce.animateTo(0.88f, tween(45, easing = FastOutSlowInEasing))
            // On release springs outward (scale: 1.05f -> 1.0f)
            launch {
                sendBounce.animateTo(1.05f, spring(stiffness = Spring.StiffnessMedium))
                sendBounce.animateTo(1.0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            }
            sendParticleProgress.snapTo(0f)
            sendParticleProgress.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
            activeSendParticles = emptyList()
        }
        onComplete()
    }

    fun triggerCancelImplosion(onComplete: () -> Unit) {
        scope.launch {
            waveformImplosion.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            onComplete()
            waveformImplosion.snapTo(1f)
        }
    }

    // Private chat's mic: the phone's voice typing, its words added to the message box
    val voiceTyping = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) onTextChanged(if (text.isBlank()) spoken else text.trimEnd() + " " + spoken)
    }
    fun startVoiceTyping() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        try {
            voiceTyping.launch(intent)
        } catch (_: ActivityNotFoundException) {
            CherishToast.show("Voice typing isn't available on this phone")
        }
    }

    // Pulsing animations, only while a voice note is being recorded (running all the time, they'd
    // rebuild the whole message box every frame for as long as the chat is open)
    val pulseAlpha: Float
    val shimmerOffset: Float
    if (isRecordingVoice) {
        val infiniteTransition = rememberInfiniteTransition(label = "recording_fx")
        val pulse by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulse_alpha"
        )
        val shimmer by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -14f,
            animationSpec = infiniteRepeatable(tween(750, easing = LinearEasing), RepeatMode.Restart),
            label = "shimmer_offset"
        )
        pulseAlpha = pulse
        shimmerOffset = shimmer
    } else {
        pulseAlpha = 0f
        shimmerOffset = 0f
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val isAmoled = com.example.ui.theme.LocalAmoledBlack.current
    val barBg = if (isPrivateMode) {
        if (isDark) Color.Black else Color.White
    } else {
        MaterialTheme.colorScheme.surface
    }
    val pillBg = if (isPrivateMode) {
        if (isAmoled) Neutral900
        else if (isDark) Neutral800
        else Neutral200
    } else {
        if (isDark) darkSurface(NavyCard) else Color.White
    }
    val pillBorder = if (isPrivateMode) {
        if (isAmoled) Neutral800
        else if (isDark) Neutral700
        else Neutral300
    } else {
        if (isDark) darkSurface(NavyRaised, Neutral800) else Gray200
    }

    // Emojis in the message box: the size they'll be in the sent message, the same however many
    // there are (picked or typed), so nothing jumps while typing
    val composerFontSize = chatText.fontSize()
    val privateComposerFontSize = chatText.fontSize(extraSp = 2)
    val emojiVisualTransformation = remember {
        VisualTransformation { original ->
            val raw = original.text
            if (raw.isEmpty()) return@VisualTransformation TransformedText(original, OffsetMapping.Identity)
            val tokens = tokenize(raw)
            if (tokens.none { it.isEmoji }) return@VisualTransformation TransformedText(original, OffsetMapping.Identity)
            val annotated = buildAnnotatedString {
                tokens.forEach { token ->
                    if (token.isEmoji) {
                        withStyle(SpanStyle(fontSize = TEXT_EMOJI_SCALE.em)) {
                            append(token.text)
                        }
                    } else {
                        append(token.text)
                    }
                }
            }
            TransformedText(annotated, OffsetMapping.Identity)
        }
    }

    // Comprehensive emoji categories like WhatsApp / Telegram
    val emojiTabs = remember { listOf("💕", "😊", "👤", "🐾", "🍔", "⚽", "💡", "🏳️") }
    val emojiCategories = remember {
        listOf(
            // 0: Couple / Love & Deep Intimacy (man & woman romance, sweet kisses, cuddles & devotion)
            listOf(
                // Her and him (her on the left, never two boys or two girls), 8 a row, with 🫂 in
                // the third row's sixth place
                "👩🏻‍❤️‍💋‍👨🏻", "👩🏻‍❤️‍👨🏻", "👫🏻", "👸🏻", "👰🏻‍♀️", "💃🏻", "🧚🏻‍♀️", "🧜🏻‍♀️",
                "💇🏻‍♀️", "💆🏻‍♀️", "🧖🏻‍♀️", "👩🏻", "👧🏻", "💋", "😘", "😚",
                "😗", "😙", "🥰", "😍", "👅", "🫂", "👄", "🫦",
                "🫠", "🛌", "🛏️", "🤱🏻", "💆🏻‍♂️", "😴", "💤", "🥱",
                "🥺", "🥹", "🤤", "🥵", "😳", "🫣", "🤭", "🤫",
                "🫶🏻", "🫰🏻", "🫀", "🤲🏻", "🫳🏻", "🫴🏻", "🤝🏻", "👣",
                // Romantic date nights, bath, intimacy & gifts
                "🛁", "🧖🏻", "🧖🏻‍♂️", "🕯️", "🍷", "🥂", "🍾", "🍫", "🍓",
                "🍒", "🧁", "🍯", "🧸", "🏩", "💒", "💍", "💌", "🔐", "🗝️",
                "🔒", "♾️", "🕊️", "🌙", "🪐", "🌟", "✨", "💫", "🪽", "🪶",
                "👑", "🎀", "💄", "🪞", "🪔", "🌹", "🥀", "💐", "🌷", "🌸", "🌺", "🌼", "💅🏻",
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
            // 2: People & Hands (with full fair-skin girl and boy representation)
            listOf(
                "👋🏻", "🤚🏻", "🖐🏻", "✋🏻", "🖖🏻", "🫱🏻", "🫲🏻", "🫳🏻", "🫴🏻", "👌🏻",
                "🤌🏻", "🤏🏻", "✌🏻", "🤞🏻", "🫰🏻", "🤟🏻", "🤘🏻", "🤙🏻", "👈🏻", "👉🏻",
                "👆🏻", "🖕🏻", "👇🏻", "☝🏻", "🫵🏻", "👍🏻", "👎🏻", "✊🏻", "👊🏻", "🤛🏻",
                "🤜🏻", "👏🏻", "🙌🏻", "🫶🏻", "👐🏻", "🤲🏻", "🤝🏻", "🙏🏻", "✍🏻", "💅🏻",
                "🤳🏻", "💪🏻", "🦾", "🦿", "🦵🏻", "🦶🏻", "👂🏻", "🦻🏻", "👃🏻", "🧠",
                "👧🏻", "👩🏻", "👸🏻", "👰🏻‍♀️", "💃🏻", "🧚🏻‍♀️", "🧜🏻‍♀️", "🧕🏻", "👱🏻‍♀️", "👩🏻‍🦰", "👩🏻‍🦱", "👩🏻‍🦳",
                "👶🏻", "🧒🏻", "👦🏻", "🧑🏻", "👱🏻", "👨🏻", "🧔🏻", "🧓🏻", "👵🏻", "👴🏻",
                "💁🏻‍♀️", "💁🏻‍♂️", "🙆🏻‍♀️", "🙆🏻‍♂️", "🙅🏻‍♀️", "🙅🏻‍♂️", "🙋🏻‍♀️", "🙋🏻‍♂️", "🧏🏻‍♀️", "🧏🏻‍♂️",
                "🙇🏻‍♀️", "🙇🏻‍♂️", "🤦🏻‍♀️", "🤦🏻‍♂️", "🤷🏻‍♀️", "🤷🏻‍♂️", "🙎🏻‍♀️", "🙎🏻‍♂️", "🙍🏻‍♀️", "🙍🏻‍♂️",
                "💆🏻‍♀️", "💆🏻‍♂️", "💇🏻‍♀️", "💇🏻‍♂️", "🧖🏻‍♀️", "🧖🏻‍♂️", "👮🏻‍♀️", "👮🏻‍♂️", "🕵🏻‍♀️", "🕵🏻‍♂️",
                "💂🏻‍♀️", "💂🏻‍♂️", "👷🏻‍♀️", "👷🏻‍♂️", "🥷🏻", "🫅🏻", "🤴🏻"
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
                "⛸️", "🥌", "🎿", "⛷🏻", "🏂🏻", "🪂", "🏋🏻", "🤼🏻", "🤸🏻", "🤺",
                "⛹🏻", "🏇🏻", "🧘🏻", "🏄🏻", "🏊🏻", "🤽🏻", "🚣🏻", "🧗🏻", "🚴🏻", "🏆",
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
        ).map { category -> category.map(::withFairSkinTone) }
    }
    val emojiPagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0, pageCount = { emojiCategories.size })

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(barBg)
    ) {
        // Selection Panel (Attachments & Emojis) positioned ABOVE the input bar (PRIVATE MODE ONLY):
        if (isPrivateMode) {
            AnimatedVisibility(
                visible = showEmojiPanel && !isRecordingVoice,
                enter = expandVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                exit = shrinkVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(120))
            ) {
                EmojiAttachmentPanel(
                    isDark = isDark,
                    pillBorder = pillBorder,
                    isPrivateMode = isPrivateMode,
                    emojiTabs = emojiTabs,
                    emojiCategories = emojiCategories,
                    emojiPagerState = emojiPagerState,
                    haptic = haptic,
                    onEmojiPicked = { emoji -> onTextChanged(text + emoji) },
                    onClose = { closeEmojiBoard() },
                    onTakePhoto = {
                        closeEmojiBoard()
                        onTakePhoto()
                    },
                    onPickAttachment = {
                        closeEmojiBoard()
                        onPickAttachment()
                    },
                    onPickDocument = {
                        closeEmojiBoard()
                        onPickDocument?.invoke() ?: onPickAttachment()
                    },
                    onPickVideo = {
                        closeEmojiBoard()
                        onPickVideo?.invoke() ?: onPickAttachment()
                    }
                )
            }
        }

        // WhatsApp / ChatGPT-Style Composer Row:
        // [ Reply Bar ]
        // [ 🙂 | Reply to ChatGPT | 📎 | 📷 | 📹 ]    [ 🎤 / ✈️ ]
        Row(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(start = 12.dp, end = 12.dp, top = if (isPrivateMode) 6.dp else 8.dp, bottom = if (isPrivateMode) 6.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
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
                                .clip(RoundedCornerShape(28.dp))
                                .background(if (isDark) darkSurface(NavyCard) else Color.White)
                                .border(BorderStroke(1.dp, pillBorder), RoundedCornerShape(28.dp))
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
                                imageVector = Icons.Rounded.Delete,
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
                                .background(Rose600, CircleShape)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Timer
                        Text(
                            text = "%d:%02d".format(recordingDurationSec / 60, recordingDurationSec % 60),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp,
                            style = LocalTextStyle.current.steadyDigits
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
                            imageVector = Icons.Rounded.Lock,
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
                            .clip(RoundedCornerShape(28.dp))
                            .background(if (isDark) darkSurface(NavyCard) else Color.White)
                            .border(BorderStroke(1.dp, pillBorder), RoundedCornerShape(28.dp))
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
                                .background(Rose600.copy(alpha = 0.9f + pulseAlpha * 0.1f), CircleShape)
                        )

                        Spacer(modifier = Modifier.width(5.dp))

                        // Timer
                        Text(
                            text = "%d:%02d".format(recordingDurationSec / 60, recordingDurationSec % 60),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            style = LocalTextStyle.current.steadyDigits
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Growing waveform with Implosion Spring on Cancel
                        WaveformView(
                            amplitudes = recordingAmplitudes.takeLast(45),
                            progress = 1f,
                            isRecording = true,
                            activeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            height = 26.dp,
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer {
                                    val imp = waveformImplosion.value
                                    scaleX = imp
                                    scaleY = imp
                                    alpha = imp
                                    translationX = (1f - imp) * (dragOffsetX - 30f)
                                }
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
                                    imageVector = if (isAtTrash) Icons.Rounded.DeleteForever else Icons.Rounded.Delete,
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
                                    fontSize = 11.sp,
                                    fontWeight = if (isNearTrash) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isNearTrash) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            } else {
                                Text(
                                    text = "Release to delete 🗑️",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            } else {
                if (isPrivateMode) {
                    // ChatGPT Text Input Pill (Private mode):
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(pillBg)
                            .border(BorderStroke(1.dp, pillBorder), RoundedCornerShape(28.dp))
                            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // ChatGPT's plain "+" at the start (toggles attachment/emoji panel)
                        IconButton(
                            onClick = {
                                if (showEmojiPanel) closeEmojiBoard() else openEmojiBoard()
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("composer_attach_button")
                        ) {
                            val plusRotation by animateFloatAsState(
                                targetValue = if (showEmojiPanel) 45f else 0f,
                                animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
                                label = "plus_rotation"
                            )
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Attach file or photo",
                                tint = if (isDark) Neutral200 else InkBlack,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer { rotationZ = plusRotation }
                            )
                        }

                        // Center: Auto-expanding text input, vertically centered
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 40.dp)
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            @Suppress("DEPRECATION")
                            val inputTextStyle = TextStyle(
                                fontFamily = chatText.font.family ?: LocalTextStyle.current.fontFamily,
                                color = if (isDark) Neutral200 else InkBlack,
                                fontSize = privateComposerFontSize,
                                lineHeight = privateComposerFontSize * 1.375f,
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )

                            BasicTextField(
                                value = text,
                                onValueChange = onTextChanged,
                                textStyle = inputTextStyle,
                                visualTransformation = emojiVisualTransformation,
                                cursorBrush = SolidColor(if (isDark) Neutral200 else InkBlack),
                                maxLines = 5,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            closeEmojiBoard()
                                            onInputFocused()
                                        }
                                    }
                                    .testTag("composer_text_input"),
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (text.isEmpty()) {
                                            Text(
                                                text = "Reply to ChatGPT",
                                                style = inputTextStyle.copy(color = Color(0xFF8E8E93)),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }

                        // ChatGPT's mic (voice typing) inside pill
                        val canSend = text.isNotBlank()
                        IconButton(
                            onClick = { startVoiceTyping() },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("composer_voice_typing")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = "Voice typing",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else {
                    // Normal Chat Text Input Pill (unchanged from original):
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(pillBg)
                            .border(BorderStroke(1.dp, pillBorder), RoundedCornerShape(28.dp))
                            .padding(start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Far Left: 🙂 Emoji button
                        IconButton(
                            onClick = { if (isEmojiBoardShown) switchToKeyboard() else openEmojiBoard() },
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("composer_emoji_button")
                        ) {
                            Icon(
                                imageVector = if (isEmojiBoardShown) Icons.Rounded.Keyboard else Icons.Rounded.Mood,
                                contentDescription = "Toggle emoji panel",
                                tint = if (isEmojiBoardShown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Center: Auto-expanding text input
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (text.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    color = Slate500,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = text,
                                onValueChange = onTextChanged,
                                textStyle = TextStyle(
                                    fontFamily = chatText.font.family ?: LocalTextStyle.current.fontFamily,
                                    color = if (isDark) Color.White else Slate900,
                                    fontSize = composerFontSize,
                                    lineHeight = composerFontSize * 1.3f
                                ),
                                visualTransformation = emojiVisualTransformation,
                                cursorBrush = SolidColor(DayBluePrimary),
                                maxLines = 5,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { focusState ->
                                        if (focusState.isFocused) {
                                            onInputFocused()
                                        }
                                    }
                                    // A big emoji sent from here flies out of the text
                                    .onGloballyPositioned { ComposerTextAnchor.update(it) }
                                    .testTag("composer_text_input")
                            )
                        }

                        // Right inside pill: 🫂 hold to hug (only with nothing typed, so it never crowds the text)
                        if (onSendHug != null && text.isEmpty()) {
                            HugHoldButton(onSend = onSendHug, modifier = Modifier.testTag("composer_hug_button"))
                        }

                        // Right inside pill: 📎 Attachment button
                        IconButton(
                            onClick = onPickAttachment,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("composer_attach_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AttachFile,
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
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = "Camera",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

            // (private chat keeps its mic and send button inside the pill, like ChatGPT)
            Spacer(modifier = Modifier.width(if (isPrivateMode) 8.dp else 6.dp))

            // Right-Side Action Circle: 🎤 Mic button (transitions to ✈️ Send button)
            // Fixed width so the mic / send button never shifts
            Box(
                modifier = Modifier.width(if (isPrivateMode) 44.dp else 48.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Lock indicator shown while holding Mic
                if (isRecordingVoice && !isLockedRecording) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .offset { IntOffset(0, -64.dp.roundToPx() + (dragOffsetY * 0.3f).roundToInt()) }
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (dragOffsetY < -45f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (dragOffsetY < -45f) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                            contentDescription = "Lock",
                            tint = if (dragOffsetY < -45f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = null,
                            tint = if (dragOffsetY < -45f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Send button morph (small): the mic smoothly turns into a send arrow when you type, with a tiny bounce when you tap send
                AnimatedContent(
                    targetState = when {
                        // Private chat is text only: Send, never the voice or video-note button
                        text.isNotBlank() || isPrivateMode -> "SEND_TEXT"
                        isRecordingVoice && isLockedRecording -> "SEND_VOICE"
                        else -> "MEDIA_RECORD"
                    },
                    transitionSpec = {
                        (scaleIn(initialScale = 0.62f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) +
                         fadeIn(tween(180))) togetherWith
                        (scaleOut(targetScale = 0.62f, animationSpec = tween(120)) +
                         fadeOut(tween(120))) using SizeTransform(clip = false)
                    },
                    label = "right_action_btn"
                ) { state ->
                    when (state) {
                        "SEND_TEXT" -> {
                            val nothingToSend = text.isBlank()
                            val sendBg = if (isPrivateMode) {
                                if (nothingToSend) {
                                    Color(0xFF2563FF) // ChatGPT voice mode vibrant blue button
                                } else {
                                    if (isDark) Color.White else InkBlack
                                }
                            } else null
                            val sendTint = if (isPrivateMode) {
                                if (nothingToSend) {
                                    Color.White
                                } else {
                                    if (isDark) InkBlack else Color.White
                                }
                            } else Color.White

                            // Private chat keeps Send / Voice mode in place
                            val sendAlpha by animateFloatAsState(if (isPrivateMode) 1f else (if (nothingToSend) 0.45f else 1f), label = "send_alpha")
                            Box(
                                modifier = Modifier
                                    .size(if (isPrivateMode) 44.dp else 48.dp)
                                    .graphicsLayer {
                                        scaleX = sendBounce.value
                                        scaleY = sendBounce.value
                                        alpha = sendAlpha
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
                                        if (!nothingToSend) {
                                            triggerSendLaunchPhysics {
                                                onSendText()
                                                closeEmojiBoard()
                                            }
                                        } else if (isPrivateMode) {
                                            startVoiceTyping()
                                        }
                                    }
                                    .testTag("composer_send_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPrivateMode) {
                                        if (nothingToSend) Icons.Rounded.GraphicEq else Icons.Rounded.ArrowUpward
                                    } else Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = if (isPrivateMode && nothingToSend) "Voice mode" else "Send",
                                    tint = sendTint,
                                    modifier = Modifier.size(if (isPrivateMode) 22.dp else 22.dp)
                                )

                                // Microscopic burst of 3-5 glowing particles floating up 20dp with air drag resistance
                                if (activeSendParticles.isNotEmpty() && sendParticleProgress.value > 0f) {
                                    val progress = sendParticleProgress.value
                                    val floatDistancePx = with(density) { 20.dp.toPx() }
                                    androidx.compose.foundation.Canvas(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        activeSendParticles.forEach { particle ->
                                            val currentY = -progress * floatDistancePx * (1f - progress * 0.32f)
                                            val currentX = particle.offsetX + particle.driftX * progress * (1f - progress * 0.25f)
                                            val particleAlpha = (1f - progress * progress).coerceIn(0f, 1f)
                                            val radius = particle.size * (1f - progress * 0.35f)
                                            drawCircle(
                                                color = particle.color.copy(alpha = particleAlpha),
                                                radius = radius,
                                                center = Offset(size.width / 2f + currentX, size.height / 2f + currentY)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        "SEND_VOICE" -> {
                            val sendBg = if (isPrivateMode) {
                                if (isDark) darkTone(Neutral200) else Gray800
                            } else null
                            val sendTint = if (isPrivateMode) {
                                if (isDark) darkTone(Slate900) else Color.White
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
                                        triggerSendLaunchPhysics {
                                            onStopAndSendVoiceRecord()
                                        }
                                    }
                                    .testTag("composer_send_locked_voice_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPrivateMode) Icons.Rounded.ArrowUpward else Icons.AutoMirrored.Rounded.Send,
                                    contentDescription = "Send voice note",
                                    tint = sendTint,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        "MEDIA_RECORD" -> {
                            val recordBg = if (isPrivateMode) {
                                if (isRecordingVoice) {
                                    if (isDark) darkTone(Gray200) else Gray700
                                } else {
                                    if (isDark) darkTone(Graphite700) else Gray200
                                }
                            } else if (isRecordingVoice) Rose600 else null

                            val recordTint = if (isPrivateMode) {
                                if (isRecordingVoice) {
                                    if (isDark) darkTone(Slate900) else Color.White
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            } else Color.White

                            Box(contentAlignment = Alignment.Center) {
                                // Live concentric breathing rings, scaled to the real voice amplitude:
                                // drawn around the button, so they stay round and don't move the bar
                                val ringOuter = if (isPrivateMode) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                val ringInner = if (isPrivateMode) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                                val showRings = isRecordingVoice && !isLockedRecording
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .drawBehind {
                                            if (showRings) {
                                                val liveAmp = (recordingAmplitudes.lastOrNull() ?: 0.15f).coerceIn(0.1f, 1f)
                                                drawCircle(ringOuter, radius = (24f + liveAmp * 21f + pulseAlpha * 7f).dp.toPx())
                                                drawCircle(ringInner, radius = (24f + liveAmp * 11f + pulseAlpha * 4f).dp.toPx())
                                            }
                                        }
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
                                                Modifier.clip(CircleShape).background(recordBg ?: Color.Gray)
                                            } else {
                                                Modifier
                                                    .appGradientShadow(CircleShape)
                                                    .clip(CircleShape)
                                                    .background(appHorizontalGradient())
                                            }
                                        )
                                        .pointerInput(isVideoMode) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                down.consume()
                                                dragOffsetX = 0f
                                                dragOffsetY = 0f

                                                // Wait up to 220ms before starting recording.
                                                // If the finger lifts in that time it's a tap, not a hold.
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
                                                    isVideoMode = !isVideoMode
                                                    return@awaitEachGesture
                                                }

                                                // Confirmed hold
                                                if (isVideoMode) {
                                                    try { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Exception) {}
                                                    onRecordVideoNote()
                                                    return@awaitEachGesture
                                                }

                                                // Confirmed hold for voice note
                                                val startTime = System.currentTimeMillis()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onStartVoiceRecord()

                                                var hasLocked = false
                                                var crossedTrashThreshold = false
                                                // The finger's speed: a quick flick up locks, a quick flick
                                                // left cancels, without sliding all the way
                                                val velocityTracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                                                val flickSpeedPx = 900.dp.toPx()

                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val change = event.changes.firstOrNull { it.id == down.id }
                                                    if (change == null || !change.pressed) break
                                                    change.consume()
                                                    velocityTracker.addPosition(change.uptimeMillis, change.position)

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

                                                    val flickedUp = dragOffsetY < -16f &&
                                                        velocityTracker.calculateVelocity().y <= -flickSpeedPx
                                                    if ((dragOffsetY < -55f || flickedUp) && !hasLocked) {
                                                        hasLocked = true
                                                        isLockedRecording = true
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        break
                                                    }
                                                }

                                                if (!hasLocked && !isLockedRecording) {
                                                    val flickedLeft = dragOffsetX < -20f &&
                                                        velocityTracker.calculateVelocity().x <= -flickSpeedPx
                                                    if (dragOffsetX < -82f || flickedLeft) {
                                                        try {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        } catch (_: Exception) {}
                                                        triggerCancelImplosion {
                                                            onCancelVoiceRecord()
                                                        }
                                                    } else {
                                                        triggerSendLaunchPhysics {
                                                            onStopAndSendVoiceRecord()
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        .testTag(if (isVideoMode) "composer_video_note_action_button" else "composer_voice_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AnimatedContent(
                                        targetState = isVideoMode,
                                        transitionSpec = {
                                            (scaleIn(initialScale = 0.4f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) +
                                             fadeIn(tween(140))) togetherWith
                                            (scaleOut(targetScale = 0.4f, animationSpec = tween(90)) +
                                             fadeOut(tween(90))) using SizeTransform(clip = false)
                                        },
                                        label = "composer_media_icon"
                                    ) { inVideo ->
                                        Icon(
                                            imageVector = if (inVideo) Icons.Rounded.Videocam else Icons.Rounded.Mic,
                                            contentDescription = if (inVideo) "Circular Video Note (tap to switch to Mic, hold to record)" else "Hold to record voice note (tap to switch to Video)",
                                            tint = recordTint,
                                            modifier = Modifier.size(23.dp)
                                        )
                                    }
                                }

                                // Secondary mode badge: Videocam / Mic (tap/shows the alternate mode)
                                if (!isRecordingVoice) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .offset(x = 1.dp, y = 1.dp)
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(Slate900)
                                            .border(1.2.dp, RoseGoldPrimary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AnimatedContent(
                                            targetState = isVideoMode,
                                            transitionSpec = {
                                                (scaleIn(initialScale = 0.4f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)) +
                                                 fadeIn(tween(140))) togetherWith
                                                (scaleOut(targetScale = 0.4f, animationSpec = tween(90)) +
                                                 fadeOut(tween(90))) using SizeTransform(clip = false)
                                            },
                                            label = "composer_media_badge_icon"
                                        ) { inVideo ->
                                            Icon(
                                                imageVector = if (inVideo) Icons.Rounded.Mic else Icons.Rounded.Videocam,
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
        }

        // WhatsApp/Telegram-Style Tabbed Emoji Panel, in the keyboard's place (NORMAL MODE ONLY)
        if (!isPrivateMode && isEmojiBoardShown && !isRecordingVoice) {
            val boardHeightPx = if (keyboardHeightPx > 0) keyboardHeightPx else with(density) { 280.dp.roundToPx() }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val shownPx = (boardHeightPx * emojiBoardOpen.value - keyboardInsets.getBottom(this))
                            .roundToInt()
                            .coerceIn(0, boardHeightPx)
                        val board = measurable.measure(constraints.copy(minHeight = boardHeightPx, maxHeight = boardHeightPx))
                        layout(board.width, shownPx) { board.place(0, 0) }
                    }
            ) {
                Surface(
                    color = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color.White,
                    border = BorderStroke(1.dp, pillBorder),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Category Tab Row (bottom-style like WhatsApp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isDark) darkTone(Graphite850) else Neutral200)
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
                                        fontSize = if (isSelected) 18.sp else 15.sp
                                    )
                                }
                            }

                            // Close button at end
                            IconButton(
                                onClick = { closeEmojiBoard() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
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
                                    val displayEmoji = remember(emoji) { withFairSkinTone(emoji) }
                                    Box(
                                        modifier = Modifier
                                            .pressScaleTarget()
                                            .clip(RoundedCornerShape(8.dp))
                                            .springClickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onTextChanged(text + displayEmoji)
                                            }
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = displayEmoji, fontSize = 24.sp, maxLines = 1, softWrap = false, modifier = Modifier.fitToCell())
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

@Composable
private fun EmojiAttachmentPanel(
    isDark: Boolean,
    pillBorder: Color,
    isPrivateMode: Boolean,
    emojiTabs: List<String>,
    emojiCategories: List<List<String>>,
    emojiPagerState: androidx.compose.foundation.pager.PagerState,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback,
    onEmojiPicked: (String) -> Unit,
    onClose: () -> Unit,
    onTakePhoto: () -> Unit,
    onPickAttachment: () -> Unit,
    onPickDocument: () -> Unit,
    onPickVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    val panelBg = if (isDark) Neutral900 else Color.White
    val scope = rememberCoroutineScope()

    Surface(
        color = panelBg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        border = BorderStroke(1.dp, pillBorder),
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp)
            .padding(horizontal = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Section: Quick Attachment Action Items
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PanelActionItem(
                    icon = Icons.Rounded.CameraAlt,
                    label = "Camera",
                    color = Blue500,
                    isDark = isDark,
                    onClick = onTakePhoto
                )
                PanelActionItem(
                    icon = Icons.Rounded.PhotoLibrary,
                    label = "Photos",
                    color = Emerald500,
                    isDark = isDark,
                    onClick = onPickAttachment
                )
                PanelActionItem(
                    icon = Icons.Rounded.Videocam,
                    label = "Video",
                    color = Pink500,
                    isDark = isDark,
                    onClick = onPickVideo
                )
                PanelActionItem(
                    icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
                    label = "Files",
                    color = Color(0xFF8B5CF6),
                    isDark = isDark,
                    onClick = onPickDocument
                )
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
            )

            // Category Tab Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDark) Neutral900 else Neutral200)
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
                                scope.launch { emojiPagerState.animateScrollToPage(index) }
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tabEmoji,
                            fontSize = if (isSelected) 16.sp else 14.sp
                        )
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Emoji Pager
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
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(currentEmojis, key = { "${pageIndex}_$it" }) { emoji ->
                        val displayEmoji = remember(emoji) { withFairSkinTone(emoji) }
                        Box(
                            modifier = Modifier
                                .pressScaleTarget()
                                .clip(RoundedCornerShape(8.dp))
                                .springClickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onEmojiPicked(displayEmoji)
                                }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = displayEmoji, fontSize = 22.sp, maxLines = 1, softWrap = false, modifier = Modifier.fitToCell())
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shrinks an emoji the phone draws wider than its cell until it fits: a couple the phone can't
 * join into one picture comes out as her, a heart and him side by side, and would be cut off.
 */
private fun Modifier.fitToCell(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
    val width = placeable.width.coerceAtMost(constraints.maxWidth)
    val scale = width.toFloat() / placeable.width.coerceAtLeast(1)
    layout(width, placeable.height) {
        if (scale >= 1f) {
            placeable.place(0, 0)
        } else {
            placeable.placeWithLayer((width - placeable.width) / 2, 0) {
                scaleX = scale
                scaleY = scale
            }
        }
    }
}

@Composable
private fun PanelActionItem(
    icon: ImageVector,
    label: String,
    color: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .pressScaleTarget()
            .clip(RoundedCornerShape(12.dp))
            .springClickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (isDark) Neutral800 else Neutral200),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (isDark) Neutral200 else Gray700
        )
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
    val isAmoled = com.example.ui.theme.LocalAmoledBlack.current
    val accent = if (isPrivateMode) Color(0xFF10A37F) else MaterialTheme.colorScheme.primary
    val cardColor = when {
        isPrivateMode -> if (isAmoled) Neutral900 else if (isDark) Neutral750 else Neutral200
        isDark -> darkSurface(NavyRaised, Neutral900)
        else -> androidx.compose.ui.graphics.lerp(Color.White, accent, 0.07f)
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardColor,
        border = BorderStroke(1.dp, if (isPrivateMode) (if (isAmoled) Neutral800 else if (isDark) Neutral700 else Neutral300) else accent.copy(alpha = if (isDark) 0.35f else 0.22f)),
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
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
                imageVector = Icons.AutoMirrored.Rounded.Reply,
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
                    text = "Replying to ${if (isPrivateMode) (reply.senderName ?: "ChatGPT") else (reply.senderName ?: "Partner")}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPrivateMode) (if (isDark) Neutral200 else InkBlack) else accent,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = reply.text,
                    fontSize = 11.sp,
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
                    Icons.Rounded.Close,
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

/** Private chat's round send button: ChatGPT's blue. */
private val PrivateSendBlue = Color(0xFF2F6BEA)

data class SendBurstParticle(
    val offsetX: Float,
    val driftX: Float,
    val size: Float,
    val color: Color
)
