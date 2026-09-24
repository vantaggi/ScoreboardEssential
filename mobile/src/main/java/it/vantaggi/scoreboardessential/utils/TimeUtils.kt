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

    /**
     * Il minuto di gioco come lo dice il calcio: 0:30 e' il 1', 65:10 e' il 66'.
     *
     * Solo aritmetica sui millisecondi. Il registro passava il tempo trascorso a
     * SimpleDateFormat("mm:ss") dentro una Date, cioe' lo trattava come un istante: "mm" ripartiva
     * da zero ogni ora (un gol al 65' diventava 05:00) e il fuso del telefono entrava nel conto
     * (in India, +5:30, ogni riga era spostata di mezz'ora).
     */
    fun matchMinute(millis: Long): String = "${millis.coerceAtLeast(0L) / 60_000L + 1}'"
}
