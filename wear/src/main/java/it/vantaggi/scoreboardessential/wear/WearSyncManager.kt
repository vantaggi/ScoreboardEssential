package it.vantaggi.scoreboardessential.wear

import it.vantaggi.scoreboardessential.shared.PlayerData
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class WearSyncEvent {
    data class ScoreUpdate(
        val team1Score: Int,
        val team2Score: Int,
    ) : WearSyncEvent()

    data class TeamNamesUpdate(
        val team1Name: String,
        val team2Name: String,
    ) : WearSyncEvent()

    data class KeeperTimerUpdate(
        val isRunning: Boolean,
        val duration: Long,
    ) : WearSyncEvent()

    object MatchReset : WearSyncEvent()
    data class PlayerListUpdate(val players: List<PlayerData>) : WearSyncEvent()
    data class TeamPlayersUpdate(
        val team1Players: List<PlayerData>,
        val team2Players: List<PlayerData>
    ) : WearSyncEvent()

    data class TimerSync(val millis: Long, val isRunning: Boolean) : WearSyncEvent()
}

object WearSyncManager {
    private val _syncEvents = MutableSharedFlow<WearSyncEvent>()
    val syncEvents: SharedFlow<WearSyncEvent> = _syncEvents.asSharedFlow()

    suspend fun postSyncEvent(event: WearSyncEvent) {
        _syncEvents.emit(event)
    }
}
