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
@Config(sdk = [33], manifest = Config.NONE)
class WearViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var application: Application

    @Mock
    private lateinit var vibrator: Vibrator

    private lateinit var viewModel: WearViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        MockitoAnnotations.openMocks(this)

        Mockito.`when`(application.getSystemService(Context.VIBRATOR_SERVICE)).thenReturn(vibrator)
        Mockito.`when`(application.applicationContext).thenReturn(application)

        try {
            viewModel = WearViewModel(application)
        } catch (e: Exception) {
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
}
