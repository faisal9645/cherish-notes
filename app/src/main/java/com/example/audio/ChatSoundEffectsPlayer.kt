package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import com.example.security.SecurityPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Premium, zero-latency acoustic sound synthesizer for intimate couple messaging.
 * Delivers gentle, warm, romantic auditory chimes and tactile feedback for all chat actions.
 */
class ChatSoundEffectsPlayer private constructor(
    private val appContext: Context,
    private val securityPreferences: SecurityPreferences
) {
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    enum class SoundType {
        SENT,
        RECEIVED,
        REACTION,
        STAR,
        RECORD_START,
        RECORD_STOP,
        DISGUISE_EXIT,
        DELETE
    }

    private val soundBuffers = HashMap<SoundType, ShortArray>()

    init {
        // Pre-synthesize lush acoustic waveform buffers in background
        scope.launch {
            synthesizeAllSounds()
        }
    }

    private fun synthesizeAllSounds() {
        val sampleRate = 44100

        // 1. SENT: Warm romantic rising pop-chime (D5 to A5)
        soundBuffers[SoundType.SENT] = generateTone(
            sampleRate = sampleRate,
            durationMs = 140,
            startFreq = 587.33,
            endFreq = 880.0,
            attackMs = 8,
            decayRate = 24.0,
            harmonicWeight = 0.28
        )

        // 2. RECEIVED: Sweet double bell chime (E5 -> B5 / E6)
        soundBuffers[SoundType.RECEIVED] = generateDoubleChime(
            sampleRate = sampleRate,
            freq1 = 659.25,
            freq2 = 987.77,
            freq3 = 1318.51
        )

        // 3. REACTION: Playful sparkle bubble sweep
        soundBuffers[SoundType.REACTION] = generateTone(
            sampleRate = sampleRate,
            durationMs = 110,
            startFreq = 540.0,
            endFreq = 1280.0,
            attackMs = 6,
            decayRate = 28.0,
            harmonicWeight = 0.35
        )

        // 4. STAR: Crystalline star twinkle ping
        soundBuffers[SoundType.STAR] = generateTone(
            sampleRate = sampleRate,
            durationMs = 220,
            startFreq = 1396.91,
            endFreq = 1760.0,
            attackMs = 4,
            decayRate = 18.0,
            harmonicWeight = 0.42
        )

        // 5. RECORD_START: Soft mid warm chime
        soundBuffers[SoundType.RECORD_START] = generateTone(
            sampleRate = sampleRate,
            durationMs = 85,
            startFreq = 440.0,
            endFreq = 587.33,
            attackMs = 5,
            decayRate = 30.0,
            harmonicWeight = 0.2
        )

        // 6. RECORD_STOP: Soft release tone
        soundBuffers[SoundType.RECORD_STOP] = generateTone(
            sampleRate = sampleRate,
            durationMs = 85,
            startFreq = 587.33,
            endFreq = 440.0,
            attackMs = 5,
            decayRate = 30.0,
            harmonicWeight = 0.2
        )

        // 7. DISGUISE_EXIT: Discreet subtle snap
        soundBuffers[SoundType.DISGUISE_EXIT] = generateTone(
            sampleRate = sampleRate,
            durationMs = 60,
            startFreq = 340.0,
            endFreq = 220.0,
            attackMs = 3,
            decayRate = 45.0,
            harmonicWeight = 0.15
        )

        // 8. DELETE: Soft whoosh swish
        soundBuffers[SoundType.DELETE] = generateTone(
            sampleRate = sampleRate,
            durationMs = 100,
            startFreq = 480.0,
            endFreq = 240.0,
            attackMs = 8,
            decayRate = 32.0,
            harmonicWeight = 0.2
        )
    }

    fun playSound(type: SoundType) {
        if (!securityPreferences.isChatSoundsEnabled()) return

        // Respect user's device silent / vibrate mode
        val ringer = audioManager?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL
        if (ringer != AudioManager.RINGER_MODE_NORMAL) return

        scope.launch {
            try {
                var pcm = soundBuffers[type]
                if (pcm == null) {
                    synthesizeAllSounds()
                    pcm = soundBuffers[type] ?: return@launch
                }

                val attributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val format = AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(44100)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()

                val track = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioTrack.Builder()
                        .setAudioAttributes(attributes)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(pcm.size * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build()
                } else {
                    @Suppress("DEPRECATION")
                    AudioTrack(
                        attributes,
                        format,
                        pcm.size * 2,
                        AudioTrack.MODE_STATIC,
                        AudioManager.AUDIO_SESSION_ID_GENERATE
                    )
                }

                track.write(pcm, 0, pcm.size)
                track.play()

                val durationMs = (pcm.size * 1000L) / 44100L + 50L
                kotlinx.coroutines.delay(durationMs)
                try {
                    track.stop()
                    track.release()
                } catch (_: Exception) {}
            } catch (_: Exception) {
                // Ignore playback failures gracefully
            }
        }
    }

    private fun generateTone(
        sampleRate: Int,
        durationMs: Int,
        startFreq: Double,
        endFreq: Double,
        attackMs: Int,
        decayRate: Double,
        harmonicWeight: Double
    ): ShortArray {
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)
        val attackSamples = (sampleRate * (attackMs / 1000.0)).coerceAtLeast(1.0)
        var phase = 0.0

        for (i in 0 until numSamples) {
            val progress = i.toDouble() / numSamples
            val currentFreq = startFreq + (endFreq - startFreq) * progress
            val t = i.toDouble() / sampleRate

            val attackEnv = if (i < attackSamples) i / attackSamples else 1.0
            val decayEnv = exp(-t * decayRate)
            val envelope = attackEnv * decayEnv

            phase += 2.0 * PI * currentFreq / sampleRate
            val sampleVal = (sin(phase) * (1.0 - harmonicWeight) + sin(phase * 2.0) * harmonicWeight) * envelope

            buffer[i] = (sampleVal * 28000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return buffer
    }

    private fun generateDoubleChime(
        sampleRate: Int,
        freq1: Double,
        freq2: Double,
        freq3: Double
    ): ShortArray {
        val totalMs = 260
        val numSamples = (sampleRate * (totalMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)

        val note2StartSample = (sampleRate * 0.075).toInt()
        var phase1 = 0.0
        var phase2 = 0.0
        var phase3 = 0.0

        for (i in 0 until numSamples) {
            val t1 = i.toDouble() / sampleRate
            val env1 = (if (t1 < 0.005) t1 / 0.005 else 1.0) * exp(-t1 * 18.0)
            phase1 += 2.0 * PI * freq1 / sampleRate
            var sample = sin(phase1) * env1 * 0.55

            if (i >= note2StartSample) {
                val t2 = (i - note2StartSample).toDouble() / sampleRate
                val env2 = (if (t2 < 0.005) t2 / 0.005 else 1.0) * exp(-t2 * 14.0)
                phase2 += 2.0 * PI * freq2 / sampleRate
                phase3 += 2.0 * PI * freq3 / sampleRate
                sample += (sin(phase2) * 0.5 + sin(phase3) * 0.3) * env2
            }

            buffer[i] = (sample * 26000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return buffer
    }

    companion object {
        @Volatile
        private var INSTANCE: ChatSoundEffectsPlayer? = null

        fun getInstance(context: Context): ChatSoundEffectsPlayer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChatSoundEffectsPlayer(
                    context.applicationContext,
                    SecurityPreferences.getInstance(context)
                ).also { INSTANCE = it }
            }
        }
    }
}
