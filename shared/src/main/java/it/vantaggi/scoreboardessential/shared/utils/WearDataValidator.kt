package it.vantaggi.scoreboardessential.shared.utils

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
    fun isValidScore(score: Int): Boolean = score >= 0

    /**
     * Validates that the timer milliseconds are non-negative.
     *
     * @param millis The timer duration or elapsed time in milliseconds.
     * @return True if the timer value is valid (>= 0), false otherwise.
     */
    fun isValidTimer(millis: Long): Boolean = millis >= 0

    /**
     * Validates that the team number is valid (1 or 2).
     *
     * @param team Number of the team to validate.
     * @return True if the team number is valid (1 or 2), false otherwise.
     */
    fun isValidTeamNumber(team: Int): Boolean = team in 1..2
}
