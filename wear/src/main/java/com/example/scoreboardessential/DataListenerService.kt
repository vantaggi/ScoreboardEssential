package com.example.scoreboardessential

import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DataListenerService : WearableListenerService(), ViewModelStoreOwner {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private val appViewModelStore by lazy { ViewModelStore() }
    override fun getViewModelStore(): ViewModelStore = appViewModelStore
    private lateinit var wearViewModel: WearViewModel

    override fun onCreate() {
        super.onCreate()
        wearViewModel = ViewModelProvider.AndroidViewModelFactory.getInstance(application).create(WearViewModel::class.java)
        Log.d("DataListenerService", "Service created and ViewModel instance obtained.")
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        Log.d("DataListenerService", "onDataChanged called for ${dataEvents.count} events")
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                Log.d("DataListenerService", "Processing data for path: ${dataItem.uri.path}")

                serviceScope.launch {
                    when (dataItem.uri.path) {
                        "/score_update" -> {
                            val team1Score = dataMap.getInt("team1_score", 0)
                            val team2Score = dataMap.getInt("team2_score", 0)
                            Log.d("DataListenerService", "Updating scores to $team1Score : $team2Score")
                            wearViewModel.setScores(team1Score, team2Score)
                        }
                        "/team_names" -> {
                            val team1Name = dataMap.getString("team1_name", "TEAM 1")
                            val team2Name = dataMap.getString("team2_name", "TEAM 2")
                            Log.d("DataListenerService", "Updating team names to $team1Name & $team2Name")
                            wearViewModel.setTeamNames(team1Name, team2Name)
                        }
                        "/keeper_timer" -> {
                            val command = dataMap.getString("command")
                            Log.d("DataListenerService", "Received keeper timer command: $command")
                            if (command == "start") {
                                wearViewModel.handleKeeperTimer() // This will start if not running
                            } else if (command == "stop") {
                                wearViewModel.resetKeeperTimer()
                            }
                        }
                        "/reset_match" -> {
                            Log.d("DataListenerService", "Resetting match")
                            wearViewModel.resetMatch()
                        }
                    }
                }
            }
        }
        dataEvents.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        appViewModelStore.clear()
        Log.d("DataListenerService", "Service destroyed.")
    }
}
