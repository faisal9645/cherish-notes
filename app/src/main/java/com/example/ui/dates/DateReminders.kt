package com.example.ui.dates

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DateCategory
import com.example.data.model.ImportantDate
import com.example.ui.theme.RoseGoldPrimary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** One date's next time: in [daysUntil] days (0 = today), its [years]th time (null if unknown or first). */
data class UpcomingDate(val date: ImportantDate, val daysUntil: Int, val years: Int?)

/**
 * Our dates: birthdays, anniversaries, meetings, places we went... Yearly ones come round
 * every year (Feb 29 falls on Feb 28 in other years); one-time ones only on their day.
 */
object DateReminders {
    /** How far ahead the reminder on opening the app looks. */
    const val REMIND_WITHIN_DAYS = 7

    private fun startOfDay(cal: Calendar): Calendar = cal.apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    private fun daysBetween(from: Calendar, to: Calendar): Int =
        ((to.timeInMillis - from.timeInMillis) / 86_400_000.0).roundToInt()

    /** The next time [date] comes round (today counts), or null for a one-time date that has passed. */
    fun next(date: ImportantDate, now: Long = System.currentTimeMillis()): UpcomingDate? {
        val today = startOfDay(Calendar.getInstance().apply { timeInMillis = now })
        val original = startOfDay(Calendar.getInstance().apply { timeInMillis = date.dateMillis })
        if (!date.repeatAnnually) {
            val days = daysBetween(today, original)
            return if (days >= 0) UpcomingDate(date, days, null) else null
        }
        val month = original.get(Calendar.MONTH)
        val day = original.get(Calendar.DAY_OF_MONTH)
        fun occurrenceIn(year: Int): Calendar = startOfDay(Calendar.getInstance()).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, minOf(day, getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        var occurrence = occurrenceIn(today.get(Calendar.YEAR))
        if (occurrence.before(today)) occurrence = occurrenceIn(today.get(Calendar.YEAR) + 1)
        val years = occurrence.get(Calendar.YEAR) - original.get(Calendar.YEAR)
        return UpcomingDate(date, daysBetween(today, occurrence), years.takeIf { it > 0 })
    }

    /** Dates coming up within [withinDays] days (today first). */
    fun upcoming(dates: List<ImportantDate>, withinDays: Int, now: Long = System.currentTimeMillis()): List<UpcomingDate> =
        dates.mapNotNull { next(it, now) }.filter { it.daysUntil in 0..withinDays }.sortedBy { it.daysUntil }

    fun whenLabel(daysUntil: Int): String = when (daysUntil) {
        0 -> "Today 🎉"
        1 -> "Tomorrow"
        else -> "in $daysUntil days"
    }

    /** "turns 25" for a birthday, "3 years" for the rest. */
    fun yearsLabel(upcoming: UpcomingDate): String? {
        val years = upcoming.years ?: return null
        return when (upcoming.date.getTypedCategory()) {
            DateCategory.BIRTHDAY -> "turns $years"
            else -> "$years ${if (years == 1) "year" else "years"}"
        }
    }

    fun emoji(category: DateCategory): String = when (category) {
        DateCategory.ANNIVERSARY -> "💍"
        DateCategory.BIRTHDAY -> "🎂"
        DateCategory.FIRST_DATE -> "☕"
        DateCategory.PLACE -> "📍"
        DateCategory.FAMILY -> "🏡"
        DateCategory.MILESTONE -> "🌟"
        DateCategory.CUSTOM -> "❤️"
    }

    fun label(category: DateCategory): String = when (category) {
        DateCategory.ANNIVERSARY -> "Anniversary"
        DateCategory.BIRTHDAY -> "Birthday"
        DateCategory.FIRST_DATE -> "Meetings"
        DateCategory.PLACE -> "Place we went"
        DateCategory.FAMILY -> "Family"
        DateCategory.MILESTONE -> "Special day"
        DateCategory.CUSTOM -> "Other"
    }

    fun formatDate(millis: Long): String =
        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(Date(millis))

    /**
     * The day the couple got together ("yyyy-MM-dd"), for "days together" and celebrations: the
     * day set in settings ([setting]), else the earliest anniversary in our dates, else null.
     */
    fun togetherSince(dates: List<ImportantDate>, setting: String? = null): String? {
        setting?.trim()?.ifBlank { null }?.let { return it }
        val first = dates.filter { it.getTypedCategory() == DateCategory.ANNIVERSARY }.minByOrNull { it.dateMillis }
            ?: return null
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(first.dateMillis))
    }

