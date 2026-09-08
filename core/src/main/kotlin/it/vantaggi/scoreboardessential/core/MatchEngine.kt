package it.vantaggi.scoreboardessential.core

/**
 * Il guscio che tiene la storia degli eventi e ne deriva lo stato.
 *
 * La storia e' la sorgente di verita': lo stato e' sempre e solo `events.fold(rules.initial())`.
 * Il motore non conosce nessuno sport -- delega tutto a [rules] -- quindi aggiungerne uno non
 * tocca questo file.
 *
 * **L'annullamento e' un rifacimento del fold, non un'operazione inversa.** Si toglie l'ultimo
 * evento e si ricalcola da [SportRules.initial]. E' l'unico annullamento corretto attraverso un
 * confine di game o di set: un'inversa dovrebbe "riaprire" un set gia' chiuso, cioe' ricostruire
 * game, vantaggi e turno di servizio con cui il set era stato vinto -- informazioni che lo stato
 * finale non contiene piu'. Il rifacimento non ha quel problema perche' non inverte niente:
 * ripercorre. E costa poco: una partita di padel sono qualche centinaio di eventi, e il fold gira
 * una volta per pressione del tasto annulla, non per frame.
 */
class MatchEngine(
    val rules: SportRules,
) {
    private val mutableLog = mutableListOf<LoggedEvent>()

    /**
     * La storia completa, timestamp compresi.
     *
     * Il motore tiene [LoggedEvent] e non il solo [ScoringEvent] perche' il QUANDO e' meta' del
     * valore della cronologia: senza, dall'export verso Padel Elite si ricava l'ordine dei punti
     * ma non l'andamento, i recuperi, la durata dei set. E' anche la forma che la colonna
     * `eventLog` persiste, quindi salvare e ripristinare non richiedono conversioni.
     */
    val log: List<LoggedEvent>
        get() = mutableLog.toList()

    /** Copia difensiva: chi la riceve non deve poter far divergere la storia dallo stato. */
    val events: List<ScoringEvent>
        get() = mutableLog.map { it.event }

    /**
     * Stato memorizzato, non ricalcolato a ogni accesso.
     *
     * La lettura e' il caso frequente (interfaccia, orologio, service leggono a ogni ridisegno)
     * mentre il ricalcolo serve solo su annullamento e ripristino: tenere il valore corrente rende
     * la lettura O(1) e l'aggiunta di un evento un singolo passo di fold, invece di rifare l'intera
     * partita per mostrare un punteggio.
     */
    var state: ScoreState = rules.initial()
        private set

    /**
     * Registra [event] e avanza di un passo.
     *
     * Anche un evento senza effetto (partita gia' finita, lato fuori da 1..2) viene registrato: e'
     * cio' che rende vera la proprieta' "applica poi annulla torna esattamente allo stato di
     * prima". Se lo si scartasse, l'annullamento subito dopo un evento innocuo mangerebbe invece
     * l'ultimo punto valido.
     */
    fun apply(
        event: ScoringEvent,
        atMillis: Long? = null,
    ): ScoreState {
        mutableLog.add(LoggedEvent(event, atMillis))
        state = rules.apply(state, event)
        return state
    }

    fun canUndo(): Boolean = mutableLog.isNotEmpty()

    /** Su motore vuoto non lancia e non cambia nulla: [apply] e' totale, e anche questo lo e'. */
    fun undo(): ScoreState {
        if (mutableLog.isEmpty()) return state
        mutableLog.removeAt(mutableLog.lastIndex)
        state = derive(mutableLog)
        return state
    }

    fun reset(): ScoreState {
        mutableLog.clear()
        state = rules.initial()
        return state
    }

    /**
     * Riprende una partita salvata.
     *
     * La lista arriva verbatim dalla persistenza e viene ripercorsa cosi' com'e': non si filtra
     * niente, perche' il ripristino deve dare lo stesso motore che era stato salvato, annullamenti
     * disponibili compresi.
     */
    fun restore(events: List<ScoringEvent>): ScoreState = restoreLog(events.map { LoggedEvent(it) })

    /** Come [restore], ma conservando i tempi: e' la forma che arriva dalla persistenza. */
    fun restoreLog(entries: List<LoggedEvent>): ScoreState {
        mutableLog.clear()
        mutableLog.addAll(entries)
        state = derive(mutableLog)
        return state
    }

    private fun derive(source: List<LoggedEvent>): ScoreState = source.fold(rules.initial()) { acc, entry -> rules.apply(acc, entry.event) }
}
