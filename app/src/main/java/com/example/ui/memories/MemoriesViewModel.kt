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
    val isUploadingPhoto: Boolean = false
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

    fun addMemory(title: String, description: String, dateMillis: Long, imageUri: Uri?, location: String?) {
        viewModelScope.launch {
            var photoUrl: String? = null
            if (imageUri != null) {
                _uiState.update { it.copy(isUploadingPhoto = true) }
                try {
                    val compressed = mediaRepository.compressAndPrepareImage(imageUri)
                    val upload = mediaRepository.uploadFile(compressed, MessageType.IMAGE, "memories")
                    photoUrl = upload.getOrNull()
                } finally {
                    _uiState.update { it.copy(isUploadingPhoto = false) }
                }
            }
            coupleFeaturesRepository.addMemory(title, description, dateMillis, photoUrl, location)
        }
    }

    fun deleteMemory(memoryId: String) {
        viewModelScope.launch {
            coupleFeaturesRepository.deleteMemory(memoryId)
        }
    }
}
