package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object VideoThumbnailHelper {
    private val memoryCache = object : LruCache<String, Bitmap>(30) {}

    suspend fun getThumbnail(context: Context, videoPathOrUrl: String): Bitmap? {
        if (videoPathOrUrl.isBlank()) return null
        
        memoryCache.get(videoPathOrUrl)?.let { return it }

        return withContext(Dispatchers.IO) {
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                if (videoPathOrUrl.startsWith("http://") || videoPathOrUrl.startsWith("https://")) {
                    retriever.setDataSource(videoPathOrUrl, HashMap())
                } else if (videoPathOrUrl.startsWith("content://")) {
                    retriever.setDataSource(context, Uri.parse(videoPathOrUrl))
                } else {
                    val file = File(videoPathOrUrl)
                    if (file.exists()) {
                        retriever.setDataSource(file.absolutePath)
                    } else {
                        retriever.setDataSource(context, Uri.parse(videoPathOrUrl))
                    }
                }
                val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.frameAtTime
                if (bitmap != null) {
                    memoryCache.put(videoPathOrUrl, bitmap)
                }
                bitmap
            } catch (e: Exception) {
                null
            } finally {
                try {
                    retriever?.release()
                } catch (_: Exception) {}
            }
        }
    }
}
