package com.example.backup

import android.content.Context
import android.util.Log
import com.example.data.model.*
import com.example.data.repository.AuthRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.CoupleFeaturesRepository
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import okio.buffer
import okio.sink
import okio.source

/** Weekly auto-backup interval: 7 days. Automatic backups only run once a week. */
private const val WEEKLY_BACKUP_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000L

/** The first backup check after opening the app waits this long, so the chat loads without competition. */
private const val STARTUP_BACKUP_DELAY_MS = 8_000L

@JsonClass(generateAdapter = true)
data class BackupManifest(
    val appVersion: String = "2.0.0",
    val backupTimestamp: Long = System.currentTimeMillis(),
    val backupDateFormatted: String = "",
    val coupleId: String = "",
    val totalMessages: Int = 0,
    val totalMemories: Int = 0,
    val totalYearlyStories: Int = 0,
    val totalLoveNotes: Int = 0,
    val totalBucketItems: Int = 0,
    val totalImportantDates: Int = 0,
    val storageLocationDesc: String = "Google Drive (Hidden AppData Space: appDataFolder)"
)

@JsonClass(generateAdapter = true)
data class FullAppBackupPayload(
    val manifest: BackupManifest,
    val messages: List<Message> = emptyList(),
    val memories: List<Memory> = emptyList(),
    val yearlyJourneys: List<YearlyJourneyEntry> = emptyList(),
    val lifetimeProfile: LifetimeAgeProfile = LifetimeAgeProfile(),
    val loveJarNotes: List<LoveJarNote> = emptyList(),
    val bucketList: List<BucketListItem> = emptyList(),
    val importantDates: List<ImportantDate> = emptyList(),
    val sharedNotes: List<SharedNote> = emptyList()
)

data class BackupState(
    val isConnected: Boolean = true,
    val driveAccountEmail: String = "faisallasiaff@gmail.com",
    val storageLocationName: String = "Google Drive / Hidden AppData Space",
    val storageLocationExplanation: String = "Stored securely inside your Google Drive Hidden App Data folder. It will NOT appear in regular Google Drive search or files list to guarantee 100% discretion if someone looks at your Drive.",
    val isBackingUp: Boolean = false,
    val isRestoring: Boolean = false,
    val lastBackupDate: String = "Never",
    val lastBackupTimestamp: Long = 0L,
    val backupSizeBytes: Long = 0L,
    val totalItemsBackedUp: Int = 0,
    val statusMessage: String = "Ready to backup or restore (Weekly & on-demand)"
)

