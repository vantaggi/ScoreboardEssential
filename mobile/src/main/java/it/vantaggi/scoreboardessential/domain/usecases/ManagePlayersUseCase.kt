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

class ManagePlayersUseCase(
    @Suppress("UNUSED_PARAMETER") private val playerDao: PlayerDao,
    private val wearDataSync: OptimizedWearDataSync,
) {
    private val _teamRoster = MutableStateFlow(TeamRoster())
    val teamRoster: StateFlow<TeamRoster> = _teamRoster.asStateFlow()

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
