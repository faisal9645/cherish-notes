package com.example.backup

import com.example.data.model.Message
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Backups and the chat's offline copy are JSON through Moshi's generated adapters only (the app
 * doesn't ship kotlin-reflect). A class inside them without @JsonClass(generateAdapter = true)
 * would make Moshi throw on the phone, when saving or restoring a backup; here it fails instead.
 */
class BackupJsonTest {
    private val moshi = Moshi.Builder().build()

    @Test
    fun everyBackupClassHasAGeneratedAdapter() {
        // An adapter is built with the adapters of everything inside it
        moshi.adapter(FullAppBackupPayload::class.java)
        moshi.adapter<List<Message>>(Types.newParameterizedType(List::class.java, Message::class.java))
    }

    @Test
    fun aBackupComesBackTheSame() {
        val payload = FullAppBackupPayload(
            manifest = BackupManifest(coupleId = "couple_test", totalMessages = 1),
            messages = listOf(
                Message(id = "m1", text = "hello", senderId = "user_a", receiverId = "user_b", timestamp = 5L)
            )
        )
        val adapter = moshi.adapter(FullAppBackupPayload::class.java)
        assertEquals(payload, adapter.fromJson(adapter.toJson(payload)))
    }
}
