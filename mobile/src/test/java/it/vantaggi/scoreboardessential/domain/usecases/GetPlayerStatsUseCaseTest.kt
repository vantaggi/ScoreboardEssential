package it.vantaggi.scoreboardessential.domain.usecases

import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.MatchDao
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerDao
import it.vantaggi.scoreboardessential.database.PlayerSportCount
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class GetPlayerStatsUseCaseTest {
    private lateinit var playerDao: PlayerDao
    private lateinit var matchDao: MatchDao
    private lateinit var getPlayerStatsUseCase: GetPlayerStatsUseCase

    @Before
    fun setup() {
        playerDao = mock()
        matchDao = mock()
        whenever(matchDao.getPlayerSportCounts()).thenReturn(flowOf(emptyList()))
        getPlayerStatsUseCase = GetPlayerStatsUseCase(playerDao, matchDao)
    }

    @Test
    fun getTopScorers_computesWinRateFromFootballCounts() =
        runTest {
            val player = Player(playerId = 1, playerName = "Player 1", appearances = 30, goals = 5)
            whenever(playerDao.getTopScorers(Int.MAX_VALUE))
                .thenReturn(flowOf(listOf(PlayerWithRoles(player, emptyList()))))
            // 10 partite di calcio, 4 vinte; il padel (20 partite, 20 vinte) non entra nel calcio.
            whenever(matchDao.getPlayerSportCounts())
                .thenReturn(
                    flowOf(
                        listOf(
                            PlayerSportCount(1, SportRegistry.FOOTBALL, played = 10, won = 4),
                            PlayerSportCount(1, SportRegistry.PADEL, played = 20, won = 20),
                        ),
                    ),
                )

            val result = getPlayerStatsUseCase.getTopScorers(10).first()

            assertEquals(0.4f, result[0].winRate, 0.0001f)
            assertEquals(10, result[0].appearances)
        }

    @Test
    fun getTopScorers_leavesOutPlayersWhoNeverPlayedFootball() =
        runTest {
            val footballer = Player(playerId = 1, playerName = "Calciatore", appearances = 1, goals = 0)
            val padelOnly = Player(playerId = 2, playerName = "Solo padel", appearances = 8, goals = 0)
            whenever(playerDao.getTopScorers(Int.MAX_VALUE))
                .thenReturn(flowOf(listOf(PlayerWithRoles(padelOnly, emptyList()), PlayerWithRoles(footballer, emptyList()))))
            whenever(matchDao.getPlayerSportCounts())
                .thenReturn(
                    flowOf(
                        listOf(
                            PlayerSportCount(1, SportRegistry.FOOTBALL, played = 1, won = 0),
                            PlayerSportCount(2, SportRegistry.PADEL, played = 8, won = 5),
                        ),
                    ),
                )

            val result = getPlayerStatsUseCase.getTopScorers(10).first()

            assertEquals(listOf("Calciatore"), result.map { it.playerName })
        }

    @Test
    fun getTopScorers_ordersByGoalsThenFewerFootballMatchesAndCutsAtLimit() =
        runTest {
            val a = Player(playerId = 1, playerName = "A", appearances = 0, goals = 3)
            val b = Player(playerId = 2, playerName = "B", appearances = 0, goals = 3)
            val c = Player(playerId = 3, playerName = "C", appearances = 0, goals = 1)
            whenever(playerDao.getTopScorers(Int.MAX_VALUE))
                .thenReturn(flowOf(listOf(a, b, c).map { PlayerWithRoles(it, emptyList()) }))
            whenever(matchDao.getPlayerSportCounts())
                .thenReturn(
                    flowOf(
                        listOf(
                            PlayerSportCount(1, SportRegistry.FOOTBALL, played = 5, won = 0),
                            PlayerSportCount(2, SportRegistry.FOOTBALL, played = 2, won = 0),
                            PlayerSportCount(3, SportRegistry.FOOTBALL, played = 1, won = 0),
                        ),
                    ),
                )

            val result = getPlayerStatsUseCase.getTopScorers(2).first()

            assertEquals(listOf("B", "A"), result.map { it.playerName })
        }

    @Test
    fun getTopScorers_returnsMappedPlayerStats() =
        runTest {
            // Arrange
            val player1 = Player(playerId = 1, playerName = "Player 1", appearances = 10, goals = 5)
            val player2 = Player(playerId = 2, playerName = "Player 2", appearances = 5, goals = 2)
            val playersWithRoles =
                listOf(
                    PlayerWithRoles(player1, emptyList()),
                    PlayerWithRoles(player2, emptyList()),
                )
            whenever(playerDao.getTopScorers(Int.MAX_VALUE)).thenReturn(flowOf(playersWithRoles))
            whenever(matchDao.getPlayerSportCounts())
                .thenReturn(
                    flowOf(
                        listOf(
                            PlayerSportCount(1, SportRegistry.FOOTBALL, played = 10, won = 0),
                            PlayerSportCount(2, SportRegistry.FOOTBALL, played = 5, won = 0),
                        ),
                    ),
                )

            // Act
            val result = getPlayerStatsUseCase.getTopScorers(10).first()

            // Assert
            assertEquals(2, result.size)
            assertEquals(1, result[0].playerId)
            assertEquals("Player 1", result[0].playerName)
            assertEquals(5, result[0].goals)
            assertEquals(10, result[0].appearances)
            assertEquals(0.0f, result[0].winRate, 0.0f)

            assertEquals(2, result[1].playerId)
            assertEquals("Player 2", result[1].playerName)
        }

    @Test
    fun getPlayerStats_existingPlayer_returnsStats() =
        runTest {
            // Arrange
            val player = Player(playerId = 1, playerName = "Target Player", appearances = 20, goals = 8)
            val playerWithRoles = PlayerWithRoles(player, emptyList())
            whenever(playerDao.getPlayerWithRoles(1)).thenReturn(flowOf(playerWithRoles))
            whenever(matchDao.getPlayerSportCounts())
                .thenReturn(flowOf(listOf(PlayerSportCount(1, SportRegistry.FOOTBALL, played = 12, won = 0))))

            // Act
            val result = getPlayerStatsUseCase.getPlayerStats(1).first()

            // Assert
            assertEquals(1, result?.playerId)
            assertEquals("Target Player", result?.playerName)
            assertEquals(8, result?.goals)
            assertEquals(12, result?.appearances)
            assertEquals(0.0f, result?.winRate)
        }

    @Test
    fun getPlayerStats_nonExistingPlayer_returnsNull() =
        runTest {
            // Arrange
            whenever(playerDao.getPlayerWithRoles(99)).thenReturn(flowOf(null))

            // Act
            val result = getPlayerStatsUseCase.getPlayerStats(99).first()

            // Assert
            assertNull(result)
        }
}
