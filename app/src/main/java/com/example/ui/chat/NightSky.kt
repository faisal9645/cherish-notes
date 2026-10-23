package com.example.ui.chat

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.util.NightCalm
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/*
 * Our nights and mornings in the chat. Once we've both said goodnight, the chat behind the
 * messages becomes a starry night on both phones, until morning. The first message of the morning
 * brings a small sunrise. And from 11 PM the chat is calm (see NightCalm): its colours stay, but
 * its motion is slower and softer and its sounds quieter.
 */

/** The chat's calm night (11 PM to 6 AM): its animations slower and softer, see NightCalm. */
internal val LocalChatNight = staticCompositionLocalOf { false }

/** Whether it's the calm night now; it turns by itself at 11 PM and 6 AM. */
@Composable
internal fun rememberIsNight(): Boolean {
    var night by remember { mutableStateOf(NightCalm.isNight()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
            night = NightCalm.isNight()
        }
    }
    return night
}

private const val HOUR_MS = 60L * 60 * 1000

/** "Good night" in a few of the ways we say it (a short message, or the goodnight card's). */
private val GOODNIGHT_WORDS = Regex("""\b(good\s*night|goodnight|gud\s*night|gd\s*night|gn|night\s*night|sweet\s*dreams)\b""", RegexOption.IGNORE_CASE)

internal fun Message.isGoodnight(): Boolean =
    effect == EFFECT_GOODNIGHT ||
        (getTypedType() == MessageType.TEXT && text.length <= 80 && GOODNIGHT_WORDS.containsMatchIn(text))

/** When this night began: 8 PM (it lasts until 6 AM). */
private fun nightBeganAt(now: Long): Long = Calendar.getInstance().apply {
    timeInMillis = now - 6 * HOUR_MS
    set(Calendar.HOUR_OF_DAY, 20)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** We've both said goodnight tonight (from 8 PM, until 6 AM): the chat is a starry night for both of us. */
internal fun bothSaidGoodnight(messages: List<Message>, myId: String, now: Long = System.currentTimeMillis()): Boolean {
    val began = nightBeganAt(now)
    if (now < began || now >= began + 10 * HOUR_MS) return false
    var mine = false
    var theirs = false
    for (message in messages) {
        if (message.timestamp < began || message.isDeleted || !message.isGoodnight()) continue
        if (message.senderId == myId) mine = true else theirs = true
        if (mine && theirs) return true
    }
    return false
}

/** This morning's first message (from 5 AM), while it's still morning (until noon); null otherwise. */
internal fun firstMorningMessage(messages: List<Message>, now: Long = System.currentTimeMillis()): Message? {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val hour = cal.get(Calendar.HOUR_OF_DAY)
    if (hour < 5 || hour >= 12) return null
    val dawn = cal.apply {
        set(Calendar.HOUR_OF_DAY, 5)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return messages.filter { !it.isDeleted && it.timestamp >= dawn }.minByOrNull { it.timestamp }
}

private fun nightPrefs(context: Context) = context.getSharedPreferences("cherish_night", Context.MODE_PRIVATE)

private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

/** This morning's sunrise was already shown on this phone. */
internal fun isSunriseShown(context: Context): Boolean = nightPrefs(context).getString("sunrise_shown", null) == todayKey()

internal fun markSunriseShown(context: Context) {
    nightPrefs(context).edit().putString("sunrise_shown", todayKey()).apply()
}

private val NightInk = Color(0xFF070A1C)
private val SunGold = Color(0xFFFFC46B)
private val SunRose = Color(0xFFFF9E8A)
private val SunCore = Color(0xFFFFF4C9)

/**
 * Behind the messages, over the wallpaper: the starry night (fading in once we've both said
 * goodnight) and the morning's sunrise. The night is darkest in the middle, lighter under the top
 * bar and the message box, so they stay easy to read.
 */
@Composable
internal fun NightSky(starry: Boolean, sunrise: Boolean, onSunriseDone: () -> Unit, modifier: Modifier = Modifier) {
    // The same day/dark as the wallpaper (the app's theme), so the night photo and this agree
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val isNight = rememberIsNight()
    val night = remember { Animatable(if (starry) 1f else 0f) }
    LaunchedEffect(starry) {
        night.animateTo(if (starry) 1f else 0f, tween(if (starry) 2_600 else 1_800, easing = FastOutSlowInEasing))
    }
    if (night.value > 0f) {
        val deep = if (isDark) 0.4f else 0.66f
        Box(modifier = modifier.graphicsLayer { alpha = night.value }) {
            // When custom 11 PM night wallpaper is active (isNight && isDark), do not overlay the heavy dark gradient
            if (!isNight || !isDark) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to NightInk.copy(alpha = deep * 0.35f),
                                0.18f to NightInk.copy(alpha = deep),
                                0.82f to NightInk.copy(alpha = deep * 0.85f),
                                1f to NightInk.copy(alpha = deep * 0.4f)
                            )
                        )
                )
            }
            TwinklingStars(
                modifier = Modifier.fillMaxSize(),
                count = if (isNight && isDark) 65 else 125,
                brightness = if (isDark) 0.90f else 0.98f,
                periodMs = 3_200,
                maxYFraction = if (isNight && isDark) 0.62f else 1f,
                blueGlow = true
            )
            // A soft moon, high on the right (skip if photo wallpaper already contains moon)
            if (!isNight || !isDark) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 72.dp, end = 18.dp)
                        .size(64.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(Brush.radialGradient(listOf(Color(0x40FFF1C1), Color(0x00FFF1C1))), CircleShape)
                    )
                    Text(text = "🌙", fontSize = 26.sp)
                }
            }
        }
    }
    if (sunrise) Sunrise(onDone = onSunriseDone, modifier = modifier)
}

/** The first message of the morning: a small sun rises behind the messages, glows warm, and fades. */
@Composable
private fun Sunrise(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        rise.animateTo(1f, tween(4_400, easing = LinearEasing))
        onDone()
    }
    Canvas(modifier = modifier) {
        val t = rise.value
        // Up over the first 60%, a warm glow throughout, all of it fading over the last quarter
        val up = FastOutSlowInEasing.transform((t / 0.6f).coerceIn(0f, 1f))
        val glow = (t / 0.25f).coerceAtMost(1f) * (if (t < 0.75f) 1f else (1f - t) / 0.25f)
        val radius = size.minDimension * 0.1f
        val center = Offset(size.width * 0.5f, size.height * 0.9f + radius * 1.3f - up * size.height * 0.3f)
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.45f to SunRose.copy(alpha = 0.16f * glow),
                1f to SunGold.copy(alpha = 0.36f * glow)
            )
        )
        drawCircle(
            Brush.radialGradient(listOf(SunGold.copy(alpha = 0.5f * glow), SunGold.copy(alpha = 0f)), center = center, radius = radius * 3.4f),
            radius = radius * 3.4f,
            center = center
        )
        // Soft rays, turning slowly
        rotate(t * 24f, pivot = center) {
            for (i in 0 until 12) {
                val angle = i * (Math.PI.toFloat() / 6f)
                val direction = Offset(cos(angle), sin(angle))
                drawLine(
                    color = SunGold.copy(alpha = 0.32f * glow),
                    start = center + direction * (radius * 1.3f),
                    end = center + direction * (radius * 1.85f),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
        drawCircle(
            Brush.radialGradient(listOf(SunCore, SunGold), center = center, radius = radius),
            radius = radius,
            center = center,
            alpha = glow
        )
    }
}
