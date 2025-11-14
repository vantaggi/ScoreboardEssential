package it.vantaggi.scoreboardessential.wear

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.WearableListenerService
import it.vantaggi.scoreboardessential.shared.communication.WearConstants

class WearDataLayerService : WearableListenerService() {

    companion object {
        private const val TAG = "WearDataLayerService"
        const val ACTION_SCORE_UPDATE = "it.vantaggi.scoreboardessential.wear.SCORE_UPDATE"
        const val EXTRA_TEAM1_SCORE = "team1_score"
        const val EXTRA_TEAM2_SCORE = "team2_score"
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

    private fun handleData(dataItem: DataItem) {
        when (dataItem.uri.path) {
            WearConstants.PATH_SCORE -> {
                val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                val team1 = dataMap.getInt(WearConstants.KEY_TEAM1_SCORE, 0)
                val team2 = dataMap.getInt(WearConstants.KEY_TEAM2_SCORE, 0)

                // Invia broadcast locale
                val intent = Intent(ACTION_SCORE_UPDATE).apply {
                    putExtra(EXTRA_TEAM1_SCORE, team1)
                    putExtra(EXTRA_TEAM2_SCORE, team2)
                }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                Log.d(TAG, "Broadcasted score update: T1=$team1, T2=$team2")
            }
        }
    }
}
