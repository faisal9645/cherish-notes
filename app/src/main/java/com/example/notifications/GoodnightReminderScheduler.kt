package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

/**
 * Schedules daily 11:00 PM (23:00) exact alarm to ensure goodnight is said or prompted every evening.
 */
object GoodnightReminderScheduler {
    private const val TAG = "GoodnightScheduler"
    private const val REQUEST_CODE = 2026

    fun scheduleNext(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // Target next 11:00 PM (23:00:00)
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) {
                // If it's already past 11:00 PM today, target 11:00 PM tomorrow
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val targetTimeMillis = cal.timeInMillis
        val intent = Intent(context, GoodnightReminderReceiver::class.java).apply {
            action = GoodnightReminderReceiver.ACTION_GOODNIGHT_11PM
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetTimeMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetTimeMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetTimeMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, targetTimeMillis, pendingIntent)
            }
            Log.d(TAG, "Scheduled 11 PM Goodnight alarm for millis=$targetTimeMillis")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule exact alarm for 11 PM goodnight, falling back", e)
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, targetTimeMillis, pendingIntent)
            } catch (_: Exception) {}
        }
    }
}
