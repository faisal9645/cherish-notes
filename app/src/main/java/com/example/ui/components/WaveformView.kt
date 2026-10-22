package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * A voice note's waveform:
 * - While recording, the bars ripple with the live sound.
 * - While playing, the bars hold still (the voice's own shape) and the played part fills in as it
 *   goes, with a dot at the playhead; [progressProvider] is read only while drawing, so playback
 *   redraws the wave without recomposing the message around it.
 * - A tap seeks there; a sideways drag scrubs, and the note jumps there on release. A vertical swipe
 *   starting on the wave is left to the list, so scrolling never seeks or starts a note by accident.
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
    modifier: Modifier = Modifier,
    progressProvider: (() -> Float)? = null,
    // The note's length: scrubbing ticks once for every second the playhead passes
    durationSeconds: Int = 0
) {
    val coroutineScope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val latestDuration by rememberUpdatedState(durationSeconds)
    val safeAmplitudes = remember(amplitudes) {
        runCatching {
            (amplitudes as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() } ?: emptyList()
        }.getOrDefault(emptyList())
    }
    val latestProgress by rememberUpdatedState(progress)
    val latestProvider by rememberUpdatedState(progressProvider)
    val latestOnSeek by rememberUpdatedState(onSeek)

    // Under the finger while scrubbing (null otherwise): the wave follows it, and seeks on release
    var dragFraction by remember { mutableStateOf<Float?>(null) }

    // The playhead droplet swells while it's being dragged
    val dropletScale = remember { Animatable(1f) }

    // Live motion while recording, and while it plays (a crest dancing with the playhead), only
    // while the app is on screen. Read while drawing, so it redraws the wave without recomposing
    val isForeground = rememberAppIsResumed()
    val wavePhaseState: State<Float>? = if ((isRecording || isPlaying) && isForeground) {
        val transition = rememberInfiniteTransition(label = "waveform_motion")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave_phase"
        )
    } else {
        null
    }

    val gestureModifier = if (onSeek != null && !isRecording) {
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val slop = viewConfiguration.touchSlop
                val longPressMs = viewConfiguration.longPressTimeoutMillis
                val pointerId = down.id
                var dragging = false
                var travelX = 0f
                var travelY = 0f
                // The second under the playhead while scrubbing, for its tick
                var lastStep = -1
                fun fractionAt(x: Float): Float {
                    val w = size.width.toFloat()
                    return if (w > 0f) (x / w).coerceIn(0f, 1f) else 0f
                }
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) {
                        if (dragging) {
                            val f = dragFraction ?: fractionAt(change.position.x)
                            latestOnSeek?.invoke(f)
                            change.consume()
                        } else if (!change.isConsumed && change.uptimeMillis - down.uptimeMillis < longPressMs) {
                            // A plain tap: jump there
                            latestOnSeek?.invoke(fractionAt(change.position.x))
                            change.consume()
                        }
                        break
                    }
                    if (!dragging) {
                        // Someone else (the list scrolling, a long-press menu) took it
                        if (change.isConsumed) break
                        val delta = change.positionChange()
                        travelX += delta.x
                        travelY += delta.y
                        if (abs(travelY) > slop && abs(travelY) > abs(travelX)) break // a scroll
                        if (abs(travelX) > slop) {
                            dragging = true
                            coroutineScope.launch {
                                dropletScale.animateTo(1.9f, spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    }
                    if (dragging) {
                        change.consume()
                        val fraction = fractionAt(change.position.x)
                        dragFraction = fraction
                        // A light tick for each second it passes (each tenth when its length isn't known)
                        val steps = latestDuration.takeIf { it > 0 } ?: 10
                        val step = (fraction * steps).toInt()
                        if (step != lastStep) {
                            if (lastStep >= 0) {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            }
                            lastStep = step
                        }
                    }
                }
                if (dragging) {
                    dragFraction = null
                    coroutineScope.launch {
                        dropletScale.animateTo(1f, spring(dampingRatio = 0.48f, stiffness = Spring.StiffnessMedium))
                    }
                }
            }
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
        val shownProgress = (dragFraction ?: latestProvider?.invoke() ?: latestProgress).coerceIn(0f, 1f)
        val centerY = size.height / 2f
        val playedX = size.width * shownProgress
        val wavePhase = wavePhaseState?.value ?: 0f
        val dragging = dragFraction != null

        val totalBars = 36
        val barWidth = 3.dp.toPx()
        val step = size.width / totalBars.toFloat()

        for (i in 0 until totalBars) {
            val x = i * step + (step / 2f)
            val rawAmp = if (safeAmplitudes.isNotEmpty()) {
                val idx = ((i.toFloat() / totalBars) * safeAmplitudes.size).toInt()
                    .coerceIn(0, safeAmplitudes.lastIndex)
                safeAmplitudes[idx].coerceIn(0.15f, 1f)
            } else {
                (sin(i * 0.42f) * 0.32f + 0.62f).coerceIn(0.18f, 1f)
            }
            // Playing: how close it is to the playhead (1 on it, fading out over a few bars)
            val bars = (x - playedX) / step
            val crest = if (isPlaying && !isRecording && !dragging) exp(-(bars * bars) / 8f) else 0f
            val amp = when {
                isRecording -> (rawAmp + sin(wavePhase + i * 0.48f) * 0.18f).coerceIn(0.18f, 1f)
                // A crest travels with the playhead: the bars round it rise and fall as it plays
                crest > 0.01f -> (rawAmp * (1f + 0.38f * crest * (0.6f + 0.4f * sin(wavePhase * 2f + i * 0.9f))))
                    .coerceIn(0.15f, 1.12f)
                else -> rawAmp
            }
            val barH = (size.height * amp * 0.92f).coerceAtLeast(barWidth)
            val isPlayed = x <= playedX || isRecording
            if (crest > 0.2f) {
                // A soft glow under the crest, in the wave's own colour
                drawLine(
                    color = activeColor.copy(alpha = activeColor.alpha * 0.22f * crest),
                    start = Offset(x, centerY - barH / 2f),
                    end = Offset(x, centerY + barH / 2f),
                    strokeWidth = barWidth * 2.6f,
                    cap = StrokeCap.Round
                )
            }
            drawLine(
                color = if (isPlayed) activeColor else inactiveColor,
                start = Offset(x, centerY - barH / 2f),
                end = Offset(x, centerY + barH / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }

        // The playhead: shown once it has started (or while dragging), larger while it plays
        if (!isRecording && (shownProgress in 0.005f..0.995f || dragging)) {
            val baseRadius = (if (isPlaying || dragging) 5.dp else 3.5.dp).toPx()
            val radius = baseRadius * dropletScale.value
            if (dragging) {
                drawCircle(color = activeColor.copy(alpha = 0.25f), radius = radius * 1.8f, center = Offset(playedX, centerY))
            }
            drawCircle(color = activeColor, radius = radius, center = Offset(playedX, centerY))
        }
    }
}
