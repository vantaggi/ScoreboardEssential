// wear/src/main/java/com/example/scoreboardessential/DataListenerService.kt

package com.example.scoreboardessential

import com.example.scoreboardessential.communication.WearConstants
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
                WearConstants.MSG_SCORE_CHANGED -> {
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
                WearConstants.MSG_TIMER_ACTION -> {
                    val action = String(messageEvent.data)
                    Log.d(TAG, "Timer action from mobile: $action")
                    // Handle timer sync
                }

                // Match actions from mobile
                WearConstants.MSG_MATCH_ACTION -> {
                    val action = String(messageEvent.data)
                    Log.d(TAG, "Match action from mobile: $action")
                    when (action) {
                        "START_MATCH" -> WearSyncManager.postSyncEvent(WearSyncEvent.MatchReset)
                        "END_MATCH" -> WearSyncManager.postSyncEvent(WearSyncEvent.MatchReset)
                    }
                }

                // Keeper timer actions from mobile
                WearConstants.MSG_KEEPER_ACTION -> {
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
                            WearConstants.PATH_SCORE -> {
                                DataMapItem.fromDataItem(item).dataMap.apply {
                                    val team1Score = getInt(WearConstants.KEY_TEAM1_SCORE)
                                    val team2Score = getInt(WearConstants.KEY_TEAM2_SCORE)

                                    Log.d(TAG, "Score sync: $team1Score - $team2Score")
                                    WearSyncManager.postSyncEvent(
                                        WearSyncEvent.ScoreUpdate(team1Score, team2Score)
                                    )
                                }
                            }

                            // Team names
                            WearConstants.PATH_TEAM_NAMES -> {
                                DataMapItem.fromDataItem(item).dataMap.apply {
                                    val team1Name = getString(WearConstants.KEY_TEAM1_NAME, "TEAM 1")
                                    val team2Name = getString(WearConstants.KEY_TEAM2_NAME, "TEAM 2")

                                    Log.d(TAG, "Team names sync: $team1Name vs $team2Name")
                                    WearSyncManager.postSyncEvent(
                                        WearSyncEvent.TeamNamesUpdate(team1Name, team2Name)
                                    )
                                }
                            }

                            // Timer state
                            WearConstants.PATH_TIMER_STATE -> {
                                DataMapItem.fromDataItem(item).dataMap.apply {
                                    val isRunning = getBoolean(WearConstants.KEY_TIMER_RUNNING, false)
                                    val millis = getLong(WearConstants.KEY_TIMER_MILLIS, 0L)

                                    Log.d(TAG, "Timer sync: running=$isRunning, millis=$millis")
                                    // Update timer in ViewModel
                                }
                            }

                            // Keeper timer
                            WearConstants.PATH_KEEPER_TIMER -> {
                                DataMapItem.fromDataItem(item).dataMap.apply {
                                    val isRunning = getBoolean(WearConstants.KEY_KEEPER_RUNNING, false)
                                    val duration = getLong(WearConstants.KEY_KEEPER_MILLIS, 300000L)

                                    Log.d(TAG, "Keeper timer sync: running=$isRunning, duration=$duration")
                                    WearSyncManager.postSyncEvent(
                                        WearSyncEvent.KeeperTimerUpdate(isRunning, duration)
                                    )
                                }
                            }

                            // Match state
                            WearConstants.PATH_MATCH_STATE -> {
                                DataMapItem.fromDataItem(item).dataMap.apply {
                                    val isActive = getBoolean(WearConstants.KEY_MATCH_ACTIVE, false)

                                    Log.d(TAG, "Match state sync: active=$isActive")
                                    if (!isActive) {
                                        WearSyncManager.postSyncEvent(WearSyncEvent.MatchReset)
                                    }
                                 }
                            }

                            // Player list updates
                            WearConstants.PATH_PLAYERS -> {
                                DataMapItem.fromDataItem(item).dataMap.apply {
                                    val playerDataMaps = getDataMapArrayList("players") ?: arrayListOf()
                                    val players = playerDataMaps.map { playerMap ->
                                        PlayerData(
                                            id = playerMap.getInt("id", 0),
                                            name = playerMap.getString("name", ""),
                                            roles = playerMap.getStringArrayList("roles") ?: emptyList(),
                                            goals = playerMap.getInt("goals", 0),
                                            appearances = playerMap.getInt("appearances", 0)
                                        )
                                    }

                                    Log.d(TAG, "Player list sync: ${players.size} players")
                                    WearSyncManager.postSyncEvent(
                                        WearSyncEvent.PlayerListUpdate(players)
                                    )
                                }
                            }

                            // Team players updates
                            WearConstants.PATH_TEAM_PLAYERS -> {
                                DataMapItem.fromDataItem(item).dataMap.apply {
                                    val team1DataMaps = getDataMapArrayList("team1_players") ?: arrayListOf()
                                    val team2DataMaps = getDataMapArrayList("team2_players") ?: arrayListOf()
                                    
                                    val team1Players = team1DataMaps.map { playerMap ->
                                        PlayerData(
                                            id = playerMap.getInt("id", 0),
                                            name = playerMap.getString("name", ""),
                                            roles = playerMap.getStringArrayList("roles") ?: emptyList(),
                                            goals = playerMap.getInt("goals", 0),
                                            appearances = playerMap.getInt("appearances", 0)
                                        )
                                    }
                                    
                                    val team2Players = team2DataMaps.map { playerMap ->
                                        PlayerData(
                                            id = playerMap.getInt("id", 0),
                                            name = playerMap.getString("name", ""),
                                            roles = playerMap.getStringArrayList("roles") ?: emptyList(),
                                            goals = playerMap.getInt("goals", 0),
                                            appearances = playerMap.getInt("appearances", 0)
                                        )
                                    }

                                    Log.d(TAG, "Team players sync: T1=${team1Players.size}, T2=${team2Players.size}")
                                    WearSyncManager.postSyncEvent(
                                        WearSyncEvent.TeamPlayersUpdate(team1Players, team2Players)
                                    )
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