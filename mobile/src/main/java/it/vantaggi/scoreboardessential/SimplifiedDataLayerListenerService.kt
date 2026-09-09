package it.vantaggi.scoreboardessential

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import it.vantaggi.scoreboardessential.shared.utils.WearDataValidator
import java.util.concurrent.ConcurrentHashMap

class SimplifiedDataLayerListenerService : WearableListenerService() {
    companion object {
        private const val TAG = "SimplifiedDataService"
        const val ACTION_SCORE_UPDATE = "it.vantaggi.scoreboardessential.SCORE_UPDATE"
        const val ACTION_TIMER_UPDATE = "it.vantaggi.scoreboardessential.TIMER_UPDATE"
        const val ACTION_TEAM_NAMES_UPDATE = "it.vantaggi.scoreboardessential.TEAM_NAMES_UPDATE"
        const val ACTION_KEEPER_TIMER_UPDATE = "it.vantaggi.scoreboardessential.KEEPER_TIMER_UPDATE"
        const val ACTION_MATCH_STATE_UPDATE = "it.vantaggi.scoreboardessential.MATCH_STATE_UPDATE"
        const val ACTION_REQUEST_SYNC = "it.vantaggi.scoreboardessential.REQUEST_SYNC"
        const val ACTION_SCORER_SELECTED = "it.vantaggi.scoreboardessential.SCORER_SELECTED"
        const val ACTION_SCORE_INTENT = "it.vantaggi.scoreboardessential.SCORE_INTENT"

        /**
         * Ultima sequenza vista, PER NODO.
         *
         * Per nodo perche' due orologi accoppiati hanno contatori indipendenti: un contatore solo
         * farebbe scartare in blocco le intenzioni del secondo. Nel companion e non su un campo
         * d'istanza perche' il sistema distrugge e ricrea il service fra un messaggio e l'altro,
         * e a ogni ricreazione la memoria dell'ultima sequenza si azzererebbe.
         */
        private val lastSeqByNode = ConcurrentHashMap<String, Long>()
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        super.onDataChanged(dataEvents)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Data received, count: ${dataEvents.count}")
        }

