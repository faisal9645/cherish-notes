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
            val outputFile = File(audioDir, "voice_${System.currentTimeMillis()}.m4a")
            currentOutputFile = outputFile

            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

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
                    val normalized = (maxAmp / 32767f).coerceIn(0.15f, 1f)
                    ampList.add(normalized)
                    if (ampList.size > 40) ampList.removeAt(0)
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
}
