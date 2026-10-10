package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.MessageStatus
import com.example.data.model.MessageType
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ChatRepository(
    private val context: Context,
    private val authRepository: AuthRepository
) {
    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseFirestore.getInstance() else null
        } catch (e: Exception) {
            Log.w("ChatRepository", "Firestore not initialized", e)
            null
        }
    }

    // In-memory cache for ultra-fast rendering & offline resilience
    private val _messagesFlow = MutableStateFlow<List<Message>>(emptyList())
    val messagesFlow: StateFlow<List<Message>> = _messagesFlow.asStateFlow()

    // Dual-consent chat deletion state (both must accept to delete chat)
    private val _deletionRequestFlow = MutableStateFlow<com.example.data.model.ChatDeletionRequest?>(null)
    val deletionRequestFlow: StateFlow<com.example.data.model.ChatDeletionRequest?> = _deletionRequestFlow.asStateFlow()

    private var globalTodayListener: ListenerRegistration? = null
    private var currentActiveConversationId: String? = null

    // Older chat (before today) comes in pages. Each page has its own listener that starts below the
    // previous page's oldest message, so every message downloads once and loaded pages still get
    // live edits, reactions and deletions. All of this is touched on the main thread only.
    private val olderPageListeners = mutableListOf<ListenerRegistration>()
    private val olderPages = mutableListOf<List<Message>>()
    private val olderPageOldestTimestamps = mutableListOf<Long>()
    private var olderPagesGeneration = 0
    private var pendingOlderLoad = 0L
    private var isInitialTodaySnapshot = true
    
    private var previousMessageLimit = 0L
    private var todayMessages: List<Message> = emptyList()
    private var previousMessages: List<Message> = emptyList()
    private var localBackupMessages: List<Message> = emptyList()

    private val _isQueryExhaustedFlow = MutableStateFlow(false)
    val isQueryExhaustedFlow: StateFlow<Boolean> = _isQueryExhaustedFlow.asStateFlow()

    private val _isLoadingMoreFlow = MutableStateFlow(false)
    val isLoadingMoreFlow: StateFlow<Boolean> = _isLoadingMoreFlow.asStateFlow()

    private val _hasPreviousChatsAvailableFlow = MutableStateFlow(true)
    val hasPreviousChatsAvailableFlow: StateFlow<Boolean> = _hasPreviousChatsAvailableFlow.asStateFlow()

    private val _galleryMediaMessages = MutableStateFlow<List<Message>>(emptyList())
    val galleryMediaMessages: StateFlow<List<Message>> = _galleryMediaMessages.asStateFlow()

    private val _isGalleryLoadingFlow = MutableStateFlow(false)
    val isGalleryLoadingFlow: StateFlow<Boolean> = _isGalleryLoadingFlow.asStateFlow()

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var galleryLoadJob: Job? = null
    @Volatile private var galleryLoadedConversationId: String? = null

    // Deleted during this session, so late gallery or backup results can't bring them back
    private val deletedMessageIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    var isQueryExhausted: Boolean
        get() = _isQueryExhaustedFlow.value
        set(value) { _isQueryExhaustedFlow.value = value }

    val isLoadingMore: Boolean
        get() = _isLoadingMoreFlow.value

    fun getStartOfToday(): Long {
        val cal = java.util.Calendar.getInstance()
        if (cal.get(java.util.Calendar.HOUR_OF_DAY) < 6) {
            // Before 6 AM, today's chat session started yesterday morning at 6:00 AM
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        cal.set(java.util.Calendar.HOUR_OF_DAY, 6)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    init {
        _messagesFlow.value = emptyList()
        CoroutineScope(Dispatchers.IO).launch {
            authRepository.currentUserState
                .map { it?.coupleId?.trim()?.ifBlank { null } ?: "couple_faisal_shali" }
                .distinctUntilChanged()
                .collectLatest { convId ->
                    // Listener state is only touched on the main thread, like the listener callbacks
                    withContext(Dispatchers.Main) {
                        clearOlderPages()
                        todayMessages = emptyList()
                        _isQueryExhaustedFlow.value = false
                        _isLoadingMoreFlow.value = false
                        _hasPreviousChatsAvailableFlow.value = true
                        resetGalleryCache()
                        startGlobalMessagesListener(convId)
                    }
                }
        }
    }

    fun getConversationId(): String {
        val user = authRepository.currentUserState.value
        val coupleId = user?.coupleId?.trim()?.ifBlank { null } ?: "couple_faisal_shali"
        return coupleId
    }

    fun loadMoreMessages() {
        loadOlderPage(OLDER_PAGE_SIZE)
    }

    /**
     * Every message sent from [from] (inclusive) until [until] (exclusive), oldest first, read in
     * pages straight from Firestore (for the monthly recap). Null when it couldn't be read.
     */
    suspend fun loadMessagesBetween(from: Long, until: Long): List<Message>? {
        val fs = firestore ?: return null
        val messagesRef = fs.collection("conversations")
            .document(currentActiveConversationId ?: getConversationId())
            .collection("messages")
        return try {
            val all = mutableListOf<Message>()
            var lastDoc: DocumentSnapshot? = null
            while (true) {
                var page = messagesRef
                    .whereGreaterThanOrEqualTo("timestamp", from)
                    .whereLessThan("timestamp", until)
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .limit(RANGE_PAGE_SIZE)
                lastDoc?.let { page = page.startAfter(it) }
                // From the server: a cache-only answer could be missing messages
                val docs = page.get(Source.SERVER).await().documents
                docs.mapNotNullTo(all) { it.toMessageOrNull() }
                if (docs.size < RANGE_PAGE_SIZE) break
                lastDoc = docs.last()
            }
            all
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ChatRepository", "Couldn't read messages for the range", e)
            null
        }
    }

    /** "Show in chat" for an older message: make sure about [SEARCH_OLDER_TARGET] older messages are loaded. */
    fun expandLimitForSearch() {
        val missing = SEARCH_OLDER_TARGET - previousMessageLimit
        if (missing <= 0L) return
        if (_isLoadingMoreFlow.value) pendingOlderLoad = missing else loadOlderPage(missing)
    }

    /** Loads the next page of messages from before today, below the oldest one already loaded. */
    private fun loadOlderPage(pageSize: Long) {
        if (pageSize <= 0L || _isQueryExhaustedFlow.value || _isLoadingMoreFlow.value) return
        val fs = firestore
        if (fs == null) {
            _isQueryExhaustedFlow.value = true
            return
        }
        val conversationId = currentActiveConversationId ?: getConversationId()
        val upperBound = olderPageOldestTimestamps.lastOrNull() ?: getStartOfToday()
        val pageIndex = olderPages.size
        val generation = olderPagesGeneration
        olderPages.add(emptyList())
        previousMessageLimit += pageSize
        _isLoadingMoreFlow.value = true

        val registration = fs.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .whereLessThan("timestamp", upperBound)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(pageSize)
            .addSnapshotListener { snapshot, error ->
                if (generation != olderPagesGeneration) return@addSnapshotListener
                val isNewestPage = pageIndex == olderPages.lastIndex
                if (error != null) {
                    Log.w("ChatRepository", "Listen older messages failed", error)
                    if (isNewestPage) _isLoadingMoreFlow.value = false
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener
                val docs = snapshot.documents
                if (isNewestPage) {
                    // Until the next page is requested, the newest page decides where it will start
                    // and whether history has ended (a first, cache-only answer may still grow)
                    val oldest = docs.lastOrNull()?.getLong("timestamp") ?: upperBound
                    if (olderPageOldestTimestamps.size > pageIndex) {
                        olderPageOldestTimestamps[pageIndex] = oldest
                    } else {
                        olderPageOldestTimestamps.add(oldest)
                    }
                    _isQueryExhaustedFlow.value = docs.size < pageSize
                    if (pageIndex == 0) _hasPreviousChatsAvailableFlow.value = docs.isNotEmpty()
                    _isLoadingMoreFlow.value = false
                }
                olderPages[pageIndex] = docs.mapNotNull { it.toMessageOrNull() }
                previousMessages = olderPages.flatten()
                mergeAndEmitMessages()
                if (isNewestPage && pendingOlderLoad > 0L) {
                    val more = pendingOlderLoad
                    pendingOlderLoad = 0L
                    loadOlderPage(more)
                }
            }
        olderPageListeners.add(registration)
    }

    private fun clearOlderPages() {
        olderPagesGeneration++
        olderPageListeners.forEach { it.remove() }
        olderPageListeners.clear()
        olderPages.clear()
        olderPageOldestTimestamps.clear()
        pendingOlderLoad = 0L
        previousMessageLimit = 0L
        previousMessages = emptyList()
    }

    /**
     * Loads every gallery item (photos, videos, voice notes, links and starred messages) straight from
     * Firestore, independent of chat pagination, so the gallery is complete right away while the chat
     * keeps paging older history on scroll.
     */
    fun loadAllGalleryMedia(forceRefresh: Boolean = false) {
        // Whatever is already on the device shows up instantly
        publishGalleryItems(_messagesFlow.value + localBackupMessages)

        val fs = firestore ?: return
        val convId = currentActiveConversationId ?: getConversationId()
        if (!forceRefresh && (galleryLoadJob?.isActive == true || galleryLoadedConversationId == convId)) return

        galleryLoadJob?.cancel()
        _isGalleryLoadingFlow.value = true
        val messagesRef = fs.collection("conversations").document(convId).collection("messages")
        val job = repoScope.launch {
            // Everything already on the phone shows at once from its cache. From the server only
            // what's new since the last check is fetched; the whole gallery once a week, to pick up
            // changes to older items.
            try {
                loadGalleryFrom(messagesRef, Source.CACHE, convId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ChatRepository", "Gallery load from cache failed", e)
            }
            try {
                val lastSync = galleryPrefs.getLong("gallery_synced_$convId", 0L)
                val lastFullSync = galleryPrefs.getLong("gallery_full_synced_$convId", 0L)
                val syncStartedAt = System.currentTimeMillis()
                if (lastSync > 0L && syncStartedAt - lastFullSync < GALLERY_FULL_SYNC_INTERVAL_MS) {
                    val newer = messagesRef
                        .whereGreaterThan("timestamp", lastSync - GALLERY_SYNC_OVERLAP_MS)
                        .get(Source.SERVER).await()
                        .documents.mapNotNull { it.toMessageOrNull() }
                    publishGalleryItems(newer)
                } else {
                    loadGalleryFrom(messagesRef, Source.SERVER, convId)
                    galleryPrefs.edit().putLong("gallery_full_synced_$convId", syncStartedAt).apply()
                }
                galleryPrefs.edit().putLong("gallery_synced_$convId", syncStartedAt).apply()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ChatRepository", "Gallery sync from server failed", e)
            }
            galleryLoadedConversationId = convId
        }
        galleryLoadJob = job
        job.invokeOnCompletion {
            if (galleryLoadJob === job) _isGalleryLoadingFlow.value = false
        }
    }

    private suspend fun loadGalleryFrom(
        messagesRef: CollectionReference,
        source: Source,
        conversationId: String
    ) = coroutineScope {
        // Photos, videos, voice notes, starred and link messages can all be queried directly
        val targetedQueries = listOf(
            messagesRef.whereIn("type", listOf(MessageType.IMAGE.name, MessageType.VIDEO.name, MessageType.AUDIO.name)),
            messagesRef.whereEqualTo("isStarred", true),
            messagesRef.whereEqualTo("hasLink", true)
        )
        val targeted = targetedQueries
            .map { query -> async { query.get(source).await().documents.mapNotNull { it.toMessageOrNull() } } }
            .awaitAll()
            .flatten()
        publishGalleryItems(targeted)

        // Messages from before the hasLink flag existed: page through the history once per phone
        // (newest first, publishing as pages arrive) and tag the link messages, so from then on the
        // query above finds them without a scan
        if (isLinkBackfillDone(conversationId)) return@coroutineScope
        val untaggedLinkIds = mutableListOf<String>()
        var lastDoc: DocumentSnapshot? = null
        val pending = mutableListOf<Message>()
        var lastPublishAt = 0L
        while (true) {
            var page = messagesRef.orderBy("timestamp", Query.Direction.DESCENDING).limit(GALLERY_SCAN_PAGE_SIZE)
            lastDoc?.let { page = page.startAfter(it) }
            val docs = page.get(source).await().documents
            docs.forEach { doc ->
                val message = doc.toMessageOrNull() ?: return@forEach
                pending += message
                if (!message.hasLink && LINK_REGEX.containsMatchIn(message.text)) untaggedLinkIds += message.id
            }
            val isLastPage = docs.size < GALLERY_SCAN_PAGE_SIZE
            val now = System.currentTimeMillis()
            if (isLastPage || now - lastPublishAt >= GALLERY_PUBLISH_INTERVAL_MS) {
                publishGalleryItems(pending.toList())
                pending.clear()
                lastPublishAt = now
            }
            if (isLastPage) break
            lastDoc = docs.last()
        }
        if (source == Source.SERVER) {
            tagLinkMessages(messagesRef, untaggedLinkIds)
            markLinkBackfillDone(conversationId)
        }
    }

    private val galleryPrefs by lazy {
        context.getSharedPreferences("cherish_gallery_prefs", Context.MODE_PRIVATE)
    }

    private fun isLinkBackfillDone(conversationId: String): Boolean =
        galleryPrefs.getBoolean("links_tagged_$conversationId", false)

    private fun markLinkBackfillDone(conversationId: String) {
        galleryPrefs.edit().putBoolean("links_tagged_$conversationId", true).apply()
    }

    private fun tagLinkMessages(messagesRef: CollectionReference, messageIds: List<String>) {
        val fs = firestore ?: return
        messageIds.chunked(FIRESTORE_BATCH_LIMIT).forEach { chunk ->
            try {
                val batch = fs.batch()
                chunk.forEach { batch.update(messagesRef.document(it), "hasLink", true) }
                batch.commit().addOnFailureListener { e ->
                    Log.w("ChatRepository", "Failed to tag ${chunk.size} link messages", e)
                }
            } catch (e: Exception) {
                Log.w("ChatRepository", "Failed to tag ${chunk.size} link messages", e)
            }
        }
    }

    private fun isGalleryRelevant(message: Message): Boolean {
        if (message.isDeleted) return false
        return when (message.getTypedType()) {
            MessageType.IMAGE, MessageType.VIDEO, MessageType.AUDIO -> true
            else -> message.isStarred || message.isVideoNote ||
                !message.mediaUrl.isNullOrBlank() || message.mediaUrls.isNotEmpty() ||
                LINK_REGEX.containsMatchIn(message.text)
        }
    }

    private fun publishGalleryItems(candidates: List<Message>) {
        val (relevant, irrelevant) = candidates
            .filter { it.id.isNotBlank() }
            .partition { it.id !in deletedMessageIds && isGalleryRelevant(it) }
        if (relevant.isEmpty() && irrelevant.isEmpty()) return
        val dropIds = irrelevant.mapTo(HashSet()) { it.id }
        _galleryMediaMessages.update { current ->
            val merged = LinkedHashMap<String, Message>(current.size + relevant.size)
            current.forEach { if (it.id !in dropIds) merged[it.id] = it }
            relevant.forEach { merged[it.id] = it }
            merged.values.sortedByDescending { it.timestamp }
        }
    }

    private fun resetGalleryCache() {
        galleryLoadJob?.cancel()
        galleryLoadJob = null
        galleryLoadedConversationId = null
        _isGalleryLoadingFlow.value = false
        _galleryMediaMessages.value = emptyList()
    }

    fun recoverAllMessages() {
        // Un-delete all messages in memory
        _messagesFlow.value = _messagesFlow.value.map {
            if (it.isDeleted) it.copy(isDeleted = false) else it
        }
        todayMessages = todayMessages.map {
            if (it.isDeleted) it.copy(isDeleted = false) else it
        }
        previousMessages = previousMessages.map {
            if (it.isDeleted) it.copy(isDeleted = false) else it
        }

        // Gallery: every photo, video, voice note, link and starred message comes back right away
        // (from what's already on the phone; it's only fetched when it isn't)
        loadAllGalleryMedia()

        // Chat: older history comes back page by page as the user scrolls up
        _hasPreviousChatsAvailableFlow.value = true
        if (olderPages.isEmpty()) {
            _isQueryExhaustedFlow.value = false
            loadOlderPage(OLDER_PAGE_SIZE)
        }
        mergeAndEmitMessages()

        // The local backup stays on this device (never uploaded to the partner); read it off the main thread
        repoScope.launch {
            val backupMessages = readLocalBackupMessages().filter { it.id !in deletedMessageIds }
            if (backupMessages.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    localBackupMessages = backupMessages
                    mergeAndEmitMessages()
                }
                publishGalleryItems(backupMessages)
            }
            triggerBackup()
        }
    }

    private fun readLocalBackupMessages(): List<Message> {
        return try {
            val backupFile = java.io.File(context.filesDir, "gdrive_appdata_cherish_vault_backup.json")
            if (!backupFile.exists()) return emptyList()
            val moshi = com.squareup.moshi.Moshi.Builder().add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
            val adapter = moshi.adapter(com.example.backup.FullAppBackupPayload::class.java)
            adapter.fromJson(backupFile.readText())?.messages.orEmpty()
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed to load local backup for recoverAllMessages", e)
            emptyList()
        }
    }

    /**
     * 6 AM: a new day. Yesterday's chat and media are hidden again, and "today" is listened to from
     * the new 6 AM (the running listener still started from yesterday's).
     */
    fun startNewDay() {
        resetPreviousChats()
        currentActiveConversationId?.let { startGlobalMessagesListener(it, forceRestart = true) }
    }

    fun resetPreviousChats() {
        clearOlderPages()
        localBackupMessages = emptyList()
        resetGalleryCache()
        _isQueryExhaustedFlow.value = false
        _isLoadingMoreFlow.value = false
        _hasPreviousChatsAvailableFlow.value = true
        mergeAndEmitMessages()
    }

    private fun startGlobalMessagesListener(conversationId: String, forceRestart: Boolean = false) {
        if (!forceRestart && currentActiveConversationId == conversationId && globalTodayListener != null) return
        globalTodayListener?.remove()
        clearOlderPages()
        currentActiveConversationId = conversationId
        isInitialTodaySnapshot = true
        val fs = firestore
        if (fs == null) {
            _isLoadingMoreFlow.value = false
            _isQueryExhaustedFlow.value = true
            return
        }

        val startOfToday = getStartOfToday()
        val convRef = fs.collection("conversations")
            .document(conversationId)
            .collection("messages")

        // 1. Listen to ALL of today's messages without pagination (flawless smooth scrolling)
        val todayQuery = convRef
            .whereGreaterThanOrEqualTo("timestamp", startOfToday)
            .orderBy("timestamp", Query.Direction.ASCENDING)

        // Metadata changes too, so my message flips from "sending" to "sent" the moment the server has it
        globalTodayListener = todayQuery.addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                Log.w("ChatRepository", "Listen today messages failed", error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                if (!isInitialTodaySnapshot) {
                    for (change in snapshot.documentChanges) {
                        if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val msg = change.document.toMessageOrNull() ?: continue
                            val currentUserId = authRepository.getCurrentUserId()
                            if (msg.senderId != currentUserId && msg.status != MessageStatus.READ.name) {
                                if (!authRepository.isUserActivelyInChat()) {
                                    val previewText = when (msg.getTypedType()) {
                                        MessageType.TEXT -> msg.text
                                        MessageType.IMAGE -> "Photo"
                                        MessageType.AUDIO -> "Voice message"
                                        MessageType.VIDEO -> "Video"
                                        MessageType.DOCUMENT -> "Document"
                                        else -> "New message"
                                    }
                                    com.example.notifications.NotificationHelper.showMessageNotification(
                                        context = context,
                                        senderName = msg.senderName,
                                        messageText = previewText,
                                        conversationId = conversationId,
                                        messageId = msg.id
                                    )
                                }
                            }
                        }
                    }
                }
                isInitialTodaySnapshot = false

                val currentUserId = authRepository.getCurrentUserId()
                todayMessages = snapshot.documents.mapNotNull { doc ->
                    val msg = doc.toMessageOrNull() ?: return@mapNotNull null
                    // My message that hasn't reached the server yet is still sending (a clock, not a tick)
                    if (doc.metadata.hasPendingWrites() && msg.senderId == currentUserId &&
                        msg.status == MessageStatus.SENT.name
                    ) {
                        msg.status = MessageStatus.SENDING.name
                    }
                    msg
                }
                if (!snapshot.metadata.isFromCache) {
                    markArrivedAsDelivered(convRef, todayMessages, currentUserId)
                }
                mergeAndEmitMessages()
            }
        }

        // 2. Previous chats (before today) are paged in on scroll; only check whether any exist
        convRef.whereLessThan("timestamp", startOfToday)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { prevSnap ->
                val hasPrev = !prevSnap.isEmpty
                _hasPreviousChatsAvailableFlow.value = hasPrev
                if (!hasPrev) {
                    _isQueryExhaustedFlow.value = true
                }
            }
    }

    // Partner messages this phone already marked delivered, so each one costs a single write
    private val deliveredMarkedIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** The partner's messages have reached this phone (straight from the server): tell the sender. */
    private fun markArrivedAsDelivered(messagesRef: CollectionReference, messages: List<Message>, currentUserId: String) {
        val fs = firestore ?: return
        val arrived = messages.filter {
            it.senderId != currentUserId && !it.isDeleted &&
                it.status == MessageStatus.SENT.name && deliveredMarkedIds.add(it.id)
        }
        if (arrived.isEmpty()) return
        val deliveredUpdate = mapOf<String, Any>("status" to MessageStatus.DELIVERED.name)
        arrived.chunked(FIRESTORE_BATCH_LIMIT).forEach { chunk ->
            try {
                val batch = fs.batch()
                chunk.forEach { batch.update(messagesRef.document(it.id), deliveredUpdate) }
                batch.commit().addOnFailureListener { e ->
                    Log.w("ChatRepository", "Failed to mark ${chunk.size} messages delivered", e)
                }
            } catch (e: Exception) {
                Log.w("ChatRepository", "Failed to mark ${chunk.size} messages delivered", e)
            }
        }
    }

    /**
     * Firestore maps Kotlin "isX" booleans to fields named "x" when a whole message is written, while
     * field updates (star, pin, edit) write "isX". Prefer the "isX" field, which carries the latest
     * change, and fall back to the bean-style name, so those flags survive a round trip.
     */
    private fun DocumentSnapshot.toMessageOrNull(): Message? {
        val message = try {
            toObject(Message::class.java)
        } catch (e: Exception) {
            Log.w("ChatRepository", "Skipping unreadable message $id", e)
            null
        } ?: return null
        if (message.id.isBlank()) message.id = id
        message.isStarred = flag("isStarred", "starred") ?: message.isStarred
        message.isPinned = flag("isPinned", "pinned") ?: message.isPinned
        message.isEdited = flag("isEdited", "edited") ?: message.isEdited
        message.isAudioPlayed = flag("isAudioPlayed", "audioPlayed") ?: message.isAudioPlayed
        return message
    }

    private fun DocumentSnapshot.flag(field: String, legacyField: String): Boolean? =
        (get(field) as? Boolean) ?: (get(legacyField) as? Boolean)

    private fun mergeAndEmitMessages() {
        val firestoreMessages = previousMessages + todayMessages
        
        val localBackupSubset = localBackupMessages
            .filter { it.timestamp < getStartOfToday() }
            .sortedByDescending { it.timestamp }
            .take(previousMessageLimit.toInt())
            .toList()

        val combinedMessages = firestoreMessages + localBackupSubset
        val firestoreIds = combinedMessages.map { it.id }.toSet()
        val maxFirestoreTimestamp = combinedMessages.maxOfOrNull { it.timestamp } ?: 0L

        val pendingOptimistic = _messagesFlow.value.filter {
            it.timestamp > maxFirestoreTimestamp && it.id !in firestoreIds
        }

        _messagesFlow.value = (combinedMessages + pendingOptimistic)
            .sortedBy { it.timestamp }
            .distinctBy { it.id }
    }

    /**
     * Returns a flow of messages for the given conversation.
     * Does NOT re-create the Firestore listener — the single global listener
     * set up in init handles all realtime updates. This just bridges to the
     * shared _messagesFlow so callers get the same stream.
     */
    fun listenToMessages(conversationId: String): Flow<List<Message>> {
        return _messagesFlow.asStateFlow()
    }

    suspend fun sendMessage(
        text: String,
        type: MessageType = MessageType.TEXT,
        mediaUrl: String? = null,
        mediaName: String? = null,
        mediaSize: Long = 0L,
        durationSeconds: Int = 0,
        waveform: List<Float> = emptyList(),
        replyTo: Message? = null,
        mediaUrls: List<String> = emptyList(),
        isVideoNote: Boolean = false,
        thumbnailUrls: List<String> = emptyList(),
        effect: String? = null
    ): Result<Message> {
        val currentUser = authRepository.currentUserState.value
        val partner = authRepository.partnerUserState.value
        val convId = getConversationId()
        val messageId = UUID.randomUUID().toString()

        val newMessage = Message(
            id = messageId,
            conversationId = convId,
            senderId = currentUser?.id ?: "user_me",
            senderName = currentUser?.displayName ?: "Me",
            receiverId = partner?.id ?: "user_partner",
            text = text,
            timestamp = System.currentTimeMillis(),
            type = type.name,
            mediaUrl = mediaUrl ?: mediaUrls.firstOrNull(),
            mediaName = mediaName,
            mediaSize = mediaSize,
            durationSeconds = durationSeconds,
            waveform = waveform.map { it.toDouble() },
            status = MessageStatus.SENT.name,
            replyToMessageId = replyTo?.id,
            replyToText = replyTo?.text?.take(80),
            replyToSenderName = replyTo?.senderName,
            mediaUrls = mediaUrls,
            isVideoNote = isVideoNote,
            hasLink = LINK_REGEX.containsMatchIn(text),
            thumbnailUrls = thumbnailUrls,
            effect = effect
        )

        // Optimistically add to local state, sending until the server has it
        _messagesFlow.value = _messagesFlow.value + newMessage.copy(status = MessageStatus.SENDING.name)

        return try {
            val fs = firestore
            if (fs != null) {
                val convRef = fs.collection("conversations").document(convId)
                convRef.collection("messages").document(messageId).set(newMessage)

                // Update conversation summary
                val summary = mapOf(
                    "id" to convId,
                    "lastMessageText" to if (type == MessageType.TEXT) text else "[${type.name.lowercase()}]",
                    "lastMessageSenderId" to newMessage.senderId,
                    "lastMessageTimestamp" to newMessage.timestamp
                )
                convRef.set(summary, com.google.firebase.firestore.SetOptions.merge())
            }
            try {
                com.example.CherishApplication.instance.googleDriveBackupManager.triggerImmediateAutoBackup()
            } catch (_: Exception) {}
            Result.success(newMessage)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error sending message to Firestore", e)
            try {
                com.example.CherishApplication.instance.googleDriveBackupManager.triggerImmediateAutoBackup()
            } catch (_: Exception) {}
            Result.success(newMessage) // preserved locally
        }
    }

    /**
     * Marks the partner's messages as read with a single list update (so the chat and the backup
     * react once, not once per message) and one batched write. A batch fails as a whole when one of
     * its messages no longer exists on the server (e.g. kept only in the local backup), so it then
     * falls back to per-message updates.
     */
    suspend fun markAsRead(messageIds: Collection<String>) {
        val ids = messageIds.toSet()
        if (ids.isEmpty()) return
        val currentUserId = authRepository.getCurrentUserId()
        val readAt = System.currentTimeMillis()
        _messagesFlow.update { list ->
            list.map {
                if (it.id in ids && it.receiverId == currentUserId) {
                    it.copy(status = MessageStatus.READ.name, readTimestamp = readAt)
                } else it
            }
        }

        val fs = firestore ?: return
        val messagesRef = fs.collection("conversations").document(getConversationId()).collection("messages")
        val readUpdate = mapOf<String, Any>("status" to MessageStatus.READ.name, "readTimestamp" to readAt)
        ids.chunked(FIRESTORE_BATCH_LIMIT).forEach { chunk ->
            try {
                val batch = fs.batch()
                chunk.forEach { batch.update(messagesRef.document(it), readUpdate) }
                batch.commit().addOnFailureListener {
                    chunk.forEach { id -> messagesRef.document(id).update(readUpdate) }
                }
            } catch (e: Exception) {
                Log.w("ChatRepository", "Failed to mark ${chunk.size} messages read", e)
            }
        }
    }

    fun markAudioPlayed(messageId: String) {
        _messagesFlow.update { list ->
            list.map {
                if (it.id == messageId) {
                    it.copy(isAudioPlayed = true)
                } else it
            }
        }
        val fs = firestore ?: return
        try {
            val ref = fs.collection("conversations").document(getConversationId()).collection("messages").document(messageId)
            ref.update("isAudioPlayed", true)
        } catch (_: Exception) {}
    }

    suspend fun toggleReaction(messageId: String, emoji: String) {
        val currentUserId = authRepository.getCurrentUserId()
        val currentMessage = _messagesFlow.value.find { it.id == messageId }
            ?: todayMessages.find { it.id == messageId }
            ?: previousMessages.find { it.id == messageId }
            ?: return

        val newReactions = currentMessage.reactions.toMutableMap()
        if (newReactions[currentUserId] == emoji) {
            newReactions.remove(currentUserId)
        } else {
            newReactions[currentUserId] = emoji
        }

        val updated = currentMessage.copy(reactions = newReactions)
        _messagesFlow.value = _messagesFlow.value.map { if (it.id == messageId) updated else it }
        todayMessages = todayMessages.map { if (it.id == messageId) updated else it }
        previousMessages = previousMessages.map { if (it.id == messageId) updated else it }

        try {
            val convId = currentActiveConversationId ?: getConversationId()
            val fs = firestore
            if (fs != null) {
                fs.collection("conversations")
                    .document(convId)
                    .collection("messages")
                    .document(messageId)
                    .set(mapOf("reactions" to newReactions), SetOptions.merge())
                    .await()
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed to update reaction in Firestore for message $messageId", e)
        }
    }

    suspend fun toggleStar(messageId: String) {
        val currentMessage = findMessage(messageId) ?: return
        val newStarred = !currentMessage.isStarred
        updateMessageEverywhere(messageId) { it.copy(isStarred = newStarred) }

        try {
            val convId = getConversationId()
            // Both names, so app versions that still read the bean-style "starred" field agree
            firestore?.collection("conversations")
                ?.document(convId)
                ?.collection("messages")
                ?.document(messageId)
                ?.update(mapOf("isStarred" to newStarred, "starred" to newStarred))
        } catch (e: Exception) {
            // ignore
        }
    }

    suspend fun togglePin(messageId: String) {
        val currentMessage = _messagesFlow.value.find { it.id == messageId } ?: return
        val newPinned = !currentMessage.isPinned
        val updated = currentMessage.copy(isPinned = newPinned)
        _messagesFlow.value = _messagesFlow.value.map { if (it.id == messageId) updated else it }

        try {
            val convId = getConversationId()
            firestore?.collection("conversations")
                ?.document(convId)
                ?.collection("messages")
                ?.document(messageId)
                ?.update("isPinned", newPinned)
        } catch (e: Exception) {
            // ignore
        }
    }

    suspend fun editMessage(messageId: String, newText: String) {
        val currentMessage = _messagesFlow.value.find { it.id == messageId } ?: return
        val hasLink = LINK_REGEX.containsMatchIn(newText)
        val updated = currentMessage.copy(text = newText, isEdited = true, hasLink = hasLink)
        _messagesFlow.value = _messagesFlow.value.map { if (it.id == messageId) updated else it }

        try {
            val convId = getConversationId()
            firestore?.collection("conversations")
                ?.document(convId)
                ?.collection("messages")
                ?.document(messageId)
                ?.update(mapOf("text" to newText, "isEdited" to true, "hasLink" to hasLink))
        } catch (e: Exception) {
            // ignore
        }
        try {
            com.example.CherishApplication.instance.googleDriveBackupManager.triggerImmediateAutoBackup()
        } catch (_: Exception) {}
    }

    suspend fun deleteMessage(messageId: String) {
        deleteMessages(listOf(messageId))
    }

    suspend fun deleteMessages(messageIds: Collection<String>) {
        val ids = messageIds.filter { it.isNotBlank() }.toSet()
        if (ids.isEmpty()) return
        deletedMessageIds.addAll(ids)

        // Remove from every in-memory source at once, so the messages leave chat and gallery immediately
        // and can't be merged back from a stale list before Firestore confirms
        _messagesFlow.update { list -> list.filterNot { it.id in ids } }
        _galleryMediaMessages.update { list -> list.filterNot { it.id in ids } }
        todayMessages = todayMessages.filterNot { it.id in ids }
        previousMessages = previousMessages.filterNot { it.id in ids }
        localBackupMessages = localBackupMessages.filterNot { it.id in ids }

        val fs = firestore
        if (fs != null) {
            val messagesRef = fs.collection("conversations").document(getConversationId()).collection("messages")
            ids.chunked(FIRESTORE_BATCH_LIMIT).forEach { chunk ->
                try {
                    val batch = fs.batch()
                    chunk.forEach { batch.delete(messagesRef.document(it)) }
                    batch.commit().addOnFailureListener { e ->
                        Log.w("ChatRepository", "Failed to delete ${chunk.size} messages", e)
                    }
                } catch (e: Exception) {
                    Log.w("ChatRepository", "Failed to delete ${chunk.size} messages", e)
                }
            }
        }
        triggerBackup()
    }

    /**
     * Removes single photos/videos from their messages. A multi-photo message keeps its remaining
     * photos; a message left without any media is deleted entirely.
     */
    suspend fun removeMediaUrls(messages: List<Message>, urlsToRemove: Set<String>) {
        if (messages.isEmpty() || urlsToRemove.isEmpty()) return
        val emptiedIds = mutableSetOf<String>()
        val trimmedMessages = mutableListOf<Message>()
        messages.distinctBy { it.id }.forEach { original ->
            val message = findMessage(original.id) ?: original
            val currentUrls = message.getAllMediaUrls()
            val keptIndices = currentUrls.indices.filter { currentUrls[it] !in urlsToRemove }
            val remaining = keptIndices.map { currentUrls[it] }
            when {
                remaining.isEmpty() -> emptiedIds += message.id
                remaining.size < currentUrls.size -> {
                    val caption = if (GENERIC_PHOTO_CAPTION.matches(message.text.trim())) {
                        if (remaining.size == 1) "Sent a photo" else "Sent ${remaining.size} photos"
                    } else {
                        message.text
                    }
                    // Previews stay paired with their photos
                    val thumbnails = if (message.thumbnailUrls.size == currentUrls.size) {
                        keptIndices.map { message.thumbnailUrls[it] }
                    } else {
                        emptyList()
                    }
                    trimmedMessages += message.copy(
                        mediaUrl = remaining.first(),
                        mediaUrls = remaining,
                        text = caption,
                        thumbnailUrls = thumbnails
                    )
                }
            }
        }

        trimmedMessages.forEach { trimmed ->
            updateMessageEverywhere(trimmed.id) {
                it.copy(
                    mediaUrl = trimmed.mediaUrl,
                    mediaUrls = trimmed.mediaUrls,
                    text = trimmed.text,
                    thumbnailUrls = trimmed.thumbnailUrls
                )
            }
            try {
                firestore?.collection("conversations")
                    ?.document(getConversationId())
                    ?.collection("messages")
                    ?.document(trimmed.id)
                    ?.update(
                        mapOf(
                            "mediaUrl" to trimmed.mediaUrl,
                            "mediaUrls" to trimmed.mediaUrls,
                            "text" to trimmed.text,
                            "thumbnailUrls" to trimmed.thumbnailUrls
                        )
                    )
                    ?.addOnFailureListener { e -> Log.w("ChatRepository", "Failed to remove photos from ${trimmed.id}", e) }
            } catch (e: Exception) {
                Log.w("ChatRepository", "Failed to remove photos from ${trimmed.id}", e)
            }
        }

        if (emptiedIds.isNotEmpty()) deleteMessages(emptiedIds) else triggerBackup()
    }

    private fun findMessage(messageId: String): Message? =
        _messagesFlow.value.find { it.id == messageId }
            ?: _galleryMediaMessages.value.find { it.id == messageId }
            ?: todayMessages.find { it.id == messageId }
            ?: previousMessages.find { it.id == messageId }

    private fun updateMessageEverywhere(messageId: String, transform: (Message) -> Message) {
        val apply: (List<Message>) -> List<Message> = { list -> list.map { if (it.id == messageId) transform(it) else it } }
        _messagesFlow.update(apply)
        _galleryMediaMessages.update(apply)
        todayMessages = apply(todayMessages)
        previousMessages = apply(previousMessages)
    }

    private fun triggerBackup() {
        try {
            com.example.CherishApplication.instance.googleDriveBackupManager.triggerImmediateAutoBackup()
        } catch (_: Exception) {}
    }

    // --- DUAL-CONSENT CHAT DELETION (Both must accept before chat can be deleted) ---
    fun requestChatDeletion(scope: String = "ALL_MESSAGES", targetMessageId: String? = null) {
        val currentUser = authRepository.currentUserState.value
        val userId = currentUser?.id ?: "current_user"
        val userName = currentUser?.displayName?.ifBlank { "Partner" } ?: "Partner"

        val request = com.example.data.model.ChatDeletionRequest(
            id = UUID.randomUUID().toString(),
            requestedByUserId = userId,
            requestedByUserName = userName,
            timestamp = System.currentTimeMillis(),
            scope = scope,
            targetMessageId = targetMessageId,
            status = com.example.data.model.DeletionRequestStatus.PENDING.name
        )
        _deletionRequestFlow.value = request
    }

    suspend fun acceptChatDeletion() {
        val request = _deletionRequestFlow.value ?: return
        if (request.scope == "ALL_MESSAGES") {
            // Clear entire conversation history with mutual consent
            _messagesFlow.value = listOf(
                Message(
                    id = "mutual_clear_${System.currentTimeMillis()}",
                    conversationId = getConversationId(),
                    senderId = "system",
                    senderName = "Cherish Vault",
                    receiverId = "both",
                    text = "🔒 Chat history cleared with mutual consent from both partners.",
                    timestamp = System.currentTimeMillis(),
                    type = MessageType.TEXT.name
                )
            )
        } else if (request.targetMessageId != null) {
            deleteMessage(request.targetMessageId)
        }
        _deletionRequestFlow.value = null
    }

    fun declineChatDeletion() {
        _deletionRequestFlow.value = null
    }

    fun cancelChatDeletion() {
        _deletionRequestFlow.value = null
    }

    fun getStarredMessages(): List<Message> {
        return _messagesFlow.value.filter { it.isStarred && !it.isDeleted }
    }

    fun getMediaMessages(): List<Message> {
        return _messagesFlow.value.filter {
            !it.isDeleted && (it.getTypedType() == MessageType.IMAGE || it.getTypedType() == MessageType.VIDEO || it.getTypedType() == MessageType.AUDIO)
        }
    }

    suspend fun restoreMessages(messages: List<Message>) {
        if (messages.isEmpty()) return
        val current = _messagesFlow.value.toMutableList()
        val existingIds = current.map { it.id }.toSet()
        val toAdd = messages.filter { it.id !in existingIds }
        if (toAdd.isNotEmpty()) {
            current.addAll(toAdd)
            _messagesFlow.value = current.sortedBy { it.timestamp }
            // Intentionally not uploading to Firestore to ensure recovered data is local and does not show to the partner
        }
    }

    private fun getSampleStarterMessages(): List<Message> {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - 3600000
        val thirtyMinAgo = now - 1800000
        val tenMinAgo = now - 600000
        val convId = "couple_cherish_private"

        return listOf(
            Message(
                id = "m1",
                conversationId = convId,
                senderId = "partner",
                senderName = "My Love",
                receiverId = "user_me",
                text = "Hey sweetheart! Hope you're having an amazing day ❤️",
                timestamp = oneHourAgo,
                type = MessageType.TEXT.name,
                status = MessageStatus.READ.name,
                reactions = mapOf("user_me" to "❤️")
            ),
            Message(
                id = "m2",
                conversationId = convId,
                senderId = "user_me",
                senderName = "Me",
                receiverId = "partner",
                text = "Thinking of you makes everything better 🥰 Cannot wait to see you tonight!",
                timestamp = thirtyMinAgo,
                type = MessageType.TEXT.name,
                status = MessageStatus.READ.name,
                reactions = mapOf("partner" to "🥰")
            ),
            Message(
                id = "m3",
                conversationId = convId,
                senderId = "partner",
                senderName = "My Love",
                receiverId = "user_me",
                text = "I'm cooking that pasta you love 🍝 See you soon my heart!",
                timestamp = tenMinAgo,
                type = MessageType.TEXT.name,
                status = MessageStatus.DELIVERED.name,
                isStarred = true
            )
        )
    }

    private companion object {
        const val OLDER_PAGE_SIZE = 60L
        const val SEARCH_OLDER_TARGET = 500L
        const val GALLERY_SCAN_PAGE_SIZE = 500L
        const val RANGE_PAGE_SIZE = 500L
        /** The gallery is fully re-read from the server this often; otherwise only new items. */
        const val GALLERY_FULL_SYNC_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
        /** New-items check reaches a little before the last one, for clock or write delays. */
        const val GALLERY_SYNC_OVERLAP_MS = 60L * 60 * 1000
        const val GALLERY_PUBLISH_INTERVAL_MS = 300L
        const val FIRESTORE_BATCH_LIMIT = 450
        val LINK_REGEX = Regex("""(https?://\S+)|(www\.\S+)""", RegexOption.IGNORE_CASE)
        val GENERIC_PHOTO_CAPTION = Regex("""Sent (a photo|\d+ photos?)""", RegexOption.IGNORE_CASE)
    }
}

