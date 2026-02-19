package it.vantaggi.scoreboardessential

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ApplicationProvider
import it.vantaggi.scoreboardessential.database.MatchDao
import it.vantaggi.scoreboardessential.database.PlayerDao
import it.vantaggi.scoreboardessential.repository.MatchRepository
import it.vantaggi.scoreboardessential.repository.MatchSettingsRepository
import it.vantaggi.scoreboardessential.repository.UserPreferencesRepository
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class MainViewModelEdgeCasesTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MainViewModel
    private lateinit var application: Application
    private lateinit var mockRepository: MatchRepository
    private lateinit var mockUserPreferencesRepository: UserPreferencesRepository
    private lateinit var mockMatchSettingsRepository: MatchSettingsRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()

        mockRepository = mock(MatchRepository::class.java).apply {
            whenever(allMatches).thenReturn(emptyFlow())
        }
        mockUserPreferencesRepository = mock(UserPreferencesRepository::class.java).apply {
            whenever(hasSeenTutorial).thenReturn(emptyFlow())
        }
        mockMatchSettingsRepository = mock(MatchSettingsRepository::class.java).apply {
            whenever(getSettingsFlow()).thenReturn(emptyFlow())
        }

        viewModel = MainViewModel(mockRepository, mockUserPreferencesRepository, mockMatchSettingsRepository, application)

        // Inject mock DAOs and ConnectionManager to avoid NPEs
        val playerDaoField = MainViewModel::class.java.getDeclaredField("playerDao")
        playerDaoField.isAccessible = true
        playerDaoField.set(viewModel, mock(PlayerDao::class.java).apply {
            whenever(getAllPlayers()).thenReturn(emptyFlow())
        })

        val matchDaoField = MainViewModel::class.java.getDeclaredField("matchDao")
        matchDaoField.isAccessible = true
        matchDaoField.set(viewModel, mock(MatchDao::class.java))

        val connectionManagerField = MainViewModel::class.java.getDeclaredField("connectionManager")
        connectionManagerField.isAccessible = true
        val mockConnectionManager = mock(OptimizedWearDataSync::class.java)
        whenever(mockConnectionManager.connectionState).thenReturn(MutableStateFlow(it.vantaggi.scoreboardessential.shared.communication.ConnectionState.Disconnected))
        connectionManagerField.set(viewModel, mockConnectionManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        val onClearedMethod = androidx.lifecycle.ViewModel::class.java.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(viewModel)
    }

    @Test
    fun `undoLastGoal with empty stack should not crash`() {
        viewModel.undoLastGoal()
        assertEquals(false, viewModel.canUndo.value)
    }

    @Test
    fun `startStopMatchTimer without service bound should be safe`() {
        viewModel.startStopMatchTimer()
        // No crash expected
    }

    @Test
    fun `receive score from Wear should update LiveData`() {
        // Arrange
        val intent = Intent(SimplifiedDataLayerListenerService.ACTION_SCORE_UPDATE).apply {
            putExtra(WearConstants.KEY_TEAM1_SCORE, 10)
            putExtra(WearConstants.KEY_TEAM2_SCORE, 5)
        }

        val observer = Observer<Int> {}
        viewModel.team1Score.observeForever(observer)
        viewModel.team2Score.observeForever(observer)

        // Act
        LocalBroadcastManager.getInstance(application).sendBroadcastSync(intent)

        // Assert
        assertEquals(10, viewModel.team1Score.value)
        assertEquals(5, viewModel.team2Score.value)

        viewModel.team1Score.removeObserver(observer)
        viewModel.team2Score.removeObserver(observer)
    }
}
