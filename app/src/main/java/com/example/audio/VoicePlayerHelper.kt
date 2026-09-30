package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
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
                mediaPlayer?.let { startProgressTracker(it, onCompletion) }
                return
            } catch (e: Exception) {
                Log.e("VoicePlayerHelper", "Failed to resume audio, restarting", e)
            }
        }

        // Case 3: Start new audio or switch tracks
        stop()
        _currentTrackId.value = messageId
        _isPlaying.value = true // Set immediately so icon changes to Pause/Loading instantly

        try {
            val player = MediaPlayer()
            mediaPlayer = player

            // Support Base64 data URIs (e.g. data:audio/m4a;base64,...)
            if (audioUriOrUrl.startsWith("data:") || audioUriOrUrl.contains(";base64,")) {
                val cacheDir = File(context.cacheDir, "voice_cache").apply { mkdirs() }
                val safeFileName = "voice_" + Math.abs(messageId.hashCode()).toString() + ".m4a"
                val cachedFile = File(cacheDir, safeFileName)
                if (!cachedFile.exists() || cachedFile.length() == 0L) {
                    val base64Part = if (audioUriOrUrl.contains(",")) {
                        audioUriOrUrl.substringAfter(",")
                    } else {
                        audioUriOrUrl
                    }
                    val audioBytes = Base64.decode(base64Part, Base64.DEFAULT)
                    cachedFile.writeBytes(audioBytes)
                }
                player.setDataSource(cachedFile.absolutePath)
            } else if (audioUriOrUrl.startsWith("http://") || audioUriOrUrl.startsWith("https://")) {
                player.setDataSource(audioUriOrUrl)
            } else if (audioUriOrUrl.startsWith("file://")) {
                val path = Uri.parse(audioUriOrUrl).path
                if (path != null && File(path).exists()) {
                    player.setDataSource(path)
                } else {
                    player.setDataSource(context, Uri.parse(audioUriOrUrl))
                }
            } else if (File(audioUriOrUrl).exists()) {
                player.setDataSource(audioUriOrUrl)
            } else {
                player.setDataSource(context, Uri.parse(audioUriOrUrl))
            }

            player.prepareAsync()
            player.setOnPreparedListener { mp ->
                if (_currentTrackId.value != messageId) {
                    mp.release()
                    return@setOnPreparedListener
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    try {
                        mp.playbackParams = mp.playbackParams.setSpeed(_playbackSpeed.value)
                    } catch (_: Exception) {}
                }
                mp.start()
                _isPlaying.value = true
                startProgressTracker(mp, onCompletion)
            }
            player.setOnCompletionListener {
                _isPlaying.value = false
                _playbackProgress.value = 0f
                _currentPositionSec.value = 0
                _currentTrackId.value = null
                progressJob?.cancel()
                onCompletion()
            }
            player.setOnErrorListener { _, what, extra ->
                Log.e("VoicePlayerHelper", "MediaPlayer error: $what, $extra")
                stop()
                true
            }
        } catch (e: Exception) {
            Log.e("VoicePlayerHelper", "Failed to play audio", e)
            stop()
        }
    }

    private fun startProgressTracker(player: MediaPlayer, onCompletion: () -> Unit) {
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

    fun pause() {
        try {
            mediaPlayer?.pause()
        } catch (_: Exception) {}
        progressJob?.cancel()
        _isPlaying.value = false
    }

    fun stop() {
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
    }
}
