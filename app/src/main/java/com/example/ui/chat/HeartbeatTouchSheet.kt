package com.example.ui.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.home.HeartbeatPink
import com.example.ui.home.HeartbeatBlue
import com.example.util.HeartbeatHapticHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.example.ui.components.softShadow

@Composable
fun HeartbeatTouchDialog(
    partnerName: String,
    isPartnerTouching: Boolean,
    isPartnerOnline: Boolean = false,
    onTouchChanged: (Boolean) -> Unit,
    onSyncHeartbeatStreak: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val touchStart = if (isDark) com.example.ui.theme.DarkBlueSecondary else com.example.ui.theme.DayBlueSecondary
    val touchEnd = if (isDark) com.example.ui.theme.DarkBlueTertiary else com.example.ui.theme.DayBlueTertiary
    val touchAccent = if (isDark) Color(0xFFA9C5ED) else com.example.ui.theme.DayBlueTertiary
    val touchInk = if (isDark) Color.White else Color(0xFF17243B)
    val hapticHelper = remember { HeartbeatHapticHelper(context) }
    var isMeTouching by remember { mutableStateOf(false) }

    // Heartbeat feel (vibration/haptics) ONLY runs when BOTH partners touch and hold
    val isBothTouching = isMeTouching && isPartnerTouching
    val isConnected = isBothTouching

    var heartCenter by remember { mutableStateOf<Offset?>(null) }
    var syncConnectedSeconds by remember { mutableIntStateOf(0) }
    var whoStoppedMessage by remember { mutableStateOf<String?>(null) }
    var previousBothTouching by remember { mutableStateOf(false) }

    // Dual-Touch Gravitational Pull physics: simulates two liquid-magnetic gravitational bodies
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val gravitationalCoalescence = remember { Animatable(0f) }
    var hasEngagedMagneticLock by remember { mutableStateOf(false) }

    LaunchedEffect(isBothTouching) {
        if (isBothTouching) {
            gravitationalCoalescence.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioHighBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        } else {
            hasEngagedMagneticLock = false
            gravitationalCoalescence.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    // Crisp haptic micro-tick when the magnetic lock engages
    LaunchedEffect(gravitationalCoalescence.value) {
        if (gravitationalCoalescence.value >= 0.86f && !hasEngagedMagneticLock && isBothTouching) {
            hasEngagedMagneticLock = true
            try {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            } catch (_: Exception) {}
        }
    }

    // Handle heartbeat haptics
    LaunchedEffect(shouldFeelHeartbeat(isBothTouching)) {
        if (isBothTouching) {
            hapticHelper.startHeartbeat()
            onSyncHeartbeatStreak()
        } else {
            hapticHelper.stopHeartbeat()
        }
    }

    // Live timer & detection of who released first
    LaunchedEffect(isBothTouching) {
        if (isBothTouching) {
            whoStoppedMessage = null
            syncConnectedSeconds = 0
            previousBothTouching = true
            while (isActive) {
                delay(1000)
                syncConnectedSeconds++
            }
        } else if (previousBothTouching) {
            previousBothTouching = false
            hapticHelper.stopHeartbeat()
            val dur = syncConnectedSeconds.coerceAtLeast(1)
            whoStoppedMessage = when {
                !isMeTouching && isPartnerTouching -> "You released touch first (${dur}s heartbeat held) 💔"
                isMeTouching && !isPartnerTouching -> "$partnerName released touch first (${dur}s heartbeat held) 💔"
                else -> "Touch hold ended (${dur}s heartbeat held) ❤️"
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onTouchChanged(false)
            hapticHelper.stopHeartbeat()
        }
    }

    // Dynamic loving romantic content while both are touching - changes daily for a full month.
    // Each line stays long enough to read before the next one fades in.
    val dayOfMonth = remember { java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH) }
    val romanticMessages = remember(dayOfMonth) { getDailyRomanticMessages(dayOfMonth) }
    val currentLoveMessage = remember(syncConnectedSeconds, romanticMessages) {
        romanticMessages[(syncConnectedSeconds / LOVE_MESSAGE_SECONDS) % romanticMessages.size]
    }

    // Heart pulse animation: while both hold, a calm beat in step with the ~1 s vibration rhythm
    val pulseHalfBeatMs = if (isBothTouching) 520 else 750
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = if (isBothTouching) 0.97f else if (isPartnerTouching || isMeTouching) 0.98f else 1.0f,
        targetValue = if (isBothTouching) 1.2f else if (isPartnerTouching || isMeTouching) 1.15f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseHalfBeatMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = if (isBothTouching) 0.55f else if (isPartnerTouching || isMeTouching) 0.3f else 0.15f,
        targetValue = if (isBothTouching) 1.0f else if (isPartnerTouching || isMeTouching) 0.7f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseHalfBeatMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val density = androidx.compose.ui.platform.LocalDensity.current
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    val dismissDialog = {
        onTouchChanged(false)
        onDismiss()
    }

    Dialog(
        onDismissRequest = dismissDialog,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220)),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.94f, animationSpec = tween(180))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                if (isDark) Color(0xFF111B2E) else Color(0xFFEFF4FF),
                                if (isDark) Color(0xFF17243B) else Color(0xFFE0E7FF),
                                if (isDark) Color.Black else Color.White
                            )
                        )
                    )
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            // Exclude touches in top-right corner to prevent close button tap from triggering touch pulse
                            val closeAreaPx = with(density) { 96.dp.toPx() }
                            if (down.position.y < closeAreaPx && down.position.x > size.width - closeAreaPx) {
                                return@awaitEachGesture
                            }
                            isMeTouching = true
                            onTouchChanged(true)
                            try {
                                while (true) {
                                    val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Main)
                                    val anyPressed = event.changes.any { it.pressed }
                                    if (!anyPressed) break
                                }
                            } finally {
                                isMeTouching = false
                                onTouchChanged(false)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                // Background Ambient Glow
                val glowColor = (if (isConnected) touchEnd else touchStart).copy(alpha = glowAlpha * 0.45f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = size.width * (if (isConnected) 0.70f else 0.4f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowColor,
                                Color.Transparent
                            ),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )
                }

                // Hearts rising from behind the big heart while both are holding
                FloatingHeartsStream(
                    active = isConnected,
                    origin = heartCenter,
                    modifier = Modifier.fillMaxSize()
                )

                // Close button top end
                IconButton(
                    onClick = dismissDialog,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp)
                        .size(44.dp)
                        .background(touchInk.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", tint = touchInk)
                }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 28.dp)
            ) {
                Text(
                    text = "Heartbeat Touch",
                    color = touchInk,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Live status banner
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isConnected) touchAccent.copy(alpha = 0.28f) else touchInk.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isConnected) touchAccent.copy(alpha = 0.7f) else touchInk.copy(alpha = 0.15f)
                    )
                ) {
                    Text(
                        text = when {
                            isBothTouching -> "✨ Connected: Both holding heartbeat ($syncConnectedSeconds s) ❤️"
                            whoStoppedMessage != null -> whoStoppedMessage!!
                            !isMeTouching && isPartnerTouching -> "❤️ $partnerName is touching the heart! Touch & hold now"
                            isMeTouching && !isPartnerTouching -> "❤️ Touching your heart... Waiting for $partnerName"
                            else -> "Touch & hold your finger on the heart together"
                        },
                        color = touchInk.copy(alpha = if (isConnected) 1f else 0.9f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Loving message card when connected
                AnimatedVisibility(
                    visible = isBothTouching,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = (if (isDark) Color(0xFF263653) else Color(0xFFDBEAFE)).copy(alpha = 0.75f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, touchAccent.copy(alpha = 0.5f))
                        ) {
                            Crossfade(
                                targetState = currentLoveMessage,
                                animationSpec = tween(900),
                                label = "love_message"
                            ) { message ->
                                Text(
                                    text = message,
                                    color = if (isDark) Color(0xFFD1E3FF) else Color(0xFF183C9D),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                // Pulsating Touch Sensor Circle with Dual-Touch Gravitational Pull Physics
                val coalescence = gravitationalCoalescence.value
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(220.dp)
                        .onGloballyPositioned { coordinates ->
                            heartCenter = coordinates.positionInRoot() +
                                Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
                        }
                        .scale(pulseScale)
                ) {
                    // Outer ripple ring & fluid surface tension canvas
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxSeparation = size.width * 0.28f
                        val currentSeparation = maxSeparation * (1f - coalescence)

                        // If both are touching or coalescing, draw two liquid droplets pulling together
                        if (coalescence > 0.01f || isBothTouching) {
                            val droplet1Center = Offset(center.x - currentSeparation, center.y + currentSeparation * 0.35f)
                            val droplet2Center = Offset(center.x + currentSeparation, center.y - currentSeparation * 0.35f)
                            val dropletRadius = (size.width * 0.22f) * (0.85f + 0.25f * coalescence)

                            // Surface tension liquid neck bridge
                            if (currentSeparation < maxSeparation * 0.75f) {
                                val neckAlpha = (1f - (currentSeparation / (maxSeparation * 0.75f))).coerceIn(0f, 1f)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            (if (isDark) com.example.ui.theme.DarkBluePrimary else com.example.ui.theme.DayBluePrimary).copy(alpha = 0.55f * neckAlpha),
                                            touchEnd.copy(alpha = 0.4f * neckAlpha),
                                            Color.Transparent
                                        ),
                                        center = center,
                                        radius = dropletRadius * 1.5f
                                    ),
                                    radius = dropletRadius * 1.5f,
                                    center = center
                                )
                            }

                            // Body 1: User's liquid droplet (Rose/Pink)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        touchStart.copy(alpha = 0.75f),
                                        touchStart.copy(alpha = 0.25f),
                                        Color.Transparent
                                    ),
                                    center = droplet1Center,
                                    radius = dropletRadius
                                ),
                                radius = dropletRadius,
                                center = droplet1Center
                            )

                            // Body 2: Partner's liquid droplet (Azure/Blue)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        touchEnd.copy(alpha = 0.75f),
                                        touchEnd.copy(alpha = 0.25f),
                                        Color.Transparent
                                    ),
                                    center = droplet2Center,
                                    radius = dropletRadius
                                ),
                                radius = dropletRadius,
                                center = droplet2Center
                            )
                        } else {
                            // Single ambient idle ripple
                            drawCircle(
                                color = (if (isConnected) touchEnd else touchStart).copy(alpha = glowAlpha * 0.30f),
                                radius = size.width * 0.44f,
                                center = center
                            )
                        }
                    }

                    // Outer border ring with magnetic glow
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (hasEngagedMagneticLock) 3.5.dp else 2.5.dp,
                                color = (if (isConnected) touchEnd else touchStart).copy(alpha = glowAlpha),
                                shape = CircleShape
                            )
                    )

                    // Inner core circle: merges both bodies into unified magnetic lock
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size((130f + 16f * coalescence).dp)
                            .softShadow(if (isConnected) (20f + 10f * coalescence).dp else 6.dp, CircleShape)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    if (isDark) listOf(
                                        touchStart.copy(alpha = 0.7f + 0.3f * coalescence),
                                        com.example.ui.theme.DarkBluePrimary.copy(alpha = 0.7f + 0.3f * coalescence),
                                        touchEnd.copy(alpha = 0.7f + 0.3f * coalescence)
                                    ) else listOf(
                                        touchStart.copy(alpha = 0.7f + 0.3f * coalescence),
                                        com.example.ui.theme.DayBluePrimary.copy(alpha = 0.7f + 0.3f * coalescence),
                                        touchEnd.copy(alpha = 0.7f + 0.3f * coalescence)
                                    )
                                )
                            )
                            .border(
                                width = if (hasEngagedMagneticLock) 2.5.dp else 2.dp,
                                color = if (hasEngagedMagneticLock) Color.White else Color.White.copy(alpha = 0.65f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = "Heart symbol",
                            tint = Color.White,
                            modifier = Modifier
                                .size((62f + 8f * coalescence).dp)
                                .scale(if (isConnected) pulseScale / 1.12f else 1.0f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                Text(
                    text = when {
                        isBothTouching -> "Feeling live heartbeat pulse together ❤️"
                        !isMeTouching && isPartnerTouching -> "Touch & hold now with $partnerName!"
                        isMeTouching -> "Keep holding... Waiting for $partnerName"
                        whoStoppedMessage != null -> "Touch and hold again to reconnect"
                        else -> "Touch & Hold Heart"
                    },
                    color = touchInk.copy(alpha = 0.95f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isBothTouching) {
                        "Heartbeat pulses simultaneously in both hands. If anyone releases, it stops automatically."
                    } else {
                        "Both partners must hold the heart simultaneously for heartbeat vibrations to sync in real-time."
                    },
                    color = touchInk.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )
            }
        }
        }
    }
}

