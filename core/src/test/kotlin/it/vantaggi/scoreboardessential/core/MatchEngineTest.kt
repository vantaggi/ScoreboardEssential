package it.vantaggi.scoreboardessential.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I test usano [FootballRules] perche' il motore non deve sapere nulla dello sport: se passano con
 * un contatore semplice passano con qualunque regola, e restano leggibili.
 *
 * Quasi tutte le attese sono confronti con il fold fatto a mano sulle stesse regole, non numeri
 * scritti a mano: cosi' verificano il motore e non il calcio.
 */
class MatchEngineTest {
    private fun foldManuale(events: List<ScoringEvent>): ScoreState =
        events.fold(FootballRules.initial()) { acc, event -> FootballRules.apply(acc, event) }

    /**
     * Sequenza deterministica ma irregolare, senza librerie: i due moduli primi tra loro evitano
     * che lato e tipo di evento cadano in fase. Include di proposito i lati 0 e 3 -- eventi senza
     * senso, che il motore deve registrare e le regole ignorare.
     */
    private fun evento(i: Int): ScoringEvent {
        val side = (i * 7 + 1) % 4
        return if ((i * 5 + 2) % 3 == 0) ScoringEvent.Correction(side) else ScoringEvent.Point(side)
    }

    @Test
    fun applicareNEventiDaLoStessoStatoDelFoldManuale() {
        val engine = MatchEngine(FootballRules)
        val sequenza =
            listOf(
                ScoringEvent.Point(1),
                ScoringEvent.Point(2),
                ScoringEvent.Point(1),
                ScoringEvent.Correction(2),
                ScoringEvent.Point(1),
            )

        sequenza.forEach { engine.apply(it) }

        assertEquals(foldManuale(sequenza), engine.state)
        assertEquals(sequenza, engine.events)
    }

    @Test
    fun loStatoIniziale() {
        val engine = MatchEngine(FootballRules)

        assertEquals(FootballRules.initial(), engine.state)
        assertTrue(engine.events.isEmpty())
        assertFalse(engine.canUndo())
    }

    @Test
    fun ilPunteggioInTestaSegueIPuntiApplicati() {
        val engine = MatchEngine(FootballRules)

        repeat(3) { engine.apply(ScoringEvent.Point(1)) }
        engine.apply(ScoringEvent.Point(2))

        assertEquals(3 to 1, engine.state.headline())
    }

    /**
     * La proprieta' centrale: per QUALUNQUE sequenza, applicare e poi annullare riporta esattamente
     * allo stato di prima -- e alla stessa storia. Duecento passi con eventi anche assurdi.
     */
    @Test
    fun applicarePoiAnnullareTornaEsattamenteAlloStatoPrecedente() {
        val engine = MatchEngine(FootballRules)

        for (i in 0 until 200) {
            val statoPrima = engine.state
            val storiaPrima = engine.events

            engine.apply(evento(i))
            engine.undo()

            assertEquals(statoPrima, engine.state)
            assertEquals(storiaPrima, engine.events)

            // Si avanza sul serio, altrimenti la sequenza resterebbe lunga zero per sempre.
            engine.apply(evento(i + 1))
        }
    }

    @Test
    fun annullareSuMotoreVuotoNonLanciaENonCambiaNulla() {
        val engine = MatchEngine(FootballRules)

        assertFalse(engine.canUndo())
        assertEquals(FootballRules.initial(), engine.undo())
        assertEquals(FootballRules.initial(), engine.state)
        assertTrue(engine.events.isEmpty())
    }

    @Test
    fun annullareFinoInFondoRiportaAlloStatoIniziale() {
        val engine = MatchEngine(FootballRules)
        for (i in 0 until 30) {
            engine.apply(evento(i))
        }

        while (engine.canUndo()) {
            engine.undo()
        }

        assertEquals(FootballRules.initial(), engine.state)
        assertTrue(engine.events.isEmpty())
    }

    @Test
    fun annullareRicalcolaEnonInverte() {
        val engine = MatchEngine(FootballRules)
        val sequenza = listOf(ScoringEvent.Point(1), ScoringEvent.Point(1), ScoringEvent.Point(2))
        sequenza.forEach { engine.apply(it) }

        engine.undo()

        assertEquals(foldManuale(sequenza.dropLast(1)), engine.state)
        assertEquals(sequenza.dropLast(1), engine.events)
    }

    @Test
    fun ripristinareRicostruisceLoStatoDaUnaListaDiEventi() {
        val salvato = MatchEngine(FootballRules)
        for (i in 0 until 25) {
            salvato.apply(evento(i))
        }

        val ripreso = MatchEngine(FootballRules)
        val statoRipreso = ripreso.restore(salvato.events)

        assertEquals(salvato.state, statoRipreso)
        assertEquals(salvato.state, ripreso.state)
        assertEquals(salvato.events, ripreso.events)
        assertTrue(ripreso.canUndo())
    }

    @Test
    fun ripristinareScartaLaStoriaPrecedente() {
        val engine = MatchEngine(FootballRules)
        repeat(4) { engine.apply(ScoringEvent.Point(2)) }

        val nuova = listOf(ScoringEvent.Point(1))
        engine.restore(nuova)

        assertEquals(nuova, engine.events)
        assertEquals(foldManuale(nuova), engine.state)
    }

    @Test
    fun ripristinareUnaListaVuotaEquivaleAlloStatoIniziale() {
        val engine = MatchEngine(FootballRules)
        engine.apply(ScoringEvent.Point(1))

        assertEquals(FootballRules.initial(), engine.restore(emptyList()))
        assertFalse(engine.canUndo())
    }

    @Test
    fun azzerareSvuotaStoriaEStato() {
        val engine = MatchEngine(FootballRules)
        repeat(5) { engine.apply(ScoringEvent.Point(1)) }

        val dopoReset = engine.reset()

        assertEquals(FootballRules.initial(), dopoReset)
        assertEquals(FootballRules.initial(), engine.state)
        assertTrue(engine.events.isEmpty())
        assertFalse(engine.canUndo())
    }

    /** La lista esposta e' una copia: chi l'ha in mano non vede -- ne' altera -- la storia viva. */
    @Test
    fun laStoriaEspostaEUnaCopia() {
        val engine = MatchEngine(FootballRules)
        engine.apply(ScoringEvent.Point(1))

        val istantanea = engine.events
        engine.apply(ScoringEvent.Point(2))

        assertEquals(1, istantanea.size)
        assertEquals(2, engine.events.size)
    }
}
