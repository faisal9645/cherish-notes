package com.example.ui.home

import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.chat.FullScreenMediaViewer
import com.example.ui.components.AvatarView
import com.example.ui.theme.OnlineGreen
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * One of us on the Love & Us tab.
 * [birthday] is "yyyy-MM-dd" (null until set); [status] replaces online / last seen when set
 * (e.g. the partner's quiet time); [batteryLevel] is null when it shouldn't show.
 */
data class LovePerson(
    val id: String,
    val name: String,
    val photoUrl: String?,
    val birthday: String?,
    val isOnline: Boolean,
    val lastSeen: Long,
    val status: String? = null,
    val batteryLevel: Int? = null,
    val isCharging: Boolean = false,
    // Only used to tell which of us is which side
    val email: String? = null,
    val timeZone: String? = null
)

/**
 * Partner's local time (small): if you're ever in different time zones only, returns
 * "Her time: 11:40 PM" (or "His time: 11:40 PM") to show under her photo.
 * If in the same time zone, returns null so nothing is displayed.
 */
fun formatPartnerLocalTimeIfDifferent(
    partnerTimeZoneId: String?,
    myTimeZoneId: String? = null,
    now: Long = System.currentTimeMillis(),
    label: String = "Her time"
): String? {
    val cleanPartnerTz = partnerTimeZoneId?.trim()?.ifBlank { null } ?: return null
    val partnerZone = try {
        TimeZone.getTimeZone(cleanPartnerTz)
    } catch (_: Exception) {
        return null
    }
    val myZone = try {
        if (!myTimeZoneId.isNullOrBlank()) TimeZone.getTimeZone(myTimeZoneId) else TimeZone.getDefault()
    } catch (_: Exception) {
        TimeZone.getDefault()
    }

    val myOffset = myZone.getOffset(now)
    val partnerOffset = partnerZone.getOffset(now)
    // ONLY show if you're ever in different time zones!
    if (myOffset == partnerOffset) {
        return null
    }

    val formatter = SimpleDateFormat("h:mm a", Locale.US).apply {
        timeZone = partnerZone
    }
    val timeFormatted = formatter.format(Date(now))
    return "$label: $timeFormatted"
}

private const val DAY_MS = 86_400_000L

// Her side is pink, on the left; his blue, on the right (like the heartbeat button). She and he are
// recognised by name, id or email, so it's right whatever way each of us signed in (a Google
// sign-in gives a random id).
private val HER_MARKERS = listOf("shali")
private val HIS_MARKERS = listOf("faisal")

private fun LovePerson.isMarked(markers: List<String>): Boolean {
    val text = listOfNotNull(id, name, email).joinToString(" ").lowercase(Locale.ROOT)
    return markers.any { it in text }
}

/**
 * Her first (pink, left), him second (blue, right), the same on both phones. Only when neither
 * name, id nor email tells, a stable order by user id decides.
 */
/** Her user id (the pink side), the same on both phones. */
fun herUserId(me: LovePerson, partner: LovePerson): String = herFirst(me, partner).first.id

private fun herFirst(me: LovePerson, partner: LovePerson): Pair<LovePerson, LovePerson> {
    val meHer = me.isMarked(HER_MARKERS)
    val partnerHer = partner.isMarked(HER_MARKERS)
    if (meHer != partnerHer) return if (meHer) me to partner else partner to me
    val meHim = me.isMarked(HIS_MARKERS)
    val partnerHim = partner.isMarked(HIS_MARKERS)
    if (meHim != partnerHim) return if (meHim) partner to me else me to partner
    return if (me.id >= partner.id) me to partner else partner to me
}

/** The couple card's colours: white by day, deep navy by night, the same pink and blue on both. */
private class CardColors(
    val isLight: Boolean,
    val background: Brush,
    val border: Color,
    val ink: Color, // main text and icons
    val softInk: Color, // the note, small lines
    val pill: Color, // pill fill
    val dimInk: Color, // "Offline"
    val hairline: Color, // thin dividers
    val blueInk: Color // blue that reads well on this card
)

// Blush pink at the top left, through white, to a pale blue at the bottom right
private val DayCard = CardColors(
    isLight = true,
    background = SolidColor(Color(0xFFFEFEFF)),
    border = HeartbeatBlue.copy(alpha = 0.12f),
    ink = Color(0xFF1E1B4B),
    softInk = Color(0xFF5B5F7E),
    pill = Color.White,
    dimInk = Color(0xFF6B7280),
    hairline = Color(0xFF1E1B4B).copy(alpha = 0.10f),
    blueInk = HeartbeatBlue
)

// Night: a deep navy from the app's own blue family (not grey, not neon); the rings, names and
// decorations stay the same pink and blue as by day
private val NightCard = CardColors(
    isLight = false,
    background = Brush.verticalGradient(listOf(Color(0xFF111B45), Color(0xFF0B1233))),
    border = HeartbeatBlue.copy(alpha = 0.28f),
    ink = Color(0xFFF3F4FA),
    softInk = Color(0xFFBAC2E8),
    pill = Color.White.copy(alpha = 0.08f),
    dimInk = Color(0xFFA6ADCB),
    hairline = Color.White.copy(alpha = 0.12f),
    blueInk = HeartbeatBlue
)

/** The navy behind the night card's hearts (their separating edge matches it). */
private val NightCardEdge = Color(0xFF0E1638)

private val LocalCardColors = staticCompositionLocalOf { DayCard }

/** The couple's names: a handwritten signature. */
private val SignatureFont = FontFamily(Font(R.font.great_vibes_regular))

private val AVATAR_SIZE = 96.dp
private val RING_WIDTH = 2.5.dp
/**
 * Space between the two photo boxes. Negative: the boxes' 4dp margins overlap, leaving the rings
 * about 4dp apart, with the two hearts over the join.
 */
private val AVATAR_GAP = (-4).dp

private fun parseDay(date: String?): Calendar? {
    val clean = date?.trim()?.ifBlank { null } ?: return null
    return try {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(clean) ?: return null
        Calendar.getInstance().apply { time = parsed }
    } catch (_: Exception) {
        null
    }
}

/** Full years at [now]. */
private fun ageAt(born: Calendar, now: Long): Int {
    val today = Calendar.getInstance().apply { timeInMillis = now }
    var age = today.get(Calendar.YEAR) - born.get(Calendar.YEAR)
    val hadBirthday = today.get(Calendar.MONTH) > born.get(Calendar.MONTH) ||
        (today.get(Calendar.MONTH) == born.get(Calendar.MONTH) &&
            today.get(Calendar.DAY_OF_MONTH) >= born.get(Calendar.DAY_OF_MONTH))
    if (!hadBirthday) age--
    return age.coerceAtLeast(0)
}

/** Days until the next birthday (0 = today); Feb 29 counts on Feb 28 in other years. */
private fun daysToBirthday(born: Calendar, now: Long): Int {
    val today = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    fun birthdayIn(year: Int) = (today.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, born.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, minOf(born.get(Calendar.DAY_OF_MONTH), getActualMaximum(Calendar.DAY_OF_MONTH)))
    }
    var next = birthdayIn(today.get(Calendar.YEAR))
    if (next.before(today)) next = birthdayIn(today.get(Calendar.YEAR) + 1)
    return ((next.timeInMillis - today.timeInMillis) / DAY_MS.toDouble()).roundToInt()
}

