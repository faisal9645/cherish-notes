package com.example.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log

/**
 * The phone's side of a call's sound: audio focus (music and videos pause during the call),
 * which headsets are connected, phone (cellular) calls taking over, and turning the screen off
 * at the ear. Agora itself sets the call audio mode and routes the sound; volume is left to the
 * phone's own call volume and is never changed here.
 */
class CallAudioController(private val context: Context) {

    interface Listener {
        /** A phone call (or the system) has the line; false once it's back. */
        fun onInterrupted(interrupted: Boolean)
        fun onHeadsetsChanged(bluetooth: Boolean, wired: Boolean)
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var listener: Listener? = null
    private var focusRequest: AudioFocusRequest? = null
    private var started = false

    private val proximityLock: PowerManager.WakeLock? by lazy {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (power != null && power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "Cherish:CallAtEar")
        } else {
            null
        }
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> listener?.onInterrupted(false)
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                listener?.onInterrupted(true)
            }
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = reportHeadsets()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = reportHeadsets()
    }

    fun start(listener: Listener) {
        this.listener = listener
        if (started) return
        started = true
        requestFocus()
        try {
            audioManager?.registerAudioDeviceCallback(deviceCallback, mainHandler)
        } catch (e: Exception) {
            Log.w(TAG, "Headset updates unavailable", e)
        }
        reportHeadsets()
    }

    fun stop() {
        if (!started) return
        started = false
        abandonFocus()
        try {
            audioManager?.unregisterAudioDeviceCallback(deviceCallback)
        } catch (_: Exception) {}
        setScreenOffAtEar(false)
        listener = null
    }

    /** At the ear on the earpiece: the screen turns off when the sensor is covered. */
    fun setScreenOffAtEar(enabled: Boolean) {
        val lock = proximityLock ?: return
        try {
            if (enabled && !lock.isHeld) {
                lock.acquire(MAX_CALL_MS)
            } else if (!enabled && lock.isHeld) {
                lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Screen-off at ear unavailable", e)
        }
    }

    /** Elevates the mobile device's call sound stream to maximum volume for clear, loud audio. */
    fun maximizeCallVolume(isSpeaker: Boolean = false) {
        try {
            val am = audioManager ?: return
            val stream = if (isSpeaker) AudioManager.STREAM_MUSIC else AudioManager.STREAM_VOICE_CALL
            val maxVol = am.getStreamMaxVolume(stream)
            val currentVol = am.getStreamVolume(stream)
            if (currentVol < maxVol) {
                am.setStreamVolume(stream, maxVol, 0)
            }
            val voiceMax = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, voiceMax, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't maximize call volume", e)
        }
    }

    fun hasBluetoothHeadset(): Boolean = outputs().any { it.type in BLUETOOTH_TYPES || isBleHeadset(it) }

    fun hasWiredHeadset(): Boolean = outputs().any { it.type in WIRED_TYPES }

    /** A cellular call is on (ringing doesn't count: the user may not answer it). */
    fun isPhoneCallActive(): Boolean {
        val mode = audioManager?.mode ?: return false
        if (mode == AudioManager.MODE_IN_CALL || mode == AudioManager.MODE_RINGTONE) return true
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mode == AudioManager.MODE_CALL_SCREENING
    }

    private fun reportHeadsets() {
        listener?.onHeadsetsChanged(hasBluetoothHeadset(), hasWiredHeadset())
    }

    private fun outputs(): List<AudioDeviceInfo> = try {
        audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.toList().orEmpty()
    } catch (_: Exception) {
        emptyList()
    }

    private fun isBleHeadset(device: AudioDeviceInfo): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_HEADSET

    private fun requestFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(focusListener, mainHandler)
                    .build()
                focusRequest = request
                am.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus request failed", e)
        }
    }

    private fun abandonFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
                focusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(focusListener)
            }
        } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "CallAudioController"
        private const val MAX_CALL_MS = 6L * 60 * 60 * 1000
        private val BLUETOOTH_TYPES = buildSet {
            add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(AudioDeviceInfo.TYPE_HEARING_AID)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(AudioDeviceInfo.TYPE_BLE_HEADSET)
                add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(AudioDeviceInfo.TYPE_BLE_BROADCAST)
            }
        }
        private val WIRED_TYPES = buildSet {
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(AudioDeviceInfo.TYPE_USB_HEADSET)
                add(AudioDeviceInfo.TYPE_USB_DEVICE)
            }
        }
    }
}
