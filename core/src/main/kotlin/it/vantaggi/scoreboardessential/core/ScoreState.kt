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
     * riscelta. Da qui, senza [serveAnchor], il servitore e' `config.serveOrder[serveIndex % 4]` e il
     * lato che serve e' `serveIndex % 2` -- che riproduce esattamente l'alternanza per game, senza casi speciali al
     * confine di set. Nel tie-break avanza ogni due punti dopo il primo, non a fine game; alla sua
     * chiusura torna a [GamePoints.TieBreak.openedAt] + 1, come se il tie-break fosse stato un game
     * solo. E' l'unico punto in cui il contatore scende: quello che conta e' il lato che apre il
     * set dopo, non la monotonia.
     */
    val serveIndex: Int = 0,
    /** Valorizzato a fine partita: e' l'unita' che viene persistita. */
    val finalScore: List<Int>? = null,
    override val wonBy: Int? = null,
    /**
     * L'ultima scelta di chi batte ([ScoringEvent.ServeChoice]), o null: allora vale la rotazione
     * di sempre, A1, B1, A2, B2 dal primo game. Il contatore [serveIndex] non cambia per una
     * scelta: cambia da dove si legge la rotazione.
     */
    val serveAnchor: ServeAnchor? = null,
) : ScoreState {
    override fun headline(): Pair<Int, Int> {
        val h = finalScore ?: setsWon
        return h[0] to h[1]
    }

    /** La squadra al servizio adesso: 0 il lato 1, 1 il lato 2. */
    val servingTeam: Int get() = teamAt(serveIndex)

    /** Quale giocatore della squadra al servizio serve adesso: 1 il primo, 2 il secondo. */
    val servingSlot: Int get() = slotAt(serveIndex)

    /** La posizione del servitore nell'ordine A1, B1, A2, B2 (0..3). */
    val servingOrderIndex: Int get() = (servingSlot - 1) * 2 + servingTeam

    /**
     * La squadra che serve al turno [index] del contatore. Senza scelta e' `index % 2`; con una
     * scelta si conta dal turno in cui e' stata fatta. floorMod perche' la chiusura di un tie-break
     * puo' riportare il contatore prima di una scelta fatta durante il tie-break: la rotazione si
     * legge all'indietro con la stessa regola.
     */
    fun teamAt(index: Int): Int {
        val anchor = serveAnchor ?: return Math.floorMod(index, 2)
        return Math.floorMod(anchor.team + index - anchor.at, 2)
    }

    /**
     * La correzione "adesso serve il giocatore [slot] del lato [side]", a partita in corso.
     *
     * Dice anche chi servira' per l'altra coppia, perche' la scelta li vuole tutti e due. Se il lato
     * e' quello che gia' serve, si e' sbagliato solo il giocatore: l'altra coppia resta com'era. Se
     * il lato e' l'altro, la coppia che il tabellone dava al servizio non ha ancora servito, quindi
     * al suo turno serve il giocatore che il tabellone mostrava adesso. E' il caso tipico: a inizio
     * set ha aperto l'altra coppia.
     */
    fun serveCorrection(
        side: Int,
        slot: Int,
    ): ScoringEvent.ServeChoice {
        val team = side - 1
        val otherNext = if (team == servingTeam) slotAt(serveIndex + 1) else servingSlot
        val slots = if (team == 0) listOf(slot, otherNext) else listOf(otherNext, slot)
        return ScoringEvent.ServeChoice(side = side, slot1 = slots[0], slot2 = slots[1])
    }

    /**
     * Il giocatore (1 o 2) che serve al turno [index]. Ogni squadra serve un turno si' e uno no, e
     * a ogni suo turno cambia giocatore: dal turno della scelta, il primo turno di ciascuna squadra
     * e' quello del giocatore scelto. Senza scelta e' `index % 4 / 2 + 1`, la rotazione di sempre.
     */
    fun slotAt(index: Int): Int {
        val anchor = serveAnchor ?: return Math.floorMod(index, 4) / 2 + 1
        val k = index - anchor.at
        val first = anchor.slots[teamAt(index)]
        return if (Math.floorMod(Math.floorDiv(k, 2), 2) == 0) first else 3 - first
    }
}

/**
 * Dove riparte la rotazione del servizio dopo una scelta a mano.
 *
 * Al turno [at] del contatore serve la squadra [team] (0 o 1) con il giocatore `slots[team]`;
 * l'altra squadra, al suo turno successivo, con `slots[1 - team]`. Da li' la rotazione e' quella di
 * sempre: le squadre si alternano e dentro ogni coppia i giocatori pure.
 */
data class ServeAnchor(
    val at: Int,
    val team: Int,
    val slots: List<Int>,
)
