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

    override fun onCreate() {
        super.onCreate()
        instance = this

        try {
            FirebaseApp.initializeApp(this)
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
    }

    companion object {
        lateinit var instance: CherishApplication
            private set
    }
}
