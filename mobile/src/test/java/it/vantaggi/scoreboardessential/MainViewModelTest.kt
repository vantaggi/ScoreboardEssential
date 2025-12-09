package it.vantaggi.scoreboardessential

import android.app.Application
import android.graphics.Color
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.repository.MatchRepository
import it.vantaggi.scoreboardessential.repository.MatchSettingsRepository
import it.vantaggi.scoreboardessential.repository.UserPreferencesRepository
import it.vantaggi.scoreboardessential.service.MatchTimerService
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Field

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MainViewModel
    private lateinit var mockApplication: Application
    private lateinit var mockRepository: MatchRepository
    private lateinit var mockUserPreferencesRepository: UserPreferencesRepository
    private lateinit var mockMatchSettingsRepository: MatchSettingsRepository
    private lateinit var mockMatchTimerService: MatchTimerService

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockApplication = mock(Application::class.java)
        mockRepository =
            mock(MatchRepository::class.java).apply {
                `when`(allMatches).thenReturn(emptyFlow())
            }
        mockUserPreferencesRepository =
            mock(UserPreferencesRepository::class.java).apply {
                `when`(hasSeenTutorial).thenReturn(emptyFlow())
            }
        mockMatchSettingsRepository = mock(MatchSettingsRepository::class.java)
        viewModel = MainViewModel(mockRepository, mockUserPreferencesRepository, mockMatchSettingsRepository, mockApplication)
        mockMatchTimerService = mock(MatchTimerService::class.java)

        // Use reflection to inject the mock service and set isServiceBound to true
        val serviceField: Field = MainViewModel::class.java.getDeclaredField("matchTimerService")
        serviceField.isAccessible = true
        serviceField.set(viewModel, mockMatchTimerService)

        val isBoundField: Field = MainViewModel::class.java.getDeclaredField("isServiceBound")
        isBoundField.isAccessible = true
        isBoundField.set(viewModel, true)
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

    @Test
    fun `addTeam1Score increments score and shows scorer dialog`() {
        // Arrange
        val scoreObserver = Observer<Int> {}
        val dialogObserver = Observer<Pair<Int, List<PlayerWithRoles>>> {}
        viewModel.team1Score.observeForever(scoreObserver)
        viewModel.showSelectScorerDialog.observeForever(dialogObserver)
        viewModel.addPlayerToTeam(PlayerWithRoles(Player(1, "Player 1", 0, 0), emptyList()), 1)

        // Act
        viewModel.addTeam1Score()

        // Assert
        assertEquals(1, viewModel.team1Score.value)
        assert(viewModel.showSelectScorerDialog.value != null)

        // Cleanup
        viewModel.team1Score.removeObserver(scoreObserver)
        viewModel.showSelectScorerDialog.removeObserver(dialogObserver)
    }

    @Test
    fun `addTeam2Score increments score and shows scorer dialog`() {
        // Arrange
        val scoreObserver = Observer<Int> {}
        val dialogObserver = Observer<Pair<Int, List<PlayerWithRoles>>> {}
        viewModel.team2Score.observeForever(scoreObserver)
        viewModel.showSelectScorerDialog.observeForever(dialogObserver)
        viewModel.addPlayerToTeam(PlayerWithRoles(Player(2, "Player 2", 0, 0), emptyList()), 2)

        // Act
        viewModel.addTeam2Score()

        // Assert
        assertEquals(1, viewModel.team2Score.value)
        assert(viewModel.showSelectScorerDialog.value != null)

        // Cleanup
        viewModel.team2Score.removeObserver(scoreObserver)
        viewModel.showSelectScorerDialog.removeObserver(dialogObserver)
    }

    @Test
    fun `subtractTeam1Score decrements score when greater than zero`() {
        // Arrange
        val scoreObserver = Observer<Int> {}
        viewModel.team1Score.observeForever(scoreObserver)
        viewModel.addTeam1Score() // Score is now 1

        // Act
        viewModel.subtractTeam1Score()

        // Assert
        assertEquals(0, viewModel.team1Score.value)

        // Cleanup
        viewModel.team1Score.removeObserver(scoreObserver)
    }

    @Test
    fun `subtractTeam2Score decrements score when greater than zero`() {
        // Arrange
        val scoreObserver = Observer<Int> {}
        viewModel.team2Score.observeForever(scoreObserver)
        viewModel.addTeam2Score() // Score is now 1

        // Act
        viewModel.subtractTeam2Score()

        // Assert
        assertEquals(0, viewModel.team2Score.value)

        // Cleanup
        viewModel.team2Score.removeObserver(scoreObserver)
    }

    @Test
    fun `subtractTeam1Score does not decrement score when zero`() {
        // Arrange
        val scoreObserver = Observer<Int> {}
        viewModel.team1Score.observeForever(scoreObserver)

        // Act
        viewModel.subtractTeam1Score()

        // Assert
        assertEquals(0, viewModel.team1Score.value)

        // Cleanup
        viewModel.team1Score.removeObserver(scoreObserver)
    }

    @Test
    fun `subtractTeam2Score does not decrement score when zero`() {
        // Arrange
        val scoreObserver = Observer<Int> {}
        viewModel.team2Score.observeForever(scoreObserver)

        // Act
        viewModel.subtractTeam2Score()

        // Assert
        assertEquals(0, viewModel.team2Score.value)

        // Cleanup
        viewModel.team2Score.removeObserver(scoreObserver)
    }

    @Test
    fun `subtractTeam1Score does not show scorer dialog`() {
        // Arrange
        val dialogObserver = Observer<Pair<Int, List<PlayerWithRoles>>> {}
        viewModel.showSelectScorerDialog.observeForever(dialogObserver)
        viewModel.addTeam1Score() // Score is now 1

        // Act
        viewModel.subtractTeam1Score()

        // Assert
        assert(viewModel.showSelectScorerDialog.value == null)

        // Cleanup
        viewModel.showSelectScorerDialog.removeObserver(dialogObserver)
    }

    @Test
    fun `subtractTeam2Score does not show scorer dialog`() {
        // Arrange
        val dialogObserver = Observer<Pair<Int, List<PlayerWithRoles>>> {}
        viewModel.showSelectScorerDialog.observeForever(dialogObserver)
        viewModel.addTeam2Score() // Score is now 1

        // Act
        viewModel.subtractTeam2Score()

        // Assert
        assert(viewModel.showSelectScorerDialog.value == null)

        // Cleanup
        viewModel.showSelectScorerDialog.removeObserver(dialogObserver)
    }

    @Test
    fun `setTeam1Name updates team1Name LiveData`() {
        // Arrange
        val nameObserver = Observer<String> {}
        viewModel.team1Name.observeForever(nameObserver)
        val newName = "New Team 1"

        // Act
        viewModel.setTeam1Name(newName)

        // Assert
        assertEquals(newName, viewModel.team1Name.value)

        // Cleanup
        viewModel.team1Name.removeObserver(nameObserver)
    }

    @Test
    fun `setTeam2Name updates team2Name LiveData`() {
        // Arrange
        val nameObserver = Observer<String> {}
        viewModel.team2Name.observeForever(nameObserver)
        val newName = "New Team 2"

        // Act
        viewModel.setTeam2Name(newName)

        // Assert
        assertEquals(newName, viewModel.team2Name.value)

        // Cleanup
        viewModel.team2Name.removeObserver(nameObserver)
    }

    @Test
    fun `startStopMatchTimer calls startTimer when timer is not running`() {
        // Arrange
        // Mock the internal live data or service behavior
        val isRunningLiveData = androidx.lifecycle.MutableLiveData<Boolean>()
        isRunningLiveData.value = false
        `when`(mockMatchTimerService.isMatchTimerRunning).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(false))

        // We also need to manipulate the ViewModel's internal LiveData which mirrors the service state
        // Reflection to set _isMatchTimerRunning
        val isMatchTimerRunningField = viewModel.javaClass.getDeclaredField("_isMatchTimerRunning")
        isMatchTimerRunningField.isAccessible = true
        val mutableLiveData = isMatchTimerRunningField.get(viewModel) as androidx.lifecycle.MutableLiveData<Boolean>
        mutableLiveData.postValue(false)

        // Act
        viewModel.startStopMatchTimer()

        // Assert
        verify(mockMatchTimerService).startTimer()
    }

    @Test
    fun `startStopMatchTimer calls pauseTimer when timer is running`() {
        // Arrange
        val isMatchTimerRunningField = viewModel.javaClass.getDeclaredField("_isMatchTimerRunning")
        isMatchTimerRunningField.isAccessible = true
        val mutableLiveData = isMatchTimerRunningField.get(viewModel) as androidx.lifecycle.MutableLiveData<Boolean>
        mutableLiveData.postValue(true)

        `when`(mockMatchTimerService.isMatchTimerRunning).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(true))

        // Act
        viewModel.startStopMatchTimer()

        // Assert
        verify(mockMatchTimerService).pauseTimer()
    }

    @Test
    fun `resetMatchTimer calls stopTimer`() {
        // Act
        viewModel.resetMatchTimer()

        // Assert
        verify(mockMatchTimerService).resetTimer()
    }

    @Test
    fun `startKeeperTimer calls startKeeperTimer on service`() {
        // Arrange
        val duration = 10000L
        viewModel.setKeeperTimer(duration / 1000)

        // Act
        viewModel.startKeeperTimer()

        // Assert
        verify(mockMatchTimerService).startKeeperTimer(duration)
    }

    @Test
    fun `resetKeeperTimer calls resetKeeperTimer on service`() {
        // Act
        viewModel.resetKeeperTimer()

        // Assert
        verify(mockMatchTimerService).resetKeeperTimer()
    }

    @Test
    fun `endMatch resets scores and stops timer`() =
        runTest {
            // Arrange
            val score1Observer = Observer<Int> {}
            val score2Observer = Observer<Int> {}
            val eventsObserver = Observer<List<MatchEvent>> {}
            viewModel.team1Score.observeForever(score1Observer)
            viewModel.team2Score.observeForever(score2Observer)
            viewModel.matchEvents.observeForever(eventsObserver)

            // Set initial scores by calling add score methods
            viewModel.addTeam1Score()
            viewModel.addTeam2Score()
            advanceUntilIdle() // Allow suspend functions in addScore to complete

            // Act
            viewModel.endMatch()
            advanceUntilIdle() // Allow suspend functions in endMatch to complete

            // Assert
            assertEquals(0, viewModel.team1Score.value)
            assertEquals(0, viewModel.team2Score.value)
            verify(mockMatchTimerService, atLeastOnce()).stopTimer()

            val matchEndedEvent = viewModel.matchEvents.value?.find { it.event.contains("Match ended") }
            assert(matchEndedEvent != null)

            // Cleanup
            viewModel.team1Score.removeObserver(score1Observer)
            viewModel.team2Score.removeObserver(score2Observer)
            viewModel.matchEvents.removeObserver(eventsObserver)
        }
}
