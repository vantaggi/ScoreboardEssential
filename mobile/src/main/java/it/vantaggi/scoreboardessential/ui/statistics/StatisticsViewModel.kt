package it.vantaggi.scoreboardessential.ui.statistics

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.domain.model.PlayerStatsDTO
import it.vantaggi.scoreboardessential.domain.usecases.GetPlayerStatsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

class StatisticsViewModel(
    application: Application,
) : ViewModel() {
    private val playerDao = AppDatabase.getDatabase(application).playerDao()
    private val matchDao = AppDatabase.getDatabase(application).matchDao()
    private val getPlayerStatsUseCase = GetPlayerStatsUseCase(playerDao, matchDao)

    enum class FilterType {
        ALL,
        ATTACK,
        DEFENSE,
    }

    private val filter = MutableStateFlow(FilterType.ALL)

    @OptIn(ExperimentalCoroutinesApi::class)
    val topScorers: StateFlow<List<PlayerStatsDTO>> =
        filter
            .flatMapLatest { filterType ->
                val categories =
                    when (filterType) {
                        FilterType.ALL -> null
                        FilterType.ATTACK -> listOf("ATTACCO")
                        FilterType.DEFENSE -> listOf("DIFESA", "PORTA")
                    }
                getPlayerStatsUseCase.getTopScorers(10, categories)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(newFilter: FilterType) {
        filter.value = newFilter
    }

    class Factory(
        private val application: Application,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(StatisticsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return StatisticsViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
