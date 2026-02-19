package it.vantaggi.scoreboardessential.domain.usecases

import it.vantaggi.scoreboardessential.database.MatchDao
import it.vantaggi.scoreboardessential.database.PlayerDao
import it.vantaggi.scoreboardessential.domain.model.PlayerStatsDTO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Use case responsible for retrieving player statistics.
 *
 * @property playerDao Data Access Object for accessing player data.
 * @property matchDao Data Access Object for accessing match data.
 */
class GetPlayerStatsUseCase(
    private val playerDao: PlayerDao,
    private val matchDao: MatchDao,
) {
    /**
     * Retrieves a list of top scoring players, limited by the provided count.
     * The returned statistics include appearances and goals.
     * Note: Win rate is currently returned as 0.0f due to database schema limitations (MatchPlayerCrossRef lacks team affiliation).
     *
     * @param limit The maximum number of players to return.
     * @return A Flow emitting a list of [PlayerStatsDTO].
     */
    fun getTopScorers(limit: Int): Flow<List<PlayerStatsDTO>> =
        playerDao.getTopScorers(limit).map { playersWithRoles ->
            playersWithRoles.map { playerWithRoles ->
                val player = playerWithRoles.player
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
                    roles = playerWithRoles.roles,
                )
            }
        }

    /**
     * Calculates stats for a specific player.
     * Note: Win Rate calculation is limited by current DB schema.
     *
     * @param playerId The ID of the player to retrieve stats for.
     * @return A Flow emitting [PlayerStatsDTO] if found, or null if not found.
     */
    fun getPlayerStats(playerId: Int): Flow<PlayerStatsDTO?> =
        playerDao.getPlayerWithRoles(playerId).map { playerWithRoles ->
            if (playerWithRoles != null) {
                val player = playerWithRoles.player
                // Here we would ideally calculate wins.
                // For now, we return what we have.
                PlayerStatsDTO(
                    playerId = player.playerId,
                    playerName = player.playerName,
                    goals = player.goals,
                    appearances = player.appearances,
                    winRate = 0.0f,
                )
            } else {
                null
            }
        }
}
