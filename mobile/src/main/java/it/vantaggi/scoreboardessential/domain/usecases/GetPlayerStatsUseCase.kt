package it.vantaggi.scoreboardessential.domain.usecases

import it.vantaggi.scoreboardessential.database.MatchDao
import it.vantaggi.scoreboardessential.database.PlayerDao
import it.vantaggi.scoreboardessential.domain.model.PlayerStatsDTO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class GetPlayerStatsUseCase(
    private val playerDao: PlayerDao,
    private val matchDao: MatchDao,
) {
    fun getTopScorers(limit: Int): Flow<List<PlayerStatsDTO>> =
        playerDao.getTopScorers(limit).map { players ->
            players.map { player ->
                // Use appearances from Player entity which serves as a cache for finished matches.
                // We could verify this with matchDao.getFinishedMatchesCountForPlayer(player.playerId)
                // but to avoid N+1 query issue, we rely on the Player entity being kept in sync.
                //
                // Note regarding Win Rate:
                // The current database schema (MatchPlayerCrossRef) does not store which team a player was on.
                // Therefore, it is impossible to calculate an accurate Win Rate from history.
                // We return 0.0f until the schema is updated to link players to specific teams in a match.
                PlayerStatsDTO(
                    playerId = player.playerId,
                    playerName = player.playerName,
                    goals = player.goals,
                    appearances = player.appearances,
                    winRate = 0.0f,
                )
            }
        }

    /**
     * Calculates stats for a specific player.
     * Note: Win Rate calculation is limited by current DB schema.
     */
    fun getPlayerStats(playerId: Int): Flow<PlayerStatsDTO?> =
        flow {
            val playerWithRoles = playerDao.getPlayerWithRoles(playerId).first()
            if (playerWithRoles != null) {
                val player = playerWithRoles.player
                // Here we would ideally calculate wins.
                // For now, we return what we have.
                emit(
                    PlayerStatsDTO(
                        playerId = player.playerId,
                        playerName = player.playerName,
                        goals = player.goals,
                        appearances = player.appearances,
                        winRate = 0.0f,
                    ),
                )
            } else {
                emit(null)
            }
        }
}
