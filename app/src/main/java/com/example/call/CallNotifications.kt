package com.example.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.example.MainActivity
import com.example.R
import com.example.security.SecurityPreferences

/**
 * Notifications for calls: the ringing one, the one shown during a call (it keeps the call going
 * in the background) and missed calls. When the app's privacy settings hide who's writing, they
 * hide who's calling too: no name, no photo, nothing about the relationship.
 */
object CallNotifications {
    const val CHANNEL_INCOMING = "calls_incoming"
    const val CHANNEL_INCOMING_SILENT = "calls_incoming_silent"
    const val CHANNEL_ONGOING = "calls_ongoing"
    const val CHANNEL_MISSED = "calls_missed"

    const val ID_INCOMING = 2001
    const val ID_ONGOING = 2002
    const val ID_MISSED = 2003

    private const val REQUEST_FULL_SCREEN = 2101
    private const val REQUEST_ANSWER = 2102
    private const val REQUEST_DECLINE = 2103
    private const val REQUEST_HANG_UP = 2104
    private const val REQUEST_SHOW = 2105
    private const val REQUEST_MISSED = 2106

    private const val NEUTRAL_INCOMING = "Incoming call"
    private const val NEUTRAL_ONGOING = "Ongoing call"
    private const val NEUTRAL_MISSED = "Missed call"
    private const val ACCENT = 0xFF2563FF.toInt()

