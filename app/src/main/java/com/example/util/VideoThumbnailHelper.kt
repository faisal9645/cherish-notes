package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

object VideoThumbnailHelper {
    private val maxCacheBytes = ((Runtime.getRuntime().maxMemory() / 16)
        .coerceIn(4 * 1024 * 1024L, 24 * 1024 * 1024L)).toInt()

    private val memoryCache = object : LruCache<String, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount.coerceAtLeast(1)
        }
    }

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

    fun getVideoCacheFile(context: Context, url: String): File {
        val safeKey = abs(url.hashCode()).toString(16)
        val dir = File(context.cacheDir, "video_notes").apply { if (!exists()) mkdirs() }
        return File(dir, "vn_$safeKey.mp4")
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
            var retriever: MediaMetadataRetriever? = null
            try {
                val localFile = getCachedFile(context, videoPathOrUrl)

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
                val rawBitmap = retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
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
                }
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
