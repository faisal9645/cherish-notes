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
 * A ringing call's ringtone and vibration, played by the app: on its call screen in Cherish, and
 * out of the app from the ringing service (CallService), whose notification makes no sound of its
 * own. The phone's own ringtone over and over when Call Notification Ringing is on, and the call
 * vibration either way, as the phone's sound mode allows: Silent plays nothing, Vibrate only vibrates.
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
    /**
     * The vibrator the call vibration was started on. Android only cancels a vibration through the
     * same one (each screen or service gets its own), so stopping must use this one: another would
     * leave the phone vibrating on after the call was declined.
     */
    private var vibrating: Vibrator? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    /** Plays the ringtone again when it ends, where it can't loop by itself or when looped playback stalls. */
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
    fun start(caller: Context, withRingtone: Boolean) {
        // The app's own context whoever starts it (a call screen or service comes and goes)
        val context = caller.applicationContext ?: caller
        stop(context)
        val mode = (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.ringerMode
            ?: AudioManager.RINGER_MODE_NORMAL
        if (mode == AudioManager.RINGER_MODE_SILENT) return

        // Hold CPU and screen wakelock so device wakes up and stays awake to ring and vibrate while screen is off/locked
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            wakeLock = pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "cherish:call_ringer_wake")?.apply {
                setReferenceCounted(false)
                acquire(100_000L)
            }
            @Suppress("DEPRECATION")
            val screenLock = pm?.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                android.os.PowerManager.ON_AFTER_RELEASE,
                "cherish:call_ringer_screen_wake"
            )
            screenLock?.acquire(15_000L)
        } catch (_: Exception) {}

        if (withRingtone && mode == AudioManager.RINGER_MODE_NORMAL) {
            try {
                // The phone's ringtone as the system keeps a copy of it (readable without any
                // permission), else the file it was chosen from
                val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: android.provider.Settings.System.DEFAULT_RINGTONE_URI
                ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
                    audioAttributes = ATTRIBUTES
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                    play()
                }
                // Watchdog replay ensuring continuous ringing even across OEMs that stop after 1 track cycle
                handler.removeCallbacks(replay)
                handler.postDelayed(replay, 1_000L)
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
            vibrating = vibrator
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
        vibrating?.let {
            try {
                it.cancel()
            } catch (_: Exception) {}
        }
        vibrating = null
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
