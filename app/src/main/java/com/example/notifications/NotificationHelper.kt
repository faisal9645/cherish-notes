package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.security.SecurityPreferences
import java.util.concurrent.atomic.AtomicInteger

/**
 * NotificationHelper with stealth/hidden disguised notifications
 * and single-notification consolidation (prevents multiple spam notifications).
 */
object NotificationHelper {
    const val CHANNEL_MESSAGES_ID = "notes_sync_channel"
    private const val CHANNEL_MESSAGES_NAME = "Notes & Reminders"
    const val CHANNEL_REMINDERS_ID = "notes_reminders_channel"
    private const val CHANNEL_REMINDERS_NAME = "Note Reminders"
    private const val NOTIFICATION_ID_MESSAGE = 1001

    private val pendingUnreadCount = AtomicInteger(0)

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES_ID,
                CHANNEL_MESSAGES_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Discreet sync and reminder notifications"
                enableVibration(true)
                setShowBadge(true)
                // Private visibility on lock screen
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
            }

            val remindersChannel = NotificationChannel(
                CHANNEL_REMINDERS_ID,
                CHANNEL_REMINDERS_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Time-sensitive alerts for scheduled note reminders"
                enableVibration(true)
                setShowBadge(true)
                enableLights(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(messagesChannel)
            manager?.createNotificationChannel(remindersChannel)
        }
    }

    /**
     * Show a stealth disguised notification that others will never suspect,
     * consolidated into a single notification without duplicates.
     */
    fun showMessageNotification(
        context: Context,
        senderName: String,
        messageText: String,
        conversationId: String? = null
    ) {
        val count = pendingUnreadCount.incrementAndGet()
        val prefs = SecurityPreferences.getInstance(context)
        val isDiscreet = prefs.isHideNotificationContent()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("conversationId", conversationId)
            putExtra("open_chat", true)
            putExtra("from_notification", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // When discreet/hidden mode is enabled, notification payloads mask sender names and contents
        val displayTitle = if (isDiscreet) "Notes" else senderName
        val displayText = if (isDiscreet) {
            if (count <= 1) "Checklist reminder updated" else "$count reminders synchronized"
        } else {
            messageText
        }
        val subText = if (isDiscreet) "Notes" else "Cherish"

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(displayTitle)
            .setContentText(displayText)
            .setSubText(subText)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setOnlyAlertOnce(true) // Never buzz multiple times for updates
            .setVisibility(if (isDiscreet) NotificationCompat.VISIBILITY_SECRET else NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_MESSAGE, notification)
        } catch (_: SecurityException) {
            // Handled if POST_NOTIFICATIONS runtime permission not yet prompted
        }
    }

    /**
     * Clears notifications and resets the counter when the user views the chat
     */
    fun clearNotifications(context: Context) {
        pendingUnreadCount.set(0)
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_MESSAGE)
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_CHECK_AFTER)
        } catch (_: Exception) {}
    }

    const val NOTIFICATION_ID_CHECK_AFTER = 1002

    /**
     * Subtle reminder notification when partner's Check-After countdown finishes
     */
    fun showCheckAfterReminderNotification(context: Context, partnerName: String = "Your partner") {
        val prefs = SecurityPreferences.getInstance(context)
        if (!prefs.isCheckAfterReminderEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_chat", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isDisguised = prefs.isHideNotificationContent() || prefs.isDisguiseModeEnabled()
        val displayTitle = if (isDisguised) "Notes" else "❤️ It's time to check"
        val displayText = if (isDisguised) {
            "Reminder schedule completed"
        } else {
            "$partnerName's check-after time has arrived 💕"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(displayTitle)
            .setContentText(displayText)
            .setSubText(if (isDisguised) "Notes" else "Cherish")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_CHECK_AFTER, notification)
        } catch (_: SecurityException) {
            // Handled if POST_NOTIFICATIONS runtime permission not yet prompted
        }
    }

    /**
     * Show a native notification when a note reminder triggers.
     * When tapped, opens the app in Notes mode and directly loads the targeted note.
     */
    fun showNoteReminderNotification(
        context: Context,
        noteId: String,
        title: String,
        content: String
    ) {
        val prefs = SecurityPreferences.getInstance(context)
        if (!prefs.isNoteRemindersEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_note_id", noteId)
            putExtra("from_notification", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            noteId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayTitle = title.ifBlank { "Note Reminder" }
        val displayText = if (content.isNotBlank()) content else "You have a scheduled note reminder."

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(displayTitle)
            .setContentText(displayText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayText))
            .setSubText("Notes")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(noteId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Handled if POST_NOTIFICATIONS runtime permission not yet prompted
        }
    }

    fun cancelNoteReminderNotification(context: Context, noteId: String) {
        try {
            NotificationManagerCompat.from(context).cancel(noteId.hashCode())
        } catch (_: Exception) {}
    }
}


