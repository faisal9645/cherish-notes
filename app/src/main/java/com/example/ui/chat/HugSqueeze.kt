package com.example.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.data.model.Message
import com.example.security.SecurityPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/*
 * A hug held, not just sent: holding the 🫂 button builds the pressure, from a light embrace
 * through a warm hold to a deep tight squeeze, buzzing harder as it goes. Letting go sends a 🫂
 * that tight: it plays on both phones with that many squeezes, that hard, and the other phone
 * feels each squeeze. A quick tap sends a light one.
 */

/** How tight, saved with the 🫂 message as its effect: "hug:0" to "hug:100". */
private const val HUG_EFFECT_PREFIX = "hug:"

internal fun hugEffect(level: Float): String = HUG_EFFECT_PREFIX + (level.coerceIn(0f, 1f) * 100).roundToInt()

/** How tight this 🫂 was sent (0-1), or null for a plain one. */
internal fun Message.hugSqueeze(): Float? =
    effect?.takeIf { it.startsWith(HUG_EFFECT_PREFIX) }
        ?.removePrefix(HUG_EFFECT_PREFIX)?.toIntOrNull()
        ?.let { (it / 100f).coerceIn(0f, 1f) }

internal enum class HugStrength(val title: String, val adverb: String, val color: Color) {
    // In 🫂's own blues, deepening as it tightens
    Light("Light embrace", "gently", Color(0xFF6EC1F2)),
    Warm("Warm hold", "warmly", Color(0xFF4A97E8)),
    Deep("Deep tight squeeze", "tight", Color(0xFF2F6FD6));

    companion object {
        fun of(level: Float): HugStrength = when {
            level < 0.34f -> Light
            level < 0.67f -> Warm
            else -> Deep
        }
    }
}

/** Holding this long reaches the tightest squeeze. */
private const val HUG_FULL_HOLD_MS = 3_000

/** A quick tap still sends a hug: a light one. */
private const val HUG_TAP_LEVEL = 0.15f

/** The 🫂 button: hold it to build the squeeze, let go to send it (see the top of this file). */
@Composable
internal fun HugHoldButton(onSend: (level: Float) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    val pressure = remember { Animatable(0f) }
    var holding by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(36.dp)
            .graphicsLayer {
                val s = 1f + 0.28f * pressure.value
                scaleX = s
                scaleY = s
            }
            .semantics {
                contentDescription = "Hug: hold to squeeze tighter"
                role = Role.Button
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    holding = true
                    val build = scope.launch {
                        pressure.snapTo(0f)
                        pressure.animateTo(1f, tween(HUG_FULL_HOLD_MS, easing = LinearOutSlowInEasing))
                    }
                    // Felt in the hand: pulses coming faster and harder as it tightens
                    val feel = scope.launch {
                        if (!SecurityPreferences.getInstance(context).isHapticFeedbackEnabled()) return@launch
                        while (isActive && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                            val p = pressure.value
                            context.vibrateSqueezePulse(p)
                            delay((280 - 190 * p).toLong())
                        }
                    }
                    val up = try {
                        waitForUpOrCancellation()
                    } finally {
                        // Pointer cancellation/disposal must not leave a pulse loop running.
                        build.cancel()
                        feel.cancel()
                        holding = false
                    }
                    build.cancel()
                    feel.cancel()
                    holding = false
                    // Slid off the button: no hug
                    if (up != null) {
                        up.consume()
                        val lvl = pressure.value.coerceAtLeast(HUG_TAP_LEVEL)
                        context.vibrateHugRelease(lvl)
                        onSend(lvl)
                    }
                    scope.launch { pressure.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 400f)) }
                }
            }
    ) {
        Text(text = "🫂", fontSize = 20.sp, style = LocalTextStyle.current.copy(lineHeight = TextUnit.Unspecified))
        if (holding) HugPressureMeter(pressure = { pressure.value })
    }
}

/** Over the button while it's held: how tight the hug is so far. */
@Composable
private fun HugPressureMeter(pressure: () -> Float) {
    val lift = with(LocalDensity.current) { 56.dp.roundToPx() }
    Popup(
        alignment = Alignment.BottomCenter,
        offset = IntOffset(0, -lift),
        properties = PopupProperties(focusable = false, clippingEnabled = false)
    ) {
        val p = pressure()
        val strength = HugStrength.of(p)
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(184.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "🫂",
                    fontSize = 34.sp,
                    style = LocalTextStyle.current.copy(lineHeight = TextUnit.Unspecified),
                    modifier = Modifier.graphicsLayer {
                        val s = 0.9f + 0.45f * p
                        scaleX = s
                        scaleY = s
                    }
                )
                Text(text = strength.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = strength.color)
                LinearProgressIndicator(
                    progress = { p },
                    color = strength.color,
                    trackColor = strength.color.copy(alpha = 0.18f),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Text(
                    text = "Release to send",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

/** One pulse while holding the hug: stronger and longer as it tightens. */
internal fun android.content.Context.vibrateSqueezePulse(pressure: Float) {
    val p = pressure.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    vibrateWave(longArrayOf(0, (24 + 46 * p).toLong()), intArrayOf(0, (50 + 205 * p).toInt()))
}

/**
 * A hug arriving (or played again): one squeeze buzz for each squeeze it plays, as hard as it was
 * sent. [peaksMs]: when each squeeze is tightest, from now.
 */
internal fun android.content.Context.vibrateHugSqueezes(level: Float, peaksMs: List<Long>) {
    val safeLevel = level.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: PLAIN_HUG_LEVEL
    val pulse = (90 + 130 * safeLevel).toLong()
    val strength = (70 + 140 * safeLevel).toInt()
    val timings = ArrayList<Long>()
    val amplitudes = ArrayList<Int>()
    var at = 0L
    for (peak in peaksMs.filter { it in 0L..5_000L }.distinct().sorted().take(3)) {
        val start = (peak - pulse / 2).coerceAtLeast(at)
        timings += start - at
        amplitudes += 0
        timings += pulse
        amplitudes += strength
        at = start + pulse
    }
    vibrateWave(timings.toLongArray(), amplitudes.toIntArray(), inHand = false)
}

/** Release uses the same bounded vibrator path as incoming hugs. */
internal fun android.content.Context.vibrateHugRelease(level: Float) {
    val safeLevel = level.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: PLAIN_HUG_LEVEL
    val ms = (35 + 55 * safeLevel).toLong()
    val strength = (100 + 110 * safeLevel).toInt()
    vibrateWave(longArrayOf(0, ms / 2, 15, ms), intArrayOf(0, strength / 2, 0, strength))
}