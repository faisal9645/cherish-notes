package com.example.audio

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class VoicePlayerHelper(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _currentTrackId = MutableStateFlow<String?>(null)
    val currentTrackId: StateFlow<String?> = _currentTrackId.asStateFlow()

    // Backward-compat alias for any existing collectors
    val currentlyPlayingId: StateFlow<String?> = _currentTrackId.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _currentPositionSec = MutableStateFlow(0)
    val currentPositionSec: StateFlow<Int> = _currentPositionSec.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    /** Where the current track plays from (a URL or a local path), to restart it on another output. */
    private var currentSource: String? = null
    private var currentOnCompletion: () -> Unit = {}

    // ---- Raise to ear: near the face a voice note plays through the earpiece, screen off ----

    private val sensorManager by lazy { context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager }
    private val proximitySensor by lazy { sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY) }
    private val audioManager by lazy { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    /** Turns the screen off while the phone is held to the ear, so the cheek can't tap anything. */
    private val screenOffNearEar: PowerManager.WakeLock? by lazy {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        if (power != null && power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "Cherish:VoiceNoteAtEar")
        } else {
            null
        }
    }

    private var isWatchingProximity = false
    private var isAtEar = false

    private val proximityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val sensor = proximitySensor ?: return
            val distance = event.values.firstOrNull() ?: return
            // Many sensors only report "near" (0) or their maximum range
            setAtEar(distance < minOf(sensor.maximumRange, 5f))
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    fun togglePlaybackSpeed(): Float {
        val nextSpeed = when (_playbackSpeed.value) {
            1.0f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        setPlaybackSpeed(nextSpeed)
        return nextSpeed
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        mp.playbackParams = mp.playbackParams.setSpeed(speed)
                    }
                }
            } catch (e: Exception) {
                Log.w("VoicePlayerHelper", "Could not set playback speed", e)
            }
        }
    }

    fun playAudio(messageId: String, audioUriOrUrl: String, onCompletion: () -> Unit = {}) {
        // Case 1: Already active and currently playing -> PAUSE
        if (_currentTrackId.value == messageId && _isPlaying.value && mediaPlayer != null) {
            pause()
            return
        }

        // Case 2: Already active and paused -> RESUME
        if (_currentTrackId.value == messageId && !_isPlaying.value && mediaPlayer != null) {
            try {
                mediaPlayer?.start()
                _isPlaying.value = true
                mediaPlayer?.let { startProgressTracker(it) }
                watchProximity(true)
                return
            } catch (e: Exception) {
                Log.e("VoicePlayerHelper", "Failed to resume audio, restarting", e)
            }
        }

        // Case 3: Start new audio or switch tracks (keeping the earpiece if it's at the ear)
        stopPlayback(keepEarpiece = true)
        _currentTrackId.value = messageId
        _isPlaying.value = true // Set immediately so icon changes to Pause/Loading instantly
        currentOnCompletion = onCompletion

        try {
            val source = resolveSource(messageId, audioUriOrUrl)
            currentSource = source
            startPlayer(messageId, source, startAtMs = 0)
        } catch (e: Exception) {
            Log.e("VoicePlayerHelper", "Failed to play audio", e)
            stop()
        }
    }

    /** A source MediaPlayer can open again later: inline (base64) audio is written to the cache. */
    private fun resolveSource(messageId: String, audioUriOrUrl: String): String {
        if (!audioUriOrUrl.startsWith("data:") && !audioUriOrUrl.contains(";base64,")) return audioUriOrUrl
        val cacheDir = File(context.cacheDir, "voice_cache").apply { mkdirs() }
        val cachedFile = File(cacheDir, "voice_" + Math.abs(messageId.hashCode()).toString() + ".m4a")
        if (!cachedFile.exists() || cachedFile.length() == 0L) {
            val base64Part = if (audioUriOrUrl.contains(",")) audioUriOrUrl.substringAfter(",") else audioUriOrUrl
            cachedFile.writeBytes(Base64.decode(base64Part, Base64.DEFAULT))
        }
        return cachedFile.absolutePath
    }

    private fun setDataSource(player: MediaPlayer, source: String) {
        when {
            source.startsWith("http://") || source.startsWith("https://") -> player.setDataSource(source)
            source.startsWith("file://") -> {
                val path = Uri.parse(source).path
                if (path != null && File(path).exists()) {
                    player.setDataSource(path)
                } else {
                    player.setDataSource(context, Uri.parse(source))
                }
            }
            File(source).exists() -> player.setDataSource(source)
            else -> player.setDataSource(context, Uri.parse(source))
        }
    }

    /** Plays [source] from [startAtMs], through the earpiece when the phone is at the ear. */
    private fun startPlayer(messageId: String, source: String, startAtMs: Int) {
        val player = MediaPlayer()
        mediaPlayer = player
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(if (isAtEar) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        setDataSource(player, source)
        player.setOnPreparedListener { mp ->
            if (_currentTrackId.value != messageId || mediaPlayer !== mp) {
                mp.release()
                return@setOnPreparedListener
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    mp.playbackParams = mp.playbackParams.setSpeed(_playbackSpeed.value)
                } catch (_: Exception) {}
            }
            if (startAtMs > 0) mp.seekTo(startAtMs)
            mp.start()
            _isPlaying.value = true
            startProgressTracker(mp)
            watchProximity(true)
        }
        player.setOnCompletionListener { mp ->
            if (mediaPlayer !== mp) return@setOnCompletionListener
            _isPlaying.value = false
            _playbackProgress.value = 0f
            _currentPositionSec.value = 0
            _currentTrackId.value = null
            progressJob?.cancel()
            currentOnCompletion()
            // Nothing played on after it: back to the speaker
            if (_currentTrackId.value == null) watchProximity(false)
        }
        player.setOnErrorListener { _, what, extra ->
            Log.e("VoicePlayerHelper", "MediaPlayer error: $what, $extra")
            stop()
            true
        }
        player.prepareAsync()
    }

    private fun startProgressTracker(player: MediaPlayer) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && _isPlaying.value) {
                try {
                    if (player.isPlaying) {
                        val duration = player.duration
                        if (duration > 0) {
                            val pos = player.currentPosition
                            _playbackProgress.value = (pos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                            _currentPositionSec.value = pos / 1000
                        }
                    }
                } catch (_: Exception) {}
                delay(60)
            }
        }
    }

    fun seekTo(progress: Float) {
        try {
            mediaPlayer?.let { mp ->
                val duration = mp.duration
                if (duration > 0) {
                    val targetMs = (duration * progress.coerceIn(0f, 1f)).toInt()
                    mp.seekTo(targetMs)
                    _playbackProgress.value = progress.coerceIn(0f, 1f)
                    _currentPositionSec.value = targetMs / 1000
                }
            }
        } catch (e: Exception) {
            Log.e("VoicePlayerHelper", "Failed to seek audio", e)
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
        } catch (_: Exception) {}
        progressJob?.cancel()
        _isPlaying.value = false
        watchProximity(false)
    }

    fun stop() {
        stopPlayback(keepEarpiece = false)
    }

    private fun stopPlayback(keepEarpiece: Boolean) {
        progressJob?.cancel()
        progressJob = null
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
        } catch (e: Exception) {
            // ignore
        }
        mediaPlayer = null
        _isPlaying.value = false
        _currentTrackId.value = null
        _playbackProgress.value = 0f
        _currentPositionSec.value = 0
        if (!keepEarpiece) watchProximity(false)
    }

    /** Listens for the phone at the ear while a voice note plays; on headphones it isn't needed. */
    private fun watchProximity(watch: Boolean) {
        if (watch) {
            if (isWatchingProximity || hasHeadphones()) return
            val sensor = proximitySensor ?: return
            isWatchingProximity = sensorManager?.registerListener(proximityListener, sensor, SensorManager.SENSOR_DELAY_NORMAL) == true
            if (isWatchingProximity) {
                try {
                    // The system turns the screen off whenever the sensor is covered
                    screenOffNearEar?.takeIf { !it.isHeld }?.acquire(30 * 60 * 1000L)
                } catch (e: Exception) {
                    Log.w("VoicePlayerHelper", "Screen-off at ear unavailable", e)
                }
            }
        } else {
            if (isWatchingProximity) {
                sensorManager?.unregisterListener(proximityListener)
                isWatchingProximity = false
            }
            try {
                screenOffNearEar?.takeIf { it.isHeld }?.release()
            } catch (_: Exception) {}
            if (isAtEar) {
                isAtEar = false
                routeToEarpiece(false)
            }
        }
    }

    /** Moves playback between the speaker and the earpiece, picking up a moment back. */
    private fun setAtEar(atEar: Boolean) {
        if (isAtEar == atEar) return
        isAtEar = atEar
        routeToEarpiece(atEar)
        val mp = mediaPlayer ?: return
        val trackId = _currentTrackId.value ?: return
        val source = currentSource ?: return
        if (!_isPlaying.value) return
        val position = try {
            (mp.currentPosition - 600).coerceAtLeast(0)
        } catch (_: Exception) {
            0
        }
        progressJob?.cancel()
        try {
            mp.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        try {
            startPlayer(trackId, source, position)
        } catch (e: Exception) {
            Log.e("VoicePlayerHelper", "Could not switch audio output", e)
            stop()
        }
    }

    private fun routeToEarpiece(earpiece: Boolean) {
        val am = audioManager ?: return
        try {
            if (earpiece) {
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    am.availableCommunicationDevices
                        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                        ?.let { am.setCommunicationDevice(it) }
                } else {
                    @Suppress("DEPRECATION")
                    am.isSpeakerphoneOn = false
                }
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.clearCommunicationDevice()
                am.mode = AudioManager.MODE_NORMAL
            }
        } catch (e: Exception) {
            Log.w("VoicePlayerHelper", "Could not route audio", e)
        }
    }

    private fun hasHeadphones(): Boolean {
        val am = audioManager ?: return false
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { device ->
            when (device.type) {
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_USB_HEADSET -> true
                else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && device.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
        }
    }
}
