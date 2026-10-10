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

class CherishApplication : Application(), coil.ImageLoaderFactory {

    override fun newImageLoader(): coil.ImageLoader {
        return coil.ImageLoader.Builder(this)
            .components {
                add(coil.decode.VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            // Recent photos stay in memory; up to a few percent of the disk keeps the rest
            .memoryCache { coil.memory.MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.03)
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

    override fun onCreate() {
        super.onCreate()
        instance = this

        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                try {
                    FirebaseApp.initializeApp(this)
                } catch (_: Exception) {}
            }
            if (FirebaseApp.getApps(this).isEmpty()) {
                val rawKey = BuildConfig.FIREBASE_API_KEY
                val apiKey = if (rawKey.isBlank() || rawKey == "YOUR_FIREBASE_API_KEY") "AIzaSyPlaceholderForFirebaseInit" else rawKey
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

        NotificationHelper.createNotificationChannels(this)

        securityPreferences = SecurityPreferences.getInstance(this)
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

        com.example.notifications.NoteReminderScheduler.rescheduleAllUpcomingReminders(this, notesRepository)

        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                    // Leaving for a photo picker, the camera or a full-screen player doesn't count
                    val leftForReal = !securityPreferences.ignoreNextPause &&
                        !securityPreferences.ignoreChatNavigation &&
                        !securityPreferences.isExternalPickerActive &&
                        !securityPreferences.isMediaViewerActive &&
                        !securityPreferences.isTheaterModeActive
                    if (securityPreferences.isDisguiseModeEnabled() && leftForReal) {
                        securityPreferences.reDisguise()
                    }
                    leftAppAt = if (leftForReal) android.os.SystemClock.elapsedRealtime() else 0L
                } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
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
                isAppInForeground = resumedCount.incrementAndGet() > 0
            }

            override fun onActivityPaused(activity: android.app.Activity) {
                isAppInForeground = resumedCount.decrementAndGet() > 0
            }

            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {}
        })
    }

    companion object {
        lateinit var instance: CherishApplication
            private set

        @Volatile
        var isAppInForeground: Boolean = false
            private set
    }
}
