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
     * senso, che le regole ignorano e che quindi non entrano nella storia.
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
     * La proprieta' centrale, nella forma in cui e' osservabile: per ogni evento **con effetto**,
     * applicare e poi annullare riporta esattamente allo stato di prima e alla stessa storia.
     *
     * L'altra meta' del contratto e' altrettanto importante e sta nello stesso ciclo: un evento
     * senza effetto non cambia ne' lo stato ne' la storia, quindi non c'e' niente da annullare.
     * Duecento passi con eventi anche assurdi -- lati 0 e 3 compresi, che le regole ignorano.
     */
    @Test
    fun applicarePoiAnnullareTornaEsattamenteAlloStatoPrecedente() {
        val engine = MatchEngine(FootballRules)
        var conEffetto = 0
        var senzaEffetto = 0

        for (i in 0 until 200) {
            val statoPrima = engine.state
            val storiaPrima = engine.events

            engine.apply(evento(i))

            if (engine.events == storiaPrima) {
                // Non e' successo niente: non e' entrato nella storia, e lo stato e' fermo.
                senzaEffetto++
                assertEquals(statoPrima, engine.state)
            } else {
                conEffetto++
                engine.undo()
                assertEquals(statoPrima, engine.state)
                assertEquals(storiaPrima, engine.events)
            }

            // Si avanza sul serio, altrimenti la sequenza resterebbe lunga zero per sempre.
            engine.apply(evento(i + 1))
        }

        // Il ciclo deve aver esercitato ENTRAMBI i rami, altrimenti meta' contratto non e' provata.
        assertTrue("nessun evento con effetto", conEffetto > 0)
        assertTrue("nessun evento senza effetto", senzaEffetto > 0)
    }

    /**
     * Il caso che ha fatto rovesciare la decisione: un tocco a partita finita non deve consumare
     * un annullamento.
     *
     * Prima veniva registrato, quindi il primo annullamento lo toglieva e **sembrava non fare
     * niente**; per togliere davvero il punto precedente ne servivano due. Un comando che appare
     * inerte non si distingue da un'app bloccata.
     */
    @Test
    fun unToccoDopoLaFineNonConsumaUnAnnullamento() {
        val regole =
            RacketRules(
                id = SportRegistry.PADEL,
                config = SportConfig(mode = ScoringMode.POINTS, deuce = DeuceRule.GOLDEN_POINT, sets = 1),
            )
        val engine = MatchEngine(regole)
        // Col golden point un game si chiude a 4 punti: 24 punti di fila sono 6-0, set e partita.
        repeat(24) { engine.apply(ScoringEvent.Point(side = 1)) }
        val finita = engine.state
        assertEquals(1, finita.wonBy)

        engine.apply(ScoringEvent.Point(side = 2))

        assertEquals("un tocco a partita finita non e' successo", 24, engine.log.size)
        assertEquals(finita, engine.state)

        // UN solo annullamento, UN cambiamento visibile.
        engine.undo()
        assertEquals(23, engine.log.size)
        assertEquals(null, engine.state.wonBy)
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