private fun shouldFeelHeartbeat(isBothTouching: Boolean): Boolean = isBothTouching

/** How long each love message stays while both are holding. */
private const val LOVE_MESSAGE_SECONDS = 8

fun getDailyRomanticMessages(dayOfMonth: Int): List<String> {
    return when (dayOfMonth.coerceIn(1, 31)) {
        1 -> listOf(
            "A fresh month of loving you with all my heart... ❤️",
            "Every heartbeat is a new promise to cherish you forever ✨",
            "Feel my love flowing straight into your fingertips 💕",
            "You are my first thought at sunrise and last peace at night 💖"
        )
        2 -> listOf(
            "Two souls beating to one sacred rhythm across the miles... ❤️",
            "In your pulse, I feel my greatest sanctuary and peace ✨",
            "Nothing compares to holding this heart with you 💕",
            "Loving you is as natural as breathing every single day 💖"
        )
        3 -> listOf(
            "Three little words that mean my whole universe: I love you ❤️",
            "Feel our pulses dancing together in complete harmony ✨",
            "With every beat, my soul whispers how lucky I am to have you 💕",
            "You make every distance feel like right next to each other 💖"
        )
        4 -> listOf(
            "Four seasons, forever with you in my arms... ❤️",
            "My heart beats faster the moment our fingers meet ✨",
            "You are my sacred home and the love of my lifetime 💕",
            "Hold on tight... I'm sending all my warmth into your hand 💖"
        )
        5 -> listOf(
            "Five senses captivated entirely by your love... ❤️",
            "Can you feel how deeply and purely my heart belongs to you? ✨",
            "Every beat says: you are my once in a lifetime 💕",
            "Our love grows more precious with every passing day 💖"
        )
        6 -> listOf(
            "Deep in my heart, you have your permanent throne... ❤️",
            "Two hands holding one pulse, bound by unconditional love ✨",
            "Whenever I miss you, this heartbeat connects our souls 💕",
            "You bring color, warmth, and laughter to my whole life 💖"
        )
        7 -> listOf(
            "Seven days a week, my love for you only multiplies... ❤️",
            "Your heartbeat is my favorite song in the entire universe ✨",
            "Distance is just geography; our hearts beat together right now 💕",
            "I choose you today, tomorrow, and every day forever 💖"
        )
        8 -> listOf(
            "Infinite love symbolized in this endless pulse... ❤️",
            "Feel this warmth? It is my love surrounding your hand ✨",
            "Every pulse reminds me of how precious you are to me 💕",
            "Holding your heart like this is pure magic 💖"
        )
        9 -> listOf(
            "On cloud nine every time I feel you close to me... ❤️",
            "You are the rhythm of my days and the peace of my nights ✨",
            "Two imperfect hearts loving each other in perfect harmony 💕",
            "Never doubt how deeply and eternally you are loved 💖"
        )
        10 -> listOf(
            "A perfect ten... You are my dream come true ❤️",
            "Feel the steady rhythm of a love that will never fade ✨",
            "Through every storm, our love stays solid and radiant 💕",
            "My fingers never want to let go of this connection 💖"
        )
        11 -> listOf(
            "Eleven out of ten reasons why I adore you endlessly... ❤️",
            "Our souls recognize each other across every barrier ✨",
            "Synchronized in love, bonded in heart 💕",
            "You are my happiest thought, every second of every day 💖"
        )
        12 -> listOf(
            "Twelve months in a year will never be enough to love you... ❤️",
            "Listen closely: every pulse beats only your name ✨",
            "You are my anchor, my sweetest comfort, and my best friend 💕",
            "Keep holding... I am right here with you in spirit 💖"
        )
        13 -> listOf(
            "Lucky in love every single second I have you... ❤️",
            "Our connection is rare, sacred, and unbreakable ✨",
            "Feel the tender warmth flowing directly from my chest to yours 💕",
            "Loving you is the easiest and most beautiful thing in life 💖"
        )
        14 -> listOf(
            "Every day is Valentine's Day when our hearts beat together... ❤️",
            "A pulse of romance, loyalty, and deep passion ✨",
            "Two lives woven together in an unbreakable tapestry 💕",
            "I love you more than words, poetry, or songs could say 💖"
        )
        15 -> listOf(
            "Halfway through the month, completely head over heels... ❤️",
            "My heart beats steady and strong whenever we connect ✨",
            "You are the light in my life that never dims 💕",
            "Thank you for being the most loving partner in the world 💖"
        )
        16 -> listOf(
            "Sweet devotion pulsing through both our hands... ❤️",
            "Our hearts beat together as if we are side by side ✨",
            "You bring so much gentleness and tenderness into my heart 💕",
            "Forever is not long enough to love you 💖"
        )
        17 -> listOf(
            "Pure magic happens when our fingers hold this heart... ❤️",
            "Feel how our pulses lock into one single rhythm ✨",
            "You are the answer to every prayer my heart ever whispered 💕",
            "Never letting go of this hand, never letting go of you 💖"
        )
        18 -> listOf(
            "Eighteen shades of love pulsing through my soul... ❤️",
            "A sacred bond that distance will never be able to touch ✨",
            "Every pulse tells you: I am yours, completely and truly 💕",
            "You are my forever favorite person in this world 💖"
        )
        19 -> listOf(
            "Wrapped up in your love with every single heartbeat... ❤️",
            "Feel the gentle surge of affection travelling straight to you ✨",
            "Nothing makes me smile quite like your touch 💕",
            "Our love is pure, strong, and deeply rooted 💖"
        )
        20 -> listOf(
            "Twenty reasons why you have my entire heart... ❤️",
            "Our rhythm is unique—created by us, just for us ✨",
            "Feel the peace that only true love can give 💕",
            "You are my sanctuary, my joy, and my treasure 💖"
        )
        21 -> listOf(
            "A full season of pure devotion in every single beat... ❤️",
            "Two hearts sharing one breath across the miles ✨",
            "Loving you is my favorite daily blessing 💕",
            "My pulse beats with gratitude for your love 💖"
        )
        22 -> listOf(
            "Double the love, double the heartbeat intensity... ❤️",
            "Can you feel the love radiating through our fingertips? ✨",
            "You make my heart feel so secure, so cherished, and so warm 💕",
            "No matter where life takes us, my heart will always seek yours 💖"
        )
        23 -> listOf(
            "Twenty-three hours of missing you, twenty-four hours of loving you... ❤️",
            "Our sacred connection burns bright and steady ✨",
            "Every heartbeat carries a silent kiss across the distance 💕",
            "You are the greatest gift life has ever given me 💖"
        )
        24 -> listOf(
            "Twenty-four hours of round-the-clock devotion... ❤️",
            "Feel how deeply connected we are right at this very second ✨",
            "Your love is the gentle rhythm that keeps my world spinning 💕",
            "Holding your heartbeat is the sweetest comfort 💖"
        )
        25 -> listOf(
            "Silver celebration of our love this month... ❤️",
            "In every beat of this heart, you will find my loyalty ✨",
            "Two souls united by destiny and sealed with deep affection 💕",
            "I love every little thing about who you are 💖"
        )
        26 -> listOf(
            "Strong, steady, and unconditional love pulsing now... ❤️",
            "Distance is powerless against the strength of our hearts ✨",
            "Feel the gentle rhythm: I am here, I am yours 💕",
            "You make ordinary moments feel like absolute heaven 💖"
        )
        27 -> listOf(
            "Twenty-seven stars aligned to bring our hearts together... ❤️",
            "Our connection is rare and meant to last for eternity ✨",
            "Feel my pulse whispering: you are my home 💕",
            "Nothing could ever take your place in my soul 💖"
        )
        28 -> listOf(
            "Four full weeks of cherishing your precious love... ❤️",
            "A heartbeat of loyalty, respect, and boundless tenderness ✨",
            "You are my companion, my lover, and my guiding star 💕",
            "Hold tight my sweet love... Our story is just beginning 💖"
        )
        29 -> listOf(
            "Twenty-nine beats of pure happiness every time we touch... ❤️",
            "My love for you has no end and knows no bounds ✨",
            "Feel how closely our hearts are intertwined right now 💕",
            "You are the beat of my heart and the peace of my mind 💖"
        )
        30 -> listOf(
            "Thirty days of pure joy and endless affection with you... ❤️",
            "Through every sunrise and sunset, my heart is yours ✨",
            "Feel our pulses celebrate this beautiful union 💕",
            "Thank you for loving me so deeply and purely 💖"
        )
        else -> listOf(
            "End of the month, but my love for you lasts for all eternity... ❤️",
            "A full month complete, and I love you more than when it started ✨",
            "Our hearts beat as one sacred melody forever 💕",
            "Here's to another month of holding you close in my heart 💖"
        )
    }
}