        dataEvents.forEach { event ->
            try {
                handleDataEvent(event)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling data event", e)
            }
        }
    }

    private fun handleDataEvent(event: DataEvent) {
        if (event.type != DataEvent.TYPE_CHANGED) return

        val path = event.dataItem.uri.path
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Event path=$path")
        }
        val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap

        when (path) {
            WearConstants.PATH_SCORE -> {
                val team1 = dataMap.getInt(WearConstants.KEY_TEAM1_SCORE, 0)
                val team2 = dataMap.getInt(WearConstants.KEY_TEAM2_SCORE, 0)

                if (!WearDataValidator.isValidScore(team1) || !WearDataValidator.isValidScore(team2)) {
                    Log.w(TAG, "Invalid score received. Ignoring.")
                    return
                }

                val intent =
                    Intent(ACTION_SCORE_UPDATE).apply {
                        putExtra(WearConstants.KEY_TEAM1_SCORE, team1)
                        putExtra(WearConstants.KEY_TEAM2_SCORE, team2)
                    }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Broadcasted score update")
                }
            }

            WearConstants.PATH_TIMER_STATE -> {
                val millis = dataMap.getLong(WearConstants.KEY_TIMER_MILLIS, 0L)
                val isRunning = dataMap.getBoolean(WearConstants.KEY_TIMER_RUNNING, false)

                if (!WearDataValidator.isValidTimer(millis)) {
                    Log.w(TAG, "Invalid timer value received. Ignoring.")
                    return
                }

                val intent =
                    Intent(ACTION_TIMER_UPDATE).apply {
                        putExtra(WearConstants.KEY_TIMER_MILLIS, millis)
                        putExtra(WearConstants.KEY_TIMER_RUNNING, isRunning)
                    }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Broadcasted timer update")
                }
            }

            WearConstants.PATH_TEAM_NAMES -> {
                val intent = Intent(ACTION_TEAM_NAMES_UPDATE)
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            }

            WearConstants.PATH_KEEPER_TIMER -> {
                val millis = dataMap.getLong(WearConstants.KEY_KEEPER_MILLIS, 0L)
                val isRunning = dataMap.getBoolean(WearConstants.KEY_KEEPER_RUNNING, false)

                if (!WearDataValidator.isValidTimer(millis)) {
                    Log.w(TAG, "Invalid keeper timer value received. Ignoring.")
                    return
                }

                val intent =
                    Intent(ACTION_KEEPER_TIMER_UPDATE).apply {
                        putExtra(WearConstants.KEY_KEEPER_MILLIS, millis)
                        putExtra(WearConstants.KEY_KEEPER_RUNNING, isRunning)
                    }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            }

            WearConstants.PATH_MATCH_STATE -> {
                val isActive = dataMap.getBoolean(WearConstants.KEY_MATCH_ACTIVE, true)
                val intent =
                    Intent(ACTION_MATCH_STATE_UPDATE).apply {
                        putExtra(WearConstants.KEY_MATCH_ACTIVE, isActive)
                    }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Message received: ${messageEvent.path}")
        }
        try {
            when (messageEvent.path) {
                WearConstants.MSG_REQUEST_SYNC -> {
                    LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_REQUEST_SYNC))
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Broadcasted sync request")
                    }
                }

                WearConstants.MSG_SCORER_SELECTED -> {
                    handleScorerSelected(messageEvent.data)
                }

                WearConstants.MSG_SCORE_INTENT -> {
                    handleScoreIntent(messageEvent.sourceNodeId, messageEvent.data)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling message ${messageEvent.path}", e)
        }
    }

    /**
     * Un tocco sull'orologio: e' un'INTENZIONE ("un punto al lato 1"), non uno stato assoluto.
     *
     * Formato di filo: un [DataMap] serializzato, con [WearConstants.KEY_SIDE] (Int, 1 o 2) e
     * [WearConstants.KEY_SEQ] (Long, strettamente crescente per nodo, il primo valore lecito e' 1).
     *
     * MessageClient non coalesce, ma puo' comunque riconsegnare: la sequenza rende l'applicazione
     * idempotente. E' la ragione per cui il v2 non manda piu' la coppia assoluta -- due punti a un
     * secondo di distanza sullo stesso path coalescente diventavano uno solo.
     */
    private fun handleScoreIntent(
        sourceNodeId: String,
        data: ByteArray?,
    ) {
        if (data == null || data.isEmpty()) {
            Log.w(TAG, "Empty score-intent payload. Ignoring.")
            return
        }
        val dataMap = DataMap.fromByteArray(data)
        val side = dataMap.getInt(WearConstants.KEY_SIDE, 0)
        // Il telefono e l'orologio sono due APK aggiornati in momenti diversi: si accetta anche un
        // seq scritto come Int, perche' una sequenza letta come 0 verrebbe scartata in silenzio e
        // il difetto si vedrebbe solo su un dispositivo reale.
        val seq = dataMap.getLong(WearConstants.KEY_SEQ, dataMap.getInt(WearConstants.KEY_SEQ, 0).toLong())

        // Assente su un orologio che parlasse una bozza precedente del v2: si assume il punto,
        // che e' di gran lunga il gesto piu' frequente.
        val kind = dataMap.getString(WearConstants.KEY_INTENT_KIND, WearConstants.INTENT_POINT)

        if (!WearDataValidator.isValidTeamNumber(side) || seq <= 0L) {
            Log.w(TAG, "Invalid score-intent fields (side=$side, seq=$seq). Ignoring.")
            return
        }

        val ultima = lastSeqByNode[sourceNodeId] ?: 0L
        if (seq <= ultima) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Score intent already seen (seq=$seq <= $ultima). Ignoring.")
            }
            return
        }
        lastSeqByNode[sourceNodeId] = seq

        val intent =
            Intent(ACTION_SCORE_INTENT).apply {
                putExtra(WearConstants.KEY_SIDE, side)
                putExtra(WearConstants.KEY_INTENT_KIND, kind)
            }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Broadcasted score intent $kind for side $side (seq=$seq)")
        }
    }

    /**
     * Parses a scorer-selection message sent from the Wear device.
     * Wire format (UTF-8): "playerName|role1,role2|teamNumber".
     * Broadcasts [ACTION_SCORER_SELECTED] so the mobile UI can attribute the goal.
     */
    private fun handleScorerSelected(data: ByteArray?) {
        if (data == null || data.isEmpty()) {
            Log.w(TAG, "Empty scorer-selection payload. Ignoring.")
            return
        }
        val parts = String(data, Charsets.UTF_8).split("|")
        if (parts.size < 3) {
            Log.w(TAG, "Malformed scorer-selection payload. Ignoring.")
            return
        }
        val playerName = parts[0]
        val roles = parts[1]
        val teamNumber = parts[2].toIntOrNull()
        if (playerName.isBlank() || teamNumber == null || !WearDataValidator.isValidTeamNumber(teamNumber)) {
            Log.w(TAG, "Invalid scorer-selection fields. Ignoring.")
            return
        }
        // Quarto campo opzionale: presente solo dagli orologi aggiornati.
        val playerId = parts.getOrNull(3)?.toIntOrNull()
        val intent =
            Intent(ACTION_SCORER_SELECTED).apply {
                putExtra(WearConstants.KEY_PLAYER_NAME, playerName)
                putExtra(WearConstants.KEY_PLAYER_ROLES, roles)
                putExtra(WearConstants.EXTRA_TEAM_NUMBER, teamNumber)
                if (playerId != null) putExtra(WearConstants.KEY_PLAYER_ID, playerId)
            }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Broadcasted scorer selection for team $teamNumber")
        }
    }
}
