package it.vantaggi.scoreboardessential.database

import it.vantaggi.scoreboardessential.domain.usecases.GetPlayerStatsUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations

class GetPlayerStatsUseCaseTest {
    @Mock
    private lateinit var playerDao: PlayerDao

    @Mock
    private lateinit var matchDao: MatchDao

    private lateinit var getPlayerStatsUseCase: GetPlayerStatsUseCase

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        getPlayerStatsUseCase = GetPlayerStatsUseCase(playerDao, matchDao)
    }

    @Test
    fun `getTopScorers returns correct list sorted by goals`() =
        runBlocking {
            // Given
            val player1 = Player(1, "Player 1", 10, 5)
            val player2 = Player(2, "Player 2", 8, 3)
            val players = listOf(player1, player2)

            `when`(playerDao.getTopScorers(10)).thenReturn(flowOf(players))

            // When
            val result = getPlayerStatsUseCase.getTopScorers(10).first()

            // Then
            assertEquals(2, result.size)
            assertEquals("Player 1", result[0].playerName)
            assertEquals(5, result[0].goals)
            assertEquals("Player 2", result[1].playerName)
            assertEquals(3, result[1].goals)
        }

    @Test
    fun `getPlayerStats returns correct stats for a player`() =
        runBlocking {
            // Given
            val player = Player(1, "Player 1", 10, 5)
            val playerWithRoles = PlayerWithRoles(player, emptyList())

            `when`(playerDao.getPlayerWithRoles(1)).thenReturn(flowOf(playerWithRoles))

            // When
            val result = getPlayerStatsUseCase.getPlayerStats(1).first()

            // Then
            assertEquals("Player 1", result?.playerName)
            assertEquals(5, result?.goals)
            assertEquals(10, result?.appearances)
        }
}
