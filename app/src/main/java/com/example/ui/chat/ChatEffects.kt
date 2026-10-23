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
import androidx.compose.material.icons.rounded.Favorite
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
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random
import com.example.ui.theme.HeartPink
import com.example.ui.theme.Rose600

/** Haptic vocabulary for the chat; Compose's LocalHapticFeedback only offers a long-press buzz. */
enum class ChatHaptic { Tick, Send, Receive, LongPress, Delete }

fun View.chatHaptic(type: ChatHaptic) {
    // Settings > Haptic Feedback (all of it) and its separate send / new message / long-press choices
    val prefs = com.example.security.SecurityPreferences.getInstance(context)
    if (!prefs.isHapticFeedbackEnabled()) return
    when (type) {
        // A soft tick when a message goes out
        ChatHaptic.Send -> if (prefs.isHapticOnSend()) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        // A gentle buzz when a new message arrives in the open chat
        ChatHaptic.Receive -> if (prefs.isHapticOnReceive()) context.vibrateGentle()
        // A heartbeat on long-press (or the usual long-press click when that's off)
        ChatHaptic.LongPress ->
            if (prefs.isHeartbeatOnLongPress()) context.vibrateHeartbeat()
            else performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        ChatHaptic.Tick -> performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        ChatHaptic.Delete -> performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        )
    }
}

/** One short, soft buzz. */
fun Context.vibrateGentle() {
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 26) {
            val effect = VibrationEffect.createOneShot(28, if (vibrator.hasAmplitudeControl()) 90 else VibrationEffect.DEFAULT_AMPLITUDE)
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                vibrator.vibrate(effect)
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(28)
        }
    } catch (_: Exception) {}
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

/**
 * A pattern of buzzes: [timings] alternate off and on (ms, starting with off), [amplitudes] 0-255
 * for each (plain buzzes where the phone can't vary strength). [inHand]: feedback for a touch (it
 * follows the phone's touch-vibration setting on Android 13+); otherwise something felt, like a
 * hug or a kiss arriving.
 */
// One bounded waveform at a time. Do not retry a failing OEM vibrator service in a burst.
private object ChatVibrationGate {
    private var busyUntil = 0L
    private var touchUntil = 0L
    private var failedUntil = 0L
    @Synchronized fun acquire(duration: Long, inHand: Boolean): Boolean {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now < failedUntil || now < touchUntil) return false
        if (!inHand && now < busyUntil) return false
        if (inHand) touchUntil = now + duration.coerceAtMost(120L)
        busyUntil = now + duration + 80L
        return true
    }
    @Synchronized fun backOff() {
        failedUntil = android.os.SystemClock.elapsedRealtime() + 30_000L
    }
}

