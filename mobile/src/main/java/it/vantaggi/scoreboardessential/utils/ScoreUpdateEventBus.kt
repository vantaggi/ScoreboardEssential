package it.vantaggi.scoreboardessential.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class ScoreUpdateEvent(val team1Score: Int, val team2Score: Int)

object ScoreUpdateEventBus {
    private val _events = MutableSharedFlow<ScoreUpdateEvent>()
    val events = _events.asSharedFlow()

    private val _timerEvents = MutableSharedFlow<TimerEvent>()
    val timerEvents = _timerEvents.asSharedFlow()

    suspend fun postEvent(event: ScoreUpdateEvent) {
        _events.emit(event)
    }

    suspend fun postTimerEvent(event: TimerEvent) {
        _timerEvents.emit(event)
    }
}
