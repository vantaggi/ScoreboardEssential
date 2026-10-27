package it.vantaggi.scoreboardessential.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le righe delle liste del polso sul tondo: intere al centro, strette vicino al bordo alto e basso
 * (wearable.md). Le misure sono quelle del tondo da 192dp a xhdpi: 384px di diametro, righe larghe
 * 344px (5% di margine per lato).
 */
class BordoTondoTest {
    private val diametro = 384f
    private val larghezza = 344f

    @Test
    fun `la riga al centro e' intera`() {
        assertEquals(1f, BordoTondo.scala(140f, 244f, larghezza, diametro), 0f)
    }

    @Test
    fun `vicino al bordo la riga si stringe fino a stare nel cerchio`() {
        // Riga da 104px col bordo alto a 40px dallo schermo: la corda a 152px dal centro e' 2 * 117px.
        val scala = BordoTondo.scala(40f, 144f, larghezza, diametro)
        val corda = 2f * kotlin.math.sqrt(192f * 192f - 152f * 152f)

        assertEquals(corda / larghezza, scala, 0.001f)
        assertTrue("la riga deve stringersi, scala $scala", scala < 1f)
        // Sotto come sopra: conta la distanza dal centro, non il verso.
        assertEquals(scala, BordoTondo.scala(diametro - 144f, diametro - 40f, larghezza, diametro), 0.001f)
    }

    @Test
    fun `una riga fuori dallo schermo resta alla scala minima`() {
        assertEquals(BordoTondo.SCALA_MINIMA, BordoTondo.scala(400f, 504f, larghezza, diametro), 0f)
        assertEquals(BordoTondo.SCALA_MINIMA, BordoTondo.scala(-120f, -16f, larghezza, diametro), 0f)
    }

    @Test
    fun `senza misure non si tocca niente`() {
        assertEquals(1f, BordoTondo.scala(0f, 0f, 0f, diametro), 0f)
        assertEquals(1f, BordoTondo.scala(0f, 100f, larghezza, 0f), 0f)
    }
}
