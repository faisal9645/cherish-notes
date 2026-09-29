package com.example.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VoicePlayerHelper(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _currentlyPlayingId = MutableStateFlow<String?>(null)
    val currentlyPlayingId: StateFlow<String?> = _currentlyPlayingId.asStateFlow()

    private val _playbackProgress = MutableStateFlow(0f)
    val playbackProgress: StateFlow<Float> = _playbackProgress.asStateFlow()

    private val _currentPositionSec = MutableStateFlow(0)
    val currentPositionSec: StateFlow<Int> = _currentPositionSec.asStateFlow()

    fun playAudio(messageId: String, audioUriOrUrl: String, onCompletion: () -> Unit = {}) {
        if (_currentlyPlayingId.value == messageId && mediaPlayer?.isPlaying == true) {
            pause()
            return
        }

        stop()

        try {
            mediaPlayer = MediaPlayer().apply {
                if (audioUriOrUrl.startsWith("http://") || audioUriOrUrl.startsWith("https://")) {
                    setDataSource(audioUriOrUrl)
                } else {
                    setDataSource(context, Uri.parse(audioUriOrUrl))
                }
                prepareAsync()
                setOnPreparedListener { mp ->
                    mp.start()
                    _currentlyPlayingId.value = messageId
                    startProgressTracker(mp, onCompletion)
                }
                setOnCompletionListener {
                    stop()
                    onCompletion()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("VoicePlayerHelper", "MediaPlayer error: $what, $extra")
                    stop()
                    false
                }
            }
        } catch (e: Exception) {
            Log.e("VoicePlayerHelper", "Failed to play audio", e)
            stop()
        }
    }

    private fun startProgressTracker(player: MediaPlayer, onCompletion: () -> Unit) {
        progressJob?.cancel()
        progressJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive && player.isPlaying) {
                val duration = player.duration
                if (duration > 0) {
                    val pos = player.currentPosition
                    _playbackProgress.value = (pos.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    _currentPositionSec.value = pos / 1000
                }
                delay(80)
            }
        }
    }

    fun pause() {
        mediaPlayer?.pause()
        progressJob?.cancel()
        _currentlyPlayingId.value = null
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
        _currentlyPlayingId.value = null
        _playbackProgress.value = 0f
        _currentPositionSec.value = 0
    }
}
