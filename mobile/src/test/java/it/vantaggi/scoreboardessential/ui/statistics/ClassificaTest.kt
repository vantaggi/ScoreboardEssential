package it.vantaggi.scoreboardessential.ui.statistics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il lime per un primo vero e lo stato vuoto per una classifica di zeri (passo 6). Rosso se il lime
 * torna sul primo anche a pari merito o a zero, o se una classifica di soli zeri si mostra.
 */
class ClassificaTest {
    @Test
    fun `c'e' un primo solo sopra zero e davanti al secondo`() {
        assertTrue(Classifica.haUnPrimo(listOf(5, 3, 3)))
        assertTrue(Classifica.haUnPrimo(listOf(1)))
        assertFalse("pari merito in testa", Classifica.haUnPrimo(listOf(4, 4, 1)))
        assertFalse("tutti a zero", Classifica.haUnPrimo(listOf(0, 0)))
        assertFalse("nessuno", Classifica.haUnPrimo(emptyList()))
    }

    @Test
    fun `una classifica di soli zeri e' vuota`() {
        assertTrue(Classifica.eVuota(listOf(0, 0, 0)))
        assertTrue(Classifica.eVuota(emptyList()))
        assertFalse(Classifica.eVuota(listOf(0, 1)))
    }
}
