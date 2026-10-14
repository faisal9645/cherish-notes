package com.example.call

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.CherishApplication
import com.example.audio.VoicePlayerHelper
import com.example.data.model.Message
import com.google.firebase.FirebaseApp
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Plays call recordings, in the chat and the Shared Gallery. A recording is fetched through the
 * signed-in Firebase connection (the Storage rules check it's this couple's phone) into the app's
 * private cache, and played with the voice note player. No download link is ever made or stored.
 */
object CallRecordingPlayer {
    private const val TAG = "CallRecordingPlayer"
    private const val CACHE_DIR = "call_recordings"
    private const val MAX_CACHE_BYTES = 300L * 1024 * 1024

    enum class Status { IDLE, LOADING, READY, UNAVAILABLE, FAILED }

    /** One recording's loading state; [part] of [parts] for recordings over ~3 hours. */
    data class Item(
        val status: Status = Status.IDLE,
        val part: Int = 0,
        val parts: Int = 1,
        val note: String? = null
    )

    private class Unavailable(message: String) : Exception(message)

    private val _items = MutableStateFlow<Map<String, Item>>(emptyMap())
    val items: StateFlow<Map<String, Item>> = _items.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val loading = mutableSetOf<String>()
    /** The downloaded parts of each recording (by message id), to pause, resume and play on. */
    private val files = mutableMapOf<String, List<File>>()

