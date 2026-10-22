package com.example.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

object StorageOptimizer {
    private const val TAG = "StorageOptimizer"

    // Storage budget tuned for an innocent ~70-85MB Notes app profile in Android Settings
    private const val MAX_TOTAL_CACHE_BYTES = 50L * 1024 * 1024 // 50 MB
    private const val TARGET_CACHE_BYTES = 30L * 1024 * 1024 // 30 MB

    private const val MAX_MEDIA_TEMP_AGE_MS = 4L * 60 * 60 * 1000 // 4 hours
    private const val MAX_CAMERA_SNAP_AGE_MS = 1L * 60 * 60 * 1000 // 1 hour
    private const val MAX_TEMP_FILE_AGE_MS = 1L * 60 * 60 * 1000 // 1 hour

    // Category caps (enforced via LRU)
    private const val MAX_VIDEO_CACHE_BYTES = 18L * 1024 * 1024 // ~3-4 recent short video clips
    private const val MAX_VOICE_CACHE_BYTES = 8L * 1024 * 1024 // ~70-80 voice memos
    private const val MAX_STAGING_MEDIA_BYTES = 8L * 1024 * 1024 // Outgoing uploads staging
    private const val MAX_CALL_RECORDINGS_BYTES = 150L * 1024 * 1024

    /**
     * Non-destructive, safe background maintenance that keeps the app snappy and
     * ensures its storage footprint in Android Settings matches a clean Notes app.
     * Removes orphaned uploads, camera snaps, stale video note caches, old update installers, and .tmp files.
     * Never deletes chat messages, databases, credentials, or permanent notes.
     */
    suspend fun pruneStaleCaches(context: Context) = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val cacheDir = context.cacheDir

            // 1. Clean stale temporary media folders
            cleanStaleFiles(File(cacheDir, "camera_snaps"), now - MAX_CAMERA_SNAP_AGE_MS)
            cleanStaleFiles(File(cacheDir, "compressed_media"), now - MAX_MEDIA_TEMP_AGE_MS)
            cleanStaleFiles(File(cacheDir, "outgoing_media"), now - MAX_MEDIA_TEMP_AGE_MS)
            cleanStaleFiles(File(cacheDir, "avatar_snaps"), now - MAX_MEDIA_TEMP_AGE_MS)
            cleanStaleFiles(File(cacheDir, "updates"), now - MAX_TEMP_FILE_AGE_MS)

            // 2. Clean temporary .tmp files
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.name.endsWith(".tmp", ignoreCase = true) && (now - file.lastModified() > MAX_TEMP_FILE_AGE_MS)) {
                    file.delete()
                }
            }

            // 3. Category-level LRU limits to keep individual media types bounded
            enforceLruLimit(File(cacheDir, "video_notes"), MAX_VIDEO_CACHE_BYTES)
            enforceLruLimit(File(cacheDir, "voice_cache"), MAX_VOICE_CACHE_BYTES)
            enforceLruLimit(File(cacheDir, "voice_notes"), MAX_VOICE_CACHE_BYTES)
            enforceLruLimit(File(cacheDir, "compressed_media"), MAX_STAGING_MEDIA_BYTES)
            enforceLruLimit(File(cacheDir, "outgoing_media"), MAX_STAGING_MEDIA_BYTES)
            enforceLruLimit(File(cacheDir, "callrec"), MAX_CALL_RECORDINGS_BYTES)
            enforceLruLimit(File(cacheDir, "call_recordings"), MAX_CALL_RECORDINGS_BYTES)

            // 4. Global cache threshold with tighter eviction if total exceeded
            val totalCache = getDirSize(cacheDir)
            if (totalCache > MAX_TOTAL_CACHE_BYTES) {
                enforceLruLimit(File(cacheDir, "video_notes"), TARGET_CACHE_BYTES / 3)
                enforceLruLimit(File(cacheDir, "compressed_media"), TARGET_CACHE_BYTES / 5)
                enforceLruLimit(File(cacheDir, "outgoing_media"), TARGET_CACHE_BYTES / 5)
                enforceLruLimit(File(cacheDir, "voice_cache"), TARGET_CACHE_BYTES / 5)
            }

            // 5. Played voice notes and videos are kept (see KeptMedia), within their own limits
            KeptMedia.trim(context)
        } catch (e: Exception) {
            Log.w(TAG, "pruneStaleCaches failed gracefully: ${e.message}")
        }
    }

    /**
     * Purges local cached media files associated with a deleted message ID.
     */
    fun deleteMessageMediaCache(context: Context, messageId: String) {
        if (messageId.isBlank()) return
        try {
            val hashKey = abs(messageId.hashCode()).toString()
            val hexKey = abs(messageId.hashCode()).toString(16)
            val cacheDir = context.cacheDir

            // Clean voice cache
            File(cacheDir, "voice_cache").listFiles()?.forEach { file ->
                if (file.name.contains(hashKey) || file.name.contains(hexKey)) {
                    file.delete()
                }
            }

            // Clean video note cache
            File(cacheDir, "video_notes").listFiles()?.forEach { file ->
                if (file.name.contains(hashKey) || file.name.contains(hexKey)) {
                    file.delete()
                }
            }

            // Its kept voice note (see KeptMedia)
            KeptMedia.forgetVoice(context, messageId)
        } catch (_: Exception) {}
    }

    /**
     * Complete cache clearing invoked from Storage & Cache Manager screen.
     * Returns total bytes freed.
     */
    suspend fun performSafeCacheClean(context: Context): Long = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        val beforeSize = getDirSize(cacheDir)

        deleteDirContents(File(cacheDir, "compressed_media"))
        deleteDirContents(File(cacheDir, "outgoing_media"))
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
        val now = System.currentTimeMillis()
        val isCallRecDir = dir.name.equals("callrec", ignoreCase = true)
        val files = dir.listFiles()?.filter { file ->
            if (!file.isFile) return@filter false
            // Protect pending recording upload jobs (.json) and recordings written within last 2 hours
            if (isCallRecDir && (file.name.endsWith(".json", ignoreCase = true) || (now - file.lastModified() < 2 * 3600 * 1000L))) {
                return@filter false
            }
            true
        }?.sortedBy { it.lastModified() } ?: return
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
