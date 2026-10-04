package com.example.data.local.notes

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.UUID

class NotesRepository(
    private val noteDao: NoteDao
) {
    val allNotes: Flow<List<NoteEntity>> = noteDao.getAllActiveNotes()

    suspend fun insertNote(note: NoteEntity) {
        noteDao.insertNote(note)
    }

    suspend fun updateNote(note: NoteEntity) {
        noteDao.updateNote(note.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun getNoteById(id: String): NoteEntity? = noteDao.getNoteById(id)

    suspend fun updateReminder(id: String, reminderTime: Long?) {
        noteDao.updateReminder(id, reminderTime)
    }

    suspend fun getUpcomingReminders(currentTime: Long = System.currentTimeMillis()): List<NoteEntity> =
        noteDao.getUpcomingReminders(currentTime)

    fun getUpcomingRemindersCount(currentTime: Long = System.currentTimeMillis()): Flow<Int> =
        noteDao.getUpcomingRemindersCount(currentTime)

    suspend fun deleteNote(id: String) {
        noteDao.deleteById(id)
    }

    suspend fun deleteNotes(ids: List<String>) {
        if (ids.isNotEmpty()) {
            noteDao.deleteNotesByIds(ids)
        }
    }

    suspend fun togglePin(id: String, currentPinned: Boolean) {
        noteDao.setPinned(id, !currentPinned)
    }

    suspend fun updateChecklistItem(noteId: String, itemId: String, isDone: Boolean) {
        val note = noteDao.getNoteById(noteId) ?: return
        val currentChecklist = note.getChecklist()
        val updatedChecklist = currentChecklist.map {
            if (it.id == itemId) it.copy(isDone = isDone) else it
        }
        noteDao.updateChecklist(noteId, NoteEntity.encodeChecklist(updatedChecklist))
    }

    suspend fun duplicateNote(note: NoteEntity) {
        val copy = note.copy(
            id = UUID.randomUUID().toString(),
            title = "${note.title} (Copy)",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        noteDao.insertNote(copy)
    }

    suspend fun seedDefaultsIfEmpty() {
        if (noteDao.getCount() == 0) {
            val defaultNotes = listOf(
                NoteEntity(
                    id = "seed_1",
                    title = "Grocery & Pantry Checklist",
                    content = "Weekly grocery replenishment for home and kitchen essentials.",
                    category = "Lists",
                    colorHex = "#FFFFFF",
                    isPinned = true,
                    checklistJson = NoteEntity.encodeChecklist(
                        listOf(
                            ChecklistItem(id = "c1", text = "Almond milk & Greek yogurt", isDone = true),
                            ChecklistItem(id = "c2", text = "Whole grain sourdough bread", isDone = false),
                            ChecklistItem(id = "c3", text = "Fresh avocados (3)", isDone = false),
                            ChecklistItem(id = "c4", text = "Extra virgin olive oil", isDone = false),
                            ChecklistItem(id = "c5", text = "Cold brew blend coffee beans", isDone = true)
                        )
                    ),
                    updatedAt = System.currentTimeMillis() - 1000 * 60 * 15
                ),
                NoteEntity(
                    id = "seed_2",
                    title = "Work Meeting Summary & Tasks",
                    content = "Reviewed Q3 roadmap and feature timeline. Next sprint starts Tuesday. Submit pull requests by 4 PM. Follow up on database migration benchmarks.",
                    category = "Work",
                    colorHex = "#FFFFFF",
                    isPinned = false,
                    updatedAt = System.currentTimeMillis() - 1000 * 60 * 60 * 5
                ),
                NoteEntity(
                    id = "seed_3",
                    title = "Book & Podcast Recommendations",
                    content = "1. Designing Data-Intensive Applications by Martin Kleppmann\n2. Atomic Habits by James Clear\n3. Thinking in Systems by Donella Meadows\n4. Huberman Lab - Science of Focus",
                    category = "Ideas",
                    colorHex = "#FFFFFF",
                    isPinned = false,
                    updatedAt = System.currentTimeMillis() - 1000 * 60 * 60 * 24
                ),
                NoteEntity(
                    id = "seed_4",
                    title = "Apartment Wishlist & Improvements",
                    content = "Ergonomic standing desk lamp, monstera plant ceramic pot, linen bedsheets, coffee beans grinder, noise-cancelling desk pad.",
                    category = "Personal",
                    colorHex = "#FFFFFF",
                    isPinned = false,
                    updatedAt = System.currentTimeMillis() - 1000 * 60 * 60 * 48
                ),
                NoteEntity(
                    id = "seed_5",
                    title = "Weekly Workout Schedule",
                    content = "Mon: Push (Chest & Shoulders)\nWed: Pull (Back & Biceps)\nFri: Legs & Core\nSun: 5km light recovery jog",
                    category = "Personal",
                    colorHex = "#FFFFFF",
                    isPinned = false,
                    updatedAt = System.currentTimeMillis() - 1000 * 60 * 60 * 72
                )
            )
            noteDao.insertNotes(defaultNotes)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: NotesRepository? = null

        fun getInstance(context: Context): NotesRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppNotesDatabase.getInstance(context)
                val repo = NotesRepository(db.noteDao())
                INSTANCE = repo
                // Seed initial notes asynchronously
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        repo.seedDefaultsIfEmpty()
                    } catch (_: Exception) {}
                }
                repo
            }
        }
    }
}
