package it.vantaggi.scoreboardessential.ui.statistics

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.domain.model.PlayerStatsDTO
import it.vantaggi.scoreboardessential.domain.usecases.GetPlayerStatsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    private val allStats = MutableStateFlow<List<PlayerStatsDTO>>(emptyList())

    val topScorers: StateFlow<List<PlayerStatsDTO>> =
        combine(allStats, filter) { stats, filter ->
            when (filter) {
                FilterType.ALL -> stats
                FilterType.ATTACK -> stats.filter { it.roles.any { role -> role.category == "ATTACCO" } }
                FilterType.DEFENSE -> stats.filter { it.roles.any { role -> role.category == "DIFESA" || role.category == "PORTA" } }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadTopScorers()
    }

    fun setFilter(filterType: FilterType) {
        filter.value = filterType
    }

    private fun loadTopScorers() {
        viewModelScope.launch {
            getPlayerStatsUseCase.getTopScorers(10).collect { stats ->
                allStats.value = stats
            }
        }
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
