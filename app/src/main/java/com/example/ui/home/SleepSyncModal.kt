package com.example.ui.home

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * "Goodnight Kiss" / Sleep Sync Modal
 *
 * Tap and hold a glowing sphere until both phones trigger a synchronized soft vibration.
 * Sets partner's status to "Asleep 🌙" until they unlock their phone in the morning.
 */
@Composable
fun GoodnightKissSleepSyncModal(
    partnerName: String,
    onDismiss: () -> Unit,
    onSleepSyncComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isHolding by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    var isSuccess by remember { mutableStateOf(false) }

    // Breathing glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "sphere_glow")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse_scale"
    )
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation"
    )

    fun triggerSoftVibration() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Synchronized soft waveform vibration (heartbeat pulse)
                val timings = longArrayOf(0, 45, 90, 65, 120, 140)
                val amplitudes = intArrayOf(0, 110, 0, 140, 0, 180)
                vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 50, 80, 70), -1)
            }
        } catch (_: Exception) {}
    }

    fun triggerTickHaptic() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(20)
            }
        } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090616).copy(alpha = 0.96f))
                .testTag("goodnight_kiss_modal"),
            contentAlignment = Alignment.Center
        ) {
            // Close Button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .fillMaxWidth()
            ) {
                // Header Moon Icon
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFDE047).copy(alpha = 0.16f),
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.NightsStay,
                            contentDescription = null,
                            tint = Color(0xFFFDE047),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isSuccess) "Goodnight Kiss Sent! 💋" else "Goodnight Kiss & Sleep Sync",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isSuccess) {
                        "Synchronized sleep status active: Asleep 🌙\nBoth phones locked in sweet dreams until morning."
                    } else {
                        "Tap and hold the glowing sphere to send a warm kiss & sync sleep with $partnerName."
                    },
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(36.dp))

                // The Glowing Celestial Sphere
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(220.dp)
                        .pointerInput(isSuccess) {
                            if (isSuccess) return@pointerInput
                            detectTapGestures(
                                onPress = {
                                    isHolding = true
                                    val startTime = System.currentTimeMillis()
                                    val holdJob = scope.launch {
                                        var lastTick = 0
                                        while (isHolding && holdProgress < 1f) {
                                            val elapsed = System.currentTimeMillis() - startTime
                                            holdProgress = (elapsed / 1800f).coerceIn(0f, 1f)
                                            val currentTick = (holdProgress * 4).toInt()
                                            if (currentTick > lastTick) {
                                                lastTick = currentTick
                                                triggerTickHaptic()
                                            }
                                            delay(16)
                                        }
                                        if (holdProgress >= 1f) {
                                            isSuccess = true
                                            triggerSoftVibration()
                                            onSleepSyncComplete()
                                        }
                                    }
                                    tryAwaitRelease()
                                    isHolding = false
                                    holdJob.cancel()
                                    if (!isSuccess) {
                                        holdProgress = 0f
                                    }
                                }
                            )
                        }
                        .testTag("glowing_sleep_sphere")
                ) {
                    val sphereSize = 130.dp
                    val currentScale = if (isHolding) 1.15f else pulseScale

                    // Canvas aura, radiant rays & progress ring
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val r = (sphereSize.toPx() / 2f) * currentScale

                        // Background luminous stardust rings
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFE879F9).copy(alpha = if (isHolding) 0.38f else 0.22f),
                                    Color(0xFF818CF8).copy(alpha = if (isHolding) 0.20f else 0.12f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = r * 1.8f
                            ),
                            radius = r * 1.8f,
                            center = center
                        )

                        // Orbiting star particles
                        val numStars = 8
                        for (i in 0 until numStars) {
                            val angleRad = Math.toRadians((rotationAngle + i * (360f / numStars)).toDouble())
                            val orbitR = r * 1.35f
                            val sx = center.x + (orbitR * cos(angleRad)).toFloat()
                            val sy = center.y + (orbitR * sin(angleRad)).toFloat()
                            drawCircle(
                                color = Color(0xFFFDE047).copy(alpha = 0.65f),
                                radius = 2.5.dp.toPx(),
                                center = Offset(sx, sy)
                            )
                        }

                        // Progress ring around the sphere
                        if (holdProgress > 0f) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(Color(0xFFF43F5E), Color(0xFFA855F7), Color(0xFF38BDF8), Color(0xFFF43F5E))
                                ),
                                startAngle = -90f,
                                sweepAngle = 360f * holdProgress,
                                useCenter = false,
                                style = Stroke(width = 4.5.dp.toPx())
                            )
                        }
                    }

                    // Main Glowing Orb
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(sphereSize * currentScale)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = if (isSuccess) {
                                        listOf(Color(0xFFFDE047), Color(0xFFEC4899), Color(0xFF8B5CF6))
                                    } else if (isHolding) {
                                        listOf(Color(0xFFFFF1F2), Color(0xFFFB7185), Color(0xFF9333EA))
                                    } else {
                                        listOf(Color(0xFFFCE7F3), Color(0xFFE879F9), Color(0xFF6366F1))
                                    }
                                )
                            )
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (isSuccess) "🌙✨" else if (isHolding) "💋" else "✨",
                                fontSize = if (isSuccess) 38.sp else 32.sp
                            )
                            if (!isSuccess && !isHolding) {
                                Text(
                                    text = "HOLD",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White.copy(alpha = 0.95f),
                                    letterSpacing = 1.2.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Status or instructions
                if (isSuccess) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .testTag("sleep_sync_done_btn")
                    ) {
                        Text("Goodnight my love 💛", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Text(
                        text = if (isHolding) {
                            "Synchronizing heartbeat & soft vibration... ${(holdProgress * 100).toInt()}%"
                        } else {
                            "Press & hold to send kiss"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isHolding) Color(0xFFF472B6) else Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
