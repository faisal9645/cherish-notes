package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.DateCategory
import com.example.data.model.ImportantDate
import com.example.data.model.Memory
import com.example.data.model.NoteCategory
import com.example.data.model.SharedNote
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CoupleFeaturesRepository(
    private val context: Context,
    private val authRepository: AuthRepository
) {
    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseFirestore.getInstance() else null
        } catch (e: Exception) {
            Log.w("CoupleFeaturesRepository", "Firestore not initialized", e)
            null
        }
    }

    private val _memoriesFlow = MutableStateFlow<List<Memory>>(getSampleMemories())
    val memoriesFlow: StateFlow<List<Memory>> = _memoriesFlow.asStateFlow()

    private val _datesFlow = MutableStateFlow<List<ImportantDate>>(getSampleImportantDates())
    val datesFlow: StateFlow<List<ImportantDate>> = _datesFlow.asStateFlow()

    private val _notesFlow = MutableStateFlow<List<SharedNote>>(getSampleNotes())
    val notesFlow: StateFlow<List<SharedNote>> = _notesFlow.asStateFlow()

    private fun getCoupleId(): String {
        return authRepository.currentUserState.value?.coupleId ?: "couple_cherish_private"
    }

    // Memories
    fun listenToMemories(): Flow<List<Memory>> = callbackFlow {
        val coupleId = getCoupleId()
        val fs = firestore
        if (fs != null) {
            val reg = fs.collection("couples")
                .document(coupleId)
                .collection("memories")
                .orderBy("dateMillis", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("CoupleFeaturesRepo", "Listen memories failed", error)
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        val items = snapshot.documents.mapNotNull { it.toObject(Memory::class.java) }
                        _memoriesFlow.value = items
                        trySend(items)
                    } else {
                        trySend(_memoriesFlow.value)
                    }
                }
            awaitClose { reg.remove() }
        } else {
            trySend(_memoriesFlow.value)
            awaitClose { }
        }
    }

    suspend fun addMemory(title: String, description: String, dateMillis: Long, photoUrl: String? = null, location: String? = null) {
        val coupleId = getCoupleId()
        val memory = Memory(
            id = UUID.randomUUID().toString(),
            coupleId = coupleId,
            title = title,
            description = description,
            photoUrl = photoUrl,
            dateMillis = dateMillis,
            location = location,
            createdByUserId = authRepository.getCurrentUserId(),
            createdAt = System.currentTimeMillis()
        )
        _memoriesFlow.value = listOf(memory) + _memoriesFlow.value
        try {
            firestore?.collection("couples")
                ?.document(coupleId)
                ?.collection("memories")
                ?.document(memory.id)
                ?.set(memory)
                ?.await()
        } catch (e: Exception) {
            Log.w("CoupleFeaturesRepo", "Firestore save memory error", e)
        }
    }

    suspend fun deleteMemory(memoryId: String) {
        val coupleId = getCoupleId()
        _memoriesFlow.value = _memoriesFlow.value.filter { it.id != memoryId }
        try {
            firestore?.collection("couples")
                ?.document(coupleId)
                ?.collection("memories")
                ?.document(memoryId)
                ?.delete()
                ?.await()
        } catch (e: Exception) {
            // ignore
        }
    }

    // Important Dates
    fun listenToImportantDates(): Flow<List<ImportantDate>> = callbackFlow {
        val coupleId = getCoupleId()
        val fs = firestore
        if (fs != null) {
            val reg = fs.collection("couples")
                .document(coupleId)
                .collection("importantDates")
                .orderBy("dateMillis", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        val items = snapshot.documents.mapNotNull { it.toObject(ImportantDate::class.java) }
                        _datesFlow.value = items
                        trySend(items)
                    } else {
                        trySend(_datesFlow.value)
                    }
                }
            awaitClose { reg.remove() }
        } else {
            trySend(_datesFlow.value)
            awaitClose { }
        }
    }

    suspend fun addImportantDate(title: String, dateMillis: Long, category: DateCategory, notes: String? = null) {
        val coupleId = getCoupleId()
        val item = ImportantDate(
            id = UUID.randomUUID().toString(),
            coupleId = coupleId,
            title = title,
            dateMillis = dateMillis,
            category = category.name,
            notes = notes
        )
        _datesFlow.value = (_datesFlow.value + item).sortedBy { it.dateMillis }
        try {
            firestore?.collection("couples")
                ?.document(coupleId)
                ?.collection("importantDates")
                ?.document(item.id)
                ?.set(item)
                ?.await()
        } catch (e: Exception) {
            Log.w("CoupleFeaturesRepo", "Save date error", e)
        }
    }

    suspend fun deleteImportantDate(dateId: String) {
        val coupleId = getCoupleId()
        _datesFlow.value = _datesFlow.value.filter { it.id != dateId }
        try {
            firestore?.collection("couples")
                ?.document(coupleId)
                ?.collection("importantDates")
                ?.document(dateId)
                ?.delete()
                ?.await()
        } catch (e: Exception) {
            // ignore
        }
    }

    // Shared Notes
    fun listenToSharedNotes(): Flow<List<SharedNote>> = callbackFlow {
        val coupleId = getCoupleId()
        val fs = firestore
        if (fs != null) {
            val reg = fs.collection("couples")
                .document(coupleId)
                .collection("sharedNotes")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener
                    if (snapshot != null && !snapshot.isEmpty) {
                        val items = snapshot.documents.mapNotNull { it.toObject(SharedNote::class.java) }
                        _notesFlow.value = items
                        trySend(items)
                    } else {
                        trySend(_notesFlow.value)
                    }
                }
            awaitClose { reg.remove() }
        } else {
            trySend(_notesFlow.value)
            awaitClose { }
        }
    }

    suspend fun saveSharedNote(noteId: String?, title: String, content: String, category: NoteCategory) {
        val coupleId = getCoupleId()
        val id = noteId ?: UUID.randomUUID().toString()
        val existing = _notesFlow.value.find { it.id == id }
        val note = SharedNote(
            id = id,
            coupleId = coupleId,
            title = title,
            content = content,
            category = category.name,
            updatedByUserId = authRepository.getCurrentUserId(),
            updatedAt = System.currentTimeMillis(),
            isPinned = existing?.isPinned ?: false
        )
        val currentList = _notesFlow.value.toMutableList()
        val idx = currentList.indexOfFirst { it.id == id }
        if (idx >= 0) {
            currentList[idx] = note
        } else {
            currentList.add(0, note)
        }
        _notesFlow.value = currentList
        try {
            firestore?.collection("couples")
                ?.document(coupleId)
                ?.collection("sharedNotes")
                ?.document(id)
                ?.set(note)
                ?.await()
        } catch (e: Exception) {
            Log.w("CoupleFeaturesRepo", "Save note error", e)
        }
    }

    suspend fun togglePinNote(noteId: String) {
        val coupleId = getCoupleId()
        val note = _notesFlow.value.find { it.id == noteId } ?: return
        val updated = note.copy(isPinned = !note.isPinned)
        _notesFlow.value = _notesFlow.value.map { if (it.id == noteId) updated else it }
        try {
            firestore?.collection("couples")
                ?.document(coupleId)
                ?.collection("sharedNotes")
                ?.document(noteId)
                ?.update("isPinned", updated.isPinned)
                ?.await()
        } catch (e: Exception) {
            // ignore
        }
    }

    suspend fun deleteNote(noteId: String) {
        val coupleId = getCoupleId()
        _notesFlow.value = _notesFlow.value.filter { it.id != noteId }
        try {
            firestore?.collection("couples")
                ?.document(coupleId)
                ?.collection("sharedNotes")
                ?.document(noteId)
                ?.delete()
                ?.await()
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun getSampleMemories(): List<Memory> {
        val now = System.currentTimeMillis()
        val thirtyDays = 30L * 24 * 3600 * 1000
        val sixtyDays = 60L * 24 * 3600 * 1000
        return listOf(
            Memory(
                id = "mem1",
                coupleId = "couple_cherish_private",
                title = "Starry Night by the Lake",
                description = "We brought blankets and stayed talking until 3 AM under the shooting stars. A night I'll never forget.",
                dateMillis = now - thirtyDays,
                location = "Emerald Lake",
                photoUrl = null
            ),
            Memory(
                id = "mem2",
                coupleId = "couple_cherish_private",
                title = "Our First Coffee Date",
                description = "You laughed at my latte art mustache and we both knew right there that this was something truly special.",
                dateMillis = now - sixtyDays,
                location = "Café Rose",
                photoUrl = null
            )
        )
    }

    private fun getSampleImportantDates(): List<ImportantDate> {
        val now = System.currentTimeMillis()
        val oneDay = 24L * 3600 * 1000
        return listOf(
            ImportantDate(
                id = "date1",
                coupleId = "couple_cherish_private",
                title = "Our Anniversary",
                dateMillis = now + 42 * oneDay,
                category = DateCategory.ANNIVERSARY.name,
                notes = "Celebrating our love story! Reserve our favorite rooftop table."
            ),
            ImportantDate(
                id = "date2",
                coupleId = "couple_cherish_private",
                title = "Her Birthday 🎂",
                dateMillis = now + 85 * oneDay,
                category = DateCategory.BIRTHDAY.name,
                notes = "Make it unforgettable! Special surprise gift in progress."
            ),
            ImportantDate(
                id = "date3",
                coupleId = "couple_cherish_private",
                title = "The Day We First Met",
                dateMillis = now - 365 * oneDay,
                category = DateCategory.FIRST_DATE.name,
                notes = "The best day of my life."
            )
        )
    }

    private fun getSampleNotes(): List<SharedNote> {
        return listOf(
            SharedNote(
                id = "note1",
                coupleId = "couple_cherish_private",
                title = "Places We Must Visit Together ✈️",
                content = "1. Sunset in Santorini, Greece\n2. Cherry blossom stroll in Kyoto\n3. Cabin in Banff with hot cocoa\n4. Northern Lights in Iceland\n5. Cooking class in Tuscany",
                category = NoteCategory.BUCKET_LIST.name,
                isPinned = true
            ),
            SharedNote(
                id = "note2",
                coupleId = "couple_cherish_private",
                title = "Reasons Why I Love You ❤️",
                content = "- The way your eyes light up when you smile\n- How you always check if I arrived safely\n- Your sweet laugh when we watch funny videos\n- How you make even boring grocery runs feel like an adventure\n- You are my favorite human in the entire world.",
                category = NoteCategory.LOVE_LETTER.name,
                isPinned = true
            ),
            SharedNote(
                id = "note3",
                coupleId = "couple_cherish_private",
                title = "Movie Night Watchlist 🍿",
                content = "• About Time\n• Before Sunrise\n• La La Land\n• Interstellar (your turn to pick!)\n• Amélie",
                category = NoteCategory.WISHLIST.name,
                isPinned = false
            )
        )
    }
}
