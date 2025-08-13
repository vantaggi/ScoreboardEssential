package com.example.scoreboardessential

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class UpdatedDataListenerService : WearableListenerService() {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        coroutineScope.launch {
            dataEvents.forEach { event ->
                if (event.type == DataEvent.TYPE_CHANGED) {
                    event.dataItem.also { item ->
                        when (item.uri.path) {
                            "/score_update" -> {
                                DataMapItem.fromDataItem(item).dataMap.apply {
                                    val team1Score = getInt("team1_score", 0)
                                    val team2Score = getInt("team2_score", 0)
                                    WearSyncManager.postSyncEvent(
                                        WearSyncEvent.ScoreUpdate(team1Score, team2Score)
                                    )
                                }
                            }
                            "/team_names" -> {
                                DataMapItem.fromDataItem(item).dataMap.apply {
                                    val team1Name = getString("team1_name", "TEAM 1")
                                    val team2Name = getString("team2_name", "TEAM 2")
                                    WearSyncManager.postSyncEvent(
                                        WearSyncEvent.TeamNamesUpdate(team1Name, team2Name)
                                    )
                                }
                            }
                            "/keeper_timer" -> {
                                DataMapItem.fromDataItem(item).dataMap.apply {
                                    val isRunning = getBoolean("is_running", false)
                                    val duration = getLong("duration", 300000L)
                                    WearSyncManager.postSyncEvent(
                                        WearSyncEvent.KeeperTimerUpdate(isRunning, duration)
                                    )
                                }
                            }
                            "/reset_match" -> {
                                DataMapItem.fromDataItem(item).dataMap.apply {
                                    val reset = getBoolean("reset", false)
                                    if (reset) {
                                        WearSyncManager.postSyncEvent(WearSyncEvent.MatchReset)
                                    }
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