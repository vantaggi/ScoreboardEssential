package it.vantaggi.scoreboardessential.domain.model

data class TimerState(
    val isRunning: Boolean = false,
    val timeMillis: Long = 0L
)
