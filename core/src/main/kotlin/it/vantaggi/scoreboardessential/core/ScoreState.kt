package it.vantaggi.scoreboardessential.core

/**
 * Stato del punteggio di una partita.
 *
 * Gerarchia sigillata a **due** casi, non tre. Il terzo (punteggio a periodi, per la pallavolo)
 * arrivera' con lo sport che lo definisce davvero: introdurlo adesso significherebbe indovinarne
 * la forma senza un solo consumatore.
 *
 * Nota di implementazione: i punteggi sono `List<Int>` e non `IntArray`. In Kotlin le data class
 * con `IntArray` ereditano `equals` per identita', quindi due stati identici risulterebbero
 * diversi -- e in un motore che confronta stati per verificare l'annullamento sarebbe un difetto
 * silenzioso proprio nei test.
 */
sealed interface ScoreState {
    /** Non-null solo quando la regola dello sport ha decretato un vincitore (1 o 2). */
    val wonBy: Int?

    /**
     * La coppia che finisce in `matches.team1Score` / `matches.team2Score`.
     *
     * Per il calcio sono i gol. Per gli sport con racchetta sono i set vinti (o i game del set,
     * se la partita e' a set unico): e' il motivo per cui la query delle vittorie gia' esistente
     * resta numericamente corretta senza modifiche.
     */
    fun headline(): Pair<Int, Int>
}

/** Calcio, e in futuro qualunque sport a contatore semplice. */
data class CounterScore(
    val points: List<Int> = listOf(0, 0),
    override val wonBy: Int? = null,
) : ScoreState {
    override fun headline(): Pair<Int, Int> = points[0] to points[1]
}

/** Punteggio del game in corso. */
sealed interface GamePoints {
    /** Game normale: conteggio grezzo 0,1,2,3,4... reso poi come 0/15/30/40/vantaggio. */
    data class Normal(
        val raw: List<Int> = listOf(0, 0),
    ) : GamePoints

    /** Tie-break: si conta a punti interi fino a [target], con scarto di due. */
    data class TieBreak(
        val points: List<Int> = listOf(0, 0),
        val target: Int,
        /**
         * Il `serveIndex` del primo punto del tie-break. Serve alla chiusura: il set dopo lo apre
         * chi ha RICEVUTO quel punto, e dal solo contatore finale non si ricava, perche' dipende
         * da quanti punti si sono giocati. Senza default: uno zero inventato sbaglierebbe il lato
         * in silenzio.
         */
        val openedAt: Int,
    ) : GamePoints
}

/** Un set concluso: i game di ciascun lato e, se giocato, il punteggio del tie-break. */
data class SetLine(
    val games: List<Int>,
    val tieBreak: List<Int>? = null,
)

/** Padel e tennis. */
data class RacketScore(
    val setsWon: List<Int> = listOf(0, 0),
    val closedSets: List<SetLine> = emptyList(),
    val gamesInSet: List<Int> = listOf(0, 0),
    val game: GamePoints = GamePoints.Normal(),
    /**
     * Quante volte il turno di servizio e' cambiato dall'inizio della PARTITA.
     *
     * Contatore monotono, **mai azzerato**: a inizio set la rotazione continua invece di essere
     * riscelta. Da qui il servitore e' `config.serveOrder[serveIndex % 4]` e il lato che serve e'
     * `serveIndex % 2` -- che riproduce esattamente l'alternanza per game, senza casi speciali al
     * confine di set. Nel tie-break avanza ogni due punti dopo il primo, non a fine game; alla sua
     * chiusura torna a [GamePoints.TieBreak.openedAt] + 1, come se il tie-break fosse stato un game
     * solo. E' l'unico punto in cui il contatore scende: quello che conta e' il lato che apre il
     * set dopo, non la monotonia.
     */
    val serveIndex: Int = 0,
    /** Valorizzato a fine partita: e' l'unita' che viene persistita. */
    val finalScore: List<Int>? = null,
    override val wonBy: Int? = null,
) : ScoreState {
    override fun headline(): Pair<Int, Int> {
        val h = finalScore ?: setsWon
        return h[0] to h[1]
    }
}
