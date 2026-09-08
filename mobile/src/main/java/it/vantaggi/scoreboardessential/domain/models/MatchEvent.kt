package it.vantaggi.scoreboardessential.domain.models

/**
 * Natura dell'evento, separata dal testo mostrato.
 *
 * Prima il letterale "Goal" faceva entrambi i lavori: era la copia visibile all'utente E la
 * chiave con cui annullamento, log e report riconoscevano una marcatura. Tradurlo o rinominarlo
 * avrebbe rotto in silenzio tre confronti sparsi, e persisterlo avrebbe congelato una stringa di
 * presentazione dentro i dati.
 */
enum class MatchEventType {
    /** Una marcatura attribuibile a un giocatore. */
    SCORE,

    /** Tutto il resto: inizio partita, timer, correzioni, annullamenti. */
    INFO,
}

data class MatchEvent(
    val timestamp: String,
    val event: String,
    val team: Int? = null,
    val player: String? = null,
    val playerRole: String? = null,
    val type: MatchEventType = MatchEventType.INFO,
)
