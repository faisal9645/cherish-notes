package com.example.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.example.data.model.Message
import com.squareup.moshi.Moshi

/**
 * Every chat message this phone has had, in its private storage: it goes only when the app is
 * uninstalled (no backup takes it anywhere). Recover All shows older chat from here at once, so
 * it isn't read from Firestore again; Firestore is asked only for what the archive doesn't have.
 *
 * Per chat it knows a stretch of time it holds every message of ([completeRange]): older chat from
 * that stretch needs no download. The stretch grows as older pages come from Firestore and as new
 * days are caught up.
 */
class ChatArchive private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    /** Every message from [from] (inclusive) until [until] (exclusive) is in the archive. */
    data class Range(val from: Long, val until: Long)

    // Message has a generated adapter (@JsonClass), so no reflection is needed
    private val adapter = Moshi.Builder().build().adapter(Message::class.java)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE messages (" +
                "id TEXT PRIMARY KEY, " +
                "conversation TEXT NOT NULL, " +
                "timestamp INTEGER NOT NULL, " +
                "json TEXT NOT NULL)"
        )
        db.execSQL("CREATE INDEX messages_by_time ON messages (conversation, timestamp)")
        db.execSQL(
            "CREATE TABLE ranges (" +
                "conversation TEXT PRIMARY KEY, " +
                "complete_from INTEGER NOT NULL, " +
                "complete_until INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** Adds or updates [messages] (the newest copy of each wins). */
    fun save(conversationId: String, messages: Collection<Message>) {
        if (conversationId.isBlank() || messages.isEmpty()) return
        try {
            val db = writableDatabase
            db.beginTransaction()
            try {
                val insert = db.compileStatement(
                    "INSERT OR REPLACE INTO messages (id, conversation, timestamp, json) VALUES (?, ?, ?, ?)"
                )
                for (message in messages) {
                    if (message.id.isBlank()) continue
                    insert.clearBindings()
                    insert.bindString(1, message.id)
                    insert.bindString(2, conversationId)
                    insert.bindLong(3, message.timestamp)
                    insert.bindString(4, adapter.toJson(message))
                    insert.executeInsert()
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't save ${messages.size} messages", e)
        }
    }

    /** Deleted messages leave the archive too. */
    fun remove(ids: Collection<String>) {
        if (ids.isEmpty()) return
        try {
            val db = writableDatabase
            ids.chunked(SQL_ARGS_PER_STATEMENT).forEach { chunk ->
                val marks = chunk.joinToString(",") { "?" }
                db.delete("messages", "id IN ($marks)", chunk.toTypedArray())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't remove ${ids.size} messages", e)
        }
    }

    /** Up to [limit] messages from before [before], newest first. */
    fun before(conversationId: String, before: Long, limit: Int): List<Message> = query(
        "SELECT json FROM messages WHERE conversation = ? AND timestamp < $before " +
            "ORDER BY timestamp DESC LIMIT ${limit.coerceAtLeast(1)}",
        conversationId
    )

    /** Every message from [from] (inclusive) until [until] (exclusive), newest first. */
    fun between(conversationId: String, from: Long, until: Long): List<Message> = query(
        "SELECT json FROM messages WHERE conversation = ? AND timestamp >= $from AND timestamp < $until " +
            "ORDER BY timestamp DESC",
        conversationId
    )

    /** The stretch of time the archive holds every message of, for this chat (null: none yet). */
    fun completeRange(conversationId: String): Range? = try {
        readableDatabase.rawQuery(
            "SELECT complete_from, complete_until FROM ranges WHERE conversation = ?",
            arrayOf(conversationId)
        ).use { cursor ->
            if (cursor.moveToFirst()) Range(cursor.getLong(0), cursor.getLong(1)) else null
        }
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't read the complete range", e)
        null
    }

    fun setCompleteRange(conversationId: String, range: Range) {
        if (conversationId.isBlank()) return
        try {
            val values = ContentValues().apply {
                put("conversation", conversationId)
                put("complete_from", range.from)
                put("complete_until", range.until)
            }
            writableDatabase.insertWithOnConflict("ranges", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't save the complete range", e)
        }
    }

    private fun query(sql: String, conversationId: String): List<Message> = try {
        readableDatabase.rawQuery(sql, arrayOf(conversationId)).use { cursor ->
            val out = ArrayList<Message>(cursor.count)
            while (cursor.moveToNext()) {
                try {
                    adapter.fromJson(cursor.getString(0))?.let(out::add)
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping an unreadable archived message", e)
                }
            }
            out
        }
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't read the archive", e)
        emptyList()
    }

    companion object {
        private const val TAG = "ChatArchive"
        private const val DATABASE_NAME = "cherish_chat_archive.db"
        private const val DATABASE_VERSION = 1
        // SQLite's limit on "?" in one statement is 999
        private const val SQL_ARGS_PER_STATEMENT = 500

        @Volatile
        private var instance: ChatArchive? = null

        fun get(context: Context): ChatArchive = instance ?: synchronized(this) {
            instance ?: ChatArchive(context.applicationContext).also { instance = it }
        }
    }
}
