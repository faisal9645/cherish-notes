package com.example.ui.chat

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.HeartRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Message effect: a goodnight that dims the partner's screen with stars when they see it. */
const val EFFECT_GOODNIGHT = "goodnight"

/** A mood older than this fades from the partner's header (and its hug nudge with it). */
const val MOOD_FRESH_MS = 12L * 60 * 60 * 1000

private const val HOUR_MS = 60L * 60 * 1000

// ---------------------------------------------------------------------------------------------
// Days together and celebrations
// ---------------------------------------------------------------------------------------------

/** Dates for the days counter: "together since" is a "yyyy-MM-dd" day, counted on this phone's calendar. */
object LoveDates {
    private fun dayFormat() = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }

    fun parse(date: String?): Calendar? {
        val clean = date?.trim()?.ifBlank { null } ?: return null
        return try {
            val parsed = dayFormat().parse(clean) ?: return null
            Calendar.getInstance().apply { time = parsed }
        } catch (_: Exception) {
            null
        }
    }

    /** "Day N together": the first day is Day 1. Null when unset or in the future. */
    fun dayNumber(since: String?, now: Long = System.currentTimeMillis()): Int? {
        val start = parse(since) ?: return null
        val today = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Rounded, so a daylight-saving hour doesn't shift the count
        val days = ((today.timeInMillis - start.timeInMillis) / 86_400_000.0).roundToInt()
        return if (days < 0) null else days + 1
    }

    /**
     * Today's celebration, if any: the anniversary ("Happy 2 years, us"), the monthly date
     * ("Happy 5 months, us"; the 31st falls on a short month's last day) or every 100th day.
     */
    fun celebrationFor(since: String?, now: Long = System.currentTimeMillis()): String? {
        val start = parse(since) ?: return null
        val today = Calendar.getInstance().apply { timeInMillis = now }
        val months = (today.get(Calendar.YEAR) - start.get(Calendar.YEAR)) * 12 +
            (today.get(Calendar.MONTH) - start.get(Calendar.MONTH))
        val monthDay = minOf(start.get(Calendar.DAY_OF_MONTH), today.getActualMaximum(Calendar.DAY_OF_MONTH))
        if (months >= 1 && today.get(Calendar.DAY_OF_MONTH) == monthDay) {
            return if (months % 12 == 0) {
                val years = months / 12
                "Happy $years ${if (years == 1) "year" else "years"}, us"
            } else {
                "Happy $months ${if (months == 1) "month" else "months"}, us"
            }
        }
        val day = dayNumber(since, now) ?: return null
        return if (day >= 100 && day % 100 == 0) "Day $day together" else null
    }

    fun formatLong(since: String?): String? {
        val start = parse(since) ?: return null
        return SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(start.time)
    }
}

// ---------------------------------------------------------------------------------------------
// Once-a-day / once-a-night bookkeeping (on this phone)
// ---------------------------------------------------------------------------------------------

private fun lovePrefs(context: Context) = context.getSharedPreferences("cherish_love", Context.MODE_PRIVATE)

private fun calendarDayKey(now: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now))

/** One key per night: 10 PM and 1 AM belong to the same night (the day turns at 6 AM). */
private fun nightKey(now: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now - 6 * HOUR_MS))

