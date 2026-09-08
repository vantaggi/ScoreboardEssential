package it.vantaggi.scoreboardessential.core

/**
 * Calcio: contatore semplice, fine partita decisa dall'utente.
 *
 * Trascrizione fedele di `MainViewModel.addScore`/`subtractScore`: nessuna soglia, nessun
 * auto-avvio del cronometro, nessuna regola che dichiari un vincitore. Le stranezze del
 * comportamento attuale si copiano invece di correggerle -- l'identita' di comportamento e' la
 * proprieta' che rende questo passo un semplice spostamento di codice.
 */
object FootballRules : SportRules {
    override val id: String = "football"

    /**
     * Nessun campo di [SportConfig] ha un significato nel calcio: non ci sono set, game, deuce
     * ne' ordine di servizio. Restano i default perche' l'interfaccia li richiede; leggerli qui
     * sarebbe un errore.
     */
    override val config: SportConfig = SportConfig()

    override val capabilities: SportCapabilities =
        SportCapabilities(
            clock = ClockMode.COUNT_UP,
            hasAuxCountdown = true,
            hasRoles = true,
            attributesScorer = true,
            decrementIsUndo = false,
            scoreEventKey = "goal",
        )

    override fun initial(): ScoreState = CounterScore()

    override fun apply(
        state: ScoreState,
        event: ScoringEvent,
    ): ScoreState {
        // Guardie della totalita': stato di un altro sport, lato fuori range o partita gia'
        // chiusa lasciano tutto com'e'. Il terzo caso qui e' irraggiungibile -- wonBy non viene
        // mai valorizzato dal calcio -- ma vale per uno stato costruito altrove.
        if (state !is CounterScore || event.side !in 1..2 || state.wonBy != null) return state

        val delta =
            when (event) {
                is ScoringEvent.Point -> event.weight
                is ScoringEvent.Correction -> -1
            }
        val index = event.side - 1
        // coerceAtLeast(0) ricalca subtractScore: il "-1" e' una correzione di battitura, non un
        // annullamento, e non porta mai il punteggio sotto zero.
        val points = state.points.mapIndexed { i, p -> if (i == index) (p + delta).coerceAtLeast(0) else p }
        // wonBy resta null: nel calcio la partita la chiude l'utente, nessuna regola la decide.
        return state.copy(points = points)
    }

    override fun display(state: ScoreState): ScoreDisplay {
        val (side1, side2) = state.headline()
        return ScoreDisplay(
            side1Primary = side1.toString(),
            side2Primary = side2.toString(),
        )
    }
}
