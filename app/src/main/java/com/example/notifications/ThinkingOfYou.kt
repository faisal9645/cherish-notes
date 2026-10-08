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
    private const val FRESH_WINDOW_MS = 45_000L
    private const val PREFS = "cherish_love"
    private const val KEY_LAST_SIGNAL = "last_heartbeat_signal"

    private val _received = MutableSharedFlow<Long>(extraBufferCapacity = 4)
    /** A heartbeat just played on this phone, for the chat's heart pulse. */
    val received: SharedFlow<Long> = _received.asSharedFlow()

    private val seen = LinkedHashSet<String>()

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

        if (sentAt <= 0L || kotlin.math.abs(System.currentTimeMillis() - sentAt) > FRESH_WINDOW_MS) return false
        if (!SecurityPreferences.getInstance(context).isNotificationsEnabled()) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        if (!isPhoneInUse(context)) return false

        playHeartbeat(context)
        _received.tryEmit(System.currentTimeMillis())
        return true
    }

    /** Screen on and unlocked: someone is actually using the phone. */
    private fun isPhoneInUse(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        if (!power.isInteractive) return false
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguard?.isKeyguardLocked != true
    }

    /** Two soft heartbeats. Played as a notification vibration, so it also works from the background. */
    private fun playHeartbeat(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            if (!vibrator.hasVibrator()) return

            // thump-thump ... thump-thump
            val timings = longArrayOf(0, 55, 120, 85, 560, 55, 120, 85)
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitudes = intArrayOf(0, 140, 0, 255, 0, 140, 0, 255)
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
}
