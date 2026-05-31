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
import org.junit.Assert.assertEquals
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

    @Test
    fun `syncMatchTimer updates state and controls internal timer`() =
        runTest {
            // Arrange
            val timeMillis = 60000L // 1 minute
            val expectedTime = "01:00"

            // Act: Start Timer
            viewModel.syncMatchTimer(timeMillis, true)

            // Assert
            assertTrue("Timer value should be updated", viewModel.matchTimer.value == expectedTime)

            // Access private field to check if job is active
            val timerJobField = WearViewModel::class.java.getDeclaredField("matchTimerJob")
            timerJobField.isAccessible = true
            val job = timerJobField.get(viewModel) as? kotlinx.coroutines.Job
            assertTrue("Timer job should be active", job?.isActive == true)

            // Act: Stop Timer
            viewModel.syncMatchTimer(timeMillis, false)

            // Assert
            val jobStopped = timerJobField.get(viewModel) as? kotlinx.coroutines.Job
            assertTrue("Timer job should be cancelled", jobStopped?.isActive == false || jobStopped?.isCancelled == true)
        }

    @Test
    fun `resetMatchTimer resets timer state`() =
        runTest {
            // Arrange
            viewModel.syncMatchTimer(60000L, true) // 01:00, running

            // Act
            viewModel.resetMatchTimer()

            // Assert
            assertEquals("00:00", viewModel.matchTimer.value)

            val matchTimeField = WearViewModel::class.java.getDeclaredField("matchTimeInSeconds")
            matchTimeField.isAccessible = true
            assertEquals(0L, matchTimeField.get(viewModel))

            val isRunningField = WearViewModel::class.java.getDeclaredField("isMatchTimerRunning")
            isRunningField.isAccessible = true
            assertEquals(false, isRunningField.get(viewModel))

            val timerJobField = WearViewModel::class.java.getDeclaredField("matchTimerJob")
            timerJobField.isAccessible = true
            val job = timerJobField.get(viewModel) as? kotlinx.coroutines.Job
            assertTrue("Timer job should be cancelled", job?.isActive != true)
        }

    @Test
    fun `resetMatch resets scores and timers`() =
        runTest {
            // Arrange
            viewModel.updateScoresFromMobile(2, 3)
            viewModel.syncMatchTimer(60000L, true)

            // Act
            viewModel.resetMatch(fromRemote = true)

            // Assert
            assertEquals(0, viewModel.team1Score.value)
            assertEquals(0, viewModel.team2Score.value)
            assertEquals("00:00", viewModel.matchTimer.value)

            val matchTimeField = WearViewModel::class.java.getDeclaredField("matchTimeInSeconds")
            matchTimeField.isAccessible = true
            assertEquals(0L, matchTimeField.get(viewModel))

            val isRunningField = WearViewModel::class.java.getDeclaredField("isMatchTimerRunning")
            isRunningField.isAccessible = true
            assertEquals(false, isRunningField.get(viewModel))
        }
}
