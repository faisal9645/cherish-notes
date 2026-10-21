package com.example.share

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.CherishApplication
import com.example.data.model.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Something shared into the app from another app. While the app is Notes it's saved as a note and
 * waits here, so nothing reaches the partner unless it's sent from inside Cherish (see
 * PendingSharesBar): someone else sharing to "Notes" only ever makes a note.
 */
data class PendingShare(
    val id: String,
    /** "text", "image", "video", "audio" or "file". */
    val kind: String,
    /** The shared text, or a photo's caption. */
    val text: String,
    /** The prepared copy (app-private), for media and files. */
    val filePath: String? = null,
    val fileName: String? = null,
    /** The note it was saved as in Notes, removed once it's sent. */
    val noteId: String? = null
)

object PendingShares {
    private const val TAG = "PendingShares"
    private const val PREFS = "pending_shares"
    private const val KEY_ITEMS = "items"

    private val _items = MutableStateFlow<List<PendingShare>>(emptyList())
    val items: StateFlow<List<PendingShare>> = _items.asStateFlow()

    @Volatile private var loaded = false

    /** Sending runs on after the share screen or the chat has closed. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun dir(context: Context): File = File(context.filesDir, "pending_shares").apply { mkdirs() }

    fun load(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_ITEMS, null)
            _items.value = runCatching { parse(raw) }.getOrDefault(emptyList())
                // A file that has gone (storage cleared) can't be sent any more
                .filter { it.filePath == null || File(it.filePath).exists() }
            loaded = true
        }
    }

    fun add(context: Context, shares: List<PendingShare>) {
        if (shares.isEmpty()) return
        load(context)
        synchronized(this) {
            _items.value = _items.value + shares
            save(context)
        }
    }

    /** Drops [ids] from the list (their copies too); the notes they were saved as stay in Notes. */
    fun remove(context: Context, ids: Set<String>) {
        load(context)
        synchronized(this) {
            val (gone, kept) = _items.value.partition { it.id in ids }
            gone.forEach { share -> share.filePath?.let { runCatching { File(it).delete() } } }
            _items.value = kept
            save(context)
        }
    }

    /**
     * Sends [shares] to the partner in order, each one taken off the list (and its stand-in note
     * out of Notes) once it's gone. Ones that fail stay for another try. [done] gets how many went.
     */
    fun send(app: CherishApplication, shares: List<PendingShare>, done: (sent: Int, failed: Int) -> Unit = { _, _ -> }) {
        scope.launch {
            var sent = 0
            var failed = 0
            for (share in shares) {
                val ok = runCatching { sendOne(app, share) }.onFailure {
                    Log.w(TAG, "Couldn't send a shared item", it)
                }.getOrDefault(false)
                if (ok) {
                    sent++
                    remove(app, setOf(share.id))
                    share.noteId?.let { noteId -> runCatching { app.notesRepository.deleteNote(noteId) } }
                } else {
                    failed++
                }
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) { done(sent, failed) }
        }
    }

    private suspend fun sendOne(app: CherishApplication, share: PendingShare): Boolean {
        if (share.kind == "text") {
            if (share.text.isBlank()) return true
            return app.chatRepository.sendMessage(share.text.trim()).isSuccess
        }
        val path = share.filePath ?: return true
        val file = File(path)
        if (!file.exists()) return true
        val type = when (share.kind) {
            "image" -> MessageType.IMAGE
            "video" -> MessageType.VIDEO
            "audio" -> MessageType.AUDIO
            else -> MessageType.DOCUMENT
        }
        val coupleId = app.chatRepository.getConversationId()
        val url = app.mediaRepository.uploadFile(file, type, coupleId).getOrNull() ?: return false
        val defaultText = when (type) {
            MessageType.IMAGE -> "Sent a photo"
            MessageType.VIDEO -> "Sent a video"
            MessageType.AUDIO -> "Voice note"
            else -> share.fileName ?: file.name
        }
        return app.chatRepository.sendMessage(
            text = share.text.ifBlank { defaultText },
            type = type,
            mediaUrl = url,
            mediaName = share.fileName ?: file.name,
            mediaSize = file.length()
        ).isSuccess
    }

    private fun save(context: Context) {
        val array = JSONArray()
        _items.value.forEach { share ->
            array.put(
                JSONObject()
                    .put("id", share.id)
                    .put("kind", share.kind)
                    .put("text", share.text)
                    .put("file", share.filePath ?: "")
                    .put("name", share.fileName ?: "")
                    .put("note", share.noteId ?: "")
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun parse(raw: String?): List<PendingShare> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = JSONArray(raw)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            PendingShare(
                id = o.getString("id"),
                kind = o.optString("kind", "file"),
                text = o.optString("text", ""),
                filePath = o.optString("file", "").ifBlank { null },
                fileName = o.optString("name", "").ifBlank { null },
                noteId = o.optString("note", "").ifBlank { null }
            )
        }
    }

    /** A file the share screen prepared (in the cache) moved in with the pending ones, so it's kept. */
    fun keep(context: Context, prepared: File, id: String): File {
        val target = File(dir(context), "${id}_${prepared.name}")
        if (prepared.renameTo(target)) return target
        prepared.copyTo(target, overwrite = true)
        runCatching { prepared.delete() }
        return target
    }

    /** The name an app gives a shared file, if it gives one. */
    fun displayName(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (index != -1) name = cursor.getString(index)
                    }
                }
            }
        }
        return name ?: uri.path?.substringAfterLast('/')
    }
}
