package com.example.scoreboardessential

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.scoreboardessential.database.AppDatabase
import com.example.scoreboardessential.database.Player
import com.example.scoreboardessential.database.PlayerWithRoles
import com.example.scoreboardessential.database.Role
import com.example.scoreboardessential.repository.PlayerRepository
import kotlinx.coroutines.flow.Flow
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

    private val playerRepository: PlayerRepository

    private val _players = MutableStateFlow<List<PlayerWithRoles>>(emptyList())
    val players: StateFlow<List<PlayerWithRoles>> = _players.asStateFlow()

    val allRoles: Flow<List<Role>>

    private var currentSortOrder = SortOrder.NAME_ASC
    private var allPlayersInternal: List<PlayerWithRoles> = emptyList()

    init {
        val playerDao = AppDatabase.getDatabase(application).playerDao()
        playerRepository = PlayerRepository(playerDao)
        allRoles = playerRepository.allRoles
        loadPlayers()
    }

    private fun loadPlayers() {
        viewModelScope.launch {
            playerRepository.allPlayers.collect { playersList ->
                allPlayersInternal = playersList
                applySorting()
            }
        }
    }

    private fun applySorting() {
        _players.value = when (currentSortOrder) {
            SortOrder.NAME_ASC -> allPlayersInternal.sortedBy { it.player.playerName.lowercase() }
            SortOrder.GOALS_DESC -> allPlayersInternal.sortedByDescending { it.player.goals }
            SortOrder.APPEARANCES_DESC -> allPlayersInternal.sortedByDescending { it.player.appearances }
        }
    }

    fun createPlayer(name: String, roleIds: List<Int>) {
        viewModelScope.launch {
            val player = Player(
                playerName = name,
                appearances = 0,
                goals = 0
            )
            playerRepository.insertPlayerWithRoles(player, roleIds)
        }
    }

    fun updatePlayer(player: Player, roleIds: List<Int>) {
        viewModelScope.launch {
            playerRepository.updatePlayerWithRoles(player, roleIds)
        }
    }

    fun deletePlayer(playerWithRoles: PlayerWithRoles) {
        viewModelScope.launch {
            playerRepository.deletePlayer(playerWithRoles.player)
        }
    }

    fun restorePlayer(player: Player, roleIds: List<Int>) {
        viewModelScope.launch {
            playerRepository.insertPlayerWithRoles(player, roleIds)
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