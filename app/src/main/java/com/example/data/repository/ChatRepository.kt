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
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import okio.buffer
import okio.sink
import okio.source

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
    private val todayMessagesMap = LinkedHashMap<String, Message>()
    @Volatile private var hasAppBeenOpened: Boolean = false
    @Volatile private var pendingConversationId: String? = null
    private var previousMessages: List<Message> = emptyList()
    private var localBackupMessages: List<Message> = emptyList()
    private var sortedLocalBackupDescending: List<Message> = emptyList()

    // Older chat shown from the phone's own archive (see ChatArchive), newest first, instead of
    // reading it from Firestore again. Exhausted: all of the archive's complete stretch is shown,
    // so anything older comes from Firestore pages
    private val archive by lazy { com.example.data.local.ChatArchive.get(context) }
    private var archivedMessages: List<Message> = emptyList()
    private var archiveExhausted = false
    private var archiveTodayJob: Job? = null
    private val archiveRangeLock = kotlinx.coroutines.sync.Mutex()

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

    private val chatCacheFile by lazy {
        java.io.File(context.filesDir, "cherish_recent_chat_cache.json")
    }

    private val chatCacheAdapter by lazy {
        val moshi = com.squareup.moshi.Moshi.Builder().build()
        val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, Message::class.java)
        moshi.adapter<List<Message>>(listType)
    }

    private fun loadRecentCachedMessages(): List<Message> {
        return try {
            if (!chatCacheFile.exists()) return emptyList()
            val list = chatCacheFile.source().buffer().use { source ->
                chatCacheAdapter.fromJson(source).orEmpty()
            }
            val now = System.currentTimeMillis()
            list.map { msg ->
                if (msg.status == MessageStatus.SENDING.name && now - msg.timestamp > 60_000L) {
                    msg.copy(status = MessageStatus.SENT.name)
                } else msg
            }
        } catch (e: Exception) {
            Log.w("ChatRepository", "Failed to load recent cached messages", e)
            emptyList()
        }
    }

    private var cacheSaveJob: kotlinx.coroutines.Job? = null

    private fun saveRecentCachedMessages(messages: List<Message>) {
        if (messages.isEmpty()) return
        cacheSaveJob?.cancel()
        cacheSaveJob = repoScope.launch {
            kotlinx.coroutines.delay(1500L) // Debounce frequent updates (ticks, reactions, status)
            try {
                // Keep the most recent 200 messages for instantaneous cold start
                val recent = if (messages.size > 200) messages.takeLast(200) else messages
                val tempFile = java.io.File(context.filesDir, "cherish_recent_chat_cache.tmp")
                tempFile.sink().buffer().use { sink ->
                    chatCacheAdapter.toJson(sink, recent)
                }
                if (tempFile.exists()) {
                    tempFile.renameTo(chatCacheFile)
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.w("ChatRepository", "Failed to save recent cached messages", e)
                }
            }
        }
    }

    init {
        // Load recent messages from local cache on background thread so cold start / push doesn't block main thread
        repoScope.launch {
            val initialCached = loadRecentCachedMessages()
            if (initialCached.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    if (_messagesFlow.value.isEmpty()) {
                        _messagesFlow.value = initialCached
                    }
                }
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            authRepository.currentUserState
                .map { it?.coupleId?.trim()?.ifBlank { null } }
                .distinctUntilChanged()
                .collectLatest { convId ->
                    // Listener state is only touched on the main thread, like the listener callbacks
                    withContext(Dispatchers.Main) {
                        clearOlderPages()
                        todayMessages = emptyList()
                        todayMessagesMap.clear()
                        localBackupMessages = emptyList()
                        _isQueryExhaustedFlow.value = false
                        _isLoadingMoreFlow.value = false
                        _hasPreviousChatsAvailableFlow.value = convId != null
                        resetGalleryCache()
                        pendingConversationId = convId
                        val isForeground = com.example.CherishApplication.isAppInForeground
                        if (convId != null && (isForeground || hasAppBeenOpened)) {
                            startGlobalMessagesListener(convId)
                        } else if (convId == null) {
                            stopGlobalMessagesListener()
                        }
                    }
                }
        }
    }

    fun ensureGlobalMessagesListener(forceRestart: Boolean = false) {
        hasAppBeenOpened = true
        val convId = pendingConversationId ?: getConversationId()
        if (convId.isNotBlank()) {
            startGlobalMessagesListener(convId, forceRestart = forceRestart)
        }
    }

    fun getConversationId(): String {
        val user = authRepository.currentUserState.value
        val coupleId = user?.coupleId?.trim()?.ifBlank { null }
        if (!coupleId.isNullOrBlank()) return coupleId
        return authRepository.getSavedCoupleId()
    }

    fun stopGlobalMessagesListener() {
        globalTodayListener?.remove()
        globalTodayListener = null
        currentActiveConversationId = null
        clearOlderPages()
        todayMessages = emptyList()
        todayMessagesMap.clear()
        localBackupMessages = emptyList()
        sortedLocalBackupDescending = emptyList()
        _messagesFlow.value = emptyList()
        resetGalleryCache()
    }

    fun loadMoreMessages() {
        // Older chat this phone already has comes straight from its archive, with no download
        if (loadMoreFromArchive()) return
        val hasMoreBackup = sortedLocalBackupDescending.isNotEmpty() && previousMessageLimit < sortedLocalBackupDescending.size
        if (_isQueryExhaustedFlow.value && hasMoreBackup) {
            previousMessageLimit += OLDER_PAGE_SIZE
            mergeAndEmitMessages()
            _isQueryExhaustedFlow.value = previousMessageLimit >= sortedLocalBackupDescending.size
            return
        }
        loadOlderPage(OLDER_PAGE_SIZE)
    }

    /**
     * Every message sent from [from] (inclusive) until [until] (exclusive), oldest first (for the
     * monthly recap): from the phone's archive when it holds all of that stretch, else read in pages
     * from Firestore (and then kept in the archive). Null when it couldn't be read.
     */
    suspend fun loadMessagesBetween(from: Long, until: Long): List<Message>? {
        val conversationId = currentActiveConversationId ?: getConversationId()
        val range = withContext(Dispatchers.IO) { archive.completeRange(conversationId) }
        if (range != null && from >= range.from && until <= range.until) {
            return withContext(Dispatchers.IO) { archive.between(conversationId, from, until).asReversed() }
        }
        val fs = firestore ?: return null
        val messagesRef = fs.collection("conversations")
            .document(conversationId)
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
            withContext(Dispatchers.IO) { archive.save(conversationId, all) }
            addCompleteStretch(conversationId, com.example.data.local.ChatArchive.Range(from, until))
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
        val hasMoreBackup = sortedLocalBackupDescending.isNotEmpty() && previousMessageLimit < sortedLocalBackupDescending.size
        if (pageSize <= 0L || (_isQueryExhaustedFlow.value && !hasMoreBackup) || _isLoadingMoreFlow.value) return
        val fs = firestore
        if (fs == null) {
            if (hasMoreBackup) {
                previousMessageLimit += pageSize
                mergeAndEmitMessages()
                _isQueryExhaustedFlow.value = previousMessageLimit >= sortedLocalBackupDescending.size
            } else {
                _isQueryExhaustedFlow.value = true
            }
            return
        }
        val conversationId = currentActiveConversationId ?: getConversationId()
        // Below the older pages already loaded, else below what the archive showed, else today
        val upperBound = olderPageOldestTimestamps.lastOrNull()
            ?: archivedMessages.lastOrNull()?.timestamp
            ?: getStartOfToday()
        val pageIndex = olderPages.size
        val generation = olderPagesGeneration
        olderPages.add(emptyList())
        previousMessageLimit += pageSize
        _isLoadingMoreFlow.value = true

        // Read once, not listened to: an older page hardly changes, and a live listener read it all
        // from the server again every time the chat reconnected (it's kept in the archive instead)
        fs.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .whereLessThan("timestamp", upperBound)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(pageSize)
            .get()
            .addOnFailureListener { error ->
                if (generation != olderPagesGeneration) return@addOnFailureListener
                Log.w("ChatRepository", "Couldn't read older messages", error)
                if (pageIndex == olderPages.lastIndex) _isLoadingMoreFlow.value = false
            }
            .addOnSuccessListener { snapshot ->
                if (generation != olderPagesGeneration) return@addOnSuccessListener
                val isNewestPage = pageIndex == olderPages.lastIndex
                val docs = snapshot.documents
                if (isNewestPage) {
                    // The newest page decides where the next one starts and whether history has ended
                    val oldest = docs.lastOrNull()?.getLong("timestamp") ?: upperBound
                    if (olderPageOldestTimestamps.size > pageIndex) {
                        olderPageOldestTimestamps[pageIndex] = oldest
                    } else {
                        olderPageOldestTimestamps.add(oldest)
                    }
                    val moreBackup = sortedLocalBackupDescending.isNotEmpty() && previousMessageLimit < sortedLocalBackupDescending.size
                    _isQueryExhaustedFlow.value = (docs.size < pageSize) && !moreBackup
                    if (pageIndex == 0) {
                        _hasPreviousChatsAvailableFlow.value = docs.isNotEmpty() ||
                            sortedLocalBackupDescending.isNotEmpty() || archivedMessages.isNotEmpty()
                    }
                    _isLoadingMoreFlow.value = false
                    // From the server: the archive now holds every message down to this page's oldest
                    if (!snapshot.metadata.isFromCache) {
                        growArchiveRange(conversationId, upperBound, docs.lastOrNull()?.getLong("timestamp"), reachedStart = docs.size < pageSize)
                    }
                }
                val pageMessages = docs.mapNotNull { it.toMessageOrNull() }
                olderPages[pageIndex] = pageMessages
                // Kept on the phone, so next time this stretch shows without a download
                if (pageMessages.isNotEmpty()) repoScope.launch { archive.save(conversationId, pageMessages) }
                previousMessages = olderPages.flatten()
                // Coalesce the merge+sort, so pages arriving close together emit once
                scheduleMerge()
                if (isNewestPage && pendingOlderLoad > 0L) {
                    val more = pendingOlderLoad
                    pendingOlderLoad = 0L
                    loadOlderPage(more)
                }
            }
    }

    /**
     * The next page of older chat from the phone's archive (from its complete stretch, see
     * ChatArchive). The first one catches the archive up with the days it hasn't got (only those
     * are downloaded). False when the archive has nothing more to show: Firestore pages from there.
     */
    private fun loadMoreFromArchive(): Boolean {
        if (archiveExhausted || olderPages.isNotEmpty()) return false
        if (_isLoadingMoreFlow.value) return true
        val conversationId = currentActiveConversationId ?: getConversationId()
        if (conversationId.isBlank()) return false
        val generation = olderPagesGeneration
        val startOfToday = getStartOfToday()
        val top = archivedMessages.lastOrNull()?.timestamp ?: startOfToday
        val isFirstPage = archivedMessages.isEmpty()
        _isLoadingMoreFlow.value = true
        repoScope.launch {
            if (isFirstPage) catchUpArchive(conversationId, startOfToday)
            val range = archive.completeRange(conversationId)
            val usable = range != null && range.until >= startOfToday && top > range.from
            val page = if (range != null && usable) {
                archive.before(conversationId, top, ARCHIVE_PAGE_SIZE).filter { it.timestamp >= range.from }
            } else emptyList()
            withContext(Dispatchers.Main) {
                _isLoadingMoreFlow.value = false
                // Reset meanwhile (a new day, another chat): this page belongs to the old one
                if (generation != olderPagesGeneration) return@withContext
                val shown = page.filter { it.id !in deletedMessageIds }
                if (shown.isNotEmpty()) {
                    archivedMessages = archivedMessages + shown
                    _hasPreviousChatsAvailableFlow.value = true
                    _isQueryExhaustedFlow.value = false
                    mergeAndEmitMessages()
                }
                if (page.size < ARCHIVE_PAGE_SIZE) {
                    archiveExhausted = true
                    if (range != null && usable && range.from <= 0L) {
                        // The archive reaches the chat's very first message: nothing older anywhere
                        _isQueryExhaustedFlow.value = previousMessageLimit >= sortedLocalBackupDescending.size
                    } else if (shown.isEmpty()) {
                        // Nothing from the archive this time: on with Firestore pages straight away
                        loadMoreMessages()
                    }
                }
            }
        }
        return true
    }

    /**
     * Brings the archive's complete stretch up to today: only what was sent since it was last
     * complete is downloaded (a few days' messages, usually). Nothing while it has no stretch yet.
     */
    private suspend fun catchUpArchive(conversationId: String, startOfToday: Long) {
        val range = archive.completeRange(conversationId) ?: return
        if (range.until >= startOfToday) return
        val fs = firestore ?: return
        val messagesRef = fs.collection("conversations").document(conversationId).collection("messages")
        try {
            var lastDoc: DocumentSnapshot? = null
            while (true) {
                var page = messagesRef
                    .whereGreaterThanOrEqualTo("timestamp", range.until - ARCHIVE_SYNC_OVERLAP_MS)
                    .whereLessThan("timestamp", startOfToday)
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .limit(RANGE_PAGE_SIZE)
                lastDoc?.let { page = page.startAfter(it) }
                // From the server: a cache-only answer could be missing messages
                val docs = page.get(Source.SERVER).await().documents
                archive.save(conversationId, docs.mapNotNull { it.toMessageOrNull() })
                if (docs.size < RANGE_PAGE_SIZE) break
                lastDoc = docs.last()
            }
            archiveRangeLock.withLock {
                // Re-read: an older page may have stretched it further back meanwhile
                val now = archive.completeRange(conversationId) ?: range
                archive.setCompleteRange(
                    conversationId,
                    com.example.data.local.ChatArchive.Range(minOf(now.from, range.from), startOfToday)
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ChatRepository", "Couldn't catch the chat archive up", e)
        }
    }

    /**
     * An older page came from the server: every message from its oldest one up to [pageTop] is in
     * the archive now, so its complete stretch reaches back that far (where the two meet).
     */
    private fun growArchiveRange(conversationId: String, pageTop: Long, pageBottom: Long?, reachedStart: Boolean) {
        val from = when {
            reachedStart -> 0L
            // Other messages sent at the very same time as the oldest may not have fit in the page
            pageBottom != null -> pageBottom + 1
            else -> return
        }
        addCompleteStretch(conversationId, com.example.data.local.ChatArchive.Range(from, pageTop))
    }

    /**
     * Every message in [stretch] is in the archive now (read whole from the server): joined to its
     * complete stretch where the two meet; a newer one that doesn't meet it takes its place.
     */
    private fun addCompleteStretch(conversationId: String, stretch: com.example.data.local.ChatArchive.Range) {
        if (conversationId.isBlank() || stretch.until <= stretch.from) return
        repoScope.launch {
            archiveRangeLock.withLock {
                val range = archive.completeRange(conversationId)
                val joined = when {
                    range == null -> stretch
                    stretch.from <= range.until && stretch.until >= range.from ->
                        com.example.data.local.ChatArchive.Range(minOf(range.from, stretch.from), maxOf(range.until, stretch.until))
                    stretch.until > range.until -> stretch
                    else -> range
                }
                if (joined != range) archive.setCompleteRange(conversationId, joined)
            }
        }
    }

    /** Today's messages go into the archive too: a moment after they last changed, not on every tick. */
    private fun archiveTodaySoon(conversationId: String, messages: List<Message>) {
        if (messages.isEmpty()) return
        archiveTodayJob?.cancel()
        archiveTodayJob = repoScope.launch {
            kotlinx.coroutines.delay(ARCHIVE_TODAY_DELAY_MS)
            archive.save(conversationId, messages.filter { it.status != MessageStatus.SENDING.name })
        }
    }

    /**
     * "Show in chat" for an older message sent at [sentAt]: all the chat down to it from the archive
     * in one go, with no download. [onDone] gets false when the archive doesn't reach that far, or
     * Firestore pages are already showing; then pages load the usual way.
     */
    fun showArchivedDownTo(sentAt: Long, onDone: (Boolean) -> Unit) {
        val conversationId = currentActiveConversationId ?: getConversationId()
        val startOfToday = getStartOfToday()
        val top = archivedMessages.lastOrNull()?.timestamp ?: startOfToday
        if (sentAt >= top) {
            onDone(true)
            return
        }
        if (conversationId.isBlank() || olderPages.isNotEmpty() || _isLoadingMoreFlow.value) {
            onDone(false)
            return
        }
        val generation = olderPagesGeneration
        val isFirstPage = archivedMessages.isEmpty()
        _isLoadingMoreFlow.value = true
        repoScope.launch {
            if (isFirstPage) catchUpArchive(conversationId, startOfToday)
            val range = archive.completeRange(conversationId)
            val reaches = range != null && range.until >= startOfToday && sentAt >= range.from
            val stretch = if (reaches) archive.between(conversationId, sentAt, top) else emptyList()
            withContext(Dispatchers.Main) {
                _isLoadingMoreFlow.value = false
                if (generation != olderPagesGeneration || !reaches) {
                    onDone(false)
                    return@withContext
                }
                val shown = stretch.filter { it.id !in deletedMessageIds }
                if (shown.isNotEmpty()) {
                    archivedMessages = archivedMessages + shown
                    _hasPreviousChatsAvailableFlow.value = true
                    _isQueryExhaustedFlow.value = false
                    mergeAndEmitMessages()
                }
                onDone(true)
            }
        }
    }

    private fun clearOlderPages() {
        olderPagesGeneration++
        mergeHandler.removeCallbacks(mergeRunnable)
        mergeScheduled = false
        olderPageListeners.forEach { it.remove() }
        olderPageListeners.clear()
        olderPages.clear()
        olderPageOldestTimestamps.clear()
        pendingOlderLoad = 0L
        previousMessageLimit = 0L
        previousMessages = emptyList()
        archivedMessages = emptyList()
        archiveExhausted = false
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
            // what's new since the last check is fetched (the whole gallery only the first time on
            // this install: re-reading it every week cost thousands of reads)
            try {
                loadGalleryFrom(messagesRef, Source.CACHE, convId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ChatRepository", "Gallery load from cache failed", e)
            }
            try {
                val lastSync = galleryPrefs.getLong("gallery_synced_$convId", 0L)
                val syncStartedAt = System.currentTimeMillis()
                if (lastSync > 0L) {
                    val since = lastSync - GALLERY_SYNC_OVERLAP_MS
                    val newer = messagesRef
                        .whereGreaterThan("timestamp", since)
                        .get(Source.SERVER).await()
                        .documents.mapNotNull { it.toMessageOrNull() }
                    publishGalleryItems(newer)
                    // Every message since then: into the archive, whose complete stretch reaches that far now
                    archive.save(convId, newer)
                    addCompleteStretch(convId, com.example.data.local.ChatArchive.Range(since + 1, syncStartedAt))
                } else {
                    loadGalleryFrom(messagesRef, Source.SERVER, convId)
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
        // Photos, videos, voice notes, call recordings, starred and link messages can all be queried directly
        val targetedQueries = listOf(
            messagesRef.whereIn(
                "type",
                listOf(MessageType.IMAGE.name, MessageType.VIDEO.name, MessageType.AUDIO.name, MessageType.CALL_RECORDING.name)
            ),
            messagesRef.whereEqualTo("isStarred", true),
            messagesRef.whereEqualTo("hasLink", true)
        )
        val targeted = targetedQueries
            .map { query -> async { query.get(source).await().documents.mapNotNull { it.toMessageOrNull() } } }
            .awaitAll()
            .flatten()
        publishGalleryItems(targeted)

        // Messages from before the hasLink flag existed: page through the history once (newest first,
        // publishing as pages arrive) and tag the link messages, so from then on the query above finds
        // them without a scan. Once one phone has done it, the chat's own document says so, and no
        // phone reads the whole history for it again (not after a reinstall either)
        if (isLinkBackfillDone(conversationId)) return@coroutineScope
        if (source == Source.SERVER && linksTaggedForChat(messagesRef)) {
            markLinkBackfillDone(conversationId)
            return@coroutineScope
        }
        val scanStartedAt = System.currentTimeMillis()
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
                // Read from the server anyway: kept in the archive, so Recover All needn't read it again
                if (source == Source.SERVER) archive.save(conversationId, pending.toList())
                pending.clear()
                lastPublishAt = now
            }
            if (isLastPage) break
            lastDoc = docs.last()
        }
        if (source == Source.SERVER) {
            tagLinkMessages(messagesRef, untaggedLinkIds)
            markLinkBackfillDone(conversationId)
            // The whole history came by: the archive holds every message up to when this started
            addCompleteStretch(conversationId, com.example.data.local.ChatArchive.Range(0L, scanStartedAt))
            try {
                messagesRef.parent?.set(mapOf("linksTagged" to true), SetOptions.merge())
            } catch (e: Exception) {
                Log.w("ChatRepository", "Couldn't note the link tagging on the chat", e)
            }
        }
    }

    /** The chat's own document says its link messages were tagged already (one read, not the whole history). */
    private suspend fun linksTaggedForChat(messagesRef: CollectionReference): Boolean = try {
        messagesRef.parent?.get(Source.SERVER)?.await()?.getBoolean("linksTagged") == true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
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
            MessageType.IMAGE, MessageType.VIDEO, MessageType.AUDIO, MessageType.CALL_RECORDING -> true
            else -> message.isStarred || message.isVideoNote ||
                !message.mediaUrl.isNullOrBlank() || message.mediaUrls.isNotEmpty() ||
                !message.callId.isNullOrBlank() ||
                message.text.contains("voice call", ignoreCase = true) ||
                message.text.contains("call declined", ignoreCase = true) ||
                message.text.contains("cancelled call", ignoreCase = true) ||
                message.text.contains("missed call", ignoreCase = true) ||
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

    fun recordCallCompleted(message: Message) {
        publishGalleryItems(listOf(message))
        _messagesFlow.update { current ->
            (listOf(message) + current).distinctBy { it.id }.sortedBy { it.timestamp }
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

        // Chat: older history comes back as the user scrolls up: from the phone's archive at once
        // where it has it (after downloading only the days it's missing), else from Firestore
        _hasPreviousChatsAvailableFlow.value = true
        if (olderPages.isEmpty() && archivedMessages.isEmpty()) {
            _isQueryExhaustedFlow.value = false
            archiveExhausted = false
            loadMoreMessages()
        }
        mergeAndEmitMessages()

        // The local backup stays on this device (never uploaded to the partner); read it off the main thread
        repoScope.launch {
            val backupMessages = readLocalBackupMessages().filter { it.id !in deletedMessageIds }
            if (backupMessages.isNotEmpty()) {
                val startOfToday = getStartOfToday()
                val sortedDescending = backupMessages
                    .filter { it.timestamp < startOfToday }
                    .sortedByDescending { it.timestamp }
                withContext(Dispatchers.Main) {
                    localBackupMessages = backupMessages
                    sortedLocalBackupDescending = sortedDescending
                    if (previousMessageLimit < OLDER_PAGE_SIZE) {
                        previousMessageLimit = OLDER_PAGE_SIZE
                    }
                    _hasPreviousChatsAvailableFlow.value = true
                    _isQueryExhaustedFlow.value = false
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
            val moshi = com.squareup.moshi.Moshi.Builder().build()
            val adapter = moshi.adapter(com.example.backup.FullAppBackupPayload::class.java)
            backupFile.source().buffer().use { source ->
                adapter.fromJson(source)?.messages.orEmpty()
            }
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
        sortedLocalBackupDescending = emptyList()
        resetGalleryCache()
        _isQueryExhaustedFlow.value = false
        _isLoadingMoreFlow.value = false
        _hasPreviousChatsAvailableFlow.value = true
        mergeAndEmitMessages()
    }

    private fun startGlobalMessagesListener(conversationId: String, forceRestart: Boolean = false) {
        if (conversationId.isBlank()) {
            stopGlobalMessagesListener()
            return
        }
        if (!forceRestart && currentActiveConversationId == conversationId && globalTodayListener != null) return
        globalTodayListener?.remove()
        clearOlderPages()
        todayMessagesMap.clear()
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
                val currentUserId = authRepository.getCurrentUserId()

                // Only the documents that changed are read again (a send, a tick, a reaction): the
                // rest keep the Message they had, so their rows aren't rebuilt and this stays quick
                // on the main thread however long today's chat is
                if (isInitialTodaySnapshot) todayMapped.clear()
                for (change in snapshot.documentChanges) {
                    val doc = change.document
                    val mapped = if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) null
                        else doc.toMessageOrNull()
                    if (mapped == null) todayMapped.remove(doc.id) else todayMapped[doc.id] = mapped
                }

                if (!isInitialTodaySnapshot) {
                    for (change in snapshot.documentChanges) {
                        if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val msg = todayMapped[change.document.id] ?: continue
                            // A call recording isn't a message from the partner: no notification for it
                            if (msg.getTypedType() == MessageType.CALL_RECORDING) continue
                            if (msg.senderId != currentUserId && msg.status != MessageStatus.READ.name) {
                                if (!com.example.notifications.NotificationHelper.isAppOpenOnScreen(context)) {
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

                todayMessages = snapshot.documents.mapNotNull { doc ->
                    val msg = todayMapped[doc.id] ?: doc.toMessageOrNull()?.also { todayMapped[doc.id] = it }
                        ?: return@mapNotNull null
                    if (doc.metadata.hasPendingWrites() && msg.senderId == currentUserId &&
                        msg.status == MessageStatus.SENT.name &&
                        !acknowledgedSentIds.contains(msg.id)
                    ) {
                        msg.copy(status = MessageStatus.SENDING.name)
                    } else {
                        msg
                    }
                }
                if (!snapshot.metadata.isFromCache) {
                    markArrivedAsDelivered(convRef, todayMessages, currentUserId)
                }
                archiveTodaySoon(conversationId, todayMessages)
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

    // Today's messages as last read, by document id (see the today listener)
    private val todayMapped = HashMap<String, Message>()

    // Partner messages this phone already marked delivered, so each one costs a single write
    private val deliveredMarkedIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    // Messages this phone confirmed reached the server so pending write cache won't revert them to sending
    private val acknowledgedSentIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Called when the partner opens the Cherish app to deliver all pending received messages.
     * Double tick only appears when partner user actually opens Cherish.
     */
    fun markPendingDelivered() {
        val convId = currentActiveConversationId ?: return
        val fs = firestore ?: return
        val currentUserId = authRepository.getCurrentUserId()
        if (currentUserId.isBlank()) return
        val convRef = fs.collection("conversations").document(convId).collection("messages")
        markArrivedAsDelivered(convRef, todayMessages + previousMessages, currentUserId)
    }

    /** The partner's messages have reached this phone (straight from the server): tell the sender only if app is open. */
    private fun markArrivedAsDelivered(messagesRef: CollectionReference, messages: List<Message>, currentUserId: String) {
        // Double tick should ONLY appear when the partner user actively opens the Cherish app
        // (Must be in foreground AND secret Cherish app revealed, NOT disguised as Notes)
        val app = context.applicationContext as? com.example.CherishApplication
        val isCherishAppOpen = com.example.CherishApplication.isAppInForeground &&
            app?.securityPreferences?.isDisguiseActive() == false
        if (!isCherishAppOpen) return
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
        val kind = message.getTypedType()
        // Only voice, video and call messages have a length to find
        if (message.durationSeconds <= 0 && kind != MessageType.TEXT && kind != MessageType.IMAGE) {
            val num = (get("durationSeconds") as? Number)?.toInt()
                ?: (get("duration") as? Number)?.toInt()
            if (num != null && num > 0) {
                message.durationSeconds = num
            } else {
                val parsed = DURATION_IN_TEXT.find(message.text)?.groupValues?.get(1)?.toIntOrNull()
                if (parsed != null && parsed > 0) {
                    message.durationSeconds = parsed
                } else if (message.waveform.isNotEmpty()) {
                    message.durationSeconds = (message.waveform.size / 10).coerceAtLeast(1)
                }
            }
        }
        if (message.durationSeconds > 0) {
            com.example.audio.VoicePlayerHelper.recordTrackDuration(message.id, message.durationSeconds)
        }
        message.isStarred = flag("isStarred", "starred") ?: message.isStarred
        message.isPinned = flag("isPinned", "pinned") ?: message.isPinned
        message.isEdited = flag("isEdited", "edited") ?: message.isEdited
        message.isAudioPlayed = flag("isAudioPlayed", "audioPlayed") ?: message.isAudioPlayed
        message.isYouTubePlayed = flag("isYouTubePlayed", "youTubePlayed") ?: message.isYouTubePlayed
        return message
    }

    private fun DocumentSnapshot.flag(field: String, legacyField: String): Boolean? =
        (get(field) as? Boolean) ?: (get(legacyField) as? Boolean)

    // Coalesces bursts of merge requests (many live older-page listeners firing) into one emit.
    private val mergeHandler = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var mergeScheduled = false
    private val mergeRunnable = Runnable {
        mergeScheduled = false
        mergeAndEmitMessages()
    }

    private fun scheduleMerge() {
        if (mergeScheduled) return
        mergeScheduled = true
        // ~2 frames: collapses a load's worth of page callbacks without a visible delay
        mergeHandler.postDelayed(mergeRunnable, 32L)
    }

    private fun mergeAndEmitMessages() {
        mergeScheduled = false
        mergeHandler.removeCallbacks(mergeRunnable)
        // Firestore's copies come last, so they win over the archive's
        val firestoreMessages = archivedMessages + previousMessages + todayMessages
        
        val combinedMessages = if (sortedLocalBackupDescending.isEmpty()) {
            firestoreMessages
        } else {
            val limit = if (previousMessageLimit > 0) previousMessageLimit.toInt() else OLDER_PAGE_SIZE.toInt()
            val localBackupSubset = sortedLocalBackupDescending.take(limit)
            firestoreMessages + localBackupSubset
        }

        if (combinedMessages.isEmpty()) {
            _messagesFlow.value = emptyList()
            return
        }

        val existingIds = HashSet<String>(combinedMessages.size)
        var maxFirestoreTimestamp = 0L
        for (m in combinedMessages) {
            existingIds.add(m.id)
            if (m.timestamp > maxFirestoreTimestamp) {
                maxFirestoreTimestamp = m.timestamp
            }
        }

        val currentMessages = _messagesFlow.value
        val pendingOptimistic = if (currentMessages.isNotEmpty()) {
            currentMessages.filter { it.timestamp > maxFirestoreTimestamp && it.id !in existingIds }
        } else emptyList()

        val map = LinkedHashMap<String, Message>(combinedMessages.size + pendingOptimistic.size)
        for (m in combinedMessages) {
            map[m.id] = m
        }
        for (m in pendingOptimistic) {
            map[m.id] = m
        }

        val sortedList = map.values.sortedBy { it.timestamp }
        _messagesFlow.value = sortedList
        saveRecentCachedMessages(sortedList)
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

        val resolvedReceiverId = partner?.id?.takeIf { it.isNotBlank() }
            ?: currentUser?.partnerId?.takeIf { it.isNotBlank() }
            ?: "user_partner"

        val newMessage = Message(
            id = messageId,
            conversationId = convId,
            senderId = currentUser?.id ?: "user_me",
            senderName = currentUser?.displayName ?: "Me",
            receiverId = resolvedReceiverId,
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
            val a = com.google.firebase.auth.FirebaseAuth.getInstance()
            if (a.currentUser == null) {
                try {
                    kotlinx.coroutines.withTimeoutOrNull(4000L) {
                        a.signInAnonymously().await()
                    }
                } catch (e: Exception) {
                    Log.w("ChatRepository", "Anonymous sign-in before send failed: ${e.message}")
                }
            }

            val fs = firestore
            if (fs != null && convId.isNotBlank()) {
                val convRef = fs.collection("conversations").document(convId)
                val write = convRef.collection("messages").document(messageId).set(newMessage)

                // Write is queued in local offline database; acknowledge it so UI flips to single tick
                acknowledgedSentIds.add(messageId)

                // The partner's phone hears about it once it's saved
                com.example.notifications.PartnerPush.messageSaving(context, convId, messageId, write)

                write.addOnSuccessListener {
                    acknowledgedSentIds.add(messageId)
                    _messagesFlow.update { list ->
                        list.map {
                            if (it.id == messageId && it.status == MessageStatus.SENDING.name) {
                                it.copy(status = MessageStatus.SENT.name)
                            } else it
                        }
                    }
                }.addOnFailureListener { err ->
                    Log.w("ChatRepository", "Write message $messageId failed on server", err)
                }

                // Wait briefly for server ack without blocking or hanging on spotty connection
                try {
                    kotlinx.coroutines.withTimeoutOrNull(3000L) {
                        write.await()
                    }
                } catch (_: Exception) {}

                // Instantly update local state so single tick appears immediately!
                _messagesFlow.update { list ->
                    list.map {
                        if (it.id == messageId && it.status == MessageStatus.SENDING.name) {
                            it.copy(status = MessageStatus.SENT.name)
                        } else it
                    }
                }

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
            acknowledgedSentIds.add(messageId)
            _messagesFlow.update { list ->
                list.map {
                    if (it.id == messageId && it.status == MessageStatus.SENDING.name) {
                        it.copy(status = MessageStatus.SENT.name)
                    } else it
                }
            }
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
                if (it.id in ids && (it.receiverId == currentUserId || it.senderId != currentUserId)) {
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

    fun markYouTubePlayed(messageId: String) {
        val uid = authRepository.getCurrentUserId()
        val message = _messagesFlow.value.find { it.id == messageId }
            ?: _galleryMediaMessages.value.find { it.id == messageId }
            ?: return
        if (uid.isBlank() || message.senderId == uid || message.isDeleted || message.isYouTubePlayed) return
        val fs = firestore ?: return
        val conversationId = getConversationId()
        if (conversationId.isBlank()) return
        fs.collection("conversations").document(conversationId).collection("messages")
            .document(messageId).update("isYouTubePlayed", true)
            .addOnSuccessListener {
                updateMessageEverywhere(messageId) { it.copy(isYouTubePlayed = true) }
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
        // A deleted call recording's audio leaves this phone too (the server deletes the original)
        com.example.call.CallRecordingPlayer.forget(context, ids)
        for (id in ids) {
            com.example.util.StorageOptimizer.deleteMessageMediaCache(context, id)
        }
        // Their kept videos too (kept under their link, see KeptMedia)
        val deletedMedia = (_messagesFlow.value + archivedMessages + _galleryMediaMessages.value)
            .filter { it.id in ids }
            .flatMap { listOfNotNull(it.mediaUrl) + it.mediaUrls }
            .filter { it.isNotBlank() }
            .distinct()
        if (deletedMedia.isNotEmpty()) {
            repoScope.launch {
                deletedMedia.forEach { url ->
                    try {
                        com.example.util.VideoThumbnailHelper.getVideoCacheFile(context, url).delete()
                    } catch (_: Exception) {}
                }
            }
        }

        // Remove from every in-memory source at once, so the messages leave chat and gallery immediately
        // and can't be merged back from a stale list before Firestore confirms
        _messagesFlow.update { list -> list.filterNot { it.id in ids } }
        _galleryMediaMessages.update { list -> list.filterNot { it.id in ids } }
        todayMessages = todayMessages.filterNot { it.id in ids }
        previousMessages = previousMessages.filterNot { it.id in ids }
        localBackupMessages = localBackupMessages.filterNot { it.id in ids }
        sortedLocalBackupDescending = sortedLocalBackupDescending.filterNot { it.id in ids }
        archivedMessages = archivedMessages.filterNot { it.id in ids }
        repoScope.launch { archive.remove(ids) }

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

    /**
     * A video message's gallery poster as the server has it now (the other phone may have made it
     * since this phone last loaded the message). Empty when it has none yet; null when the server
     * couldn't be asked (offline) or the message is gone.
     */
    suspend fun fetchSavedPoster(messageId: String): String? {
        val fs = firestore ?: return null
        return try {
            val doc = fs.collection("conversations").document(getConversationId())
                .collection("messages").document(messageId)
                .get(Source.SERVER).await()
            if (!doc.exists()) return null
            val poster = (doc.get("thumbnailUrls") as? List<*>)
                ?.filterIsInstance<String>()
                ?.firstOrNull { it.isNotBlank() }
            if (poster != null) updateMessageEverywhere(messageId) { it.copy(thumbnailUrls = listOf(poster)) }
            poster.orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ChatRepository", "Couldn't check the poster of $messageId", e)
            null
        }
    }

    /** Saves [posterUrl] as a video message's gallery poster, for both phones. */
    fun savePoster(messageId: String, posterUrl: String) {
        updateMessageEverywhere(messageId) { it.copy(thumbnailUrls = listOf(posterUrl)) }
        try {
            firestore?.collection("conversations")
                ?.document(getConversationId())
                ?.collection("messages")
                ?.document(messageId)
                ?.update("thumbnailUrls", listOf(posterUrl))
                ?.addOnFailureListener { e -> Log.w("ChatRepository", "Failed to save the poster of $messageId", e) }
        } catch (e: Exception) {
            Log.w("ChatRepository", "Failed to save the poster of $messageId", e)
        }
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
        /** Older chat shown from the phone's archive at a time (nothing downloads, so a big page) */
        const val ARCHIVE_PAGE_SIZE = 300
        /** Catching the archive up starts a little before where it was complete, for clock or write delays */
        const val ARCHIVE_SYNC_OVERLAP_MS = 60L * 60 * 1000
        /** Today's messages are archived this long after they last changed */
        const val ARCHIVE_TODAY_DELAY_MS = 3_000L
        const val SEARCH_OLDER_TARGET = 500L
        const val GALLERY_SCAN_PAGE_SIZE = 500L
        const val RANGE_PAGE_SIZE = 500L
        /** New-items check reaches a little before the last one, for clock or write delays. */
        const val GALLERY_SYNC_OVERLAP_MS = 60L * 60 * 1000
        const val GALLERY_PUBLISH_INTERVAL_MS = 300L
        const val FIRESTORE_BATCH_LIMIT = 450
        val LINK_REGEX = Regex("""(https?://\S+)|(www\.\S+)""", RegexOption.IGNORE_CASE)
        // "Voice message (12s)": a length written into an old message's text
        private val DURATION_IN_TEXT = Regex("""\((\d+)\s*s\)""")
        val GENERIC_PHOTO_CAPTION = Regex("""Sent (a photo|\d+ photos?)""", RegexOption.IGNORE_CASE)
    }
}

