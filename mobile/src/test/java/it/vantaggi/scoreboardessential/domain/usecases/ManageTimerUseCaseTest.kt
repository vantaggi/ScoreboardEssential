package it.vantaggi.scoreboardessential.domain.usecases

import it.vantaggi.scoreboardessential.service.MatchTimerService
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class ManageTimerUseCaseTest {
    private lateinit var timerService: MatchTimerService
    private lateinit var wearDataSync: OptimizedWearDataSync
    private lateinit var useCase: ManageTimerUseCase

    // Flows to mock service behavior
    private val isRunningFlow = MutableStateFlow(false)
    private val timeMillisFlow = MutableStateFlow(0L)

    @Before
    fun setup() {
        timerService = mock()
        wearDataSync = mock()

        // Stub flows
        whenever(timerService.isMatchTimerRunning).thenReturn(isRunningFlow)
        whenever(timerService.matchTimerValue).thenReturn(timeMillisFlow)

        // Reset flows
        isRunningFlow.value = false
        timeMillisFlow.value = 0L

        // Setup mock behavior to update flows when methods are called
        // Note: startTimer, pauseTimer, and stopTimer in MatchTimerService return Unit (or void in Java),
        // so we can't stub them with 'whenever(...).doAnswer' if they are final.
        // However, MatchTimerService is likely open or we are using mockito-inline.
        // The issue 'InvalidUseOfMatchersException' often comes from argument matchers used incorrectly or outside stubbing.
        // Here we are using 'doAnswer' on void methods. The syntax `whenever(mock.voidMethod()).doAnswer` is incorrect for void methods.
        // It should be `doAnswer { ... }.whenever(mock).voidMethod()`.

        doAnswer {
            isRunningFlow.value = true
            null
        }.whenever(timerService).startTimer()

        doAnswer {
            isRunningFlow.value = false
            null
        }.whenever(timerService).pauseTimer()

        doAnswer {
            isRunningFlow.value = false
            timeMillisFlow.value = 0L
            null
        }.whenever(timerService).stopTimer()

        doAnswer {
            timeMillisFlow.value = it.getArgument(0)
            null
        }.whenever(timerService).updateMatchTimer(any())

        useCase = ManageTimerUseCase(timerService, wearDataSync)
    }

    @Test
    fun `startOrPauseTimer starts timer when not running`() =
        runTest {
            // Given
            isRunningFlow.value = false

            // When
            useCase.startOrPauseTimer()

            // Then
            verify(timerService).startTimer()
            assertTrue(useCase.timerState.first().isRunning)
        }

    @Test
    fun `startOrPauseTimer pauses timer when running`() =
        runTest {
            // Given
            isRunningFlow.value = true
            useCase.startOrPauseTimer() // This should pause it

            // Then
            verify(timerService).pauseTimer()
            assertFalse(useCase.timerState.first().isRunning)
        }

    @Test
    fun `resetTimer stops timer and resets state`() =
        runTest {
            // When
            useCase.resetTimer()

            // Then
            verify(timerService).stopTimer()
            assertFalse(useCase.timerState.first().isRunning)
            assertEquals(0L, useCase.timerState.first().timeMillis)
        }

    @Test
    fun `updateTimerValue updates timeMillis`() =
        runTest {
            // When
            useCase.updateTimerValue(1000L)

            // Then
            verify(timerService).updateMatchTimer(1000L)
            assertEquals(1000L, useCase.timerState.first().timeMillis)
        }

    @Test
    fun `setTimerRunning updates isRunning`() =
        runTest {
            // When
            useCase.setTimerRunning(true)

            // Then
            verify(timerService).startTimer()
            assertTrue(useCase.timerState.first().isRunning)
        }
}
