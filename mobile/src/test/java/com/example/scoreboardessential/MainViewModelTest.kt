package com.example.scoreboardessential

import android.graphics.Color
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.example.scoreboardessential.repository.MatchRepository
import com.example.scoreboardessential.utils.ScoreUpdateEventBus
import com.example.scoreboardessential.utils.TimerEvent
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

import android.app.Application
import org.mockito.ArgumentMatchers.any

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MainViewModel
    private lateinit var mockApplication: Application
    private lateinit var mockRepository: MatchRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockApplication = mock(Application::class.java)
        mockRepository = mock(MatchRepository::class.java).apply {
            `when`(allMatches).thenReturn(emptyFlow())
        }
        viewModel = MainViewModel(mockRepository, mockApplication)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setTeam1Color updates team1Color LiveData`() {
        // Arrange
        val colorObserver = Observer<Int> {}
        viewModel.team1Color.observeForever(colorObserver)
        val color = Color.RED

        // Act
        viewModel.setTeamColor(1, color)

        // Assert
        assertEquals(color, viewModel.team1Color.value)
        viewModel.team1Color.removeObserver(colorObserver)
    }

    @Test
    fun `setTeam2Color updates team2Color LiveData`() {
        // Arrange
        val colorObserver = Observer<Int> {}
        viewModel.team2Color.observeForever(colorObserver)
        val color = Color.BLUE

        // Act
        viewModel.setTeamColor(2, color)

        // Assert
        assertEquals(color, viewModel.team2Color.value)
        viewModel.team2Color.removeObserver(colorObserver)
    }

    @Test
    fun `timer event Start starts match timer`() = runTest {
        // Arrange
        val timerObserver = Observer<Long> {}
        viewModel.matchTimerValue.observeForever(timerObserver)

        // Act
        ScoreUpdateEventBus.postTimerEvent(TimerEvent.Start)
        advanceTimeBy(1000) // Advance time to allow the timer to tick
        advanceUntilIdle()

        // Assert
        assert(viewModel.matchTimerValue.value!! > 0)
        viewModel.matchTimerValue.removeObserver(timerObserver)
    }

    @Test
    fun `timer event Pause pauses match timer`() = runTest {
        // Arrange
        val timerObserver = Observer<Long> {}
        viewModel.matchTimerValue.observeForever(timerObserver)

        viewModel.startStopMatchTimer() // Start it first
        advanceTimeBy(1000)
        advanceUntilIdle()
        val timeWhenPaused = viewModel.matchTimerValue.value

        // Act
        ScoreUpdateEventBus.postTimerEvent(TimerEvent.Pause)
        advanceTimeBy(1000)
        advanceUntilIdle()

        // Assert
        assertEquals(timeWhenPaused, viewModel.matchTimerValue.value)
        viewModel.matchTimerValue.removeObserver(timerObserver)
    }

    @Test
    fun `timer event Reset resets match timer`() = runTest {
        // Arrange
        val timerObserver = Observer<Long> {}
        viewModel.matchTimerValue.observeForever(timerObserver)

        viewModel.startStopMatchTimer() // Start it first
        advanceTimeBy(1000)
        advanceUntilIdle()

        // Act
        ScoreUpdateEventBus.postTimerEvent(TimerEvent.Reset)
        advanceUntilIdle()

        // Assert
        assertEquals(0L, viewModel.matchTimerValue.value)
        viewModel.matchTimerValue.removeObserver(timerObserver)
    }

    @Test
    fun `service should unbind correctly on viewmodel clear`() {
        // Arrange
        // Use reflection to set isServiceBound to true
        val isServiceBoundField = viewModel::class.java.getDeclaredField("isServiceBound")
        isServiceBoundField.isAccessible = true
        isServiceBoundField.set(viewModel, true)

        // Act
        // Call protected onCleared() method using reflection
        val onClearedMethod = viewModel::class.java.superclass.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(viewModel)

        // Assert
        verify(mockApplication, times(1)).unbindService(any())
    }
}
