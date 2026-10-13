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
        if (message.id in loading) return
        val callId = message.callId?.takeIf { it.matches(CALL_ID) }
        val coupleId = message.conversationId.ifBlank { app.chatRepository.getConversationId() }
        if (callId == null || coupleId.isBlank()) {
            setItem(message.id, Item(Status.UNAVAILABLE, note = "This recording isn't available"))
            return
        }
        loading += message.id
        setItem(message.id, Item(Status.LOADING))
        scope.launch {
            try {
                val parts = fetch(app, coupleId, callId)
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
        scope.launch(Dispatchers.IO) {
            callIds.filter { it.matches(CALL_ID) }.forEach { File(root, it).deleteRecursively() }
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
    private suspend fun fetch(app: CherishApplication, coupleId: String, callId: String): List<File> {
        val repository = CallRepository(app)
        repository.ensureMember(coupleId)
        // The server's own record decides whether it can be played (a chat message alone doesn't)
        val record = repository.getCall(coupleId, callId)
        when (record?.recordingStatus) {
            null, RecordingStatus.READY -> Unit
            RecordingStatus.DELETED -> throw Unavailable("This recording was deleted")
            RecordingStatus.FAILED -> throw Unavailable("This call wasn't recorded")
            else -> throw Unavailable("This recording is still being saved")
        }
        val prefix = "couples/$coupleId/call-recordings/$callId/"
        val paths = record?.recordingPaths?.filter { it.startsWith(prefix) }?.ifEmpty { null }
            ?: listOf("${prefix}recording.mp4")
        if (FirebaseApp.getApps(app).isEmpty()) throw IllegalStateException("Firebase isn't set up")
        val storage = FirebaseStorage.getInstance()

        return withContext(Dispatchers.IO) {
            val dir = File(File(app.cacheDir, CACHE_DIR), callId).apply { mkdirs() }
            val parts = paths.mapIndexed { index, path ->
                val target = File(dir, "part_$index.mp4")
                if (!target.exists() || target.length() == 0L) {
                    val partial = File(dir, "part_$index.mp4.download")
                    try {
                        storage.reference.child(path).getFile(partial).await()
                    } catch (e: StorageException) {
                        partial.delete()
                        when (e.errorCode) {
                            StorageException.ERROR_OBJECT_NOT_FOUND -> throw Unavailable("This recording isn't available anymore")
                            StorageException.ERROR_NOT_AUTHORIZED -> throw Unavailable("This phone can't open this recording")
                            else -> throw e
                        }
                    }
                    if (!partial.renameTo(target)) {
                        partial.copyTo(target, overwrite = true)
                        partial.delete()
                    }
                }
                target.setLastModified(System.currentTimeMillis())
                target
            }
            // Most recently played last out of the cache
            dir.setLastModified(System.currentTimeMillis())
            pruneCache(File(app.cacheDir, CACHE_DIR), keep = dir)
            parts
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
    private val CALL_ID = Regex("^[A-Za-z0-9]{16,40}$")
}
