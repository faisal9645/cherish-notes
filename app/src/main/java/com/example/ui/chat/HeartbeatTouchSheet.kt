package com.example.ui.chat

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
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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

@Composable
fun HeartbeatTouchDialog(
    partnerName: String,
    isPartnerTouching: Boolean,
    onTouchChanged: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val hapticHelper = remember { HeartbeatHapticHelper(context) }
    var isMeTouching by remember { mutableStateOf(false) }

    val isConnected = isMeTouching && isPartnerTouching

    // Trigger haptics when both are touching
    LaunchedEffect(isConnected) {
        if (isConnected) {
            hapticHelper.startHeartbeat()
        } else {
            hapticHelper.stopHeartbeat()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onTouchChanged(false)
            hapticHelper.stopHeartbeat()
        }
    }

    // Heart pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "heartbeat")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = if (isConnected) 1.0f else 1.0f,
        targetValue = if (isConnected) 1.28f else 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isConnected) 420 else 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = if (isConnected) 0.5f else 0.2f,
        targetValue = if (isConnected) 0.95f else 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isConnected) 420 else 900, easing = FastOutSlowInEasing),
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
                val radius = size.width * (if (isConnected) 0.65f else 0.4f)
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
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Text(
                    text = "Heartbeat Touch",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isConnected) HeartRed.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isConnected) HeartRed.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f)
                    )
                ) {
                    Text(
                        text = when {
                            isConnected -> "✨ Synchronized! Both hearts beating together ❤️"
                            isMeTouching && !isPartnerTouching -> "Touching... Waiting for $partnerName to hold"
                            !isMeTouching && isPartnerTouching -> "❤️ $partnerName is holding! Touch the screen now!"
                            else -> "Hold your finger on the screen simultaneously"
                        },
                        color = if (isConnected) Color.White else Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(48.dp))

                // Pulsating Touch Sensor Circle
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(190.dp)
                        .scale(pulseScale)
                ) {
                    // Outer ripple ring
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(
                                (if (isConnected) HeartRed else RoseGoldPrimary)
                                    .copy(alpha = glowAlpha * 0.25f)
                            )
                            .border(
                                2.dp,
                                (if (isConnected) HeartRed else RoseGoldPrimary).copy(alpha = glowAlpha),
                                CircleShape
                            )
                    )

                    // Inner circle
                    Surface(
                        shape = CircleShape,
                        color = if (isConnected) HeartRed else if (isMeTouching) RoseGoldPrimary else Color.White.copy(alpha = 0.12f),
                        shadowElevation = if (isConnected) 16.dp else 4.dp,
                        modifier = Modifier.size(120.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isConnected || isMeTouching) Icons.Default.Favorite else Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(54.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                Text(
                    text = if (isMeTouching) "Keep holding..." else "Touch & Hold Anywhere",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "When you and $partnerName hold at the same time, you'll feel synchronized heartbeat vibrations in your palm.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )
            }
        }
    }
}
