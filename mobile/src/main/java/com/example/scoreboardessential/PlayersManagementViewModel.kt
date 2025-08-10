package com.example.scoreboardessential

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scoreboardessential.database.AppDatabase
import com.example.scoreboardessential.database.Player
import com.example.scoreboardessential.database.PlayerDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SortOrder {
    NAME_ASC,
    GOALS_DESC,
    APPEARANCES_DESC
}

class PlayersManagementViewModel(application: Application) : AndroidViewModel(application) {

    private val playerDao: PlayerDao = AppDatabase.getDatabase(application).playerDao()

    private val _players = MutableStateFlow<List<Player>>(emptyList())
    val players: StateFlow<List<Player>> = _players.asStateFlow()

    private var currentSortOrder = SortOrder.NAME_ASC
    private var allPlayers: List<Player> = emptyList()

    init {
        loadPlayers()
    }

    private fun loadPlayers() {
        viewModelScope.launch {
            playerDao.getAllPlayers().collect { playersList ->
                allPlayers = playersList
                applySorting()
            }
        }
    }

    private fun applySorting() {
        _players.value = when (currentSortOrder) {
            SortOrder.NAME_ASC -> allPlayers.sortedBy { it.playerName.lowercase() }
            SortOrder.GOALS_DESC -> allPlayers.sortedByDescending { it.goals }
            SortOrder.APPEARANCES_DESC -> allPlayers.sortedByDescending { it.appearances }
        }
    }

    fun createPlayer(name: String, roles: String) {
        viewModelScope.launch {
            val player = Player(
                playerName = name,
                roles = roles,
                appearances = 0,
                goals = 0
            )
            playerDao.insert(player)
        }
    }

    fun updatePlayer(player: Player) {
        viewModelScope.launch {
            playerDao.update(player)
        }
    }

    fun deletePlayer(player: Player) {
        viewModelScope.launch {
            playerDao.delete(player)
        }
    }

    fun restorePlayer(player: Player) {
        viewModelScope.launch {
            playerDao.insert(player)
        }
    }

    fun sortByName() {
        currentSortOrder = SortOrder.NAME_ASC
        applySorting()
    }

    fun sortByGoals() {
        currentSortOrder = SortOrder.GOALS_DESC
        applySorting()
    }

    fun sortByAppearances() {
        currentSortOrder = SortOrder.APPEARANCES_DESC
        applySorting()
    }
}