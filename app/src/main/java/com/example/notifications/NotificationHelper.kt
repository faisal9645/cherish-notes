package com.example.notifications

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.security.SecurityPreferences
import java.util.concurrent.atomic.AtomicInteger

/**
 * NotificationHelper with stealth/hidden disguised notifications
 * and single-notification consolidation (prevents multiple spam notifications).
 */
object NotificationHelper {
    // The partner's messages ring with their own soft chime (the same bells as the chat's "received"
    // sound), so it's them without looking; a channel's sound is set once, hence the new id
    const val CHANNEL_MESSAGES_ID = "notes_sync_channel_v4"
    private const val CHANNEL_MESSAGES_NAME = "Notes & Messages"
    // From 11 PM (see NightCalm): the same chime, quieter and gentler, and a lighter buzz
    const val CHANNEL_MESSAGES_NIGHT_ID = "notes_sync_night_v1"
    private const val CHANNEL_MESSAGES_NIGHT_NAME = "Notes & Messages (night)"
    const val CHANNEL_REMINDERS_ID = "notes_reminders_channel_v2"
    private const val CHANNEL_REMINDERS_NAME = "Note Reminders"
    private val OLD_CHANNEL_IDS = listOf("notes_sync_channel", "notes_reminders_channel", "notes_sync_channel_v2", "notes_sync_channel_v3")
    @Volatile private var oldChannelsRemoved = false
    const val NOTIFICATION_ID_MESSAGE = 1001

    private val pendingUnreadCount = AtomicInteger(0)
    private val notifiedMessageIds = java.util.Collections.synchronizedSet(LinkedHashSet<String>())
    private val activeNoteReminderIds = java.util.Collections.synchronizedSet(LinkedHashSet<Int>())

