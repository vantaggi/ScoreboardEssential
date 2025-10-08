// wear/src/main/java/it/vantaggi/scoreboardessential/wear/OptimizedDataLayerListenerService.kt
package it.vantaggi.scoreboardessential.wear

import android.util.Log
import com.google.android.gms.wearable.*
import it.vantaggi.scoreboardessential.shared.PlayerData
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class OptimizedDataLayerListenerService : WearableListenerService() {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val TAG = "WearDataListener"

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "Ricevuto messaggio: ${messageEvent.path}")

        coroutineScope.launch {
            when (messageEvent.path) {
                WearConstants.MSG_SCORE_CHANGED -> {
                    val data = String(messageEvent.data)
                    val scores = data.split(",").mapNotNull { it.toIntOrNull() }
                    if (scores.size == 2) {
                        Log.d(TAG, "Dati punteggio (da messaggio) deserializzati: ${scores[0]} - ${scores[1]}")
                        WearSyncManager.postSyncEvent(
                            WearSyncEvent.ScoreUpdate(scores[0], scores[1])
                        )
                    }
                }
                WearConstants.MSG_MATCH_ACTION -> {
                    val action = String(messageEvent.data)
                    Log.d(TAG, "Azione partita (da messaggio): $action")
                    when (action) {
                        "START_MATCH", "END_MATCH" -> WearSyncManager.postSyncEvent(WearSyncEvent.MatchReset)
                    }
                }
            }
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val events = dataEvents.map { it.freeze() }
        dataEvents.release()
        coroutineScope.launch {
            for (event in events) {
                Log.d(TAG, "Ricevuto evento dati: ${event.dataItem.uri.path}")
                if (event.type == DataEvent.TYPE_CHANGED) {
                    handleDataChangeEvent(event.dataItem)
                }
            }
        }
    }

    private suspend fun handleDataChangeEvent(item: DataItem) {
        val path = item.uri.path ?: return

        when (path) {
            WearConstants.PATH_SCORE -> {
                DataMapItem.fromDataItem(item).dataMap.run {
                    val team1Score = getInt(WearConstants.KEY_TEAM1_SCORE)
                    val team2Score = getInt(WearConstants.KEY_TEAM2_SCORE)
                    Log.d(TAG, "Dati punteggio deserializzati: T1=${team1Score}, T2=${team2Score}")
                    WearSyncManager.postSyncEvent(WearSyncEvent.ScoreUpdate(team1Score, team2Score))
                }
            }
            WearConstants.PATH_TEAM_NAMES -> {
                DataMapItem.fromDataItem(item).dataMap.run {
                    val team1Name = getString(WearConstants.KEY_TEAM1_NAME, "TEAM 1")
                    val team2Name = getString(WearConstants.KEY_TEAM2_NAME, "TEAM 2")
                    Log.d(TAG, "Nomi squadra sincronizzati: T1=${team1Name}, T2=${team2Name}")
                    WearSyncManager.postSyncEvent(WearSyncEvent.TeamNamesUpdate(team1Name, team2Name))
                }
            }
            WearConstants.PATH_TIMER_STATE -> {
                // Not implemented in the original file, can be added if needed
            }
            WearConstants.PATH_KEEPER_TIMER -> {
                DataMapItem.fromDataItem(item).dataMap.run {
                    val isRunning = getBoolean(WearConstants.KEY_KEEPER_RUNNING, false)
                    val duration = getLong(WearConstants.KEY_KEEPER_MILLIS, 300000L)
                    Log.d(TAG, "Dati Keeper Timer deserializzati: Running=${isRunning}, Duration=${duration}")
                    WearSyncManager.postSyncEvent(WearSyncEvent.KeeperTimerUpdate(isRunning, duration))
                }
            }
            WearConstants.PATH_MATCH_STATE -> {
                DataMapItem.fromDataItem(item).dataMap.run {
                    val isActive = getBoolean(WearConstants.KEY_MATCH_ACTIVE, false)
                    Log.d(TAG, "Dati stato partita deserializzati: IsActive=${isActive}")
                    if (!isActive) {
                        WearSyncManager.postSyncEvent(WearSyncEvent.MatchReset)
                    }
                }
            }
            WearConstants.PATH_PLAYERS -> {
                DataMapItem.fromDataItem(item).dataMap.run {
                    val playerDataMaps = getDataMapArrayList(WearConstants.KEY_PLAYERS) ?: arrayListOf()
                    val players = playerDataMaps.map {
                        PlayerData(
                            id = it.getInt(WearConstants.KEY_PLAYER_ID),
                            name = it.getString(WearConstants.KEY_PLAYER_NAME, ""),
                            roles = it.getStringArrayList(WearConstants.KEY_PLAYER_ROLES) ?: emptyList(),
                            goals = it.getInt(WearConstants.KEY_PLAYER_GOALS),
                            appearances = it.getInt(WearConstants.KEY_PLAYER_APPEARANCES)
                        )
                    }
                    Log.d(TAG, "Dati lista giocatori deserializzati: Count=${players.size}")
                    WearSyncManager.postSyncEvent(WearSyncEvent.PlayerListUpdate(players))
                }
            }
            WearConstants.PATH_TEAM_PLAYERS -> {
                DataMapItem.fromDataItem(item).dataMap.run {
                    val team1DataMaps = getDataMapArrayList(WearConstants.KEY_TEAM1_PLAYERS) ?: arrayListOf()
                    val team2DataMaps = getDataMapArrayList(WearConstants.KEY_TEAM2_PLAYERS) ?: arrayListOf()
                    val team1Players = team1DataMaps.map {
                        PlayerData(
                            id = it.getInt(WearConstants.KEY_PLAYER_ID),
                            name = it.getString(WearConstants.KEY_PLAYER_NAME, ""),
                            roles = it.getStringArrayList(WearConstants.KEY_PLAYER_ROLES) ?: emptyList(),
                            goals = it.getInt(WearConstants.KEY_PLAYER_GOALS),
                            appearances = it.getInt(WearConstants.KEY_PLAYER_APPEARANCES)
                        )
                    }
                    val team2Players = team2DataMaps.map {
                        PlayerData(
                            id = it.getInt(WearConstants.KEY_PLAYER_ID),
                            name = it.getString(WearConstants.KEY_PLAYER_NAME, ""),
                            roles = it.getStringArrayList(WearConstants.KEY_PLAYER_ROLES) ?: emptyList(),
                            goals = it.getInt(WearConstants.KEY_PLAYER_GOALS),
                            appearances = it.getInt(WearConstants.KEY_PLAYER_APPEARANCES)
                        )
                    }
                    Log.d(TAG, "Dati giocatori squadra deserializzati: T1=${team1Players.size}, T2=${team2Players.size}")
                    WearSyncManager.postSyncEvent(WearSyncEvent.TeamPlayersUpdate(team1Players, team2Players))
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}