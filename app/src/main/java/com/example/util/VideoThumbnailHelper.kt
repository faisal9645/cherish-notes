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
    private val memoryCache = object : LruCache<String, Bitmap>(50) {}

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
        if (!videoPathOrUrl.startsWith("http://") && !videoPathOrUrl.startsWith("https://")) {
            val f = if (videoPathOrUrl.startsWith("file://")) File(videoPathOrUrl.removePrefix("file://")) else File(videoPathOrUrl)
            return@withContext if (f.exists() && f.length() > 1024) f else null
        }
        val target = getVideoCacheFile(context, videoPathOrUrl)
        if (target.exists() && target.length() > 1024) {
            return@withContext target
        }
        val temp = File(target.parentFile, "${target.name}.tmp")
        try {
            val conn = URL(videoPathOrUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 12_000
            conn.readTimeout = 20_000
            conn.instanceFollowRedirects = true
            conn.connect()
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { input ->
                    FileOutputStream(temp).use { output ->
                        input.copyTo(output)
                    }
                }
                if (temp.exists() && temp.length() > 1024) {
                    if (target.exists()) target.delete()
                    if (temp.renameTo(target)) {
                        return@withContext target
                    }
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
                val localFile = if (videoPathOrUrl.startsWith("http://") || videoPathOrUrl.startsWith("https://")) {
                    val cached = getVideoCacheFile(context, videoPathOrUrl)
                    if (cached.exists() && cached.length() > 1024) cached else getCachedFile(context, videoPathOrUrl)
                } else {
                    val f = if (videoPathOrUrl.startsWith("file://")) File(videoPathOrUrl.removePrefix("file://")) else File(videoPathOrUrl)
                    if (f.exists() && f.length() > 1024) f else null
                }

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
                val bitmap = retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(300_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
                    ?: retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)

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
