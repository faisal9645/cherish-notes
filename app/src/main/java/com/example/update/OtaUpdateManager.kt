package com.example.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val hasUpdate: Boolean = false,
    val latestVersionName: String = "",
    val latestVersionCode: Int = 0,
    val releaseNotes: String = "",
    val downloadUrl: String = "",
    val assetSize: Long = 0L,
    val releaseDate: String = ""
)

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpdateAvailable(val info: UpdateInfo) : UpdateState()
    object UpToDate : UpdateState()
    data class Downloading(val progress: Float, val downloadedBytes: Long, val totalBytes: Long) : UpdateState()
    data class ReadyToInstall(val apkFile: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

class OtaUpdateManager private constructor(private val appContext: Context) {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _latestUpdateInfo = MutableStateFlow<UpdateInfo?>(null)
    val latestUpdateInfo: StateFlow<UpdateInfo?> = _latestUpdateInfo.asStateFlow()

    suspend fun checkForUpdates(silent: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!silent) _updateState.value = UpdateState.Checking
        try {
            val url = URL(GITHUB_API_LATEST_RELEASE)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "CherishApp/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 10000
                readTimeout = 10000
            }

            if (conn.responseCode != 200) {
                if (!silent) _updateState.value = UpdateState.Error("Could not reach update server (${conn.responseCode})")
                return@withContext null
            }

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)

            val tagName = json.optString("tag_name", "") // e.g. "v1.1.1-12" or "v1.1.1"
            val releaseName = json.optString("name", tagName)
            val releaseNotes = json.optString("body", "Performance improvements and bug fixes.")
            val publishedAt = json.optString("published_at", "")

            // Parse version code from tag name (e.g. "v1.1.1-12" -> 12, or fallback to version numbers)
            val remoteVersionCode = parseVersionCode(tagName)
            val remoteVersionName = parseVersionName(tagName)

            var apkDownloadUrl = ""
            var apkSize = 0L

            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkDownloadUrl = asset.optString("browser_download_url", "")
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            val currentCode = BuildConfig.VERSION_CODE
            val isNewer = isSemanticVersionNewer(remoteVersionName, BuildConfig.VERSION_NAME) || 
                (remoteVersionName == BuildConfig.VERSION_NAME && remoteVersionCode > currentCode)

            val info = UpdateInfo(
                hasUpdate = isNewer && apkDownloadUrl.isNotBlank(),
                latestVersionName = remoteVersionName.ifBlank { releaseName },
                latestVersionCode = remoteVersionCode,
                releaseNotes = releaseNotes,
                downloadUrl = apkDownloadUrl,
                assetSize = apkSize,
                releaseDate = publishedAt
            )

            _latestUpdateInfo.value = info

            if (info.hasUpdate) {
                _updateState.value = UpdateState.UpdateAvailable(info)
            } else {
                if (!silent) _updateState.value = UpdateState.UpToDate
            }

            return@withContext info
        } catch (e: Exception) {
            if (!silent) _updateState.value = UpdateState.Error(e.message ?: "Failed to check for updates")
            return@withContext null
        }
    }

    suspend fun downloadAndInstallUpdate(context: Context, info: UpdateInfo) = withContext(Dispatchers.IO) {
        if (info.downloadUrl.isBlank()) {
            _updateState.value = UpdateState.Error("No download URL provided")
            return@withContext
        }

        try {
            val updatesDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
            val apkFile = File(updatesDir, "cherish_update_${info.latestVersionCode}.apk")

            val url = URL(info.downloadUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "CherishApp/${BuildConfig.VERSION_NAME}")
                connectTimeout = 15000
                readTimeout = 30000
            }

            // Handle redirect if needed
            var redirectConn = conn
            var responseCode = redirectConn.responseCode
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || 
                responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                responseCode == 307 || responseCode == 308) {
                val newUrl = redirectConn.getHeaderField("Location")
                redirectConn = (URL(newUrl).openConnection() as HttpURLConnection).apply {
                    setRequestProperty("User-Agent", "CherishApp/${BuildConfig.VERSION_NAME}")
                    connectTimeout = 15000
                    readTimeout = 30000
                }
            }

            val totalBytes = redirectConn.contentLengthLong.let { if (it <= 0) info.assetSize else it }
            var downloadedBytes = 0L

            redirectConn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var lastReportTime = System.currentTimeMillis()

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastReportTime > 150) { // report every 150ms
                            lastReportTime = now
                            val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes.toFloat() else 0f
                            _updateState.value = UpdateState.Downloading(progress.coerceIn(0f, 1f), downloadedBytes, totalBytes)
                        }
                    }
                }
            }

            _updateState.value = UpdateState.ReadyToInstall(apkFile)

            withContext(Dispatchers.Main) {
                installApk(context, apkFile)
            }

        } catch (e: Exception) {
            _updateState.value = UpdateState.Error(e.message ?: "Download failed")
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    Toast.makeText(context, "Please allow Cherish to install updates, then return to install", Toast.LENGTH_LONG).show()
                    return
                }
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun parseVersionCode(tag: String): Int {
        val clean = tag.removePrefix("v").removePrefix("V")
        if (clean.contains("-")) {
            val buildPart = clean.substringAfterLast("-")
            buildPart.toIntOrNull()?.let { return it }
        }
        return -1
    }

    private fun parseVersionName(tag: String): String {
        val clean = tag.removePrefix("v").removePrefix("V")
        return if (clean.contains("-")) clean.substringBefore("-") else clean
    }

    private fun isSemanticVersionNewer(remote: String, current: String): Boolean {
        val r = remote.split(".").mapNotNull { it.toIntOrNull() }
        val c = current.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(r.size, c.size)) {
            val rVal = r.getOrNull(i) ?: 0
            val cVal = c.getOrNull(i) ?: 0
            if (rVal > cVal) return true
            if (rVal < cVal) return false
        }
        return false
    }

    fun resetState() {
        _updateState.value = UpdateState.Idle
    }

    companion object {
        private const val GITHUB_API_LATEST_RELEASE = "https://api.github.com/repos/faisal9645/notes/releases/latest"

        @Volatile
        private var INSTANCE: OtaUpdateManager? = null

        fun getInstance(context: Context): OtaUpdateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OtaUpdateManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}