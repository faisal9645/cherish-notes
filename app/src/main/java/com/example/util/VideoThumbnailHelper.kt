package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

data class VideoInfo(
    val aspect: Float,
    val durationSeconds: Int,
    val width: Int,
    val height: Int
)

object VideoThumbnailHelper {
    // Serialize frame extraction: fast scrolling must not start multiple native decoders.
    private val thumbnailMutex = Mutex()
    private val maxCacheBytes = ((Runtime.getRuntime().maxMemory() / 16)
        .coerceIn(4 * 1024 * 1024L, 24 * 1024 * 1024L)).toInt()

    private val memoryCache = object : LruCache<String, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount.coerceAtLeast(1)
        }
    }

    private val videoInfoCache = java.util.concurrent.ConcurrentHashMap<String, VideoInfo>()

    fun getVideoInfo(videoPathOrUrl: String): VideoInfo? = videoInfoCache[videoPathOrUrl]

    fun trimMemory(level: Int) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
            memoryCache.evictAll()
        } else if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            memoryCache.trimToSize(memoryCache.maxSize() / 2)
        }
    }

    fun clearMemoryCache() {
        memoryCache.evictAll()
    }

    /**
     * A video's kept copy: in the app's private storage (see KeptMedia), not the cache that Android
     * or the cache clean-up empties, so a played video is never downloaded twice. One left in the
     * old cache folder moves over.
     */
    fun getVideoCacheFile(context: Context, url: String): File {
        val safeKey = abs(url.hashCode()).toString(16)
        val file = File(KeptMedia.videoDir(context), "vn_$safeKey.mp4")
        if (!file.exists()) {
            val old = File(File(context.cacheDir, "video_notes"), "vn_$safeKey.mp4")
            if (old.exists() && old.length() > 1024) old.renameTo(file)
        } else {
            // Just used: the size limit makes room from the ones played longest ago
            file.setLastModified(System.currentTimeMillis())
        }
        return file
    }

    fun cacheLocalVideo(context: Context, sourceFile: File, url: String) {
        if (!sourceFile.exists() || sourceFile.length() <= 1024) return
        try {
            val target = getVideoCacheFile(context, url)
            if (!target.exists() || target.length() != sourceFile.length()) {
                sourceFile.copyTo(target, overwrite = true)
            }
        } catch (e: Exception) {
            Log.w("VideoThumbnailHelper", "Failed to cache local video: ${e.message}")
        }
    }

    suspend fun getCachedFile(context: Context, videoPathOrUrl: String): File? = withContext(Dispatchers.IO) {
        if (videoPathOrUrl.isBlank()) return@withContext null

        // 1. Base64 data URI
        if (videoPathOrUrl.startsWith("data:video")) {
            val target = getVideoCacheFile(context, videoPathOrUrl)
            if (target.exists() && target.length() > 1024) return@withContext target
            return@withContext try {
                val base64Data = videoPathOrUrl.substringAfter("base64,")
                val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                target.writeBytes(bytes)
                if (target.exists() && target.length() > 1024) target else null
            } catch (e: Exception) {
                Log.w("VideoThumbnailHelper", "Failed decoding base64 video", e)
                null
            }
        }

        // 2. Local content URI
        if (videoPathOrUrl.startsWith("content://")) {
            val target = getVideoCacheFile(context, videoPathOrUrl)
            if (target.exists() && target.length() > 1024) return@withContext target
            return@withContext try {
                context.contentResolver.openInputStream(Uri.parse(videoPathOrUrl))?.use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                }
                if (target.exists() && target.length() > 1024) target else null
            } catch (e: Exception) {
                Log.w("VideoThumbnailHelper", "Failed copying content video", e)
                null
            }
        }

        // 3. Local file path
        if (!videoPathOrUrl.startsWith("http://") && !videoPathOrUrl.startsWith("https://")) {
            val f = if (videoPathOrUrl.startsWith("file://")) File(videoPathOrUrl.removePrefix("file://")) else File(videoPathOrUrl)
            return@withContext if (f.exists() && f.length() > 1024) f else null
        }

        // 4. Remote HTTP/HTTPS URL (follow redirects up to 6 times)
        val target = getVideoCacheFile(context, videoPathOrUrl)
        if (target.exists() && target.length() > 1024) {
            return@withContext target
        }
        val temp = File(target.parentFile, "${target.name}.tmp")
        var currentUrl = videoPathOrUrl
        var redirects = 0
        val maxRedirects = 6
        try {
            while (redirects < maxRedirects) {
                val urlObj = URL(currentUrl)
                val conn = (urlObj.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android; Mobile)")
                    setRequestProperty("Accept", "*/*")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (!location.isNullOrBlank()) {
                        currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                            location
                        } else {
                            URL(urlObj, location).toString()
                        }
                        redirects++
                        continue
                    } else {
                        break
                    }
                } else if (code in 200..299) {
                    conn.inputStream.use { input ->
                        FileOutputStream(temp).use { output ->
                            input.copyTo(output)
                        }
                    }
                    conn.disconnect()
                    if (temp.exists() && temp.length() > 1024) {
                        if (target.exists()) target.delete()
                        if (temp.renameTo(target)) {
                            return@withContext target
                        }
                    }
                    break
                } else {
                    Log.w("VideoThumbnailHelper", "HTTP error $code while downloading video: $currentUrl")
                    conn.disconnect()
                    break
                }
            }
        } catch (e: Exception) {
            Log.w("VideoThumbnailHelper", "Failed downloading video for cache: ${e.message}")
        } finally {
            if (temp.exists()) temp.delete()
        }
        null
    }

    suspend fun getThumbnail(context: Context, videoPathOrUrl: String): Bitmap? {
        if (videoPathOrUrl.isBlank()) return null
        
        memoryCache.get(videoPathOrUrl)?.let { return it }

        return withContext(Dispatchers.IO) {
            thumbnailMutex.withLock {
            memoryCache.get(videoPathOrUrl)?.let { return@withLock it }
            val key = java.security.MessageDigest.getInstance("SHA-256")
                .digest(videoPathOrUrl.toByteArray()).joinToString("") { "%02x".format(it) }
            val posterDir = File(context.cacheDir, "video_posters").apply { mkdirs() }
            val poster = File(posterDir, "$key.jpg")
            android.graphics.BitmapFactory.decodeFile(poster.absolutePath)?.let {
                poster.setLastModified(System.currentTimeMillis())
                memoryCache.put(videoPathOrUrl, it)
                return@withLock it
            }
            var retriever: MediaMetadataRetriever? = null
            try {
                // Use an existing download, but never fetch an entire video just for its poster.
                val localFile = getVideoCacheFile(context, videoPathOrUrl).takeIf { it.exists() && it.length() > 1024 }
                    ?: if (videoPathOrUrl.startsWith("data:video")) getCachedFile(context, videoPathOrUrl) else null

                retriever = MediaMetadataRetriever()
                if (localFile != null && localFile.exists()) {
                    retriever.setDataSource(localFile.absolutePath)
                } else if (videoPathOrUrl.startsWith("content://")) {
                    retriever.setDataSource(context, Uri.parse(videoPathOrUrl))
                } else if (videoPathOrUrl.startsWith("http://") || videoPathOrUrl.startsWith("https://")) {
                    retriever.setDataSource(videoPathOrUrl, HashMap())
                } else {
                    retriever.setDataSource(context, Uri.parse(videoPathOrUrl))
                }

                // Camera startup frame (at time 0) is pitch black.
                // Seek to 500ms (500_000 us) to get the properly exposed face / scene.
                val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 480
                val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 480
                val scale = minOf(1f, 480f / maxOf(rawWidth, rawHeight).coerceAtLeast(1))
                val rawBitmap = if (android.os.Build.VERSION.SDK_INT >= 27) {
                    retriever.getScaledFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        (rawWidth * scale).toInt().coerceAtLeast(1), (rawHeight * scale).toInt().coerceAtLeast(1))
                } else retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(300_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
                    ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

                val bitmap = if (rawBitmap != null) {
                    val maxDim = 480
                    if (rawBitmap.width > maxDim || rawBitmap.height > maxDim) {
                        val ratio = rawBitmap.width.toFloat() / rawBitmap.height.toFloat()
                        val targetWidth: Int
                        val targetHeight: Int
                        if (ratio > 1f) {
                            targetWidth = maxDim
                            targetHeight = (maxDim / ratio).toInt().coerceAtLeast(1)
                        } else {
                            targetHeight = maxDim
                            targetWidth = (maxDim * ratio).toInt().coerceAtLeast(1)
                        }
                        val scaled = Bitmap.createScaledBitmap(rawBitmap, targetWidth, targetHeight, true)
                        if (scaled !== rawBitmap) {
                            rawBitmap.recycle()
                        }
                        scaled
                    } else {
                        rawBitmap
                    }
                } else null

                if (bitmap != null) {
                    memoryCache.put(videoPathOrUrl, bitmap)
                    // Disposable bounded disk cache survives scrolling, memory trims and restarts.
                    runCatching {
                        val temp = File(posterDir, "$key.tmp")
                        FileOutputStream(temp).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                        if (!temp.renameTo(poster)) temp.delete()
                        posterDir.listFiles()?.filter { it.extension == "jpg" }
                            ?.sortedByDescending { it.lastModified() }?.drop(128)?.forEach { it.delete() }
                    }
                }

                try {
                    val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: (bitmap?.width ?: 0)
                    val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: (bitmap?.height ?: 0)
                    val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val isRotated = rot == 90 || rot == 270
                    val orientedW = if (isRotated) rawH else rawW
                    val orientedH = if (isRotated) rawW else rawH
                    val aspect = if (orientedH > 0 && orientedW > 0) orientedW.toFloat() / orientedH.toFloat() else (bitmap?.let { it.width.toFloat() / it.height.toFloat() } ?: 1f)
                    videoInfoCache[videoPathOrUrl] = VideoInfo(
                        aspect = aspect,
                        durationSeconds = (durMs / 1000).toInt().coerceAtLeast(0),
                        width = orientedW,
                        height = orientedH
                    )
                } catch (_: Exception) {}

                bitmap
            } catch (e: Exception) {
                Log.w("VideoThumbnailHelper", "Failed getting thumbnail: ${e.message}")
                null
            } finally {
                try {
                    retriever?.release()
                } catch (_: Exception) {}
            }
            }
        }
    }
}
