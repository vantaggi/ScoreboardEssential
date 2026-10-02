package it.vantaggi.scoreboardessential.shared

object HapticFeedbackManager {
    // Pattern for a light tap (e.g., score change)
    val PATTERN_TICK = longArrayOf(0, 50)

    // Pattern for an important action (e.g., start timer)
    val PATTERN_CONFIRM = longArrayOf(0, 120)

    // Pattern for an alert (e.g., keeper timer end)
    val PATTERN_ALERT = longArrayOf(0, 200, 100, 200)

    // Pattern for a touch that was heard but does nothing (e.g., a tap on a finished match):
    // one long pulse, the same "not taken" reading on phone and watch. Three short ticks are
    // reserved for undo (DESIGN.md, "Coerenza fra telefono e orologio").
    val PATTERN_INERT_TAP = longArrayOf(0, 400)

    // Pattern for undo/correction: three short ticks. Shared because the phone speaks it too (undo
    // with one tap, step 12) and on the watch it is the takeback signal. It is NOT the old
    // finished-match tap, which became PATTERN_INERT_TAP above (DESIGN.md, "Coerenza fra telefono
    // e orologio").
    val PATTERN_UNDO = longArrayOf(0, 30, 50, 30, 50, 30)
}