private fun formatNumber(value: Long): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

/** A heart shape about [size] wide, centred on ([cx], [cy]). */
private fun heartPath(cx: Float, cy: Float, size: Float): Path = Path().apply {
    val h = size * 0.92f
    moveTo(cx, cy + h * 0.38f)
    cubicTo(cx - size * 0.62f, cy - h * 0.02f, cx - size * 0.36f, cy - h * 0.62f, cx, cy - h * 0.2f)
    cubicTo(cx + size * 0.36f, cy - h * 0.62f, cx + size * 0.62f, cy - h * 0.02f, cx, cy + h * 0.38f)
    close()
}

/** An elegant calligraphic heart shape with graceful curves and flourishes for left / right sides. */
private fun calligraphicHeartPath(cx: Float, cy: Float, size: Float, isLeft: Boolean): Path = Path().apply {
    val s = size
    if (isLeft) {
        moveTo(cx - 0.08f * s, cy - 0.16f * s)
        cubicTo(cx - 0.26f * s, cy - 0.44f * s, cx - 0.56f * s, cy - 0.36f * s, cx - 0.52f * s, cy - 0.08f * s)
        cubicTo(cx - 0.48f * s, cy + 0.18f * s, cx - 0.22f * s, cy + 0.38f * s, cx, cy + 0.52f * s)
        cubicTo(cx + 0.22f * s, cy + 0.38f * s, cx + 0.48f * s, cy + 0.18f * s, cx + 0.52f * s, cy - 0.08f * s)
        cubicTo(cx + 0.56f * s, cy - 0.36f * s, cx + 0.26f * s, cy - 0.44f * s, cx + 0.06f * s, cy - 0.22f * s)
        cubicTo(cx - 0.02f * s, cy - 0.12f * s, cx - 0.04f * s, cy - 0.02f * s, cx, cy + 0.10f * s)
        cubicTo(cx + 0.04f * s, cy + 0.20f * s, cx + 0.14f * s, cy + 0.26f * s, cx + 0.24f * s, cy + 0.22f * s)
    } else {
        moveTo(cx + 0.08f * s, cy - 0.16f * s)
        cubicTo(cx + 0.26f * s, cy - 0.44f * s, cx + 0.56f * s, cy - 0.36f * s, cx + 0.52f * s, cy - 0.08f * s)
        cubicTo(cx + 0.48f * s, cy + 0.18f * s, cx + 0.22f * s, cy + 0.38f * s, cx, cy + 0.52f * s)
        cubicTo(cx - 0.22f * s, cy + 0.38f * s, cx - 0.48f * s, cy + 0.18f * s, cx - 0.52f * s, cy - 0.08f * s)
        cubicTo(cx - 0.56f * s, cy - 0.36f * s, cx - 0.26f * s, cy - 0.44f * s, cx - 0.06f * s, cy - 0.22f * s)
        cubicTo(cx + 0.02f * s, cy - 0.12f * s, cx + 0.04f * s, cy - 0.02f * s, cx, cy + 0.10f * s)
        cubicTo(cx - 0.04f * s, cy + 0.20f * s, cx - 0.14f * s, cy + 0.26f * s, cx - 0.24f * s, cy + 0.22f * s)
    }
}

