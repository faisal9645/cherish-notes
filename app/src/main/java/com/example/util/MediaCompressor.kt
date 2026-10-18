package com.example.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Makes media smaller before it uploads, with the phone's own encoders (Media3 Transformer):
 * gallery videos to 720p H.264, call recordings from the call engine's raw WAV to AAC. When it
 * can't (an unusual format, an encoder that refuses) or it wouldn't help, the caller sends the
 * original: nothing is ever held back because it couldn't be made smaller.
 */
@OptIn(UnstableApi::class)
object MediaCompressor {
    private const val TAG = "MediaCompressor"

    /** Videos go out with their short side at most this many pixels (720p, like messaging apps). */
    private const val VIDEO_SHORT_SIDE = 720
    private const val VIDEO_BIT_RATE = 2_000_000
    private const val VIDEO_AUDIO_BIT_RATE = 96_000
    /** A video already this small, or this light (bits a second), is sent as it is. */
    private const val SMALL_VIDEO_BYTES = 6L * 1024 * 1024
    private const val LIGHT_VIDEO_BIT_RATE = 2_600_000
    /** A call recording is speech: AAC at this rate is clear, about 0.35 MB a minute. */
    private const val RECORDING_BIT_RATE = 48_000
    private const val VIDEO_TIMEOUT_MS = 10L * 60 * 1000
    private const val RECORDING_TIMEOUT_MS = 5L * 60 * 1000
    private const val MIN_OUTPUT_BYTES = 128L

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * A smaller copy of the video at [source] (a content or file link), or null to send the
     * original. [onProgress] goes from 0 to 1.
     */
    suspend fun compressVideo(context: Context, source: Uri, onProgress: (Float) -> Unit = {}): File? {
        val app = context.applicationContext
        val info = withContext(Dispatchers.IO) { probeVideo(app, source) } ?: return null
        val shortSide = minOf(info.width, info.height)
        val bytes = withContext(Dispatchers.IO) { sizeOf(app, source) }.takeIf { it > 0 } ?: info.estimatedBytes
        val small = bytes in 1..SMALL_VIDEO_BYTES
        val light = shortSide <= VIDEO_SHORT_SIDE && info.bitRate in 1..LIGHT_VIDEO_BIT_RATE
        if (small || light) return null

        val output = File(File(app.cacheDir, "compressed_media").apply { mkdirs() }, "vid_${UUID.randomUUID()}.mp4")
        val effects = if (shortSide > VIDEO_SHORT_SIDE) listOf(Presentation.createForShortSide(VIDEO_SHORT_SIDE)) else emptyList()
        val item = EditedMediaItem.Builder(MediaItem.fromUri(source))
            .setEffects(Effects(emptyList(), effects))
            .build()
        val composition = Composition.Builder(singleItemSequence(item))
            // HDR phone videos come out as ordinary (SDR) video every phone shows correctly
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()
        val encoders = DefaultEncoderFactory.Builder(app)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(VIDEO_BIT_RATE).build())
            .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(VIDEO_AUDIO_BIT_RATE).build())
            .setEnableFallback(true)
            .build()
        val ok = export(app, composition, output, encoders, MimeTypes.VIDEO_H264, VIDEO_TIMEOUT_MS, onProgress)
        // Only worth sending if it really is smaller
        if (!ok || output.length() < MIN_OUTPUT_BYTES || (bytes > 0 && output.length() >= bytes)) {
            output.delete()
            return null
        }
        Log.i(TAG, "Video made smaller: ${bytes / 1024} KB -> ${output.length() / 1024} KB")
        return output
    }

    /** Writes the recording [wav] as AAC to [output] (.m4a); false when it couldn't. */
    suspend fun compressRecording(context: Context, wav: File, output: File): Boolean {
        if (!wav.exists() || wav.length() < MIN_OUTPUT_BYTES) return false
        val app = context.applicationContext
        val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(wav)))
            .setRemoveVideo(true)
            .build()
        val composition = Composition.Builder(singleItemSequence(item)).build()
        val encoders = DefaultEncoderFactory.Builder(app)
            .setRequestedAudioEncoderSettings(AudioEncoderSettings.Builder().setBitrate(RECORDING_BIT_RATE).build())
            .setEnableFallback(true)
            .build()
        val ok = export(app, composition, output, encoders, null, RECORDING_TIMEOUT_MS) {}
        if (!ok || output.length() < MIN_OUTPUT_BYTES) {
            output.delete()
            return false
        }
        Log.i(TAG, "Call recording made smaller: ${wav.length() / 1024} KB -> ${output.length() / 1024} KB")
        return true
    }

    /** Runs one export into [output]; false when it failed or took longer than [timeoutMs]. */
    private suspend fun export(
        context: Context,
        composition: Composition,
        output: File,
        encoders: DefaultEncoderFactory,
        videoMimeType: String?,
        timeoutMs: Long,
        onProgress: (Float) -> Unit
    ): Boolean = withContext(Dispatchers.Main) {
        output.delete()
        var transformer: Transformer? = null
        val finished = try {
            withTimeoutOrNull(timeoutMs) {
                coroutineScope {
                    val progress = launch {
                        val holder = ProgressHolder()
                        while (true) {
                            delay(PROGRESS_INTERVAL_MS)
                            val running = transformer ?: continue
                            if (running.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                                onProgress((holder.progress / 100f).coerceIn(0f, 1f))
                            }
                        }
                    }
                    try {
                        suspendCancellableCoroutine { continuation ->
                            val builder = Transformer.Builder(context)
                                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                                .setEncoderFactory(encoders)
                                .setLooper(Looper.getMainLooper())
                                .addListener(object : Transformer.Listener {
                                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                        if (continuation.isActive) continuation.resume(true)
                                    }

                                    override fun onError(
                                        composition: Composition,
                                        exportResult: ExportResult,
                                        exportException: ExportException
                                    ) {
                                        Log.w(TAG, "Couldn't make it smaller", exportException)
                                        if (continuation.isActive) continuation.resume(false)
                                    }
                                })
                            if (videoMimeType != null) builder.setVideoMimeType(videoMimeType)
                            val started = builder.build()
                            transformer = started
                            // Transformer is only used on the thread it was built for
                            continuation.invokeOnCancellation { mainHandler.post { runCatching { started.cancel() } } }
                            started.start(composition, output.absolutePath)
                        }
                    } finally {
                        progress.cancel()
                    }
                }
            }
        } catch (e: CancellationException) {
            output.delete()
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't make it smaller", e)
            false
        }
        if (finished != true) {
            if (finished == null) Log.w(TAG, "Making it smaller took too long; sending the original")
            output.delete()
        }
        finished == true
    }

    /** How big the file at [source] is; 0 when the app it comes from doesn't say. */
    private fun sizeOf(context: Context, source: Uri): Long = try {
        if (source.scheme == "file") {
            source.path?.let { File(it).length() } ?: 0L
        } else {
            context.contentResolver.openAssetFileDescriptor(source, "r")?.use { it.length.coerceAtLeast(0L) } ?: 0L
        }
    } catch (e: Exception) {
        0L
    }

    /**
     * One item to export. The older builder works out the tracks from the media itself; the newer
     * one needs them listed, and a video with no sound has no audio track to list.
     */
    @Suppress("DEPRECATION")
    private fun singleItemSequence(item: EditedMediaItem): EditedMediaItemSequence = EditedMediaItemSequence.Builder(item).build()

    private class VideoInfo(val width: Int, val height: Int, val bitRate: Int, val estimatedBytes: Long)

    /** The video's size and bit rate; null when it has no video to work on. */
    private fun probeVideo(context: Context, source: Uri): VideoInfo? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, source)
            if (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes") return null
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val bitRate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull() ?: 0
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            if (width <= 0 || height <= 0) return null
            VideoInfo(width, height, bitRate, if (bitRate > 0) bitRate.toLong() / 8 * durationMs / 1000 else 0L)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read the video", e)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private const val PROGRESS_INTERVAL_MS = 250L
}
