package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Audio Waveform Visualizer for voice notes:
 * - Provides live dynamic visual feedback during voice recording (organic soundwave ripple & real-time amplitudes)
 * - Provides interactive playback visualizer (played progress, active/inactive ridges, current playhead bounce)
 * - Supports drag-to-scrub and tap-to-seek during playback with an Elastic Magnifying Droplet under the fingertip
 */
@Composable
fun WaveformView(
    amplitudes: List<Float>,
    progress: Float = 0f,
    isPlaying: Boolean = false,
    isRecording: Boolean = false,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
    onSeek: ((Float) -> Unit)? = null,
    height: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val safeAmplitudes = remember(amplitudes) {
        runCatching {
            (amplitudes as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    // Elastic droplet magnifying physics while scrubbing
    val dropletScale = remember { Animatable(1f) }
    val dropletElevation = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // Live continuous animation only active during recording or playback to conserve CPU and battery
    val wavePhase = if (isRecording) {
        val transition = rememberInfiniteTransition(label = "waveform_motion")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave_phase"
        )
        phase
    } else {
        0f
    }

    val playheadPulse = if (isPlaying) {
        val transition = rememberInfiniteTransition(label = "waveform_pulse")
        val pulse by transition.animateFloat(
            initialValue = 0.88f,
            targetValue = 1.22f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 650, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "playhead_pulse"
        )
        pulse
    } else {
        1.0f
    }

    fun startScrubbingPhysics() {
        isDragging = true
        coroutineScope.launch {
            dropletScale.animateTo(
                targetValue = 2.15f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        coroutineScope.launch {
            dropletElevation.animateTo(
                targetValue = 8f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    fun endScrubbingPhysics() {
        isDragging = false
        coroutineScope.launch {
            dropletScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        coroutineScope.launch {
            dropletElevation.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    val gestureModifier = if (onSeek != null && !isRecording) {
        Modifier
            .pointerInput(onSeek) {
                detectTapGestures(
                    onPress = { offset ->
                        startScrubbingPhysics()
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek(fraction)
                        val released = tryAwaitRelease()
                        endScrubbingPhysics()
                    }
                )
            }
            .pointerInput(onSeek) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        startScrubbingPhysics()
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek(fraction)
                    },
                    onDragEnd = {
                        endScrubbingPhysics()
                    },
                    onDragCancel = {
                        endScrubbingPhysics()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onSeek(fraction)
                    }
                )
            }
    } else {
        Modifier
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(gestureModifier)
    ) {
        val centerY = size.height / 2f
        val playedX = size.width * progress.coerceIn(0f, 1f)

        // Number of bars adapts cleanly based on width: approx 36 bars
        val totalBars = 36
        val barWidth = 3.dp.toPx()
        val step = size.width / totalBars.toFloat()

        for (i in 0 until totalBars) {
            val x = i * step + (step / 2f)

            // Calculate base amplitude
            val rawAmp = if (safeAmplitudes.isNotEmpty()) {
                val idx = ((i.toFloat() / totalBars) * safeAmplitudes.size).toInt()
                    .coerceIn(0, safeAmplitudes.lastIndex)
                safeAmplitudes[idx].coerceIn(0.15f, 1f)
            } else {
                (sin(i * 0.42f) * 0.32f + 0.62f).coerceIn(0.18f, 1f)
            }

            // Apply live feedback dynamics
            val amp = when {
                isRecording -> {
                    // Dynamic live soundwave ripple during active recording
                    val waveFlutter = (sin(wavePhase + i * 0.48f) * 0.18f)
                    (rawAmp + waveFlutter).coerceIn(0.18f, 1f)
                }
                isPlaying -> {
                    // Playhead bounce: if this bar is close to the current playhead, give it a bounce
                    val distToPlayhead = kotlin.math.abs(x - playedX)
                    if (distToPlayhead < step * 1.5f) {
                        (rawAmp * playheadPulse).coerceIn(0.15f, 1f)
                    } else {
                        rawAmp
                    }
                }
                else -> rawAmp
            }

            val barH = (size.height * amp * 0.92f).coerceAtLeast(barWidth)
            val isPlayed = x <= playedX || isRecording
            val color = if (isPlayed) activeColor else inactiveColor

            drawLine(
                color = color,
                start = Offset(x, centerY - barH / 2f),
                end = Offset(x, centerY + barH / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }

        // Draw an elastic magnifying droplet playhead dot
        if (!isRecording && (progress in 0.005f..0.995f || isDragging)) {
            val currentScale = dropletScale.value
            val currentElevation = dropletElevation.value
            val baseRadius = 3.5.dp.toPx()
            val effectiveRadius = baseRadius * currentScale

            // Glowing halo when scrubbing
            if (currentScale > 1.1f) {
                drawCircle(
                    color = activeColor.copy(alpha = 0.28f),
                    radius = effectiveRadius * 1.8f,
                    center = Offset(playedX, centerY - currentElevation.dp.toPx())
                )
            }

            // Magnifying Droplet Playhead
            drawCircle(
                color = activeColor,
                radius = effectiveRadius,
                center = Offset(playedX, centerY - currentElevation.dp.toPx())
            )

            // Droplet specular highlight
            if (currentScale > 1.2f) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.75f),
                    radius = effectiveRadius * 0.35f,
                    center = Offset(playedX - effectiveRadius * 0.3f, (centerY - currentElevation.dp.toPx()) - effectiveRadius * 0.3f)
                )
            }
        }
    }
}
