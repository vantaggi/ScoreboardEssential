package it.vantaggi.scoreboardessential.wear

import android.app.Application
import android.content.Context
import android.os.Vibrator
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WearViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var application: Application

    @Mock
    private lateinit var vibrator: Vibrator

    @Mock
    private lateinit var packageManager: android.content.pm.PackageManager

    private lateinit var viewModel: WearViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        MockitoAnnotations.openMocks(this)

        Mockito.`when`(application.getSystemService(Context.VIBRATOR_SERVICE)).thenReturn(vibrator)
        Mockito.`when`(application.packageManager).thenReturn(packageManager)
        Mockito.`when`(packageManager.hasSystemFeature(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(application.applicationContext).thenReturn(application)

        try {
            viewModel = WearViewModel(application)
        } catch (e: Exception) {
            e.printStackTrace()
            // Ignore initialization errors for DataClient if possible
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setKeeperTimerState DOES NOT restart timer when update is small`() =
        runTest {
            // Arrange

            // Act
            viewModel.setKeeperTimerState(KeeperTimerState.Running(10))

            val timerField = WearViewModel::class.java.getDeclaredField("keeperCountDownTimer")
            timerField.isAccessible = true
            val timer1 = timerField.get(viewModel)

            // Act again with same value
            viewModel.setKeeperTimerState(KeeperTimerState.Running(10))
            val timer2 = timerField.get(viewModel)

            // Assert: Timer should be the SAME instance
            assertTrue("Timer should NOT be recreated on small/identical update", timer1 === timer2)

            // Act again with small difference (1 second)
            // Note: Running(9) vs Running(10) is 1 sec diff.
            // Wait, if 10 is current, and we receive 9. 10-9 = 1. 1 < 2. Should be ignored?
            // Yes, if we receive an update that is very close, we assume our local timer is fine.
            viewModel.setKeeperTimerState(KeeperTimerState.Running(9))
            val timer3 = timerField.get(viewModel)
            assertTrue("Timer should NOT be recreated on small update", timer1 === timer3)
        }

    @Test
    fun `setKeeperTimerState restarts timer when update is large`() =
        runTest {
            // Arrange
            viewModel.setKeeperTimerState(KeeperTimerState.Running(10))

            val timerField = WearViewModel::class.java.getDeclaredField("keeperCountDownTimer")
            timerField.isAccessible = true
            val timer1 = timerField.get(viewModel)

            // Act again with large difference (5 seconds)
            viewModel.setKeeperTimerState(KeeperTimerState.Running(5))
            val timer2 = timerField.get(viewModel)

            // Assert: Timer should be a NEW instance
            assertTrue("Timer SHOULD be recreated on large update", timer1 !== timer2)
        }

    @Test
    fun `incrementScore increases team score`() =
        runTest {
            // Arrange
            viewModel.updateScoresFromMobile(0, 0)

            // Act
            viewModel.incrementScore(1)
            viewModel.incrementScore(2)
            viewModel.incrementScore(2)

            // Assert
            assertTrue("Team 1 score should be 1", viewModel.team1Score.value == 1)
            assertTrue("Team 2 score should be 2", viewModel.team2Score.value == 2)
        }

    @Test
    fun `decrementScore decreases team score but not below zero`() =
        runTest {
            // Arrange
            viewModel.updateScoresFromMobile(2, 1)

            // Act
            viewModel.decrementScore(1)
            viewModel.decrementScore(2)
            viewModel.decrementScore(2)
            viewModel.decrementScore(2) // Should stay at 0

            // Assert
            assertTrue("Team 1 score should be 1", viewModel.team1Score.value == 1)
            assertTrue("Team 2 score should be 0", viewModel.team2Score.value == 0)
        }

    @Test
    fun `incrementScore with invalid team ID does not change scores`() =
        runTest {
            // Arrange
            viewModel.updateScoresFromMobile(0, 0)

            // Act
            viewModel.incrementScore(3) // Invalid team ID

            // Assert
            assertTrue("Team 1 score should remain 0", viewModel.team1Score.value == 0)
            assertTrue("Team 2 score should remain 0", viewModel.team2Score.value == 0)
        }
}
