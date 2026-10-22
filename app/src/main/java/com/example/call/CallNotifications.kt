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
    // New ids in 1.7.90: earlier channels could have been made hidden on the lock screen, and an
    // app can't change that on a channel that already exists
    const val CHANNEL_INCOMING = "calls_incoming_v2"
    const val CHANNEL_INCOMING_SILENT = "calls_incoming_silent_v2"
    /** Shows the call without a sound or vibration of its own: the app rings (CallRinger). */
    const val CHANNEL_INCOMING_QUIET = "calls_ringing_v3"
    const val CHANNEL_ONGOING = "calls_ongoing_v2"
    const val CHANNEL_MISSED = "calls_missed_v2"
    private val OLD_CHANNEL_IDS = listOf("calls_incoming", "calls_incoming_silent", "calls_ongoing", "calls_missed")
    @Volatile private var oldChannelsRemoved = false

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
        val incoming = NotificationChannel(CHANNEL_INCOMING, "Priority alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Time-sensitive alerts that ring"
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
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        val incomingSilent = NotificationChannel(CHANNEL_INCOMING_SILENT, "Priority alerts (silent)", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Time-sensitive alerts without sound"
            setSound(null, null)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 900, 700, 900, 700)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        val incomingQuiet = NotificationChannel(CHANNEL_INCOMING_QUIET, "Priority alerts (quiet)", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Shown while the app sounds an alert"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        val ongoing = NotificationChannel(CHANNEL_ONGOING, "Ongoing activity", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while something is in progress"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            setShowBadge(false)
        }
        val missed = NotificationChannel(CHANNEL_MISSED, "Missed alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Alerts you didn't see"
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannels(listOf(incoming, incomingSilent, incomingQuiet, ongoing, missed))
        if (!oldChannelsRemoved) {
            oldChannelsRemoved = true
            OLD_CHANNEL_IDS.forEach { id -> runCatching { manager.deleteNotificationChannel(id) } }
        }
    }

    /** What a locked phone shows instead when the phone hides notification content there: no name. */
    private fun lockScreenVersion(
        context: Context,
        channelId: String,
        title: String,
        text: String,
        answer: PendingIntent? = null,
        decline: PendingIntent? = null,
        fullScreen: PendingIntent? = null,
        timeoutMs: Long = 0L
    ): Notification {
        val prefs = SecurityPreferences.getInstance(context)
        val isCalc = prefs.isDisguiseModeEnabled() && prefs.getDisguiseStyle() == SecurityPreferences.DISGUISE_STYLE_CALCULATOR
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(if (isCalc) R.drawable.ic_stat_calculator else R.drawable.ic_stat_notes)
            .setColor(if (isCalc) 0xFF2E7D32.toInt() else ACCENT)
            .setContentTitle(title)
            .setContentText(text)
            .apply {
                if (fullScreen != null) {
                    setContentIntent(fullScreen)
                    setFullScreenIntent(fullScreen, true)
                    setCategory(NotificationCompat.CATEGORY_CALL)
                    setPriority(NotificationCompat.PRIORITY_MAX)
                    setOngoing(true)
                    setAutoCancel(false)
                }
                if (timeoutMs > 0L) setTimeoutAfter(timeoutMs)
                if (decline != null) addAction(0, "Decline", decline)
                if (answer != null) addAction(0, "Answer", answer)
            }
            .build()
    }

    /** The call whose ringing notification is up, and the call that was quietened (a volume key). */
    @Volatile private var shownCallId: String? = null
    @Volatile private var silencedCallId: String? = null

    /** What makes a ringing call's notification heard. */
    internal enum class Ringing {
        /** The app rings (CallRinger, kept running by CallService): the notification makes no sound. */
        APP,
        /** The notification rings by itself, the ringtone (or vibration) over and over. */
        NOTIFICATION,
        /** Quietened: no sound or vibration, and no full screen (it would turn the screen back on). */
        QUIET
    }

    /**
     * The ringing call's notification, out of its call screen: a call with Answer and Decline (and
     * the call screen over the lock screen, where Android allows it), or the disguised alert when
     * Incoming Call Privacy is Discreet. With Call Notification Ringing on it rings, the phone's
     * ringtone over and over, until it's answered, declined, called off or rings out; off, it
     * vibrates the same way, without the ringtone. The phone's Silent and Vibrate modes apply.
     *
     * The app rings itself, from a short foreground service (a phone call) that keeps it awake and
     * running while it rings, also on a locked or idle phone and when the push started the app;
     * the notification is that service's own. Where Android doesn't allow the service just then,
     * the notification rings by itself instead. Already up for this call: it just goes on ringing.
     */
    fun showIncoming(context: Context, invite: CallInvite) {
        createChannels(context)
        if (shownCallId == invite.callId && (CallService.isRingingFor(invite.callId) || isIncomingShown(context))) return
        shownCallId = invite.callId
        if (silencedCallId == invite.callId) {
            showRingingNotification(context, invite)
            return
        }
        // Shown at once (the banner, or the call screen on a locked phone, with no delay) and rung by
        // the app alone, one ringtone: never the notification's own as well.
        ringFromApp(context, invite)
        CallService.startRinging(context, invite)
        val ringingMode = if (CallRinger.isRinging()) Ringing.APP else Ringing.NOTIFICATION
        notify(context, ID_INCOMING, buildIncoming(context, invite, ringingMode))
    }

    /** The notification shows the call and rings by itself (the app's ringing service isn't up). */
    internal fun showRingingNotification(context: Context, invite: CallInvite) {
        shownCallId = invite.callId
        val ringing = if (silencedCallId == invite.callId) Ringing.QUIET else Ringing.NOTIFICATION
        notify(context, ID_INCOMING, buildIncoming(context, invite, ringing))
    }

    /** The ringing call's notification (see showIncoming); [ringing] says what's heard. */
    internal fun buildIncoming(context: Context, invite: CallInvite, ringing: Ringing): Notification {
        createChannels(context)
        val prefs = SecurityPreferences.getInstance(context)
        val isMasked = prefs.getCallMaskingStyle().equals("discreet", ignoreCase = true) || prefs.isDisguiseActive()
        val silenced = ringing == Ringing.QUIET
        val channelId = when (ringing) {
            // Ringing by itself: its own ringtone (with Call Notification Ringing on) or vibration
            Ringing.NOTIFICATION -> if (prefs.isCallRingingEnabled()) CHANNEL_INCOMING else CHANNEL_INCOMING_SILENT
            // The app rings (CallRinger), so the notification makes no sound of its own; or quietened
            Ringing.APP, Ringing.QUIET -> CHANNEL_INCOMING_QUIET
        }
        val remaining = invite.ringUntil - System.currentTimeMillis()
        val ringFor = if (remaining < 25_000L) 75_000L else remaining.coerceAtMost(90_000L)
        val appIcon = com.example.notifications.NotificationHelper.getAppIconBitmap(context)
        val answer = PendingIntent.getActivity(
            context, REQUEST_ANSWER, CallActivity.answerIntent(context, invite), immutableFlags()
        )
        val decline = PendingIntent.getBroadcast(
            context, REQUEST_DECLINE, CallActionReceiver.declineIntent(context, invite), immutableFlags()
        )

        val notification = if (isMasked) {
            // Discreet: the disguised alert. It opens Notes; the call screen shows once Cherish is
            // unlocked, and answering from it asks to unlock the phone first
            val isCalc = prefs.isDisguiseModeEnabled() && prefs.getDisguiseStyle() == SecurityPreferences.DISGUISE_STYLE_CALCULATOR
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
            NotificationCompat.Builder(context, channelId)
                .setSmallIcon(if (isCalc) R.drawable.ic_stat_calculator else R.drawable.ic_stat_notes)
                .apply {
                    if (appIcon != null) setLargeIcon(appIcon)
                }
                .setColor(if (isCalc) 0xFF2E7D32.toInt() else ACCENT)
                .setContentTitle(maskedTitle)
                .setContentText(maskedText)
                .apply {
                    if (!maskedTitle.equals("Notes", ignoreCase = true) && !maskedTitle.equals("Calculator", ignoreCase = true)) {
                        setSubText(maskedTitle)
                    }
                }
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setAutoCancel(false)
                .setSilent(silenced)
                .setTimeoutAfter(ringFor)
                .setContentIntent(openNotesIntent)
                .addAction(0, "Decline", decline)
                .addAction(0, "Answer", answer)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(lockScreenVersion(context, channelId, maskedTitle, maskedText, answer, decline, openNotesIntent, ringFor))
                .build()
        } else {
            val showIdentity = CallPrivacy.showIdentity(context)
            val title = if (showIdentity) invite.callerName.ifBlank { "Your partner" } else NEUTRAL_INCOMING
            val text = if (showIdentity) "Incoming voice call" else "Tap to answer"
            val person = Person.Builder().setName(title).setImportant(true).build()
            val fullScreen = PendingIntent.getActivity(
                context, REQUEST_FULL_SCREEN, CallActivity.incomingIntent(context, invite), immutableFlags()
            )
            val isCalc = prefs.isDisguiseModeEnabled() && prefs.getDisguiseStyle() == SecurityPreferences.DISGUISE_STYLE_CALCULATOR
            val builder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(if (isCalc) R.drawable.ic_stat_calculator else R.drawable.ic_stat_notes)
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
                .setSilent(silenced)
                .setTimeoutAfter(ringFor)
                .setContentIntent(fullScreen)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(lockScreenVersion(context, channelId, NEUTRAL_INCOMING, "Tap to answer", answer, decline, fullScreen, ringFor))
                .addPerson(person)
            if (silenced) {
                // Android only takes a call-style notification with a full screen (or a call service)
                builder.addAction(0, "Decline", decline).addAction(0, "Answer", answer).build()
            } else {
                builder.setFullScreenIntent(fullScreen, true)
                try {
                    builder.setStyle(NotificationCompat.CallStyle.forIncomingCall(person, decline, answer)).build()
                } catch (e: Exception) {
                    Log.w(TAG, "Call style unavailable, using plain actions", e)
                    builder.setStyle(null)
                        .addAction(0, "Decline", decline)
                        .addAction(0, "Answer", answer)
                        .build()
                }
            }
        }
        if (ringing == Ringing.NOTIFICATION) {
            // Rings (or vibrates) like a call until it's answered, declined, called off or rings out
            notification.flags = notification.flags or Notification.FLAG_INSISTENT
        }
        return notification
    }

    /**
     * The app rings for the ringing call: on its call screen in Cherish (no notification then), or
     * from the ringing service. Not once it was quietened.
     */
    fun ringFromApp(context: Context, invite: CallInvite) {
        // Quietened, or ringing already (the call screen, then its service): not started over
        if (silencedCallId == invite.callId || CallRinger.isRinging()) return
        CallRinger.start(context, withRingtone = SecurityPreferences.getInstance(context).isCallRingingEnabled())
    }

    /**
     * A volume key on the call screen while it rings: quiet, like a phone call. The call stays on
     * screen and in its notification, silently, until it's answered, declined, called off or rings out.
     */
    fun silence(context: Context, invite: CallInvite) {
        if (silencedCallId == invite.callId) return
        silencedCallId = invite.callId
        CallRinger.stop(context)
        // The ringing service's notification makes no sound of its own: stopping the app's ringing is enough
        if (CallService.isRingingFor(invite.callId)) return
        if (shownCallId == invite.callId && isIncomingShown(context)) {
            // Up again without sound: Android stops a notification's sound when it goes
            try {
                NotificationManagerCompat.from(context).cancel(ID_INCOMING)
            } catch (_: Exception) {}
            showRingingNotification(context, invite)
        }
    }

    /**
     * The call stopped ringing here (answered, declined, called off, missed): its ringing stops,
     * and the ringing service with it unless [keepService] (an answered call keeps it, as the call's).
     */
    fun cancelIncoming(context: Context, keepService: Boolean = false) {
        CallRinger.stop(context)
        shownCallId = null
        if (!keepService) CallService.stopRinging(context)
        try {
            NotificationManagerCompat.from(context).cancel(ID_INCOMING)
        } catch (_: Exception) {}
    }

    private fun isIncomingShown(context: Context): Boolean = try {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            ?.activeNotifications?.any { it.id == ID_INCOMING } == true
    } catch (_: Exception) {
        false
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
        val prefs = SecurityPreferences.getInstance(context)
        val isCalc = prefs.isDisguiseModeEnabled() && prefs.getDisguiseStyle() == SecurityPreferences.DISGUISE_STYLE_CALCULATOR
        val builder = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(if (isCalc) R.drawable.ic_stat_calculator else R.drawable.ic_stat_notes)
            .setColor(if (isCalc) 0xFF2E7D32.toInt() else ACCENT)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(show)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(lockScreenVersion(context, CHANNEL_ONGOING, NEUTRAL_ONGOING, "Voice call"))
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
        // Inside the app it's on the call screen and in the chat already: nothing pops up
        if (com.example.notifications.NotificationHelper.isAppOpenOnScreen(context)) return
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
        val isCalc = prefs.isDisguiseModeEnabled() && prefs.getDisguiseStyle() == SecurityPreferences.DISGUISE_STYLE_CALCULATOR
        val title = if (isMasked) prefs.getMaskedNotificationTitle() else NEUTRAL_MISSED
        val text = if (isMasked) prefs.getMaskedNotificationText() else if (showIdentity && !callerName.isNullOrBlank()) "From $callerName" else "Tap to open"
        val isRinging = prefs.isCallRingingEnabled()
        val appIcon = com.example.notifications.NotificationHelper.getAppIconBitmap(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(if (isCalc) R.drawable.ic_stat_calculator else R.drawable.ic_stat_notes)
            .apply {
                if (appIcon != null) setLargeIcon(appIcon)
            }
            .setColor(if (isCalc) 0xFF2E7D32.toInt() else ACCENT)
            .setContentTitle(title)
            .setContentText(text)
            .apply {
                if (!isMasked) {
                    setSubText("Cherish")
                }
            }
            .setCategory(if (isMasked) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_MISSED_CALL)
            .setAutoCancel(true)
            .setSilent(!isRinging)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                lockScreenVersion(
                    context,
                    CHANNEL_MISSED,
                    if (isMasked) prefs.getMaskedNotificationTitle() else NEUTRAL_MISSED,
                    if (isMasked) prefs.getMaskedNotificationText() else "Tap to open"
                )
            )
            .build()
        notify(context, ID_MISSED, notification)
    }


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
