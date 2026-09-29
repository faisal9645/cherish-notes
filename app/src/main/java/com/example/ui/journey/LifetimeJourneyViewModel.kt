package com.example.ui.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.LifetimeAgeProfile
import com.example.data.model.YearlyJourneyEntry
import com.example.data.repository.CoupleFeaturesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

data class LifetimeJourneyUiState(
    val profile: LifetimeAgeProfile = LifetimeAgeProfile(),
    val journeys: List<YearlyJourneyEntry> = emptyList(),
    val currentYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val myCurrentAge: Int = 34,
    val partnerCurrentAge: Int = 31,
    val totalYearsTogether: Int = 3
)

class LifetimeJourneyViewModel(
    private val repository: CoupleFeaturesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LifetimeJourneyUiState())
    val uiState: StateFlow<LifetimeJourneyUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.lifetimeProfileFlow.collect { profile ->
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                val myAge = currentYear - profile.myBirthYear
                val partnerAge = currentYear - profile.partnerBirthYear
                val yearsTogether = maxOf(1, currentYear - profile.relationshipStartYear + 1)

                _uiState.value = _uiState.value.copy(
                    profile = profile,
                    currentYear = currentYear,
                    myCurrentAge = myAge,
                    partnerCurrentAge = partnerAge,
                    totalYearsTogether = yearsTogether
                )
            }
        }

        viewModelScope.launch {
            repository.yearlyJourneysFlow.collect { list ->
                _uiState.value = _uiState.value.copy(journeys = list)
            }
        }
    }

    fun updateProfile(myBirthYear: Int, partnerBirthYear: Int, startYear: Int, vow: String) {
        repository.updateLifetimeProfile(myBirthYear, partnerBirthYear, startYear, vow)
    }

    fun addYearlyJourney(
        year: Int,
        myAge: Int,
        partnerAge: Int,
        yearTheme: String,
        placesWent: String,
        howWeEnjoyed: String,
        specialMemory: String,
        songOrQuote: String,
        passionRating: Int
    ) {
        val entry = YearlyJourneyEntry(
            id = "year_${year}_${UUID.randomUUID().toString().take(6)}",
            year = year,
            myAge = myAge,
            partnerAge = partnerAge,
            yearTheme = yearTheme.trim(),
            placesWent = placesWent.trim(),
            howWeEnjoyed = howWeEnjoyed.trim(),
            specialMemory = specialMemory.trim(),
            songOrQuote = songOrQuote.trim(),
            passionRating = passionRating
        )
        repository.addYearlyJourney(entry)
    }

    fun updateYearlyJourney(entry: YearlyJourneyEntry) {
        repository.updateYearlyJourney(entry)
    }

    fun deleteYearlyJourney(id: String) {
        repository.deleteYearlyJourney(id)
    }
}
