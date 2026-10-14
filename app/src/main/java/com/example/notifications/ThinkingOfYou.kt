package com.example.notifications

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.example.security.SecurityPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event emitted when the partner receives and feels our heartbeat trigger.
 */
data class HeartbeatFeltEvent(
    val signalId: String,
    val timestamp: Long
)

/**
 * "Thinking of you": one tap on the partner's phone, a heartbeat (thump-thump… thump-thump) on
 * this one. No words and nothing shown in the notification shade.
 *
 * It's a moment, not a message: it only plays while this phone is in use (screen on and unlocked)
 * and only if it arrives right away. A heartbeat that arrives late, e.g. when the phone is switched
 * back on, is dropped and never played afterwards. Notifications switched off (in the app or for
 * the app in Android) mean no heartbeat either.
 */
object ThinkingOfYou {
    /** A heartbeat older than this (or this far in the future, for clock differences) is dropped. */
    private const val FRESH_WINDOW_MS = 120_000L
    private const val PREFS = "cherish_love"
    private const val KEY_LAST_SIGNAL = "last_heartbeat_signal"

    private val _received = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    /** A heartbeat just played on this phone, for the chat's heart pulse. */
    val received: SharedFlow<Long> = _received.asSharedFlow()

    private val _partnerFelt = MutableSharedFlow<HeartbeatFeltEvent>(extraBufferCapacity = 4)
    /** Partner received and felt our heartbeat trigger. */
    val partnerFelt: SharedFlow<HeartbeatFeltEvent> = _partnerFelt.asSharedFlow()

    private val seen = LinkedHashSet<String>()
    private val seenFelt = LinkedHashSet<String>()

    /**
     * A heartbeat from the partner, from the push or from the couple listener (whichever comes
     * first; the other one is ignored). [sentAt] is the server's time of sending.
     */
    @Synchronized
    fun onSignal(context: Context, signalId: String, sentAt: Long): Boolean {
        if (signalId.isBlank() || !seen.add(signalId)) return false
        if (seen.size > 50) seen.remove(seen.first())
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Each heartbeat is decided once: one that was dropped is never played later
        if (prefs.getString(KEY_LAST_SIGNAL, null) == signalId) return false
        prefs.edit().putString(KEY_LAST_SIGNAL, signalId).apply()

        val secPrefs = SecurityPreferences.getInstance(context)
        if (!secPrefs.isHeartbeatTriggerEnabled()) {
            Log.d("ThinkingOfYou", "Heartbeat trigger is disabled in settings")
            return false
        }

        val isAppInForeground = NotificationHelper.isAppOpenOnScreen(context)

        // If Cherish app is closed or in background, and a notification is already sitting in tray (not cleared),
        // suppress both vibration and notification so duplicate alerts/sounds do not happen.
        if (!isAppInForeground && NotificationHelper.isAlertAlreadyTriggered()) {
            Log.d("ThinkingOfYou", "Prior notification not yet opened/cleared; suppressing duplicate heartbeat alert")
            return false
        }

        // If app is currently open on screen, play gentle heartbeat and emit pulse to chat
        if (isAppInForeground) {
            playHeartbeat(context)
            _received.tryEmit(System.currentTimeMillis())
            return true
        }

        // Cherish is closed/in background and no prior notification is waiting:
        // Alert once according to preferences
        if (secPrefs.isNotificationsEnabled()) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Cherish::HeartbeatWakeLock")
            try {
                wakeLock?.acquire(2000L)
                playHeartbeat(context)
                Log.d("ThinkingOfYou", "Heartbeat vibration played successfully for $signalId")
            } finally {
                if (wakeLock?.isHeld == true) {
                    try { wakeLock.release() } catch (_: Exception) {}
                }
            }

            _received.tryEmit(System.currentTimeMillis())

            if (secPrefs.isBadgeNotificationEnabled()) {
                NotificationHelper.updateLauncherBadge(context, 1)
            }
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                NotificationHelper.showHeartbeatNotification(context)
            }
        }

        return true
    }

    /**
     * Called when the partner acknowledges receiving and feeling our heartbeat trigger.
     */
    @Synchronized
    fun onPartnerFeltHeartbeat(context: Context, signalId: String, feltAt: Long): Boolean {
        if (signalId.isBlank() || !seenFelt.add(signalId)) return false
        if (seenFelt.size > 50) seenFelt.remove(seenFelt.first())

        val secPrefs = SecurityPreferences.getInstance(context)
        if (!secPrefs.isHeartbeatTriggerEnabled()) return false

        // Gentle reciprocal flutter haptic so the sender physically feels partner's acknowledgment
        playReciprocalFeltHaptic(context)

        val event = HeartbeatFeltEvent(signalId, feltAt)
        _partnerFelt.tryEmit(event)
        Log.d("ThinkingOfYou", "Partner felt our heartbeat trigger for signal $signalId")

        val isAppInForeground = NotificationHelper.isAppOpenOnScreen(context)
        if (!isAppInForeground && secPrefs.isNotificationsEnabled()) {
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                NotificationHelper.showHeartbeatFeltNotification(context)
            }
        }
        return true
    }

    /**
     * Two soft heartbeats (thump-thump... thump-thump). Played as a notification vibration, so it
     * also works from the background. Also played on the sender's phone as the heartbeat goes out.
     */
    fun playHeartbeat(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            if (!vibrator.hasVibrator()) return

            // Gentle, intimate heartbeat: thump-thump ... thump-thump (reduced slightly from jarring 255)
            val timings = longArrayOf(0, 42, 120, 65, 560, 42, 120, 65)
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitudes = intArrayOf(0, 95, 0, 175, 0, 95, 0, 175)
                val effect = if (vibrator.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                } else {
                    VibrationEffect.createWaveform(timings, -1)
                }
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, attributes)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1, attributes)
            }
        } catch (e: Exception) {
            Log.w("ThinkingOfYou", "Heartbeat vibration failed", e)
        }
    }

    /**
     * Gentle reciprocal flutter haptic: played on sender's phone when partner feels the heartbeat.
     */
    fun playReciprocalFeltHaptic(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            if (!vibrator.hasVibrator()) return

            // Gentle reciprocal flutter: flutter-flutter (distinct reciprocal feel)
            val timings = longArrayOf(0, 35, 75, 45, 180, 35, 75, 45)
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitudes = intArrayOf(0, 110, 0, 195, 0, 110, 0, 195)
                val effect = if (vibrator.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(timings, amplitudes, -1)
                } else {
                    VibrationEffect.createWaveform(timings, -1)
                }
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, attributes)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1, attributes)
            }
        } catch (e: Exception) {
            Log.w("ThinkingOfYou", "Reciprocal heartbeat vibration failed", e)
        }
    }
}