/** When tonight started (10 PM), for "did I already say goodnight tonight". */
fun tonightStartMillis(now: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance().apply {
        timeInMillis = now - 6 * HOUR_MS
        set(Calendar.HOUR_OF_DAY, 22)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return cal.timeInMillis
}

/** Night (10 PM to 5 AM) and tonight's goodnight card hasn't been shown yet. */
fun isGoodnightDue(context: Context): Boolean {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    if (hour in 5 until 22) return false
    return lovePrefs(context).getString("goodnight_card_shown", null) != nightKey()
}

fun markGoodnightShown(context: Context) {
    lovePrefs(context).edit().putString("goodnight_card_shown", nightKey()).apply()
}

/** Today's celebration, unless it was already shown today. */
fun dueCelebration(context: Context, since: String?): String? {
    val title = LoveDates.celebrationFor(since) ?: return null
    return if (lovePrefs(context).getString("celebrated_on", null) == calendarDayKey()) null else title
}

fun markCelebrated(context: Context) {
    lovePrefs(context).edit().putString("celebrated_on", calendarDayKey()).apply()
}

fun isGoodnightStarsSeen(context: Context, messageId: String): Boolean =
    lovePrefs(context).getString("goodnight_stars_seen", null) == messageId

fun markGoodnightStarsSeen(context: Context, messageId: String) {
    lovePrefs(context).edit().putString("goodnight_stars_seen", messageId).apply()
}

/** The mood (by the time it was set) whose hug nudge was already answered or closed. */
fun hugNudgeHandledFor(context: Context): Long = lovePrefs(context).getLong("hug_nudge_handled", 0L)

fun markHugNudgeHandled(context: Context, moodAt: Long) {
    lovePrefs(context).edit().putLong("hug_nudge_handled", moodAt).apply()
}

/** Moods that ask for a hug. */
fun isStressedMood(mood: String?): Boolean {
    val m = mood?.lowercase() ?: return false
    return "stress" in m || "need hugs" in m || "😣" in m
}

// ---------------------------------------------------------------------------------------------
// Visuals
// ---------------------------------------------------------------------------------------------

private class Star(val x: Float, val y: Float, val radius: Float, val phase: Float)

/** Softly twinkling stars over the whole area. */
@Composable
private fun TwinklingStars(modifier: Modifier = Modifier, count: Int = 60, brightness: Float = 1f) {
    val stars = remember {
        val random = Random(42)
        List(count) {
            Star(random.nextFloat(), random.nextFloat(), 0.6f + random.nextFloat() * 1.8f, random.nextFloat())
        }
    }
    val transition = rememberInfiniteTransition(label = "twinkle")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "twinkle_time"
    )
    Canvas(modifier = modifier) {
        stars.forEach { star ->
            val twinkle = 0.3f + 0.7f * (0.5f + 0.5f * sin(2f * PI.toFloat() * (time + star.phase)))
            val center = Offset(star.x * size.width, star.y * size.height)
            val r = star.radius.dp.toPx()
            if (star.radius > 1.8f) {
                drawCircle(Color.White.copy(alpha = 0.18f * twinkle * brightness), radius = r * 3f, center = center)
            }
            drawCircle(Color.White.copy(alpha = twinkle * brightness), radius = r, center = center)
        }
    }
}

/**
 * From 10 PM, the first open of the night: a moon-and-stars card. One tap sends the partner a
 * goodnight (which dims their screen with stars when they see it).
 */
@Composable
fun GoodnightCard(partnerName: String, onSendGoodnight: () -> Unit, onDismiss: () -> Unit) {
    var sent by remember { mutableStateOf(false) }
    val cardIn = remember { Animatable(0.88f) }
    val moonFloat = rememberInfiniteTransition(label = "moon")
    val moonY by moonFloat.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "moon_y"
    )
    LaunchedEffect(Unit) { cardIn.animateTo(1f, spring(dampingRatio = 0.65f, stiffness = 360f)) }
    LaunchedEffect(sent) {
        if (sent) {
            delay(1_100)
            onDismiss()
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05060F).copy(alpha = 0.55f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .testTag("goodnight_card"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 30.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = cardIn.value
                    scaleY = cardIn.value
                }
                .shadow(20.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF141833), Color(0xFF231F48), Color(0xFF3A2A5C)))
                )
                // Taps on the card itself don't close it
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            TwinklingStars(modifier = Modifier.matchParentSize(), count = 34)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 26.dp)
            ) {
                Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .background(
                                Brush.radialGradient(listOf(Color(0x55FFE9A8), Color(0x00FFE9A8))),
                                CircleShape
                            )
                    )
                    Text(
                        text = "🌙",
                        fontSize = 52.sp,
                        modifier = Modifier.graphicsLayer { translationY = moonY.dp.toPx() }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Sleep well, my love",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF5EEDC),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (sent) "Goodnight sent to $partnerName 💛" else "Say goodnight to $partnerName?",
                    fontSize = 14.sp,
                    color = Color(0xFFCFC6E6),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(18.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (sent) Color(0xFFFFE8A3).copy(alpha = 0.35f) else Color(0xFFFFE8A3),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = !sent) {
                            sent = true
                            onSendGoodnight()
                        }
                        .testTag("goodnight_send")
                ) {
                    Text(
                        text = if (sent) "Sent ✨" else "Send goodnight 🌙",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2A2140),
                        modifier = Modifier.padding(horizontal = 26.dp, vertical = 11.dp)
                    )
                }
                if (!sent) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Not now",
                        fontSize = 13.sp,
                        color = Color(0xFFA79FC2),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onDismiss() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/** The partner said goodnight: the screen softly dims, stars come out, a moon glows. */
@Composable
fun GoodnightStarsOverlay(partnerName: String, onDismiss: () -> Unit) {
    val dim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { dim.animateTo(1f, tween(1_400, easing = FastOutSlowInEasing)) }
    val glow = rememberInfiniteTransition(label = "moon_glow")
    val glowScale by glow.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "moon_glow_scale"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = dim.value }
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF02030A).copy(alpha = 0.86f), Color(0xFF0B0A1F).copy(alpha = 0.72f))
                )
            )
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .testTag("goodnight_stars"),
        contentAlignment = Alignment.Center
    ) {
        TwinklingStars(modifier = Modifier.fillMaxSize(), count = 80)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .graphicsLayer {
                            scaleX = glowScale
                            scaleY = glowScale
                        }
                        .background(
                            Brush.radialGradient(listOf(Color(0x44FFF1C1), Color(0x00FFF1C1))),
                            CircleShape
                        )
                )
                Text(text = "🌙", fontSize = 64.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Goodnight",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF5EEDC)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "from $partnerName 💛",
                fontSize = 14.sp,
                color = Color(0xFFCFC6E6)
            )
        }
    }
}

