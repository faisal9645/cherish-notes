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
    private val notifiedMessageIds = java.util.Collections.synchronizedSet(LinkedHashSet<String>())

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES_ID,
                CHANNEL_MESSAGES_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Discreet sync and reminder notifications"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
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
     * Check if user is actively inside the chat screen so we don't disturb them
     * with redundant notifications while they are already viewing messages live.
     */
    fun isAppOpenOnScreen(context: Context): Boolean {
        return try {
            val app = context.applicationContext as? com.example.CherishApplication
            app?.authRepository?.isAppOpenOnScreen() ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun isUserActivelyViewingChat(context: Context): Boolean {
        return try {
            val app = context.applicationContext as? com.example.CherishApplication
            app?.authRepository?.isUserActivelyInChat() ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Show a stealth disguised notification that others will never suspect,
     * consolidated into a single notification without duplicates.
     * Alerts ONLY ONCE while unread messages accumulate, updates the icon badge,
     * and shows disguised Notes reminder on the lockscreen.
     */
    fun showMessageNotification(
        context: Context,
        senderName: String,
        messageText: String,
        conversationId: String? = null,
        messageId: String? = null
    ) {
        // Nothing pops up while the app is open on screen (any tab, or Notes): the app shows new
        // messages itself. Notifications are for when it's closed or in the background.
        if (isAppOpenOnScreen(context)) {
            return
        }

        // Deduplicate message ID so FCM and Firestore listeners don't double count
        if (!messageId.isNullOrBlank()) {
            if (!notifiedMessageIds.add(messageId)) {
                return
            }
        }

        val count = pendingUnreadCount.incrementAndGet()
        val prefs = SecurityPreferences.getInstance(context)
        if (!prefs.isNotificationsEnabled()) {
            clearNotifications(context)
            return
        }
        val isDiscreet = prefs.isDisguiseActive() && (prefs.isHideNotificationContent() || prefs.isDisguiseModeEnabled())

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Do not open chat automatically
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // When discreet/hidden mode is enabled, notification payloads mask sender names and contents
        // Masked: what was chosen in Settings (by default "Notes" / "Checklist reminder updated")
        val maskedTitle = prefs.getMaskedNotificationTitle()
        val maskedText = prefs.getMaskedNotificationText()
        val displayTitle = if (isDiscreet) maskedTitle else senderName
        val displayText = if (isDiscreet) {
            maskedText
        } else {
            messageText.ifBlank { "New message" }
        }
        val subText = if (isDiscreet) maskedTitle else "Cherish"

        // Public version shown on secure lock screens
        val publicNotification = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
            .setSmallIcon(if (!prefs.isDisguiseActive()) R.drawable.ic_cherish_heart else R.drawable.ic_stat_notes)
            .setContentTitle(maskedTitle)
            .setContentText(maskedText)
            .setSubText(maskedTitle)
            .setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
            .setNumber(1)
            .build()

        val isBadgeEnabled = prefs.isBadgeNotificationEnabled()

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
            .setSmallIcon(if (!prefs.isDisguiseActive()) R.drawable.ic_cherish_heart else R.drawable.ic_stat_notes)
            .setContentTitle(displayTitle)
            .setContentText(displayText)
            .setSubText(subText)
            .apply {
                if (isBadgeEnabled) {
                    setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
                    setNumber(1)
                } else {
                    setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
                    setNumber(0)
                }
            }
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setOnlyAlertOnce(true) // Crucial: alerts only one time while unread messages accumulate
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPublicVersion(publicNotification)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_MESSAGE, notification)
        } catch (_: SecurityException) {
            // Handled if POST_NOTIFICATIONS runtime permission not yet prompted
        }

        // Update launcher icon badge to 1 for OEM launchers (only if badge enabled)
        if (isBadgeEnabled) {
            updateLauncherBadge(context, if (count > 0) 1 else 0)
        } else {
            updateLauncherBadge(context, 0)
        }
    }

    /**
     * Clears notifications and resets the counter and badge when the user views the chat
     */
    fun clearNotifications(context: Context) {
        pendingUnreadCount.set(0)
        notifiedMessageIds.clear()
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_MESSAGE)
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_CHECK_AFTER)
        } catch (_: Exception) {}
        updateLauncherBadge(context, 0)
    }

    /**
     * Broadcasts unread count badge (1 when unread, 0 when cleared) to Samsung, Sony, HTC and compatible OEM launchers
     */
    fun updateLauncherBadge(context: Context, count: Int) {
        try {
            val badgeCount = if (count > 0) 1 else 0
            val intent = Intent("android.intent.action.BADGE_COUNT_UPDATE").apply {
                putExtra("badge_count", badgeCount)
                putExtra("badge_count_package_name", context.packageName)
                putExtra("badge_count_class_name", "com.example.MainActivity")
            }
            context.sendBroadcast(intent)
        } catch (_: Exception) {}
    }

    const val NOTIFICATION_ID_CHECK_AFTER = 1002

    /**
     * Subtle reminder notification when partner's Check-After countdown finishes
     */
    fun showCheckAfterReminderNotification(context: Context, partnerName: String = "Your partner") {
        val prefs = SecurityPreferences.getInstance(context)
        if (!prefs.isNotificationsEnabled()) return
        if (!prefs.isCheckAfterReminderEnabled()) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isDisguised = prefs.isDisguiseActive() && (prefs.isHideNotificationContent() || prefs.isDisguiseModeEnabled())
        val displayTitle = if (isDisguised) prefs.getMaskedNotificationTitle() else "❤️ It's time to check"
        val displayText = if (isDisguised) {
            "Reminder schedule completed"
        } else {
            "$partnerName's check-after time has arrived 💕"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
            .setSmallIcon(if (!prefs.isDisguiseActive()) R.drawable.ic_cherish_heart else R.drawable.ic_stat_notes)
            .setContentTitle(displayTitle)
            .setContentText(displayText)
            .setSubText(if (isDisguised) prefs.getMaskedNotificationTitle() else "Cherish")
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
        // The Notes app's own reminders: only its reminder switch counts (not the chat's
        // notifications switch)
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
            .setSmallIcon(if (!prefs.isDisguiseActive()) R.drawable.ic_cherish_heart else R.drawable.ic_stat_notes)
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


