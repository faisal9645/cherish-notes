package com.example.ui.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.HeartRed
import com.example.ui.theme.RoseGoldPrimary
import com.example.util.HeartbeatHapticHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
    val hapticHelper = remember { HeartbeatHapticHelper(context) }
    var isMeTouching by remember { mutableStateOf(false) }

    // Heartbeat feel (vibration/haptics) ONLY runs when BOTH partners touch and hold
    val isBothTouching = isMeTouching && isPartnerTouching
    val isConnected = isBothTouching

    var heartCenter by remember { mutableStateOf<Offset?>(null) }
    var syncConnectedSeconds by remember { mutableIntStateOf(0) }
    var whoStoppedMessage by remember { mutableStateOf<String?>(null) }
    var previousBothTouching by remember { mutableStateOf(false) }

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

    // Dynamic loving romantic content while both are touching - changes daily for a full month
    val dayOfMonth = remember { java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH) }
    val romanticMessages = remember(dayOfMonth) { getDailyRomanticMessages(dayOfMonth) }
    val currentLoveMessage = remember(syncConnectedSeconds, romanticMessages) {
        romanticMessages[(syncConnectedSeconds / 3) % romanticMessages.size]
    }

    // Heart pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = if (isBothTouching) 0.94f else if (isPartnerTouching || isMeTouching) 0.98f else 1.0f,
        targetValue = if (isBothTouching) 1.34f else if (isPartnerTouching || isMeTouching) 1.15f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isBothTouching) 380 else 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = if (isBothTouching) 0.55f else if (isPartnerTouching || isMeTouching) 0.3f else 0.15f,
        targetValue = if (isBothTouching) 1.0f else if (isPartnerTouching || isMeTouching) 0.7f else 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isBothTouching) 380 else 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    Dialog(
        onDismissRequest = {
            onTouchChanged(false)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F0814),
                            Color(0xFF1E0E1B),
                            Color(0xFF0D040A)
                        )
                    )
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isMeTouching = true
                            onTouchChanged(true)
                            tryAwaitRelease()
                            isMeTouching = false
                            onTouchChanged(false)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Background Ambient Glow
            val glowColor = (if (isConnected) HeartRed else RoseGoldPrimary).copy(alpha = glowAlpha * 0.45f)
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
                onClick = {
                    onTouchChanged(false)
                    onDismiss()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 28.dp)
            ) {
                Text(
                    text = "Heartbeat Touch",
                    color = Color.White,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Live status banner
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isConnected) HeartRed.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isConnected) HeartRed.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.15f)
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
                        color = if (isConnected) Color.White else Color.White.copy(alpha = 0.9f),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
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
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF3B1028).copy(alpha = 0.75f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, HeartRed.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = currentLoveMessage,
                                color = Color(0xFFFFD1DC),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                // Pulsating Touch Sensor Circle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(200.dp)
                        .onGloballyPositioned { coordinates ->
                            heartCenter = coordinates.positionInRoot() +
                                Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
                        }
                        .scale(pulseScale)
                ) {
                    // Outer ripple ring
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(
                                (if (isConnected) HeartRed else RoseGoldPrimary)
                                    .copy(alpha = glowAlpha * 0.30f)
                            )
                            .border(
                                3.dp,
                                (if (isConnected) HeartRed else RoseGoldPrimary).copy(alpha = glowAlpha),
                                CircleShape
                            )
                    )

                    // Inner circle
                    Surface(
                        shape = CircleShape,
                        color = if (isConnected) HeartRed else if (isMeTouching) RoseGoldPrimary else Color.White.copy(alpha = 0.12f),
                        shadowElevation = if (isConnected) 20.dp else 4.dp,
                        modifier = Modifier.size(130.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Heart symbol",
                                tint = if (isConnected || isMeTouching) Color.White else HeartRed.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .size(62.dp)
                                    .scale(if (isConnected) pulseScale / 1.15f else 1.0f)
                            )
                        }
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
                    color = Color.White.copy(alpha = 0.95f),
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
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

private fun shouldFeelHeartbeat(isBothTouching: Boolean): Boolean = isBothTouching

