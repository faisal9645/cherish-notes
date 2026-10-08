package com.example.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AvatarView
import com.example.ui.theme.RoseGoldPrimary
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/** One of us on the Love & Us tab. [birthday] is "yyyy-MM-dd" or null when not set yet. */
data class LovePerson(val id: String, val name: String, val photoUrl: String?, val birthday: String?)

private const val DAY_MS = 86_400_000L

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

/**
 * Both of us: each one's age, days of life (counting live, with the seconds ticking) and the next
 * birthday; underneath, the days we've lived between us and how many of them together. Tap a side
 * to set or change that birthday.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BothOfUsCard(
    me: LovePerson,
    partner: LovePerson,
    togetherSince: String?,
    isVisible: Boolean,
    onSetBirthday: (userId: String, date: String) -> Unit
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("both_of_us_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Text(
                text = "Both of us 🎂",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 2.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                PersonColumn(me, now, Modifier.weight(1f)) { editing = me }
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight().padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                )
                PersonColumn(partner, now, Modifier.weight(1f)) { editing = partner }
            }

            // Between us
            val myBorn = parseDay(me.birthday)
            val partnerBorn = parseDay(partner.birthday)
            val together = parseDay(togetherSince)
            if (myBorn != null && partnerBorn != null) {
                val lived = (now - myBorn.timeInMillis) / DAY_MS + (now - partnerBorn.timeInMillis) / DAY_MS
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = RoseGoldPrimary.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = buildString {
                            append("Together we've lived ${formatNumber(lived)} days")
                            if (together != null && now >= together.timeInMillis) {
                                append(", and ${formatNumber((now - together.timeInMillis) / DAY_MS + 1)} of them side by side")
                            }
                            append(" 💛")
                        },
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }

    editing?.let { person ->
        val initial = parseDay(person.birthday)?.let {
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH))
            }.timeInMillis
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { editing = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { utcMillis ->
                        // The picker gives a UTC day; it's stored as that calendar day
                        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            .apply { timeZone = TimeZone.getTimeZone("UTC") }
                            .format(java.util.Date(utcMillis))
                        onSetBirthday(person.id, day)
                    }
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) { Text("Cancel") }
            }
        ) {
            DatePicker(
                state = pickerState,
                title = {
                    Text(
                        text = "${person.name}'s birthday",
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun PersonColumn(person: LovePerson, now: Long, modifier: Modifier, onEdit: () -> Unit) {
    val born = parseDay(person.birthday)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = person.id.isNotBlank()) { onEdit() }
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("both_of_us_${person.id}")
    ) {
        AvatarView(photoUrl = person.photoUrl, name = person.name, size = 46.dp, showOnlineBadge = false)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = person.name,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (born == null || now < born.timeInMillis) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(50), color = RoseGoldPrimary.copy(alpha = 0.13f)) {
                Text(
                    text = "+ Add birthday",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = RoseGoldPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        } else {
            PersonNumbers(born, now)
        }
    }
}

@Composable
private fun PersonNumbers(born: Calendar, now: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val age = ageAt(born, now)
        val lived = now - born.timeInMillis
        Text(
            text = "$age years",
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        // Days of life, counting live
        Text(
            text = formatNumber(lived / DAY_MS),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = RoseGoldPrimary
        )
        Text(
            text = "days of life",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${formatNumber(lived / 1000L)} seconds",
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(8.dp))
        val untilBirthday = daysToBirthday(born, now)
        Box(contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(50),
                color = if (untilBirthday == 0) RoseGoldPrimary else RoseGoldPrimary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = when (untilBirthday) {
                        0 -> "🎉 Birthday today!"
                        1 -> "🎂 Tomorrow, turns ${age + 1}"
                        else -> "🎂 in $untilBirthday days"
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (untilBirthday == 0) Color.White else RoseGoldPrimary,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}
