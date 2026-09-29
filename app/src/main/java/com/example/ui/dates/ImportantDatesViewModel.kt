package com.example.ui.dates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DateCategory
import com.example.data.model.ImportantDate
import com.example.data.repository.CoupleFeaturesRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ImportantDatesUiState(
    val dates: List<ImportantDate> = emptyList()
)

class ImportantDatesViewModel(
    private val coupleFeaturesRepository: CoupleFeaturesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportantDatesUiState())
    val uiState: StateFlow<ImportantDatesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            coupleFeaturesRepository.listenToImportantDates().collect { list ->
                _uiState.update { it.copy(dates = list) }
            }
        }
    }

    fun addDate(title: String, dateMillis: Long, category: DateCategory, notes: String?) {
        viewModelScope.launch {
            coupleFeaturesRepository.addImportantDate(title, dateMillis, category, notes)
        }
    }

    fun deleteDate(dateId: String) {
        viewModelScope.launch {
            coupleFeaturesRepository.deleteImportantDate(dateId)
        }
    }
}