    /** Plays [message]'s recording, or pauses/resumes it if it's the one playing. */
    fun toggle(context: Context, message: Message) {
        val app = context.applicationContext as? CherishApplication ?: return
        if (VoicePlayerHelper.callInProgress) {
            Toast.makeText(app, "You can listen once the call ends", Toast.LENGTH_SHORT).show()
            return
        }
        val player = app.voicePlayerHelper
        val known = files[message.id]
        if (player.currentTrackId.value == message.id && known != null) {
            val part = _items.value[message.id]?.part ?: 0
            player.playAudio(message.id, known.getOrElse(part) { known.first() }.absolutePath)
            return
        }
        if (known != null && known.isNotEmpty() && known.all { it.exists() && it.length() > 0 }) {
            setItem(message.id, Item(Status.READY, part = 0, parts = known.size))
            playPart(app, message.id, known, 0)
            return
        }
        val rawCallId = message.callId?.trim()?.takeIf { it.isNotBlank() }
            ?: message.recordingPath?.substringAfter("call-recordings/")?.substringBefore("/")?.takeIf { it.isNotBlank() }
            ?: message.id.removePrefix(MESSAGE_PREFIX).takeIf { it.isNotBlank() }
        val callId = rawCallId?.filter { it.isLetterOrDigit() || it == '-' || it == '_' }?.takeIf { it.length in 4..128 }
        if (callId != null) {
            val dir = File(File(app.cacheDir, CACHE_DIR), callId)
            val recDir = File(app.cacheDir, "callrec")
            val candidateFiles = listOf(
                File(dir, "part_0.mp4"),
                File(dir, "part_0.aac"),
                File(dir, "part_0.wav"),
                File(dir, "recording.mp4"),
                File(dir, "recording.aac"),
                File(dir, "recording.wav"),
                File(recDir, "rec_${callId}.aac"),
                File(recDir, "rec_${callId}.wav")
            )
            val localFile = candidateFiles.firstOrNull { it.exists() && it.length() > 0 }
            if (localFile != null) {
                val parts = listOf(localFile)
                files[message.id] = parts
                setItem(message.id, Item(Status.READY, part = 0, parts = 1))
                playPart(app, message.id, parts, 0)
                return
            }
        }
        if (message.id in loading) return
        val coupleId = message.conversationId.ifBlank { app.chatRepository.getConversationId() }
        if (callId == null || coupleId.isBlank()) {
            setItem(message.id, Item(Status.UNAVAILABLE, note = "This recording isn't available"))
            return
        }
        loading += message.id
        setItem(message.id, Item(Status.LOADING))
        scope.launch {
            try {
                val parts = fetch(app, coupleId, callId, message)
                files[message.id] = parts
                setItem(message.id, Item(Status.READY, part = 0, parts = parts.size))
                playPart(app, message.id, parts, 0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Unavailable) {
                setItem(message.id, Item(Status.UNAVAILABLE, note = e.message))
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't load recording $callId", e)
                setItem(message.id, Item(Status.FAILED, note = "Couldn't load the recording. Tap to try again."))
            } finally {
                loading -= message.id
            }
        }
    }

    /** Recordings deleted on this phone: their audio leaves the cache too. */
    fun forget(context: Context, messageIds: Collection<String>) {
        val callIds = messageIds.filter { it.startsWith(MESSAGE_PREFIX) }.map { it.removePrefix(MESSAGE_PREFIX) }
        if (callIds.isEmpty()) return
        messageIds.forEach { id ->
            files.remove(id)
            _items.update { it - id }
        }
        val root = File(context.applicationContext.cacheDir, CACHE_DIR)
        val recDir = File(context.applicationContext.cacheDir, "callrec")
        scope.launch(Dispatchers.IO) {
            callIds.forEach { id ->
                val safeId = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
                if (safeId.isNotBlank()) {
                    File(root, safeId).deleteRecursively()
                    File(recDir, "rec_${safeId}.aac").delete()
                    File(recDir, "rec_${safeId}.wav").delete()
                }
            }
        }
    }

    private fun playPart(app: CherishApplication, messageId: String, parts: List<File>, index: Int) {
        if (index >= parts.size) return
        _items.update { items -> items + (messageId to (items[messageId] ?: Item()).copy(part = index, parts = parts.size)) }
        app.voicePlayerHelper.playAudio(messageId, parts[index].absolutePath) {
            // A long call comes in parts: carry on with the next one
            if (index + 1 < parts.size) playPart(app, messageId, parts, index + 1)
        }
    }

    /** The recording's parts on this phone, fetched once the server says it's ready. */
    private suspend fun fetch(app: CherishApplication, coupleId: String, callId: String, message: Message): List<File> {
        val repository = CallRepository(app)
        try {
            repository.ensureMember(coupleId)
        } catch (_: Exception) {}

        val record = try {
            repository.getCall(coupleId, callId)
        } catch (_: Exception) {
            null
        }

        if (record?.recordingStatus == RecordingStatus.DELETED) {
            throw Unavailable("This recording was deleted")
        }

        val prefix = "couples/$coupleId/call-recordings/$callId/"
        val candidatePaths = mutableListOf<String>()
        record?.recordingPaths?.filter { it.startsWith(prefix) }?.let { candidatePaths.addAll(it) }
        message.recordingPaths.filter { it.startsWith(prefix) }.let { candidatePaths.addAll(it) }
        message.recordingPath?.takeIf { it.startsWith(prefix) }?.let { candidatePaths.add(it) }
        candidatePaths.add("${prefix}recording.aac")
        candidatePaths.add("${prefix}recording.mp4")
        candidatePaths.add("${prefix}recording.wav")
        val uniquePaths = candidatePaths.distinct()

        if (FirebaseApp.getApps(app).isEmpty()) throw IllegalStateException("Firebase isn't set up")
        val storage = FirebaseStorage.getInstance()

        return withContext(Dispatchers.IO) {
            val dir = File(File(app.cacheDir, CACHE_DIR), callId).apply { mkdirs() }
            val existing = listOf("part_0.mp4", "part_0.aac", "part_0.wav", "recording.mp4", "recording.aac")
                .map { File(dir, it) }
                .firstOrNull { it.exists() && it.length() > 0L }
            if (existing != null) {
                return@withContext listOf(existing)
            }

            var downloadedFile: File? = null
            var lastStorageException: StorageException? = null

            for (path in uniquePaths) {
                val ext = path.substringAfterLast('.', "mp4")
                val destFile = File(dir, "part_0.$ext")
                val partial = File(dir, "part_0.$ext.download")
                try {
                    storage.reference.child(path).getFile(partial).await()
                    if (partial.exists() && partial.length() > 0L) {
                        if (!partial.renameTo(destFile)) {
                            partial.copyTo(destFile, overwrite = true)
                            partial.delete()
                        }
                        val targetMp4 = File(dir, "part_0.mp4")
                        if (destFile != targetMp4 && (!targetMp4.exists() || targetMp4.length() == 0L)) {
                            try {
                                destFile.copyTo(targetMp4, overwrite = true)
                            } catch (_: Exception) {}
                        }
                        downloadedFile = destFile
                        break
                    }
                } catch (e: StorageException) {
                    partial.delete()
                    lastStorageException = e
                    if (e.errorCode == StorageException.ERROR_NOT_AUTHORIZED) {
                        throw Unavailable("This phone can't open this recording")
                    }
                } catch (e: Exception) {
                    partial.delete()
                }
            }

            if (downloadedFile == null) {
                if (record?.recordingStatus == RecordingStatus.FAILED) {
                    throw Unavailable("This call wasn't recorded")
                }
                if (record?.recordingStatus == RecordingStatus.RECORDING) {
                    throw Unavailable("This recording is still being saved")
                }
                if (lastStorageException?.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) {
                    throw Unavailable("This recording isn't available anymore")
                }
                throw Unavailable("This recording isn't available")
            }

            downloadedFile.setLastModified(System.currentTimeMillis())
            dir.setLastModified(System.currentTimeMillis())
            pruneCache(File(app.cacheDir, CACHE_DIR), keep = dir)
            listOf(downloadedFile)
        }
    }

    /** Keeps the cache of recordings under its limit, least recently played first out. */
    private fun pruneCache(root: File, keep: File) {
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: return
        var total = dirs.sumOf { dir -> dir.listFiles()?.sumOf { it.length() } ?: 0L }
        for (dir in dirs.sortedBy { it.lastModified() }) {
            if (total <= MAX_CACHE_BYTES) break
            if (dir == keep) continue
            total -= dir.listFiles()?.sumOf { it.length() } ?: 0L
            dir.deleteRecursively()
        }
    }

    private fun setItem(messageId: String, item: Item) {
        _items.update { it + (messageId to item) }
    }

    private const val MESSAGE_PREFIX = "callrec_"
    private val CALL_ID = Regex("^[A-Za-z0-9_-]{4,128}$")
}
