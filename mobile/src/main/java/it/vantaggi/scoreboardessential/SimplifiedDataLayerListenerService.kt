package it.vantaggi.scoreboardessential

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import it.vantaggi.scoreboardessential.shared.communication.WearConstants

class SimplifiedDataLayerListenerService : WearableListenerService() {
    companion object {
        private const val TAG = "SimplifiedDataService"
        const val ACTION_SCORE_UPDATE = "it.vantaggi.scoreboardessential.SCORE_UPDATE"
        const val ACTION_TIMER_UPDATE = "it.vantaggi.scoreboardessential.TIMER_UPDATE"
        const val ACTION_TEAM_NAMES_UPDATE = "it.vantaggi.scoreboardessential.TEAM_NAMES_UPDATE"

        const val EXTRA_TEAM1_SCORE = "team1_score"
        const val EXTRA_TEAM2_SCORE = "team2_score"
        const val EXTRA_TIMER_MILLIS = "timer_millis"
        const val EXTRA_TIMER_RUNNING = "timer_running"
        const val ACTION_KEEPER_TIMER_UPDATE = "it.vantaggi.scoreboardessential.KEEPER_TIMER_UPDATE"
        const val ACTION_MATCH_STATE_UPDATE = "it.vantaggi.scoreboardessential.MATCH_STATE_UPDATE"
        const val EXTRA_KEEPER_MILLIS = "keeper_millis"
        const val EXTRA_KEEPER_RUNNING = "keeper_running"
        const val EXTRA_MATCH_ACTIVE = "match_active"
        
        const val ACTION_WEAR_COMMAND = "it.vantaggi.scoreboardessential.WEAR_COMMAND"
        const val EXTRA_COMMAND = "wear_command"
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        Log.d(TAG, "📥 [${System.currentTimeMillis()}] Data received, count: ${dataEvents.count}")

        dataEvents.forEachIndexed { index, event ->
            Log.d(TAG, "  Event $index: type=${event.type}, path=${event.dataItem.uri.path}")
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                when (event.dataItem.uri.path) {
                    WearConstants.PATH_SCORE -> {
                        // Use Bundle to avoid Type Mismatch warnings from DataMap when receiving different types (String/Int)
                        val bundle = dataMap.toBundle()
                        
                        val team1Obj = bundle.get(WearConstants.KEY_TEAM1_SCORE)
                        val team1 = when(team1Obj) {
                            is Int -> team1Obj
                            is String -> team1Obj.toIntOrNull() ?: 0
                            else -> 0
                        }

                        val team2Obj = bundle.get(WearConstants.KEY_TEAM2_SCORE)
                        val team2 = when(team2Obj) {
                            is Int -> team2Obj
                            is String -> team2Obj.toIntOrNull() ?: 0
                            else -> 0
                        }

                        val intent =
                            Intent(ACTION_SCORE_UPDATE).apply {
                                putExtra(EXTRA_TEAM1_SCORE, team1)
                                putExtra(EXTRA_TEAM2_SCORE, team2)
                            }
                        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                        Log.d(TAG, "Broadcasted score update: T1=$team1, T2=$team2")
                    }
                    WearConstants.PATH_TIMER_STATE -> {
                        val millis = dataMap.getLong(WearConstants.KEY_TIMER_MILLIS, 0L)
                        val isRunning = dataMap.getBoolean(WearConstants.KEY_TIMER_RUNNING, false)
                        val intent =
                            Intent(ACTION_TIMER_UPDATE).apply {
                                putExtra(EXTRA_TIMER_MILLIS, millis)
                                putExtra(EXTRA_TIMER_RUNNING, isRunning)
                            }
                        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                        Log.d(TAG, "Broadcasted timer update: $millis ms, running=$isRunning")
                    }
                    WearConstants.PATH_TEAM_NAMES -> {
                        val intent = Intent(ACTION_TEAM_NAMES_UPDATE)
                        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                    }
                    WearConstants.PATH_KEEPER_TIMER -> {
                        val millis = dataMap.getLong(WearConstants.KEY_KEEPER_MILLIS, 0L)
                        val isRunning = dataMap.getBoolean(WearConstants.KEY_KEEPER_RUNNING, false)
                        val intent =
                            Intent(ACTION_KEEPER_TIMER_UPDATE).apply {
                                putExtra(EXTRA_KEEPER_MILLIS, millis)
                                putExtra(EXTRA_KEEPER_RUNNING, isRunning)
                            }
                        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                    }
                    WearConstants.PATH_MATCH_STATE -> {
                        val isActive = dataMap.getBoolean(WearConstants.KEY_MATCH_ACTIVE, true)
                        val intent =
                            Intent(ACTION_MATCH_STATE_UPDATE).apply {
                                putExtra(EXTRA_MATCH_ACTIVE, isActive)
                            }
                        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                    }
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        Log.d(TAG, "Message received: ${messageEvent.path}")
        
        when (messageEvent.path) {
            WearConstants.PATH_CMD_ADD_T1,
            WearConstants.PATH_CMD_ADD_T2,
            WearConstants.PATH_CMD_SUB_T1,
            WearConstants.PATH_CMD_SUB_T2,
            WearConstants.PATH_CMD_UNDO -> {
                 val intent = Intent(ACTION_WEAR_COMMAND).apply {
                     putExtra(EXTRA_COMMAND, messageEvent.path)
                 }
                 LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            }
        }
    }
}
