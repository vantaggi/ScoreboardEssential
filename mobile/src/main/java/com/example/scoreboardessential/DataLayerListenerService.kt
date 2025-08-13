// Updated DataLayerListenerService.kt for mobile app:

package com.example.scoreboardessential

import com.example.scoreboardessential.utils.ScoreUpdateEvent
import com.example.scoreboardessential.utils.ScoreUpdateEventBus
import com.example.scoreboardessential.utils.TimerEvent
import com.google.android.gms.wearable.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.util.Log

class DataLayerListenerService : WearableListenerService() {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val TAG = "DataLayerListener"

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Message received: ${messageEvent.path}")

        coroutineScope.launch {
            when (messageEvent.path) {
                // Timer control messages from Wear
                "/timer-control" -> {
                    val action = String(messageEvent.data)
                    Log.d(TAG, "Timer action: $action")
                    when (action) {
                        "START" -> ScoreUpdateEventBus.postTimerEvent(TimerEvent.Start)
                        "PAUSE" -> ScoreUpdateEventBus.postTimerEvent(TimerEvent.Pause)
                        "RESET" -> ScoreUpdateEventBus.postTimerEvent(TimerEvent.Reset)
                    }
                }

                // Score updates from Wear
                "/update-score", WearDataSync.MSG_SCORE_CHANGED -> {
                    val data = String(messageEvent.data)
                    val scores = data.split(",").mapNotNull { it.toIntOrNull() }
                    if (scores.size == 2) {
                        Log.d(TAG, "Score update: ${scores[0]} - ${scores[1]}")
                        ScoreUpdateEventBus.postEvent(ScoreUpdateEvent(scores[0], scores[1]))
                    }
                }

                // Match management from Wear
                "/start_match", "/msg_match_action" -> {
                    val action = String(messageEvent.data)
                    Log.d(TAG, "Match action: $action")
                    if (action.contains("START")) {
                        ScoreUpdateEventBus.postTimerEvent(TimerEvent.StartNewMatch)
                    }
                }

                "/end_match" -> {
                    Log.d(TAG, "End match requested from Wear")
                    ScoreUpdateEventBus.postTimerEvent(TimerEvent.EndMatch)
                }

                "/reset_timer" -> {
                    Log.d(TAG, "Reset timer requested from Wear")
                    ScoreUpdateEventBus.postTimerEvent(TimerEvent.Reset)
                }

                // Scorer selection from Wear
                "/scorer_selected", WearDataSync.MSG_SCORER_SELECTED -> {
                    val data = String(messageEvent.data)
                    val parts = data.split("|")
                    if (parts.size == 3) {
                        val playerName = parts[0]
                        val role = parts[1]
                        val team = parts[2].toIntOrNull() ?: 1
                        Log.d(TAG, "Scorer selected: $playerName ($role) for team $team")
                        // TODO: Handle scorer selection in ViewModel
                    }
                }
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        val events = dataEvents.map { it.freeze() }
        dataEvents.release()
        coroutineScope.launch {
            events.forEach { event ->
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val item = event.dataItem
                    val path = item.uri.path
                    Log.d(TAG, "Data changed: $path")

                    when (path) {
                        WearDataSync.PATH_SCORE, DataSyncObject.SCORE_PATH -> {
                            val dataMapItem = DataMapItem.fromDataItem(item)
                            val dataMap = dataMapItem.dataMap

                            // Check for timer state in legacy path
                            val timerState = dataMap.getString(DataSyncObject.TIMER_STATE_KEY)
                            if (timerState != null) {
                                Log.d(TAG, "Timer state from Wear: $timerState")
                                when (timerState) {
                                    "START" -> ScoreUpdateEventBus.postTimerEvent(TimerEvent.Start)
                                    "PAUSE" -> ScoreUpdateEventBus.postTimerEvent(TimerEvent.Pause)
                                    "RESET" -> ScoreUpdateEventBus.postTimerEvent(TimerEvent.Reset)
                                }
                            }

                            // Check for score updates
                            if (dataMap.containsKey(WearDataSync.KEY_TEAM1_SCORE)) {
                                val team1Score = dataMap.getInt(WearDataSync.KEY_TEAM1_SCORE, 0)
                                val team2Score = dataMap.getInt(WearDataSync.KEY_TEAM2_SCORE, 0)
                                Log.d(TAG, "Score sync from Wear: $team1Score - $team2Score")
                                ScoreUpdateEventBus.postEvent(ScoreUpdateEvent(team1Score, team2Score))
                            }
                        }

                        WearDataSync.PATH_TIMER_STATE -> {
                            val dataMapItem = DataMapItem.fromDataItem(item)
                            val dataMap = dataMapItem.dataMap
                            val isRunning = dataMap.getBoolean(WearDataSync.KEY_TIMER_RUNNING, false)
                            val millis = dataMap.getLong(WearDataSync.KEY_TIMER_MILLIS, 0L)

                            Log.d(TAG, "Timer state sync: running=$isRunning, millis=$millis")

                            if (isRunning && millis == 0L) {
                                ScoreUpdateEventBus.postTimerEvent(TimerEvent.Start)
                            } else if (!isRunning && millis == 0L) {
                                ScoreUpdateEventBus.postTimerEvent(TimerEvent.Reset)
                            } else if (!isRunning) {
                                ScoreUpdateEventBus.postTimerEvent(TimerEvent.Pause)
                            } else {
                                ScoreUpdateEventBus.postTimerEvent(TimerEvent.Start)
                            }
                        }

                        WearDataSync.PATH_MATCH_STATE -> {
                            val dataMapItem = DataMapItem.fromDataItem(item)
                            val dataMap = dataMapItem.dataMap
                            val isActive = dataMap.getBoolean(WearDataSync.KEY_MATCH_ACTIVE, false)

                            Log.d(TAG, "Match state sync: active=$isActive")

                            if (isActive) {
                                ScoreUpdateEventBus.postTimerEvent(TimerEvent.StartNewMatch)
                            } else {
                                ScoreUpdateEventBus.postTimerEvent(TimerEvent.EndMatch)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}