/**
 * Both of us. Today's date, our names as a handwritten signature and the partner's note; our
 * photos close together (pink ring on the left, blue on the right) with two hearts between them;
 * under each: online / offline and battery, age and days of life, and the birthday countdown;
 * our places (gallery, memories, dates, notes); since when and how many days together.
 *
 * Soft and calm: the same pastel card in day and night mode, no glows. She is always on the
 * left in pink and he on the right in blue, on both phones (see [herFirst]).
 */
@Composable
fun BothOfUsCard(
    me: LovePerson,
    partner: LovePerson,
    partnerNote: String?,
    togetherSince: String?,
    isVisible: Boolean,
    onSetBirthday: (userId: String, date: String) -> Unit,
    onOpenChat: () -> Unit,
    onSetTogetherSince: (date: String) -> Unit,
    onOpenGallery: () -> Unit = {},
    onOpenMemories: () -> Unit = {},
    onOpenDates: () -> Unit = {},
    onOpenNotes: () -> Unit = {}
) {
    // Everything shown is by the day: a minute tick is plenty (and only while on screen)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isVisible) {
        while (isVisible) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L)
        }
    }
    var editing by remember { mutableStateOf<LovePerson?>(null) }
    var editingTogether by remember { mutableStateOf(false) }
    var viewingPhotoUrl by remember { mutableStateOf<String?>(null) }

    // Same sides on both phones: her on the left in pink, him on the right in blue
    val (left, right) = remember(me, partner) { herFirst(me, partner) }
    val pink = HeartbeatPink
    // The same soft pastel card in day and night mode (same rings, text and colours)
    val cardColors = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) NightCard else DayCard
    val blue = cardColors.blueInk

    // The two hearts beat gently, only while the tab is on screen (read while drawing only)
    val still = remember { mutableFloatStateOf(0f) }
    val beat: State<Float> = if (isVisible) {
        rememberInfiniteTransition(label = "couple_hearts").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "couple_heart_beat"
        )
    } else {
        still
    }

    // The background hearts float very slowly (one gentle clock, read only while drawing)
    val drift: State<Float> = if (isVisible) {
        rememberInfiniteTransition(label = "couple_background").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart),
            label = "couple_background_drift"
        )
    } else {
        still
    }
    val flight: State<Float> = if (isVisible) {
        rememberInfiniteTransition(label = "couple_flying_hearts").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(18_000, easing = LinearEasing), RepeatMode.Restart),
            label = "couple_flying_hearts_rise"
        )
    } else {
        still
    }

    val cardShape = RoundedCornerShape(24.dp)
    CompositionLocalProvider(LocalCardColors provides cardColors) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(if (cardColors.isLight) 2.dp else 0.dp, cardShape)
                .clip(cardShape)
                .background(cardColors.background)
                .loveBackground(HeartbeatPink, HeartbeatBlue, drift, flight, strength = if (cardColors.isLight) 1f else 1.6f)
                .border(1.dp, cardColors.border, cardShape)
                .testTag("both_of_us_card")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Today's date in a small pill
                val today = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date(now))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(cardColors.pill)
                        .border(1.dp, cardColors.hairline, RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("both_of_us_today")
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = cardColors.softInk, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = today, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = cardColors.ink, maxLines = 1)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Our names, a handwritten signature: "shali & faisal" in pink and blue
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Filled.FavoriteBorder, contentDescription = null, tint = pink, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = pink)) { append(left.name.lowercase()) }
                            append("  &  ")
                            withStyle(SpanStyle(color = blue)) { append(right.name.lowercase()) }
                        },
                        style = TextStyle(
                            fontFamily = SignatureFont,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Normal,
                            color = cardColors.ink,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Filled.FavoriteBorder, contentDescription = null, tint = blue, modifier = Modifier.size(15.dp))
                }

                // The note, under the names
                Text(
                    text = "“${partnerNote?.ifBlank { null } ?: "Loving every moment with you ✨"}”",
                    fontSize = 14.sp,
                    color = cardColors.softInk,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // The two of us close together with the hearts between; details under each
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    // The details get columns this wide, centred under the photos
                    val column = (maxWidth / 2).coerceAtMost(168.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                            // Left static heart (based on left color: pink)
                            StaticSideHeart(
                                color = pink,
                                isLeft = true,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 6.dp)
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AVATAR_GAP)
                            ) {
                                RingedAvatar(left, pink) { viewingPhotoUrl = it }
                                RingedAvatar(right, blue) { viewingPhotoUrl = it }
                            }
                            TwinHearts(pink, blue, beat)

                            // Right static heart (based on right color: blue)
                            StaticSideHeart(
                                color = blue,
                                isLeft = false,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Under each photo: online / battery, age and days, birthday
                        Row(verticalAlignment = Alignment.Top) {
                            PersonDetails(
                                person = left,
                                color = pink,
                                now = now,
                                modifier = Modifier.width(column),
                                isPartner = (left.id == partner.id),
                                myTimeZone = me.timeZone
                            ) { editing = left }
                            PersonDetails(
                                person = right,
                                color = blue,
                                now = now,
                                modifier = Modifier.width(column),
                                isPartner = (right.id == partner.id),
                                myTimeZone = me.timeZone
                            ) { editing = right }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(cardColors.hairline)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Our places: gallery, memories, dates and notes
                LovePlaces(
                    pink = pink,
                    blue = blue,
                    onOpenGallery = onOpenGallery,
                    onOpenMemories = onOpenMemories,
                    onOpenDates = onOpenDates,
                    onOpenNotes = onOpenNotes
                )

                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(cardColors.hairline)
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Since when (left) and how many days together (the blue pill); either one sets or
                // changes the date. Until it's set, the pill is Chat Now.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val togetherDays = com.example.ui.chat.LoveDates.daysTogether(togetherSince, now)
                    val sinceText = parseDay(togetherSince)?.let { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(it.time) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { editingTogether = true }
                            .padding(vertical = 6.dp, horizontal = 2.dp)
                            .testTag("days_together")
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(pink.copy(alpha = 0.14f))
                        ) {
                            Icon(Icons.Filled.Favorite, contentDescription = null, tint = pink, modifier = Modifier.size(17.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (togetherDays != null && sinceText != null) "Since $sinceText" else "Set days together",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = cardColors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(blue.copy(alpha = if (cardColors.isLight) 0.12f else 0.35f))
                            .clickable { if (togetherDays != null) editingTogether = true else onOpenChat() }
                            .padding(start = 14.dp, end = if (togetherDays != null) 14.dp else 10.dp, top = 8.dp, bottom = 8.dp)
                            .testTag("both_of_us_days_count")
                    ) {
                        if (togetherDays != null) {
                            Text(
                                text = when (togetherDays) {
                                    0 -> "First day together"
                                    1 -> "1 day together"
                                    else -> "${formatNumber(togetherDays.toLong())} days together"
                                },
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (cardColors.isLight) blue else Color.White,
                                maxLines = 1
                            )
                        } else {
                            Text(text = "Chat Now", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = if (cardColors.isLight) blue else Color.White)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = if (cardColors.isLight) blue else Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    // Full photo viewer when tapping a photo
    viewingPhotoUrl?.let { url ->
        val allUrls = remember(left.photoUrl, right.photoUrl) {
            listOfNotNull(left.photoUrl?.takeIf { it.isNotBlank() }, right.photoUrl?.takeIf { it.isNotBlank() }).distinct()
        }
        FullScreenMediaViewer(mediaUrl = url, allMediaUrls = allUrls, onDismiss = { viewingPhotoUrl = null })
    }

    editing?.let { person ->
        DayPickerDialog(
            title = "${person.name}'s birthday",
            initial = person.birthday,
            onPick = { day -> onSetBirthday(person.id, day) },
            onDismiss = { editing = null }
        )
    }
    if (editingTogether) {
        DayPickerDialog(
            title = "Together since",
            initial = togetherSince,
            onPick = onSetTogetherSince,
            onDismiss = { editingTogether = false }
        )
    }
}

/** A background heart: where (fractions of the card), how big, how strong, and its style. */
private class BgHeart(
    val x: Float,
    val y: Float,
    val sizeDp: Float,
    val alpha: Float,
    val outline: Boolean,
    val glow: Boolean,
    val tilt: Float,
    val phase: Float
)

// Around the edges and corners; the middle (photos and text) stays clean
private val BgHearts = listOf(
    BgHeart(0.07f, 0.06f, 18f, 0.13f, outline = false, glow = true, tilt = -12f, phase = 0.00f),
    BgHeart(0.19f, 0.03f, 9f, 0.30f, outline = true, glow = false, tilt = 10f, phase = 0.30f),
    BgHeart(0.04f, 0.22f, 10f, 0.10f, outline = false, glow = false, tilt = -6f, phase = 0.60f),
    BgHeart(0.93f, 0.07f, 20f, 0.11f, outline = false, glow = true, tilt = 14f, phase = 0.20f),
    BgHeart(0.80f, 0.035f, 8f, 0.30f, outline = true, glow = false, tilt = -8f, phase = 0.50f),
    BgHeart(0.965f, 0.25f, 10f, 0.10f, outline = false, glow = false, tilt = 6f, phase = 0.80f),
    BgHeart(0.035f, 0.43f, 13f, 0.22f, outline = true, glow = false, tilt = -10f, phase = 0.15f),
    BgHeart(0.965f, 0.46f, 14f, 0.22f, outline = true, glow = false, tilt = 12f, phase = 0.45f),
    BgHeart(0.05f, 0.64f, 11f, 0.10f, outline = false, glow = true, tilt = 8f, phase = 0.70f),
    BgHeart(0.95f, 0.66f, 11f, 0.10f, outline = false, glow = true, tilt = -8f, phase = 0.35f),
    BgHeart(0.04f, 0.90f, 14f, 0.08f, outline = false, glow = false, tilt = -14f, phase = 0.90f),
    BgHeart(0.96f, 0.91f, 15f, 0.08f, outline = false, glow = false, tilt = 12f, phase = 0.25f)
)

/** A heart flying up a side of the card: its lane, size, start, sway (dp) and strength. */
private class FlyingHeart(val x: Float, val sizeDp: Float, val phase: Float, val sway: Float, val alpha: Float)

private val FlyingHearts = listOf(
    FlyingHeart(0.06f, 13f, 0.00f, 6f, 0.55f),
    FlyingHeart(0.15f, 9f, 0.21f, 5f, 0.45f),
    FlyingHeart(0.04f, 16f, 0.43f, 7f, 0.40f),
    FlyingHeart(0.19f, 10f, 0.64f, 5f, 0.50f),
    FlyingHeart(0.10f, 8f, 0.84f, 4f, 0.45f)
)

// Same lanes on the right, starting at other moments so the two sides don't mirror each other
private val FlyingHeartsRight = listOf(
    FlyingHeart(0.07f, 12f, 0.11f, 6f, 0.55f),
    FlyingHeart(0.16f, 9f, 0.32f, 5f, 0.45f),
    FlyingHeart(0.04f, 15f, 0.53f, 7f, 0.40f),
    FlyingHeart(0.19f, 10f, 0.74f, 5f, 0.50f),
    FlyingHeart(0.11f, 8f, 0.93f, 4f, 0.45f)
)

// Tiny dots (x, y, radius dp, alpha) and four-point sparkles (x, y, alpha)
private val BgDots = listOf(
    floatArrayOf(0.13f, 0.12f, 1.6f, 0.30f), floatArrayOf(0.26f, 0.08f, 1.2f, 0.22f),
    floatArrayOf(0.86f, 0.14f, 1.6f, 0.30f), floatArrayOf(0.73f, 0.07f, 1.2f, 0.22f),
    floatArrayOf(0.02f, 0.34f, 1.4f, 0.25f), floatArrayOf(0.98f, 0.36f, 1.4f, 0.25f),
    floatArrayOf(0.09f, 0.54f, 1.2f, 0.20f), floatArrayOf(0.91f, 0.56f, 1.2f, 0.20f),
    floatArrayOf(0.03f, 0.78f, 1.5f, 0.22f), floatArrayOf(0.97f, 0.79f, 1.5f, 0.22f),
    floatArrayOf(0.12f, 0.95f, 1.3f, 0.25f), floatArrayOf(0.88f, 0.96f, 1.3f, 0.25f)
)
private val BgSparkles = listOf(
    floatArrayOf(0.30f, 0.045f, 0.30f), floatArrayOf(0.69f, 0.05f, 0.30f),
    floatArrayOf(0.025f, 0.13f, 0.25f), floatArrayOf(0.975f, 0.15f, 0.25f),
    floatArrayOf(0.08f, 0.82f, 0.22f), floatArrayOf(0.92f, 0.84f, 0.22f)
)

/**
 * The card's background on white: pink decorations on the left (her side), the app's blue on the
 * right, at different strengths. Hearts around the edges (a few with a soft glow), tiny dots and
 * sparkles, and soft curves at the top and bottom running from pink into blue; the middle stays
 * clean. Built once per size; each frame only lets the hearts float a little.
 */
private fun Modifier.loveBackground(
    pink: Color,
    blue: Color,
    drift: State<Float>,
    flight: State<Float>,
    strength: Float = 1f
) = drawWithCache {
    val w = size.width
    val h = size.height
    val unitHeart = heartPath(0f, 0f, 1f)
    // Faint corner tints of the same blue
    val topTint = Brush.radialGradient(listOf(pink.copy(alpha = 0.06f), Color.Transparent), center = Offset(0f, 0f), radius = w * 0.6f)
    val bottomTint = Brush.radialGradient(listOf(blue.copy(alpha = 0.05f), Color.Transparent), center = Offset(w, h), radius = w * 0.6f)
    // Soft flowing curves near the top and bottom edges
    val topCurve = Path().apply {
        moveTo(-0.05f * w, 0.12f * h)
        cubicTo(0.25f * w, -0.02f * h, 0.62f * w, 0.17f * h, 1.05f * w, 0.03f * h)
    }
    val topCurve2 = Path().apply {
        moveTo(-0.05f * w, 0.17f * h)
        cubicTo(0.3f * w, 0.06f * h, 0.66f * w, 0.22f * h, 1.05f * w, 0.09f * h)
    }
    val bottomCurve = Path().apply {
        moveTo(-0.05f * w, 0.88f * h)
        cubicTo(0.32f * w, 0.99f * h, 0.66f * w, 0.80f * h, 1.05f * w, 0.93f * h)
    }
    // Curves run from pink on the left into blue on the right
    val curveBrush = Brush.horizontalGradient(listOf(pink, blue), startX = 0f, endX = w)
    fun sideColor(x: Float) = if (x < 0.5f) pink else blue
    val soft = Stroke(width = 10.dp.toPx())
    val fine = Stroke(width = 1.2.dp.toPx())
    val sparkle = Stroke(width = 1.dp.toPx())
    onDrawBehind {
        val t = drift.value
        drawCircle(topTint, radius = w * 0.6f, center = Offset(0f, 0f))
        drawCircle(bottomTint, radius = w * 0.6f, center = Offset(w, h))

        drawPath(topCurve, curveBrush, alpha = 0.035f * strength, style = soft)
        drawPath(topCurve, curveBrush, alpha = 0.12f * strength, style = fine)
        drawPath(topCurve2, curveBrush, alpha = 0.07f * strength, style = fine)
        drawPath(bottomCurve, curveBrush, alpha = 0.035f * strength, style = soft)
        drawPath(bottomCurve, curveBrush, alpha = 0.12f * strength, style = fine)

        // Static heart on left side based on left color (pink)
        val leftStaticPx = 18.dp.toPx()
        val leftStaticCx = 0.06f * w
        val leftStaticCy = 0.26f * h
        withTransform({
            translate(leftStaticCx, leftStaticCy)
            rotate(-14f, pivot = Offset.Zero)
            scale(leftStaticPx, leftStaticPx, pivot = Offset.Zero)
        }) {
            drawPath(unitHeart, pink.copy(alpha = 0.22f * strength))
            drawPath(unitHeart, pink, alpha = 0.9f * strength, style = Stroke(width = 1.4.dp.toPx() / leftStaticPx))
        }

        // Static heart on right side based on right color (blue)
        val rightStaticPx = 18.dp.toPx()
        val rightStaticCx = 0.94f * w
        val rightStaticCy = 0.26f * h
        withTransform({
            translate(rightStaticCx, rightStaticCy)
            rotate(14f, pivot = Offset.Zero)
            scale(rightStaticPx, rightStaticPx, pivot = Offset.Zero)
        }) {
            drawPath(unitHeart, blue.copy(alpha = 0.22f * strength))
            drawPath(unitHeart, blue, alpha = 0.9f * strength, style = Stroke(width = 1.4.dp.toPx() / rightStaticPx))
        }

        BgHearts.forEach { heart ->
            val color = sideColor(heart.x)
            val px = heart.sizeDp.dp.toPx()
            val cx = heart.x * w
            val cy = heart.y * h + sin(2f * PI.toFloat() * (t + heart.phase)) * 2.5.dp.toPx()
            if (heart.glow) {
                drawCircle(
                    Brush.radialGradient(listOf(color.copy(alpha = 0.10f), Color.Transparent), center = Offset(cx, cy), radius = px * 1.4f),
                    radius = px * 1.4f,
                    center = Offset(cx, cy)
                )
            }
            withTransform({
                translate(cx, cy)
                rotate(heart.tilt, pivot = Offset.Zero)
                scale(px, px, pivot = Offset.Zero)
            }) {
                if (heart.outline) {
                    drawPath(unitHeart, color, alpha = (heart.alpha * strength).coerceAtMost(1f), style = Stroke(width = 1.2.dp.toPx() / px))
                } else {
                    drawPath(unitHeart, color, alpha = (heart.alpha * strength).coerceAtMost(1f))
                }
            }
        }
        BgDots.forEach { d -> drawCircle(sideColor(d[0]), radius = d[2].dp.toPx(), center = Offset(d[0] * w, d[1] * h), alpha = (d[3] * strength).coerceAtMost(1f)) }

        // Hearts flying up the sides: pink on her side (left), blue on his (right). Each one rises
        // from the bottom with a gentle sway and tilt, fading in and out, so the loop never shows
        val f = flight.value
        fun flyingHeart(heart: FlyingHeart, color: Color, mirror: Boolean) {
            val t = (f + heart.phase) % 1f
            val smoothAlpha = kotlin.math.sin(t * Math.PI.toFloat()).coerceIn(0f, 1f)
            val alpha = (heart.alpha * smoothAlpha * strength).coerceIn(0f, 1f)
            if (alpha <= 0.005f) return
            val swing = kotlin.math.sin(2f * Math.PI.toFloat() * (t + heart.phase))
            val baseX = if (mirror) 1f - heart.x else heart.x
            val cx = baseX * w + swing * heart.sway.dp.toPx()
            val cy = h * (1.04f - 1.10f * t)
            val px = heart.sizeDp.dp.toPx() * (0.85f + 0.35f * t)
            withTransform({
                translate(cx, cy)
                rotate(swing * 8f, pivot = Offset.Zero)
                scale(px, px, pivot = Offset.Zero)
            }) {
                drawPath(unitHeart, color, alpha = alpha)
            }
        }
        FlyingHearts.forEach { flyingHeart(it, pink, mirror = false) }
        FlyingHeartsRight.forEach { flyingHeart(it, blue, mirror = true) }
        BgSparkles.forEach { sp ->
            val c = Offset(sp[0] * w, sp[1] * h)
            val r = 3.5.dp.toPx()
            val sparkleColor = sideColor(sp[0])
            drawLine(sparkleColor, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = sparkle.width, alpha = (sp[2] * strength).coerceAtMost(1f))
            drawLine(sparkleColor, Offset(c.x, c.y - r), Offset(c.x, c.y + r), strokeWidth = sparkle.width, alpha = (sp[2] * strength).coerceAtMost(1f))
        }
    }
}

/** A round photo with a plain coloured ring and a small heart on it. */
@Composable
private fun RingedAvatar(person: LovePerson, color: Color, onOpenPhoto: (String) -> Unit) {
    val context = LocalContext.current
    val cardColors = LocalCardColors.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(AVATAR_SIZE + 8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(AVATAR_SIZE)
                .clip(CircleShape)
                .background(cardColors.pill)
                .border(RING_WIDTH, color, CircleShape)
        ) {
            AvatarView(
                photoUrl = person.photoUrl,
                name = person.name,
                size = AVATAR_SIZE - RING_WIDTH * 2 - 3.dp,
                isOnline = false,
                showOnlineBadge = false,
                showRing = false,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        val url = person.photoUrl
                        if (!url.isNullOrBlank()) onOpenPhoto(url)
                        else Toast.makeText(context, "No profile photo uploaded yet", Toast.LENGTH_SHORT).show()
                    }
            )
        }
        // A small heart on the ring
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 4.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(cardColors.pill)
        ) {
            Icon(Icons.Filled.Favorite, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        }
    }
}

/** Two hearts beating gently between us: her pink heart on the left and his blue heart on the right, crossing each other and connecting both ring profiles. */
@Composable
private fun TwinHearts(pink: Color, blue: Color, beat: State<Float>) {
    val outline = LocalCardColors.current.pill.copy(alpha = 1f)
    val cardIsLight = LocalCardColors.current.isLight
    Box(
        modifier = Modifier
            .size(width = 68.dp, height = 48.dp)
            .drawWithCache {
                val unitHeart = heartPath(0f, 0f, 1f)
                val heartSize = 25.dp.toPx()
                // Man's heart (blue) shifted a little right; girl's heart (pink) shifted a little left
                // Both hearts cross in the center and connect both profile rings.
                // Girl's heart is exactly 90% of man's heart size.
                val manGrow = 1.14f
                val girlGrow = manGrow * 0.90f
                val blueAt = Offset(size.width / 2f + 7.5.dp.toPx(), size.height / 2f - 1.dp.toPx())
                val pinkAt = Offset(size.width / 2f - 7.5.dp.toPx(), size.height / 2f + 0.5.dp.toPx())
                val edge = Stroke(width = 2.8.dp.toPx() / heartSize)
                // The edge matches the card behind, so the overlapping hearts read as two crossing hearts
                val edgeColor = if (cardIsLight) outline else NightCardEdge
                onDrawBehind {
                    val s = heartSize * (1f + 0.045f * beat.value)
                    fun drawHeart(at: Offset, color: Color, rotation: Float, grow: Float) {
                        withTransform({
                            translate(at.x, at.y)
                            rotate(rotation, pivot = Offset.Zero)
                            scale(s * grow, s * grow, pivot = Offset.Zero)
                        }) {
                            drawPath(unitHeart, edgeColor, style = edge)
                            drawPath(unitHeart, color)
                        }
                    }
                    // Man's heart shifted little right, tilted towards his ring profile
                    drawHeart(blueAt, blue, rotation = 12f, grow = manGrow)
                    // Girl's heart shifted little left (90% size of man's heart), crossing in front and connecting her ring profile
                    drawHeart(pinkAt, pink, rotation = -12f, grow = girlGrow)
                }
            }
            .testTag("both_of_us_hearts")
    )
}

/**
 * A static drawn heart on the left or right side of the card, styled with that side's signature color.
 */
@Composable
private fun StaticSideHeart(
    color: Color,
    isLeft: Boolean,
    modifier: Modifier = Modifier
) {
    val cardIsLight = LocalCardColors.current.isLight
    Box(
        modifier = modifier
            .size(36.dp)
            .drawWithCache {
                val unitHeart = calligraphicHeartPath(0f, 0f, 1f, isLeft)
                val s = 24.dp.toPx()
                val rotation = if (isLeft) -8f else 8f
                onDrawBehind {
                    withTransform({
                        translate(size.width / 2f, size.height / 2f)
                        rotate(rotation, pivot = Offset.Zero)
                        scale(s, s, pivot = Offset.Zero)
                    }) {
                        // Soft tinted glow behind static heart
                        drawCircle(
                            Brush.radialGradient(
                                listOf(color.copy(alpha = if (cardIsLight) 0.18f else 0.28f), Color.Transparent),
                                center = Offset.Zero,
                                radius = 1.35f
                            ),
                            radius = 1.35f,
                            center = Offset.Zero
                        )
                        // Soft tinted calligraphic heart fill
                        drawPath(unitHeart, color.copy(alpha = if (cardIsLight) 0.16f else 0.24f))
                        // Clean calligraphic heart ribbon stroke
                        drawPath(
                            unitHeart,
                            color.copy(alpha = 0.95f),
                            style = Stroke(
                                width = 1.9.dp.toPx() / s,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
            .testTag(if (isLeft) "both_of_us_static_heart_left" else "both_of_us_static_heart_right")
    )
}

/** Under a photo: online / offline and battery, age and days of life, the birthday countdown. */
@Composable
private fun PersonDetails(
    person: LovePerson,
    color: Color,
    now: Long,
    modifier: Modifier = Modifier,
    isPartner: Boolean = false,
    myTimeZone: String? = null,
    onEditBirthday: () -> Unit
) {
    val cc = LocalCardColors.current
    val partnerTimeText = remember(person.timeZone, myTimeZone, now, isPartner) {
        if (!isPartner) null
        else {
            val label = if (person.isMarked(HIS_MARKERS)) "His time" else "Her time"
            formatPartnerLocalTimeIfDifferent(
                partnerTimeZoneId = person.timeZone,
                myTimeZoneId = myTimeZone,
                now = now,
                label = label
            )
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(horizontal = 3.dp)
            .testTag("both_of_us_${person.id}")
    ) {
        // Partner's local time (small): if you're ever in different time zones only, show "Her time: 11:40 PM" under her photo
        if (partnerTimeText != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .padding(bottom = 5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color.copy(alpha = if (cc.isLight) 0.08f else 0.18f))
                    .border(0.7.dp, color.copy(alpha = 0.32f), RoundedCornerShape(50))
                    .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    .testTag("partner_local_time")
            ) {
                Icon(
                    imageVector = Icons.Filled.AccessTime,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(10.5.dp)
                )
                Spacer(modifier = Modifier.width(3.5.dp))
                Text(
                    text = partnerTimeText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cc.ink,
                    maxLines = 1,
                    letterSpacing = 0.1.sp
                )
            }
        }

        // ● Online | battery
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(cc.pill)
                .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
                .padding(horizontal = 5.dp, vertical = 3.5.dp)
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (person.isOnline) OnlineGreen else cc.dimInk)
            )
            Spacer(modifier = Modifier.width(3.5.dp))
            val statusLabel = when {
                person.isOnline -> "Online"
                !person.status.isNullOrBlank() -> person.status
                else -> "Offline"
            }
            Text(
                text = statusLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (person.isOnline) OnlineGreen else cc.dimInk,
                maxLines = 1,
                softWrap = false,
                overflow = if (person.isOnline) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = if (person.isOnline) Modifier else Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                Modifier
                    .width(1.dp)
                    .height(10.dp)
                    .background(cc.hairline)
            )
            Spacer(modifier = Modifier.width(4.dp))
            CardBattery(level = person.batteryLevel, isCharging = person.isCharging, showPercentage = person.isOnline)
        }

        // Age, days of life and birthday (tap to set or change the birthday)
        val born = parseDay(person.birthday)?.takeIf { now >= it.timeInMillis }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(enabled = person.id.isNotBlank()) { onEditBirthday() }
                .padding(horizontal = 4.dp, vertical = 3.dp)
        ) {
            if (born == null) {
                SoftPill(color = color) {
                    Text(text = "+ Add birthday", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = color)
                }
            } else {
                val age = ageAt(born, now)
                Text(
                    text = "$age yrs · ${formatNumber((now - born.timeInMillis) / DAY_MS)} days",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cc.ink,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
                val untilBirthday = daysToBirthday(born, now)
                if (untilBirthday == 0) {
                    // Birthday today: a pink-to-blue pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(HeartbeatPink, HeartbeatBlue)))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(text = "🎂 BIRTHDAY TODAY", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    }
                } else {
                    SoftPill(color = color) {
                        Text(
                            text = if (untilBirthday == 1) "🎂 Tomorrow" else "🎂 in $untilBirthday days",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = color,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/** A soft tinted pill in [color]. */
@Composable
private fun SoftPill(color: Color, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) { content() }
}

/**
 * Our places, in the card's colours from pink (left) to blue (right): the gallery, memories, our
 * dates and notes, each opening its screen.
 */
@Composable
private fun LovePlaces(
    pink: Color,
    blue: Color,
    onOpenGallery: () -> Unit,
    onOpenMemories: () -> Unit,
    onOpenDates: () -> Unit,
    onOpenNotes: () -> Unit
) {
    val cc = LocalCardColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("love_places")
    ) {
        val places = listOf(
            Triple(Icons.Filled.PhotoLibrary, "Our Gallery", "Photos & Videos") to onOpenGallery,
            Triple(Icons.Filled.AllInclusive, "Our Memories", "Special Moments") to onOpenMemories,
            Triple(Icons.Filled.EventAvailable, "Our Dates", "Important Days") to onOpenDates,
            Triple(Icons.Filled.EditNote, "Our Notes", "Love Notes") to onOpenNotes
        )
        places.forEachIndexed { i, (place, onOpen) ->
            if (i > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(cc.hairline)
                )
            }
            PlaceTile(
                icon = place.first,
                title = place.second,
                subtitle = place.third,
                color = lerp(pink, blue, i / (places.size - 1f)),
                onClick = onOpen,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** One place: a soft tinted icon tile, its name and a short line under it. */
@Composable
private fun PlaceTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cc = LocalCardColors.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp)
            .testTag("love_place_${title.lowercase().replace(' ', '_')}")
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(2.dp)
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(color.copy(alpha = 0.13f))
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = cc.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            fontSize = 10.sp,
            color = cc.softInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Battery on the card: the icon (green while charging, pink when low) and the percentage. */
@Composable
private fun CardBattery(level: Int?, isCharging: Boolean, showPercentage: Boolean) {
    val lvl = level ?: 100
    val isLow = lvl <= 20
    val tint = when {
        isCharging -> OnlineGreen
        isLow && level != null -> HeartbeatPink
        else -> LocalCardColors.current.softInk
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = when {
                isCharging -> Icons.Filled.BatteryChargingFull
                isLow && level != null -> Icons.Filled.BatteryAlert
                lvl >= 90 -> Icons.Filled.BatteryFull
                lvl >= 70 -> Icons.Filled.Battery6Bar
                lvl >= 50 -> Icons.Filled.Battery5Bar
                lvl >= 35 -> Icons.Filled.Battery4Bar
                else -> Icons.Filled.Battery3Bar
            },
            contentDescription = if (level != null) "$level% battery" else "Battery",
            tint = tint,
            modifier = Modifier.size(11.5.dp)
        )
        if (showPercentage && level != null && level in 0..100) {
            Spacer(modifier = Modifier.width(1.dp))
            Text(text = "$level%", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = tint, maxLines = 1, softWrap = false)
        }
    }
}

/** Picks a calendar day; [initial] and the picked day are "yyyy-MM-dd". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPickerDialog(title: String, initial: String?, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val initialUtc = parseDay(initial)?.let {
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { utcMillis ->
                    // The picker gives a UTC day; it's stored as that calendar day
                    onPick(
                        SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            .apply { timeZone = TimeZone.getTimeZone("UTC") }
                            .format(Date(utcMillis))
                    )
                }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(
            state = pickerState,
            title = {
                Text(text = title, modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp))
            }
        )
    }
}
