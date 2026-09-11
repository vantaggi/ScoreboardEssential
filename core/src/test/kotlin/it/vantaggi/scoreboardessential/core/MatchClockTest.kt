package it.vantaggi.scoreboardessential.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Un epoch scambiato per un tempo di partita non produce un numero "un po' sbagliato": produce
 * una durata di cinquantacinque anni. Queste sono le quattro regole che lo impediscono.
 */
class MatchClockTest {
    private val ore18 = 1_757_000_000_000L

    @Test
    fun `il primo evento definisce l'inizio e vale zero`() {
        val clock = MatchClock()
        assertFalse(clock.started)
        assertEquals(0L, clock.relative(ore18))
        assertTrue(clock.started)
    }

    @Test
    fun `gli eventi successivi misurano la distanza dal primo`() {
        val clock = MatchClock()
        clock.relative(ore18)
        assertEquals(60_000L, clock.relative(ore18 + 60_000L))
        assertEquals(3_600_000L, clock.relative(ore18 + 3_600_000L))
    }

    @Test
    fun `un evento precedente all'inizio vale zero, non un tempo negativo`() {
        // Succede davvero: l'orologio al polso e quello del telefono non sono allineati al
        // millisecondo. Un tempo negativo a valle diventa una durata negativa.
        val clock = MatchClock()
        clock.relative(ore18)
        assertEquals(0L, clock.relative(ore18 - 5_000L))
    }

    @Test
    fun `riprendendo una partita il tempo ad app chiusa non conta`() {
        val clock = MatchClock()
        // L'ultimo punto registrato stava al minuto 12; l'app riapre due ore dopo.
        clock.resume(lastRelative = 12 * 60_000L, nowEpoch = ore18)

        // Il punto successivo, dato subito, sta al minuto 12 e non al minuto 132.
        assertEquals(12 * 60_000L, clock.relative(ore18))
        assertEquals(12 * 60_000L + 30_000L, clock.relative(ore18 + 30_000L))
    }

    @Test
    fun `dopo un reset il prossimo evento ridefinisce l'inizio`() {
        val clock = MatchClock()
        clock.relative(ore18)
        clock.reset()
        assertFalse(clock.started)
        assertEquals(0L, clock.relative(ore18 + 10_000_000L))
    }
}
