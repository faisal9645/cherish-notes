package com.example.ui.chat

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.random.Random

/** A local, per-account check-in; no alarms or notifications while the disguise is showing. */
@Composable
internal fun WeeklyMoodPrompt(
    userId: String,
    visible: Boolean,
    canAsk: () -> Boolean,
    onAsk: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val latestCanAsk by rememberUpdatedState(canAsk)
    val latestOnAsk by rememberUpdatedState(onAsk)
    LaunchedEffect(userId, visible) {
        if (userId.isBlank() || !visible) return@LaunchedEffect
        val prefs = context.getSharedPreferences("weekly_mood_prompt", Context.MODE_PRIVATE)
        val key = "next_$userId"
        fun nextTime(now: Long, first: Boolean): Long {
            val minimum = now + if (first) 0L else 7L * 24 * 60 * 60 * 1000
            return Calendar.getInstance().apply {
                timeInMillis = minimum
                if (first) add(Calendar.DAY_OF_YEAR, Random.nextInt(1, 8))
                set(Calendar.HOUR_OF_DAY, Random.nextInt(10, 20))
                set(Calendar.MINUTE, Random.nextInt(60))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= minimum) add(Calendar.DAY_OF_YEAR, 1)
            }.timeInMillis
        }
        if (!prefs.contains(key)) {
            prefs.edit().putLong(key, nextTime(System.currentTimeMillis(), first = true)).apply()
        }
        // Let the opened chat settle before offering a check-in.
        delay(20_000)
        while (true) {
            val now = System.currentTimeMillis()
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (now >= prefs.getLong(key, Long.MAX_VALUE) && hour in 10..19 && latestCanAsk()) {
                // Dismissal also counts: never nag again on every reopen.
                prefs.edit().putLong(key, nextTime(now, first = false)).apply()
                latestOnAsk()
            }
            delay(30_000)
        }
    }
}