fun Context.vibrateWave(timings: LongArray, amplitudes: IntArray, inHand: Boolean = true) {
    if (timings.isEmpty() || timings.size != amplitudes.size || timings.size > 16) return
    if (timings.any { it < 0L || it > 5_000L }) return
    val duration = timings.sum()
    if (duration !in 1L..6_000L || amplitudes.none { it > 0 }) return
    if (!com.example.security.SecurityPreferences.getInstance(this).isHapticFeedbackEnabled()) return
    if (!ChatVibrationGate.acquire(duration, inHand)) return
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 26) {
            // Use timing-only feedback on Vivo/iQOO to avoid dependence on custom amplitude support.
            val manufacturer = Build.MANUFACTURER.lowercase(java.util.Locale.ROOT)
            val defaultAmplitude = manufacturer.contains("vivo") || manufacturer.contains("iqoo")
            val effect = if (!defaultAmplitude && vibrator.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, amplitudes.map { it.coerceIn(0, 255) }.toIntArray(), -1)
            } else {
                VibrationEffect.createWaveform(timings, -1)
            }
            // Decorative chat feedback follows the device's touch-feedback policy.
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                vibrator.vibrate(effect)
            }
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(timings, -1)
        }
    } catch (_: Exception) {
        ChatVibrationGate.backOff()
    }
}
/** True when animations are switched off (accessibility "Remove animations" / developer options). */
fun Context.areAnimationsDisabled(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** A soft light sweep composable, used as a high-performance loading placeholder without composed modifier overhead. */
@Composable
fun ShimmerPlaceholder(
    modifier: Modifier = Modifier,
    baseColor: Color,
    highlightColor: Color
) {
    val isForeground = com.example.ui.components.rememberAppIsResumed()
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress: Float = if (isForeground) {
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
            label = "shimmer_progress"
        )
        p
    } else 0f

    androidx.compose.foundation.layout.Spacer(
        modifier = modifier.drawBehind {
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
    )
}

/** Legacy Modifier.shimmer wrapper. Prefer [ShimmerPlaceholder] to avoid composed allocation churn. */
fun Modifier.shimmer(baseColor: Color, highlightColor: Color): Modifier = composed {
    val isForeground = com.example.ui.components.rememberAppIsResumed()
    val progress: Float = if (isForeground) {
        val transition = rememberInfiniteTransition(label = "shimmer")
        val p by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
            label = "shimmer_progress"
        )
        p
    } else {
        0f
    }
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

internal val HeartColors = listOf(
    HeartPink, Color(0xFFFF6B9A), Color(0xFFFF8FB1), Rose600, Color(0xFFFF4D6D)
)

internal class BurstHeart(
    val angle: Float,
    val distanceDp: Float,
    val sizeDp: Float,
    val spin: Float,
    val delay: Float,
    val color: Color,
    // How far its path bows to one side (a part of its distance; below 0 the other way), so the
    // hearts fly out on arcs, not straight lines
    val curve: Float = 0f
)

/** Hearts that burst out of a message on double-tap. */
@Stable
class HeartBurstState {
    internal val progress = Animatable(1f)
    internal var hearts by mutableStateOf(emptyList<BurstHeart>())
        private set

    /** [scale] sizes it for its element: 1 for a message, smaller for, say, a reactions pill. */
    suspend fun burst(scale: Float = 1f, count: Int = 9) {
        hearts = List(count) {
            BurstHeart(
                // Mostly upwards (screen y grows downwards), fanned out to the sides
                angle = (-PI / 2 + (Random.nextFloat() - 0.5f) * 2.4f).toFloat(),
                distanceDp = (48f + Random.nextFloat() * 64f) * scale,
                sizeDp = (11f + Random.nextFloat() * 11f) * scale,
                spin = (Random.nextFloat() - 0.5f) * 70f,
                delay = Random.nextFloat() * 0.18f,
                color = HeartColors.random(),
                curve = (Random.nextFloat() - 0.5f) * 0.9f
            )
        }
        progress.snapTo(0f)
        progress.animateTo(1f, tween(1000, easing = LinearOutSlowInEasing))
    }
}

@Composable
fun rememberHeartBurstState(): HeartBurstState = remember { HeartBurstState() }

/** A fling that hits either end gives one soft thud, stronger the harder it hits. */
fun triggerEndThud(view: View, speed: Float) {
    val prefs = com.example.security.SecurityPreferences.getInstance(view.context)
    if (!prefs.isHapticFeedbackEnabled()) return
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            view.context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            view.context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (Build.VERSION.SDK_INT >= 26 && vibrator != null && vibrator.hasAmplitudeControl()) {
            val amplitude = ((speed / 3500f) * 220f).toInt().coerceIn(35, 255)
            val effect = VibrationEffect.createOneShot(26L, amplitude)
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                vibrator.vibrate(effect)
            }
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    } catch (_: Exception) {}
}

/** The fling ticks also get softer as it slows, like a wheel winding down. Phones without rich haptics keep today's tick. */
fun triggerWindingTick(view: View, speedFactor: Float) {
    val prefs = com.example.security.SecurityPreferences.getInstance(view.context)
    if (!prefs.isHapticFeedbackEnabled()) return
    try {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            view.context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            view.context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (Build.VERSION.SDK_INT >= 26 && vibrator != null && vibrator.hasAmplitudeControl()) {
            val amplitude = (speedFactor * 75f).toInt().coerceIn(15, 110)
            val effect = VibrationEffect.createOneShot(8L, amplitude)
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
            } else {
                vibrator.vibrate(effect)
            }
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    } catch (_: Exception) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
}

/**
 * Pulled on past either end of the chat list, in px (shared by the chat's rows, read only while drawing):
 * the newest and oldest ends bunch up like an accordion and spring back open with a bounce when let go,
 * so both ends feel like the same rubber in place of Android's system stretch.
 */
@Stable
class AccordionPull(private val list: androidx.compose.foundation.lazy.LazyListState) {
    var bottomPull by androidx.compose.runtime.mutableFloatStateOf(0f)
        private set
    var topPull by androidx.compose.runtime.mutableFloatStateOf(0f)
        private set

    val value: Float
        get() = maxOf(bottomPull, topPull)

    private var bottomSettleJob: kotlinx.coroutines.Job? = null
    private var topSettleJob: kotlinx.coroutines.Job? = null

    /** Dragged past the newest message (bottom of chat). */
    fun dragBottom(px: Float, maxPx: Float) {
        bottomSettleJob?.cancel()
        val give = (1f - bottomPull / maxPx).coerceIn(0.05f, 1f)
        bottomPull = (bottomPull + px * 0.5f * give).coerceIn(0f, maxPx)
    }

    /** Dragged past the oldest message (top of chat). */
    fun dragTop(px: Float, maxPx: Float) {
        topSettleJob?.cancel()
        val give = (1f - topPull / maxPx).coerceIn(0.05f, 1f)
        topPull = (topPull + px * 0.5f * give).coerceIn(0f, maxPx)
    }

