package it.vantaggi.scoreboardessential.shared

object HapticFeedbackManager {
    // Pattern for a light tap (e.g., score change)
    val PATTERN_TICK = longArrayOf(0, 50)

    // Pattern for an important action (e.g., start timer)
    val PATTERN_CONFIRM = longArrayOf(0, 120)

    // Pattern for an alert (e.g., keeper timer end)
    val PATTERN_ALERT = longArrayOf(0, 200, 100, 200)
}
