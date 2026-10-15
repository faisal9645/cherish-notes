package com.example.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class VoiceRecorderHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var recordingJob: Job? = null
    private var startTimeMillis: Long = 0L

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationSec = MutableStateFlow(0)
    val recordingDurationSec: StateFlow<Int> = _recordingDurationSec.asStateFlow()

    private val _amplitudes = MutableStateFlow<List<Float>>(emptyList())
    val amplitudes: StateFlow<List<Float>> = _amplitudes.asStateFlow()

    fun startRecording(): File? {
        try {
            cancelRecording() // Clean up any previous session

            val audioDir = File(context.cacheDir, "voice_notes").apply { mkdirs() }
            val outputFile = startRecorder(audioDir) ?: run {
                cancelRecording()
                return null
            }
            currentOutputFile = outputFile

            startTimeMillis = System.currentTimeMillis()
            _isRecording.value = true
            _recordingDurationSec.value = 0
            _amplitudes.value = emptyList()

            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val ampList = mutableListOf<Float>()
                while (_isRecording.value && isActive) {
                    val duration = ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt()
                    _recordingDurationSec.value = duration

                    val maxAmp = try {
                        recorder?.maxAmplitude ?: 0
                    } catch (e: Exception) {
                        0
                    }
                    val rawNormalized = (maxAmp / 32767f)
                    val normalized = if (rawNormalized > 0.02f) {
                        (rawNormalized * 2.4f).coerceIn(0.18f, 1.0f)
                    } else {
                        // Natural subtle speech rhythm flutter for visual responsiveness
                        val flutter = (kotlin.math.sin(duration * 4.0 + ampList.size * 0.45).toFloat() * 0.16f + 0.32f).coerceIn(0.18f, 0.65f)
                        flutter
                    }
                    ampList.add(normalized)
                    _amplitudes.value = ampList.toList()

                    delay(100)
                }
            }

            return outputFile
        } catch (e: Exception) {
            Log.e("VoiceRecorderHelper", "Failed to start recording", e)
            cancelRecording()
            return null
        }
    }

    /**
     * Starts recording into a new file in [dir]: Opus (the codec made for speech) in Ogg at 12 kbps,
     * about 90 KB a minute, where the phone can record it (Android 10+); AAC at 32 kbps otherwise,
     * or if Opus won't start. Mono at a voice's own sample rate either way. The file, or null.
     */
    private fun startRecorder(dir: File): File? {
        val tryOpus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        for (opus in listOf(tryOpus, false).distinct()) {
            val file = File(dir, "voice_${System.currentTimeMillis()}.${if (opus) "ogg" else "m4a"}")
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            try {
                mediaRecorder.apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    if (opus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        setOutputFormat(MediaRecorder.OutputFormat.OGG)
                        setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                        setAudioEncodingBitRate(OPUS_BIT_RATE)
                        setAudioSamplingRate(OPUS_SAMPLE_RATE)
                    } else {
                        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                        setAudioEncodingBitRate(AAC_BIT_RATE)
                        setAudioSamplingRate(AAC_SAMPLE_RATE)
                    }
                    setAudioChannels(1)
                    setOutputFile(file.absolutePath)
                    prepare()
                    start()
                }
                recorder = mediaRecorder
                return file
            } catch (e: Exception) {
                Log.w("VoiceRecorderHelper", "Couldn't start ${if (opus) "Opus" else "AAC"} recording", e)
                try {
                    mediaRecorder.release()
                } catch (_: Exception) {}
                file.delete()
            }
        }
        return null
    }

    fun stopRecording(): Pair<File?, Int> {
        val elapsed = if (startTimeMillis > 0L) System.currentTimeMillis() - startTimeMillis else 0L
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            recorder?.stop()
        } catch (e: Exception) {
            Log.w("VoiceRecorderHelper", "Error stopping recorder", e)
        }
        try {
            recorder?.release()
        } catch (e: Exception) {
            Log.w("VoiceRecorderHelper", "Error releasing recorder", e)
        }
        recorder = null

        val file = currentOutputFile
        currentOutputFile = null

        if (elapsed < 400L || file == null || !file.exists() || file.length() < 100) {
            // Accidental quick tap or corrupt file
            file?.delete()
            _recordingDurationSec.value = 0
            _amplitudes.value = emptyList()
            return Pair(null, 0)
        }

        val durationSec = ((elapsed + 500) / 1000).toInt().coerceAtLeast(1)
        _recordingDurationSec.value = 0
        return Pair(file, durationSec)
    }

    fun cancelRecording() {
        _isRecording.value = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            recorder?.stop()
        } catch (_: Exception) {}
        try {
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null

        currentOutputFile?.let {
            if (it.exists()) it.delete()
        }
        currentOutputFile = null
        _recordingDurationSec.value = 0
        _amplitudes.value = emptyList()
    }

    /**
     * Safely stop recording when the app goes to background.
     * Returns the recorded file and duration if a valid recording was in progress,
     * or null/0 if nothing useful was recorded.
     * This prevents the recording state from remaining stuck on resume.
     */
    fun safeStopForBackground(): Pair<File?, Int> {
        if (!_isRecording.value) return Pair(null, 0)
        return stopRecording()
    }

    private companion object {
        /** Opus at 12 kbps is clear wideband speech: about 90 KB a minute (AAC at 64 kbps was 480 KB). */
        const val OPUS_BIT_RATE = 12_000
        const val OPUS_SAMPLE_RATE = 16_000
        const val AAC_BIT_RATE = 32_000
        const val AAC_SAMPLE_RATE = 22_050
    }
}
