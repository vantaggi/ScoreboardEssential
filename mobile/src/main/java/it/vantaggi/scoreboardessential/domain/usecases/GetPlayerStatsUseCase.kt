package it.vantaggi.scoreboardessential.domain.usecases

import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.MatchDao
import it.vantaggi.scoreboardessential.database.PlayerDao
import it.vantaggi.scoreboardessential.database.PlayerSportCount
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.database.playedIn
import it.vantaggi.scoreboardessential.database.wonIn
import it.vantaggi.scoreboardessential.domain.model.PlayerStatsDTO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Use case responsible for retrieving player statistics of football, the only sport with scorers.
 *
 * Le partite e le vittorie sono quelle di CALCIO, contate dalle partite salvate: il totale in
 * [it.vantaggi.scoreboardessential.database.Player.appearances] somma anche padel e tennis, e chi
 * gioca a piu' sport vedeva nel calcio le partite degli altri. Gli sport senza marcatori hanno la
 * loro classifica ([MatchDao.getPlayerRecords]).
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
     * The returned statistics include football appearances and goals.
     * Solo chi ha giocato a calcio o ha gol: chi ha giocato solo a padel non e' nella classifica.
     *
     * @param limit The maximum number of players to return.
     * @param roleCategories Optional list of role categories to filter by.
     * @return A Flow emitting a list of [PlayerStatsDTO].
     */
    fun getTopScorers(
        limit: Int,
        roleCategories: List<String>? = null,
    ): Flow<List<PlayerStatsDTO>> {
        // Senza limite: l'ordine e il taglio si fanno qui, dopo aver tolto chi non gioca a calcio,
        // altrimenti un giocatore di solo padel potrebbe occupare un posto della classifica.
        val sourceFlow =
            if (roleCategories.isNullOrEmpty()) {
                playerDao.getTopScorers(Int.MAX_VALUE)
            } else {
                playerDao.getTopScorersByRoleCategories(Int.MAX_VALUE, roleCategories)
            }

        // Combine the roster flow with per-player counts so the stats stay reactive
        // and are computed with a single grouped query (no N+1).
        return combine(sourceFlow, matchDao.getPlayerSportCounts()) { playersWithRoles, counts ->
            playersWithRoles
                .map { toDto(it, counts) }
                .filter { it.goals > 0 || it.appearances > 0 }
                .sortedWith(compareByDescending<PlayerStatsDTO> { it.goals }.thenBy { it.appearances })
                .take(limit)
        }
    }

    /**
     * Calculates football stats for a specific player.
     *
     * @param playerId The ID of the player to retrieve stats for.
     * @return A Flow emitting [PlayerStatsDTO] if found, or null if not found.
     */
    fun getPlayerStats(playerId: Int): Flow<PlayerStatsDTO?> =
        combine(
            playerDao.getPlayerWithRoles(playerId),
            matchDao.getPlayerSportCounts(),
        ) { playerWithRoles, counts ->
            playerWithRoles?.let { toDto(it, counts) }
        }

    private fun toDto(
        playerWithRoles: PlayerWithRoles,
        counts: List<PlayerSportCount>,
    ): PlayerStatsDTO {
        val player = playerWithRoles.player
        val played = counts.playedIn(SportRegistry.FOOTBALL)[player.playerId] ?: 0
        val won = counts.wonIn(SportRegistry.FOOTBALL)[player.playerId] ?: 0
        return PlayerStatsDTO(
            playerId = player.playerId,
            playerName = player.playerName,
            goals = player.goals,
            appearances = played,
            winRate = if (played > 0) won.toFloat() / played else 0.0f,
            roles = playerWithRoles.roles,
        )
    }
}
