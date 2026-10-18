package com.example.call

import android.net.Uri
import android.util.Log
import com.example.CherishApplication
import com.example.data.model.Message
import com.example.data.model.MessageStatus
import com.example.data.model.MessageType
import com.example.util.formatRecordingLength
import com.example.util.MediaCompressor
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Collections

/**
 * Saves a call's recording once the call is over: makes it small (AAC instead of the raw WAV the
 * call engine writes, about a tenth of the size), adds it to the chat and the Calls tab, and
 * uploads it for the partner. What it needs is written down next to the recording first, so a
 * recording whose upload was cut short (the app was closed, no network) is finished the next
 * time the app starts.
 */
internal object CallRecordingUploader {
    private const val TAG = "CallRecordingUploader"
    /** Where the call engine records, and where unfinished uploads wait. */
    private const val RECORD_DIR = "callrec"
    /** This phone's own copies, played without downloading (see CallRecordingPlayer). */
    private const val PLAY_DIR = "call_recordings"
    private const val MIN_BYTES = 128L
    private const val MAX_ATTEMPTS = 5
    private const val GIVE_UP_AFTER_MS = 3L * 24 * 60 * 60 * 1000
    /** Unfinished uploads wait this long after the app starts, so they don't slow it down. */
    private const val RESUME_DELAY_MS = 3_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = Collections.synchronizedSet(mutableSetOf<String>())

    /** What's needed to save one call's recording, also after the app restarts. */
    data class Job(
        val coupleId: String,
        val callId: String,
        val userId: String,
        val partnerId: String,
        val senderName: String,
        val durationSec: Int,
        val endedAt: Long,
        val sourcePath: String,
        val attempts: Int = 0,
        val createdAt: Long = System.currentTimeMillis(),
        /** Measured from the WAV before it was made small (it can't be measured after). */
        val waveform: List<Double> = emptyList()
    ) {
        fun toJson(): String = JSONObject()
            .put("waveform", waveform.joinToString(","))
            .put("coupleId", coupleId)
            .put("callId", callId)
            .put("userId", userId)
            .put("partnerId", partnerId)
            .put("senderName", senderName)
            .put("durationSec", durationSec)
            .put("endedAt", endedAt)
            .put("sourcePath", sourcePath)
            .put("attempts", attempts)
            .put("createdAt", createdAt)
            .toString()

        companion object {
            fun fromJson(text: String): Job? = try {
                val json = JSONObject(text)
                Job(
                    coupleId = json.getString("coupleId"),
                    callId = json.getString("callId"),
                    userId = json.getString("userId"),
                    partnerId = json.optString("partnerId"),
                    senderName = json.optString("senderName"),
                    durationSec = json.optInt("durationSec", 1),
                    endedAt = json.optLong("endedAt", System.currentTimeMillis()),
                    sourcePath = json.getString("sourcePath"),
                    attempts = json.optInt("attempts", 0),
                    createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                    waveform = json.optString("waveform").split(",").mapNotNull { it.trim().toDoubleOrNull() }
                ).takeIf { it.coupleId.isNotBlank() && it.callId.isNotBlank() && it.userId.isNotBlank() }
            } catch (e: Exception) {
                null
            }
        }
    }

    /** Saves [job]'s recording; [onNoRecording] runs when the call left no usable recording after all. */
    fun save(app: CherishApplication, job: Job, onNoRecording: () -> Unit) {
        writeJob(app, job)
        start(app, job, onNoRecording)
    }

    /** Finishes recordings whose saving was cut short before (once the app has settled in). */
    fun resumePending(app: CherishApplication) {
        scope.launch {
            delay(RESUME_DELAY_MS)
            val pending = File(app.cacheDir, RECORD_DIR).listFiles { file -> file.name.endsWith(".json") }.orEmpty()
            for (file in pending) {
                val job = try { Job.fromJson(file.readText()) } catch (_: Exception) { null }
                if (job == null) {
                    file.delete()
                    continue
                }
                start(app, job, onNoRecording = {})
            }
        }
    }

    private fun start(app: CherishApplication, job: Job, onNoRecording: () -> Unit) {
        if (!running.add(job.callId)) return
        scope.launch {
            try {
                run(app, job, onNoRecording)
            } catch (e: Throwable) {
                // Errors too: a background save must never take the whole app down with it (a
                // broken class here once crashed the app at the end of every call)
                Log.w(TAG, "Saving the recording of ${job.callId} failed", e)
            } finally {
                running.remove(job.callId)
            }
        }
    }