    private val missedShown = LinkedHashSet<String>()

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val ringtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val incoming = NotificationChannel(CHANNEL_INCOMING, "Calls", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Rings when someone calls"
            // The phone's own ringtone, at its ringer volume; silent and vibrate modes apply
            setSound(
                ringtone,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 900, 700, 900, 700)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            setShowBadge(false)
        }
        val incomingSilent = NotificationChannel(CHANNEL_INCOMING_SILENT, "Silent calls", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Silent incoming calls without ringtone"
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 900, 700, 900, 700)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            setShowBadge(false)
        }
        val ongoing = NotificationChannel(CHANNEL_ONGOING, "Call in progress", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while a call is on"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            setShowBadge(false)
        }
        val missed = NotificationChannel(CHANNEL_MISSED, "Missed calls", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Calls you didn't answer"
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannels(listOf(incoming, incomingSilent, ongoing, missed))
    }

    /** The ringing notification, with Answer and Decline. Full screen only where Android allows it. */
    fun showIncoming(context: Context, invite: CallInvite) {
        createChannels(context)
        val prefs = SecurityPreferences.getInstance(context)
        val isMasked = prefs.isHideNotificationContent() || prefs.isDisguiseModeEnabled()
        val isRinging = prefs.isCallRingingEnabled()
        val channelId = if (isRinging) CHANNEL_INCOMING else CHANNEL_INCOMING_SILENT
        val remaining = invite.ringUntil - System.currentTimeMillis()
        val ringFor = if (remaining < 25_000L) 75_000L else remaining.coerceAtMost(90_000L)
        val appIcon = com.example.notifications.NotificationHelper.getAppIconBitmap(context)

        // When masked/hidden notification content is enabled, display discreet reminder notification
        // No full-screen takeover and no answer/decline action buttons
        if (isMasked) {
            val maskedTitle = prefs.getMaskedNotificationTitle()
            val maskedText = prefs.getMaskedNotificationText()
            val openNotesIntent = PendingIntent.getActivity(
                context,
                REQUEST_FULL_SCREEN,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("from_notification", true)
                    putExtra("is_discreet", true)
                    putExtra("incoming_call_masked", true)
                },
                immutableFlags()
            )

            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_stat_notes)
                .apply {
                    if (appIcon != null) setLargeIcon(appIcon)
                }
                .setColor(ACCENT)
                .setContentTitle(maskedTitle)
                .setContentText(maskedText)
                .setSubText(maskedTitle)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setSilent(!isRinging)
                .setTimeoutAfter(ringFor)
                .setContentIntent(openNotesIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(neutralPublic(context, channelId, maskedTitle))

            val notification = builder.build()
            notify(context, ID_INCOMING, notification)
            return
        }

        val showIdentity = CallPrivacy.showIdentity(context)
        val title = if (showIdentity) invite.callerName.ifBlank { "Your partner" } else NEUTRAL_INCOMING
        val text = if (showIdentity) "Incoming voice call" else "Tap to answer"
        val person = Person.Builder().setName(title).setImportant(true).build()
        val fullScreen = PendingIntent.getActivity(
            context, REQUEST_FULL_SCREEN, CallActivity.incomingIntent(context, invite), immutableFlags()
        )
        val answer = PendingIntent.getActivity(
            context, REQUEST_ANSWER, CallActivity.answerIntent(context, invite), immutableFlags()
        )
        val decline = PendingIntent.getBroadcast(
            context, REQUEST_DECLINE, CallActionReceiver.declineIntent(context, invite), immutableFlags()
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_notes)
            .apply {
                if (appIcon != null) setLargeIcon(appIcon)
            }
            .setColor(ACCENT)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(!isRinging)
            .setSilent(!isRinging)
            .setTimeoutAfter(ringFor)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(neutralPublic(context, channelId, NEUTRAL_INCOMING))
            .addPerson(person)

        val notification = try {
            builder.setStyle(NotificationCompat.CallStyle.forIncomingCall(person, decline, answer)).build()
        } catch (e: Exception) {
            Log.w(TAG, "Call style unavailable, using plain actions", e)
            builder.setStyle(null)
                .addAction(0, "Decline", decline)
                .addAction(0, "Answer", answer)
                .build()
        }
        if (isRinging) {
            // Keeps ringing until answered, declined or timed out
            notification.flags = notification.flags or Notification.FLAG_INSISTENT
        }
        notify(context, ID_INCOMING, notification)
    }

    fun cancelIncoming(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(ID_INCOMING)
        } catch (_: Exception) {}
    }

    /** Shown while a call is on; it belongs to the call's foreground service. */
    fun buildOngoing(context: Context, state: CallUiState): Notification {
        createChannels(context)
        val title = state.peerName ?: NEUTRAL_ONGOING
        val text = when {
            state.phase == CallPhase.OUTGOING_RINGING || state.phase == CallPhase.STARTING -> "Calling…"
            state.phase == CallPhase.CONNECTING -> "Connecting…"
            state.phase == CallPhase.RECONNECTING -> "Reconnecting…"
            else -> "Voice call"
        }
        val person = Person.Builder().setName(title).build()
        val hangUp = PendingIntent.getBroadcast(context, REQUEST_HANG_UP, CallActionReceiver.hangUpIntent(context), immutableFlags())
        val show = PendingIntent.getActivity(context, REQUEST_SHOW, CallActivity.showIntent(context), immutableFlags())
        val builder = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_notes)
            .setColor(ACCENT)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(show)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(neutralPublic(context, CHANNEL_ONGOING, NEUTRAL_ONGOING))
            .addPerson(person)
        if (state.connectedAtElapsed > 0L) {
            val connectedAtWall = System.currentTimeMillis() -
                (android.os.SystemClock.elapsedRealtime() - state.connectedAtElapsed)
            builder.setWhen(connectedAtWall).setUsesChronometer(true).setShowWhen(true)
        }
        return try {
            builder.setStyle(NotificationCompat.CallStyle.forOngoingCall(person, hangUp)).build()
        } catch (e: Exception) {
            builder.setStyle(null).addAction(0, "Hang up", hangUp).build()
        }
    }

    fun updateOngoing(context: Context, state: CallUiState) {
        notify(context, ID_ONGOING, buildOngoing(context, state))
    }

    /** A call nobody answered on this phone. Opens the chat like a message notification does. */
    fun showMissed(context: Context, callId: String, callerName: String?) {
        synchronized(missedShown) {
            if (!missedShown.add(callId)) return
            if (missedShown.size > 50) missedShown.remove(missedShown.first())
        }
        createChannels(context)
        val prefs = SecurityPreferences.getInstance(context)
        if (!prefs.isNotificationsEnabled()) return
        val isMasked = prefs.isHideNotificationContent() || prefs.isDisguiseModeEnabled()
        val showIdentity = !isMasked && CallPrivacy.showIdentity(context)
        val isDiscreet = isMasked
        val open = PendingIntent.getActivity(
            context,
            REQUEST_MISSED,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("from_notification", true)
                putExtra("is_discreet", isDiscreet)
                putExtra("open_chat", true)
            },
            immutableFlags()
        )
        val title = if (isMasked) prefs.getMaskedNotificationTitle() else NEUTRAL_MISSED
        val text = if (isMasked) prefs.getMaskedNotificationText() else if (showIdentity && !callerName.isNullOrBlank()) "From $callerName" else "Tap to open"
        val isRinging = prefs.isCallRingingEnabled()
        val notification = NotificationCompat.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_stat_notes)
            .setColor(ACCENT)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(if (isMasked) prefs.getMaskedNotificationTitle() else "Cherish")
            .setCategory(if (isMasked) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setSilent(!isRinging)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(neutralPublic(context, CHANNEL_MISSED, title))
            .build()
        notify(context, ID_MISSED, notification)
    }

    private fun neutralPublic(context: Context, channel: String, title: String): Notification =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_notes)
            .setColor(ACCENT)
            .setContentTitle(title)
            .build()

    private fun notify(context: Context, id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Notifications aren't allowed on this phone", e)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't show the call notification", e)
        }
    }

    private fun immutableFlags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    private const val TAG = "CallNotifications"
}
