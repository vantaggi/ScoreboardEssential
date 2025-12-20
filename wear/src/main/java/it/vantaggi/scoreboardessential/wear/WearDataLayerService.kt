package it.vantaggi.scoreboardessential.wear

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import it.vantaggi.scoreboardessential.shared.communication.WearConstants

class WearDataLayerService : WearableListenerService() {
    companion object {
        private const val TAG = "WearDataLayerService"

        // Broadcast Actions
        const val ACTION_SCORE_UPDATE = "it.vantaggi.scoreboardessential.wear.SCORE_UPDATE"
        const val ACTION_TEAM_NAMES_UPDATE = "it.vantaggi.scoreboardessential.wear.TEAM_NAMES_UPDATE"
        const val ACTION_TEAM_COLOR_UPDATE = "it.vantaggi.scoreboardessential.wear.TEAM_COLOR_UPDATE"
        const val ACTION_TIMER_UPDATE = "it.vantaggi.scoreboardessential.wear.TIMER_UPDATE"
        const val ACTION_KEEPER_TIMER_UPDATE = "it.vantaggi.scoreboardessential.wear.KEEPER_TIMER_UPDATE"
        const val ACTION_MATCH_STATE_UPDATE = "it.vantaggi.scoreboardessential.wear.MATCH_STATE_UPDATE"
        const val ACTION_RESET_MATCH = "it.vantaggi.scoreboardessential.wear.RESET_MATCH"

        // Extras
        const val EXTRA_TEAM1_SCORE = "team1_score"
        const val EXTRA_TEAM2_SCORE = "team2_score"
        const val EXTRA_TEAM1_NAME = "team1_name"
        const val EXTRA_TEAM2_NAME = "team2_name"
        const val EXTRA_TEAM_ID = "team_id"
        const val EXTRA_COLOR = "color"
        const val EXTRA_TIMER_MILLIS = "timer_millis"
        const val EXTRA_TIMER_RUNNING = "timer_running"
        const val EXTRA_KEEPER_MILLIS = "keeper_millis"
        const val EXTRA_KEEPER_RUNNING = "keeper_running"
        const val EXTRA_MATCH_ACTIVE = "match_active"
        
        // Padel Extras
        const val EXTRA_SPORT_TYPE = "sport_type"
        const val EXTRA_TEAM1_SETS = "team1_sets"
        const val EXTRA_TEAM2_SETS = "team2_sets"
        const val EXTRA_SERVING_TEAM = "serving_team"
        const val EXTRA_TEAM1_SCORE_STRING = "team1_score_string"
        const val EXTRA_TEAM2_SCORE_STRING = "team2_score_string"
        const val EXTRA_SERVING_SIDE = "serving_side"
        const val EXTRA_IS_GOLDEN_POINT = "is_golden_point"
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d(TAG, "📥 [${System.currentTimeMillis()}] Data received, count: ${dataEvents.count}")
        dataEvents.forEach { event ->
            Log.d(TAG, "  Event: type=${event.type}, path=${event.dataItem.uri.path}")
            if (event.type == DataEvent.TYPE_CHANGED) {
                handleData(event.dataItem)
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        Log.d(TAG, "Message received: ${messageEvent.path}")
        // Handle specific messages if needed, e.g., triggers not carrying data
    }

    private fun handleData(dataItem: DataItem) {
        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap

        when (dataItem.uri.path) {
            WearConstants.PATH_SCORE -> {
                // Try reading Int first (Legacy/Soccer)
                // Use Bundle for safer type-agnostic retrieval to avoid Wearable DataMap warnings
                val bundle = dataMap.toBundle()
                
                val team1Obj = bundle.get(WearConstants.KEY_TEAM1_SCORE)
                val team1Str = team1Obj?.toString() ?: bundle.getInt("team1_score_int", 0).toString()
                val team1 = team1Str.toIntOrNull() ?: 0

                val team2Obj = bundle.get(WearConstants.KEY_TEAM2_SCORE)
                val team2Str = team2Obj?.toString() ?: bundle.getInt("team2_score_int", 0).toString()
                val team2 = team2Str.toIntOrNull() ?: 0
                
                val finalTeam1Str = team1Str
                val finalTeam2Str = team2Str

                val sportType = bundle.getString(WearConstants.KEY_SPORT_TYPE, "SOCCER")
                val t1SetsArray = bundle.getIntArray(WearConstants.KEY_TEAM1_SETS)
                val t2SetsArray = bundle.getIntArray(WearConstants.KEY_TEAM2_SETS)
                
                val servingTeam = bundle.getInt(WearConstants.KEY_SERVING_TEAM, 0)
                val servingSide = bundle.getString(WearConstants.KEY_SERVING_SIDE, "R")
                val isGoldenPoint = bundle.getBoolean(WearConstants.KEY_IS_GOLDEN_POINT, false)

                val intent =
                    Intent(ACTION_SCORE_UPDATE).apply {
                        putExtra(EXTRA_TEAM1_SCORE, team1)
                        putExtra(EXTRA_TEAM2_SCORE, team2)
                        putExtra(EXTRA_TEAM1_SCORE_STRING, finalTeam1Str)
                        putExtra(EXTRA_TEAM2_SCORE_STRING, finalTeam2Str)
                        putExtra(EXTRA_SPORT_TYPE, sportType)
                        putExtra(EXTRA_TEAM1_SETS, t1SetsArray)
                        putExtra(EXTRA_TEAM2_SETS, t2SetsArray)
                        putExtra(EXTRA_SERVING_TEAM, servingTeam)
                        putExtra(EXTRA_SERVING_SIDE, servingSide)
                        putExtra(EXTRA_IS_GOLDEN_POINT, isGoldenPoint)
                    }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                Log.d(TAG, "Broadcasted score update: $finalTeam1Str - $finalTeam2Str (Sport: $sportType)")
            }
            WearConstants.PATH_TEAM_NAMES -> {
                val team1Name = dataMap.getString(WearConstants.KEY_TEAM1_NAME, "Team 1")
                val team2Name = dataMap.getString(WearConstants.KEY_TEAM2_NAME, "Team 2")
                val intent =
                    Intent(ACTION_TEAM_NAMES_UPDATE).apply {
                        putExtra(EXTRA_TEAM1_NAME, team1Name)
                        putExtra(EXTRA_TEAM2_NAME, team2Name)
                    }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                Log.d(TAG, "Broadcasted team names: $team1Name vs $team2Name")
            }
            WearConstants.PATH_TEAM1_COLOR -> {
                val color = dataMap.getInt("color", 0)
                val intent =
                    Intent(ACTION_TEAM_COLOR_UPDATE).apply {
                        putExtra(EXTRA_TEAM_ID, 1)
                        putExtra(EXTRA_COLOR, color)
                    }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            }
            WearConstants.PATH_TEAM2_COLOR -> {
                val color = dataMap.getInt("color", 0)
                val intent =
                    Intent(ACTION_TEAM_COLOR_UPDATE).apply {
                        putExtra(EXTRA_TEAM_ID, 2)
                        putExtra(EXTRA_COLOR, color)
                    }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            }
            WearConstants.PATH_TIMER_STATE -> {
                val millis = dataMap.getLong(WearConstants.KEY_TIMER_MILLIS, 0L)
                val running = dataMap.getBoolean(WearConstants.KEY_TIMER_RUNNING, false)
                val intent =
                    Intent(ACTION_TIMER_UPDATE).apply {
                        putExtra(EXTRA_TIMER_MILLIS, millis)
                        putExtra(EXTRA_TIMER_RUNNING, running)
                    }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            }
            WearConstants.PATH_KEEPER_TIMER -> {
                val millis = dataMap.getLong(WearConstants.KEY_KEEPER_MILLIS, 0L)
                val running = dataMap.getBoolean(WearConstants.KEY_KEEPER_RUNNING, false)
                val intent =
                    Intent(ACTION_KEEPER_TIMER_UPDATE).apply {
                        putExtra(EXTRA_KEEPER_MILLIS, millis)
                        putExtra(EXTRA_KEEPER_RUNNING, running)
                    }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            }
            WearConstants.PATH_MATCH_STATE -> {
                val active = dataMap.getBoolean(WearConstants.KEY_MATCH_ACTIVE, true)
                val intent =
                    Intent(ACTION_MATCH_STATE_UPDATE).apply {
                        putExtra(EXTRA_MATCH_ACTIVE, active)
                    }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            }
        }
    }
}
