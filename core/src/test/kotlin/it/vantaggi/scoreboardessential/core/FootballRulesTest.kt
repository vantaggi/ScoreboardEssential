package it.vantaggi.scoreboardessential.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La suite descrive il comportamento ATTUALE del calcio, non quello desiderabile: se un test
 * qui dentro fallisce dopo una modifica, e' la modifica ad aver cambiato la partita.
 */
class FootballRulesTest {
    private fun counter(
        side1: Int,
        side2: Int,
    ) = CounterScore(points = listOf(side1, side2))

    @Test
    fun `stato iniziale a zero`() {
        assertEquals(counter(0, 0), FootballRules.initial())
    }

    @Test
    fun `un punto incrementa il lato indicato`() {
        assertEquals(counter(1, 0), FootballRules.apply(counter(0, 0), ScoringEvent.Point(1)))
        assertEquals(counter(2, 4), FootballRules.apply(counter(2, 3), ScoringEvent.Point(2)))
    }

    @Test
    fun `la correzione toglie un punto al lato indicato`() {
        assertEquals(counter(1, 3), FootballRules.apply(counter(2, 3), ScoringEvent.Correction(1)))
        assertEquals(counter(2, 2), FootballRules.apply(counter(2, 3), ScoringEvent.Correction(2)))
    }

    @Test
    fun `la correzione a zero non scende sotto zero`() {
        assertEquals(counter(0, 0), FootballRules.apply(counter(0, 0), ScoringEvent.Correction(1)))
        assertEquals(counter(5, 0), FootballRules.apply(counter(5, 0), ScoringEvent.Correction(2)))
    }

    @Test
    fun `wonBy resta null anche con punteggi alti`() {
        val finale =
            (1..12).fold(FootballRules.initial()) { stato, _ ->
                FootballRules.apply(stato, ScoringEvent.Point(1))
            }
        assertEquals(12 to 0, finale.headline())
        assertNull(finale.wonBy)
    }

    @Test
    fun `apply non muta lo stato di partenza`() {
        val partenza = counter(1, 1)
        FootballRules.apply(partenza, ScoringEvent.Point(1))
        FootballRules.apply(partenza, ScoringEvent.Correction(2))
        assertEquals(counter(1, 1), partenza)
    }

    @Test
    fun `un lato fuori da 1 a 2 lascia lo stato invariato`() {
        val partenza = counter(3, 2)
        for (lato in listOf(-1, 0, 3, 99)) {
            assertSame(partenza, FootballRules.apply(partenza, ScoringEvent.Point(lato)))
            assertSame(partenza, FootballRules.apply(partenza, ScoringEvent.Correction(lato)))
        }
    }

    @Test
    fun `headline ritorna i due punteggi`() {
        assertEquals(4 to 2, counter(4, 2).headline())
    }

    @Test
    fun `display mostra solo il punteggio`() {
        val display = FootballRules.display(counter(3, 1))
        assertEquals("3", display.side1Primary)
        assertEquals("1", display.side2Primary)
        assertNull(display.side1Secondary)
        assertNull(display.side2Secondary)
        assertNull(display.periodLabel)
    }

    @Test
    fun `le capability del calcio`() {
        val capabilities = FootballRules.capabilities
        assertEquals(ClockMode.COUNT_UP, capabilities.clock)
        assertEquals("goal", capabilities.scoreEventKey)
        assertFalse(capabilities.decrementIsUndo)
        assertTrue(capabilities.hasAuxCountdown)
        assertTrue(capabilities.hasRoles)
        assertTrue(capabilities.attributesScorer)
    }
}
