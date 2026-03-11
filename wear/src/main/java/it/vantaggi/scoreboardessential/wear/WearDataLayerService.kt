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
import it.vantaggi.scoreboardessential.shared.utils.WearDataValidator

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
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "📥 [${System.currentTimeMillis()}] Data received, count: ${dataEvents.count}")
        }
        dataEvents.forEach { event ->
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "  Event: type=${event.type}, path=${event.dataItem.uri.path}")
            }
            if (event.type == DataEvent.TYPE_CHANGED) {
                handleData(event.dataItem)
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Message received: ${messageEvent.path}")
        }
        // Handle specific messages if needed, e.g., triggers not carrying data
    }

    private fun handleData(dataItem: DataItem) {
        val dataMap = DataMapItem.fromDataItem(dataItem).dataMap

        when (dataItem.uri.path) {
            WearConstants.PATH_SCORE -> {
                val team1 = dataMap.getInt(WearConstants.KEY_TEAM1_SCORE, 0)
                val team2 = dataMap.getInt(WearConstants.KEY_TEAM2_SCORE, 0)

                if (!WearDataValidator.isValidScore(team1) || !WearDataValidator.isValidScore(team2)) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Invalid score received. Ignoring.")
                    }
                    return
                }

                val intent =
                    Intent(ACTION_SCORE_UPDATE).apply {
                        putExtra(EXTRA_TEAM1_SCORE, team1)
                        putExtra(EXTRA_TEAM2_SCORE, team2)
                    }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Broadcasted score update")
                }
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
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Broadcasted team names update")
                }
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

                if (!WearDataValidator.isValidTimer(millis)) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Invalid timer value received. Ignoring.")
                    }
                    return
                }

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

                if (!WearDataValidator.isValidTimer(millis)) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "Invalid keeper timer value received. Ignoring.")
                    }
                    return
                }

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
