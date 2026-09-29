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
    private const val NOTIFICATION_ID_MESSAGE = 1001

    private val pendingUnreadCount = AtomicInteger(0)

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
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

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
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

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("conversationId", conversationId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Completely stealth notification disguise so nobody glancing at the phone notices
        val displayTitle = "Notes"
        val displayText = if (count <= 1) {
            "Checklist reminder updated"
        } else {
            "$count items synchronized"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_MESSAGES_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(displayTitle)
            .setContentText(displayText)
            .setSubText("Notes")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOnlyAlertOnce(true) // Never buzz multiple times for updates
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
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
        } catch (_: Exception) {}
    }
}
