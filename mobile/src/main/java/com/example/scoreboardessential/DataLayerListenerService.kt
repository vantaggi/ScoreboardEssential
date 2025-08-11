package com.example.scoreboardessential

import com.example.scoreboardessential.utils.ScoreUpdateEvent
import com.example.scoreboardessential.utils.ScoreUpdateEventBus
import com.example.scoreboardessential.utils.TimerEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// A service that listens for messages from the Wear OS app.
class DataLayerListenerService : WearableListenerService() {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Called when a message is received from the Wear OS app.
    override fun onMessageReceived(messageEvent: MessageEvent) {
        coroutineScope.launch {
            when (messageEvent.path) {
                "/timer-control" -> {
                    val action = String(messageEvent.data)
                    when (action) {
                        "START" -> ScoreUpdateEventBus.postTimerEvent(TimerEvent.Start)
                        "PAUSE" -> ScoreUpdateEventBus.postTimerEvent(TimerEvent.Pause)
                        "RESET" -> ScoreUpdateEventBus.postTimerEvent(TimerEvent.Reset)
                    }
                }
                "/update-score" -> {
                    val data = String(messageEvent.data)
                    val scores = data.split(",").mapNotNull { it.toIntOrNull() }
                    if (scores.size == 2) {
                        ScoreUpdateEventBus.postEvent(ScoreUpdateEvent(scores[0], scores[1]))
                    }
                }
                // The other paths are no longer sent by the new watch face,
                // but we can leave them for now in case an older version is used.
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        // TODO: Handle data changes from the wear app if necessary (e.g., timer sync)
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}
