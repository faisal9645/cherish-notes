package com.example.ui.memories

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Memory
import com.example.data.model.MessageType
import com.example.data.repository.CoupleFeaturesRepository
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class MemoriesUiState(
    val memories: List<Memory> = emptyList(),
    val isUploadingPhoto: Boolean = false,
    // Why the last memory couldn't be added (shown in the dialog), or null
    val addError: String? = null,
    // Goes up each time a memory is saved, so the dialog knows it can close
    val savedCount: Int = 0
)

class MemoriesViewModel(
    private val coupleFeaturesRepository: CoupleFeaturesRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoriesUiState())
    val uiState: StateFlow<MemoriesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            coupleFeaturesRepository.listenToMemories().collect { list ->
                _uiState.update { it.copy(memories = list) }
            }
        }
    }

    /**
     * Saves a memory. Its photo is uploaded to the couple's folder first, so both of us see it; if
     * that fails, nothing is saved and the dialog says so (and keeps what was typed for a retry).
     */
    fun addMemory(title: String, description: String, dateMillis: Long, imageUri: Uri?, location: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingPhoto = imageUri != null, addError = null) }
            val photoUrl: String? = if (imageUri == null) null else try {
                val prepared = mediaRepository.compressAndPrepareImage(imageUri)
                mediaRepository.uploadFile(prepared, MessageType.IMAGE, coupleFeaturesRepository.getCoupleId())
                    .getOrNull()
                    // A copy that only exists on this phone isn't a shared photo
                    ?.takeIf { it.startsWith("http") || it.startsWith("data:") }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("MemoriesViewModel", "Memory photo upload failed", e)
                null
            }
            if (imageUri != null && photoUrl == null) {
                _uiState.update {
                    it.copy(
                        isUploadingPhoto = false,
                        addError = "The photo couldn't be uploaded. Check your connection and try again."
                    )
                }
                return@launch
            }
            coupleFeaturesRepository.addMemory(title, description, dateMillis, photoUrl, location)
            _uiState.update { it.copy(isUploadingPhoto = false, savedCount = it.savedCount + 1) }
        }
    }

    fun clearAddError() {
        _uiState.update { it.copy(addError = null) }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            coupleFeaturesRepository.deleteMemory(memoryId)
        }
    }
}
