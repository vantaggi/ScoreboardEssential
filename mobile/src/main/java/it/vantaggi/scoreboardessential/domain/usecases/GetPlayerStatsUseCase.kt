package it.vantaggi.scoreboardessential.domain.usecases

import it.vantaggi.scoreboardessential.database.MatchDao
import it.vantaggi.scoreboardessential.database.PlayerDao
import it.vantaggi.scoreboardessential.domain.model.PlayerStatsDTO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
     * @param roleCategories Optional list of role categories to filter by.
     * @return A Flow emitting a list of [PlayerStatsDTO].
     */
    fun getTopScorers(
        limit: Int,
        roleCategories: List<String>? = null,
    ): Flow<List<PlayerStatsDTO>> {
        val sourceFlow =
            if (roleCategories.isNullOrEmpty()) {
                playerDao.getTopScorers(limit)
            } else {
                playerDao.getTopScorersByRoleCategories(limit, roleCategories)
            }

        // Combine the roster flow with per-player win counts so the win rate stays reactive
        // and is computed with a single grouped query (no N+1).
        return combine(sourceFlow, matchDao.getPlayerWinCounts()) { playersWithRoles, winCounts ->
            val winsByPlayer = winCounts.associate { it.playerId to it.wins }
            playersWithRoles.map { playerWithRoles ->
                val player = playerWithRoles.player
                val wins = winsByPlayer[player.playerId] ?: 0
                PlayerStatsDTO(
                    playerId = player.playerId,
                    playerName = player.playerName,
                    goals = player.goals,
                    appearances = player.appearances,
                    winRate = if (player.appearances > 0) wins.toFloat() / player.appearances else 0.0f,
                    roles = playerWithRoles.roles,
                )
            }
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
        combine(
            playerDao.getPlayerWithRoles(playerId),
            matchDao.getPlayerWinCounts(),
        ) { playerWithRoles, winCounts ->
            if (playerWithRoles != null) {
                val player = playerWithRoles.player
                val wins = winCounts.firstOrNull { it.playerId == player.playerId }?.wins ?: 0
                PlayerStatsDTO(
                    playerId = player.playerId,
                    playerName = player.playerName,
                    goals = player.goals,
                    appearances = player.appearances,
                    winRate = if (player.appearances > 0) wins.toFloat() / player.appearances else 0.0f,
                    roles = playerWithRoles.roles,
                )
            } else {
                null
            }
        }
}
