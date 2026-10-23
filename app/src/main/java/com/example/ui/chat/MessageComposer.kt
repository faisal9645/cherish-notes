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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import com.example.ui.theme.DayBluePrimary
import com.example.ui.theme.HeartRed
import com.example.ui.theme.appGradientShadow
import com.example.ui.theme.notesHorizontalGradient
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

@Composable
private fun RecordingTimer(seconds: State<Int>, fontSize: androidx.compose.ui.unit.TextUnit) {
    val value = seconds.value
    Text(
        text = "%d:%02d".format(value / 60, value % 60),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = fontSize,
        style = LocalTextStyle.current.steadyDigits
    )
}

@Composable
private fun RecordingWaveform(
    amplitudes: State<List<Float>>,
    activeColor: Color,
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    WaveformView(
        amplitudes = amplitudes.value.takeLast(45),
        progress = 1f,
        isRecording = true,
        activeColor = activeColor,
        height = height,
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MessageComposer(
    text: String,
    onTextChanged: (String) -> Unit,
    onSendText: () -> Unit,
    replyingTo: Message?,
    isRecordingVoice: Boolean,
    recordingDurationSec: State<Int>,
    recordingAmplitudes: State<List<Float>>,
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
    // A GIF picked from GIPHY's search (the emoji board's GIF tab); null hides the tab
    onSendGif: ((GiphyGif) -> Unit)? = null,
    // A Sticker picked from the Stickers tab; sends immediately like WhatsApp/Telegram
    onSendSticker: ((String) -> Unit)? = null,
    // Holding the camera button: record a video with the phone's camera (a tap takes a photo)
    onRecordVideo: (() -> Unit)? = null,
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
    var showGifPicker by remember { mutableStateOf(false) }
    var activeMediaTab by rememberSaveable { mutableIntStateOf(0) }

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
    val pulseAlpha: State<Float>
    val shimmerOffset: State<Float>
    if (isRecordingVoice && com.example.ui.components.rememberAppIsResumed()) {
        val infiniteTransition = rememberInfiniteTransition(label = "recording_fx")
        val pulse = infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(550, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulse_alpha"
        )
        val shimmer = infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -14f,
            animationSpec = infiniteRepeatable(tween(750, easing = LinearEasing), RepeatMode.Restart),
            label = "shimmer_offset"
        )
        pulseAlpha = pulse
        shimmerOffset = shimmer
    } else {
        pulseAlpha = remember { mutableFloatStateOf(0f) }
        shimmerOffset = remember { mutableFloatStateOf(0f) }
    }

    // Day or dark as the app's theme says (not the phone's), so Day stays white on a dark-mode phone
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val isAmoled = com.example.ui.theme.LocalAmoledBlack.current
    val barBg = if (isPrivateMode) {
        if (isDark) Color.Black else Color.White
    } else {
        if (isDark) Color.Transparent else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    }
    val pillBg = if (isPrivateMode) {
        if (isAmoled) Neutral900
        else if (isDark) Neutral800
        else Neutral200
    } else {
        // Dark: see-through, so the wallpaper shines through; Day: plain white as before
        if (isDark) Color.Transparent else Color.White
    }
    val pillBorder = if (isPrivateMode) {
        if (isAmoled) Neutral800
        else if (isDark) Neutral700
        else Neutral300
    } else {
        if (isDark) Color(0xFF526B96).copy(alpha = 0.30f) else Gray200
    }
    val typingFill = remember(isDark, pillBg) {
        if (isDark) Brush.verticalGradient(
            0f to Color(0xFF263653).copy(alpha = 0.78f),
            0.45f to Color(0xFF17243B).copy(alpha = 0.64f),
            1f to Color(0xFF111B2E).copy(alpha = 0.72f)
        ) else SolidColor(pillBg)
    }
    // The message box's shape: Day keeps its original rounder, slimmer pill
    val pillShape = RoundedCornerShape(if (isDark) 24.dp else 28.dp)
    val composerSidePadding = if (isDark) 16.dp else 12.dp
    val composerIconTint = if (isDark) Color(0xFFA9C5ED) else MaterialTheme.colorScheme.onSurfaceVariant

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
                // A boy (🧑, with his hair) and a girl, him kissing her first, never two boys or two
                // girls; 8 a row, with 🫂 in the third row's sixth place
                "🧑🏻‍❤️‍💋‍👩🏻", "🧑🏻‍❤️‍👩🏻", "👩🏻‍❤️‍💋‍🧑🏻", "👩🏻‍❤️‍🧑🏻", "👫🏻", "👸🏻", "👰🏻‍♀️", "💃🏻",
                "🧚🏻‍♀️", "🧜🏻‍♀️", "💇🏻‍♀️", "💆🏻‍♀️", "🧖🏻‍♀️", "👩🏻", "👧🏻", "💋",
                "😘", "😚", "😗", "😙", "🥰", "🫂", "😍", "👅",
                "👄", "🫦", "🫠", "🛌", "🛏️", "🤱🏻", "😴", "💤",
                "🥱", "🥺", "🥹", "🤤", "🥵", "😳", "🫣", "🤭",
                "🤫", "🫶🏻", "🫰🏻", "🫀", "🤲🏻", "🫳🏻", "🫴🏻", "🤝🏻",
                "👣",
                // Romantic date nights, bath, intimacy & gifts
                "🛁", "🧖🏻", "🕯️", "🍷", "🥂", "🍾", "🍫", "🍓",
                "🍒", "🧁", "🍯", "🧸", "🏩", "💒", "💍", "💌", "🔐", "🗝️",
                "🔒", "♾️", "🕊️", "🌙", "🪐", "🌟", "✨", "💫", "🪽", "🪶",
                "👑", "🎀", "💄", "🪞", "🪔", "🌹", "🥀", "💐", "🌷", "🌸", "🌺", "🌼", "💅🏻",
                // Passionate hearts & love symbols
                "❤️‍🔥", "❤️‍🩹", "❤️", "🩷", "💖", "💗", "💓", "💞", "💕", "💘",
                "💝", "❣️", "💟", "♥️", "🤍", "💜", "🩵", "💙", "💚", "💛",
                "🧡", "🤎", "🖤", "🔥", "😻", "😽"
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
                // The styled-hair boy (🧑) and girls only: no man, boy, baby or old man, who look
                // bald on some phones
                "🧑🏻",
                "💁🏻‍♀️", "🙆🏻‍♀️", "🙅🏻‍♀️", "🙋🏻‍♀️", "🧏🏻‍♀️", "🙇🏻‍♀️", "🤦🏻‍♀️", "🤷🏻‍♀️", "🙎🏻‍♀️", "🙍🏻‍♀️",
                "💆🏻‍♀️", "💇🏻‍♀️", "🧖🏻‍♀️", "👮🏻‍♀️", "🕵🏻‍♀️", "💂🏻‍♀️", "👷🏻‍♀️"
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
                "⛸️", "🥌", "🎿", "⛷🏻", "🏂🏻", "🪂", "🏋🏻", "🤸🏻", "🤺",
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
                .padding(start = composerSidePadding, end = composerSidePadding, top = if (isPrivateMode) 6.dp else 8.dp, bottom = if (isPrivateMode) 6.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                // Main Input Pill / Active Recording Bar (each its own compiled part, see ComposerPart)
                if (isRecordingVoice) ComposerPart {
                    // ---- WhatsApp-Style Recording Bar ----
                    if (isLockedRecording) {
                        // LOCKED hands-free mode: [ Avatar | 🔴 timer | waveform | 🔒 ] [ 🗑 ]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = if (isDark) 48.dp else 52.dp)
                                .clip(pillShape)
                                .background(if (isDark) pillBg else Color.White)
                                .border(BorderStroke(1.dp, pillBorder), pillShape)
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
                                .graphicsLayer { scaleX = 1f + pulseAlpha.value * 0.3f; scaleY = 1f + pulseAlpha.value * 0.3f }
                                .background(Rose600, CircleShape)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Timer
                        RecordingTimer(recordingDurationSec, 14.sp)

                        Spacer(modifier = Modifier.width(8.dp))

                        // Live waveform
                        RecordingWaveform(
                            amplitudes = recordingAmplitudes,
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
                                .graphicsLayer { scaleX = 1f + pulseAlpha.value * 0.3f; scaleY = 1f + pulseAlpha.value * 0.3f }
                                .background(Rose600, CircleShape)
                        )

                        Spacer(modifier = Modifier.width(5.dp))

                        // Timer
                        RecordingTimer(recordingDurationSec, 13.sp)

                        Spacer(modifier = Modifier.width(6.dp))

                        // Growing waveform with Implosion Spring on Cancel
                        RecordingWaveform(
                            amplitudes = recordingAmplitudes,
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
                                            rotationZ = sin(pulseAlpha.value * PI.toFloat() * 2f) * 6f
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
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.offset { IntOffset(shimmerOffset.value.roundToInt(), 0) }
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
            } else ComposerPart {
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
                            .heightIn(min = if (isDark) 48.dp else 44.dp)
                            .clip(pillShape)
                            .background(typingFill)
                            .border(BorderStroke(1.dp, pillBorder), pillShape)
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
                                tint = if (isEmojiBoardShown) MaterialTheme.colorScheme.primary else composerIconTint,
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
                                    color = if (isDark) Color(0xFFA1AEC4) else Slate500,
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
                                cursorBrush = SolidColor(if (isDark) Color(0xFFA9C5ED) else DayBluePrimary),
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
                                tint = composerIconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Right inside pill: 📷 Real-time Camera button (tap: a photo; hold: a video)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = onTakePhoto,
                                    // (combinedClickable buzzes on the long press itself)
                                    onLongClick = onRecordVideo,
                                    onLongClickLabel = if (onRecordVideo != null) "Record a video" else null
                                )
                                .testTag("composer_camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = "Camera",
                                tint = composerIconTint,
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
                // Lock indicator shown while holding Mic: magnetic, it's drawn to the thumb faster the
                // closer the thumb comes, growing a little; at the lock it snaps shut (the closed lock
                // pops and fades with the haptic)
                androidx.compose.animation.AnimatedVisibility(
                    visible = isRecordingVoice && !isLockedRecording,
                    enter = fadeIn(tween(120)) + scaleIn(spring(dampingRatio = 0.55f, stiffness = 600f), initialScale = 0.6f),
                    exit = scaleOut(tween(200), targetScale = 1.3f) + fadeOut(tween(200))
                ) {
                    val nearLock = dragOffsetY < -45f && !isLockedRecording
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .offset {
                                val pull = (-dragOffsetY / 55f).coerceIn(0f, 1f)
                                IntOffset(0, -64.dp.roundToPx() + (dragOffsetY * (0.3f + 0.5f * pull * pull)).roundToInt())
                            }
                            .graphicsLayer {
                                val pull = (-dragOffsetY / 55f).coerceIn(0f, 1f)
                                val grow = 1f + 0.14f * pull * pull
                                scaleX = grow
                                scaleY = grow
                            }
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (nearLock || isLockedRecording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (nearLock) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                            contentDescription = "Lock",
                            tint = if (nearLock || isLockedRecording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = null,
                            tint = if (nearLock || isLockedRecording) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                                            .background(notesHorizontalGradient())
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
                                            .background(notesHorizontalGradient())
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
                                                val liveAmp = (recordingAmplitudes.value.lastOrNull() ?: 0.15f).coerceIn(0.1f, 1f)
                                                drawCircle(ringOuter, radius = (24f + liveAmp * 21f + pulseAlpha.value * 7f).dp.toPx())
                                                drawCircle(ringInner, radius = (24f + liveAmp * 11f + pulseAlpha.value * 4f).dp.toPx())
                                            }
                                        }
                                        .then(
                                            if (isRecordingVoice && !isPrivateMode) {
                                                // Pulses with the shared chat gradient
                                                Modifier
                                                    .graphicsLayer {
                                                        scaleX = 1f + pulseAlpha.value * 0.1f
                                                        scaleY = 1f + pulseAlpha.value * 0.1f
                                                    }
                                                    .appGradientShadow(CircleShape)
                                                    .clip(CircleShape)
                                                    .background(notesHorizontalGradient())
                                            } else if (isPrivateMode) {
                                                Modifier.clip(CircleShape).background(recordBg ?: Color.Gray)
                                            } else {
                                                Modifier
                                                    .appGradientShadow(CircleShape)
                                                    .clip(CircleShape)
                                                    .background(notesHorizontalGradient())
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

        // GIPHY's GIFs, opened from the emoji board's GIF tab; a tap sends one
        if (showGifPicker && onSendGif != null) {
            GifPickerSheet(
                onPick = { gif ->
                    showGifPicker = false
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSendGif(gif)
                },
                onDismiss = { showGifPicker = false }
            )
        }

        // WhatsApp/Telegram-Style Tabbed Emoji Panel, in the keyboard's place (NORMAL MODE ONLY)
        if (!isPrivateMode && isEmojiBoardShown && !isRecordingVoice) ComposerPart {
            val boardHeightPx = if (keyboardHeightPx > 0) keyboardHeightPx else with(density) { 280.dp.roundToPx() }
            val emojiNestedScroll = remember {
                var accumulatedPostScrollY = 0f
                object : NestedScrollConnection {
                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        if (available.y > 0f) {
                            accumulatedPostScrollY += available.y
                            if (accumulatedPostScrollY > with(density) { 24.dp.toPx() }) {
                                closeEmojiBoard()
                                accumulatedPostScrollY = 0f
                                return available
                            }
                        } else {
                            accumulatedPostScrollY = 0f
                        }
                        return Offset.Zero
                    }
                }
            }
            val swipeDownModifier = Modifier.pointerInput(Unit) {
                var accumulatedY = 0f
                detectVerticalDragGestures(
                    onDragStart = { accumulatedY = 0f },
                    onDragEnd = { accumulatedY = 0f },
                    onDragCancel = { accumulatedY = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount > 0f) {
                            accumulatedY += dragAmount
                            if (accumulatedY > 20.dp.toPx()) {
                                closeEmojiBoard()
                                accumulatedY = 0f
                            }
                        }
                    }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .nestedScroll(emojiNestedScroll)
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val shownPx = (boardHeightPx * emojiBoardOpen.value - keyboardInsets.getBottom(this))
                            .roundToInt()
                            .coerceIn(0, boardHeightPx)
                        val board = measurable.measure(constraints.copy(minHeight = boardHeightPx, maxHeight = boardHeightPx))
                        layout(board.width, shownPx) { board.place(0, 0) }
                    }
            ) {
                // Dark: AMOLED black; Day: white with gentle transparency
                Surface(
                    color = if (isDark) Color.Black else Color.White.copy(alpha = 0.90f),
                    border = BorderStroke(1.dp, pillBorder),
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    MediaBoardPanel(
                        activeTab = activeMediaTab,
                        onTabSelected = { activeMediaTab = it },
                        isDark = isDark,
                        swipeDownModifier = swipeDownModifier,
                        emojiTabs = emojiTabs,
                        emojiCategories = emojiCategories,
                        emojiPagerState = emojiPagerState,
                        onEmojiPicked = { emoji ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTextChanged(text + emoji)
                        },
                        onSendGif = onSendGif,
                        onOpenGifSearchSheet = { showGifPicker = true },
                        onStickerPicked = { sticker ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (onSendSticker != null) {
                                onSendSticker(sticker)
                            } else {
                                onTextChanged(sticker)
                                onSendText()
                            }
                        },
                        onStickerLongClicked = { sticker ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTextChanged(if (text.isBlank()) sticker else "$text $sticker")
                        },
                        onBackspace = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (text.isNotEmpty()) {
                                val lastCodePoint = text.codePointBefore(text.length)
                                val charCount = Character.charCount(lastCodePoint)
                                onTextChanged(text.substring(0, text.length - charCount))
                            }
                        },
                        onSwitchToKeyboard = { switchToKeyboard() }
                    )
                }
            }
        }

    }
}

/**
 * Composes [content] as a function of its own. MessageComposer is one very large function (Row,
 * Column and Box put their content inline into it), and past 256 registers R8 wrote code for it
 * that Android refused to load: 1.8.43 closed as soon as the chat opened (VerifyError). Its
 * biggest parts go through here, which keeps every function well under that.
 */
@Composable
private fun ComposerPart(content: @Composable () -> Unit) {
    content()
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
    val panelBg = if (isDark) Color.Black else Color.White
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val panelNestedScroll = remember {
        var accumulatedPostScrollY = 0f
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                if (available.y > 0f) {
                    accumulatedPostScrollY += available.y
                    if (accumulatedPostScrollY > with(density) { 24.dp.toPx() }) {
                        onClose()
                        accumulatedPostScrollY = 0f
                        return available
                    }
                } else {
                    accumulatedPostScrollY = 0f
                }
                return Offset.Zero
            }
        }
    }
    val panelSwipeDownModifier = Modifier.pointerInput(Unit) {
        var accumulatedY = 0f
        detectVerticalDragGestures(
            onDragStart = { accumulatedY = 0f },
            onDragEnd = { accumulatedY = 0f },
            onDragCancel = { accumulatedY = 0f },
            onVerticalDrag = { _, dragAmount ->
                if (dragAmount > 0f) {
                    accumulatedY += dragAmount
                    if (accumulatedY > 20.dp.toPx()) {
                        onClose()
                        accumulatedY = 0f
                    }
                }
            }
        )
    }

    Surface(
        color = panelBg,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        border = BorderStroke(1.dp, pillBorder),
        modifier = modifier
            .fillMaxWidth()
            .height(290.dp)
            .padding(horizontal = 8.dp)
            .nestedScroll(panelNestedScroll)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Drag handle pill at top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(panelSwipeDownModifier)
                    .padding(top = 6.dp, bottom = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1))
                )
            }

            // Top Section: Quick Attachment Action Items
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(panelSwipeDownModifier)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
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
                    .background(if (isDark) Color.Black else Color.White)
                    .border(BorderStroke(0.6.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE2E8F0)))
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
