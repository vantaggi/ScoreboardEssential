package it.vantaggi.scoreboardessential.domain.usecases

import it.vantaggi.scoreboardessential.domain.model.TimerState
import it.vantaggi.scoreboardessential.service.MatchTimerService
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ManageTimerUseCase(
    private val timerService: MatchTimerService,
    @Suppress("unused")
    private val wearDataSync: OptimizedWearDataSync,
) {
    val timerState: Flow<TimerState> =
        combine(
            timerService.isMatchTimerRunning,
            timerService.matchTimerValue,
        ) { isRunning, timeMillis ->
            TimerState(isRunning, timeMillis)
        }

    fun startOrPauseTimer() {
        if (timerService.isMatchTimerRunning.value) {
            timerService.pauseTimer()
        } else {
            timerService.startTimer()
        }
    }

    fun resetTimer() {
        timerService.stopTimer()
    }

    fun updateTimerValue(timeMillis: Long) {
        timerService.updateMatchTimer(timeMillis)
    }

    fun setTimerRunning(isRunning: Boolean) {
        if (isRunning) {
            timerService.startTimer()
        } else {
            timerService.pauseTimer()
        }
    }
}
