package it.vantaggi.scoreboardessential

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.database.Role
import it.vantaggi.scoreboardessential.repository.PlayerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val _allRoles = MutableStateFlow<List<Role>>(emptyList())
    val allRoles: StateFlow<List<Role>> = _allRoles.asStateFlow()

    // Holds the ID of the currently selected role for filtering, or null if no filter is active.
    private val _selectedRoleFilter = MutableStateFlow<Int?>(null)
    val selectedRoleFilter: StateFlow<Int?> = _selectedRoleFilter.asStateFlow()

    private var currentSortOrder = SortOrder.NAME_ASC
    private var allPlayersInternal: List<PlayerWithRoles> = emptyList()

    init {
        val playerDao = AppDatabase.getDatabase(application).playerDao()
        playerRepository = PlayerRepository(playerDao)
        loadPlayersAndRoles() // Un nuovo metodo per caricare tutto
    }

    private fun loadPlayersAndRoles() {
        viewModelScope.launch {
            playerRepository.allPlayers.collect { playersList ->
                allPlayersInternal = playersList
                applyFiltersAndSorting()
            }
        }
        // Aggiungi questo nuovo blocco di raccolta per i ruoli
        viewModelScope.launch {
            playerRepository.allRoles.collect { rolesList ->
                _allRoles.value = rolesList
            }
        }
    }

    private fun applyFiltersAndSorting() {
        var filteredList = allPlayersInternal

        // Apply role filter if one is selected
        _selectedRoleFilter.value?.let { roleId ->
            filteredList = filteredList.filter { playerWithRoles ->
                playerWithRoles.roles.any { it.roleId == roleId }
            }
        }

        // Apply sorting (existing logic)
        val sortedList = when (currentSortOrder) {
            SortOrder.NAME_ASC -> filteredList.sortedBy { it.player.playerName.lowercase() }
            SortOrder.GOALS_DESC -> filteredList.sortedByDescending { it.player.goals }
            SortOrder.APPEARANCES_DESC -> filteredList.sortedByDescending { it.player.appearances }
        }
        _players.value = sortedList
    }

    fun setRoleFilter(roleId: Int?) {
        _selectedRoleFilter.value = roleId
        applyFiltersAndSorting()
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

    fun getPlayer(playerId: Long): Flow<PlayerWithRoles?> {
        return playerRepository.getPlayerWithRoles(playerId)
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
        applyFiltersAndSorting()
    }

    fun sortByGoals() {
        currentSortOrder = SortOrder.GOALS_DESC
        applyFiltersAndSorting()
    }

    fun sortByAppearances() {
        currentSortOrder = SortOrder.APPEARANCES_DESC
        applyFiltersAndSorting()
    }
}