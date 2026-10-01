package it.vantaggi.scoreboardessential.ui

import it.vantaggi.scoreboardessential.core.MatchSummarizer
import it.vantaggi.scoreboardessential.core.RacketRules
import it.vantaggi.scoreboardessential.core.SetSummary
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.utils.MatchExportUtils

/**
 * La riga dei set di una partita dello storico: «6-4 · 3-6 · 7-6 (7-4)».
 *
 * Solo numeri e simboli, nessuna parola: la riga e' uguale in ogni lingua. Si rigioca il registro
 * salvato con le regole dello sport ([MatchExportUtils.savedEngine]), cosi' i set sono quelli che
 * il motore ha chiuso e non una seconda copia da tenere allineata.
 */
object RigaDeiSet {
    private const val SEPARATORE = " · "

    /**
     * Null quando non c'e' niente da aggiungere al punteggio di testata: il calcio, il set unico
     * del padel (il punteggio E' quel set) e ogni partita senza registro leggibile.
     */
    fun of(match: Match): String? {
        val regole = SportRegistry.byId(match.sportId)
        if (regole !is RacketRules || regole.config.sets <= 1) return null
        val motore = MatchExportUtils.savedEngine(match) ?: return null
        // Senza punti il set in corso sarebbe uno «0-0» che non e' mai stato giocato.
        if (motore.log.isEmpty()) return null
        val riassunto = MatchSummarizer.summarize(motore, emptyList())
        // Il set in corso conta come un set in piu': una partita interrotta non perde il 3-2.
        // Ma solo se ha un game: subito dopo la chiusura di un set e' uno «0-0» mai giocato.
        val inCorso = riassunto.currentSet?.takeIf { it[0] + it[1] > 0 }
        val righe = riassunto.sets.map { set(it) } + listOfNotNull(inCorso?.let { "${it[0]}-${it[1]}" })
        return righe.takeIf { it.isNotEmpty() }?.joinToString(SEPARATORE)
    }

    private fun set(set: SetSummary): String {
        val game = "${set.games[0]}-${set.games[1]}"
        return set.tieBreak?.let { "$game (${it[0]}-${it[1]})" } ?: game
    }
}
