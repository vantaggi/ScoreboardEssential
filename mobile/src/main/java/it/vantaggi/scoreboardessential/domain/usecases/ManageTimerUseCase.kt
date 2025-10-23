package it.vantaggi.scoreboardessential.domain.usecases

import it.vantaggi.scoreboardessential.service.MatchTimerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ManageTimerUseCase(
    private val timerService: MatchTimerService?,
) {
    data class TimerState(
        val isRunning: Boolean = false,
        val timeMillis: Long = 0L,
    )

    private val _timerState = MutableStateFlow(TimerState())
    val timerState: StateFlow<TimerState> = _timerState

    fun startOrPauseTimer() {
        if (timerService == null) return

        if (_timerState.value.isRunning) {
            timerService.pauseTimer()
            _timerState.value = _timerState.value.copy(isRunning = false)
        } else {
            timerService.startTimer()
            _timerState.value = _timerState.value.copy(isRunning = true)
        }
    }

    fun resetTimer() {
        timerService?.stopTimer()
        _timerState.value = TimerState(isRunning = false, timeMillis = 0L)
    }

    fun updateTimerValue(timeMillis: Long) {
        _timerState.value = _timerState.value.copy(timeMillis = timeMillis)
    }

    fun setTimerRunning(isRunning: Boolean) {
        _timerState.value = _timerState.value.copy(isRunning = isRunning)
    }
}
