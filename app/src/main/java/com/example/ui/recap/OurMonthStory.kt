package com.example.ui.recap

import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.CherishApplication
import com.example.R
import com.example.ui.home.HeartbeatBlue
import com.example.ui.home.HeartbeatPink
import com.example.ui.security.SecretWindowGuard
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private val RecapFont = FontFamily(Font(R.font.great_vibes_regular))
private val Soft = Color.White.copy(alpha = 0.78f)

/** The story's pages, how long each one stays and its colours. */
private enum class RecapSlide(val durationMs: Int, val top: Color, val bottom: Color) {
    COVER(5_000, Color(0xFFFF4F9A), Color(0xFF3048F5)),
    MESSAGES(7_000, Color(0xFF3048F5), Color(0xFF0B1233)),
    LOVE(5_500, Color(0xFFFF4F9A), Color(0xFF8E2DE2)),
    EMOJI(6_000, Color(0xFFFF8A65), Color(0xFFFF4F9A)),
    MEDIA(6_500, Color(0xFF0B1233), Color(0xFF3048F5)),
    STREAK(7_000, Color(0xFFFF6A3D), Color(0xFF8E2DE2)),
    CUTEST(9_000, Color(0xFF8E2DE2), Color(0xFFFF4F9A)),
    END(6_000, Color(0xFF3048F5), Color(0xFFFF4F9A))
}

private fun monthName(year: Int, month: Int, offset: Int = 0): String {
    val cal = Calendar.getInstance().apply {
        set(year, month, 1, 12, 0, 0)
        add(Calendar.MONTH, offset)
    }
    return SimpleDateFormat("MMMM", Locale.getDefault()).format(cal.time)
}

private fun count(n: Int): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(n)

/**
 * Last month as a story, full screen: pages move on by themselves, a tap goes forward (or back on
 * the left), holding pauses.
 */
@Composable
fun OurMonthStory(recap: MonthRecap, myName: String, partnerName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as? CherishApplication
    val slides = remember(recap) {
        buildList {
            add(RecapSlide.COVER)
            if (recap.totalMessages > 0) add(RecapSlide.MESSAGES)
            if (recap.loveYous > 0) add(RecapSlide.LOVE)
            if (recap.topEmojis.isNotEmpty()) add(RecapSlide.EMOJI)
            if (recap.photos + recap.videos + recap.voiceNotes > 0) add(RecapSlide.MEDIA)
            if (recap.daysTalkedCount > 0) add(RecapSlide.STREAK)
            if (recap.cutestText != null) add(RecapSlide.CUTEST)
            add(RecapSlide.END)
        }
    }
    val month = remember(recap) { monthName(recap.year, recap.month) }
    val nextMonth = remember(recap) { monthName(recap.year, recap.month, offset = 1) }

    LaunchedEffect(recap) { OurMonthRecap.markWatched(context, recap) }

    // Nothing secret stays open behind Notes
    val isDisguiseActive by (app?.securityPreferences?.isDisguiseActive ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()
    LaunchedEffect(isDisguiseActive) {
        if (isDisguiseActive) onDismiss()
    }

    var index by remember { mutableIntStateOf(0) }
    var paused by remember { mutableStateOf(false) }
    var restart by remember { mutableIntStateOf(0) }
    val progress = remember { Animatable(0f) }
    val progressFor = remember { intArrayOf(-1) }
    LaunchedEffect(index, paused, restart) {
        if (progressFor[0] != index) {
            progress.snapTo(0f)
            progressFor[0] = index
        }
        if (paused) return@LaunchedEffect
        val remaining = ((1f - progress.value) * slides[index].durationMs).toInt()
        if (remaining > 0) progress.animateTo(1f, tween(remaining, easing = LinearEasing))
        if (index < slides.lastIndex) index++
    }
    val playFromStart: () -> Unit = {
        index = 0
        progressFor[0] = -1
        restart++
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val view = LocalView.current
        SideEffect {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.let { window ->
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            }
        }
        val slide = slides[index]
        val top by animateColorAsState(slide.top, tween(700), label = "recap_top")
        val bottom by animateColorAsState(slide.bottom, tween(700), label = "recap_bottom")
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(Brush.linearGradient(listOf(top, bottom), start = Offset.Zero, end = Offset(size.width, size.height)))
                }
                .pointerInput(slides) {
                    detectTapGestures(
                        onPress = {
                            paused = true
                            tryAwaitRelease()
                            paused = false
                        },
                        onTap = { tap ->
                            if (tap.x < size.width * 0.3f) {
                                if (index > 0) index-- else playFromStart()
                            } else if (index < slides.lastIndex) {
                                index++
                            }
                        }
                    )
                }
                .testTag("our_month_story")
        ) {
            FloatingHearts()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // One bar per page, filling as it plays
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, top = 8.dp)
                ) {
                    slides.indices.forEach { i ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(CircleShape)
                                .drawBehind {
                                    drawRect(Color.White.copy(alpha = 0.3f))
                                    val filled = when {
                                        i < index -> 1f
                                        i == index -> progress.value
                                        else -> 0f
                                    }
                                    drawRect(Color.White, size = Size(size.width * filled, size.height))
                                }
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp)
                ) {
                    Text(
                        "Our $month · ${recap.year}",
                        color = Soft,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.testTag("our_month_close")) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                AnimatedContent(
                    targetState = index,
                    transitionSpec = {
                        (fadeIn(tween(420)) + scaleIn(tween(420), initialScale = 0.92f)) togetherWith fadeOut(tween(200))
                    },
                    label = "recap_page",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) { shown ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp, vertical = 12.dp)
                    ) {
                        when (slides[shown]) {
                            RecapSlide.COVER -> CoverPage(month, recap.year, myName, partnerName)
                            RecapSlide.MESSAGES -> MessagesPage(recap, month, partnerName)
                            RecapSlide.LOVE -> LovePage(recap)
                            RecapSlide.EMOJI -> EmojiPage(recap, partnerName)
                            RecapSlide.MEDIA -> MediaPage(recap)
                            RecapSlide.STREAK -> StreakPage(recap)
                            RecapSlide.CUTEST -> CutestPage(recap, partnerName)
                            RecapSlide.END -> EndPage(nextMonth, onWatchAgain = playFromStart, onClose = onDismiss)
                        }
                    }
                }
            }

            SecretWindowGuard()
        }
    }
}