    /**
     * Our dates plus both of our birthdays (set on the Love & Us tab), titled by [titles] (user id
     * to e.g. "Shali's birthday"). A birthday already added to our dates by hand isn't doubled.
     */
    fun withBirthdays(
        dates: List<ImportantDate>,
        birthdays: Map<String, String>,
        titles: Map<String, String>
    ): List<ImportantDate> {
        val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val monthDay = SimpleDateFormat("MM-dd", Locale.US)
        val listed = dates.filter { it.getTypedCategory() == DateCategory.BIRTHDAY }
            .map { monthDay.format(Date(it.dateMillis)) }
            .toSet()
        val extra = titles.mapNotNull { (userId, title) ->
            val born = try {
                birthdays[userId]?.let { dayFormat.parse(it) }
            } catch (_: Exception) {
                null
            } ?: return@mapNotNull null
            if (monthDay.format(born) in listed) return@mapNotNull null
            ImportantDate(
                id = "birthday_$userId",
                title = title,
                dateMillis = born.time,
                category = DateCategory.BIRTHDAY.name
            )
        }
        return dates + extra
    }

    // ---- The reminder when the app is opened: once a day ----

    private fun prefs(context: Context) = context.getSharedPreferences("cherish_love", Context.MODE_PRIVATE)

    private fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    /** What to remind about now, or empty if nothing's coming up or today's reminder was shown. */
    fun dueReminder(context: Context, dates: List<ImportantDate>): List<UpcomingDate> {
        if (prefs(context).getString("dates_reminder_shown", null) == todayKey()) return emptyList()
        return upcoming(dates, REMIND_WITHIN_DAYS)
    }

    fun markReminderShown(context: Context) {
        prefs(context).edit().putString("dates_reminder_shown", todayKey()).apply()
    }
}

/** One date in a list: emoji badge, title, when, and how many years. */
@Composable
fun UpcomingDateRow(upcoming: UpcomingDate, modifier: Modifier = Modifier) {
    val category = upcoming.date.getTypedCategory()
    val isToday = upcoming.daysUntil == 0
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(RoseGoldPrimary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = DateReminders.emoji(category), fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = upcoming.date.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val years = DateReminders.yearsLabel(upcoming)
            Text(
                text = listOfNotNull(DateReminders.label(category), years).joinToString(" · "),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Surface(
            shape = RoundedCornerShape(50),
            color = if (isToday) RoseGoldPrimary else RoseGoldPrimary.copy(alpha = 0.14f)
        ) {
            Text(
                text = DateReminders.whenLabel(upcoming.daysUntil),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isToday) Color.White else RoseGoldPrimary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

/** On opening the app (once a day): what's coming up this week. */
@Composable
fun DatesReminderCard(
    reminders: List<UpcomingDate>,
    onSeeAll: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .testTag("dates_reminder"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(horizontal = 26.dp)
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(26.dp))
                // Taps on the card don't close it
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)) {
                Text(
                    text = if (reminders.any { it.daysUntil == 0 }) "Today is special 💛" else "Coming up 💛",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Don't forget these days",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(14.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    reminders.take(5).forEach { UpcomingDateRow(it) }
                }
                if (reminders.size > 5) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "and ${reminders.size - 5} more",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "See all dates",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RoseGoldPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onSeeAll() }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = RoseGoldPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onDismiss() }
                            .testTag("dates_reminder_ok")
                    ) {
                        Text(
                            text = "Got it",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp)
                        )
                    }
                }
            }
        }
    }
}
