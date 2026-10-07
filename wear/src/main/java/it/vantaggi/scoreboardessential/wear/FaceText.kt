package it.vantaggi.scoreboardessential.wear

/**
 * I testi delle fasce A e D del quadrante, scelti da una funzione pura.
 *
 * L'orologio non calcola il punteggio: mette a schermo le stringhe che il telefono ha gia'
 * impaginato. Ma nella racchetta la riga dei set e' UNA stringa sola ("6-4 · 3-6 · 2-1"), e il
 * quadrante ne vuole due letture: i game del set in corso, grandi e sempre nello stesso posto
 * (fascia A), e il resto, piccolo (fascia D). Dividerla e' tutto cio' che questo file fa.
 *
 * Pura e senza android.* perche' la regola deve poter essere provata su JVM contro il motore vero
 * (FaceTextTest fa girare MatchEngine con le RacketRules vere), e perche' passerebbe identica a un
 * eventuale Compose for Wear OS.
 */
internal object FaceText {
    /**
     * Il separatore dei set di `RacketRules`, copiato qui.
     *
     * Nel motore e' in un companion privato e `:core` non lo espone: l'orologio ne tiene una copia,
     * e FaceTextTest la fissa facendo girare il motore vero. Se il motore lo cambia, il test diventa
     * rosso invece di far comparire a schermo una riga di set intera al posto dei game.
     * Il separatore e' un middot fra due spazi.
     */
    const val SET_SEPARATOR = " · "

    private val GAME = Regex("^(\\d+)-(\\d+)$")

    /**
     * Fascia A e fascia D, in quest'ordine.
     *
     * Col cronometro (calcio) la fascia A e' il tempo, che non viene da qui: entrambe vuote.
     *
     * Racchetta in corso: A e' l'ultimo segmento di [WearScoreState.side1Secondary], i game del set
     * in corso, scritto "4 – 3" (trattino lungo). D e' il periodo e poi i set chiusi:
     * "Set 3 · 6-4 · 3-6", "Tie-break · 6-4", "Set 1". Nel padel a set unico il periodo non c'e' e
     * non ci sono set chiusi, quindi D e' vuota. Senza separatore la stringa e' un segmento solo, e
     * A la mostra per intero.
     *
     * Racchetta finita: A e' vuota, perche' RacketRules non accoda i game a partita chiusa e
     * l'ultimo segmento sarebbe un set CHIUSO, che non va mostrato come se fosse in corso. D e' la
     * riga dei set intera, ma solo se dice qualcosa in piu' dei primari: nel padel finito i primari
     * sono i game del set unico e la riga li ripete.
     *
     * La riga dell'altro lato ([WearScoreState.side2Secondary]) e' la stessa letta al rovescio e non
     * si mostra piu'.
     */
    fun split(state: WearScoreState): Pair<String, String> {
        if (state.hasClock) return "" to ""
        if (state.matchOver) {
            val finale = state.side1Secondary
            val primari = "${state.side1Primary}-${state.side2Primary}"
            return "" to if (finale == primari) "" else finale
        }
        val segmenti = state.side1Secondary.split(SET_SEPARATOR).filter { it.isNotEmpty() }
        val corrente = segmenti.lastOrNull().orEmpty()
        val dettaglio = listOf(state.periodLabel).filter { it.isNotEmpty() } + segmenti.dropLast(1)
        return trattinoLungo(corrente) to dettaglio.joinToString(SET_SEPARATOR)
    }

    /**
     * Il cronometro in ambient: solo i minuti, "34:12" diventa "34'", perche' a polso abbassato i
     * secondi non si aggiornano. I minuti non si fermano a 59 ("95:03" e' "95'"). Un testo che non
     * e' "minuti:secondi" resta com'e': meglio un tempo intero che uno inventato.
     */
    fun minuti(tempo: String): String {
        val minuti = tempo.substringBefore(':', "").toIntOrNull() ?: return tempo
        return "$minuti'"
    }

    /** "4-3" diventa "4 – 3"; una stringa che non e' una coppia di game resta com'e'. */
    private fun trattinoLungo(segmento: String): String {
        val coppia = GAME.matchEntire(segmento) ?: return segmento
        return "${coppia.groupValues[1]} – ${coppia.groupValues[2]}"
    }

    /**
     * Chi ha vinto, 1 o 2, dal confronto dei primari interi; null se la partita non e' finita, se i
     * primari non sono numeri o se sono pari. Stessa regola del telefono: meglio nessun vincitore
     * che quello sbagliato, e col pari tutte e due le cifre restano bianche.
     */
    fun vincitore(state: WearScoreState): Int? {
        if (!state.matchOver) return null
        val uno = state.side1Primary.toIntOrNull() ?: return null
        val due = state.side2Primary.toIntOrNull() ?: return null
        return when {
            uno > due -> 1
            due > uno -> 2
            else -> null
        }
    }
}
