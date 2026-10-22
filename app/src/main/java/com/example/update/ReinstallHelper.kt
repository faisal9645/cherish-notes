package com.example.update

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * A clean reinstall: download the latest APK into the public Downloads folder (so it survives the
 * uninstall), then open the system uninstall dialog. The user confirms the uninstall and taps the
 * downloaded APK to install again — a removed app can't relaunch itself, so those taps are theirs.
 *
 * Reinstalling resets the per-app usage counters Android keeps (data, battery, screen time); an
 * ordinary update over the top does not. The leftover installer is deleted on the next launch.
 */
object ReinstallHelper {
    private const val TAG = "ReinstallHelper"
    private const val VERSION_JSON_URL = "https://raw.githubusercontent.com/faisal9645/cherish-notes/main/version.json"
    private const val INSTALLER_PREFIX = "app_update_"

    data class ApkInfo(val downloadUrl: String, val versionCode: Int, val versionName: String) {
        val fileName: String get() = "$INSTALLER_PREFIX$versionCode.apk"
    }

    /** The latest APK's address and version, read from version.json (independent of "is it newer"). */
    suspend fun fetchLatestApk(): ApkInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(VERSION_JSON_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("Cache-Control", "no-cache")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val json = JSONObject(text)
            val url = json.optString("downloadUrl", "")
            val code = json.optInt("versionCode", 0)
            val name = json.optString("versionName", "")
            if (url.isBlank() || code <= 0) null else ApkInfo(url, code, name)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read the latest version", e)
            null
        }
    }

    /**
     * Downloads [info]'s APK into the public Downloads folder as [ApkInfo.fileName]. Returns true on
     * success. A file of the same name there already is replaced.
     */
    suspend fun downloadToDownloads(context: Context, info: ApkInfo, onProgress: (Float) -> Unit = {}): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // Clear any earlier installer with this name first
                deleteInstaller(context, info.fileName)
                val conn = resolveConnection(info.downloadUrl)
                val total = conn.contentLength.toLong()
                conn.inputStream.use { input ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val resolver = context.contentResolver
                        val values = ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, info.fileName)
                            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
                            put(MediaStore.Downloads.IS_PENDING, 1)
                        }
                        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        val uri = resolver.insert(collection, values) ?: return@withContext false
                        resolver.openOutputStream(uri).use { out ->
                            if (out == null) return@withContext false
                            copy(input, out, total, onProgress)
                        }
                        values.clear()
                        values.put(MediaStore.Downloads.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                    } else {
                        @Suppress("DEPRECATION")
                        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        if (!dir.exists()) dir.mkdirs()
                        val file = File(dir, info.fileName)
                        file.outputStream().use { out -> copy(input, out, total, onProgress) }
                    }
                }
                conn.disconnect()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Download to Downloads failed", e)
                false
            }
        }

    /** Opens the system uninstall dialog for this app. The user confirms; then the app is removed. */
    fun launchUninstall(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${context.packageName}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(Intent.EXTRA_RETURN_RESULT, false)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Couldn't open the uninstall dialog", e)
        }
    }

    /** Deletes leftover installers (app_update_*.apk) from Downloads; call once the new app is running. */
    fun cleanupStaleInstallers(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val selection = "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
                resolver.query(collection, arrayOf(MediaStore.Downloads._ID), selection, arrayOf("$INSTALLER_PREFIX%.apk"), null)?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    while (c.moveToNext()) {
                        val uri = android.content.ContentUris.withAppendedId(collection, c.getLong(idCol))
                        runCatching { resolver.delete(uri, null, null) }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                dir.listFiles { f -> f.name.startsWith(INSTALLER_PREFIX) && f.name.endsWith(".apk") }
                    ?.forEach { runCatching { it.delete() } }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't clean up old installers", e)
        }
    }

    private fun deleteInstaller(context: Context, fileName: String) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
                resolver.query(collection, arrayOf(MediaStore.Downloads._ID), selection, arrayOf(fileName), null)?.use { c ->
                    val idCol = c.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                    while (c.moveToNext()) {
                        val uri = android.content.ContentUris.withAppendedId(collection, c.getLong(idCol))
                        runCatching { resolver.delete(uri, null, null) }
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                File(dir, fileName).takeIf { it.exists() }?.let { runCatching { it.delete() } }
            }
        } catch (_: Exception) {}
    }

    private fun resolveConnection(url: String): HttpURLConnection {
        var current = url
        var redirects = 0
        while (true) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 30000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/octet-stream")
            }
            val code = conn.responseCode
            if (code in 300..399 && redirects < 5) {
                val next = conn.getHeaderField("Location")
                conn.disconnect()
                if (next.isNullOrBlank()) throw IllegalStateException("Redirect with no target")
                current = next
                redirects++
                continue
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw IllegalStateException("Download failed: HTTP $code")
            }
            return conn
        }
    }

    private fun copy(input: java.io.InputStream, out: java.io.OutputStream, total: Long, onProgress: (Float) -> Unit) {
        val buffer = ByteArray(64 * 1024)
        var read: Int
        var done = 0L
        while (input.read(buffer).also { read = it } != -1) {
            out.write(buffer, 0, read)
            done += read
            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
        }
        out.flush()
    }
}
