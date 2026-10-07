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
            Transitorio.CambioSport,
            Transitorio.SportNonCambiato,
            Transitorio.InAttesaDelTelefono,
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
        assertEquals("2 in coda", Frase.InCoda(2).testo(contesto("it")))
        assertEquals("2 queued", Frase.InCoda(2).testo(contesto("en")))
        assertEquals("3 non consegnati", Frase.NonConsegnati(3).testo(contesto("it")))
        assertEquals("3 not delivered", Frase.NonConsegnati(3).testo(contesto("en")))
        assertEquals("Invio 2…", Frase.Invio(2).testo(contesto("it")))
        assertEquals("Sending 2…", Frase.Invio(2).testo(contesto("en")))
        assertEquals("Partita finita", Frase.PartitaFinita.testo(contesto("it")))
        assertEquals("Match over", Frase.PartitaFinita.testo(contesto("en")))
        assertEquals("Tieni: −1", Frase.TieniMeno.testo(contesto("it")))
        assertEquals("Hold: undo", Frase.TieniAnnulla.testo(contesto("en")))
        // La chiusura e' partita e non si ritira: la frase dice che manca la conferma, non "non chiusa".
        assertEquals("Non confermata", Transitorio.ChiusuraNonConfermata.testo(contesto("it")))
        assertEquals("Not confirmed", Transitorio.ChiusuraNonConfermata.testo(contesto("en")))
        // Il cambio sport: l'italiano di SPORT NON CAMBIATO sta al limite, 18 su 18.
        assertEquals("Cambio sport…", Transitorio.CambioSport.testo(contesto("it")))
        assertEquals("Changing sport…", Transitorio.CambioSport.testo(contesto("en")))
        assertEquals("Sport non cambiato", Transitorio.SportNonCambiato.testo(contesto("it")))
        assertEquals("Sport not changed", Transitorio.SportNonCambiato.testo(contesto("en")))
        // La custodia (L5): il telefono ha messo da parte il tocco, l'italiano e' di nuovo al limite, 18 su 18.
        assertEquals("In attesa telefono", Transitorio.InAttesaDelTelefono.testo(contesto("it")))
        assertEquals("Waiting for phone", Transitorio.InAttesaDelTelefono.testo(contesto("en")))
    }

    @Test
    fun `SCOLLEGATO porta l'ora dell'ultimo dato, e senza ora niente`() {
        val italiano = contesto("it")

        assertEquals("Scollegato", Frase.Scollegato(null).testo(italiano))
        val conOra = Frase.Scollegato(1_700_000_000_000L).testo(italiano)
        assertTrue(conOra, Regex("Scollegato · \\d\\d:\\d\\d").matches(conOra))
        assertEquals("Offline", Frase.Scollegato(null).testo(contesto("en")))
    }
}
