package com.example.util

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

/**
 * Voice notes and videos, once played, kept in the app's private storage: nothing else on the
 * phone sees them, they go when the app is uninstalled, and they're never downloaded twice. Past a
 * size limit the ones played longest ago make room (and download again if played again).
 */
object KeptMedia {
    private const val TAG = "KeptMedia"
    private const val MAX_VOICE_BYTES = 100L * 1024 * 1024
    private const val MAX_VIDEO_BYTES = 300L * 1024 * 1024
    private val AUDIO_EXTENSIONS = setOf("ogg", "opus", "m4a", "aac", "mp3", "wav", "3gp", "amr")

    fun voiceDir(context: Context): File = File(context.filesDir, "kept_media/voice").apply { mkdirs() }

    fun videoDir(context: Context): File = File(context.filesDir, "kept_media/video").apply { mkdirs() }

    /** Where a voice note's copy goes: named for its message, so deleting the message removes it. */
    private fun voiceFile(context: Context, messageId: String, url: String): File {
        val extension = url.substringBefore('?').substringAfterLast('.', "").lowercase()
            .takeIf { it in AUDIO_EXTENSIONS } ?: "m4a"
        return File(voiceDir(context), "voice_" + abs(messageId.hashCode()) + "." + extension)
    }

    /** The voice note's kept copy, if it has one (marked as just played, for the size limit). */
    fun keptVoice(context: Context, messageId: String, url: String): File? =
        voiceFile(context, messageId, url).takeIf { it.exists() && it.length() > 0L }?.also {
            it.setLastModified(System.currentTimeMillis())
        }

    /** Downloads a voice note once into the app's storage (null when that failed: it then streams). */
    fun downloadVoice(context: Context, messageId: String, url: String): File? {
        keptVoice(context, messageId, url)?.let { return it }
        val target = voiceFile(context, messageId, url)
        val part = File(target.parentFile, target.name + ".part")
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
            }
            try {
                if (connection.responseCode !in 200..299) return null
                connection.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
            } finally {
                connection.disconnect()
            }
            if (part.length() > 0L && part.renameTo(target)) target else {
                part.delete()
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't keep a voice note", e)
            part.delete()
            null
        }
    }

    /** A deleted message's voice note goes from the phone too. */
    fun forgetVoice(context: Context, messageId: String) {
        val prefix = "voice_" + abs(messageId.hashCode()) + "."
        voiceDir(context).listFiles()?.forEach { if (it.name.startsWith(prefix)) it.delete() }
    }

    /** Past the size limits, the copies played longest ago go first. */
    fun trim(context: Context) {
        trimDir(voiceDir(context), MAX_VOICE_BYTES)
        trimDir(videoDir(context), MAX_VIDEO_BYTES)
    }

    private fun trimDir(dir: File, maxBytes: Long) {
        val files = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".tmp") }
            ?.sortedBy { it.lastModified() } ?: return
        var size = files.sumOf { it.length() }
        for (file in files) {
            if (size <= maxBytes) break
            val length = file.length()
            if (file.delete()) size -= length
        }
    }
}
