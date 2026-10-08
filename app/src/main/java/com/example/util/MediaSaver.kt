package com.example.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Saves chat photos and videos to the phone's gallery (Pictures/Cherish, Movies/Cherish). */
object MediaSaver {

    /** Before Android 10 saving to shared storage needs the storage permission. */
    val needsStoragePermission: Boolean get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /** Whether [url] points at a video rather than a photo. */
    fun looksLikeVideo(url: String): Boolean =
        url.startsWith("data:video") ||
            url.contains("videonote", ignoreCase = true) ||
            url.contains("/videos/", ignoreCase = true) ||
            url.contains("%2Fvideos%2F", ignoreCase = true) ||
            url.substringBefore('?').endsWith(".mp4", ignoreCase = true)

    /**
     * Copies the original file behind [url] (storage link, inline data, local file) into the
     * gallery, unchanged. True only when it was really saved.
     */
    suspend fun saveToGallery(context: Context, url: String, isVideo: Boolean = looksLikeVideo(url)): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val (input, mime) = open(context, url, isVideo) ?: return@withContext false
                input.use { source ->
                    val extension = when {
                        mime.endsWith("png") -> "png"
                        mime.endsWith("webp") -> "webp"
                        mime.startsWith("video") -> "mp4"
                        else -> "jpg"
                    }
                    val name = "Cherish_${System.currentTimeMillis()}.$extension"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        saveWithMediaStore(context, source, name, mime, isVideo)
                    } else {
                        saveToPublicFolder(context, source, name, mime, isVideo)
                    }
                }
            } catch (e: Exception) {
                Log.w("MediaSaver", "Saving to gallery failed", e)
                false
            }
        }

    /** The file's bytes and type, or null when it can't be read. */
    private fun open(context: Context, url: String, isVideo: Boolean): Pair<InputStream, String>? {
        val fallbackMime = if (isVideo) "video/mp4" else "image/jpeg"
        return when {
            url.startsWith("data:") -> {
                val mime = url.substringAfter("data:").substringBefore(";").ifBlank { fallbackMime }
                ByteArrayInputStream(Base64.decode(url.substringAfter("base64,"), Base64.DEFAULT)) to mime
            }
            url.startsWith("http://") || url.startsWith("https://") -> {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    instanceFollowRedirects = true
                }
                if (connection.responseCode !in 200..299) {
                    connection.disconnect()
                    return null
                }
                val mime = connection.contentType?.substringBefore(";")?.takeIf {
                    it.startsWith("image/") || it.startsWith("video/")
                } ?: fallbackMime
                connection.inputStream to mime
            }
            url.startsWith("content://") -> (context.contentResolver.openInputStream(Uri.parse(url)) ?: return null) to fallbackMime
            else -> {
                val file = File(url.removePrefix("file://"))
                if (!file.exists()) return null
                FileInputStream(file) to fallbackMime
            }
        }
    }

    /** Android 10+: written as "pending", so the gallery only shows it once it's complete. */
    private fun saveWithMediaStore(context: Context, source: InputStream, name: String, mime: String, isVideo: Boolean): Boolean {
        val resolver = context.contentResolver
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                (if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/Cherish"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val item = resolver.insert(collection, values) ?: return false
        return try {
            val output = resolver.openOutputStream(item) ?: throw IllegalStateException("No output stream")
            output.use { source.copyTo(it) }
            resolver.update(item, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            true
        } catch (e: Exception) {
            resolver.delete(item, null, null)
            throw e
        }
    }

    /** Android 7-9: a file in the public folder, then announced to the gallery. */
    private fun saveToPublicFolder(context: Context, source: InputStream, name: String, mime: String, isVideo: Boolean): Boolean {
        @Suppress("DEPRECATION")
        val dir = File(
            Environment.getExternalStoragePublicDirectory(
                if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            ),
            "Cherish"
        ).apply { mkdirs() }
        val file = File(dir, name)
        file.outputStream().use { source.copyTo(it) }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
        return true
    }
}
