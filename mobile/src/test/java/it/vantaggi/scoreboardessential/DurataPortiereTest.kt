package it.vantaggi.scoreboardessential

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La durata del portiere si scrive in minuti e si tiene in secondi. Rossi se: il cinque minuti di
 * tutti i giorni non torna "5", la virgola italiana non e' accettata, o un valore fuori misura
 * (zero, un'ora e mezza, testo) passa per una durata.
 */
class DurataPortiereTest {
    @Test
    fun `i secondi si leggono in minuti`() {
        assertEquals("5", DurataPortiere.minutiDi(300L))
        assertEquals("10", DurataPortiere.minutiDi(600L))
        assertEquals("4,5", DurataPortiere.minutiDi(270L))
        assertEquals("1,5", DurataPortiere.minutiDi(90L))
    }

    @Test
    fun `i minuti scritti diventano secondi, con virgola o punto`() {
        assertEquals(300L, DurataPortiere.secondiDi("5"))
        assertEquals(240L, DurataPortiere.secondiDi(" 4 "))
        assertEquals(270L, DurataPortiere.secondiDi("4,5"))
        assertEquals(270L, DurataPortiere.secondiDi("4.5"))
        assertEquals(3600L, DurataPortiere.secondiDi("60"))
    }

    @Test
    fun `minuti e secondi fanno il giro intero`() {
        listOf(60L, 90L, 270L, 300L, 360L, 600L, 1800L).forEach { secondi ->
            assertEquals(secondi, DurataPortiere.secondiDi(DurataPortiere.minutiDi(secondi)))
        }
    }

    @Test
    fun `zero, troppo e testo non sono una durata`() {
        assertNull(DurataPortiere.secondiDi("0"))
        assertNull(DurataPortiere.secondiDi("61"))
        assertNull(DurataPortiere.secondiDi(""))
        assertNull(DurataPortiere.secondiDi("cinque"))
    }
}
