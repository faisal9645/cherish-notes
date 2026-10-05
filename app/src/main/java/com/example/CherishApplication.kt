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

class CherishApplication : Application() {

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

    lateinit var voiceRecorderHelper: VoiceRecorderHelper
        private set

    lateinit var voicePlayerHelper: VoicePlayerHelper
        private set

    lateinit var googleDriveBackupManager: com.example.backup.GoogleDriveBackupManager
        private set

    val pendingNoteIdFlow = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

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
                val apiKey = BuildConfig.FIREBASE_API_KEY.ifEmpty { "AIzaSyPlaceholderForFirebaseInit" }
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
    }

    companion object {
        lateinit var instance: CherishApplication
            private set
    }
}
