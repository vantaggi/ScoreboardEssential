package it.vantaggi.scoreboardessential.wear

import android.app.Application
import it.vantaggi.scoreboardessential.shared.PlayerData
import it.vantaggi.scoreboardessential.shared.communication.ConnectionState
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WearViewModelTest {
    private lateinit var viewModel: WearViewModel
    private lateinit var connectionManager: OptimizedWearDataSync
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Mock the sync engine so no real Wearable services are touched.
        connectionManager = Mockito.mock(OptimizedWearDataSync::class.java)
        Mockito
            .lenient()
            .`when`(connectionManager.connectionState)
            .thenReturn(MutableStateFlow<ConnectionState>(ConnectionState.Disconnected))

        val app: Application = RuntimeEnvironment.getApplication()
        viewModel = WearViewModel(app, connectionManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `score increment increments team 1 score`() =
        runTest {
            viewModel.incrementScore(1)
            advanceUntilIdle()
            assertEquals(1, viewModel.team1Score.value)
        }

    @Test
    fun `score decrement does not go below zero`() =
        runTest {
            viewModel.decrementScore(1)
            advanceUntilIdle()
            assertEquals(0, viewModel.team1Score.value)
        }

    @Test
    fun `keeper toggle starts the keeper timer`() =
        runTest {
            viewModel.toggleKeeperTimer()
            advanceUntilIdle()
            assertTrue(viewModel.keeperTimer.value is KeeperTimerState.Running)
        }

    @Test
    fun `scoring with a roster requests scorer selection`() =
        runTest {
            viewModel.setAllPlayers(listOf(PlayerData(1, "Alice", listOf("Forward"))))
            assertEquals(1, viewModel.allPlayers.value.size)
            viewModel.incrementScore(2)
            assertEquals(
                "showPlayerSelection should equal the scoring team",
                2,
                viewModel.showPlayerSelection.value,
            )
        }

    @Test
    fun `scoring with no roster does not request scorer selection`() =
        runTest {
            viewModel.incrementScore(1)
            advanceUntilIdle()
            assertNull(viewModel.showPlayerSelection.value)
        }
}