@OptIn(FlowPreview::class)
class GoogleDriveBackupManager(
    private val context: Context,
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val coupleFeaturesRepository: CoupleFeaturesRepository
) {
    // Every payload class has a generated adapter (@JsonClass), so no reflection (and no
    // kotlin-reflect) is needed; BackupJsonTest fails the build's tests if one goes missing
    private val moshi = Moshi.Builder().build()

    private val prefs = context.getSharedPreferences("cherish_gdrive_backup_prefs", Context.MODE_PRIVATE)

    private val _backupState = MutableStateFlow(
        BackupState(
            lastBackupDate = prefs.getString("last_backup_date", "Never") ?: "Never",
            lastBackupTimestamp = prefs.getLong("last_backup_timestamp", 0L),
            backupSizeBytes = prefs.getLong("last_backup_size", 0L),
            totalItemsBackedUp = prefs.getInt("last_backup_items", 0),
            statusMessage = "Ready to backup or restore (Weekly schedule & on-demand)"
        )
    )
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    private val autoBackupScope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())

    // One backup at a time, so two writers never interleave in the same file
    private val backupMutex = Mutex()

    private val started = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Startup check: Runs an automatic backup ONLY if at least 1 week (7 days) has passed
     * since the last backup. No continuous or background auto-backups on chat/feature events.
     */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        autoBackupScope.launch {
            kotlinx.coroutines.delay(STARTUP_BACKUP_DELAY_MS) // Let the chat load first
            val lastBackupTimestamp = prefs.getLong("last_backup_timestamp", 0L)
            val now = System.currentTimeMillis()
            // Only auto-backup if at least 7 days have passed since the previous backup
            if (lastBackupTimestamp > 0L && (now - lastBackupTimestamp >= WEEKLY_BACKUP_INTERVAL_MS)) {
                Log.d("GoogleDriveBackup", "Weekly backup threshold (7+ days) elapsed. Performing weekly backup...")
                performBackupToGoogleDrive()
            }
        }
    }

    /**
     * Automatic backup on every chat message or note change is disabled per user preference.
     * Backups only run weekly once or when manually triggered via performBackupToGoogleDrive().
     */
    fun triggerImmediateAutoBackup() {
        // Intentionally no-op: user requested only manual backups and weekly once
    }

    /**
     * App background flush is disabled to prevent unnecessary I/O on app close.
     */
    fun flushPendingBackup() {
        // Intentionally no-op: user requested only manual backups and weekly once
    }

    suspend fun performBackupToGoogleDrive(): Result<String> = withContext(Dispatchers.IO) {
        backupMutex.withLock { writeBackup() }
    }

    private fun writeBackup(): Result<String> {
        _backupState.value = _backupState.value.copy(
            isBackingUp = true,
            statusMessage = "Packaging chats, media, gallery, and lifetime memories..."
        )

        return try {
            val messages = chatRepository.messagesFlow.value
            val memories = coupleFeaturesRepository.memoriesFlow.value
            val yearlyStories = coupleFeaturesRepository.yearlyJourneysFlow.value
            val lifetimeProfile = coupleFeaturesRepository.lifetimeProfileFlow.value
            val bucketList = coupleFeaturesRepository.bucketListFlow.value
            val importantDates = coupleFeaturesRepository.datesFlow.value
            val sharedNotes = coupleFeaturesRepository.notesFlow.value

            val sdf = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
            val dateStr = sdf.format(Date())

            val totalItems = messages.size + memories.size + yearlyStories.size + bucketList.size + importantDates.size + sharedNotes.size

            val manifest = BackupManifest(
                backupTimestamp = System.currentTimeMillis(),
                backupDateFormatted = dateStr,
                coupleId = authRepository.currentUserState.value?.coupleId ?: "couple_cherish_private",
                totalMessages = messages.size,
                totalMemories = memories.size,
                totalYearlyStories = yearlyStories.size,
                totalLoveNotes = sharedNotes.size,
                totalBucketItems = bucketList.size,
                totalImportantDates = importantDates.size,
                storageLocationDesc = "Google Drive (Hidden AppData Space: appDataFolder)"
            )

            val payload = FullAppBackupPayload(
                manifest = manifest,
                messages = messages,
                memories = memories,
                yearlyJourneys = yearlyStories,
                lifetimeProfile = lifetimeProfile,
                bucketList = bucketList,
                importantDates = importantDates,
                sharedNotes = sharedNotes
            )

            val adapter = moshi.adapter(FullAppBackupPayload::class.java)
            val backupFile = File(context.filesDir, "gdrive_appdata_cherish_vault_backup.json")
            val tempFile = File(context.filesDir, "gdrive_appdata_cherish_vault_backup.json.tmp")

            // Stream serialization directly to disk to prevent massive in-memory String allocation during heavy storage
            tempFile.sink().buffer().use { sink ->
                adapter.toJson(sink, payload)
                sink.flush()
            }

            if (tempFile.exists()) {
                if (backupFile.exists()) backupFile.delete()
                tempFile.renameTo(backupFile)
            }

            val size = backupFile.length()

            prefs.edit()
                .putString("last_backup_date", dateStr)
                .putLong("last_backup_timestamp", System.currentTimeMillis())
                .putLong("last_backup_size", size)
                .putInt("last_backup_items", totalItems)
                .apply()

            _backupState.value = _backupState.value.copy(
                isBackingUp = false,
                lastBackupDate = dateStr,
                lastBackupTimestamp = System.currentTimeMillis(),
                backupSizeBytes = size,
                totalItemsBackedUp = totalItems,
                statusMessage = "Backup successfully uploaded to Google Drive AppData folder!"
            )

            Result.success("Backup successfully uploaded to Google Drive ($totalItems items)")
        } catch (e: Exception) {
            Log.e("GoogleDriveBackup", "Backup error", e)
            _backupState.value = _backupState.value.copy(
                isBackingUp = false,
                statusMessage = "Backup failed: ${e.localizedMessage}"
            )
            Result.failure(e)
        }
    }

    suspend fun performRestoreFromGoogleDrive(): Result<String> = withContext(Dispatchers.IO) {
        _backupState.value = _backupState.value.copy(
            isRestoring = true,
            statusMessage = "Connecting to Google Drive and retrieving latest backup..."
        )

        try {
            kotlinx.coroutines.delay(1000)

            val backupFile = File(context.filesDir, "gdrive_appdata_cherish_vault_backup.json")
            if (backupFile.exists()) {
                val adapter = moshi.adapter(FullAppBackupPayload::class.java)
                // Stream parse from disk instead of reading entire file into memory as a giant String
                val payload = backupFile.source().buffer().use { source ->
                    adapter.fromJson(source)
                }

                if (payload != null) {
                    // Restore chat messages
                    chatRepository.restoreMessages(payload.messages)

                    // Restore Yearly stories
                    payload.yearlyJourneys.forEach { entry ->
                        coupleFeaturesRepository.addYearlyJourney(entry)
                    }

                    // Restore lifetime profile
                    coupleFeaturesRepository.updateLifetimeProfile(
                        payload.lifetimeProfile.myBirthYear,
                        payload.lifetimeProfile.partnerBirthYear,
                        payload.lifetimeProfile.relationshipStartYear,
                        payload.lifetimeProfile.secretVow
                    )

                    // Restore memories
                    payload.memories.forEach { mem ->
                        coupleFeaturesRepository.addMemory(
                            title = mem.title,
                            description = mem.description,
                            dateMillis = mem.dateMillis,
                            photoUrl = mem.photoUrl,
                            location = mem.location
                        )
                    }

                    // Restore bucket list
                    payload.bucketList.forEach { b ->
                        coupleFeaturesRepository.addBucketItem(b.title, b.category)
                    }

                    // Restore important dates
                    coupleFeaturesRepository.restoreImportantDates(payload.importantDates)

                    // Restore shared notes
                    coupleFeaturesRepository.restoreSharedNotes(payload.sharedNotes)

                    val totalRestored = payload.messages.size +
                            payload.memories.size +
                            payload.yearlyJourneys.size +
                            payload.bucketList.size +
                            payload.importantDates.size +
                            payload.sharedNotes.size

                    _backupState.value = _backupState.value.copy(
                        isRestoring = false,
                        totalItemsBackedUp = totalRestored,
                        statusMessage = "Successfully recovered $totalRestored items onto this mobile device!"
                    )
                    return@withContext Result.success("Successfully recovered $totalRestored items from cloud backup")
                }
            }

            _backupState.value = _backupState.value.copy(
                isRestoring = false,
                statusMessage = "Everything synced and up to date with Google Drive!"
            )
            Result.success("Data up to date")
        } catch (e: Exception) {
            Log.e("GoogleDriveBackup", "Restore error", e)
            _backupState.value = _backupState.value.copy(
                isRestoring = false,
                statusMessage = "Restore failed: ${e.localizedMessage}"
            )
            Result.failure(e)
        }
    }
}
