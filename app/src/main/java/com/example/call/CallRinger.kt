package com.example.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * A ringing call's ringtone and vibration while its call screen is open in Cherish, where no
 * notification rings (out of the app, the ringing notification does: see CallNotifications). The
 * phone's own ringtone over and over when Call Notification Ringing is on, and the call vibration
 * either way, as the phone's sound mode allows: Silent plays nothing, Vibrate only vibrates.
 */
internal object CallRinger {
    private const val TAG = "CallRinger"

    /** The rhythm of the ringing notification's channels. */
    private val VIBRATION = longArrayOf(0, 900, 700, 900, 700)

    private val ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var vibrating = false

    /** Plays the ringtone again when it ends, where it can't loop by itself (before Android 9). */
    private val replay = object : Runnable {
        override fun run() {
            val current = ringtone ?: return
            try {
                if (!current.isPlaying) current.play()
            } catch (_: Exception) {}
            handler.postDelayed(this, 1_000L)
        }
    }

    @Synchronized
    fun start(context: Context, withRingtone: Boolean) {
        stop(context)
        val mode = (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.ringerMode
            ?: AudioManager.RINGER_MODE_NORMAL
        if (mode == AudioManager.RINGER_MODE_SILENT) return
        if (withRingtone && mode == AudioManager.RINGER_MODE_NORMAL) {
            try {
                ringtone = RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.apply {
                    audioAttributes = ATTRIBUTES
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                    play()
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) handler.postDelayed(replay, 1_000L)
            } catch (e: Exception) {
                Log.w(TAG, "Ringtone unavailable", e)
            }
        }
        val vibrator = vibrator(context) ?: return
        try {
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                vibrator.vibrate(VibrationEffect.createWaveform(VIBRATION, 0), ATTRIBUTES)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(VIBRATION, 0, ATTRIBUTES)
            }
            vibrating = true
        } catch (e: Exception) {
            Log.w(TAG, "Vibration unavailable", e)
        }
    }

    @Synchronized
    fun stop(context: Context) {
        handler.removeCallbacks(replay)
        ringtone?.let {
            try {
                it.stop()
            } catch (_: Exception) {}
        }
        ringtone = null
        if (vibrating) {
            vibrating = false
            try {
                vibrator(context)?.cancel()
            } catch (_: Exception) {}
        }
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
