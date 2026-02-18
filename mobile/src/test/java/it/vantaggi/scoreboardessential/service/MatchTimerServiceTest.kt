package it.vantaggi.scoreboardessential.service

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MatchTimerServiceTest {

    private lateinit var service: MatchTimerService

    @Mock
    private lateinit var mockConnectionManager: OptimizedWearDataSync

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        // Create the service using Robolectric
        service = Robolectric.buildService(MatchTimerService::class.java).create().get()

        // Use reflection to replace the private connectionManager
        val connectionManagerField = MatchTimerService::class.java.getDeclaredField("connectionManager")
        connectionManagerField.isAccessible = true
        connectionManagerField.set(service, mockConnectionManager)
    }

    @Test
    fun `startTimer sends data only once (Optimized)`() = runTest {
        // Start the timer
        service.startTimer()

        // Advance time to allow the loop to run a few times
        Thread.sleep(2500)

        // Verify sendData is called EXACTLY ONCE
        verify(mockConnectionManager, times(1)).sendData(
            path = anyString(),
            data = anyMap(),
            urgent = anyBoolean()
        )

        service.stopTimer()
    }

    @Test
    fun `startTimer sends data periodically (with shortened interval)`() = runTest {
        // Set short interval for testing
        val originalInterval = MatchTimerService.SYNC_INTERVAL
        MatchTimerService.SYNC_INTERVAL = 1000L

        try {
            service.startTimer()

            // Wait enough for at least 1 periodic sync (Start + >1s)
            Thread.sleep(2500)

            // Verify: Should be called multiple times (Start + Periodic)
            verify(mockConnectionManager, atLeast(2)).sendData(
                path = anyString(),
                data = anyMap(),
                urgent = anyBoolean()
            )
        } finally {
            MatchTimerService.SYNC_INTERVAL = originalInterval
            service.stopTimer()
        }
    }
}
