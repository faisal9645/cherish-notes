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

    // Daily Us (Question of the Day for Deep Lovers)
    private val _dailyQuestionFlow = MutableStateFlow(
        com.example.data.model.DailyQuestion(
            id = "q_today",
            question = "What is a small, quiet moment with me that made you feel deeply loved?",
            category = "Deep Connection",
            myAnswer = null,
            partnerAnswer = "When we made dinner together in our socks and you held my hand while the pasta was boiling ❤️",
            isMyAnswerSubmitted = false,
            isPartnerAnswerSubmitted = true,
            isLikedByPartner = false,
            streakDays = 14
        )
    )
    val dailyQuestionFlow: StateFlow<com.example.data.model.DailyQuestion> = _dailyQuestionFlow.asStateFlow()

    fun submitMyDailyAnswer(answer: String) {
        val current = _dailyQuestionFlow.value
        _dailyQuestionFlow.value = current.copy(
            myAnswer = answer.trim(),
            isMyAnswerSubmitted = true,
            streakDays = current.streakDays + 1
        )
    }

    fun toggleLikeDailyAnswer() {
        val current = _dailyQuestionFlow.value
        _dailyQuestionFlow.value = current.copy(isLikedByPartner = !current.isLikedByPartner)
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

    // --- LIFETIME LOVE & AGE JOURNEY (For couples whose connection lasts their entire life) ---
    private val _lifetimeProfileFlow = MutableStateFlow(
        com.example.data.model.LifetimeAgeProfile(
            myBirthYear = 1992,
            partnerBirthYear = 1995,
            relationshipStartYear = 2024,
            secretVow = "We may not wear rings before the world, but our hearts took a vow that no paper could ever hold. We chose each other freely in secret, and our connection is an eternal sanctuary for our entire lives."
        )
    )
    val lifetimeProfileFlow: StateFlow<com.example.data.model.LifetimeAgeProfile> = _lifetimeProfileFlow.asStateFlow()

    private val _yearlyJourneysFlow = MutableStateFlow<List<com.example.data.model.YearlyJourneyEntry>>(
        listOf(
            com.example.data.model.YearlyJourneyEntry(
                id = "year_2024",
                year = 2024,
                myAge = 32,
                partnerAge = 29,
                yearTheme = "The Spark That Ignited Our Secret World",
                placesWent = "Midnight drives through city hills, secluded rooftop lounge, the secret seaside bungalow",
                howWeEnjoyed = "We discovered each other in breathless secret conversations that lasted till dawn. Every glance across crowded rooms carried electric sparks that only we understood. Escaping into our private hideaway for hours where the rest of the world completely ceased to exist.",
                specialMemory = "The stormy rainy night in October when you looked into my eyes and whispered: 'No matter what happens, you are my real home.'",
                songOrQuote = "Our Anthem: 'Until I Found You' • Secret Codeword: 'Forever'",
                passionRating = 5
            ),
            com.example.data.model.YearlyJourneyEntry(
                id = "year_2025",
                year = 2025,
                myAge = 33,
                partnerAge = 30,
                yearTheme = "Stolen Escapes & Deep Intimacy",
                placesWent = "Hidden cabin in the misty pine mountains, boutique hotel suite 402, quiet sunset beach cove",
                howWeEnjoyed = "We learned to live two lives: what the outside world sees, and the breathtaking paradise we share together. Cooking breakfast together in secret at 2 PM, laughing uncontrollably, slow dancing in dim light without any shoes, touching with a hunger that only grew deeper.",
                specialMemory = "Waking up before dawn tangled in blankets, watching the golden sun touch your face, and promising that distance and circumstances will never tear us apart.",
                songOrQuote = "Quote: 'True love does not need a wedding ring; it needs two souls who choose each other every day.'",
                passionRating = 5
            ),
            com.example.data.model.YearlyJourneyEntry(
                id = "year_2026",
                year = 2026,
                myAge = 34,
                partnerAge = 31,
                yearTheme = "Soulmates Bound for Life",
                placesWent = "Scenic mountain overlook, cozy weekend getaway, our private sacred sanctuary",
                howWeEnjoyed = "Total emotional synchronization. We support each other through life's hardships, celebrating private victories, holding each other through quiet tears and fierce passion. We don't just love each other—we protect each other's peace.",
                specialMemory = "Sitting side by side under the stars, renewing our secret oath: to grow old together in our hearts, year by year, age by age, until the very end.",
                songOrQuote = "Motto: 'My heart has belonged to you since day one, and it will be yours forever.'",
                passionRating = 5
            )
        )
    )
    val yearlyJourneysFlow: StateFlow<List<com.example.data.model.YearlyJourneyEntry>> = _yearlyJourneysFlow.asStateFlow()

    fun updateLifetimeProfile(myBirthYear: Int, partnerBirthYear: Int, startYear: Int, vow: String) {
        _lifetimeProfileFlow.value = com.example.data.model.LifetimeAgeProfile(
            myBirthYear = myBirthYear,
            partnerBirthYear = partnerBirthYear,
            relationshipStartYear = startYear,
            secretVow = vow.trim()
        )
    }

    fun addYearlyJourney(entry: com.example.data.model.YearlyJourneyEntry) {
        _yearlyJourneysFlow.value = (listOf(entry) + _yearlyJourneysFlow.value).sortedByDescending { it.year }
    }

    fun updateYearlyJourney(entry: com.example.data.model.YearlyJourneyEntry) {
        _yearlyJourneysFlow.value = _yearlyJourneysFlow.value.map {
            if (it.id == entry.id) entry else it
        }.sortedByDescending { it.year }
    }

    fun deleteYearlyJourney(id: String) {
        _yearlyJourneysFlow.value = _yearlyJourneysFlow.value.filterNot { it.id == id }
    }
}
