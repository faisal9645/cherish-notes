package com.example.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.example.ui.components.AvatarView
import com.example.ui.theme.HeartRed
import com.example.ui.theme.OnlineGreen
import com.example.ui.theme.darkTone
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

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
    val isCharging: Boolean = false
)

private const val DAY_MS = 86_400_000L
private val AVATAR_SIZE = 90.dp
private val AVATAR_BORDER = 2.5.dp
private val HEART_SIZE = 32.dp

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

/** Same wording as the chat header: "Online", "last seen today at 9:41 PM", ... */
private fun presenceText(person: LovePerson): String {
    person.status?.let { return it }
    if (person.isOnline) return "Online"
    val lastSeen = person.lastSeen
    if (lastSeen <= 0L) return "Offline"
    val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(lastSeen))
    return when {
        android.text.format.DateUtils.isToday(lastSeen) -> "last seen today at $time"
        android.text.format.DateUtils.isToday(lastSeen + android.text.format.DateUtils.DAY_IN_MILLIS) ->
            "last seen yesterday at $time"
        else -> "last seen " + SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(lastSeen))
    }
}

/**
 * Both of us, side by side: the same two round photos with a heart between them, and under each
 * one: name, online / last seen, battery, age, days of life (counting live) and the next birthday.
 * Below: our names, the partner's note, the days we've lived and the days together. Tap the
 * numbers under a photo to set or change that birthday.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BothOfUsCard(
    me: LovePerson,
    partner: LovePerson,
    partnerNote: String?,
    togetherSince: String?,
    isVisible: Boolean,
    onSetBirthday: (userId: String, date: String) -> Unit,
    onOpenChat: () -> Unit,
    onSetTogetherSince: (date: String) -> Unit
) {
    // Ticks every second only while the tab is on screen
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isVisible) {
        while (isVisible) {
            now = System.currentTimeMillis()
            delay(1_000L - now % 1_000L)
        }
    }
    var editing by remember { mutableStateOf<LovePerson?>(null) }
    var editingTogether by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val accent = MaterialTheme.colorScheme.primary
    val cardColor = if (isDark) darkTone(Color(0xFF141923)) else Color(0xFFFBFDFF)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("both_of_us_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(1.dp, if (isDark) darkTone(Color(0xFF232D3F)) else Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Soft glow behind the photos
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = if (isDark) 0.18f else 0.12f), Color.Transparent)
                        )
                    )
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 18.dp)
            ) {
                // Today's date, with the year
                val today = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date(now))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = accent.copy(alpha = 0.10f),
                    modifier = Modifier.testTag("both_of_us_today")
                ) {
                    Text(
                        text = "📅  $today",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))

                // The two of us, same size, with the heart between
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val columnWidth = min(maxWidth / 2, 136.dp)
                    Row(
                        modifier = Modifier.align(Alignment.TopCenter),
                        verticalAlignment = Alignment.Top
                    ) {
                        PersonColumn(me, now, accent, columnWidth) { editing = me }
                        PersonColumn(partner, now, HeartRed.copy(alpha = 0.8f), columnWidth) { editing = partner }
                    }
                    Surface(
                        shape = CircleShape,
                        color = HeartRed,
                        border = BorderStroke(2.dp, cardColor),
                        shadowElevation = 5.dp,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = (AVATAR_SIZE + AVATAR_BORDER * 2 - HEART_SIZE) / 2)
                            .size(HEART_SIZE)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = "Together in love",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "${me.name} & ${partner.name}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                if (!partnerNote.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "“$partnerNote”",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Days we've lived between us, and how many of them together
                val myBorn = parseDay(me.birthday)
                val partnerBorn = parseDay(partner.birthday)
                val together = parseDay(togetherSince)?.takeIf { now >= it.timeInMillis }
                if (myBorn != null && partnerBorn != null && now >= myBorn.timeInMillis && now >= partnerBorn.timeInMillis) {
                    val lived = (now - myBorn.timeInMillis) / DAY_MS + (now - partnerBorn.timeInMillis) / DAY_MS
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accent.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Together we've lived ${formatNumber(lived)} days 💛",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.6.dp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tap to set or change the day we got together
                    if (together != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { editingTogether = true }
                                .padding(vertical = 4.dp)
                                .testTag("days_together")
                        ) {
                            Text(text = "❤️", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${formatNumber((now - together.timeInMillis) / DAY_MS + 1)} Days Together",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        Text(
                            text = "❤️ Set days together",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { editingTogether = true }
                                .padding(vertical = 4.dp)
                                .testTag("days_together")
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accent.copy(alpha = 0.12f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onOpenChat() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(text = "Chat Now", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
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

@Composable
private fun PersonColumn(
    person: LovePerson,
    now: Long,
    ringColor: Color,
    width: androidx.compose.ui.unit.Dp,
    onEditBirthday: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(width)
            .testTag("both_of_us_${person.id}")
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp,
            border = BorderStroke(AVATAR_BORDER, ringColor)
        ) {
            AvatarView(
                photoUrl = person.photoUrl,
                name = person.name,
                size = AVATAR_SIZE,
                isOnline = person.isOnline,
                showOnlineBadge = person.isOnline,
                modifier = Modifier.clip(CircleShape)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = person.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = presenceText(person),
            fontSize = 11.sp,
            color = if (person.isOnline && person.status == null) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (person.isOnline && person.status == null) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        person.batteryLevel?.takeIf { it in 0..100 }?.let { level ->
            BatteryChip(level, person.isCharging, Modifier.padding(top = 3.dp))
        }

        // Age, days of life and birthday (tap to set or change the birthday)
        val born = parseDay(person.birthday)?.takeIf { now >= it.timeInMillis }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = person.id.isNotBlank()) { onEditBirthday() }
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            if (born == null) {
                Surface(shape = RoundedCornerShape(50), color = HeartRed.copy(alpha = 0.12f)) {
                    Text(
                        text = "+ Add birthday",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HeartRed,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            } else {
                val age = ageAt(born, now)
                val lived = now - born.timeInMillis
                Text(text = "$age years", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = formatNumber(lived / DAY_MS),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(text = "days of life", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "${formatNumber(lived / 1000L)} sec",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(6.dp))
                val untilBirthday = daysToBirthday(born, now)
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (untilBirthday == 0) HeartRed else HeartRed.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = when (untilBirthday) {
                            0 -> "🎉 Birthday today!"
                            1 -> "🎂 Tomorrow, turns ${age + 1}"
                            else -> "🎂 in $untilBirthday days"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (untilBirthday == 0) Color.White else HeartRed,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

/** Battery like in the chat header: level icon (or charging / low) and the percentage. */
@Composable
private fun BatteryChip(level: Int, isCharging: Boolean, modifier: Modifier = Modifier) {
    val isLow = level <= 20
    val tint = when {
        isCharging -> Color(0xFF10B981)
        isLow -> Color(0xFFEF4444)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(
            imageVector = when {
                isCharging -> Icons.Filled.BatteryChargingFull
                isLow -> Icons.Filled.BatteryAlert
                level >= 90 -> Icons.Filled.BatteryFull
                level >= 70 -> Icons.Filled.Battery6Bar
                level >= 50 -> Icons.Filled.Battery5Bar
                level >= 35 -> Icons.Filled.Battery4Bar
                else -> Icons.Filled.Battery3Bar
            },
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = if (isCharging) "$level% charging" else "$level%",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            maxLines = 1
        )
    }
}
