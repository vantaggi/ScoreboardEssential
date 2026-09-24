package it.vantaggi.scoreboardessential.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'attribuzione arriva sempre DOPO il punto, quindi deve poter riscrivere una voce gia' scritta.
 *
 * E' l'unica modifica permessa in un registro altrimenti in sola aggiunta, e lo e' per una ragione
 * precisa: il playerId e' inerte per le regole. Questi test lo verificano invece di fidarsene.
 */
class MatchEngineAttributeTest {
    @Test
    fun `attribuire non cambia lo stato`() {
        val engine = MatchEngine(FootballRules)
        engine.apply(ScoringEvent.Point(side = 1))
        engine.apply(ScoringEvent.Point(side = 2))
        val prima = engine.state

        engine.attribute(0, playerId = 7)

        assertEquals("il marcatore non tocca il punteggio", prima, engine.state)
    }

    @Test
    fun `attribuire scrive il marcatore nella voce giusta`() {
        val engine = MatchEngine(FootballRules)
        engine.apply(ScoringEvent.Point(side = 1))
        engine.apply(ScoringEvent.Point(side = 1))

        engine.attribute(1, playerId = 7)

        val eventi = engine.events.filterIsInstance<ScoringEvent.Point>()
        assertNull("il primo punto resta senza marcatore", eventi[0].playerId)
        assertEquals(7, eventi[1].playerId)
    }

    @Test
    fun `attribuire sopravvive al salvataggio e al ripristino`() {
        // E' il punto di tutta l'operazione: prima il marcatore viveva solo in memoria e spariva
        // alla morte del processo, mentre il punteggio sopravviveva.
        val engine = MatchEngine(FootballRules)
        engine.apply(ScoringEvent.Point(side = 1))
        engine.attribute(0, playerId = 7)

        val ripreso = MatchEngine(FootballRules)
        ripreso.restoreLog(MatchLogCodec.decode(MatchLogCodec.encode(engine.log))!!)

        assertEquals(7, ripreso.events.filterIsInstance<ScoringEvent.Point>()[0].playerId)
    }

    @Test
    fun `il riassunto vede i marcatori solo perche' l'attribuzione entra nel registro`() {
        // La catena completa: e' questa che era interrotta, e la riga dei marcatori nel report
        // restava vuota in ogni partita.
        val engine = MatchEngine(FootballRules)
        engine.apply(ScoringEvent.Point(side = 1))
        engine.apply(ScoringEvent.Point(side = 1))
        engine.attribute(0, playerId = 7)
        engine.attribute(1, playerId = 7)

        val riassunto = MatchSummarizer.summarize(engine, listOf(MatchPlayer(7, "Anna", 1)))

        assertEquals(1, riassunto.scorers.size)
        assertEquals(7, riassunto.scorers[0].playerId)
        assertEquals(2, riassunto.scorers[0].points)
    }

    @Test
    fun `un indice fuori intervallo o una correzione non fanno niente`() {
        val engine = MatchEngine(FootballRules)
        engine.apply(ScoringEvent.Point(side = 1))
        engine.apply(ScoringEvent.Correction(side = 1))
        val prima = engine.events

        // Chi chiama lavora su una lista che un annullamento puo' aver accorciato nel frattempo.
        engine.attribute(99, playerId = 7)
        engine.attribute(-1, playerId = 7)
        // Una correzione non ha un marcatore.
        engine.attribute(1, playerId = 7)

        assertEquals(prima, engine.events)
    }

    @Test
    fun `l'attribuzione dice se e' avvenuta`() {
        // Chi chiama conta il gol nel database solo se il motore ha davvero attribuito: prima un
        // indice ormai sbagliato dava comunque +1 al giocatore, per un punto che non c'era piu'.
        val engine = MatchEngine(FootballRules)
        engine.apply(ScoringEvent.Point(side = 1))
        engine.apply(ScoringEvent.Correction(side = 1))

        assertTrue(engine.attribute(0, playerId = 7))
        assertFalse("indice fuori intervallo", engine.attribute(99, playerId = 7))
        assertFalse("una correzione non ha marcatore", engine.attribute(1, playerId = 7))
    }

    @Test
    fun `un punto gia' attribuito non si riscrive`() {
        // Riscriverlo lascerebbe contato il gol del marcatore precedente: all'indice scelto nel
        // dialogo puo' esserci ormai un altro punto, gia' attribuito.
        val engine = MatchEngine(FootballRules)
        engine.apply(ScoringEvent.Point(side = 1))
        engine.attribute(0, playerId = 7)

        assertFalse(engine.attribute(0, playerId = 8))
        assertEquals(7, engine.events.filterIsInstance<ScoringEvent.Point>()[0].playerId)
    }
}
