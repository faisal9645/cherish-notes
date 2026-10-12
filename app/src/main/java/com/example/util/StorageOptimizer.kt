package com.example.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object StorageOptimizer {
    private const val TAG = "StorageOptimizer"
    private const val MAX_TOTAL_CACHE_BYTES = 250L * 1024 * 1024 // 250 MB
    private const val TARGET_CACHE_BYTES = 160L * 1024 * 1024 // 160 MB
    private const val MAX_MEDIA_TEMP_AGE_MS = 5L * 24 * 60 * 60 * 1000 // 5 days
    private const val MAX_TEMP_FILE_AGE_MS = 1L * 24 * 60 * 60 * 1000 // 24 hours

    /**
     * Non-destructive, safe background maintenance that keeps the app snappy even after months of heavy usage.
     * Removes orphaned uploads, camera snaps, stale video note caches, old update installers, and .tmp files.
     * Never deletes chat messages, databases, credentials, or permanent media.
     */
    suspend fun pruneStaleCaches(context: Context) = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val cacheDir = context.cacheDir

            // 1. Clean stale temporary media folders
            cleanStaleFiles(File(cacheDir, "compressed_media"), now - MAX_MEDIA_TEMP_AGE_MS)
            cleanStaleFiles(File(cacheDir, "camera_snaps"), now - MAX_MEDIA_TEMP_AGE_MS)
            cleanStaleFiles(File(cacheDir, "avatar_snaps"), now - MAX_MEDIA_TEMP_AGE_MS)
            cleanStaleFiles(File(cacheDir, "updates"), now - MAX_TEMP_FILE_AGE_MS)

            // 2. Clean temporary .tmp files
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".tmp", ignoreCase = true) && (now - file.lastModified() > MAX_TEMP_FILE_AGE_MS)) {
                    file.delete()
                }
            }

            // 3. Enforce maximum cache threshold with LRU eviction for video notes & media cache
            val totalCache = getDirSize(cacheDir)
            if (totalCache > MAX_TOTAL_CACHE_BYTES) {
                enforceLruLimit(File(cacheDir, "video_notes"), TARGET_CACHE_BYTES / 2)
                enforceLruLimit(File(cacheDir, "compressed_media"), TARGET_CACHE_BYTES / 4)
                enforceLruLimit(File(cacheDir, "voice_cache"), TARGET_CACHE_BYTES / 4)
            }
        } catch (e: Exception) {
            Log.w(TAG, "pruneStaleCaches failed gracefully: ${e.message}")
        }
    }

    /**
     * Complete cache clearing invoked from Storage & Cache Manager screen.
     * Returns total bytes freed.
     */
    suspend fun performSafeCacheClean(context: Context): Long = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        val beforeSize = getDirSize(cacheDir)

        deleteDirContents(File(cacheDir, "compressed_media"))
        deleteDirContents(File(cacheDir, "camera_snaps"))
        deleteDirContents(File(cacheDir, "avatar_snaps"))
        deleteDirContents(File(cacheDir, "updates"))
        deleteDirContents(File(cacheDir, "video_notes"))
        deleteDirContents(File(cacheDir, "image_cache"))
        deleteDirContents(File(cacheDir, "image_manager_disk_cache"))

        cacheDir.listFiles()?.forEach { file ->
            if (file.name != "voice_notes" && (file.name.contains("cache", ignoreCase = true) || file.name.endsWith(".tmp"))) {
                if (file.isDirectory) deleteDirContents(file) else file.delete()
            }
        }

        val afterSize = getDirSize(cacheDir)
        (beforeSize - afterSize).coerceAtLeast(0L)
    }

    private fun cleanStaleFiles(dir: File, olderThanTimestamp: Long) {
        if (!dir.exists() || !dir.isDirectory) return
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < olderThanTimestamp) {
                file.delete()
            }
        }
    }

    private fun enforceLruLimit(dir: File, maxBytes: Long) {
        if (!dir.exists() || !dir.isDirectory) return
        val files = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() } ?: return
        var currentSize = files.sumOf { it.length() }
        if (currentSize <= maxBytes) return

        for (file in files) {
            val len = file.length()
            if (file.delete()) {
                currentSize -= len
                if (currentSize <= maxBytes) break
            }
        }
    }

    fun deleteDirContents(dir: File?): Boolean {
        if (dir == null || !dir.exists() || !dir.isDirectory) return false
        var allDeleted = true
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                allDeleted = deleteDirContents(child) && child.delete() && allDeleted
            } else {
                allDeleted = child.delete() && allDeleted
            }
        }
        return allDeleted
    }

    fun getDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) getDirSize(file) else file.length()
        }
        return size
    }
}
