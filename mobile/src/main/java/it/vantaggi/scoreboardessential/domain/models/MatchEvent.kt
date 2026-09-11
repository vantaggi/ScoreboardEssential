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
    /**
     * Posizione, nel registro del motore, del punto a cui questa riga si riferisce.
     *
     * Serve ad attribuire un marcatore DOPO: senza, una riga del registro non avrebbe modo di
     * dire quale dei punti sia il suo, e l'unico aggancio sarebbe la posizione relativa fra due
     * liste che contengono cose diverse -- il registro del motore ha solo i punti, questo ha
     * anche gli avvii, i timer e le correzioni.
     */
    val engineIndex: Int? = null,
    /**
     * Chi ha segnato, quando lo si sa.
     *
     * E' il segnale affidabile di "attribuito". [player] non lo e': quando il marcatore manca
     * contiene comunque il nome della SQUADRA, quindi non e' mai nullo e non distingue i due casi.
     */
    val playerId: Int? = null,
)
