package it.vantaggi.scoreboardessential.core

/** Un'azione che modifica il punteggio. Sempre attribuita a un lato (1 o 2). */
sealed interface ScoringEvent {
    val side: Int

    /**
     * Un punto a favore di [side]. [playerId] e' l'id del giocatore a cui attribuirlo, quando lo
     * sport lo prevede; [weight] serve agli sport in cui un'azione vale piu' di un punto.
     */
    data class Point(
        override val side: Int,
        val playerId: Int? = null,
        val weight: Int = 1,
    ) : ScoringEvent

    /** Il pulsante "-1": correzione manuale di un errore di battitura, non un annullamento. */
    data class Correction(
        override val side: Int,
    ) : ScoringEvent
}

/** Come si contano i punti. Rispecchia la modalita' gia' presente in Padel Elite. */
enum class ScoringMode {
    /** Un tocco = un game intero. Comodo in campo, ma perde l'andamento dei punti. */
    GAMES,

    /** Un tocco = un punto: 15/30/40 e oltre. */
    POINTS,
}

/** Cosa succede sul 40-40. */
enum class DeuceRule {
    /** Punto secco: il prossimo punto decide. E' il padel amatoriale. */
    GOLDEN_POINT,

    /** Un ciclo di vantaggi, poi punto secco. */
    KILLER_POINT,

    /** Vantaggi classici: si vince per due. E' il tennis. */
    ADVANTAGE,
}

/** Se e come scorre il cronometro. */
enum class ClockMode {
    /** Cronometro che sale, come nel calcio. */
    COUNT_UP,

    /** Nessun cronometro: la partita finisce per punteggio. */
    NONE,
}

/**
 * Configurazione di uno sport.
 *
 * I primi cinque campi combaciano deliberatamente con il `config` che Padel Elite gia' scrive in
 * `v2_live_matches` (`{mode, goldenPoint, killerPoint, sets, tiebreak}`): il motore qui e' un
 * port di quello, non un progetto nuovo, e i due devono poter parlare senza traduzioni.
 */
data class SportConfig(
    val mode: ScoringMode = ScoringMode.POINTS,
    val deuce: DeuceRule = DeuceRule.GOLDEN_POINT,
    /** Al meglio di N set: 1, 3, 5. */
    val sets: Int = 1,
    val tieBreak: Boolean = true,
    val gamesPerSet: Int = 6,
    val tieBreakTo: Int = 7,
    /**
     * I quattro giocatori nell'ordine di servizio: A1, B1, A2, B2.
     *
     * Vuota quando lo sport non modella il servizio, o quando l'utente non l'ha impostata.
     */
    val serveOrder: List<Int> = emptyList(),
)

/**
 * Tutto cio' che l'interfaccia, l'orologio e il service devono sapere **senza** conoscere lo
 * sport. Sei campi, ciascuno con almeno un consumatore reale: nessuno e' speculativo.
 */
data class SportCapabilities(
    val clock: ClockMode,
    /** Il countdown ausiliario. Oggi esiste solo per la rotazione del portiere nel calcio. */
    val hasAuxCountdown: Boolean,
    /** Ruoli, roster e formazioni hanno senso per questo sport? */
    val hasRoles: Boolean,
    /** Segnando si chiede chi ha realizzato il punto? */
    val attributesScorer: Boolean,
    /** Il pulsante "-" e' un annullamento invece di una correzione? */
    val decrementIsUndo: Boolean,
    /** Chiave semantica dell'evento di punteggio: "goal", "point". Non e' testo da mostrare. */
    val scoreEventKey: String,
)

/**
 * Le stringhe gia' impaginate che il telefono spedisce all'orologio.
 *
 * L'orologio non esegue MAI regole: riceve testo pronto. Cosi' aggiungere uno sport non richiede
 * una riga di codice sul lato orologio, e sparisce l'intera classe di aggiornamenti perduti in
 * cui i due lati calcolavano il punteggio ciascuno dalla propria copia.
 */
data class ScoreDisplay(
    val side1Primary: String,
    val side1Secondary: String? = null,
    val side2Primary: String,
    val side2Secondary: String? = null,
    /** "Set 2", "Tie-break", oppure null quando lo sport non ha periodi. */
    val periodLabel: String? = null,
    /** Lato al servizio (1 o 2), quando lo sport lo modella. */
    val servingSide: Int? = null,
    /** Giocatore al servizio, quando l'ordine di servizio e' stato impostato. */
    val servingPlayerId: Int? = null,
)

/**
 * Le regole di uno sport.
 *
 * [apply] e' **pura e totale**: stesso stato piu' stesso evento danno sempre lo stesso risultato,
 * e non lancia mai. Un evento senza senso per lo stato corrente (un punto a partita finita, un
 * lato fuori range) restituisce lo stato invariato. E' quello che rende l'annullamento un
 * semplice rifacimento del fold, e i test una tabella di casi.
 */
interface SportRules {
    val id: String
    val config: SportConfig
    val capabilities: SportCapabilities

    fun initial(): ScoreState

    fun apply(
        state: ScoreState,
        event: ScoringEvent,
    ): ScoreState

    fun display(state: ScoreState): ScoreDisplay
}
