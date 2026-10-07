package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Universal, precision time and date formatter for chat messages.
 * - Logical chat days start at 6:00 AM (late night texting up to 5:59 AM stays in the same active day session).
 * - Exact message time stamps (AM/PM) ALWAYS show the real, unshifted current local time of the message.
 */
object ChatTimeFormatter {

    // 6 AM morning logical day rollover offset (6 hours in milliseconds)
    private const val DAY_ROLLOVER_OFFSET_MS = 6 * 60 * 60 * 1000L

    fun normalizeTimestamp(rawTimestamp: Long): Long {
        if (rawTimestamp <= 0L) return System.currentTimeMillis()
        // If timestamp was stored in seconds (10 digits), convert to milliseconds (13 digits)
        return if (rawTimestamp < 100_000_000_000L) rawTimestamp * 1000L else rawTimestamp
    }

    /**
     * Converts an epoch timestamp to its logical day timestamp starting at 6:00 AM.
     * Messages sent between 12:00 AM and 5:59:59 AM belong to the ongoing previous night's session.
     * At 6:00 AM, the new day starts.
     */
    private fun getLogicalDayTimestamp(rawTimestamp: Long): Long {
        val timestamp = normalizeTimestamp(rawTimestamp)
        return timestamp - DAY_ROLLOVER_OFFSET_MS
    }

    private fun isSameCalendarDay(t1: Long, t2: Long): Boolean {
        val cal1 = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).apply { timeInMillis = t1 }
        val cal2 = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).apply { timeInMillis = t2 }

        return cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) &&
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * Formats a message timestamp into standard 12-hour time with accurate AM/PM.
     * Always uses the REAL, unshifted timestamp so message time displays the correct local time!
     * Example: "2:30 AM", "9:41 AM", "12:00 PM", "6:15 PM"
     */
    fun formatMessageTime(rawTimestamp: Long): String {
        if (rawTimestamp <= 0L) return ""
        val timestamp = normalizeTimestamp(rawTimestamp)
        val cal = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).apply {
            timeInMillis = timestamp
        }

        val rawHour = cal.get(Calendar.HOUR)
        val hour = if (rawHour == 0) 12 else rawHour
        val minute = String.format(Locale.US, "%02d", cal.get(Calendar.MINUTE))
        val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"

        return "$hour:$minute $amPm"
    }

    /**
     * Checks if a message belongs to Today's logical chat session (6:00 AM to 5:59:59 AM next morning).
     */
    fun isToday(rawTimestamp: Long): Boolean {
        if (rawTimestamp <= 0L) return false
        val msgLogical = getLogicalDayTimestamp(rawTimestamp)
        val nowLogical = getLogicalDayTimestamp(System.currentTimeMillis())
        return isSameCalendarDay(msgLogical, nowLogical)
    }

    /**
     * Checks if a message belongs to Yesterday's logical chat session.
     */
    fun isYesterday(rawTimestamp: Long): Boolean {
        if (rawTimestamp <= 0L) return false
        val msgLogical = getLogicalDayTimestamp(rawTimestamp)
        val yesterdayLogical = getLogicalDayTimestamp(System.currentTimeMillis()) - 86400000L
        return isSameCalendarDay(msgLogical, yesterdayLogical)
    }

    /**
     * Checks if two messages belong to the same logical 6:00 AM day session.
     */
    fun isSameDay(t1: Long, t2: Long): Boolean {
        if (t1 <= 0L || t2 <= 0L) return false
        val logical1 = getLogicalDayTimestamp(t1)
        val logical2 = getLogicalDayTimestamp(t2)
        return isSameCalendarDay(logical1, logical2)
    }

    /**
     * Formats timestamp for conversation list preview:
     * - Today (from 6 AM): "6:15 PM"
     * - Yesterday: "Yesterday"
     * - Older: "Oct 4" (or "Oct 4, 2025" if different year)
     */
    fun formatConversationTime(rawTimestamp: Long): String {
        if (rawTimestamp <= 0L) return ""
        if (isToday(rawTimestamp)) return formatMessageTime(rawTimestamp)
        if (isYesterday(rawTimestamp)) return "Yesterday"

        val logicalTimestamp = getLogicalDayTimestamp(rawTimestamp)
        val calMsg = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).apply {
            timeInMillis = logicalTimestamp
        }
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val msgYear = calMsg.get(Calendar.YEAR)

        val pattern = if (msgYear == currentYear) "MMM d" else "MMM d, yyyy"
        return SimpleDateFormat(pattern, Locale.US).format(Date(logicalTimestamp))
    }

    /**
     * Formats date separator header for chat message history based on 6 AM rollover.
     */
    fun formatDateSeparator(rawTimestamp: Long): String {
        if (rawTimestamp <= 0L) return ""
        if (isToday(rawTimestamp)) return "Today"
        if (isYesterday(rawTimestamp)) return "Yesterday"

        val logicalTimestamp = getLogicalDayTimestamp(rawTimestamp)
        val calMsg = Calendar.getInstance(TimeZone.getDefault(), Locale.getDefault()).apply {
            timeInMillis = logicalTimestamp
        }
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val msgYear = calMsg.get(Calendar.YEAR)

        val pattern = if (msgYear == currentYear) "EEEE, MMMM d" else "EEEE, MMMM d, yyyy"
        return SimpleDateFormat(pattern, Locale.US).format(Date(logicalTimestamp))
    }
}
