package com.example.ui.disguise

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.notes.ChecklistItem
import com.example.data.local.notes.NoteEntity
import com.example.data.local.notes.NotesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class NotesSortOrder {
    RECENT,
    ALPHABETICAL,
    CATEGORY
}

data class NotesUiState(
    val notes: List<NoteEntity> = emptyList(),
    val isLoading: Boolean = true,
    val totalCount: Int = 0,
    val searchQuery: String = "",
    val selectedCategory: String = "All",
    val isGridView: Boolean = false,
    val sortOrder: NotesSortOrder = NotesSortOrder.RECENT,
    val snackbarMessage: String? = null,
    val selectedNoteIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false
)

private data class FilterState(
    val query: String,
    val category: String,
    val isGrid: Boolean,
    val sort: NotesSortOrder
)

private data class SelectionState(
    val selectedIds: Set<String>,
    val isSelectionMode: Boolean
)

class NotesDisguiseViewModel(
    private val repository: NotesRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedCategory = MutableStateFlow("All")
    private val _isGridView = MutableStateFlow(false)
    private val _sortOrder = MutableStateFlow(NotesSortOrder.RECENT)
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    private val _selectedNoteIds = MutableStateFlow<Set<String>>(emptySet())
    private val _isSelectionMode = MutableStateFlow(false)

    private val _filterState = combine(
        _searchQuery,
        _selectedCategory,
        _isGridView,
        _sortOrder
    ) { query, category, isGrid, sort ->
        FilterState(query, category, isGrid, sort)
    }

    private val _selectionState = combine(
        _selectedNoteIds,
        _isSelectionMode
    ) { selectedIds, isSelectionMode ->
        SelectionState(selectedIds, isSelectionMode || selectedIds.isNotEmpty())
    }

    val uiState: StateFlow<NotesUiState> = combine(
        repository.allNotes,
        _filterState,
        _selectionState,
        _snackbarMessage
    ) { allNotes, filter, selection, snackbar ->
        val filtered = allNotes.filter { note ->
            val matchesCategory = when (filter.category) {
                "All" -> true
                "📌 Pinned" -> note.isPinned
                else -> note.category.equals(filter.category, ignoreCase = true)
            }
            val matchesQuery = filter.query.isBlank() ||
                    note.title.contains(filter.query, ignoreCase = true) ||
                    note.content.contains(filter.query, ignoreCase = true) ||
                    note.getChecklist().any { it.text.contains(filter.query, ignoreCase = true) }
            matchesCategory && matchesQuery
        }.let { list ->
            when (filter.sort) {
                NotesSortOrder.RECENT -> list.sortedWith(compareByDescending<NoteEntity> { it.isPinned }.thenByDescending { it.updatedAt })
                NotesSortOrder.ALPHABETICAL -> list.sortedWith(compareByDescending<NoteEntity> { it.isPinned }.thenBy { it.title.lowercase() })
                NotesSortOrder.CATEGORY -> list.sortedWith(compareByDescending<NoteEntity> { it.isPinned }.thenBy { it.category })
            }
        }

        NotesUiState(
            notes = filtered,
            isLoading = false,
            totalCount = allNotes.size,
            searchQuery = filter.query,
            selectedCategory = filter.category,
            isGridView = filter.isGrid,
            sortOrder = filter.sort,
            snackbarMessage = snackbar,
            selectedNoteIds = selection.selectedIds,
            isSelectionMode = selection.isSelectionMode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotesUiState()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    fun toggleViewMode() {
        _isGridView.value = !_isGridView.value
    }

    fun setSortOrder(order: NotesSortOrder) {
        _sortOrder.value = order
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showToastMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun saveNote(
        id: String? = null,
        title: String,
        content: String,
        category: String,
        colorHex: String,
        checklist: List<ChecklistItem> = emptyList(),
        isPinned: Boolean = false
    ) {
        if (title.isBlank() && content.isBlank() && checklist.isEmpty()) return

        viewModelScope.launch {
            val noteId = id ?: UUID.randomUUID().toString()
            val note = NoteEntity(
                id = noteId,
                title = title.ifBlank { "Untitled Note" },
                content = content,
                category = category,
                colorHex = colorHex,
                checklistJson = NoteEntity.encodeChecklist(checklist),
                isPinned = isPinned,
                updatedAt = System.currentTimeMillis()
            )
            repository.insertNote(note)
            _snackbarMessage.value = if (id == null) "Note created" else "Note updated"
        }
    }

    fun togglePin(note: NoteEntity) {
        viewModelScope.launch {
            repository.togglePin(note.id, note.isPinned)
            _snackbarMessage.value = if (note.isPinned) "Unpinned" else "Pinned to top 📌"
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
            _snackbarMessage.value = "Note deleted"
        }
    }

    fun duplicateNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.duplicateNote(note)
            _snackbarMessage.value = "Note duplicated"
        }
    }

    fun toggleChecklistItem(noteId: String, itemId: String, isDone: Boolean) {
        viewModelScope.launch {
            repository.updateChecklistItem(noteId, itemId, isDone)
        }
    }

    fun startSelectionMode(initialNoteId: String? = null) {
        _isSelectionMode.value = true
        _selectedNoteIds.value = if (initialNoteId != null) setOf(initialNoteId) else emptySet()
    }

    fun toggleNoteSelection(noteId: String) {
        val current = _selectedNoteIds.value
        val updated = if (current.contains(noteId)) current - noteId else current + noteId
        _selectedNoteIds.value = updated
        _isSelectionMode.value = updated.isNotEmpty()
    }

    fun selectAll(visibleNoteIds: List<String>) {
        _isSelectionMode.value = true
        _selectedNoteIds.value = visibleNoteIds.toSet()
    }

    fun clearSelection() {
        _selectedNoteIds.value = emptySet()
        _isSelectionMode.value = false
    }

    fun deleteSelectedNotes() {
        val toDelete = _selectedNoteIds.value.toList()
        if (toDelete.isEmpty()) return
        viewModelScope.launch {
            repository.deleteNotes(toDelete)
            val count = toDelete.size
            _selectedNoteIds.value = emptySet()
            _isSelectionMode.value = false
            _snackbarMessage.value = if (count == 1) "1 note deleted" else "$count notes deleted"
        }
    }

    fun shareNote(context: Context, note: NoteEntity) {
        val shareText = buildString {
            appendLine(note.title)
            appendLine("-------------------")
            if (note.content.isNotBlank()) {
                appendLine(note.content)
            }
            val checklist = note.getChecklist()
            if (checklist.isNotEmpty()) {
                appendLine("\nChecklist:")
                checklist.forEach { item ->
                    appendLine(if (item.isDone) " [✓] ${item.text}" else " [ ] ${item.text}")
                }
            }
            appendLine("\n— Shared from Notes")
        }

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, note.title)
            type = "text/plain"
        }
        val chooser = Intent.createChooser(sendIntent, "Share Note")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