@Composable
private fun CountUp(target: Int, fontSize: TextUnit, modifier: Modifier = Modifier) {
    val value = remember(target) { Animatable(0f) }
    LaunchedEffect(target) {
        value.animateTo(target.toFloat(), tween(1_400, delayMillis = 200, easing = FastOutSlowInEasing))
    }
    Text(
        text = count(value.value.roundToInt()),
        fontSize = fontSize,
        fontWeight = FontWeight.Black,
        color = Color.White,
        lineHeight = fontSize,
        modifier = modifier
    )
}

/** A gentle beat, for the big emoji. */
@Composable
private fun Modifier.beating(): Modifier {
    val beat = rememberInfiniteTransition(label = "recap_beat").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "recap_beat_scale"
    )
    return graphicsLayer {
        scaleX = beat.value
        scaleY = beat.value
    }
}

/** Pops in when the page appears. */
@Composable
private fun Modifier.popIn(delayMs: Int = 0): Modifier {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(delayMs.toLong())
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
    }
    return graphicsLayer {
        scaleX = pop.value
        scaleY = pop.value
        alpha = pop.value.coerceIn(0f, 1f)
    }
}

@Composable
private fun CoverPage(month: String, year: Int, myName: String, partnerName: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("💞", fontSize = 54.sp, modifier = Modifier.beating())
        Spacer(modifier = Modifier.height(14.dp))
        Text("OUR MONTH TOGETHER", color = Soft, fontSize = 12.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Our $month",
            fontFamily = RecapFont,
            fontSize = 66.sp,
            lineHeight = 76.sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Text("$year", color = Soft, fontSize = 18.sp, letterSpacing = 4.sp)
        Spacer(modifier = Modifier.height(26.dp))
        Text(
            "$myName ♥ $partnerName",
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(44.dp))
        Text("Tap to see our story", color = Soft, fontSize = 12.sp)
    }
}

