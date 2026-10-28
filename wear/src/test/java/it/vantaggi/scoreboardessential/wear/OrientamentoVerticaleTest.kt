package it.vantaggi.scoreboardessential.wear

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * L'orologio ruota lo schermo quando cambia il polso o l'inclinazione, e il tabellone girava con
 * lui: a mezza partita il punteggio finiva sdraiato e i bersagli dei lati si scambiavano. Ogni
 * schermata resta in verticale, come un quadrante classico.
 */
class OrientamentoVerticaleTest {
    @Test
    fun `ogni attivita' dell'orologio e' bloccata in verticale`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val attivita = Regex("<activity\\b[^>]*>", RegexOption.DOT_MATCHES_ALL).findAll(manifest).map { it.value }.toList()

        assertTrue("nel manifest non ci sono attivita'", attivita.size >= 5)
        attivita.forEach {
            assertTrue("senza screenOrientation=portrait: $it", it.contains("android:screenOrientation=\"portrait\""))
        }
    }
}
