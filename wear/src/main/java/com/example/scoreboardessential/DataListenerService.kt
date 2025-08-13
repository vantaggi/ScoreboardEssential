// wear/src/main/java/com/example/scoreboardessential/DataListenerService.kt

package com.example.scoreboardessential

import com.google.android.gms.wearable.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.util.Log

class DataListenerService : WearableListenerService() {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val TAG = "WearDataListener"

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Message received: ${messageEvent.path}")

        coroutineScope.launch {
            when (messageEvent.path) {
                // Score updates from mobile
                WearDataSync.MSG_SCORE_CHANGED -> {
                    val data = String(messageEvent.data)
                    val scores = data.split(",").mapNotNull { it.toIntOrNull() }
                    if (scores.size == 2) {
                        Log.d(TAG, "Score update from mobile: ${scores[0]} - ${scores[1]}")
                        WearSyncManager.postSyncEvent(
                            WearSyncEvent.ScoreUpdate(scores[0], scores[1])
                        )
                    }
                }

                // Timer actions from mobile
                WearDataSync.MSG_TIMER_ACTION -> {
                    val action = String(messageEvent.data)
                    Log.d(TAG, "Timer action from mobile: $action")
                    // Handle timer sync
                }

                // Match actions from mobile
                WearDataSync.MSG_MATCH_ACTION -> {
                    val action = String(messageEvent.data)
                    Log.d(TAG, "Match action from mobile: $action")
                    when (action) {
                        "START_MATCH" -> WearSyncManager.postSyncEvent(WearSyncEvent.MatchReset)
                        "END_MATCH" -> WearSyncManager.postSyncEvent(WearSyncEvent.MatchReset)
                    }
                }

                // Keeper timer actions from mobile
                WearDataSync.MSG_KEEPER_ACTION -> {
                    val action = String(messageEvent.data)
                    Log.d(TAG, "Keeper action from mobile: $action")
                    // Handle keeper timer sync
                }
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val events = dataEvents.map { it.freeze() }
        dataEvents.release()
        coroutineScope.launch {
            events.forEach { event ->
                if (event.type == DataEvent.TYPE_CHANGED) {
                    event.dataItem.also { item ->
                        val path = item.uri.path
                        Log.d(TAG, "Data changed: $path")

                        when (path) {
                            // Score updates
                            WearDataSync.PATH_SCORE, "/score_update" -> {
                                DataMapItem.fromDataItem(item).dataMap.apply {
                                    val team1Score = getInt(WearDataSync.KEY_TEAM1_SCORE,
                                        getInt("team1_score", 0))
                                    val team2Score = getInt(WearDataSync.KEY_TEAM2_SCORE,
                                        getInt("team2_score", 0))

                                    Log.d(TAG, "Score sync: $team1Score - $team2Score")
                                    WearSyncManager.postSyncEvent(
                                        WearSyncEvent.ScoreUpdate(team1Score, team2Score)
                                    )
                                }
                            }

                            // Team names
                            WearDataSync.PATH_TEAM_NAMES, "/team_names" -> {
                                DataMapItem.fromDataItem(item).dataMap.apply {
                                    val team1Name = getString(WearDataSync.KEY_TEAM1_NAME,
                                        getString("team1_name", "TEAM 1"))
                                    val team2Name = getString(WearDataSync.KEY_TEAM2_NAME,
                                        getString("team2_name", "TEAM 2"))

                                    Log.d(TAG, "Team names sync: $team1Name vs $team2Name")
                                    WearSyncManager.postSyncEvent(
                                        WearSyncEvent.TeamNamesUpdate(team1Name, team2Name)
                                    )
                                }
                            }

                            // Timer state
                            WearDataSync.PATH_TIMER_STATE -> {
                                DataMapItem.fromDataItem(item).dataMap.apply {
                                    val isRunning = getBoolean(WearDataSync.KEY_TIMER_RUNNING, false)
                                    val millis = getLong(WearDataSync.KEY_TIMER_MILLIS, 0L)

                                    Log.d(TAG, "Timer sync: running=$isRunning, millis=$millis")
                                    // Update timer in ViewModel
                                }
                            }

                            // Keeper timer
                            WearDataSync.PATH_KEEPER_TIMER, "/keeper_timer" -> {
                                DataMapItem.fromDataItem(item).dataMap.apply {
                                    val isRunning = getBoolean(WearDataSync.KEY_KEEPER_RUNNING,
                                        getBoolean("is_running", false))
                                    val duration = getLong(WearDataSync.KEY_KEEPER_MILLIS,
                                        getLong("duration", 300000L))

                                    Log.d(TAG, "Keeper timer sync: running=$isRunning, duration=$duration")
                                    WearSyncManager.postSyncEvent(
                                        WearSyncEvent.KeeperTimerUpdate(isRunning, duration)
                                    )
                                }
                            }

                            // Match state
                            WearDataSync.PATH_MATCH_STATE -> {
                                DataMapItem.fromDataItem(item).dataMap.apply {
                                    val isActive = getBoolean(WearDataSync.KEY_MATCH_ACTIVE, false)

                                    Log.d(TAG, "Match state sync: active=$isActive")
                                    if (!isActive) {
                                        WearSyncManager.postSyncEvent(WearSyncEvent.MatchReset)
                                    }
                                 }
                            }

                            // Reset match (legacy)
                            "/reset_match" -> {
                                DataMapItem.fromDataItem(item).dataMap.apply {
                                    val reset = getBoolean("reset", false)
                                    if (reset) {
                                        Log.d(TAG, "Match reset requested")
                                        WearSyncManager.postSyncEvent(WearSyncEvent.MatchReset)
                                    }
                                }
                            }

                            // Scorer info from mobile
                            "/scorer_info" -> {
                                DataMapItem.fromDataItem(item).dataMap.apply {
                                    val scorerName = getString("scorer_name", "")
                                    val scorerRole = getString("scorer_role", "")
                                    val team = getInt("team", 0)

                                    Log.d(TAG, "Scorer info: $scorerName ($scorerRole) - Team $team")
                                    // Could display a toast or update UI
                                }
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