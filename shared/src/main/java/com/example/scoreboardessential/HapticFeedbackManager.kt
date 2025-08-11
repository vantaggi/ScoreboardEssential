package com.example.scoreboardessential.shared

object HapticFeedbackManager {
    const val HAPTIC_TAP = 50L
    const val HAPTIC_DOUBLE_TAP = 100L
    const val HAPTIC_LONG = 200L

    val PATTERN_SUCCESS = longArrayOf(0, 50, 50, 50)
    val PATTERN_WARNING = longArrayOf(0, 200, 100, 200)
    val PATTERN_ALERT = longArrayOf(0, 500, 200, 500, 200, 500)
}