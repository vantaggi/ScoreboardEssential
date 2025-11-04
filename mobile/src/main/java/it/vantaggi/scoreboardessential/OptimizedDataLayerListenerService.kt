// mobile/src/main/java/com/example/scoreboardessential/OptimizedDataLayerListenerService.kt
package it.vantaggi.scoreboardessential

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Channel
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import it.vantaggi.scoreboardessential.utils.ScoreUpdateEvent
import it.vantaggi.scoreboardessential.utils.ScoreUpdateEventBus
import it.vantaggi.scoreboardessential.utils.TimerStateEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OptimizedDataLayerListenerService : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "OptimizedDataListener"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        Log.d(TAG, "Ricevuto messaggio: ${messageEvent.path}")

        // AGGIUNGI QUESTO LOG SPECIFICO:
        if (messageEvent.path.contains("timer")) {
            Log.d(TAG, "Timer message received from Wear!")
        }

        scope.launch {
            when (messageEvent.path) {
                WearConstants.MSG_HEARTBEAT -> {
                    // Respond to heartbeat
                    respondToHeartbeat(messageEvent.sourceNodeId)
                }

                WearConstants.MSG_SCORE_CHANGED -> {
                    handleScoreChange(messageEvent.data)
                }

                WearConstants.MSG_TIMER_ACTION -> {
                    handleTimerAction(messageEvent.data)
                }

                else -> {
                    Log.w(TAG, "Unknown message path: ${messageEvent.path}")
                }
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)

        // Freeze events per uso fuori dal buffer
        val events = dataEvents.map { it.freeze() }
        dataEvents.release()

        scope.launch {
            events.forEach { event ->
                Log.d(TAG, "Ricevuto evento dati: ${event.dataItem.uri.path}")
                when (event.type) {
                    DataEvent.TYPE_CHANGED -> handleDataChange(event.dataItem)
                    DataEvent.TYPE_DELETED -> handleDataDeleted(event.dataItem)
                }
            }
        }
    }

    override fun onChannelOpened(channel: Channel) {
        super.onChannelOpened(channel)
        Log.d(TAG, "Channel opened: ${channel.path}")

        when (channel.path) {
            WearConstants.CHANNEL_TIMER_STREAM -> {
                handleTimerStream(channel)
            }
        }
    }

    private fun handleDataChange(dataItem: DataItem) {
        Log.d(TAG, "Data changed: ${dataItem.uri.path}")

        when (dataItem.uri.path) {
            WearConstants.PATH_SCORE -> {
                val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                val team1Score = dataMap.getInt(WearConstants.KEY_TEAM1_SCORE)
                val team2Score = dataMap.getInt(WearConstants.KEY_TEAM2_SCORE)
                Log.d(TAG, "Dati punteggio deserializzati: T1=$team1Score, T2=$team2Score")

                // Post event to EventBus
                scope.launch(Dispatchers.Main) {
                    ScoreUpdateEventBus.postEvent(
                        ScoreUpdateEvent(team1Score, team2Score),
                    )
                }
            }
            WearConstants.PATH_TIMER_STATE -> {
                val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                val millis = dataMap.getLong(WearConstants.KEY_TIMER_MILLIS)
                val isRunning = dataMap.getBoolean(WearConstants.KEY_TIMER_RUNNING)
                Log.d(TAG, "Dati timer deserializzati: millis=$millis, isRunning=$isRunning")

                scope.launch(Dispatchers.Main) {
                    ScoreUpdateEventBus.postTimerStateEvent(
                        TimerStateEvent(millis, isRunning),
                    )
                }
            }
            // Altri path...
        }
    }

    private fun handleDataDeleted(dataItem: DataItem) {
        Log.d(TAG, "Data deleted: ${dataItem.uri.path}")
    }

    private suspend fun respondToHeartbeat(nodeId: String) {
        try {
            val messageClient = Wearable.getMessageClient(this)
            messageClient.sendMessage(
                nodeId,
                WearConstants.MSG_HEARTBEAT,
                "ACK".toByteArray(),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to respond to heartbeat", e)
        }
    }

    private fun handleTimerStream(channel: Channel) {
        scope.launch {
            try {
                val channelClient = Wearable.getChannelClient(this@OptimizedDataLayerListenerService)
                // The onChannelOpened callback provides a Channel interface, but the getOutputStream
                // method requires a ChannelClient.Channel object. We cast it here to make it compile.
                // This is a known issue when mixing the listener service with the client API.
                val outputStream = Tasks.await(channelClient.getOutputStream(channel as ChannelClient.Channel))

                // Stream timer data
                while (true) {
                    val timerData = getCurrentTimerMillis().toString().toByteArray()
                    outputStream.write(timerData)
                    outputStream.flush()
                    delay(100) // Update ogni 100ms
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error streaming timer data", e)
            }
        }
    }

    private fun handleScoreChange(data: ByteArray) {
        // Implementazione specifica
    }

    private fun handleTimerAction(data: ByteArray) {
        // Implementazione specifica
    }

    private fun getCurrentTimerMillis(): Long {
        // Ottieni il valore corrente del timer
        return System.currentTimeMillis()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
