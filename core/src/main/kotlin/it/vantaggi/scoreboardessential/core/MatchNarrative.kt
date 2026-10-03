package it.vantaggi.scoreboardessential.core

/** Come si e' chiuso un game, dal punto di vista di chi serviva. */
enum class GameOutcome {
    /** Il game e' andato a chi serviva. */
    HELD,

    /** Il game e' andato a chi riceveva: un break. */
    BROKEN,

    /** Il tie-break: il servizio ruota, quindi non e' ne' tenuto ne' un break. */
    TIE_BREAK,

    /** Non si sa: senza il lato al servizio, o col punteggio a game dove un tocco e' un game intero. */
    UNKNOWN,
}

/**
 * Una riga del registro del padel o del tennis: un game chiuso.
 *
 * Tutti i campi sono dati. Le frasi le scrive la schermata, come per [KeyMoment]: `:core` non ha
 * stringhe da tradurre.
 */
data class GameLine(
    /** Indice del game nella partita, da 0: il tie-break e' un game. */
    val index: Int,
    /** Indice del set, da 0. */
    val set: Int,
    /** Il lato che ha vinto il game: 1 o 2. */
    val winner: Int,
    /**
     * I game del set dopo questo game, lato 1 per primo. Se il game chiude il set sono i game
     * finali del set, per esempio 6-4 oppure 7-6.
     */
    val gamesAfter: List<Int>,
    /** Il lato che serviva il primo punto del game; null quando non si sa. */
    val servingSide: Int?,
    val outcome: GameOutcome,
    /** Il punteggio del tie-break, lato 1 per primo; null se il game non e' un tie-break. */
    val tieBreakScore: List<Int>?,
    /** Il game chiude il set. */
    val closesSet: Boolean,
    /** I set vinti da ciascun lato dopo questo game, contato solo se [closesSet]; altrimenti null. */
    val setsAfter: List<Int>?,
    /** Il game chiude la partita. */
    val closesMatch: Boolean,
    /**
     * Posizione nel registro del motore ([MatchEngine.log]) del punto che ha chiuso il game. E'
     * la chiave con cui il telefono sa a quale punto della cronologia la riga corrisponde.
     */
    val lastLogIndex: Int,
)

/**
 * Il registro del padel e del tennis riletto a game: una riga per game chiuso, nell'ordine in
 * cui sono stati giocati.
 *
 * **Non e' un elenco tenuto da qualcuno: e' una funzione del registro del motore.** Ogni volta che
 * il registro cambia (un punto, un annullamento, un ripristino, un arretrato dell'orologio) si
 * rifa' il calcolo, e le righe non possono divergere da quello che il motore sa: era il difetto di
 * L3, in cui la lista parallela delle azioni e il registro andavano ognuno per conto suo.
 *
 * Il calcolo non e' nuovo: `MatchStats` rigioca gia' il registro e sa chi ha vinto ogni game, il
 * punteggio dopo il game, se e' un break o un game tenuto e il tie-break. Qui si prende quello e
 * si aggiunge solo cio' che serve a una riga: chi ha chiuso il set e dove sta il punto nel
 * registro. Il game ancora aperto non e' una riga: il tabellone e la striscia dicono gia' il
 * punto in corso.
 */
data class MatchNarrative(
    val games: List<GameLine>,
) {
    companion object {
        /**
         * Le righe dei game di [engine], o null per gli sport che non sono con racchetta.
         *
         * Il lato al servizio si sa con l'ordine di quattro giocatori o, nel tennis singolare,
         * dall'alternanza: stessa regola di [MatchStats.of], che e' dove si decide.
         */
        fun of(
            engine: MatchEngine,
            serveOrder: List<Int> = engine.rules.config.serveOrder,
        ): MatchNarrative? {
            val stats = MatchStats.of(engine, serveOrder) ?: return null
            var setsWon = listOf(0, 0)
            val lines =
                stats.games.mapIndexed { index, game ->
                    val last = stats.points[game.lastPoint]
                    if (last.setWon) setsWon = bump(setsWon, game.winner - 1)
                    GameLine(
                        index = index,
                        set = game.set,
                        winner = game.winner,
                        gamesAfter = game.gamesAfter,
                        servingSide = game.servingSide,
                        outcome =
                            when {
                                game.tieBreak -> GameOutcome.TIE_BREAK
                                game.hold == true -> GameOutcome.HELD
                                game.hold == false -> GameOutcome.BROKEN
                                else -> GameOutcome.UNKNOWN
                            },
                        tieBreakScore = game.score.takeIf { game.tieBreak },
                        closesSet = last.setWon,
                        setsAfter = setsWon.takeIf { last.setWon },
                        closesMatch = last.matchWon,
                        lastLogIndex = last.logIndex,
                    )
                }
            return MatchNarrative(lines)
        }

        private fun bump(
            values: List<Int>,
            index: Int,
        ): List<Int> = values.mapIndexed { i, v -> if (i == index) v + 1 else v }
    }
}
