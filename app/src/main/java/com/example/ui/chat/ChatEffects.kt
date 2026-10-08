package com.example.ui.chat

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Haptic vocabulary for the chat; Compose's LocalHapticFeedback only offers a long-press buzz. */
enum class ChatHaptic { Tick, Send, Receive, LongPress, Delete }

fun View.chatHaptic(type: ChatHaptic) {
    val constant = when (type) {
        ChatHaptic.Tick -> HapticFeedbackConstants.CLOCK_TICK
        ChatHaptic.Send -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        ChatHaptic.Receive -> HapticFeedbackConstants.CONTEXT_CLICK
        ChatHaptic.LongPress -> HapticFeedbackConstants.LONG_PRESS
        ChatHaptic.Delete -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
    }
    performHapticFeedback(constant)
}

/** One "lub-dub" heartbeat. On Android 13+ it follows the touch-vibration setting. */
fun Context.vibrateHeartbeat() {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return
        val timings = longArrayOf(0, 40, 90, 60)
        if (Build.VERSION.SDK_INT >= 26) {
            val effect = VibrationEffect.createWaveform(timings, intArrayOf(0, 150, 0, 255), -1)
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                vibrator.vibrate(effect)
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    } catch (_: Exception) {}
}

/** True when animations are switched off (accessibility "Remove animations" / developer options). */
fun Context.areAnimationsDisabled(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** A soft light sweep, used as a loading placeholder. */
fun Modifier.shimmer(baseColor: Color, highlightColor: Color): Modifier = composed {
    val progress by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "shimmer_progress"
    )
    drawBehind {
        drawRect(baseColor)
        val band = size.width * 0.6f
        val x = -band + (size.width + 2 * band) * progress
        drawRect(
            Brush.linearGradient(
                colors = listOf(Color.Transparent, highlightColor, Color.Transparent),
                start = Offset(x - band / 2, 0f),
                end = Offset(x + band / 2, size.height / 3)
            )
        )
    }
}

private val HeartColors = listOf(
    Color(0xFFFF2D55), Color(0xFFFF6B9A), Color(0xFFFF8FB1), Color(0xFFE11D48), Color(0xFFFF4D6D)
)

internal class BurstHeart(
    val angle: Float,
    val distanceDp: Float,
    val sizeDp: Float,
    val spin: Float,
    val delay: Float,
    val color: Color
)

/** Hearts that burst out of a message on double-tap. */
@Stable
class HeartBurstState {
    internal val progress = Animatable(1f)
    internal var hearts by mutableStateOf(emptyList<BurstHeart>())
        private set

    suspend fun burst() {
        hearts = List(9) {
            BurstHeart(
                // Mostly upwards (screen y grows downwards), fanned out to the sides
                angle = (-PI / 2 + (Random.nextFloat() - 0.5f) * 2.4f).toFloat(),
                distanceDp = 48f + Random.nextFloat() * 64f,
                sizeDp = 11f + Random.nextFloat() * 11f,
                spin = (Random.nextFloat() - 0.5f) * 70f,
                delay = Random.nextFloat() * 0.18f,
                color = HeartColors.random()
            )
        }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1000, easing = LinearOutSlowInEasing))
    }
}

@Composable
fun rememberHeartBurstState(): HeartBurstState = remember { HeartBurstState() }

/**
 * Draws [state]'s heart burst around this element. Put it before any clip in the modifier chain so
 * the hearts can fly past the bubble's edge.
 */
@Composable
fun Modifier.heartBurst(state: HeartBurstState): Modifier {
    val heart = rememberVectorPainter(Icons.Filled.Favorite)
    return drawWithContent {
        drawContent()
        val t = state.progress.value
        if (t >= 1f) return@drawWithContent
        state.hearts.forEach { h ->
            val local = ((t - h.delay) / (1f - h.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val travel = 1f - (1f - local).pow(3)
            val distance = h.distanceDp.dp.toPx() * travel
            val position = Offset(
                center.x + cos(h.angle) * distance,
                center.y + sin(h.angle) * distance - local * local * 24.dp.toPx()
            )
            val grow = if (local < 0.2f) local / 0.2f else 1f
            val alpha = if (local > 0.55f) 1f - (local - 0.55f) / 0.45f else 1f
            drawHeart(heart, position, h.sizeDp.dp.toPx() * grow, h.spin * local, h.color, alpha)
        }
    }
}

private fun DrawScope.drawHeart(heart: VectorPainter, center: Offset, size: Float, rotation: Float, color: Color, alpha: Float) {
    if (size <= 0f || alpha <= 0f) return
    translate(center.x - size / 2, center.y - size / 2) {
        rotate(rotation, pivot = Offset(size / 2, size / 2)) {
            with(heart) { draw(Size(size, size), alpha = alpha, colorFilter = ColorFilter.tint(color)) }
        }
    }
}

private class RisingHeart(
    val bornAt: Long,
    val lifeMs: Long,
    val startOffsetDp: Float,
    val driftDp: Float,
    val swayDp: Float,
    val sizeDp: Float,
    val color: Color
)

/**
 * Hearts that keep floating up from [origin] while [active] (Heartbeat Touch); hearts already in
 * the air finish their flight after it turns off.
 */
@Composable
fun FloatingHeartsStream(
    active: Boolean,
    origin: Offset?,
    modifier: Modifier = Modifier
) {
    val heart = rememberVectorPainter(Icons.Filled.Favorite)
    val hearts = remember { mutableStateListOf<RisingHeart>() }
    var now by remember { mutableLongStateOf(0L) }

    LaunchedEffect(active) {
        var lastSpawn = 0L
        while (isActive && (active || hearts.isNotEmpty())) {
            withFrameMillis { frame ->
                now = frame
                // A gentle, unhurried stream: a few hearts drifting up slowly
                if (active && frame - lastSpawn > 420) {
                    lastSpawn = frame
                    hearts += RisingHeart(
                        bornAt = frame,
                        lifeMs = 2800L + Random.nextLong(1000),
                        startOffsetDp = (Random.nextFloat() - 0.5f) * 90f,
                        driftDp = (Random.nextFloat() - 0.5f) * 70f,
                        swayDp = 6f + Random.nextFloat() * 12f,
                        sizeDp = 14f + Random.nextFloat() * 16f,
                        color = HeartColors.random()
                    )
                }
                hearts.removeAll { frame - it.bornAt > it.lifeMs }
            }
        }
    }

    Canvas(modifier) {
        val start = origin ?: center
        val rise = size.height * 0.45f
        hearts.forEach { h ->
            val p = ((now - h.bornAt).toFloat() / h.lifeMs).coerceIn(0f, 1f)
            val x = start.x + h.startOffsetDp.dp.toPx() + h.driftDp.dp.toPx() * p +
                sin(p * 2.5f * PI.toFloat()) * h.swayDp.dp.toPx()
            val y = start.y - rise * (1f - (1f - p).pow(2))
            val alpha = when {
                p < 0.12f -> p / 0.12f
                p > 0.65f -> 1f - (p - 0.65f) / 0.35f
                else -> 1f
            }
            drawHeart(heart, Offset(x, y), h.sizeDp.dp.toPx() * (0.7f + 0.4f * p), 0f, h.color, alpha * 0.9f)
        }
    }
}
