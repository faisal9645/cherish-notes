package com.example

import android.app.Application
import android.util.Log
import com.example.audio.VoicePlayerHelper
import com.example.audio.VoiceRecorderHelper
import com.example.data.local.notes.NotesRepository
import com.example.data.repository.AuthRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.CoupleFeaturesRepository
import com.example.data.repository.MediaRepository
import com.example.notifications.NotificationHelper
import com.example.security.SecurityPreferences
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class CherishApplication : Application(), coil.ImageLoaderFactory {

    override fun newImageLoader(): coil.ImageLoader {
        return coil.ImageLoader.Builder(this)
            .components {
                add(coil.decode.VideoFrameDecoder.Factory())
                // Animated GIFs move (the phone's own animated-image decoder from Android 9)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    add(coil.decode.ImageDecoderDecoder.Factory())
                } else {
                    add(coil.decode.GifDecoder.Factory())
                }
            }
            .crossfade(true)
            // Recent photos stay in memory; the disk keeps the rest. Room for a lot of them, so a
            // photo seen once isn't downloaded again (mobile data) when scrolling back or in the gallery
            .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            // A chat photo's address never changes, so it's kept on disk even when the server says
            // "don't cache": scrolling back through chat or gallery doesn't download it again
            .respectCacheHeaders(false)
            .build()
    }

    lateinit var securityPreferences: SecurityPreferences
        private set

    lateinit var notesRepository: NotesRepository
        private set

    lateinit var authRepository: AuthRepository
        private set

    lateinit var chatRepository: ChatRepository
        private set

    lateinit var mediaRepository: MediaRepository
        private set

    lateinit var coupleFeaturesRepository: CoupleFeaturesRepository
        private set

    /** Love Synchronicity moments; created on first use (Love & Us). */
    val synchronicityRepository by lazy {
        com.example.data.repository.SynchronicityRepository(this, authRepository)
    }

    lateinit var voiceRecorderHelper: VoiceRecorderHelper
        private set

    lateinit var voicePlayerHelper: VoicePlayerHelper
        private set

    lateinit var googleDriveBackupManager: com.example.backup.GoogleDriveBackupManager
        private set

    val pendingNoteIdFlow = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    /** When the app was last left (for the app lock); 0 while it's open. */
    @Volatile private var leftAppAt = 0L

    /**
     * When the app went to the background while something held Cherish open (a photo picker or the
     * camera from the chat, a call that just ended); 0 otherwise. Away from there for long, or the
     * phone locked meanwhile, it was leaving after all.
     */
    @Volatile private var heldOpenSince = 0L

    /** The phone locked while the app was away with Cherish held open: the app lock counts from then. */
    fun onScreenLockedWhileAway() {
        val since = heldOpenSince
        if (!isAppInForeground && since > 0L && leftAppAt == 0L) leftAppAt = since
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val stackTrace = Log.getStackTraceString(throwable)
            Log.e("CherishApp", "FATAL UNCAUGHT EXCEPTION on thread ${thread.name}: ${throwable.message}", throwable)
            try {
                val crashFile = java.io.File(filesDir, "last_crash.txt")
                crashFile.writeText("${java.util.Date()}\nThread: ${thread.name}\n${throwable.javaClass.name}: ${throwable.message}\n$stackTrace")
            } catch (_: Throwable) {}
            try {
                if (FirebaseApp.getApps(this).isNotEmpty()) {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    val crashData = mapOf(
                        "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        "time" to java.util.Date().toString(),
                        "thread" to thread.name,
                        "error" to "${throwable.javaClass.simpleName}: ${throwable.message}",
                        "stackTrace" to stackTrace.take(8000)
                    )
                    db.collection("app_config").document("last_crash").set(crashData)
                    Thread.sleep(1200)
                }
            } catch (_: Throwable) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                try {
                    FirebaseApp.initializeApp(this)
                } catch (_: Exception) {}
            }
            if (FirebaseApp.getApps(this).isEmpty()) {
                val rawKey = BuildConfig.FIREBASE_API_KEY
                val apiKey = if (rawKey.isBlank() || rawKey.startsWith("DEFAULT_") || rawKey == "YOUR_FIREBASE_API_KEY") {
                    "AIzaSyCYWCJQpzY9bZV9YFxzePy8ti9pL6Z1HQ4"
                } else rawKey
                val options = com.google.firebase.FirebaseOptions.Builder()
                    .setApplicationId("1:589800064404:android:48e44687a5abaab671bb81")
                    .setApiKey(apiKey)
                    .setProjectId("gen-lang-client-0340321202")
                    .setStorageBucket("gen-lang-client-0340321202.firebasestorage.app")
                    .setGcmSenderId("589800064404")
                    .build()
                FirebaseApp.initializeApp(this, options)
                Log.i("CherishApp", "FirebaseApp initialized with verified project options")
            }
        } catch (e: Exception) {
            Log.w("CherishApp", "FirebaseApp initialization: ${e.message}")
        }

        // Report previous crash if it exists
        try {
            val crashFile = java.io.File(filesDir, "last_crash.txt")
            if (crashFile.exists() && FirebaseApp.getApps(this).isNotEmpty()) {
                val report = crashFile.readText()
                if (report.isNotBlank()) {
                    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    db.collection("app_config").document("last_crash").set(
                        mapOf(
                            "timestamp" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                            "report" to report.take(8000)
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                }
            }
        } catch (_: Throwable) {}

        NotificationHelper.createNotificationChannels(this)

        securityPreferences = SecurityPreferences.getInstance(this)
        securityPreferences.ensurePreferencesMigrated(BuildConfig.VERSION_CODE)

        // First run after an install (an uninstall wipes prefs, so a clean reinstall looks fresh):
        // record it as "reinstalled now" and clear the leftover installer from Downloads. The
        // partner is told once the user is signed in again (MainActivity.onResume).
        try {
            if (securityPreferences.consumeFreshInstallFlag()) {
                securityPreferences.setLastReinstallAt(System.currentTimeMillis())
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    com.example.update.ReinstallHelper.cleanupStaleInstallers(this@CherishApplication)
                }
            }
        } catch (_: Throwable) {}

        // Safety rescue: Ensure MainActivity was never disabled by an older build
        try {
            val pm = packageManager
            val mainComp = android.content.ComponentName(this, "com.example.MainActivity")
            val currentState = pm.getComponentEnabledSetting(mainComp)
            if (currentState == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                pm.setComponentEnabledSetting(
                    mainComp,
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP
                )
            }
        } catch (_: Throwable) {}

        // Reconcile launcher icon asynchronously without interrupting cold start
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            securityPreferences.syncLauncherIcon()
        }
        notesRepository = NotesRepository.getInstance(this)
        authRepository = AuthRepository(this)
        chatRepository = ChatRepository(this, authRepository)
        mediaRepository = MediaRepository(this)
        coupleFeaturesRepository = CoupleFeaturesRepository(this, authRepository)
        voiceRecorderHelper = VoiceRecorderHelper(this)
        voicePlayerHelper = VoicePlayerHelper(this)
        googleDriveBackupManager = com.example.backup.GoogleDriveBackupManager(
            this,
            authRepository,
            chatRepository,
            coupleFeaturesRepository
        )

        // Voice calls: ring while the app runs, even before (or without) a push
        try {
            com.example.call.CallManager.init(this)
            com.example.call.CallManager.watchForIncomingCalls()
        } catch (e: Throwable) {
            Log.w("CherishApp", "CallManager init/watch error: ${e.message}")
        }

        // Stop any background sync service if running previously (push notifications work on-demand via FCM)
        try {
            com.example.sync.CherishSyncService.stop(this)
        } catch (e: Throwable) {
            Log.w("CherishApp", "CherishSyncService stop error: ${e.message}")
        }

        // Stagger non-critical disk & sync operations off the main thread so first frame paints instantly
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            kotlinx.coroutines.delay(1_200L)
            try {
                com.example.notifications.PartnerPush.resumePending(this@CherishApplication)
            } catch (e: Throwable) {
                Log.w("CherishApp", "PartnerPush resume error: ${e.message}")
            }
            try {
                com.example.notifications.NoteReminderScheduler.rescheduleAllUpcomingReminders(this@CherishApplication, notesRepository)
            } catch (e: Throwable) {
                Log.w("CherishApp", "NoteReminderScheduler error: ${e.message}")
            }
            try {
                com.example.update.OtaUpdateManager.getInstance(this@CherishApplication).cleanupDownloadedInstallers()
            } catch (e: Throwable) {
                Log.w("CherishApp", "OtaUpdateManager cleanup error: ${e.message}")
            }
        }

        // Background cache maintenance: clean stale temporary uploads, installers, and enforce LRU limits
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            kotlinx.coroutines.delay(15_000L) // Wait 15s after startup before performing maintenance
            com.example.util.StorageOptimizer.pruneStaleCaches(this@CherishApplication)
        }

        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                    // Leaving for a photo picker, the camera or a call doesn't count (a full-screen
                    // video or the Watch Party is inside the app: leaving from there does)
                    val leftForReal = !securityPreferences.ignoreNextPause &&
                        !securityPreferences.isExternalPickerActive &&
                        !securityPreferences.wasInCallRecently() &&
                        !com.example.call.CallManager.hasActiveCall() &&
                        !com.example.call.CallManager.isCallScreenVisible
                    securityPreferences.ignoreChatNavigation = false
                    if (securityPreferences.isDisguiseModeEnabled() && leftForReal) {
                        securityPreferences.reDisguise()
                    }
                    val now = android.os.SystemClock.elapsedRealtime()
                    leftAppAt = if (leftForReal) now else 0L
                    heldOpenSince = if (leftForReal) 0L else now
                    if (leftForReal) {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            com.example.util.StorageOptimizer.pruneStaleCaches(this@CherishApplication)
                        }
                    }
                } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                    // Back after a long trip out from a picker, the camera or a call that had ended
                    // (going home from there): it was leaving, so Cherish is hidden and locked as such
                    val heldSince = heldOpenSince
                    heldOpenSince = 0L
                    if (heldSince > 0L &&
                        android.os.SystemClock.elapsedRealtime() - heldSince >= HELD_OPEN_TRIP_MAX_MS &&
                        !com.example.call.CallManager.hasActiveCall() &&
                        !com.example.call.CallManager.isCallScreenVisible
                    ) {
                        if (securityPreferences.isDisguiseModeEnabled()) securityPreferences.forceDisguise()
                        if (leftAppAt == 0L) leftAppAt = heldSince
                    }
                    // App lock: asks for the PIN again once away longer than the chosen time
                    val left = leftAppAt
                    leftAppAt = 0L
                    val lockAfterSeconds = securityPreferences.getAutoLockTimeoutSeconds()
                    if (left > 0L && lockAfterSeconds >= 0 &&
                        android.os.SystemClock.elapsedRealtime() - left >= lockAfterSeconds * 1000L
                    ) {
                        securityPreferences.lockApp()
                    }
                }
            }
        )

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private val resumedCount = java.util.concurrent.atomic.AtomicInteger(0)

            override fun onActivityResumed(activity: android.app.Activity) {
                val count = resumedCount.incrementAndGet()
                isAppInForeground = count > 0
                chatRepository.ensureGlobalMessagesListener()
                authRepository.ensureCoupleRoomListener()
                if (activity !is com.example.call.CallActivity) {
                    com.example.notifications.NotificationHelper.clearNotifications(activity)
                }
                if (!securityPreferences.isDisguiseActive()) {
                    chatRepository.markPendingDelivered()
                }
                com.example.notifications.ThinkingOfYou.checkPendingHeartbeatFelt(activity)
            }

            override fun onActivityPaused(activity: android.app.Activity) {
                val count = resumedCount.updateAndGet { c -> if (c > 0) c - 1 else 0 }
                isAppInForeground = count > 0
            }

            override fun onActivityStarted(activity: android.app.Activity) {
                chatRepository.ensureGlobalMessagesListener()
                authRepository.ensureCoupleRoomListener()
                if (activity !is com.example.call.CallActivity) {
                    com.example.notifications.NotificationHelper.clearNotifications(activity)
                }
            }
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            // Trim Coil image memory cache
            val imageLoader = coil.Coil.imageLoader(this)
            if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
                imageLoader.memoryCache?.clear()
            } else if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
                imageLoader.memoryCache?.trimMemory(level)
            }
            // Trim video thumbnail cache
            com.example.util.VideoThumbnailHelper.trimMemory(level)
            // Trim time formatter cache
            com.example.util.ChatTimeFormatter.clearCache()
        } catch (_: Exception) {}
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            coil.Coil.imageLoader(this).memoryCache?.clear()
            com.example.util.VideoThumbnailHelper.clearMemoryCache()
            com.example.util.ChatTimeFormatter.clearCache()
        } catch (_: Exception) {}
    }

    companion object {
        /** Longer than this out on a picker, the camera or after a call, and it counts as having left. */
        private const val HELD_OPEN_TRIP_MAX_MS = 2L * 60 * 1000

        lateinit var instance: CherishApplication
            private set

        @Volatile
        var isAppInForeground: Boolean = false
            private set
    }
}
