package it.vantaggi.scoreboardessential.utils

/**
 * Utility class for validating data received from Wear OS devices.
 */
object WearDataValidator {

    /**
     * Validates that the score is non-negative.
     *
     * @param score The score to validate.
     * @return True if the score is valid (>= 0), false otherwise.
     */
    fun isValidScore(score: Int): Boolean {
        return score >= 0
    }

    /**
     * Validates that the timer milliseconds are non-negative.
     *
     * @param millis The timer duration or elapsed time in milliseconds.
     * @return True if the timer value is valid (>= 0), false otherwise.
     */
    fun isValidTimer(millis: Long): Boolean {
        return millis >= 0
    }
}
