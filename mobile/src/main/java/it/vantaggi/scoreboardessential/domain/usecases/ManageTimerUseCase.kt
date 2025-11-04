package it.vantaggi.scoreboardessential.domain.usecases

import it.vantaggi.scoreboardessential.service.MatchTimerService
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ManageTimerUseCase(
    private var timerService: MatchTimerService?,
    private val wearDataSync: OptimizedWearDataSync
) {
    data class TimerState(
        val isRunning: Boolean = false,
        val timeMillis: Long = 0L,
    )

    private val _timerState = MutableStateFlow(TimerState())
    val timerState: StateFlow<TimerState> = _timerState

    fun setTimerService(service: MatchTimerService?) {
        this.timerService = service
    }

    fun startOrPauseTimer() {
        if (timerService == null) {
            android.util.Log.e("ManageTimerUseCase", "Timer service is NULL!")
            return
        }

        android.util.Log.d("ManageTimerUseCase", "Current timer running: ${_timerState.value.isRunning}")

        if (_timerState.value.isRunning) {
            android.util.Log.d("ManageTimerUseCase", "Pausing timer")
            timerService?.pauseTimer()
            _timerState.value = _timerState.value.copy(isRunning = false)
        } else {
            android.util.Log.d("ManageTimerUseCase", "Starting timer")
            timerService?.startTimer()
            _timerState.value = _timerState.value.copy(isRunning = true)
        }
        syncToWear()
    }

    fun resetTimer() {
        timerService?.stopTimer()
        _timerState.value = TimerState(isRunning = false, timeMillis = 0L)
        syncToWear()
    }

    fun updateTimerValue(timeMillis: Long) {
        _timerState.value = _timerState.value.copy(timeMillis = timeMillis)
    }

    fun setTimerRunning(isRunning: Boolean) {
        _timerState.value = _timerState.value.copy(isRunning = isRunning)
    }

    private fun syncToWear() {
        val state = _timerState.value
        wearDataSync.syncTimerState(state.timeMillis, state.isRunning)
    }
}