    fun getAppIconBitmap(context: Context): Bitmap? {
        return try {
            val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher) ?: return null
            if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 144
                val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 144
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
        } catch (_: Exception) {
            null
        }
    }

    /** The partner's messages' channel right now: the quiet night one from 11 PM to 6 AM. */
    fun messagesChannelId(): String =
        if (com.example.util.NightCalm.isNight()) CHANNEL_MESSAGES_NIGHT_ID else CHANNEL_MESSAGES_ID

    /** Their chime ([night]: the softer one). */
    private fun chimeUri(context: Context, night: Boolean): android.net.Uri =
        android.net.Uri.parse(
            "android.resource://${context.packageName}/" + (if (night) R.raw.partner_chime_night else R.raw.partner_chime)
        )

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Shown on the lock screen; when the phone hides notification content there, each
            // notification's masked public version is what shows (see lockScreenVersion)
            val soundUri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES_ID,
                CHANNEL_MESSAGES_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Discreet sync and reminder notifications"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 200, 250)
                enableLights(true)
                setShowBadge(true)
                setSound(chimeUri(context, night = false), audioAttributes)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }

            val nightMessagesChannel = NotificationChannel(
                CHANNEL_MESSAGES_NIGHT_ID,
                CHANNEL_MESSAGES_NIGHT_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Discreet sync notifications, quieter at night"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 140)
                enableLights(true)
                setShowBadge(true)
                setSound(chimeUri(context, night = true), audioAttributes)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
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
                setSound(soundUri, audioAttributes)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }

            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(messagesChannel)
            manager?.createNotificationChannel(nightMessagesChannel)
            manager?.createNotificationChannel(remindersChannel)
            if (!oldChannelsRemoved) {
                oldChannelsRemoved = true
                OLD_CHANNEL_IDS.forEach { id -> runCatching { manager?.deleteNotificationChannel(id) } }
            }
        }
    }

    /**
     * What a locked phone shows instead of [channelId]'s notification when the phone hides
     * notification content on the lock screen: the app's own masked words, never who or what.
     */
    private fun lockScreenVersion(context: Context, channelId: String, prefs: SecurityPreferences): android.app.Notification =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_notes)
            .setColor(0xFF2563FF.toInt())
            .setContentTitle(prefs.getMaskedNotificationTitle())
            .setContentText(prefs.getMaskedNotificationText())
            .build()

    /**
     * Check if user is actively inside the Cherish app so notifications never show
     * while the user is using the app.
     */
    fun isAppOpenOnScreen(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm?.isInteractive == false) return false
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km?.isKeyguardLocked == true) return false

        if (com.example.CherishApplication.isAppInForeground) return true
        val app = context.applicationContext as? com.example.CherishApplication
        if (app?.authRepository?.isAppOpenOnScreen() == true) return true
        // A photo picker, the camera or a file chooser opened from the app: still using the app
        if (SecurityPreferences.getInstance(context).isInExternalPicker()) return true
        return false
    }

    fun isUserActivelyViewingChat(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (pm?.isInteractive == false) return false
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km?.isKeyguardLocked == true) return false
        return try {
            val app = context.applicationContext as? com.example.CherishApplication
            app?.authRepository?.isUserActivelyInChat() ?: false
        } catch (_: Exception) {
            false
        }
    }

    private val isNotificationAlertTriggered = java.util.concurrent.atomic.AtomicBoolean(false)
    private val hasAlertedWhileLocked = java.util.concurrent.atomic.AtomicBoolean(false)

    fun isDeviceLockedOrSleeping(context: Context): Boolean {
        return try {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isLocked = km?.isKeyguardLocked ?: false
            val isInteractive = pm?.isInteractive ?: false
            isLocked || !isInteractive
        } catch (_: Exception) {
            false
        }
    }

    fun isAlertAlreadyTriggered(): Boolean = isNotificationAlertTriggered.get()

    fun onNotificationDismissed(context: Context, noteId: String? = null) {
        SecurityPreferences.getInstance(context).setAlertedSinceLastAppOpen(false)
        isNotificationAlertTriggered.set(false)
        hasAlertedWhileLocked.set(false)
        pendingUnreadCount.set(0)
        if (!noteId.isNullOrBlank()) {
            activeNoteReminderIds.remove(noteId.hashCode())
        }
        updateLauncherBadge(context, 0)
    }

    private fun getDismissIntent(context: Context): PendingIntent {
        val intent = Intent(context, NotificationDismissedReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getNoteDismissIntent(context: Context, noteId: String): PendingIntent {
        val intent = Intent(context, NotificationDismissedReceiver::class.java).apply {
            putExtra(NotificationDismissedReceiver.EXTRA_NOTE_ID, noteId)
        }
        return PendingIntent.getBroadcast(
            context,
            noteId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        }
        return true
    }

    fun requestIgnoreBatteryOptimization(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                try {
                    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallback)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Show a stealth disguised notification that others will never suspect,
     * consolidated into a single notification without duplicates.
     * Respects user disguise, notification bar, and badge preferences.
     * Capped to always show badge count 1 and trigger 1 time only until the app is opened.
     */
    fun showMessageNotification(
        context: Context,
        senderName: String,
        messageText: String,
        conversationId: String? = null,
        messageId: String? = null
    ) {
        // Don't show notification if user is actively inside the Cherish app or viewing the chat
        if (isUserActivelyViewingChat(context) || isAppOpenOnScreen(context)) {
            return
        }

        // Deduplicate message ID so FCM and Firestore listeners don't double count
        if (!messageId.isNullOrBlank()) {
            synchronized(notifiedMessageIds) {
                if (!notifiedMessageIds.add(messageId)) {
                    return
                }
                if (notifiedMessageIds.size > 200) {
                    val it = notifiedMessageIds.iterator()
                    if (it.hasNext()) {
                        it.next()
                        it.remove()
                    }
                }
            }
        }

        val prefs = SecurityPreferences.getInstance(context)
        val isNotifBarEnabled = prefs.isNotificationsEnabled()
        val isBadgeEnabled = prefs.isBadgeNotificationEnabled()

        // If both notification bar and app badge are disabled, do not alert
        if (!isNotifBarEnabled && !isBadgeEnabled) {
            return
        }

        // WakeLock for 3s to guarantee CPU stays active during screen-off notification delivery
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wl = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "cherish:msg_notif_wake")
            wl?.acquire(3000L)
        } catch (_: Exception) {}

        // Launcher icon badge always shows 1 only, even for multiple notifications
        if (isBadgeEnabled) {
            updateLauncherBadge(context, 1)
        } else {
            updateLauncherBadge(context, 0)
        }

        isNotificationAlertTriggered.set(true)

        // If notification bar is turned off, do not post status bar notification
        if (!isNotifBarEnabled) {
            return
        }

        val isHideContent = prefs.isHideNotificationContent()
        val isDiscreet = isHideContent && prefs.isDisguiseModeEnabled()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("from_notification", true)
            putExtra("is_discreet", isDiscreet)
            putExtra("open_chat", !isDiscreet)
            if (!conversationId.isNullOrBlank()) {
                putExtra("conversationId", conversationId)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // When hide notification content is OFF, behave like normal chat app notifications
        val maskedTitle = prefs.getMaskedNotificationTitle()
        val maskedText = prefs.getMaskedNotificationText()
        val displayTitle = if (isDiscreet) maskedTitle else senderName.ifBlank { "Your Partner" }
        val displayText = if (isDiscreet) {
            maskedText
        } else {
            messageText.ifBlank { "New message" }
        }
        val subText = if (isDiscreet || maskedTitle.equals("Notes", ignoreCase = true)) null else "Cherish"

        val appIcon = getAppIconBitmap(context)

        // Ensure channel is created before building the notification so it never drops
        createNotificationChannels(context)

        val notificationBuilder = NotificationCompat.Builder(context, messagesChannelId())
            .setSmallIcon(R.drawable.ic_stat_notes)
            .apply {
                if (appIcon != null) setLargeIcon(appIcon)
            }
            .setColor(0xFF2563FF.toInt())
            .setContentTitle(displayTitle)
            .setContentText(displayText)
            .apply {
                if (!subText.isNullOrBlank() && !subText.equals("Notes", ignoreCase = true)) {
                    setSubText(subText)
                }
            }
            .apply {
                if (!isDiscreet) {
                    setStyle(NotificationCompat.BigTextStyle().bigText(displayText))
                }
            }
            .setBadgeIconType(if (isBadgeEnabled) NotificationCompat.BADGE_ICON_SMALL else NotificationCompat.BADGE_ICON_NONE)
            .setNumber(if (isBadgeEnabled) 1 else 0)
            .setAutoCancel(true)
            .setOngoing(false)
            .setOnlyAlertOnce(false)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(getDismissIntent(context))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            // Before Android 8 (no channels): their chime, softer at night
            .setSound(chimeUri(context, night = com.example.util.NightCalm.isNight()))
            .setVibrate(if (com.example.util.NightCalm.isNight()) longArrayOf(0, 140) else longArrayOf(0, 250, 200, 250))
            // On the lock screen too (masked there when the phone hides notification content)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreenVersion(context, messagesChannelId(), prefs))

        val notification = notificationBuilder.build()

        try {
            createNotificationChannels(context)
            if (isAppOpenOnScreen(context) || isUserActivelyViewingChat(context)) {
                return
            }
            // Cancel legacy separate heartbeat notification from older builds so only 1 notification is ever visible
            val nmCompat = NotificationManagerCompat.from(context)
            val nmSystem = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nmCompat.cancel(1003)
            nmSystem?.cancel(1003)
            nmCompat.notify(NOTIFICATION_ID_MESSAGE, notification)
            android.util.Log.d("NotificationHelper", "Message notification posted successfully (badge 1)")
        } catch (e: SecurityException) {
            android.util.Log.w("NotificationHelper", "POST_NOTIFICATIONS runtime permission not granted: ${e.message}")
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Failed to post notification", e)
        }
    }

    const val NOTIFICATION_ID_HEARTBEAT = NOTIFICATION_ID_MESSAGE

    /**
     * Show a heartbeat notification when the app is closed/in background,
     * respecting disguise settings (disguised notes reminder or sweet heartbeat).
     */
    fun showHeartbeatNotification(context: Context, signalId: String = "") {
        if (isAppOpenOnScreen(context)) return
        val prefs = SecurityPreferences.getInstance(context)
        if (!prefs.isNotificationsEnabled()) return
        if (!prefs.isHeartbeatTriggerEnabled()) return

        val isDiscreet = prefs.isHideNotificationContent() || prefs.isDisguiseModeEnabled()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("from_notification", true)
            putExtra("is_discreet", isDiscreet)
            putExtra("open_chat", true)
            if (signalId.isNotBlank()) {
                putExtra("heartbeat_signal_id", signalId)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val maskedTitle = prefs.getMaskedNotificationTitle()
        val maskedText = prefs.getMaskedNotificationText()
        val displayTitle = if (isDiscreet) maskedTitle else "Thinking of you"
        val displayText = if (isDiscreet) maskedText else "Sent a heartbeat \uD83D\uDC93"
        val subText = if (isDiscreet || maskedTitle.equals("Notes", ignoreCase = true)) null else "Cherish"

        val isBadgeEnabled = prefs.isBadgeNotificationEnabled()
        val appIcon = getAppIconBitmap(context)

        // Locked: one alert, then silent updates (as for messages)
        val isLocked = isDeviceLockedOrSleeping(context)
        val silentUpdate = isLocked && hasAlertedWhileLocked.get()
        hasAlertedWhileLocked.set(isLocked)

        if (isBadgeEnabled) {
            updateLauncherBadge(context, 1)
        }
        isNotificationAlertTriggered.set(true)

        val notification = NotificationCompat.Builder(context, messagesChannelId())
            .setSmallIcon(R.drawable.ic_stat_notes)
            .apply {
                if (appIcon != null) setLargeIcon(appIcon)
            }
            .setColor(0xFF2563FF.toInt())
            .setContentTitle(displayTitle)
            .setContentText(displayText)
            .apply {
                if (!subText.isNullOrBlank() && !subText.equals("Notes", ignoreCase = true)) {
                    setSubText(subText)
                }
            }
            .setBadgeIconType(if (isBadgeEnabled) NotificationCompat.BADGE_ICON_SMALL else NotificationCompat.BADGE_ICON_NONE)
            .setNumber(if (isBadgeEnabled) 1 else 0)
            .setAutoCancel(true)
            .setOngoing(false)
            .setOnlyAlertOnce(false)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(getDismissIntent(context))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 100, 150, 100, 450, 100, 150, 100))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreenVersion(context, messagesChannelId(), prefs))
            .build()

        try {
            createNotificationChannels(context)
            if (isAppOpenOnScreen(context)) return
            val nmCompat = NotificationManagerCompat.from(context)
            val nmSystem = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nmCompat.cancel(1003)
            nmSystem?.cancel(1003)
            nmCompat.notify(NOTIFICATION_ID_MESSAGE, notification)
        } catch (_: SecurityException) {} catch (_: Exception) {}
    }

    /**
     * Show a notification when the partner felt our heartbeat trigger.
     */
    fun showHeartbeatFeltNotification(context: Context) {
        if (isAppOpenOnScreen(context)) return
        val prefs = SecurityPreferences.getInstance(context)
        if (!prefs.isNotificationsEnabled()) return
        if (!prefs.isHeartbeatTriggerEnabled()) return

        val isDiscreet = prefs.isHideNotificationContent() || prefs.isDisguiseModeEnabled()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("from_notification", true)
            putExtra("is_discreet", isDiscreet)
            putExtra("open_chat", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val maskedTitle = prefs.getMaskedNotificationTitle()
        val maskedText = prefs.getMaskedNotificationText()
        val displayTitle = if (isDiscreet) maskedTitle else "Heartbeat Felt"
        val displayText = if (isDiscreet) maskedText else "Your partner felt your heartbeat \uD83D\uDC93"
        val subText = if (isDiscreet || maskedTitle.equals("Notes", ignoreCase = true)) null else "Cherish"

        val appIcon = getAppIconBitmap(context)
        val notification = NotificationCompat.Builder(context, messagesChannelId())
            .setSmallIcon(R.drawable.ic_stat_notes)
            .apply {
                if (appIcon != null) setLargeIcon(appIcon)
            }
            .setColor(0xFF2563FF.toInt())
            .setContentTitle(displayTitle)
            .setContentText(displayText)
            .apply {
                if (!subText.isNullOrBlank() && !subText.equals("Notes", ignoreCase = true)) {
                    setSubText(subText)
                }
            }
            .setAutoCancel(true)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(getDismissIntent(context))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreenVersion(context, messagesChannelId(), prefs))
            .build()

        try {
            createNotificationChannels(context)
            if (isAppOpenOnScreen(context)) return
            val nmCompat = NotificationManagerCompat.from(context)
            nmCompat.notify(NOTIFICATION_ID_HEARTBEAT, notification)
        } catch (_: SecurityException) {} catch (_: Exception) {}
    }

    /**
     * Cancels status bar notifications without resetting the unread launcher badge.
     */
    fun cancelStatusBarNotifications(context: Context) {
        try {
            val nmCompat = NotificationManagerCompat.from(context)
            val nmSystem = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

            nmCompat.cancel(NOTIFICATION_ID_MESSAGE)
            nmCompat.cancel(NOTIFICATION_ID_CHECK_AFTER)
            nmCompat.cancel(NOTIFICATION_ID_HEARTBEAT)
            nmCompat.cancel(1003)

            nmSystem?.cancel(NOTIFICATION_ID_MESSAGE)
            nmSystem?.cancel(NOTIFICATION_ID_CHECK_AFTER)
            nmSystem?.cancel(NOTIFICATION_ID_HEARTBEAT)
            nmSystem?.cancel(1003)

            synchronized(activeNoteReminderIds) {
                for (id in activeNoteReminderIds) {
                    nmCompat.cancel(id)
                    nmSystem?.cancel(id)
                }
                activeNoteReminderIds.clear()
            }

            // Everything else too, except a ringing or ongoing call's: those go when the call does
            // (opening the app while it rings mustn't silence it)
            val callIds = setOf(com.example.call.CallNotifications.ID_INCOMING, com.example.call.CallNotifications.ID_ONGOING)
            nmSystem?.activeNotifications?.forEach { shown ->
                if (shown.id !in callIds) nmSystem?.cancel(shown.tag, shown.id)
            }
        } catch (_: Exception) {}
    }

    /**
     * Clears notifications and resets the counter and badge when the user views the app
     */
    fun clearNotifications(context: Context) {
        SecurityPreferences.getInstance(context).setAlertedSinceLastAppOpen(false)
        isNotificationAlertTriggered.set(false)
        hasAlertedWhileLocked.set(false)
        pendingUnreadCount.set(0)
        notifiedMessageIds.clear()

        cancelStatusBarNotifications(context)
        updateLauncherBadge(context, 0)

        // Delayed safety sweep: if an in-flight background worker (FCM/Firestore/alarm)
        // posts right as the app is transitioning to foreground, this cleans it up cleanly.
        try {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (isAppOpenOnScreen(context)) {
                    cancelStatusBarNotifications(context)
                    updateLauncherBadge(context, 0)
                }
            }, 400L)
        } catch (_: Exception) {}
    }

    /**
     * Broadcasts unread count badge to Samsung, Sony, HTC and compatible OEM launchers
     * Always shows 1 only when there are unread notifications.
     */
    fun updateLauncherBadge(context: Context, count: Int) {
        val badgeCount = if (count > 0) 1 else 0
        try {
            val intent = Intent("android.intent.action.BADGE_COUNT_UPDATE").apply {
                putExtra("badge_count", badgeCount)
                putExtra("badge_count_package_name", context.packageName)
                putExtra("badge_count_class_name", "com.example.MainActivity")
            }
            context.sendBroadcast(intent)
        } catch (_: Exception) {}

        // Sony Xperia launcher
        try {
            val intent = Intent("com.sonyericsson.home.action.UPDATE_BADGE").apply {
                putExtra("com.sonyericsson.home.intent.extra.badge.PACKAGE_NAME", context.packageName)
                putExtra("com.sonyericsson.home.intent.extra.badge.ACTIVITY_NAME", "com.example.MainActivity")
                putExtra("com.sonyericsson.home.intent.extra.badge.SHOW_MESSAGE", badgeCount > 0)
                putExtra("com.sonyericsson.home.intent.extra.badge.MESSAGE", badgeCount.toString())
            }
            context.sendBroadcast(intent)
        } catch (_: Exception) {}

        // Samsung Native badge ContentProvider
        try {
            val uri = android.net.Uri.parse("content://com.sec.badge/apps")
            val cv = android.content.ContentValues().apply {
                put("package", context.packageName)
                put("class", "com.example.MainActivity")
                put("badgecount", badgeCount)
            }
            context.contentResolver.insert(uri, cv)
        } catch (_: Exception) {}

        // HTC launcher
        try {
            val intent = Intent("com.htc.launcher.action.UPDATE_SHORTCUT").apply {
                putExtra("packagename", context.packageName)
                putExtra("count", badgeCount)
            }
            context.sendBroadcast(intent)
        } catch (_: Exception) {}
    }

    const val NOTIFICATION_ID_CHECK_AFTER = 1002

    /**
     * Subtle reminder notification when partner's Check-After countdown finishes
     */
    fun showCheckAfterReminderNotification(context: Context, partnerName: String = "Your partner") {
        if (isAppOpenOnScreen(context)) return
        val prefs = SecurityPreferences.getInstance(context)
        if (!prefs.isNotificationsEnabled()) return
        if (!prefs.isCheckAfterReminderEnabled()) return

        val isDisguised = prefs.isHideNotificationContent() && prefs.isDisguiseModeEnabled()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("from_notification", true)
            putExtra("is_discreet", isDisguised)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayTitle = if (isDisguised) prefs.getMaskedNotificationTitle() else "It's time to check"
        val displayText = if (isDisguised) {
            "Reminder schedule completed"
        } else {
            "$partnerName's check-after time has arrived"
        }

        val appIcon = getAppIconBitmap(context)
        val notification = NotificationCompat.Builder(context, messagesChannelId())
            .setSmallIcon(R.drawable.ic_stat_notes)
            .apply {
                if (appIcon != null) setLargeIcon(appIcon)
            }
            .setColor(0xFF2563FF.toInt())
            .setContentTitle(displayTitle)
            .setContentText(displayText)
            .apply {
                if (!isDisguised) {
                    setSubText("Cherish")
                }
            }
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(getDismissIntent(context))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreenVersion(context, messagesChannelId(), prefs))
            .build()

        try {
            createNotificationChannels(context)
            if (isAppOpenOnScreen(context)) return
            val nmCompat = NotificationManagerCompat.from(context)
            nmCompat.notify(NOTIFICATION_ID_CHECK_AFTER, notification)
        } catch (_: SecurityException) {
            // Handled if POST_NOTIFICATIONS runtime permission not yet prompted
        } catch (_: Exception) {}
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
        if (isAppOpenOnScreen(context)) return

        // The Notes app's own reminders: only its reminder switch counts (not the chat's
        // notifications switch)
        val prefs = SecurityPreferences.getInstance(context)
        if (!prefs.isNoteRemindersEnabled()) return

        val notifId = noteId.hashCode()
        activeNoteReminderIds.add(notifId)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_note_id", noteId)
            putExtra("from_notification", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val displayTitle = title.ifBlank { "Note Reminder" }
        val displayText = if (content.isNotBlank()) content else "You have a scheduled note reminder."

        val reminderAppIcon = getAppIconBitmap(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS_ID)
            .setSmallIcon(R.drawable.ic_stat_notes)
            .apply {
                if (reminderAppIcon != null) setLargeIcon(reminderAppIcon)
            }
            .setColor(0xFF2563FF.toInt())
            .setContentTitle(displayTitle)
            .setContentText(displayText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayText))
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(pendingIntent)
            .setDeleteIntent(getNoteDismissIntent(context, noteId))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // A note's own reminder: shown in full on the lock screen, like any notes app's
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        try {
            createNotificationChannels(context)
            if (isAppOpenOnScreen(context)) return
            val nmCompat = NotificationManagerCompat.from(context)
            nmCompat.notify(notifId, notification)
        } catch (_: SecurityException) {
            // Handled if POST_NOTIFICATIONS runtime permission not yet prompted
        } catch (_: Exception) {}
    }

    fun cancelNoteReminderNotification(context: Context, noteId: String) {
        try {
            val notifId = noteId.hashCode()
            activeNoteReminderIds.remove(notifId)
            val nmCompat = NotificationManagerCompat.from(context)
            val nmSystem = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nmCompat.cancel(notifId)
            nmSystem?.cancel(notifId)
        } catch (_: Exception) {}
    }
}