    private suspend fun run(app: CherishApplication, job: Job, onNoRecording: () -> Unit) {
        val fs = FirebaseFirestore.getInstance()
        val callDoc = fs.collection("couples").document(job.coupleId).collection("calls").document(job.callId)
        val wav = File(job.sourcePath)
        val parentDir = wav.parentFile ?: File(app.cacheDir, RECORD_DIR)
        val compressed = File(parentDir, "rec_${job.callId}.m4a")
        val playDir = File(File(app.cacheDir, PLAY_DIR), job.callId).apply { mkdirs() }

        // 1. Small: AAC (.m4a) instead of the WAV; the WAV itself if this phone can't encode it
        var waveform = job.waveform
        val localPlayCopy = listOf("part_0.m4a", "part_0.mp4", "part_0.aac", "part_0.wav")
            .map { File(playDir, it) }
            .firstOrNull { it.exists() && it.length() >= MIN_BYTES }

        val file: File = if (compressed.length() >= MIN_BYTES) {
            compressed
        } else if (localPlayCopy != null) {
            localPlayCopy
        } else {
            val source = awaitRecording(app, job, wav)
            if (source == null) {
                Log.w(TAG, "No usable recording for ${job.callId}")
                if (job.attempts > 0) markFailed(fs, callDoc, "recording_lost")
                finish(app, job, wav, compressed)
                onNoRecording()
                return
            }
            // Its waveform for the player, while it's still a WAV (kept with the job in case
            // the app closes before the message is written)
            if (waveform.isEmpty() && source.extension.equals("wav", ignoreCase = true)) {
                waveform = CallRecordingWave.fromWav(source)
                if (waveform.isNotEmpty()) writeJob(app, job.copy(waveform = waveform))
            }
            if (MediaCompressor.compressRecording(app, source, compressed)) {
                source.delete()
                compressed
            } else {
                source
            }
        }
        val ext = file.extension.lowercase().ifBlank { "wav" }
        val path = "couples/${job.coupleId}/call-recordings/${job.callId}/recording.$ext"

        // 2. Playable on this phone right away, from its own copy
        try {
            val copy = File(playDir, "part_0.$ext")
            if (copy.absolutePath != file.absolutePath && copy.length() != file.length()) {
                file.copyTo(copy, overwrite = true)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't keep a copy to play", e)
        }

        // Measure actual audio length if not known accurately
        var actualDurationSec = job.durationSec
        if (actualDurationSec <= 1) {
            val probed = try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                val durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                retriever.release()
                (durationMs / 1000L).toInt()
            } catch (_: Exception) { 0 }
            if (probed > 0) {
                actualDurationSec = probed
            } else if (ext == "wav" && file.length() > 44L) {
                // 32kHz 16-bit mono = 64000 bytes/sec
                val sec = ((file.length() - 44L) / 64000L).toInt()
                if (sec > 0) actualDurationSec = sec
            }
        }
        val effectiveDuration = actualDurationSec.coerceAtLeast(1)

        // 3. In the chat and the Calls tab: one message per call, whichever phone writes it
        val length = formatRecordingLength(effectiveDuration)
        val message = Message(
            id = "callrec_${job.callId}",
            conversationId = job.coupleId,
            senderId = job.userId,
            senderName = job.senderName,
            receiverId = job.partnerId,
            text = "Voice call ($length)",
            timestamp = job.endedAt,
            type = MessageType.CALL_RECORDING.name,
            mediaName = "recording.$ext",
            mediaSize = file.length(),
            durationSeconds = effectiveDuration,
            waveform = waveform,
            status = MessageStatus.SENT.name,
            callId = job.callId,
            recordingPath = path,
            recordingPaths = listOf(path)
        )
        withContext(Dispatchers.Main) { app.chatRepository.recordCallCompleted(message) }
        if (job.attempts == 0) {
            val conversation = fs.collection("conversations").document(job.coupleId)
            try {
                conversation.collection("messages").document(message.id).set(message).await()
                // The other phone's plain "Voice call" line for this call, if it wrote one first
                conversation.collection("messages").document(CallManager.callLogMessageId(job.callId)).delete()
                conversation.set(
                    mapOf(
                        "id" to job.coupleId,
                        "lastMessageText" to "📞 Voice call ($length)",
                        "lastMessageSenderId" to job.userId,
                        "lastMessageTimestamp" to job.endedAt
                    ),
                    SetOptions.merge()
                ).await()
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't add the call to the chat", e)
            }
        }

        // 4. Uploaded for both partner and cloud playback in all scenarios
        val uploaded = try {
            val metadata = StorageMetadata.Builder()
                .setContentType(if (ext == "m4a") "audio/mp4" else if (ext == "aac") "audio/aac" else "audio/wav")
                .build()
            FirebaseStorage.getInstance().reference.child(path).putFile(Uri.fromFile(file), metadata).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Recording upload failed (${job.callId}, attempt ${job.attempts + 1})", e)
            false
        }
        if (uploaded) {
            try {
                callDoc.set(
                    mapOf(
                        "recording" to mapOf(
                            "status" to RecordingStatus.READY.key,
                            "storagePaths" to FieldValue.arrayUnion(path),
                            "durationSec" to effectiveDuration,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        "durationSec" to effectiveDuration,
                        "state" to CallRecord.STATE_ENDED,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                ).await()
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't mark the recording ready", e)
            }
            Log.i(TAG, "Call recording saved: $path (${file.length() / 1024} KB)")
            finish(app, job, wav, compressed)
            return
        }

        // Tried again with exponential backoff while app is open, or on next app restart
        val next = job.copy(attempts = job.attempts + 1, waveform = waveform, durationSec = effectiveDuration)
        val tooOld = System.currentTimeMillis() - job.createdAt > GIVE_UP_AFTER_MS
        if (next.attempts < MAX_ATTEMPTS && !tooOld) {
            writeJob(app, next)
            scope.launch {
                delay(4_000L * next.attempts)
                start(app, next, onNoRecording = {})
            }
            return
        }
        markFailed(fs, callDoc, "upload_failed")
        finish(app, job, wav, compressed)
    }

    /** The recording file once the call engine has finished writing it; null when there's none. */
    private suspend fun awaitRecording(app: CherishApplication, job: Job, file: File): File? {
        val parent = file.parentFile ?: File(app.cacheDir, RECORD_DIR)
        val nameBase = file.nameWithoutExtension
        val playDir = File(File(app.cacheDir, PLAY_DIR), job.callId)
        val candidates = listOf(
            file,
            File(parent, "$nameBase.wav"),
            File(parent, "$nameBase.aac"),
            File(parent, "$nameBase.m4a"),
            File(playDir, "part_0.m4a"),
            File(playDir, "part_0.wav"),
            File(playDir, "part_0.aac")
        ).distinct()
        repeat(30) {
            candidates.firstOrNull { it.exists() && it.length() >= MIN_BYTES }?.let { return it }
            delay(200L)
        }
        return candidates.firstOrNull { it.exists() && it.length() > 0 }
    }

    /** The recording won't come: unless the other phone's copy already arrived. */
    private suspend fun markFailed(fs: FirebaseFirestore, callDoc: DocumentReference, code: String) {
        try {
            fs.runTransaction { tx ->
                val status = (tx.get(callDoc).get("recording") as? Map<*, *>)?.get("status") as? String
                if (status != RecordingStatus.READY.key) {
                    tx.set(
                        callDoc,
                        mapOf(
                            "recording" to mapOf(
                                "status" to RecordingStatus.FAILED.key,
                                "failureCode" to code,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        ),
                        SetOptions.merge()
                    )
                }
                true
            }.await()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't mark the recording failed", e)
        }
    }

    /** Done with [job]: its note and working files go (this phone's copy to play stays). */
    private fun finish(app: CherishApplication, job: Job, wav: File, compressed: File) {
        jobFile(app, job.callId).delete()
        wav.delete()
        compressed.delete()
    }

    private fun jobFile(app: CherishApplication, callId: String): File =
        File(File(app.cacheDir, RECORD_DIR).apply { mkdirs() }, "rec_$callId.json")

    private fun writeJob(app: CherishApplication, job: Job) {
        try {
            jobFile(app, job.callId).writeText(job.toJson())
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't note the recording to save", e)
        }
    }
}
