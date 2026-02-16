package it.vantaggi.scoreboardessential.domain.usecases

import it.vantaggi.scoreboardessential.database.PlayerDao
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

/**
 * Use case responsible for managing the roster of players for the current match.
 * It handles adding/removing players from teams and synchronizing this state with the Wear OS device.
 *
 * @property playerDao Data Access Object for player operations (currently unused but reserved for future persistence).
 * @property wearDataSync Component for synchronizing data with Wear OS.
 */
class ManagePlayersUseCase(
    @Suppress("UNUSED_PARAMETER") private val playerDao: PlayerDao,
    private val wearDataSync: OptimizedWearDataSync,
) {
    private val _teamRoster = MutableStateFlow(TeamRoster())
    val teamRoster: StateFlow<TeamRoster> = _teamRoster.asStateFlow()

    /**
     * Adds a player to the specified team.
     * Checks if the player is already in the team to avoid duplicates.
     * Triggers a sync with Wear OS upon successful addition.
     *
     * @param player The player (with roles) to add.
     * @param teamId The ID of the team (1 or 2).
     */
    suspend fun addPlayerToTeam(
        player: PlayerWithRoles,
        teamId: Int,
    ) {
        _teamRoster.update { currentRoster ->
            if (teamId == 1) {
                if (currentRoster.team1Players.none { it.player.playerId == player.player.playerId }) {
                    currentRoster.copy(team1Players = currentRoster.team1Players + player)
                } else {
                    currentRoster
                }
            } else {
                if (currentRoster.team2Players.none { it.player.playerId == player.player.playerId }) {
                    currentRoster.copy(team2Players = currentRoster.team2Players + player)
                } else {
                    currentRoster
                }
            }
        }
        syncTeamPlayers()
    }

    /**
     * Removes a player from the specified team.
     * Triggers a sync with Wear OS upon successful removal.
     *
     * @param player The player to remove.
     * @param teamId The ID of the team (1 or 2).
     */
    suspend fun removePlayerFromTeam(
        player: PlayerWithRoles,
        teamId: Int,
    ) {
        _teamRoster.update { currentRoster ->
            if (teamId == 1) {
                currentRoster.copy(
                    team1Players = currentRoster.team1Players.filter { it.player.playerId != player.player.playerId },
                )
            } else {
                currentRoster.copy(
                    team2Players = currentRoster.team2Players.filter { it.player.playerId != player.player.playerId },
                )
            }
        }
        syncTeamPlayers()
    }

    private suspend fun syncTeamPlayers() {
        val roster = _teamRoster.value
        val team1Json = serializePlayers(roster.team1Players)
        val team2Json = serializePlayers(roster.team2Players)
        wearDataSync.syncTeamPlayers(team1Json, team2Json)
    }

    private fun serializePlayers(players: List<PlayerWithRoles>): String {
        val jsonArray = JSONArray()
        players.forEach { playerWithRoles ->
            val jsonObject = JSONObject()
            jsonObject.put("id", playerWithRoles.player.playerId)
            jsonObject.put("name", playerWithRoles.player.playerName)
            // Include roles if needed by Wear app
            val rolesArray = JSONArray()
            playerWithRoles.roles.forEach { role ->
                rolesArray.put(role.name)
            }
            jsonObject.put("roles", rolesArray)
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }
}
