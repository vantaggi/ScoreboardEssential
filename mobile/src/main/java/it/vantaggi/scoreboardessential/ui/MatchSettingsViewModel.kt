package it.vantaggi.scoreboardessential.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.vantaggi.scoreboardessential.repository.MatchSettingsRepository
import kotlinx.coroutines.launch

class MatchSettingsViewModel(private val repository: MatchSettingsRepository) : ViewModel() {

    private val _team1Name = MutableLiveData<String>()
    val team1Name: LiveData<String> = _team1Name

    private val _team2Name = MutableLiveData<String>()
    val team2Name: LiveData<String> = _team2Name

    private val _team1Color = MutableLiveData<Int>()
    val team1Color: LiveData<Int> = _team1Color

    private val _team2Color = MutableLiveData<Int>()
    val team2Color: LiveData<Int> = _team2Color

    private val _keeperTimerDuration = MutableLiveData<Long>()
    val keeperTimerDuration: LiveData<Long> = _keeperTimerDuration

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _team1Name.value = repository.getTeam1Name()
            _team2Name.value = repository.getTeam2Name()
            _team1Color.value = repository.getTeam1Color()
            _team2Color.value = repository.getTeam2Color()
            _keeperTimerDuration.value = repository.getKeeperTimerDuration()
        }
    }

    fun saveTeam1Name(name: String) {
        viewModelScope.launch {
            repository.setTeam1Name(name)
            _team1Name.value = name
        }
    }

    fun saveTeam2Name(name: String) {
        viewModelScope.launch {
            repository.setTeam2Name(name)
            _team2Name.value = name
        }
    }

    fun saveTeam1Color(color: Int) {
        viewModelScope.launch {
            repository.setTeam1Color(color)
            _team1Color.value = color
        }
    }

    fun saveTeam2Color(color: Int) {
        viewModelScope.launch {
            repository.setTeam2Color(color)
            _team2Color.value = color
        }
    }

    fun saveKeeperTimerDuration(duration: Long) {
        viewModelScope.launch {
            repository.setKeeperTimerDuration(duration)
            _keeperTimerDuration.value = duration
        }
    }
}

class MatchSettingsViewModelFactory(private val repository: MatchSettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MatchSettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MatchSettingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}