package it.vantaggi.scoreboardessential.utils

import java.util.concurrent.TimeUnit

object TimeUtils {
    /**
     * Formats milliseconds into a "MM:SS" string.
     * This implementation is optimized to avoid String.format allocations.
     *
     * @param millis The time in milliseconds to format.
     * @return A formatted string in the format "MM:SS" (e.g., "05:12").
     */
    fun formatTime(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

        return buildString(5) {
            if (minutes < 10) append('0')
            append(minutes)
            append(':')
            if (seconds < 10) append('0')
            append(seconds)
        }
    }
}
