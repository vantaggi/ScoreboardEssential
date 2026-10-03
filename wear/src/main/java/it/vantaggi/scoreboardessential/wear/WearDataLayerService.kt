package it.vantaggi.scoreboardessential.wear

import android.content.Context
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
        const val ACTION_PLAYERS_UPDATE = "it.vantaggi.scoreboardessential.wear.PLAYERS_UPDATE"
        const val ACTION_STATE_V2_UPDATE = "it.vantaggi.scoreboardessential.wear.STATE_V2_UPDATE"
        const val ACTION_BATCH_ACK = "it.vantaggi.scoreboardessential.wear.BATCH_ACK"
        const val ACTION_BATCH_NACK = "it.vantaggi.scoreboardessential.wear.BATCH_NACK"

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
        const val EXTRA_KEEPER_DURATION = "keeper_duration"
        const val EXTRA_MATCH_ACTIVE = "match_active"
        const val EXTRA_PLAYERS = "players"

        /** Il DataMap del v2 viaggia grezzo: le chiavi si leggono in un posto solo, [WearScoreState]. */
        const val EXTRA_V2_PAYLOAD = "v2_payload"

        /**
         * Falso per un v2 riletto al risveglio: e' una copia vecchia, non un'ora in cui il telefono
         * ha parlato. La riga di stato non deve scrivere "SCOLLEGATO" con l'ora di adesso.
         */
        const val EXTRA_V2_DAL_VIVO = "v2_dal_vivo"

        /**
         * Il dispatch sta nel companion perche' lo riusa [MainActivity] per rileggere al risveglio i
         * DataItem gia' presenti: un path deve diventare un broadcast in un punto solo, altrimenti
         * la rilettura e la consegna viva possono divergere in silenzio.
         */
        fun dispatchDataItem(
            context: Context,
            dataItem: DataItem,
            dalVivo: Boolean = true,
        ) {
            val dataMap = DataMapItem.fromDataItem(dataItem).dataMap

            when (dataItem.uri.path) {
                WearConstants.PATH_STATE_V2 -> {
                    // L'ora del dato vivo si scrive qui e non nel ViewModel: il servizio riceve i v2
                    // anche ad app chiusa, e "SCOLLEGATO · 18:42" deve poterli contare.
                    if (dalVivo) LastKnownMatch(context).segnaStatoVivo(System.currentTimeMillis())
                    val intent =
                        Intent(ACTION_STATE_V2_UPDATE).apply {
                            putExtra(EXTRA_V2_PAYLOAD, dataMap.toByteArray())
                            putExtra(EXTRA_V2_DAL_VIVO, dalVivo)
                        }
                    val preso = LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                    // Nessuna Activity: lo stato col suo id e' la conferma che l'ack avrebbe portato.
                    // Le voci escono dalla coda sul disco, e con loro lo stato che le contiene
                    // (sport e registro): il calcolo a freddo riparte da li' e non le conta due volte.
                    if (!preso) {
                        val idApplicato = WearScoreState.idDelBatch(dataMap)
                        if (idApplicato > 0L && PendingIntents(context).confermaBatch(idApplicato) != null) {
                            LastKnownMatch(context).save(
                                dataMap.getString(WearConstants.KEY_SPORT_ID, ""),
                                dataMap.getString(WearConstants.KEY_EVENT_LOG, ""),
                                dataMap.getInt(WearConstants.KEY_SERVING_SLOT, 0),
                            )
                        }
                    }
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Broadcasted v2 state")
                    }
                }

                WearConstants.PATH_SCORE -> {
                    val team1 = dataMap.getInt(WearConstants.KEY_TEAM1_SCORE, 0)
                    val team2 = dataMap.getInt(WearConstants.KEY_TEAM2_SCORE, 0)

                    if (!WearDataValidator.isValidScore(team1) || !WearDataValidator.isValidScore(team2)) {
                        Log.w(TAG, "Invalid score received. Ignoring.")
                        return
                    }

                    val intent =
                        Intent(ACTION_SCORE_UPDATE).apply {
                            putExtra(EXTRA_TEAM1_SCORE, team1)
                            putExtra(EXTRA_TEAM2_SCORE, team2)
                        }
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
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
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Broadcasted team names update")
                    }
                }

                WearConstants.PATH_TEAM1_COLOR -> {
                    val color = dataMap.getInt(WearConstants.KEY_TEAM_COLOR, 0)
                    val intent =
                        Intent(ACTION_TEAM_COLOR_UPDATE).apply {
                            putExtra(EXTRA_TEAM_ID, 1)
                            putExtra(EXTRA_COLOR, color)
                        }
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                }

                WearConstants.PATH_TEAM2_COLOR -> {
                    val color = dataMap.getInt(WearConstants.KEY_TEAM_COLOR, 0)
                    val intent =
                        Intent(ACTION_TEAM_COLOR_UPDATE).apply {
                            putExtra(EXTRA_TEAM_ID, 2)
                            putExtra(EXTRA_COLOR, color)
                        }
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                }

                WearConstants.PATH_TIMER_STATE -> {
                    val millis = dataMap.getLong(WearConstants.KEY_TIMER_MILLIS, 0L)
                    val running = dataMap.getBoolean(WearConstants.KEY_TIMER_RUNNING, false)

                    if (!WearDataValidator.isValidTimer(millis)) {
                        Log.w(TAG, "Invalid timer value received. Ignoring.")
                        return
                    }

                    val intent =
                        Intent(ACTION_TIMER_UPDATE).apply {
                            putExtra(EXTRA_TIMER_MILLIS, millis)
                            putExtra(EXTRA_TIMER_RUNNING, running)
                        }
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                }

                WearConstants.PATH_KEEPER_TIMER -> {
                    val millis = dataMap.getLong(WearConstants.KEY_KEEPER_MILLIS, 0L)
                    val running = dataMap.getBoolean(WearConstants.KEY_KEEPER_RUNNING, false)

                    if (!WearDataValidator.isValidTimer(millis)) {
                        Log.w(TAG, "Invalid keeper timer value received. Ignoring.")
                        return
                    }

                    // Zero se il telefono non e' aggiornato: allora vale la regola di prima.
                    val durata =
                        dataMap
                            .getLong(WearConstants.KEY_KEEPER_DURATION, 0L)
                            .takeIf { WearDataValidator.isValidTimer(it) } ?: 0L

                    val intent =
                        Intent(ACTION_KEEPER_TIMER_UPDATE).apply {
                            putExtra(EXTRA_KEEPER_MILLIS, millis)
                            putExtra(EXTRA_KEEPER_RUNNING, running)
                            putExtra(EXTRA_KEEPER_DURATION, durata)
                        }
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                }

                WearConstants.PATH_MATCH_STATE -> {
                    val active = dataMap.getBoolean(WearConstants.KEY_MATCH_ACTIVE, true)
                    val intent =
                        Intent(ACTION_MATCH_STATE_UPDATE).apply {
                            putExtra(EXTRA_MATCH_ACTIVE, active)
                        }
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                }

                WearConstants.PATH_PLAYERS -> {
                    val raw = dataMap.getString(WearConstants.KEY_PLAYERS, "")
                    val intent =
                        Intent(ACTION_PLAYERS_UPDATE).apply {
                            putExtra(EXTRA_PLAYERS, raw)
                        }
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
                }
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Data received, count: ${dataEvents.count}")
        }
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                try {
                    dispatchDataItem(this, event.dataItem)
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling data event", e)
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Message received: ${messageEvent.path}")
        }
        when (messageEvent.path) {
            WearConstants.MSG_BATCH_ACK -> gestisciAck(messageEvent.data)
            WearConstants.MSG_BATCH_NACK -> gestisciNack(messageEvent.data)
        }
    }

    private fun gestisciAck(data: ByteArray?) {
        // Il telefono dice di AVER APPLICATO l'arretrato. Finche' questo non arriva, la coda
        // sull'orologio non si tocca: e' l'unica differenza fra "consegnato" e "salvo".
        val dataMap =
            com.google.android.gms.wearable.DataMap
                .fromByteArray(data ?: return)
        val seq = dataMap.getLong(WearConstants.KEY_SEQ, 0L)
        // Zero da un telefono non aggiornato: l'ack porta solo la sequenza.
        val batchId = dataMap.getLong(WearConstants.KEY_BATCH_ID, 0L)
        if (seq <= 0L) return
        val preso =
            LocalBroadcastManager.getInstance(this).sendBroadcast(
                Intent(ACTION_BATCH_ACK).apply {
                    putExtra(WearConstants.KEY_SEQ, seq)
                    putExtra(WearConstants.KEY_BATCH_ID, batchId)
                },
            )
        // Nessuna Activity (uno swipe, il processo vivo senza schermata): l'ack si perdeva e la coda
        // restava, e alla riapertura il polso contava due volte l'arretrato gia' applicato (L5). Le
        // voci escono dal disco da qui; chi non e' un ricevitore non ha altro da fare.
        if (!preso && batchId > 0L) PendingIntents(this).confermaBatch(batchId)
    }

    private fun gestisciNack(data: ByteArray?) {
        val dataMap =
            com.google.android.gms.wearable.DataMap
                .fromByteArray(data ?: return)
        // Senza Activity il NACK non fa niente: il blocco resta in coda con il suo id, e al primo avvio
        // il polso lo rimanda e riceve la risposta con chi sa leggerla.
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent(ACTION_BATCH_NACK).apply {
                putExtra(WearConstants.KEY_BATCH_ID, dataMap.getLong(WearConstants.KEY_BATCH_ID, 0L))
                putExtra(
                    WearConstants.KEY_BATCH_NACK_REASON,
                    dataMap.getString(WearConstants.KEY_BATCH_NACK_REASON, WearConstants.NACK_RETRY),
                )
            },
        )
    }
}
