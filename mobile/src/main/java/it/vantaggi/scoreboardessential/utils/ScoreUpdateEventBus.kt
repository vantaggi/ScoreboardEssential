package it.vantaggi.scoreboardessential.utils

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class ScoreUpdateEvent(
    val team1Score: Int,
    val team2Score: Int,
)

data class TimerStateEvent(
    val millis: Long,
    val isRunning: Boolean,
)

object ScoreUpdateEventBus {
    private val _events = MutableSharedFlow<ScoreUpdateEvent>()
    val events = _events.asSharedFlow()

    private val _timerEvents = MutableSharedFlow<TimerEvent>()
    val timerEvents = _timerEvents.asSharedFlow()

    private val _timerStateEvents = MutableSharedFlow<TimerStateEvent>()
    val timerStateEvents = _timerStateEvents.asSharedFlow()

    suspend fun postEvent(event: ScoreUpdateEvent) {
        _events.emit(event)
    }

    suspend fun postTimerEvent(event: TimerEvent) {
        _timerEvents.emit(event)
    }

    suspend fun postTimerStateEvent(event: TimerStateEvent) {
        _timerStateEvents.emit(event)
    }
}
