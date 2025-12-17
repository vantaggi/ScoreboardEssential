package it.vantaggi.scoreboardessential.domain.usecases

import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@ExperimentalCoroutinesApi
class UpdateScoreUseCaseTest {
    private lateinit var wearDataSync: OptimizedWearDataSync
    private lateinit var useCase: UpdateScoreUseCase

    @Before
    fun setup() {
        wearDataSync = mock()
        useCase = UpdateScoreUseCase(wearDataSync)
    }

    @Test
    fun `incrementScore for team 1 increases score by 1`() =
        runTest {
// When
            val result = useCase.incrementScore(1)

// Then
            assertTrue(result)
            assertEquals(1, useCase.scoreState.first().team1Score)
            assertEquals(0, useCase.scoreState.first().team2Score)
        }

    @Test
    fun `decrementScore does not go below zero`() =
        runTest {
// When
            val result = useCase.decrementScore(1)

// Then
            assertFalse(result)
            assertEquals(0, useCase.scoreState.first().team1Score)
        }

    @Test
    fun `incrementScore syncs with Wear`() =
        runTest {
// When
            useCase.incrementScore(1)

// Then
            val expectedData =
                mapOf(
                    WearConstants.KEY_TEAM1_SCORE to 1,
                    WearConstants.KEY_TEAM2_SCORE to 0,
                )
            verify(wearDataSync).sendData(
                eq(WearConstants.PATH_SCORE),
                eq(expectedData),
                eq(true),
            )
        }

    @Test
    fun `resetScores sets both scores to zero`() =
        runTest {
// Given
            useCase.incrementScore(1)
            useCase.incrementScore(2)

// When
            useCase.resetScores()

// Then
            assertEquals(0, useCase.scoreState.first().team1Score)
            assertEquals(0, useCase.scoreState.first().team2Score)
        }
}
