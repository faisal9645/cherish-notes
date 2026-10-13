package com.example.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    private val prefs by lazy { appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    /** True while the system installer or the "install unknown apps" setting is open. */
    @Volatile
    private var awaitingReturn = false

    init {
        cleanupDownloadedInstallers(forceAll = false)
    }

    suspend fun checkForUpdates(silent: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        if (!silent) _updateState.value = UpdateState.Checking
        try {
            // Tier 1: Static version.json via raw.githubusercontent.com (zero API rate limits on mobile carriers)
            var info = fetchFromVersionJson()

            // Tier 2: GitHub Releases API (checked if Tier 1 failed or didn't report an update)
            if (info == null || !info.hasUpdate) {
                val apiInfo = fetchFromApi()
                if (apiInfo != null && (info == null || apiInfo.hasUpdate)) {
                    info = apiInfo
                }
            }

            // Tier 3: GitHub Web Releases 302 redirect fallback (checked if still no update found)
            if (info == null || !info.hasUpdate) {
                val webInfo = fetchFromWebRedirect()
                if (webInfo != null && (info == null || webInfo.hasUpdate)) {
                    info = webInfo
                }
            }

            if (info != null) {
                _latestUpdateInfo.value = info
                if (info.hasUpdate) {
                    _updateState.value = UpdateState.UpdateAvailable(info)
                } else {
                    cleanupDownloadedInstallers(forceAll = false)
                    if (!silent) _updateState.value = UpdateState.UpToDate
                }
                return@withContext info
            } else {
                if (!silent) _updateState.value = UpdateState.Error("Could not reach update server. Please check your internet connection.")
                return@withContext null
            }
        } catch (e: Exception) {
            if (!silent) _updateState.value = UpdateState.Error(sanitizeError(e.message))
            return@withContext null
        }
    }

    private fun fetchFromVersionJson(): UpdateInfo? {
        return try {
            val url = URL("$RAW_VERSION_JSON_URL?t=${System.currentTimeMillis()}")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "CherishApp/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")
                connectTimeout = 8000
                readTimeout = 8000
            }

            if (conn.responseCode != 200) return null

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)

            val remoteVersionCode = json.optInt("versionCode", -1)
            val remoteVersionName = json.optString("versionName", "")
            val downloadUrl = json.optString("downloadUrl", "")
            val releaseNotes = json.optString("releaseNotes", "Performance improvements and bug fixes.")
            val assetSize = json.optLong("assetSize", 0L)
            val releaseDate = json.optString("releaseDate", "")

            val currentCode = BuildConfig.VERSION_CODE
            val isNewer = if (remoteVersionCode > 0) {
                remoteVersionCode > currentCode
            } else {
                isSemanticVersionNewer(remoteVersionName, BuildConfig.VERSION_NAME)
            }

            if (downloadUrl.isBlank()) return null

            UpdateInfo(
                hasUpdate = isNewer,
                latestVersionName = remoteVersionName.ifBlank { "v$remoteVersionName" },
                latestVersionCode = remoteVersionCode,
                releaseNotes = releaseNotes,
                downloadUrl = downloadUrl,
                assetSize = assetSize,
                releaseDate = releaseDate
            )
        } catch (e: Exception) {
            android.util.Log.w("OtaUpdateManager", "fetchFromVersionJson failed: ${e.message}")
            null
        }
    }

    private fun fetchFromApi(): UpdateInfo? {
        return try {
            val url = URL(GITHUB_API_LATEST_RELEASE)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "CherishApp/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 10000
                readTimeout = 10000
            }

            if (conn.responseCode != 200) {
                android.util.Log.w("OtaUpdateManager", "GitHub API returned HTTP ${conn.responseCode}")
                return null
            }

            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)

            val tagName = json.optString("tag_name", "") // e.g. "v1.1.1-12" or "v1.1.1"
            val releaseName = json.optString("name", tagName)
            val releaseNotes = json.optString("body", "Performance improvements and bug fixes.")
            val publishedAt = json.optString("published_at", "")

            // Parse version code from tag name (e.g. "v1.1.1-12" -> 12, or fallback to version numbers)
            var remoteVersionCode = parseVersionCode(tagName)
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
                        
                        // Fallback to extracting version code from APK name if tag didn't have it
                        if (remoteVersionCode <= 0) {
                            val match = Regex("(\\d+)\\.apk", RegexOption.IGNORE_CASE).find(name)
                            if (match != null) {
                                remoteVersionCode = match.groupValues[1].toIntOrNull() ?: remoteVersionCode
                            }
                        }
                        break
                    }
                }
            }

            if (apkDownloadUrl.isBlank() && tagName.isNotBlank()) {
                apkDownloadUrl = "https://github.com/faisal9645/cherish-notes/releases/download/$tagName/cherish-notes-$tagName.apk"
            }

            val currentCode = BuildConfig.VERSION_CODE
            val isNewer = if (remoteVersionCode > 0) {
                remoteVersionCode > currentCode
            } else {
                isSemanticVersionNewer(remoteVersionName, BuildConfig.VERSION_NAME)
            }

            UpdateInfo(
                hasUpdate = isNewer && apkDownloadUrl.isNotBlank(),
                latestVersionName = remoteVersionName.ifBlank { releaseName },
                latestVersionCode = remoteVersionCode,
                releaseNotes = releaseNotes,
                downloadUrl = apkDownloadUrl,
                assetSize = apkSize,
                releaseDate = publishedAt
            )
        } catch (e: Exception) {
            android.util.Log.w("OtaUpdateManager", "fetchFromApi failed: ${e.message}")
            null
        }
    }

    private fun fetchFromWebRedirect(): UpdateInfo? {
        return try {
            var targetUrl = GITHUB_WEB_LATEST_RELEASE
            var resolvedTag = ""
            for (redirectHop in 0 until 5) {
                val conn = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36")
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                val code = conn.responseCode
                val location = conn.getHeaderField("Location")
                if (code in arrayOf(301, 302, 303, 307, 308) && !location.isNullOrBlank()) {
                    targetUrl = location
                    if (location.contains("/releases/tag/")) {
                        resolvedTag = location.substringAfterLast("/releases/tag/").substringBefore("/").substringBefore("?").trim()
                        break
                    }
                } else {
                    if (targetUrl.contains("/releases/tag/")) {
                        resolvedTag = targetUrl.substringAfterLast("/releases/tag/").substringBefore("/").substringBefore("?").trim()
                    }
                    break
                }
            }

            if (resolvedTag.isBlank()) return null

            val remoteVersionCode = parseVersionCode(resolvedTag)
            val remoteVersionName = parseVersionName(resolvedTag)

            val currentCode = BuildConfig.VERSION_CODE
            val isNewer = if (remoteVersionCode > 0) {
                remoteVersionCode > currentCode
            } else {
                isSemanticVersionNewer(remoteVersionName, BuildConfig.VERSION_NAME)
            }

            val apkDownloadUrl = "https://github.com/faisal9645/cherish-notes/releases/download/$resolvedTag/cherish-notes-$resolvedTag.apk"

            UpdateInfo(
                hasUpdate = isNewer,
                latestVersionName = remoteVersionName.ifBlank { resolvedTag },
                latestVersionCode = remoteVersionCode,
                releaseNotes = "New Cherish update available with instant OTA delivery and performance improvements.",
                downloadUrl = apkDownloadUrl,
                assetSize = 0L,
                releaseDate = ""
            )
        } catch (e: Exception) {
            android.util.Log.w("OtaUpdateManager", "Web fallback check failed: ${e.message}")
            null
        }
    }

    private fun sanitizeError(raw: String?): String {
        if (raw.isNullOrBlank()) return "Network connection interrupted. Please try again."
        val lower = raw.lowercase()
        return when {
            lower.contains("ssl") || lower.contains("decrypt") || lower.contains("cipher") ||
            lower.contains("handshake") || lower.contains("record_mac") || lower.contains("protocol error") ||
            lower.contains("bad_record") || lower.contains("bad_decrypt") ->
                "Secure connection interrupted by network. Tap Download & Install to retry."
            lower.contains("timeout") || lower.contains("timed out") ->
                "Download timed out. Please check your connection and retry."
            lower.contains("connection reset") || lower.contains("broken pipe") || lower.contains("eof") ->
                "Connection lost during download. Tap Download & Install to retry."
            lower.contains("incomplete") || lower.contains("corrupted") ->
                "Downloaded file was incomplete. Tap Download & Install to retry."
            lower.contains("code 404") || lower.contains("404") ->
                "Update package not found on server. Please try again later."
            lower.contains("no space") || lower.contains("enospc") ->
                "Not enough storage space to download update."
            else -> raw
        }
    }

    private fun openConnectionWithHeaders(urlString: String): HttpURLConnection {
        return (URL(urlString).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "CherishApp/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Connection", "close")
            setRequestProperty("Accept-Encoding", "identity")
            connectTimeout = 20000
            readTimeout = 45000
        }
    }

    private fun resolveDownloadConnection(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        var conn = openConnectionWithHeaders(currentUrl)
        var responseCode = conn.responseCode
        var redirectCount = 0

        while (responseCode in arrayOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, 307, 308) && redirectCount < 5) {
            val newUrl = conn.getHeaderField("Location") ?: break
            conn.disconnect()
            currentUrl = newUrl
            conn = openConnectionWithHeaders(currentUrl)
            responseCode = conn.responseCode
            redirectCount++
        }
        return conn
    }

    suspend fun downloadAndInstallUpdate(context: Context, info: UpdateInfo) = withContext(Dispatchers.IO) {
        if (info.downloadUrl.isBlank()) {
            _updateState.value = UpdateState.Error("No download URL provided")
            return@withContext
        }

        val updatesDir = File(appContext.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, "cherish_update_${info.latestVersionCode}.apk")
        val partFile = File(updatesDir, "cherish_update_${info.latestVersionCode}.apk.part")

        // Already downloaded, e.g. before the app restarted to get install permission
        if (apkFile.exists()) {
            val archive = appContext.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            if (archive != null && archive.packageName == appContext.packageName) {
                if (info.assetSize <= 0L || apkFile.length() == info.assetSize) {
                    _updateState.value = UpdateState.ReadyToInstall(apkFile)
                    withContext(Dispatchers.Main) { installApk(context, apkFile) }
                    return@withContext
                }
            }
            apkFile.delete()
        }

        var lastException: Exception? = null
        val maxAttempts = 3

        for (attempt in 1..maxAttempts) {
            var activeConn: HttpURLConnection? = null
            try {
                if (partFile.exists()) partFile.delete()

                var conn = resolveDownloadConnection(info.downloadUrl)
                var responseCode = conn.responseCode

                // If 404 on cherish-notes-{tag}.apk, fallback to app-release.apk
                if (responseCode == 404 && info.downloadUrl.contains("cherish-notes-")) {
                    conn.disconnect()
                    val altUrl = info.downloadUrl.substringBeforeLast("/") + "/app-release.apk"
                    conn = resolveDownloadConnection(altUrl)
                    responseCode = conn.responseCode
                }

                if (responseCode !in 200..299) {
                    conn.disconnect()
                    throw java.io.IOException("Server returned HTTP $responseCode")
                }

                activeConn = conn
                val totalBytes = conn.contentLengthLong.let { if (it <= 0) info.assetSize else it }
                var downloadedBytes = 0L

                conn.inputStream.use { input ->
                    FileOutputStream(partFile).use { output ->
                        val buffer = ByteArray(32 * 1024)
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
                        output.flush()
                    }
                }
                activeConn.disconnect()
                activeConn = null

                // Verify package archive integrity
                val archive = appContext.packageManager.getPackageArchiveInfo(partFile.absolutePath, 0)
                if (archive == null || archive.packageName != appContext.packageName) {
                    partFile.delete()
                    throw java.io.IOException("Downloaded update is incomplete. Please try again.")
                }

                // Promote verified partFile to final apkFile
                if (apkFile.exists()) apkFile.delete()
                if (!partFile.renameTo(apkFile)) {
                    partFile.copyTo(apkFile, overwrite = true)
                    partFile.delete()
                }

                _updateState.value = UpdateState.ReadyToInstall(apkFile)

                withContext(Dispatchers.Main) {
                    installApk(context, apkFile)
                }
                return@withContext

            } catch (e: Exception) {
                lastException = e
                android.util.Log.w("OtaUpdateManager", "Download attempt $attempt of $maxAttempts failed: ${e.message}")
                try { activeConn?.disconnect() } catch (_: Exception) {}
                if (partFile.exists()) partFile.delete()

                if (attempt < maxAttempts) {
                    delay(1200L * attempt)
                }
            }
        }

        val friendlyError = sanitizeError(lastException?.message)
        _updateState.value = UpdateState.Error(friendlyError)
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    // Allowing installs restarts the app on many phones (which reopens on Notes);
                    // remembered, so the install carries on when the app is back (onAppResumed)
                    rememberPendingInstall(apkFile)
                    val prefs = com.example.security.SecurityPreferences.getInstance(context)
                    prefs.ignoreNextPause = true
                    prefs.isExternalPickerActive = true
                    awaitingReturn = true
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
            val prefs = com.example.security.SecurityPreferences.getInstance(context)
            prefs.ignoreNextPause = true
            prefs.isExternalPickerActive = true
            awaitingReturn = true
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to launch installer: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Called each time the app comes back to the front. Ends an installer/settings trip, so
     * leaving the app afterwards hides it behind Notes again, and carries on an install that was
     * waiting for the "install unknown apps" permission.
     */
    fun onAppResumed(activity: Activity) {
        if (awaitingReturn) {
            awaitingReturn = false
            com.example.security.SecurityPreferences.getInstance(activity).isExternalPickerActive = false
        }
        cleanupDownloadedInstallers(forceAll = false)
        val path = prefs.getString(KEY_PENDING_APK, null) ?: return
        val since = prefs.getLong(KEY_PENDING_SINCE, 0L)
        prefs.edit().remove(KEY_PENDING_APK).remove(KEY_PENDING_SINCE).apply()
        if (System.currentTimeMillis() - since > PENDING_INSTALL_WINDOW_MS) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) return
        val apk = File(path)
        if (apk.exists()) {
            if (isNewerThanInstalled(activity, apk)) {
                installApk(activity, apk)
            } else {
                apk.delete()
            }
        }
    }

    /**
     * Automatically removes downloaded installer APKs and temporary files once the app has
     * updated or when obsolete, keeping device storage lean and clean.
     */
    fun cleanupDownloadedInstallers(forceAll: Boolean = false) {
        try {
            val updateDirs = listOfNotNull(
                File(appContext.cacheDir, "updates"),
                appContext.externalCacheDir?.let { File(it, "updates") }
            )

            for (dir in updateDirs) {
                if (!dir.exists()) continue
                val files = dir.listFiles() ?: continue
                for (file in files) {
                    if (forceAll) {
                        file.delete()
                    } else {
                        val shouldDelete = try {
                            val archive = appContext.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                            if (archive != null) {
                                val archiveCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    archive.longVersionCode
                                } else {
                                    @Suppress("DEPRECATION")
                                    archive.versionCode.toLong()
                                }
                                archiveCode <= BuildConfig.VERSION_CODE
                            } else {
                                // Incomplete or corrupt file older than 30 minutes
                                System.currentTimeMillis() - file.lastModified() > 30 * 60 * 1000L
                            }
                        } catch (_: Exception) {
                            true
                        }
                        if (shouldDelete) {
                            file.delete()
                        }
                    }
                }
            }

            val pendingPath = prefs.getString(KEY_PENDING_APK, null)
            if (!pendingPath.isNullOrBlank()) {
                val pendingFile = File(pendingPath)
                if (pendingFile.exists()) {
                    if (forceAll || !isNewerThanInstalled(appContext, pendingFile)) {
                        pendingFile.delete()
                        prefs.edit().remove(KEY_PENDING_APK).remove(KEY_PENDING_SINCE).apply()
                    }
                } else {
                    prefs.edit().remove(KEY_PENDING_APK).remove(KEY_PENDING_SINCE).apply()
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("OtaUpdateManager", "Failed to cleanup downloaded installers", e)
        }
    }

    private fun rememberPendingInstall(apkFile: File) {
        prefs.edit()
            .putString(KEY_PENDING_APK, apkFile.absolutePath)
            .putLong(KEY_PENDING_SINCE, System.currentTimeMillis())
            .apply()
    }

    @Suppress("DEPRECATION")
    private fun isNewerThanInstalled(context: Context, apk: File): Boolean {
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, 0) ?: return false
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) archive.longVersionCode else archive.versionCode.toLong()
        return archive.packageName == context.packageName && versionCode > BuildConfig.VERSION_CODE
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
        private const val RAW_VERSION_JSON_URL = "https://raw.githubusercontent.com/faisal9645/cherish-notes/main/version.json"
        private const val GITHUB_API_LATEST_RELEASE = "https://api.github.com/repos/faisal9645/cherish-notes/releases/latest"
        private const val GITHUB_WEB_LATEST_RELEASE = "https://github.com/faisal9645/cherish-notes/releases/latest"
        private const val PREFS_NAME = "cherish_ota"
        private const val KEY_PENDING_APK = "pending_install_apk"
        private const val KEY_PENDING_SINCE = "pending_install_since"
        /** An install waiting for permission is only carried on within this time. */
        private const val PENDING_INSTALL_WINDOW_MS = 10 * 60 * 1000L

        @Volatile
        private var INSTANCE: OtaUpdateManager? = null

        fun getInstance(context: Context): OtaUpdateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OtaUpdateManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}