private class FallingHeart(
    val x: Float,
    val start: Float,
    val size: Int,
    val phase: Float,
    val emoji: String
)

/** Anniversary / monthly date / 100th day: hearts fall over the chat around a small card. */
@Composable
fun LoveCelebration(title: String, subtitle: String?, onDismiss: () -> Unit) {
    val hearts = remember {
        val emojis = listOf("💛", "❤️", "💕", "💖", "🩷")
        List(28) {
            FallingHeart(
                x = Random.nextFloat(),
                start = Random.nextFloat() * 0.45f,
                size = 16 + Random.nextInt(16),
                phase = Random.nextFloat(),
                emoji = emojis[Random.nextInt(emojis.size)]
            )
        }
    }
    val time = remember { Animatable(0f) }
    val cardIn = remember { Animatable(0.8f) }
    val beat = rememberInfiniteTransition(label = "celebrate_beat")
    val beatScale by beat.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "celebrate_beat_scale"
    )
    LaunchedEffect(Unit) {
        launch { cardIn.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 320f)) }
        time.animateTo(1f, tween(6_000, easing = LinearEasing))
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.28f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .testTag("love_celebration"),
        contentAlignment = Alignment.Center
    ) {
        val height = maxHeight
        val width = maxWidth
        hearts.forEach { heart ->
            Text(
                text = heart.emoji,
                fontSize = heart.size.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .graphicsLayer {
                        val p = ((time.value - heart.start) / 0.55f).coerceIn(0f, 1f)
                        val sway = sin(2f * PI.toFloat() * (p * 1.6f + heart.phase))
                        translationX = heart.x * (width.toPx() - 24.dp.toPx()) + sway * 16.dp.toPx()
                        translationY = -40.dp.toPx() + p * (height.toPx() + 80.dp.toPx())
                        rotationZ = sway * 18f
                        alpha = when {
                            p <= 0f -> 0f
                            p > 0.85f -> (1f - p) / 0.15f
                            else -> 1f
                        }
                    }
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 36.dp)
                .graphicsLayer {
                    scaleX = cardIn.value
                    scaleY = cardIn.value
                }
                .shadow(18.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFFFFE3EA), Color(0xFFFFC2D1), Color(0xFFFF9DB5))))
                .padding(horizontal = 28.dp, vertical = 24.dp)
        ) {
            Text(
                text = "💛",
                fontSize = 50.sp,
                modifier = Modifier.graphicsLayer {
                    scaleX = beatScale
                    scaleY = beatScale
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4A1F2E),
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = subtitle, fontSize = 13.sp, color = Color(0xFF7A3B4E), textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * A heartbeat from the partner while the chat is open: a heart beats twice in time with the
 * vibration and fades. No words, and it doesn't block the chat.
 */
@Composable
fun HeartbeatReceivedPulse(onDone: () -> Unit) {
    val scale = remember { Animatable(0.6f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(140))
        repeat(2) {
            scale.animateTo(1.12f, tween(60))
            scale.animateTo(0.96f, tween(110))
            scale.animateTo(1.3f, tween(85))
            scale.animateTo(1f, tween(260))
            delay(300)
        }
        alpha.animateTo(0f, tween(550))
        onDone()
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .graphicsLayer {
                    this.alpha = alpha.value * 0.8f
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .background(Brush.radialGradient(listOf(HeartRed.copy(alpha = 0.32f), Color.Transparent)), CircleShape)
        )
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = HeartRed,
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer {
                    this.alpha = alpha.value
                    scaleX = scale.value
                    scaleY = scale.value
                }
        )
    }
}

/** The partner is stressed: a soft card under the header offering to send a hug. */
@Composable
fun HugNudgeCard(partnerName: String, mood: String, onSendHug: () -> Unit, onDismiss: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 68.dp, start = 14.dp, end = 14.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (isDark) Color(0xFF26202A) else Color(0xFFFFF1F4),
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("hug_nudge")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
            ) {
                Text(text = "🤗", fontSize = 26.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if ("stress" in mood.lowercase()) "$partnerName is feeling stressed 😣"
                        else "$partnerName could use a hug 🥺",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = "A hug might help",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = HeartRed,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onSendHug() }
                        .testTag("hug_nudge_send")
                ) {
                    Text(
                        text = "Send a hug",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