@Composable
private fun MessagesPage(recap: MonthRecap, month: String, partnerName: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("This month we sent each other", color = Color.White, fontSize = 19.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(10.dp))
        CountUp(recap.totalMessages, 76.sp)
        Text("messages", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(30.dp))
        // Who wrote how much
        val share = remember { Animatable(0.5f) }
        val mineShare = if (recap.totalMessages > 0) recap.mine.toFloat() / recap.totalMessages else 0.5f
        LaunchedEffect(mineShare) {
            share.animateTo(mineShare.coerceIn(0.06f, 0.94f), tween(1_200, delayMillis = 400, easing = FastOutSlowInEasing))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .drawBehind {
                    drawRect(Color(0xFFFFB3D1))
                    drawRect(Color.White, size = Size(size.width * share.value, size.height))
                }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("You · ${count(recap.mine)}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.weight(1f))
            Text("$partnerName · ${count(recap.theirs)}", color = Color(0xFFFFD1E3), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(modifier = Modifier.height(28.dp))
        if (recap.busiestDay > 0) {
            InfoLine("📅", "Busiest day: $month ${recap.busiestDay}", "${count(recap.busiestDayCount)} messages")
        }
        if (recap.favouriteHour >= 0) {
            val hour = remember(recap.favouriteHour) {
                val cal = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, recap.favouriteHour); set(Calendar.MINUTE, 0) }
                SimpleDateFormat("h a", Locale.getDefault()).format(cal.time)
            }
            Spacer(modifier = Modifier.height(10.dp))
            InfoLine("🕒", "We talk most around $hour", null)
        }
    }
}

@Composable
private fun InfoLine(emoji: String, title: String, detail: String?) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = 0.14f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Text(emoji, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                if (detail != null) Text(detail, color = Soft, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LovePage(recap: MonthRecap) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("💕", fontSize = 72.sp, modifier = Modifier.beating())
        Spacer(modifier = Modifier.height(16.dp))
        Text("We said “I love you”", color = Color.White, fontSize = 21.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(6.dp))
        CountUp(recap.loveYous, 92.sp)
        Text(if (recap.loveYous == 1) "time" else "times", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(22.dp))
        Text(
            if (recap.loveYous >= recap.daysInMonth) "That's more than once a day 🥹" else "and meant it every single time",
            color = Soft,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmojiPage(recap: MonthRecap, partnerName: String) {
    val first = recap.topEmojis.first()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Our favourite emoji", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(18.dp))
        Text(first.emoji, fontSize = 110.sp, lineHeight = 120.sp, modifier = Modifier.popIn(150))
        Text("used ${count(first.count)} times", color = Soft, fontSize = 15.sp)
        if (recap.topEmojis.size > 1) {
            Spacer(modifier = Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
                recap.topEmojis.drop(1).forEachIndexed { i, item ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.popIn(450 + i * 150)) {
                        Text(item.emoji, fontSize = 50.sp)
                        Text(count(item.count), color = Soft, fontSize = 13.sp)
                    }
                }
            }
        }
        if (recap.myEmoji != null || recap.partnerEmoji != null) {
            Spacer(modifier = Modifier.height(30.dp))
            Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.16f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    recap.myEmoji?.let { Text("Yours $it", color = Color.White, fontSize = 15.sp) }
                    if (recap.myEmoji != null && recap.partnerEmoji != null) {
                        Text("   ·   ", color = Soft, fontSize = 15.sp)
                    }
                    recap.partnerEmoji?.let { Text("$partnerName's $it", color = Color.White, fontSize = 15.sp) }
                }
            }
        }
    }
}

@Composable
private fun MediaPage(recap: MonthRecap) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Moments we shared", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(22.dp))
        if (recap.photoPreviews.isNotEmpty()) {
            PhotoCollage(recap.photoPreviews)
            Spacer(modifier = Modifier.height(26.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
            if (recap.photos > 0) MediaStat("📸", recap.photos, if (recap.photos == 1) "photo" else "photos")
            if (recap.videos > 0) MediaStat("🎬", recap.videos, if (recap.videos == 1) "video" else "videos")
            if (recap.voiceNotes > 0) MediaStat("🎙️", recap.voiceNotes, if (recap.voiceNotes == 1) "voice note" else "voice notes")
        }
    }
}

@Composable
private fun MediaStat(emoji: String, value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(emoji, fontSize = 26.sp)
        CountUp(value, 28.sp)
        Text(label, color = Soft, fontSize = 12.sp)
    }
}

