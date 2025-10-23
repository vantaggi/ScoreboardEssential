package it.vantaggi.scoreboardessential.domain.usecases

import it.vantaggi.scoreboardessential.database.PlayerDao
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ManagePlayersUseCase(
    private val playerDao: PlayerDao,
    private val wearDataSync: OptimizedWearDataSync,
) {
    data class TeamRoster(
        val team1Players: List<PlayerWithRoles> = emptyList(),
        val team2Players: List<PlayerWithRoles> = emptyList(),
    )

    private val _teamRoster = MutableStateFlow(TeamRoster())
    val teamRoster: StateFlow<TeamRoster> = _teamRoster

    fun addPlayerToTeam(
        player: PlayerWithRoles,
        teamNumber: Int,
    ) {
        val currentRoster = _teamRoster.value

        val newRoster =
            when (teamNumber) {
                1 -> {
                    val updatedTeam1 = currentRoster.team1Players.toMutableList()
                    if (updatedTeam1.none { it.player.playerId == player.player.playerId }) {
                        updatedTeam1.add(player)
                    }
                    currentRoster.copy(team1Players = updatedTeam1)
                }
                2 -> {
                    val updatedTeam2 = currentRoster.team2Players.toMutableList()
                    if (updatedTeam2.none { it.player.playerId == player.player.playerId }) {
                        updatedTeam2.add(player)
                    }
                    currentRoster.copy(team2Players = updatedTeam2)
                }
                else -> return
            }

        _teamRoster.value = newRoster
        syncTeamPlayersToWear()
    }

    fun removePlayerFromTeam(
        player: PlayerWithRoles,
        teamNumber: Int,
    ) {
        val currentRoster = _teamRoster.value

        val newRoster =
            when (teamNumber) {
                1 -> {
                    val updatedTeam1 =
                        currentRoster.team1Players
                            .filter { it.player.playerId != player.player.playerId }
                    currentRoster.copy(team1Players = updatedTeam1)
                }
                2 -> {
                    val updatedTeam2 =
                        currentRoster.team2Players
                            .filter { it.player.playerId != player.player.playerId }
                    currentRoster.copy(team2Players = updatedTeam2)
                }
                else -> return
            }

        _teamRoster.value = newRoster
        syncTeamPlayersToWear()
    }

    private fun syncTeamPlayersToWear() {
        val roster = _teamRoster.value

        val team1Data = roster.team1Players.map { it.toPlayerData() }
        val team2Data = roster.team2Players.map { it.toPlayerData() }

        wearDataSync.syncTeamPlayers(team1Data, team2Data)
    }

    private fun PlayerWithRoles.toPlayerData() =
        it.vantaggi.scoreboardessential.shared.PlayerData(
            id = player.playerId,
            name = player.playerName,
            roles = roles.map { it.name },
            goals = player.goals,
            appearances = player.appearances,
        )
}
