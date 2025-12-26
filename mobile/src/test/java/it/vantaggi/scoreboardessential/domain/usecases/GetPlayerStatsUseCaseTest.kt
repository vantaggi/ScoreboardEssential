package it.vantaggi.scoreboardessential.domain.usecases

import it.vantaggi.scoreboardessential.database.MatchDao
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerDao
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
        getPlayerStatsUseCase = GetPlayerStatsUseCase(playerDao, matchDao)
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
            whenever(playerDao.getTopScorers(10)).thenReturn(flowOf(playersWithRoles))

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

            // Act
            val result = getPlayerStatsUseCase.getPlayerStats(1).first()

            // Assert
            assertEquals(1, result?.playerId)
            assertEquals("Target Player", result?.playerName)
            assertEquals(8, result?.goals)
            assertEquals(20, result?.appearances)
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
