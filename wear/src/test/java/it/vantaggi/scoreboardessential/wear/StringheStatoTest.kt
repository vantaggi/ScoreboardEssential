package it.vantaggi.scoreboardessential.wear

import android.content.Context
import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Locale

/**
 * Nel tondo da 192dp la frase piu' lunga deve restare sotto i 18 caratteri, in entrambe le
 * lingue: oltre, il testo si taglia. Le frasi si leggono dalle risorse vere, passando dalla stessa
 * funzione che le mette a schermo, non da una lista copiata nel test.
 */
@RunWith(RobolectricTestRunner::class)
class StringheStatoTest {
    private fun contesto(lingua: String): Context {
        val app = RuntimeEnvironment.getApplication()
        val configurazione = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(lingua)) }
        return app.createConfigurationContext(configurazione)
    }

    /**
     * Ogni frase del catalogo, con il numero piu' largo che puo' comparire in una partita vera:
     * tre cifre (un padel lungo sta sotto i 300 tocchi). Con quattro cifre, cioe' vicino al tetto
     * di 2000 della coda, "NON CONSEGNATI" esce a 19 e si affida all'autoSize fino a 10sp.
     */
    private fun catalogo(n: Int): List<Frase> =
        listOf(
            Frase.Rifiutati(n),
            Frase.NonConsegnati(n),
            Frase.Invio(n),
            Frase.InCoda(n),
            Frase.Scollegato(null),
            Frase.Scollegato(1_700_000_000_000L),
            Frase.PartitaFinita,
            Frase.TieniMeno,
            Frase.TieniAnnulla,
            Transitorio.NonConfermato,
            Transitorio.Consegnati(n),
            Transitorio.Chiusura,
            Transitorio.ChiusuraNonConfermata,
        )

    private fun verificaLunghezze(lingua: String) {
        val contesto = contesto(lingua)
        listOf(9, 99, 999).forEach { n ->
            catalogo(n).forEach { frase ->
                val testo = frase.testo(contesto)
                assertTrue("[$lingua] \"$testo\" e' vuota", testo.isNotBlank())
                assertTrue("[$lingua] \"$testo\" ha ${testo.length} caratteri, il massimo e' 18", testo.length <= 18)
            }
        }
    }

    @Test
    fun `in italiano ogni frase entra nei 18 caratteri`() = verificaLunghezze("it")

    @Test
    fun `in inglese ogni frase entra nei 18 caratteri`() = verificaLunghezze("en")

    @Test
    fun `le due lingue sono davvero due, e l'italiano e' quello del design`() {
        // Senza questo, un contesto che ignorasse la lingua farebbe passare i due test sopra sullo
        // stesso catalogo: la lunghezza della frase inglese non sarebbe mai stata guardata.
        assertEquals("2 IN CODA", Frase.InCoda(2).testo(contesto("it")))
        assertEquals("2 QUEUED", Frase.InCoda(2).testo(contesto("en")))
        assertEquals("3 NON CONSEGNATI", Frase.NonConsegnati(3).testo(contesto("it")))
        assertEquals("3 NOT DELIVERED", Frase.NonConsegnati(3).testo(contesto("en")))
        assertEquals("INVIO 2…", Frase.Invio(2).testo(contesto("it")))
        assertEquals("SENDING 2…", Frase.Invio(2).testo(contesto("en")))
        assertEquals("PARTITA FINITA", Frase.PartitaFinita.testo(contesto("it")))
        assertEquals("MATCH OVER", Frase.PartitaFinita.testo(contesto("en")))
        assertEquals("TIENI: −1", Frase.TieniMeno.testo(contesto("it")))
        assertEquals("HOLD: UNDO", Frase.TieniAnnulla.testo(contesto("en")))
    }

    @Test
    fun `SCOLLEGATO porta l'ora dell'ultimo dato, e senza ora niente`() {
        val italiano = contesto("it")

        assertEquals("SCOLLEGATO", Frase.Scollegato(null).testo(italiano))
        val conOra = Frase.Scollegato(1_700_000_000_000L).testo(italiano)
        assertTrue(conOra, Regex("SCOLLEGATO · \\d\\d:\\d\\d").matches(conOra))
        assertEquals("OFFLINE", Frase.Scollegato(null).testo(contesto("en")))
    }
}
