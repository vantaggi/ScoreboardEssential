package it.vantaggi.scoreboardessential.domain.usecases

import it.vantaggi.scoreboardessential.database.MatchDao
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerDao
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.domain.model.PlayerStatsDTO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class GetPlayerStatsUseCaseReactiveTest {
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
    fun getPlayerStats_emitsUpdates() =
        runTest {
            // Arrange
            val flow = MutableSharedFlow<PlayerWithRoles?>(replay = 1)
            val player1 = Player(playerId = 1, playerName = "Player 1", appearances = 10, goals = 5)
            flow.tryEmit(PlayerWithRoles(player1, emptyList()))

            whenever(playerDao.getPlayerWithRoles(1)).thenReturn(flow)

            val results = mutableListOf<PlayerStatsDTO?>()
            val job =
                launch {
                    getPlayerStatsUseCase.getPlayerStats(1).collect {
                        results.add(it)
                    }
                }
            advanceUntilIdle()

            // Assert initial state
            assertEquals(1, results.size)
            assertEquals(5, results[0]?.goals)

            // Act: Update flow
            val playerUpdated = Player(playerId = 1, playerName = "Player 1", appearances = 11, goals = 6)
            flow.emit(PlayerWithRoles(playerUpdated, emptyList()))
            advanceUntilIdle()

            // Assert update received
            // Fails if flow completes after first emission
            assertEquals("Should emit update", 2, results.size)
            assertEquals(6, results[1]?.goals)

            job.cancel()
        }
}
