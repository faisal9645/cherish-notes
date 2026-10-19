package com.example.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
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
    /** Ringing now (started and not stopped), so a second start doesn't restart the ringtone. */
    @Volatile private var active = false
    // While the ringtone should be heard: where from, which of the phone's sounds is playing, and
    // how many checks in a row found it quiet
    private var ringContext: Context? = null
    private var uriIndex = 0
    private var quietChecks = 0
    private var focus: AudioFocusRequest? = null

    /**
     * Keeps the ringtone heard: plays it again when it ends (where it can't loop by itself, or when
     * looped playback stalls), and moves to the phone's next sound when one won't play at all.
     */
    private val replay = object : Runnable {
        override fun run() {
            val context = ringContext ?: return
            val current = ringtone
            if (current != null && current.isPlaying) {
                quietChecks = 0
            } else {
                quietChecks++
                if (current == null || quietChecks >= 3) {
                    try {
                        current?.stop()
                    } catch (_: Exception) {}
                    ringtone = playRingtone(context, uriIndex + 1)
                    quietChecks = 0
                } else {
                    try {
                        current.play()
                    } catch (_: Exception) {}
                }
            }
            handler.postDelayed(this, 1_000L)
        }
    }

    /** Ringing already (from the call screen or the ringing service). */
    fun isRinging(): Boolean = active

    @Synchronized
    fun start(caller: Context, withRingtone: Boolean) {
        // The app's own context whoever starts it (a call screen or service comes and goes)
        val context = caller.applicationContext ?: caller
        stop(context)
        val mode = (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.ringerMode
            ?: AudioManager.RINGER_MODE_NORMAL
        if (mode == AudioManager.RINGER_MODE_SILENT) return
        active = true

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
            // Heard over music or a video: they pause while it rings
            takeFocus(context)
            ringContext = context
            uriIndex = 0
            quietChecks = 0
            ringtone = playRingtone(context, 0)
            // Watchdog replay ensuring continuous ringing even across OEMs that stop after 1 track cycle
            handler.removeCallbacks(replay)
            handler.postDelayed(replay, 1_000L)
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
        active = false
        handler.removeCallbacks(replay)
        ringContext = null
        ringtone?.let {
            try {
                it.stop()
            } catch (_: Exception) {}
        }
        ringtone = null
        releaseFocus(context)
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

    /**
     * The phone's ringtone, from the first of its sounds that plays (from [from] on, round again):
     * the system's own copy of the ringtone first, readable without any permission; then the file
     * it was chosen from (which can need one), then its notification and alarm sounds.
     */
    private fun playRingtone(context: Context, from: Int): Ringtone? {
        val uris = listOfNotNull(
            android.provider.Settings.System.DEFAULT_RINGTONE_URI,
            RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ).distinct()
        if (uris.isEmpty()) return null
        for (step in uris.indices) {
            val index = (from + step) % uris.size
            try {
                val tone = RingtoneManager.getRingtone(context, uris[index]) ?: continue
                tone.audioAttributes = ATTRIBUTES
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) tone.isLooping = true
                tone.play()
                uriIndex = index
                return tone
            } catch (e: Exception) {
                Log.w(TAG, "Ringtone ${uris[index]} unavailable", e)
            }
        }
        return null
    }

    private fun takeFocus(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(ATTRIBUTES)
                .build()
            audio.requestAudioFocus(request)
            focus = request
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus unavailable", e)
        }
    }

    private fun releaseFocus(context: Context) {
        val request = focus ?: return
        focus = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        try {
            (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.abandonAudioFocusRequest(request)
        } catch (_: Exception) {}
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
}
