package com.example.util

import java.util.Calendar

/**
 * After 11 PM (until 6 AM) the app goes calm: the same colours, but its motion slower and softer
 * and its sounds quieter, so a late chat feels like a whisper.
 */
object NightCalm {
    private const val START_HOUR = 22
    private const val END_HOUR = 6

    /** Animations take this much longer at night. */
    const val SLOWER = 1.4f

    /** Sounds play at this share of their volume at night. */
    const val QUIETER = 0.45f

    fun isNight(now: Long = System.currentTimeMillis()): Boolean {
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        return hour >= START_HOUR || hour < END_HOUR
    }

    /** [ms] as long as it takes now: [SLOWER] at night. */
    fun duration(ms: Int, night: Boolean = isNight()): Int = if (night) (ms * SLOWER).toInt() else ms
}
