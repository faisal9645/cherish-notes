package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.security.SecurityPreferences

object CheckAfterReminderScheduler {
    private const val TAG = "CheckAfterScheduler"
    private const val REQUEST_CODE = 2002

    fun scheduleReminder(context: Context, targetTimeMillis: Long, partnerName: String) {
        val prefs = SecurityPreferences.getInstance(context)
        if (!prefs.isCheckAfterReminderEnabled()) {
            Log.d(TAG, "Reminder disabled by user preference")
            return
        }

        if (targetTimeMillis <= System.currentTimeMillis()) {
            Log.d(TAG, "Target time is in the past, skipping alarm schedule")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, CheckAfterReminderReceiver::class.java).apply {
            action = CheckAfterReminderReceiver.ACTION_CHECK_AFTER_EXPIRED
            putExtra(CheckAfterReminderReceiver.EXTRA_PARTNER_NAME, partnerName)
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
            Log.d(TAG, "Check-After reminder scheduled for millis=$targetTimeMillis")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule exact alarm, falling back", e)
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, targetTimeMillis, pendingIntent)
            } catch (_: Exception) {}
        }
    }

    fun cancelReminder(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, CheckAfterReminderReceiver::class.java).apply {
            action = CheckAfterReminderReceiver.ACTION_CHECK_AFTER_EXPIRED
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Check-After reminder cancelled")
        }
    }
}