    /** Legacy / bottom drag helper. */
    fun dragBy(px: Float, maxPx: Float) = dragBottom(px, maxPx)

    /** Released back from bottom accordion. Returns how much of it that took. */
    fun releaseBottom(px: Float): Float {
        bottomSettleJob?.cancel()
        val taken = minOf(px, bottomPull.coerceAtLeast(0f))
        bottomPull -= taken
        return taken
    }

    /** Released back from top accordion. Returns how much of it that took. */
    fun releaseTop(px: Float): Float {
        topSettleJob?.cancel()
        val taken = minOf(px, topPull.coerceAtLeast(0f))
        topPull -= taken
        return taken
    }

    /** Legacy release helper. */
    fun releaseBy(px: Float): Float = releaseBottom(px)

    fun settleBottom(scope: kotlinx.coroutines.CoroutineScope, velocityPxPerSec: Float = 0f) {
        if (bottomPull == 0f && velocityPxPerSec == 0f) return
        bottomSettleJob?.cancel()
        bottomSettleJob = scope.launch {
            androidx.compose.animation.core.animate(
                initialValue = bottomPull,
                targetValue = 0f,
                initialVelocity = velocityPxPerSec,
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.42f, stiffness = 380f)
            ) { pulled, _ -> bottomPull = pulled }
        }
    }

    fun settleTop(scope: kotlinx.coroutines.CoroutineScope, velocityPxPerSec: Float = 0f) {
        if (topPull == 0f && velocityPxPerSec == 0f) return
        topSettleJob?.cancel()
        topSettleJob = scope.launch {
            androidx.compose.animation.core.animate(
                initialValue = topPull,
                targetValue = 0f,
                initialVelocity = velocityPxPerSec,
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.42f, stiffness = 380f)
            ) { pulled, _ -> topPull = pulled }
        }
    }

    /** Settle both ends back to flat. */
    fun settle(scope: kotlinx.coroutines.CoroutineScope, velocityPxPerSec: Float = 0f) {
        if (bottomPull > 0f) settleBottom(scope, velocityPxPerSec)
        if (topPull > 0f) settleTop(scope, velocityPxPerSec)
    }

    /**
     * How far the row [key] moves now (+ is down for top accordion, - is up for bottom accordion).
     * The boundary items move the most, with exponential decay into the list.
     */
    fun shiftFor(key: Any): Float {
        val b = bottomPull
        val t = topPull
        if (b == 0f && t == 0f) return 0f
        val visible = list.layoutInfo.visibleItemsInfo
        val item = visible.firstOrNull { it.key == key } ?: return 0f
        var total = 0f
        if (b > 0f) {
            // Newest message (index 0) bunches up toward bottom
            total -= b * kotlin.math.exp(-item.index / 4.5f)
        }
        if (t > 0f) {
            // Oldest message (max visible index) bunches down toward top
            val maxIdx = visible.maxOfOrNull { it.index } ?: 0
            val dist = (maxIdx - item.index).coerceAtLeast(0)
            total += t * kotlin.math.exp(-dist / 4.5f)
        }
        return total
    }
}

/** The chat list's [AccordionPull], for its rows (none outside the chat). */
internal val LocalAccordionPull = androidx.compose.runtime.staticCompositionLocalOf<AccordionPull?> { null }

/**
 * Draws [state]'s heart burst around this element. Put it before any clip in the modifier chain so
 * the hearts can fly past the bubble's edge.
 */
@Composable
fun Modifier.heartBurst(state: HeartBurstState): Modifier {
    val heart = rememberVectorPainter(Icons.Rounded.Favorite)
    return drawWithContent {
        drawContent()
        val t = state.progress.value
        if (t >= 1f) return@drawWithContent
        state.hearts.forEach { h ->
            val local = ((t - h.delay) / (1f - h.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val travel = 1f - (1f - local).pow(3)
            val distance = h.distanceDp.dp.toPx() * travel
            // Bowed out to one side mid-flight (a bezier-like arc), back on its line at the end
            val bow = h.curve * h.distanceDp.dp.toPx() * sin(travel * PI.toFloat())
            val position = Offset(
                center.x + cos(h.angle) * distance - sin(h.angle) * bow,
                center.y + sin(h.angle) * distance + cos(h.angle) * bow - local * local * 24.dp.toPx()
            )
            val grow = if (local < 0.2f) local / 0.2f else 1f
            val alpha = if (local > 0.55f) 1f - (local - 0.55f) / 0.45f else 1f
            drawHeart(heart, position, h.sizeDp.dp.toPx() * grow, h.spin * local, h.color, alpha)
        }
    }
}

internal fun DrawScope.drawHeart(heart: VectorPainter, center: Offset, size: Float, rotation: Float, color: Color, alpha: Float) {
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
    val heart = rememberVectorPainter(Icons.Rounded.Favorite)
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
