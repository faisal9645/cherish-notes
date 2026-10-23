package com.example.share

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.example.CherishApplication
import com.example.data.local.notes.NoteEntity
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * "Share to Notes" from another app. It has no screen of its own (the app shared from stays in
 * view) and never opens the app, so sharing never starts a second copy of it inside someone
 * else's app.
 *
 * Shared items (photos, videos, files, YouTube links) are immediately sent directly to the
 * partner in the background without requiring manual confirmation inside Cherish.
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Rebuilt or brought back from Recents: this share was handled already
        if (savedInstanceState != null || (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            finish()
            return
        }
        val app = application as CherishApplication
        val share = intent
        lifecycleScope.launch {
            // The shared files are read now, while this screen holds the permission to read them
            val items = try {
                prepare(app, share)
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't take in the share", e)
                emptyList()
            }
            if (items.isEmpty()) {
                Toast.makeText(app, "Couldn't share", Toast.LENGTH_SHORT).show()
                ShareRotationHelper.rotateToNextAlias(app)
                finish()
                return@launch
            }
            PendingShares.add(app, items)
            PendingShares.send(app, items) { sent, failed ->
                if (failed > 0) {
                    Toast.makeText(app, "Couldn't send everything. It's waiting in the chat.", Toast.LENGTH_SHORT).show()
                } else if (sent > 0) {
                    Toast.makeText(app, "Sent ✨", Toast.LENGTH_SHORT).show()
                }
            }
            Toast.makeText(app, "Sending…", Toast.LENGTH_SHORT).show()
            ShareRotationHelper.rotateToNextAlias(app)
            finish()
        }
    }

    /** What was shared, ready to keep: text, or each file copied in (photos made small). */
    private suspend fun prepare(app: CherishApplication, intent: Intent): List<PendingShare> {
        val action = intent.action
        if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return emptyList()

        val uris = mutableListOf<Uri>()
        intent.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i)?.uri?.let { if (it !in uris) uris.add(it) }
            }
        }
        if (action == Intent.ACTION_SEND) {
            val single = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            } ?: intent.data
            if (single != null && single !in uris) uris.add(single)
        } else {
            val many = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            }
            many?.forEach { if (it !in uris) uris.add(it) }
        }

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() }

        if (uris.isEmpty()) {
            val text = sharedText ?: return emptyList()
            // A YouTube share goes as its link (for watching together)
            val toSend = if (text.contains("youtube.com") || text.contains("youtu.be")) {
                Regex("""https?://\S+""").find(text)?.value ?: text
            } else text
            return listOf(PendingShare(id = UUID.randomUUID().toString(), kind = "text", text = toSend.trim()))
        }

        val caption = if (uris.size == 1) sharedText?.takeIf { !it.startsWith("http") } else null
        return uris.mapNotNull { uri ->
            try {
                val mime = runCatching { contentResolver.getType(uri) }.getOrNull() ?: intent.type ?: "application/octet-stream"
                val kind = when {
                    mime.startsWith("image/") -> "image"
                    mime.startsWith("video/") -> "video"
                    mime.startsWith("audio/") -> "audio"
                    else -> "file"
                }
                val name = PendingShares.displayName(this, uri)
                val prepared = if (kind == "image") {
                    app.mediaRepository.compressAndPrepareImage(uri)
                } else {
                    app.mediaRepository.copyToCache(uri, name)
                }
                val id = UUID.randomUUID().toString()
                val kept = PendingShares.keep(this, prepared, id)
                PendingShare(id = id, kind = kind, text = caption.orEmpty(), filePath = kept.absolutePath, fileName = name ?: kept.name)
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't take in $uri", e)
                null
            }
        }
    }

    /** The share as a plain note, so Notes shows what was "saved". */
    private suspend fun saveAsNote(app: CherishApplication, item: PendingShare, intent: Intent): String? {
        return try {
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim()?.takeIf { it.isNotBlank() }
            val (title, content) = when (item.kind) {
                "text" -> {
                    val firstLine = item.text.lineSequence().firstOrNull()?.trim().orEmpty()
                    (subject ?: firstLine.take(40).ifBlank { "Shared note" }) to item.text
                }
                else -> {
                    val label = when (item.kind) {
                        "image" -> "Shared photo"
                        "video" -> "Shared video"
                        "audio" -> "Shared audio"
                        else -> "Shared file"
                    }
                    (subject ?: item.text.takeIf { it.isNotBlank() }?.take(40) ?: label) to
                        listOfNotNull(item.text.takeIf { it.isNotBlank() }, "📎 ${item.fileName ?: label}").joinToString("\n")
                }
            }
            val note = NoteEntity(title = title, content = content, category = "Personal")
            app.notesRepository.insertNote(note)
            note.id
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't save the share as a note", e)
            null
        }
    }

    private companion object {
        const val TAG = "ShareReceiver"
    }
}
