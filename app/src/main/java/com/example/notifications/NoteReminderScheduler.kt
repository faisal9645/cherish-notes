package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.local.notes.NotesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object NoteReminderScheduler {
    private const val TAG = "NoteReminderScheduler"

    fun scheduleReminder(
        context: Context,
        noteId: String,
        title: String,
        content: String,
        reminderTimeMillis: Long
    ) {
        if (reminderTimeMillis <= System.currentTimeMillis()) {
            Log.d(TAG, "Reminder time is in the past, skipping schedule for note=$noteId")
            return
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, NoteReminderReceiver::class.java).apply {
            action = NoteReminderReceiver.ACTION_NOTE_REMINDER
            putExtra(NoteReminderReceiver.EXTRA_NOTE_ID, noteId)
            putExtra(NoteReminderReceiver.EXTRA_NOTE_TITLE, title)
            putExtra(NoteReminderReceiver.EXTRA_NOTE_CONTENT, content)
        }

        val requestCode = noteId.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTimeMillis, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTimeMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminderTimeMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, reminderTimeMillis, pendingIntent)
            }
            Log.d(TAG, "Note reminder scheduled for noteId=$noteId at $reminderTimeMillis")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to schedule exact alarm for noteId=$noteId, using fallback", e)
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, reminderTimeMillis, pendingIntent)
            } catch (_: Exception) {}
        }
    }

    fun cancelReminder(context: Context, noteId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val intent = Intent(context, NoteReminderReceiver::class.java).apply {
            action = NoteReminderReceiver.ACTION_NOTE_REMINDER
        }

        val requestCode = noteId.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Note reminder alarm cancelled for noteId=$noteId")
        }

        NotificationHelper.cancelNoteReminderNotification(context, noteId)
    }

    fun rescheduleAllUpcomingReminders(context: Context, repository: NotesRepository) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val upcoming = repository.getUpcomingReminders(System.currentTimeMillis())
                for (note in upcoming) {
                    val rTime = note.reminderTime ?: continue
                    if (rTime > System.currentTimeMillis()) {
                        scheduleReminder(
                            context = context,
                            noteId = note.id,
                            title = note.title,
                            content = note.content,
                            reminderTimeMillis = rTime
                        )
                    }
                }
                Log.d(TAG, "Rescheduled ${upcoming.size} upcoming note reminders")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to reschedule upcoming note reminders", e)
            }
        }
    }
}
