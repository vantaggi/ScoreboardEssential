package it.vantaggi.scoreboardessential

import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.MessageEvent
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
class SyncRequestTest {
    private lateinit var service: SimplifiedDataLayerListenerService

    @Before
    fun setup() {
        service = Robolectric.buildService(SimplifiedDataLayerListenerService::class.java).create().get()
    }

    @Test
    fun `onMessageReceived with MSG_REQUEST_SYNC broadcasts ACTION_REQUEST_SYNC`() {
        // Arrange
        println("WearConstants.MSG_REQUEST_SYNC = ${WearConstants.MSG_REQUEST_SYNC}")
        val messageEvent = mock(MessageEvent::class.java)
        `when`(messageEvent.path).thenReturn(WearConstants.MSG_REQUEST_SYNC)
        println("messageEvent.path = ${messageEvent.path}")

        var received = false
        val receiver =
            object : android.content.BroadcastReceiver() {
                override fun onReceive(
                    context: android.content.Context?,
                    intent: Intent?,
                ) {
                    println("Test Receiver got intent: ${intent?.action}")
                    if (intent?.action == SimplifiedDataLayerListenerService.ACTION_REQUEST_SYNC) {
                        received = true
                    }
                }
            }
        LocalBroadcastManager
            .getInstance(
                service,
            ).registerReceiver(receiver, android.content.IntentFilter(SimplifiedDataLayerListenerService.ACTION_REQUEST_SYNC))

        // Act
        service.onMessageReceived(messageEvent)
        ShadowLooper.idleMainLooper()

        // Assert
        assertTrue("Should have received ACTION_REQUEST_SYNC broadcast", received)
        LocalBroadcastManager.getInstance(service).unregisterReceiver(receiver)
    }

    @Test
    fun `onMessageReceived with other message does not broadcast`() {
        // Arrange
        val messageEvent = mock(MessageEvent::class.java)
        `when`(messageEvent.path).thenReturn("/other/path")

        var received = false
        val receiver =
            object : android.content.BroadcastReceiver() {
                override fun onReceive(
                    context: android.content.Context?,
                    intent: Intent?,
                ) {
                    if (intent?.action == SimplifiedDataLayerListenerService.ACTION_REQUEST_SYNC) {
                        received = true
                    }
                }
            }
        LocalBroadcastManager
            .getInstance(
                service,
            ).registerReceiver(receiver, android.content.IntentFilter(SimplifiedDataLayerListenerService.ACTION_REQUEST_SYNC))

        // Act
        service.onMessageReceived(messageEvent)

        // Assert
        assertFalse("Should NOT have received ACTION_REQUEST_SYNC broadcast", received)
        LocalBroadcastManager.getInstance(service).unregisterReceiver(receiver)
    }
}
