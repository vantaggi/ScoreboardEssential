package com.example.scoreboardessential

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class WearSyncEvent {
    data class ScoreUpdate(val team1Score: Int, val team2Score: Int) : WearSyncEvent()
    data class KeeperTimerUpdate(val isRunning: Boolean, val duration: Long) : WearSyncEvent()
    object MatchReset : WearSyncEvent()
    data class TeamNamesUpdate(val team1Name: String, val team2Name: String) : WearSyncEvent()
}

object WearSyncManager {
    private val _syncEvents = MutableSharedFlow<WearSyncEvent>()
    val syncEvents = _syncEvents.asSharedFlow()

    suspend fun postSyncEvent(event: WearSyncEvent) {
        _syncEvents.emit(event)
    }
}