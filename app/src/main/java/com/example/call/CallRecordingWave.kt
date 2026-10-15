package com.example.call

import android.util.Log
import com.example.data.model.Message
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A call recording's waveform, the bars its player draws like a voice note's. Measured from the
 * recording itself (the call engine's WAV, before it's made small) and saved with its message;
 * a recording from before that gets a steady wave of its own, the same every time it's shown.
 */
object CallRecordingWave {
    private const val TAG = "CallRecordingWave"
    const val BARS = 48
    /** Stretches of sound measured per bar: enough for its loudness without reading a long call whole. */
    private const val WINDOWS_PER_BAR = 8
    private const val WINDOW_FRAMES = 2048

    /** [BARS] bars from 0 to 1 for a 16-bit PCM WAV, loudest bar 1; empty when it can't be read. */
    fun fromWav(file: File): List<Double> = try {
        RandomAccessFile(file, "r").use { raf -> measure(raf) }
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't measure ${file.name}", e)
        emptyList()
    }

    /** The waveform a recording's player draws: its own, or the steady one for its call. */
    fun of(message: Message): List<Float> {
        if (message.waveform.isNotEmpty()) return message.waveform.map { it.toFloat() }
        val random = Random((message.callId ?: message.id).hashCode())
        var level = 0.5f
        return List(BARS) {
            // A walk, not noise: neighbouring bars are alike, like speech
            level = (level + (random.nextFloat() - 0.5f) * 0.5f).coerceIn(0.2f, 0.95f)
            level
        }
    }

    private fun measure(raf: RandomAccessFile): List<Double> {
        val header = ByteArray(12)
        raf.readFully(header)
        if (String(header, 0, 4, Charsets.US_ASCII) != "RIFF" || String(header, 8, 4, Charsets.US_ASCII) != "WAVE") return emptyList()
        var channels = 1
        var bits = 16
        var dataStart = -1L
        var dataLength = 0L
        val chunk = ByteArray(8)
        while (raf.filePointer + 8 <= raf.length()) {
            raf.readFully(chunk)
            val id = String(chunk, 0, 4, Charsets.US_ASCII)
            val size = le32(chunk, 4)
            if (id == "fmt ") {
                val fmt = ByteArray(minOf(size, 16L).toInt())
                raf.readFully(fmt)
                if (fmt.size >= 16) {
                    channels = le16(fmt, 2).coerceAtLeast(1)
                    bits = le16(fmt, 14)
                }
                raf.seek(raf.filePointer + (size - fmt.size) + (size and 1L))
            } else if (id == "data") {
                dataStart = raf.filePointer
                val available = raf.length() - dataStart
                // A recording cut short may not have its length written in
                dataLength = if (size <= 0L || size > available) available else size
                break
            } else {
                raf.seek(raf.filePointer + size + (size and 1L))
            }
        }
        if (dataStart < 0 || bits != 16) return emptyList()
        val frameBytes = 2 * channels
        val frames = dataLength / frameBytes
        if (frames < BARS * 64L) return emptyList()

        val buffer = ByteArray(WINDOW_FRAMES * frameBytes)
        val levels = DoubleArray(BARS)
        for (bar in 0 until BARS) {
            val from = frames * bar / BARS
            val to = frames * (bar + 1) / BARS
            val span = to - from
            var sum = 0.0
            var count = 0L
            for (w in 0 until WINDOWS_PER_BAR) {
                val start = from + span * w / WINDOWS_PER_BAR
                val take = minOf(WINDOW_FRAMES.toLong(), to - start).toInt()
                if (take <= 0) continue
                raf.seek(dataStart + start * frameBytes)
                val bytes = take * frameBytes
                raf.readFully(buffer, 0, bytes)
                // The first channel's samples
                var i = 0
                while (i + 1 < bytes) {
                    val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort().toDouble()
                    sum += sample * sample
                    count++
                    i += frameBytes
                }
            }
            levels[bar] = if (count > 0) sqrt(sum / count) else 0.0
        }
        val loudest = levels.maxOrNull() ?: 0.0
        if (loudest <= 0.0) return emptyList()
        // Quieter bars stay visible (a square root), and the list stays small in the message
        return levels.map { (sqrt(it / loudest) * 100).toInt() / 100.0 }
    }

    private fun le16(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xFF) or ((bytes[at + 1].toInt() and 0xFF) shl 8)

    private fun le32(bytes: ByteArray, at: Int): Long =
        ((bytes[at].toLong() and 0xFF) or
            ((bytes[at + 1].toLong() and 0xFF) shl 8) or
            ((bytes[at + 2].toLong() and 0xFF) shl 16) or
            ((bytes[at + 3].toLong() and 0xFF) shl 24))
}
