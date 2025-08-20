 package com.example.scoreboardessential

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.scoreboardessential.utils.ScoreUpdateEventBus
import com.example.scoreboardessential.utils.TimerEvent
import com.google.android.gms.wearable.MessageEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@RunWith(AndroidJUnit4::class)
class DataLayerListenerServiceTest {

    @Test
    fun testOnMessageReceived_timerStart_postsStartEvent() = runBlocking {
        // Arrange
        val service = DataLayerListenerService()
        val messageEvent = mock(MessageEvent::class.java)
        `when`(messageEvent.path).thenReturn("/timer-control")
        `when`(messageEvent.data).thenReturn("START".toByteArray())

        // Act
        service.onMessageReceived(messageEvent)

        // Assert
        val event = ScoreUpdateEventBus.timerEvents.first()
        assert(event is TimerEvent.Start)
    }

    @Test
    fun testOnMessageReceived_timerPause_postsPauseEvent() = runBlocking {
        // Arrange
        val service = DataLayerListenerService()
        val messageEvent = mock(MessageEvent::class.java)
        `when`(messageEvent.path).thenReturn("/timer-control")
        `when`(messageEvent.data).thenReturn("PAUSE".toByteArray())

        // Act
        service.onMessageReceived(messageEvent)

        // Assert
        val event = ScoreUpdateEventBus.timerEvents.first()
        assert(event is TimerEvent.Pause)
    }

    @Test
    fun testOnMessageReceived_timerReset_postsResetEvent() = runBlocking {
        // Arrange
        val service = DataLayerListenerService()
        val messageEvent = mock(MessageEvent::class.java)
        `when`(messageEvent.path).thenReturn("/timer-control")
        `when`(messageEvent.data).thenReturn("RESET".toByteArray())

        // Act
        service.onMessageReceived(messageEvent)

        // Assert
        val event = ScoreUpdateEventBus.timerEvents.first()
        assert(event is TimerEvent.Reset)
    }
}
