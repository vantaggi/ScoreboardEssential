// mobile/src/main/java/com/example/scoreboardessential/OptimizedDataLayerListenerService.kt
package com.example.scoreboardessential

import android.util.Log
import com.example.scoreboardessential.communication.WearConstants
import com.google.android.gms.wearable.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.example.scoreboardessential.utils.ScoreUpdateEvent
import com.example.scoreboardessential.utils.ScoreUpdateEventBus
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.delay

class OptimizedDataLayerListenerService : WearableListenerService() {

    private val TAG = "OptimizedDataListener"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        // Log per debugging
        Log.d(TAG, "Message received: ${messageEvent.path} from ${messageEvent.sourceNodeId}")

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

                // Post event to EventBus
                scope.launch(Dispatchers.Main) {
                    ScoreUpdateEventBus.postEvent(
                        ScoreUpdateEvent(team1Score, team2Score)
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
                "ACK".toByteArray()
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
