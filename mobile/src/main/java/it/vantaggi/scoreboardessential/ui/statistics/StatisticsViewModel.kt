package it.vantaggi.scoreboardessential.ui.statistics

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.domain.model.PlayerStatsDTO
import it.vantaggi.scoreboardessential.domain.usecases.GetPlayerStatsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class StatisticsViewModel(
    application: Application,
) : ViewModel() {
    private val playerDao = AppDatabase.getDatabase(application).playerDao()
    private val matchDao = AppDatabase.getDatabase(application).matchDao()
    private val getPlayerStatsUseCase = GetPlayerStatsUseCase(playerDao, matchDao)

    private val _topScorers = MutableStateFlow<List<PlayerStatsDTO>>(emptyList())
    val topScorers: StateFlow<List<PlayerStatsDTO>> = _topScorers

    init {
        loadTopScorers()
    }

    private fun loadTopScorers() {
        viewModelScope.launch {
            getPlayerStatsUseCase.getTopScorers(10).collect { stats ->
                _topScorers.value = stats
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
