package it.vantaggi.scoreboardessential.core

/**
 * Gli sport che l'applicazione sa giocare.
 *
 * E' un `object` con un `firstOrNull`, non una tabella di database: le regole non sono
 * esprimibili in colonne (nessuna colonna dice "sul 40-40 si gioca il punto secco"), quindi una
 * tabella `sports` duplicherebbe cio' che questo registro gia' sa e potrebbe divergerne senza
 * poter comunque essere autoritativa. Nel database `sportId` resta un TEXT non interpretato.
 *
 * Il tennis costa **una riga**, ed e' il test di estensibilita' del modello: se aggiungere uno
 * sport con racchetta richiedesse di toccare [RacketRules] invece di aggiungere una
 * configurazione, la separazione fra classe e configurazione sarebbe sbagliata e andrebbe rivista
 * prima della pallavolo -- al prezzo di una giornata, non di una release.
 */
object SportRegistry {
    const val FOOTBALL = "football"
    const val PADEL = "padel"
    const val TENNIS = "tennis"

    private val all: List<SportRules> =
        listOf(
            FootballRules,
            // Padel amatoriale: punto secco sul 40-40, set unico.
            RacketRules(
                id = PADEL,
                config = SportConfig(mode = ScoringMode.POINTS, deuce = DeuceRule.GOLDEN_POINT, sets = 1),
            ),
            // Tennis: vantaggi, al meglio di tre set. Una riga di configurazione.
            RacketRules(
                id = TENNIS,
                config = SportConfig(mode = ScoringMode.POINTS, deuce = DeuceRule.ADVANTAGE, sets = 3),
            ),
        )

    /** Gli sport selezionabili dall'utente, nell'ordine in cui vanno mostrati. */
    fun selectable(): List<SportRules> = all

    /**
     * Ripiega sul calcio invece di lanciare.
     *
     * Una riga di database con uno `sportId` che questa build non conosce -- perche' l'utente ha
     * installato una versione precedente, o perche' uno sport e' stato rimosso -- deve degradare,
     * non far crashare l'apertura della cronologia.
     */
    fun byId(id: String): SportRules = all.firstOrNull { it.id == id } ?: FootballRules

    /**
     * Le regole per una partita, con l'ordine di servizio scelto dall'utente innestato nella
     * configurazione. Per gli sport che non modellano il servizio l'elenco viene ignorato.
     */
    fun forMatch(
        id: String,
        serveOrder: List<Int> = emptyList(),
    ): SportRules {
        val base = byId(id)
        if (serveOrder.isEmpty() || base !is RacketRules) return base
        return RacketRules(id = base.id, config = base.config.copy(serveOrder = serveOrder))
    }
}
