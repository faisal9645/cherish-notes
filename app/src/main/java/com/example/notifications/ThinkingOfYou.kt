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
    /**
     * A heartbeat, or its "felt it", older than this (or this far in the future, for phone clocks
     * that are off) is dropped: e.g. the last one still on the couple record when the app starts.
     */
    private const val FRESH_WINDOW_MS = 10L * 60 * 1000
    private const val PREFS = "cherish_love"
    private const val KEY_LAST_SIGNAL = "last_heartbeat_signal"
    /** Heartbeats this phone sent and hasn't been told were felt: "id:sentAt", newest last. */
    private const val KEY_SENT_SIGNALS = "sent_heartbeat_signals"
    private const val MAX_SENT_SIGNALS = 20

    private fun isFresh(at: Long): Boolean = at <= 0L || kotlin.math.abs(System.currentTimeMillis() - at) <= FRESH_WINDOW_MS

    /** Remembers a heartbeat this phone is sending, so only this phone is told it was felt. */
    @Synchronized
    fun rememberSent(context: Context, signalId: String) {
        if (signalId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val kept = sentSignals(prefs).filter { (id, at) -> id != signalId && now - at < 24 * 60 * 60 * 1000L }
        val updated = (kept + (signalId to now)).takeLast(MAX_SENT_SIGNALS)
        prefs.edit().putString(KEY_SENT_SIGNALS, updated.joinToString(",") { (id, at) -> "$id:$at" }).apply()
    }

    /** True once for a heartbeat this phone sent (it's then forgotten, so it's never shown twice). */
    @Synchronized
    private fun takeSent(context: Context, signalId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sent = sentSignals(prefs)
        val match = sent.firstOrNull { it.first == signalId } ?: return false
        prefs.edit().putString(KEY_SENT_SIGNALS, (sent - match).joinToString(",") { (id, at) -> "$id:$at" }).apply()
        return true
    }

    private fun sentSignals(prefs: android.content.SharedPreferences): List<Pair<String, Long>> =
        prefs.getString(KEY_SENT_SIGNALS, null).orEmpty().split(",").mapNotNull { entry ->
            val id = entry.substringBeforeLast(":", "")
            val at = entry.substringAfterLast(":", "").toLongOrNull()
            if (id.isBlank() || at == null) null else id to at
        }

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
        // A moment, not a message: one that arrives late isn't played (nor answered "felt")
        if (!isFresh(sentAt)) {
            Log.d("ThinkingOfYou", "Heartbeat $signalId arrived too late, dropped")
            return false
        }

        val secPrefs = SecurityPreferences.getInstance(context)
        if (!secPrefs.isHeartbeatTriggerEnabled()) {
            Log.d("ThinkingOfYou", "Heartbeat trigger is disabled in settings")
            return false
        }

        val isAppInForeground = NotificationHelper.isAppOpenOnScreen(context)

        // If app is currently open on screen, play gentle heartbeat and emit pulse to chat
        if (isAppInForeground) {
            playHeartbeat(context)
            _received.tryEmit(System.currentTimeMillis())
            return true
        }

        // Cherish is closed/in background:
        // Always play intimate heartbeat haptic and post notification according to preferences
        if (secPrefs.isNotificationsEnabled()) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Cherish::HeartbeatWakeLock")
            try {
                wakeLock?.acquire(3000L)
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
                NotificationHelper.showHeartbeatNotification(context, signalId)
            }
        }

        return true
    }

    private const val KEY_PENDING_FELT_SIGNAL = "pending_felt_signal_id"
    private const val KEY_PENDING_FELT_AT = "pending_felt_sent_at"

    fun isScreenInteractive(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isInteractive == true
    }

    fun setPendingHeartbeatFelt(context: Context, signalId: String, sentAt: Long) {
        if (signalId.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PENDING_FELT_SIGNAL, signalId)
            .putLong(KEY_PENDING_FELT_AT, sentAt)
            .apply()
    }

    fun checkPendingHeartbeatFelt(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val signalId = prefs.getString(KEY_PENDING_FELT_SIGNAL, null) ?: return
        val sentAt = prefs.getLong(KEY_PENDING_FELT_AT, 0L)
        prefs.edit().remove(KEY_PENDING_FELT_SIGNAL).remove(KEY_PENDING_FELT_AT).apply()
        // Only acknowledge if received within 5 minutes while partner's mobile was on
        if (signalId.isNotBlank() && (System.currentTimeMillis() - sentAt < 300_000L)) {
            val app = context.applicationContext as? com.example.CherishApplication
            app?.authRepository?.acknowledgeHeartbeatFelt(signalId)
        }
    }

    /**
     * Called when the partner acknowledges receiving and feeling our heartbeat trigger. Only the
     * phone that sent that heartbeat is told, once, and only while it's fresh.
     */
    @Synchronized
    fun onPartnerFeltHeartbeat(context: Context, signalId: String, feltAt: Long): Boolean {
        if (signalId.isBlank() || !seenFelt.add(signalId)) return false
        if (seenFelt.size > 50) seenFelt.remove(seenFelt.first())
        if (!isFresh(feltAt) || !takeSent(context, signalId)) return false

        val secPrefs = SecurityPreferences.getInstance(context)
        if (!secPrefs.isHeartbeatTriggerEnabled()) return false

        val isAppInForeground = NotificationHelper.isAppOpenOnScreen(context)

        if (!isAppInForeground) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Cherish::HeartbeatFeltWakeLock")
            try {
                wakeLock?.acquire(3000L)
                playReciprocalFeltHaptic(context)
            } finally {
                if (wakeLock?.isHeld == true) {
                    try { wakeLock.release() } catch (_: Exception) {}
                }
            }
        } else {
            playReciprocalFeltHaptic(context)
        }

        val event = HeartbeatFeltEvent(signalId, feltAt)
        _partnerFelt.tryEmit(event)
        Log.d("ThinkingOfYou", "Partner felt our heartbeat trigger for signal $signalId")

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
