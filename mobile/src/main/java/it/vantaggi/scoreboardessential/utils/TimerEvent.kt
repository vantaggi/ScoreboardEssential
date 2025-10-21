package it.vantaggi.scoreboardessential.utils

sealed class TimerEvent {
    object Start : TimerEvent()

    object Pause : TimerEvent()

    object Reset : TimerEvent()

    object StartNewMatch : TimerEvent()

    object EndMatch : TimerEvent()
}
