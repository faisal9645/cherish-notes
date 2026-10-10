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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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

    // Only real memories (no made-up samples)
    private val _memoriesFlow = MutableStateFlow<List<Memory>>(emptyList())
    val memoriesFlow: StateFlow<List<Memory>> = _memoriesFlow.asStateFlow()

    // Only real dates: reminders and the days counter must never show made-up ones
    private val _datesFlow = MutableStateFlow<List<ImportantDate>>(emptyList())
    val datesFlow: StateFlow<List<ImportantDate>> = _datesFlow.asStateFlow()

    // Only real notes (no made-up samples)
    private val _notesFlow = MutableStateFlow<List<SharedNote>>(emptyList())
    val notesFlow: StateFlow<List<SharedNote>> = _notesFlow.asStateFlow()

    /** The couple's id (their Firestore and Storage folder). */
    fun getCoupleId(): String {
        return authRepository.currentUserState.value?.coupleId?.trim()?.ifBlank { null } ?: "couple_faisal_shali"
    }

    private fun notifyAutoBackup() {
        try {
            com.example.CherishApplication.instance.googleDriveBackupManager.triggerImmediateAutoBackup()
        } catch (_: Exception) {}
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
                    if (snapshot != null) {
                        val items = snapshot.documents.mapNotNull { it.toObject(Memory::class.java) }
                        _memoriesFlow.value = items
                        trySend(items)
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
        notifyAutoBackup()
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
        notifyAutoBackup()
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
                    if (snapshot != null) {
                        val items = snapshot.documents.mapNotNull { it.toObject(ImportantDate::class.java) }
                        _datesFlow.value = items
                        trySend(items)
                    }
                }
            awaitClose { reg.remove() }
        } else {
            trySend(_datesFlow.value)
            awaitClose { }
        }
    }

    suspend fun addImportantDate(
        title: String,
        dateMillis: Long,
        category: DateCategory,
        notes: String? = null,
        repeatAnnually: Boolean = true
    ) {
        val coupleId = getCoupleId()
        val item = ImportantDate(
            id = UUID.randomUUID().toString(),
            coupleId = coupleId,
            title = title,
            dateMillis = dateMillis,
            category = category.name,
            notes = notes,
            repeatAnnually = repeatAnnually
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
                    if (snapshot != null) {
                        val items = snapshot.documents.mapNotNull { it.toObject(SharedNote::class.java) }
                        _notesFlow.value = items
                        trySend(items)
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

    suspend fun restoreSharedNotes(notes: List<SharedNote>) {
        if (notes.isEmpty()) return
        val current = _notesFlow.value.toMutableList()
        val existingIds = current.map { it.id }.toSet()
        val toAdd = notes.filter { it.id !in existingIds }
        if (toAdd.isNotEmpty()) {
            current.addAll(0, toAdd)
            _notesFlow.value = current
        }
    }

    suspend fun restoreImportantDates(dates: List<ImportantDate>) {
        if (dates.isEmpty()) return
        val current = _datesFlow.value.toMutableList()
        val existingIds = current.map { it.id }.toSet()
        val toAdd = dates.filter { it.id !in existingIds }
        if (toAdd.isNotEmpty()) {
            current.addAll(toAdd)
            _datesFlow.value = current.sortedBy { it.dateMillis }
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

    // Daily Us: one question a day (the same on both phones), both answers kept on the couple's
    // document; each one's answer is revealed to the other once they've answered too
    private val _dailyQuestionFlow = MutableStateFlow(com.example.data.model.DailyQuestion())
    val dailyQuestionFlow: StateFlow<com.example.data.model.DailyQuestion> = _dailyQuestionFlow.asStateFlow()

    /** Saves my answer to today's question; when both have answered, the streak grows (once a day). */
    fun submitMyDailyAnswer(answer: String) {
        val text = answer.trim().ifBlank { return }
        val uid = authRepository.getCurrentUserId().ifBlank { return }
        val ref = loveCoupleRef() ?: return
        val fs = firestore ?: return
        _dailyQuestionFlow.value = _dailyQuestionFlow.value.copy(myAnswer = text, isMyAnswerSubmitted = true)
        val today = dayKey()
        val yesterday = dayKey(System.currentTimeMillis() - DAY_MS)
        fs.runTransaction { tx ->
            val snap = tx.get(ref)
            val daily = snap.get("dailyUs") as? Map<*, *>
            val sameDay = daily?.get("date") == today
            val answers = mutableMapOf<String, Any?>()
            val likes = mutableMapOf<String, Any?>()
            if (sameDay) {
                (daily?.get("answers") as? Map<*, *>)?.forEach { (k, v) -> if (k is String) answers[k] = v }
                (daily?.get("likes") as? Map<*, *>)?.forEach { (k, v) -> if (k is String) likes[k] = v }
            }
            answers[uid] = text
            tx.set(
                ref,
                mapOf("dailyUs" to mapOf("date" to today, "answers" to answers, "likes" to likes)),
                com.google.firebase.firestore.SetOptions.mergeFields("dailyUs")
            )
            if (answers.size >= 2) {
                val streak = snap.get("dailyStreak") as? Map<*, *>
                val lastDate = streak?.get("lastDate") as? String
                if (lastDate != today) {
                    val count = (streak?.get("count") as? Number)?.toInt() ?: 0
                    tx.set(
                        ref,
                        mapOf("dailyStreak" to mapOf("count" to if (lastDate == yesterday) count + 1 else 1, "lastDate" to today)),
                        com.google.firebase.firestore.SetOptions.mergeFields("dailyStreak")
                    )
                }
            }
            null
        }.addOnFailureListener { Log.w("CoupleFeaturesRepo", "Saving the daily answer failed", it) }
    }

    /** Loves (or un-loves) the partner's answer today. */
    fun toggleLikeDailyAnswer() {
        val uid = authRepository.getCurrentUserId().ifBlank { return }
        val ref = loveCoupleRef() ?: return
        val current = _dailyQuestionFlow.value
        if (!current.isPartnerAnswerSubmitted) return
        val liked = !current.isLikedByPartner
        _dailyQuestionFlow.value = current.copy(isLikedByPartner = liked)
        ref.update(com.google.firebase.firestore.FieldPath.of("dailyUs", "likes", uid), liked)
            .addOnFailureListener { Log.w("CoupleFeaturesRepo", "Saving the like failed", it) }
    }

    // Love Jar (Reasons Why I Love You)
    private val _loveJarNotesFlow = MutableStateFlow(
        listOf(
            com.example.data.model.LoveJarNote("n1", "The way your nose scrunches when you genuinely laugh at my silly jokes.", "My Love", "🌸"),
            com.example.data.model.LoveJarNote("n2", "How safe, peaceful, and warm I feel whenever my head is on your chest.", "My Love", "✨"),
            com.example.data.model.LoveJarNote("n3", "You always make sure I drank enough water throughout the busy day.", "My Love", "💧"),
            com.example.data.model.LoveJarNote("n4", "How patient, gentle, and kind your heart is with me.", "My Love", "💖"),
            com.example.data.model.LoveJarNote("n5", "Because loving you is the easiest, most natural thing I have ever done.", "My Love", "💌")
        )
    )
    val loveJarNotesFlow: StateFlow<List<com.example.data.model.LoveJarNote>> = _loveJarNotesFlow.asStateFlow()

    fun addLoveJarNote(text: String, emoji: String) {
        val note = com.example.data.model.LoveJarNote(
            id = UUID.randomUUID().toString(),
            text = text.trim(),
            author = authRepository.currentUserState.value?.displayName?.ifBlank { "Me" } ?: "Me",
            emoji = emoji
        )
        _loveJarNotesFlow.value = listOf(note) + _loveJarNotesFlow.value
    }

    // Shared Couple Bucket List
    private val _bucketListFlow = MutableStateFlow(
        listOf(
            com.example.data.model.BucketListItem("b1", "Stargaze together all night from a truck bed", "Romantic Dates", true, "Aug 15"),
            com.example.data.model.BucketListItem("b2", "Roadtrip down the Pacific Coast Highway with our playlist", "Travel", false),
            com.example.data.model.BucketListItem("b3", "Take an authentic pasta-making class together", "Experiences", false),
            com.example.data.model.BucketListItem("b4", "Watch the sunrise on the beach wrapped in a warm blanket", "Romantic Dates", true, "Jul 4"),
            com.example.data.model.BucketListItem("b5", "Build a cozy pillow fort and have an all-night movie marathon", "Cozy Fun", false)
        )
    )
    val bucketListFlow: StateFlow<List<com.example.data.model.BucketListItem>> = _bucketListFlow.asStateFlow()

    fun toggleBucketItem(id: String) {
        val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
        val dateText = sdf.format(java.util.Date())
        _bucketListFlow.value = _bucketListFlow.value.map {
            if (it.id == id) {
                it.copy(
                    isCompleted = !it.isCompleted,
                    completedDate = if (!it.isCompleted) dateText else null
                )
            } else it
        }
    }

    fun addBucketItem(title: String, category: String) {
        val item = com.example.data.model.BucketListItem(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            category = category,
            isCompleted = false
        )
        _bucketListFlow.value = _bucketListFlow.value + item
    }

    // ---- Our movies, kept on the couple's record: suggested by one of us, then watched by each of
    // us (per person, so both phones agree on who watched it first and second) ----
    private val _moviesFlow = MutableStateFlow<List<com.example.data.model.MovieItem>>(emptyList())
    val moviesFlow: StateFlow<List<com.example.data.model.MovieItem>> = _moviesFlow.asStateFlow()

    /** Adds a movie; [watchedTogether] marks it watched by both of us already. */
    fun addMovie(title: String, genre: String, emoji: String, watchedTogether: Boolean, notes: String = "") {
        val ref = loveCoupleRef() ?: return
        val myId = authRepository.getCurrentUserId().ifBlank { null } ?: return
        val now = System.currentTimeMillis()
        val watched = if (watchedTogether) listOfNotNull(myId, partnerIdOrNull()).associateWith { now } else emptyMap()
        val item = com.example.data.model.MovieItem(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            genre = genre.trim().ifBlank { "Romance" },
            emoji = emoji.ifBlank { "🎬" },
            notes = notes.trim(),
            addedBy = myId,
            addedAt = now,
            watchedAt = watched
        )
        _moviesFlow.value = listOf(item) + _moviesFlow.value
        ref.set(
            mapOf(
                "movies" to mapOf(
                    item.id to mapOf(
                        "title" to item.title,
                        "genre" to item.genre,
                        "emoji" to item.emoji,
                        "notes" to item.notes,
                        "addedBy" to item.addedBy,
                        "addedAt" to item.addedAt,
                        "watchedAt" to item.watchedAt
                    )
                )
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).addOnFailureListener { Log.w("CoupleFeaturesRepo", "Saving the movie failed", it) }
    }

    /** I watched it (or take that back). The order between us comes from these times. */
    fun toggleMovieWatched(id: String) {
        val ref = loveCoupleRef() ?: return
        val myId = authRepository.getCurrentUserId().ifBlank { null } ?: return
        val movie = _moviesFlow.value.firstOrNull { it.id == id } ?: return
        val watchedNow = myId !in movie.watchedAt
        val now = System.currentTimeMillis()
        _moviesFlow.value = _moviesFlow.value.map {
            if (it.id != id) it
            else it.copy(watchedAt = if (watchedNow) it.watchedAt + (myId to now) else it.watchedAt - myId)
        }
        ref.update(
            com.google.firebase.firestore.FieldPath.of("movies", id, "watchedAt", myId),
            if (watchedNow) now else com.google.firebase.firestore.FieldValue.delete()
        ).addOnFailureListener { Log.w("CoupleFeaturesRepo", "Saving the watched mark failed", it) }
    }

    fun deleteMovie(id: String) {
        _moviesFlow.value = _moviesFlow.value.filterNot { it.id == id }
        loveCoupleRef()?.update(com.google.firebase.firestore.FieldPath.of("movies", id), com.google.firebase.firestore.FieldValue.delete())
            ?.addOnFailureListener { Log.w("CoupleFeaturesRepo", "Deleting the movie failed", it) }
    }

    // ---- Our books, kept on the couple's record: each of us has our own page and finish time, so
    // it shows who's reading what right now and who finished first ----
    private val _booksFlow = MutableStateFlow<List<com.example.data.model.BookItem>>(emptyList())
    val booksFlow: StateFlow<List<com.example.data.model.BookItem>> = _booksFlow.asStateFlow()

    fun addBook(title: String, author: String, totalPages: Int, genre: String, emoji: String, notes: String = "") {
        val ref = loveCoupleRef() ?: return
        val myId = authRepository.getCurrentUserId().ifBlank { null } ?: return
        val item = com.example.data.model.BookItem(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            author = author.trim(),
            totalPages = totalPages.coerceIn(1, 20_000),
            genre = genre.trim(),
            emoji = emoji.ifBlank { "📖" },
            notes = notes.trim(),
            addedBy = myId,
            addedAt = System.currentTimeMillis()
        )
        _booksFlow.value = listOf(item) + _booksFlow.value
        ref.set(
            mapOf(
                "books" to mapOf(
                    item.id to mapOf(
                        "title" to item.title,
                        "author" to item.author,
                        "totalPages" to item.totalPages,
                        "genre" to item.genre,
                        "emoji" to item.emoji,
                        "notes" to item.notes,
                        "addedBy" to item.addedBy,
                        "addedAt" to item.addedAt
                    )
                )
            ),
            com.google.firebase.firestore.SetOptions.merge()
        ).addOnFailureListener { Log.w("CoupleFeaturesRepo", "Saving the book failed", it) }
    }

    /** The page I'm on; the last page means I finished it (the first time is kept for the order). */
    fun updateMyBookPage(id: String, page: Int) {
        val ref = loveCoupleRef() ?: return
        val myId = authRepository.getCurrentUserId().ifBlank { null } ?: return
        val book = _booksFlow.value.firstOrNull { it.id == id } ?: return
        val clean = page.coerceIn(0, book.totalPages)
        val finished = clean >= book.totalPages
        val finishedAt = when {
            finished && !book.isFinishedBy(myId) -> System.currentTimeMillis()
            finished -> book.finishedAt[myId]
            else -> null
        }
        _booksFlow.value = _booksFlow.value.map {
            if (it.id != id) it
            else it.copy(
                pages = it.pages + (myId to clean),
                finishedAt = if (finishedAt != null) it.finishedAt + (myId to finishedAt) else it.finishedAt - myId
            )
        }
        ref.update(
            com.google.firebase.firestore.FieldPath.of("books", id, "pages", myId), clean,
            com.google.firebase.firestore.FieldPath.of("books", id, "finishedAt", myId),
            finishedAt ?: com.google.firebase.firestore.FieldValue.delete()
        ).addOnFailureListener { Log.w("CoupleFeaturesRepo", "Saving the page failed", it) }
    }

    fun deleteBook(id: String) {
        _booksFlow.value = _booksFlow.value.filterNot { it.id == id }
        loveCoupleRef()?.update(com.google.firebase.firestore.FieldPath.of("books", id), com.google.firebase.firestore.FieldValue.delete())
            ?.addOnFailureListener { Log.w("CoupleFeaturesRepo", "Deleting the book failed", it) }
    }

    // ---- Synced Ambient Listening / Watch Party: shared YouTube videos or music links ----
    private val _watchPartyFlow = MutableStateFlow<com.example.data.model.WatchPartySession?>(null)
    val watchPartyFlow: StateFlow<com.example.data.model.WatchPartySession?> = _watchPartyFlow.asStateFlow()

    fun startWatchParty(videoId: String, title: String, mediaUrl: String) {
        val myId = authRepository.getCurrentUserId()
        val session = com.example.data.model.WatchPartySession(
            id = "active",
            videoId = videoId,
            mediaUrl = mediaUrl,
            title = title.ifBlank { "Watch Party" },
            isPlaying = true,
            positionSeconds = 0f,
            updatedAt = System.currentTimeMillis(),
            updatedBy = myId,
            startedBy = myId,
            isActive = true
        )
        _watchPartyFlow.value = session
        val ref = loveCoupleRef() ?: return
        val map = mapOf(
            "videoId" to session.videoId,
            "mediaUrl" to session.mediaUrl,
            "title" to session.title,
            "isPlaying" to session.isPlaying,
            "positionSeconds" to session.positionSeconds,
            "updatedAt" to session.updatedAt,
            "updatedBy" to session.updatedBy,
            "startedBy" to session.startedBy,
            "isActive" to session.isActive
        )
        ref.set(mapOf("watchParty" to map), com.google.firebase.firestore.SetOptions.merge())
    }

    fun updateWatchPartyPlayback(isPlaying: Boolean, positionSeconds: Float) {
        val current = _watchPartyFlow.value?.takeIf { it.isActive } ?: return
        val myId = authRepository.getCurrentUserId()
        val updated = current.copy(
            isPlaying = isPlaying,
            positionSeconds = positionSeconds,
            updatedAt = System.currentTimeMillis(),
            updatedBy = myId
        )
        _watchPartyFlow.value = updated
        val ref = loveCoupleRef() ?: return
        val map = mapOf(
            "isPlaying" to isPlaying,
            "positionSeconds" to positionSeconds,
            "updatedAt" to updated.updatedAt,
            "updatedBy" to myId
        )
        ref.set(mapOf("watchParty" to map), com.google.firebase.firestore.SetOptions.merge())
    }

    fun endWatchParty() {
        _watchPartyFlow.value = null
        val ref = loveCoupleRef() ?: return
        // A merge, so ending also works when no party was ever saved
        val map = mapOf(
            "isActive" to false,
            "isPlaying" to false,
            "updatedAt" to System.currentTimeMillis(),
            "updatedBy" to authRepository.getCurrentUserId()
        )
        ref.set(mapOf("watchParty" to map), com.google.firebase.firestore.SetOptions.merge())
            .addOnFailureListener { Log.w("CoupleFeaturesRepo", "Ending the watch party failed", it) }
    }

    // ---- Goodnight Kiss / Sleep Sync: tap and hold glowing sphere until both phones trigger vibration ----
    private val _sleepSyncFlow = MutableStateFlow<com.example.data.model.SleepSyncEvent?>(null)
    val sleepSyncFlow: StateFlow<com.example.data.model.SleepSyncEvent?> = _sleepSyncFlow.asStateFlow()

    fun triggerSleepSync() {
        val myId = authRepository.getCurrentUserId()
        val myName = authRepository.currentUserState.value?.displayName?.ifBlank { null } ?: "My Love"
        val now = System.currentTimeMillis()
        val event = com.example.data.model.SleepSyncEvent(
            timestamp = now,
            senderId = myId,
            senderName = myName,
            status = "Asleep 🌙",
            isAsleep = true
        )
        _sleepSyncFlow.value = event
        // My note comes back when I wake up (only "Asleep" shows meanwhile)
        val currentNote = authRepository.currentUserState.value?.statusMessage.orEmpty()
        if (currentNote.isNotBlank() && currentNote != "Asleep 🌙") {
            context.getSharedPreferences("cherish_love", Context.MODE_PRIVATE).edit().putString("note_before_sleep", currentNote).apply()
        }
        authRepository.updateStatusMessage("Asleep 🌙")
        loveCoupleRef()?.set(
            mapOf(
                "sleepSync" to mapOf(
                    "timestamp" to now,
                    "senderId" to myId,
                    "senderName" to myName,
                    "status" to "Asleep 🌙",
                    "isAsleep" to true
                )
            ),
            com.google.firebase.firestore.SetOptions.merge()
        )
    }

    fun wakeUpFromSleep() {
        _sleepSyncFlow.value = null
        val love = context.getSharedPreferences("cherish_love", Context.MODE_PRIVATE)
        val note = love.getString("note_before_sleep", null)?.ifBlank { null } ?: "Loving every moment with you ✨"
        love.edit().remove("note_before_sleep").apply()
        authRepository.updateStatusMessage(note)
        loveCoupleRef()?.update(com.google.firebase.firestore.FieldPath.of("sleepSync", "isAsleep"), false)
    }

    // ---- Lifetime Story: ages from our real birthdays and together date; the vow and the yearly
    // stories kept on the couple's document for both phones ----
    private val _lifetimeProfileFlow = MutableStateFlow(com.example.data.model.LifetimeAgeProfile(0, 0, 0))
    val lifetimeProfileFlow: StateFlow<com.example.data.model.LifetimeAgeProfile> = _lifetimeProfileFlow.asStateFlow()

    private val _yearlyJourneysFlow = MutableStateFlow<List<com.example.data.model.YearlyJourneyEntry>>(emptyList())
    val yearlyJourneysFlow: StateFlow<List<com.example.data.model.YearlyJourneyEntry>> = _yearlyJourneysFlow.asStateFlow()

    /** Birth years and start year are used only until real birthdays / a together date are set. */
    fun updateLifetimeProfile(myBirthYear: Int, partnerBirthYear: Int, startYear: Int, vow: String) {
        val ref = loveCoupleRef() ?: return
        val myId = authRepository.getCurrentUserId()
        val birthYears = buildMap<String, Any> {
            if (myId.isNotBlank()) put(myId, myBirthYear)
            partnerIdOrNull()?.let { put(it, partnerBirthYear) }
        }
        _lifetimeProfileFlow.value = com.example.data.model.LifetimeAgeProfile(myBirthYear, partnerBirthYear, startYear, vow.trim())
        ref.set(
            mapOf("lifetime" to mapOf("birthYears" to birthYears, "startYear" to startYear, "vow" to vow.trim())),
            com.google.firebase.firestore.SetOptions.merge()
        ).addOnFailureListener { Log.w("CoupleFeaturesRepo", "Saving the lifetime profile failed", it) }
    }

    fun addYearlyJourney(entry: com.example.data.model.YearlyJourneyEntry) = saveYearlyJourney(entry)

    fun updateYearlyJourney(entry: com.example.data.model.YearlyJourneyEntry) = saveYearlyJourney(entry)

    private fun saveYearlyJourney(entry: com.example.data.model.YearlyJourneyEntry) {
        val ref = loveCoupleRef() ?: return
        _yearlyJourneysFlow.value = (_yearlyJourneysFlow.value.filterNot { it.id == entry.id } + entry).sortedByDescending { it.year }
        // Ages are stored from the writer's side ("my" age is the author's)
        val stored = mapOf(
            "year" to entry.year,
            "myAge" to entry.myAge,
            "partnerAge" to entry.partnerAge,
            "yearTheme" to entry.yearTheme,
            "placesWent" to entry.placesWent,
            "howWeEnjoyed" to entry.howWeEnjoyed,
            "specialMemory" to entry.specialMemory,
            "songOrQuote" to entry.songOrQuote,
            "passionRating" to entry.passionRating,
            "photoUrl" to entry.photoUrl,
            "createdAt" to entry.createdAt,
            "authorId" to authRepository.getCurrentUserId()
        )
        ref.set(mapOf("journeys" to mapOf(entry.id to stored)), com.google.firebase.firestore.SetOptions.merge())
            .addOnFailureListener { Log.w("CoupleFeaturesRepo", "Saving the yearly story failed", it) }
    }

    fun deleteYearlyJourney(id: String) {
        _yearlyJourneysFlow.value = _yearlyJourneysFlow.value.filterNot { it.id == id }
        loveCoupleRef()?.update(com.google.firebase.firestore.FieldPath.of("journeys", id), com.google.firebase.firestore.FieldValue.delete())
            ?.addOnFailureListener { Log.w("CoupleFeaturesRepo", "Deleting the yearly story failed", it) }
    }

    // ---- Open When letters: written for the other one, kept on the couple's document ----
    private val _openWhenFlow = MutableStateFlow<List<com.example.data.model.OpenWhenLetter>>(emptyList())
    /** All our letters, newest first: the ones written for me and the ones I wrote. */
    val openWhenFlow: StateFlow<List<com.example.data.model.OpenWhenLetter>> = _openWhenFlow.asStateFlow()

    fun addOpenWhenLetter(title: String, condition: String, content: String, emoji: String) {
        val ref = loveCoupleRef() ?: return
        val myId = authRepository.getCurrentUserId()
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val myName = authRepository.currentUserState.value?.displayName?.ifBlank { null } ?: "Me"
        _openWhenFlow.value = listOf(
            com.example.data.model.OpenWhenLetter(
                id = id, title = title.trim(), envelopeEmoji = emoji, content = content.trim(),
                authorId = myId, authorName = myName, createdAt = now, unlockCondition = condition.trim()
            )
        ) + _openWhenFlow.value
        val letter = mapOf(
            "title" to title.trim(),
            "condition" to condition.trim(),
            "content" to content.trim(),
            "emoji" to emoji,
            "authorId" to myId,
            "createdAt" to now
        )
        ref.set(mapOf("openWhen" to mapOf(id to letter)), com.google.firebase.firestore.SetOptions.merge())
            .addOnFailureListener { Log.w("CoupleFeaturesRepo", "Saving the letter failed", it) }
    }

    /** The reader broke the seal (only letters written for me are marked). */
    fun markOpenWhenOpened(id: String) {
        val letter = _openWhenFlow.value.firstOrNull { it.id == id } ?: return
        if (letter.isOpened || letter.authorId == authRepository.getCurrentUserId()) return
        val now = System.currentTimeMillis()
        _openWhenFlow.value = _openWhenFlow.value.map { if (it.id == id) it.copy(isOpened = true, openedAt = now) else it }
        loveCoupleRef()?.update(com.google.firebase.firestore.FieldPath.of("openWhen", id, "openedAt"), now)
            ?.addOnFailureListener { Log.w("CoupleFeaturesRepo", "Marking the letter opened failed", it) }
    }

    /** Deletes one of my own letters. */
    fun deleteOpenWhenLetter(id: String) {
        val letter = _openWhenFlow.value.firstOrNull { it.id == id } ?: return
        if (letter.authorId != authRepository.getCurrentUserId()) return
        _openWhenFlow.value = _openWhenFlow.value.filterNot { it.id == id }
        loveCoupleRef()?.update(com.google.firebase.firestore.FieldPath.of("openWhen", id), com.google.firebase.firestore.FieldValue.delete())
            ?.addOnFailureListener { Log.w("CoupleFeaturesRepo", "Deleting the letter failed", it) }
    }

    // ---- The couple's document, read by Daily Us, Open When and Lifetime Story ----
    private var coupleDocListener: com.google.firebase.firestore.ListenerRegistration? = null
    private var loveListeningCoupleId: String? = null
    @Volatile private var coupleDocData: Map<String, Any?> = emptyMap()
    private val loveScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main)

    private fun loveCoupleRef() = authRepository.currentUserState.value?.coupleId?.trim()?.ifBlank { null }
        ?.let { firestore?.collection("couples")?.document(it) }

    private fun partnerIdOrNull(): String? =
        (authRepository.partnerUserState.value?.id ?: authRepository.currentUserState.value?.partnerId)?.trim()?.ifBlank { null }

    /** The 6 AM day ("yyyy-MM-dd"), as everywhere else in the app. */
    private fun dayKey(now: Long = System.currentTimeMillis()): String =
        com.example.security.SecurityPreferences.getInstance(context).logicalDayKey(now)

    /** Listens to the couple's document once a couple is known (again if it changes). */
    fun ensureLoveUsListener() {
        val coupleId = authRepository.currentUserState.value?.coupleId?.trim()?.ifBlank { null } ?: return
        if (loveListeningCoupleId == coupleId) return
        val fs = firestore ?: return
        coupleDocListener?.remove()
        loveListeningCoupleId = coupleId
        coupleDocListener = fs.collection("couples").document(coupleId).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            coupleDocData = snapshot.data ?: emptyMap()
            rebuildLoveUs()
        }
    }

    /** Re-reads everything (also called when Love & Us opens, so a new day gets its new question). */
    fun rebuildLoveUs() {
        ensureLoveUsListener()
        val doc = coupleDocData
        val myId = authRepository.getCurrentUserId()
        val partnerId = partnerIdOrNull()
        val myName = authRepository.currentUserState.value?.displayName?.ifBlank { null } ?: "Me"
        val partnerName = authRepository.partnerUserState.value?.displayName?.ifBlank { null } ?: "Your love"

        // Daily Us
        val today = dayKey()
        val yesterday = dayKey(System.currentTimeMillis() - DAY_MS)
        val daily = doc["dailyUs"] as? Map<*, *>
        val sameDay = daily?.get("date") == today
        val answers = (if (sameDay) daily?.get("answers") as? Map<*, *> else null).orEmpty()
        val likes = (if (sameDay) daily?.get("likes") as? Map<*, *> else null).orEmpty()
        val theirKey = partnerId ?: answers.keys.firstOrNull { it != myId } as? String
        val mine = answers[myId] as? String
        val theirs = theirKey?.let { answers[it] } as? String
        val streak = doc["dailyStreak"] as? Map<*, *>
        val lastStreakDay = streak?.get("lastDate") as? String
        val streakDays = if (lastStreakDay == today || lastStreakDay == yesterday) (streak?.get("count") as? Number)?.toInt() ?: 0 else 0
        val (question, category) = dailyQuestionFor(today)
        _dailyQuestionFlow.value = com.example.data.model.DailyQuestion(
            id = today,
            question = question,
            category = category,
            myAnswer = mine,
            partnerAnswer = theirs,
            isMyAnswerSubmitted = mine != null,
            isPartnerAnswerSubmitted = theirs != null,
            isLikedByPartner = likes[myId] == true,
            partnerLovedMyAnswer = theirKey != null && likes[theirKey] == true,
            streakDays = streakDays
        )

        // Open When
        _openWhenFlow.value = (doc["openWhen"] as? Map<*, *>).orEmpty().mapNotNull { (key, value) ->
            val id = key as? String ?: return@mapNotNull null
            val letter = value as? Map<*, *> ?: return@mapNotNull null
            val authorId = letter["authorId"] as? String ?: ""
            val openedAt = (letter["openedAt"] as? Number)?.toLong()
            com.example.data.model.OpenWhenLetter(
                id = id,
                title = letter["title"] as? String ?: "",
                envelopeEmoji = letter["emoji"] as? String ?: "\uD83D\uDC8C",
                content = letter["content"] as? String ?: "",
                authorId = authorId,
                authorName = if (authorId == myId) myName else partnerName,
                createdAt = (letter["createdAt"] as? Number)?.toLong() ?: 0L,
                unlockCondition = letter["condition"] as? String ?: "",
                isOpened = openedAt != null,
                openedAt = openedAt
            )
        }.sortedByDescending { it.createdAt }

        // Lifetime Story: real birthdays and together date first, what was typed otherwise
        val life = doc["lifetime"] as? Map<*, *>
        val birthYears = (life?.get("birthYears") as? Map<*, *>).orEmpty()
        val birthdays = authRepository.birthdays.value
        fun yearOf(date: String?) = date?.take(4)?.toIntOrNull()
        val myBirthYear = yearOf(birthdays[myId]) ?: (birthYears[myId] as? Number)?.toInt() ?: 0
        val partnerBirthYear = partnerId?.let { yearOf(birthdays[it]) ?: (birthYears[it] as? Number)?.toInt() } ?: 0
        val since = authRepository.togetherSince.value ?: _datesFlow.value
            .filter { it.getTypedCategory() == DateCategory.ANNIVERSARY }
            .minByOrNull { it.dateMillis }
            ?.let { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(it.dateMillis)) }
        val startYear = yearOf(since) ?: (life?.get("startYear") as? Number)?.toInt() ?: java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val vow = (life?.get("vow") as? String)?.ifBlank { null } ?: com.example.data.model.LifetimeAgeProfile().secretVow
        _lifetimeProfileFlow.value = com.example.data.model.LifetimeAgeProfile(myBirthYear, partnerBirthYear, startYear, vow)

        _yearlyJourneysFlow.value = (doc["journeys"] as? Map<*, *>).orEmpty().mapNotNull { (key, value) ->
            val id = key as? String ?: return@mapNotNull null
            val entry = value as? Map<*, *> ?: return@mapNotNull null
            val authorId = entry["authorId"] as? String ?: ""
            val authorAge = (entry["myAge"] as? Number)?.toInt() ?: 0
            val otherAge = (entry["partnerAge"] as? Number)?.toInt() ?: 0
            // Ages were stored from the writer's side: swap them for the other one of us
            val writtenByMe = authorId.isBlank() || authorId == myId
            com.example.data.model.YearlyJourneyEntry(
                id = id,
                year = (entry["year"] as? Number)?.toInt() ?: 0,
                myAge = if (writtenByMe) authorAge else otherAge,
                partnerAge = if (writtenByMe) otherAge else authorAge,
                yearTheme = entry["yearTheme"] as? String ?: "",
                placesWent = entry["placesWent"] as? String ?: "",
                howWeEnjoyed = entry["howWeEnjoyed"] as? String ?: "",
                specialMemory = entry["specialMemory"] as? String ?: "",
                songOrQuote = entry["songOrQuote"] as? String ?: "",
                passionRating = (entry["passionRating"] as? Number)?.toInt() ?: 5,
                photoUrl = entry["photoUrl"] as? String,
                createdAt = (entry["createdAt"] as? Number)?.toLong() ?: 0L,
                authorId = authorId
            )
        }.sortedByDescending { it.year }

        // Our movies and books
        fun longs(value: Any?): Map<String, Long> = (value as? Map<*, *>).orEmpty().mapNotNull { (k, v) ->
            val key = k as? String ?: return@mapNotNull null
            val time = (v as? Number)?.toLong() ?: return@mapNotNull null
            key to time
        }.toMap()
        _moviesFlow.value = (doc["movies"] as? Map<*, *>).orEmpty().mapNotNull { (key, value) ->
            val id = key as? String ?: return@mapNotNull null
            val m = value as? Map<*, *> ?: return@mapNotNull null
            val title = (m["title"] as? String)?.ifBlank { null } ?: return@mapNotNull null
            com.example.data.model.MovieItem(
                id = id,
                title = title,
                genre = m["genre"] as? String ?: "",
                emoji = (m["emoji"] as? String)?.ifBlank { null } ?: "\uD83C\uDFAC",
                notes = m["notes"] as? String ?: "",
                addedBy = m["addedBy"] as? String ?: "",
                addedAt = (m["addedAt"] as? Number)?.toLong() ?: 0L,
                watchedAt = longs(m["watchedAt"])
            )
        }.sortedByDescending { it.addedAt }
        _booksFlow.value = (doc["books"] as? Map<*, *>).orEmpty().mapNotNull { (key, value) ->
            val id = key as? String ?: return@mapNotNull null
            val b = value as? Map<*, *> ?: return@mapNotNull null
            val title = (b["title"] as? String)?.ifBlank { null } ?: return@mapNotNull null
            com.example.data.model.BookItem(
                id = id,
                title = title,
                author = b["author"] as? String ?: "",
                totalPages = (b["totalPages"] as? Number)?.toInt()?.coerceAtLeast(1) ?: 1,
                genre = b["genre"] as? String ?: "",
                emoji = (b["emoji"] as? String)?.ifBlank { null } ?: "\uD83D\uDCD6",
                notes = b["notes"] as? String ?: "",
                addedBy = b["addedBy"] as? String ?: "",
                addedAt = (b["addedAt"] as? Number)?.toLong() ?: 0L,
                pages = (b["pages"] as? Map<*, *>).orEmpty().mapNotNull { (k, v) ->
                    val user = k as? String ?: return@mapNotNull null
                    val page = (v as? Number)?.toInt() ?: return@mapNotNull null
                    user to page
                }.toMap(),
                finishedAt = longs(b["finishedAt"])
            )
        }.sortedByDescending { it.addedAt }

        // Synced Watch Party: on from Start until either of us ends it
        val wp = doc["watchParty"] as? Map<*, *>
        val wpOver = wp != null && (
            wp["isActive"] != true ||
                System.currentTimeMillis() - ((wp["updatedAt"] as? Number)?.toLong() ?: 0L) > WATCH_PARTY_IDLE_MS ||
                // The sample video older versions started by themselves whenever the button was tapped
                (wp["videoId"] == "dQw4w9WgXcQ" && wp["title"] == "Cozy Ambient Music")
            )
        if (wp != null && !wpOver) {
            _watchPartyFlow.value = com.example.data.model.WatchPartySession(
                id = "active",
                videoId = wp["videoId"] as? String ?: "",
                mediaUrl = wp["mediaUrl"] as? String ?: "",
                title = wp["title"] as? String ?: "Watch Party",
                isPlaying = wp["isPlaying"] as? Boolean ?: false,
                positionSeconds = (wp["positionSeconds"] as? Number)?.toFloat() ?: 0f,
                updatedAt = (wp["updatedAt"] as? Number)?.toLong() ?: 0L,
                updatedBy = wp["updatedBy"] as? String ?: "",
                startedBy = wp["startedBy"] as? String ?: "",
                isActive = true
            )
        } else if (wpOver) {
            _watchPartyFlow.value = null
        }

        // Goodnight Kiss / Sleep Sync
        val ss = doc["sleepSync"] as? Map<*, *>
        if (ss != null && ss["isAsleep"] == true) {
            val ts = (ss["timestamp"] as? Number)?.toLong() ?: 0L
            if (System.currentTimeMillis() - ts < 14L * 3600 * 1000) {
                _sleepSyncFlow.value = com.example.data.model.SleepSyncEvent(
                    timestamp = ts,
                    senderId = ss["senderId"] as? String ?: "",
                    senderName = ss["senderName"] as? String ?: "",
                    status = ss["status"] as? String ?: "Asleep 🌙",
                    isAsleep = true
                )
            }
        }
    }

    init {
        // Ages, the together date and the couple itself can change: re-read when they do
        loveScope.launch {
            // Only what Love & Us shows: who we are and our couple (a partner going online, typing
            // or charging changes nothing here, so it doesn't re-read everything each time)
            kotlinx.coroutines.flow.combine(
                authRepository.birthdays,
                authRepository.togetherSince,
                authRepository.currentUserState
                    .map { user -> listOf(user?.id, user?.coupleId, user?.partnerId, user?.displayName) }
                    .distinctUntilChanged(),
                authRepository.partnerUserState
                    .map { partner -> partner?.id to partner?.displayName }
                    .distinctUntilChanged()
            ) { _, _, _, _ -> }.collect { rebuildLoveUs() }
        }
    }

    private companion object {
        const val DAY_MS = 86_400_000L

        /** A Watch Party nobody has played, paused or moved for this long was forgotten: it's over. */
        const val WATCH_PARTY_IDLE_MS = 6 * 60 * 60 * 1000L

        /** One question a day, the same on both phones: (question, category). */
        val DAILY_QUESTIONS = listOf(
            "What is a small, quiet moment with me that made you feel deeply loved?" to "Deep Connection",
            "What was your very first impression of me?" to "Our Story",
            "Which song always makes you think of us?" to "Little Things",
            "What is one dream you want us to live together?" to "Future",
            "When do you feel closest to me?" to "Deep Connection",
            "What made you smile today?" to "Today",
            "What's one thing I do that always makes your day better?" to "Little Things",
            "Where would you take me if we could go anywhere tomorrow?" to "Adventure",
            "What is your favourite memory of us so far?" to "Our Story",
            "What do you love most about the way we talk to each other?" to "Deep Connection",
            "What's a tiny habit of mine you secretly love?" to "Little Things",
            "What would our perfect lazy Sunday look like?" to "Cozy",
            "What is something new you'd like us to try together?" to "Adventure",
            "Which moment made you sure about us?" to "Our Story",
            "What do you need more of from me this week?" to "Care",
            "What is one thing you've never told me but want to?" to "Deep Connection",
            "Describe me in three words." to "Playful",
            "What's your favourite photo of us, and why?" to "Memories",
            "What does home mean to you?" to "Deep Connection",
            "What are you most grateful for about us today?" to "Gratitude",
            "What would you write in a letter to us ten years from now?" to "Future",
            "Which day with me would you love to live again?" to "Memories",
            "What makes you feel safe with me?" to "Care",
            "What's a silly thing that always makes us laugh?" to "Playful",
            "What's a goal you want my support with?" to "Care",
            "What is your favourite way I show love?" to "Love Languages",
            "If we had a whole day with no plans, how would you spend it with me?" to "Cozy",
            "What is something about me that surprised you?" to "Our Story",
            "Which place reminds you of us the most?" to "Memories",
            "What do you want to celebrate together this year?" to "Future",
            "What's one thing you love about yourself when you're with me?" to "Deep Connection",
            "What would you cook for me on a special night?" to "Playful",
            "What's the sweetest message I ever sent you?" to "Memories",
            "How can I make tomorrow a little easier for you?" to "Care",
            "What are you looking forward to most with me?" to "Future",
            "What is a promise you want to make to me today?" to "Deep Connection"
        )

        fun dailyQuestionFor(day: String): Pair<String, String> {
            val dayNumber = try {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(day)?.time?.div(DAY_MS) ?: 0L
            } catch (_: Exception) {
                0L
            }
            return DAILY_QUESTIONS[(Math.floorMod(dayNumber, DAILY_QUESTIONS.size.toLong())).toInt()]
        }
    }
}