fun getDailyRomanticMessages(dayOfMonth: Int): List<String> {
    return when (dayOfMonth.coerceIn(1, 31)) {
        1 -> listOf(
            "Day 1: A fresh month of loving you with all my heart... ❤️",
            "Every heartbeat is a new promise to cherish you forever ✨",
            "Feel my love flowing straight into your fingertips 💕",
            "You are my first thought at sunrise and last peace at night 💖"
        )
        2 -> listOf(
            "Day 2: Two souls beating to one sacred rhythm across the miles... ❤️",
            "In your pulse, I feel my greatest sanctuary and peace ✨",
            "Nothing compares to holding this heart with you 💕",
            "Loving you is as natural as breathing every single day 💖"
        )
        3 -> listOf(
            "Day 3: Three little words that mean my whole universe: I love you ❤️",
            "Feel our pulses dancing together in complete harmony ✨",
            "With every beat, my soul whispers how lucky I am to have you 💕",
            "You make every distance feel like right next to each other 💖"
        )
        4 -> listOf(
            "Day 4: Four seasons, forever with you in my arms... ❤️",
            "My heart beats faster the moment our fingers meet ✨",
            "You are my sacred home and the love of my lifetime 💕",
            "Hold on tight... I'm sending all my warmth into your hand 💖"
        )
        5 -> listOf(
            "Day 5: Five senses captivated entirely by your love... ❤️",
            "Can you feel how deeply and purely my heart belongs to you? ✨",
            "Every beat says: you are my once in a lifetime 💕",
            "Our love grows more precious with every passing day 💖"
        )
        6 -> listOf(
            "Day 6: Deep in my heart, you have your permanent throne... ❤️",
            "Two hands holding one pulse, bound by unconditional love ✨",
            "Whenever I miss you, this heartbeat connects our souls 💕",
            "You bring color, warmth, and laughter to my whole life 💖"
        )
        7 -> listOf(
            "Day 7: Seven days a week, my love for you only multiplies... ❤️",
            "Your heartbeat is my favorite song in the entire universe ✨",
            "Distance is just geography; our hearts beat together right now 💕",
            "I choose you today, tomorrow, and every day forever 💖"
        )
        8 -> listOf(
            "Day 8: Infinite love symbolized in this endless pulse... ❤️",
            "Feel this warmth? It is my love surrounding your hand ✨",
            "Every pulse reminds me of how precious you are to me 💕",
            "Holding your heart like this is pure magic 💖"
        )
        9 -> listOf(
            "Day 9: On cloud nine every time I feel you close to me... ❤️",
            "You are the rhythm of my days and the peace of my nights ✨",
            "Two imperfect hearts loving each other in perfect harmony 💕",
            "Never doubt how deeply and eternally you are loved 💖"
        )
        10 -> listOf(
            "Day 10: A perfect ten... You are my dream come true ❤️",
            "Feel the steady rhythm of a love that will never fade ✨",
            "Through every storm, our love stays solid and radiant 💕",
            "My fingers never want to let go of this connection 💖"
        )
        11 -> listOf(
            "Day 11: Eleven out of ten reasons why I adore you endlessly... ❤️",
            "Our souls recognize each other across every barrier ✨",
            "Synchronized in love, bonded in heart 💕",
            "You are my happiest thought, every second of every day 💖"
        )
        12 -> listOf(
            "Day 12: Twelve months in a year will never be enough to love you... ❤️",
            "Listen closely: every pulse beats only your name ✨",
            "You are my anchor, my sweetest comfort, and my best friend 💕",
            "Keep holding... I am right here with you in spirit 💖"
        )
        13 -> listOf(
            "Day 13: Lucky in love every single second I have you... ❤️",
            "Our connection is rare, sacred, and unbreakable ✨",
            "Feel the tender warmth flowing directly from my chest to yours 💕",
            "Loving you is the easiest and most beautiful thing in life 💖"
        )
        14 -> listOf(
            "Day 14: Every day is Valentine's Day when our hearts beat together... ❤️",
            "A pulse of romance, loyalty, and deep passion ✨",
            "Two lives woven together in an unbreakable tapestry 💕",
            "I love you more than words, poetry, or songs could say 💖"
        )
        15 -> listOf(
            "Day 15: Halfway through the month, completely head over heels... ❤️",
            "My heart beats steady and strong whenever we connect ✨",
            "You are the light in my life that never dims 💕",
            "Thank you for being the most loving partner in the world 💖"
        )
        16 -> listOf(
            "Day 16: Sweet devotion pulsing through both our hands... ❤️",
            "Our hearts beat together as if we are side by side ✨",
            "You bring so much gentleness and tenderness into my heart 💕",
            "Forever is not long enough to love you 💖"
        )
        17 -> listOf(
            "Day 17: Pure magic happens when our fingers hold this heart... ❤️",
            "Feel how our pulses lock into one single rhythm ✨",
            "You are the answer to every prayer my heart ever whispered 💕",
            "Never letting go of this hand, never letting go of you 💖"
        )
        18 -> listOf(
            "Day 18: Eighteen shades of love pulsing through my soul... ❤️",
            "A sacred bond that distance will never be able to touch ✨",
            "Every pulse tells you: I am yours, completely and truly 💕",
            "You are my forever favorite person in this world 💖"
        )
        19 -> listOf(
            "Day 19: Wrapped up in your love with every single heartbeat... ❤️",
            "Feel the gentle surge of affection travelling straight to you ✨",
            "Nothing makes me smile quite like your touch 💕",
            "Our love is pure, strong, and deeply rooted 💖"
        )
        20 -> listOf(
            "Day 20: Twenty reasons why you have my entire heart... ❤️",
            "Our rhythm is unique—created by us, just for us ✨",
            "Feel the peace that only true love can give 💕",
            "You are my sanctuary, my joy, and my treasure 💖"
        )
        21 -> listOf(
            "Day 21: A full season of pure devotion in every single beat... ❤️",
            "Two hearts sharing one breath across the miles ✨",
            "Loving you is my favorite daily blessing 💕",
            "My pulse beats with gratitude for your love 💖"
        )
        22 -> listOf(
            "Day 22: Double the love, double the heartbeat intensity... ❤️",
            "Can you feel the love radiating through our fingertips? ✨",
            "You make my heart feel so secure, so cherished, and so warm 💕",
            "No matter where life takes us, my heart will always seek yours 💖"
        )
        23 -> listOf(
            "Day 23: Twenty-three hours of missing you, twenty-four hours of loving you... ❤️",
            "Our sacred connection burns bright and steady ✨",
            "Every heartbeat carries a silent kiss across the distance 💕",
            "You are the greatest gift life has ever given me 💖"
        )
        24 -> listOf(
            "Day 24: Twenty-four hours of round-the-clock devotion... ❤️",
            "Feel how deeply connected we are right at this very second ✨",
            "Your love is the gentle rhythm that keeps my world spinning 💕",
            "Holding your heartbeat is the sweetest comfort 💖"
        )
        25 -> listOf(
            "Day 25: Silver celebration of our love this month... ❤️",
            "In every beat of this heart, you will find my loyalty ✨",
            "Two souls united by destiny and sealed with deep affection 💕",
            "I love every little thing about who you are 💖"
        )
        26 -> listOf(
            "Day 26: Strong, steady, and unconditional love pulsing now... ❤️",
            "Distance is powerless against the strength of our hearts ✨",
            "Feel the gentle rhythm: I am here, I am yours 💕",
            "You make ordinary moments feel like absolute heaven 💖"
        )
        27 -> listOf(
            "Day 27: Twenty-seven stars aligned to bring our hearts together... ❤️",
            "Our connection is rare and meant to last for eternity ✨",
            "Feel my pulse whispering: you are my home 💕",
            "Nothing could ever take your place in my soul 💖"
        )
        28 -> listOf(
            "Day 28: Four full weeks of cherishing your precious love... ❤️",
            "A heartbeat of loyalty, respect, and boundless tenderness ✨",
            "You are my companion, my lover, and my guiding star 💕",
            "Hold tight my sweet love... Our story is just beginning 💖"
        )
        29 -> listOf(
            "Day 29: Twenty-nine beats of pure happiness every time we touch... ❤️",
            "My love for you has no end and knows no bounds ✨",
            "Feel how closely our hearts are intertwined right now 💕",
            "You are the beat of my heart and the peace of my mind 💖"
        )
        30 -> listOf(
            "Day 30: Thirty days of pure joy and endless affection with you... ❤️",
            "Through every sunrise and sunset, my heart is yours ✨",
            "Feel our pulses celebrate this beautiful union 💕",
            "Thank you for loving me so deeply and purely 💖"
        )
        else -> listOf(
            "Day 31: End of the month, but my love for you lasts for all eternity... ❤️",
            "A full month complete, and I love you more than when it started ✨",
            "Our hearts beat as one sacred melody forever 💕",
            "Here's to another month of holding you close in my heart 💖"
        )
    }
}
