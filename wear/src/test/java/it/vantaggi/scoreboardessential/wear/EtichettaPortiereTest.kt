package it.vantaggi.scoreboardessential.wear

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Come il polso scrive la durata del portiere. Rosso se 4,5 minuti scritti dal telefono tornano a leggersi
 * "4 min 30 s" (visto al giro sugli emulatori) o se Off, i minuti interi o i secondi cambiano forma.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "it")
class EtichettaPortiereTest {
    private val contesto: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `i decimi di minuto si leggono coi decimali, come nel campo del telefono`() {
        assertEquals("Off", etichettaPortiere(contesto, 0))
        assertEquals("45 s", etichettaPortiere(contesto, 45))
        assertEquals("5 min", etichettaPortiere(contesto, 300))
        assertEquals("4,5 min", etichettaPortiere(contesto, 270))
        assertEquals("1,5 min", etichettaPortiere(contesto, 90))
        assertEquals("1 min 20 s", etichettaPortiere(contesto, 80))
    }
}
