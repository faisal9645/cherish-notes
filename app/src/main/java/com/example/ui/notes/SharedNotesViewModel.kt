package com.example.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.NoteCategory
import com.example.data.model.SharedNote
import com.example.data.repository.CoupleFeaturesRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SharedNotesUiState(
    val notes: List<SharedNote> = emptyList()
)

class SharedNotesViewModel(
    private val coupleFeaturesRepository: CoupleFeaturesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SharedNotesUiState())
    val uiState: StateFlow<SharedNotesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            coupleFeaturesRepository.listenToSharedNotes().collect { list ->
                _uiState.update { it.copy(notes = list) }
            }
        }
    }

    fun saveNote(noteId: String?, title: String, content: String, category: NoteCategory) {
        viewModelScope.launch {
            coupleFeaturesRepository.saveSharedNote(noteId, title, content, category)
        }
    }

    fun togglePin(noteId: String) {
        viewModelScope.launch {
            coupleFeaturesRepository.togglePinNote(noteId)
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            coupleFeaturesRepository.deleteNote(noteId)
        }
    }
}