/** Up to four photos, tilted a little like prints on a table. */
@Composable
private fun PhotoCollage(previews: List<String>) {
    val tilts = listOf(-5f, 4f, 3f, -4f)
    Column(verticalArrangement = Arrangement.spacedBy((-10).dp), horizontalAlignment = Alignment.CenterHorizontally) {
        previews.take(4).chunked(2).forEachIndexed { row, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                pair.forEachIndexed { column, url ->
                    val i = row * 2 + column
                    val model = remember(url) {
                        if (url.startsWith("data:image")) {
                            runCatching { Base64.decode(url.substringAfter("base64,"), Base64.DEFAULT) }.getOrDefault(url)
                        } else {
                            url
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(if (previews.size == 1) 190.dp else 128.dp)
                            .popIn(120 + i * 140)
                            .graphicsLayer { rotationZ = tilts[i] }
                    ) {
                        AsyncImage(
                            model = model,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .padding(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreakPage(recap: MonthRecap) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🔥", fontSize = 60.sp, modifier = Modifier.beating())
        Spacer(modifier = Modifier.height(8.dp))
        Text("Our longest streak", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        CountUp(recap.longestStreak, 84.sp)
        Text(if (recap.longestStreak == 1) "day" else "days in a row", color = Color.White, fontSize = 19.sp)
        Spacer(modifier = Modifier.height(24.dp))
        // The month: a dot for each day we both wrote
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            recap.daysWeTalked.chunked(7).forEachIndexed { week, days ->
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    days.forEachIndexed { d, talked ->
                        val day = week * 7 + d + 1
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(26.dp)
                                .popIn(200 + day * 25)
                                .background(if (talked) Color.White else Color.White.copy(alpha = 0.16f), CircleShape)
                        ) {
                            Text(
                                "$day",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (talked) HeartbeatPink else Soft
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            "We talked on ${recap.daysTalkedCount} of ${recap.daysInMonth} days",
            color = Soft,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CutestPage(recap: MonthRecap, partnerName: String) {
    val text = recap.cutestText ?: return
    val size = when {
        text.length <= 50 -> 26.sp
        text.length <= 120 -> 22.sp
        text.length <= 260 -> 19.sp
        else -> 16.sp
    }
    val date = remember(recap.cutestAt) {
        SimpleDateFormat("MMMM d · h:mm a", Locale.getDefault()).format(Date(recap.cutestAt))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("THE CUTEST MESSAGE", color = Soft, fontSize = 12.sp, letterSpacing = 3.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color.White.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
            modifier = Modifier
                .fillMaxWidth()
                .popIn(100)
        ) {
            Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
                Text("“", fontFamily = FontFamily.Serif, fontSize = 58.sp, lineHeight = 58.sp, color = Color.White.copy(alpha = 0.8f))
                Text(
                    text = text,
                    fontFamily = FontFamily.Serif,
                    fontStyle = FontStyle.Italic,
                    fontSize = size,
                    lineHeight = size * 1.35f,
                    color = Color.White,
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "— ${if (recap.cutestIsMine) "You" else partnerName}",
                    fontFamily = RecapFont,
                    fontSize = 32.sp,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.End)
                )
                Text(date, color = Soft, fontSize = 12.sp, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

@Composable
private fun EndPage(nextMonth: String, onWatchAgain: () -> Unit, onClose: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("❤️", fontSize = 52.sp, modifier = Modifier.beating())
        Spacer(modifier = Modifier.height(10.dp))
        Text("Here's to", color = Soft, fontSize = 18.sp)
        Text(nextMonth, fontFamily = RecapFont, fontSize = 64.sp, lineHeight = 74.sp, color = Color.White, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            "Every message, every little moment.\nThank you for this month together.",
            color = Soft,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(34.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onWatchAgain,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                modifier = Modifier.testTag("our_month_again")
            ) { Text("Watch again") }
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = HeartbeatPink),
                modifier = Modifier.testTag("our_month_done")
            ) { Text("Close", fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Soft white hearts drifting up behind the pages. */
@Composable
private fun FloatingHearts() {
    val clock = rememberInfiniteTransition(label = "recap_hearts").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart),
        label = "recap_hearts_rise"
    )
    val heart = remember {
        Path().apply {
            moveTo(0f, 0.4f)
            cubicTo(-0.6f, 0f, -0.5f, -0.55f, 0f, -0.25f)
            cubicTo(0.5f, -0.55f, 0.6f, 0f, 0f, 0.4f)
            close()
        }
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val f = clock.value
        RecapHearts.forEach { h ->
            val t = (f * h.speed + h.phase) % 1f
            val fade = (t / 0.15f).coerceAtMost(1f) * ((1f - t) / 0.3f).coerceAtMost(1f)
            val x = h.x * size.width + sin(2f * PI.toFloat() * (t * 1.5f + h.phase)) * 14.dp.toPx()
            val y = size.height * (1.05f - 1.15f * t)
            val px = h.size.dp.toPx()
            translate(x, y) {
                scale(px, px, pivot = Offset.Zero) {
                    drawPath(heart, Color.White, alpha = (h.alpha * fade).coerceIn(0f, 1f))
                }
            }
        }
    }
}

private class RecapHeart(val x: Float, val size: Float, val phase: Float, val speed: Float, val alpha: Float)

private val RecapHearts = listOf(
    RecapHeart(0.08f, 18f, 0.00f, 1f, 0.16f),
    RecapHeart(0.22f, 12f, 0.35f, 2f, 0.12f),
    RecapHeart(0.38f, 22f, 0.62f, 1f, 0.10f),
    RecapHeart(0.55f, 14f, 0.18f, 2f, 0.14f),
    RecapHeart(0.70f, 26f, 0.80f, 1f, 0.09f),
    RecapHeart(0.86f, 16f, 0.47f, 2f, 0.15f),
    RecapHeart(0.94f, 11f, 0.05f, 1f, 0.12f),
    RecapHeart(0.30f, 9f, 0.90f, 2f, 0.14f)
)

/** In the chat, in the first days of a month: last month's story is ready. */
@Composable
fun OurMonthInviteCard(recap: MonthRecap, onWatch: () -> Unit, onDismiss: () -> Unit) {
    val month = remember(recap) { monthName(recap.year, recap.month) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 68.dp, start = 14.dp, end = 14.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Transparent,
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("our_month_invite")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Brush.horizontalGradient(listOf(HeartbeatPink, HeartbeatBlue)))
                    .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)
            ) {
                Text("✨", fontSize = 26.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Our $month is ready",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        "Your month together, as a little story",
                        fontSize = 12.5.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onWatch() }
                        .testTag("our_month_watch")
                ) {
                    Text(
                        "Watch",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HeartbeatPink,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** On Love & Us: watch last month's story any time. */
@Composable
fun OurMonthCard(myId: String, myName: String, partnerName: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val range = remember { OurMonthRecap.lastMonth() }
    val month = remember(range) { monthName(range.year, range.month) }
    var isNew by remember { mutableStateOf(OurMonthRecap.isNew(context)) }
    var isLoading by remember { mutableStateOf(false) }
    var story by remember { mutableStateOf<MonthRecap?>(null) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("our_month_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isLoading) {
                    isLoading = true
                    scope.launch {
                        val recap = OurMonthRecap.load(context, myId, range)
                        isLoading = false
                        when {
                            recap == null -> Toast.makeText(context, "Couldn't load our month. Check your connection and try again.", Toast.LENGTH_LONG).show()
                            recap.totalMessages == 0 -> Toast.makeText(context, "No messages in $month to look back on.", Toast.LENGTH_SHORT).show()
                            else -> story = recap
                        }
                    }
                }
                .padding(16.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(HeartbeatPink, HeartbeatBlue)))
            ) {
                Icon(Icons.Default.AutoStories, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Our $month", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    if (isNew) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(6.dp), color = HeartbeatPink) {
                            Text(
                                "NEW",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    "Our month together as a little story",
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = HeartbeatPink)
            } else {
                Icon(Icons.Default.PlayCircle, contentDescription = "Watch", tint = HeartbeatPink, modifier = Modifier.size(32.dp))
            }
        }
    }

    story?.let { recap ->
        OurMonthStory(
            recap = recap,
            myName = myName,
            partnerName = partnerName,
            onDismiss = {
                story = null
                isNew = OurMonthRecap.isNew(context)
            }
        )
    }
}
