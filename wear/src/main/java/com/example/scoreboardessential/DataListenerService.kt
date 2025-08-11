package com.example.scoreboardessential

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService

class DataListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                event.dataItem.also { item ->
                    when (item.uri.path) {
                        "/score_update" -> {
                            DataMapItem.fromDataItem(item).dataMap.apply {
                                val team1Score = getInt("team1_score", 0)
                                val team2Score = getInt("team2_score", 0)
                                // Handle score update
                                // You could use a shared preference or broadcast to update the UI
                            }
                        }
                        "/keeper_timer" -> {
                            DataMapItem.fromDataItem(item).dataMap.apply {
                                val isRunning = getBoolean("is_running", false)
                                val duration = getLong("duration", 300000L)
                                // Handle keeper timer update
                            }
                        }
                        "/reset_match" -> {
                            DataMapItem.fromDataItem(item).dataMap.apply {
                                val reset = getBoolean("reset", false)
                                if (reset) {
                                    // Handle match reset
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}