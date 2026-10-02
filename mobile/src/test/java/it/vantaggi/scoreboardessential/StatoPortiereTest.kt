package it.vantaggi.scoreboardessential

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Lo slot del portiere sul telefono (passo 13): in che stato e', cosa dice a voce, e se il tocco
 * deve azzerare prima di ripartire. Lo slot rosso montato davvero si prova su emulatore
 * (MainActivityLayoutTest).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "it")
class StatoPortiereTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `lo stato dello slot viene dal conto e dall'evento di scadenza`() {
        assertEquals(StatoPortiere.FERMO, statoDelPortiere(inCorso = false, scaduto = false))
        assertEquals(StatoPortiere.IN_CORSO, statoDelPortiere(inCorso = true, scaduto = false))
        assertEquals(StatoPortiere.SCADUTO, statoDelPortiere(inCorso = false, scaduto = true))
        assertEquals("un conto che riparte non e' piu' scaduto", StatoPortiere.IN_CORSO, statoDelPortiere(inCorso = true, scaduto = true))
    }

    @Test
    fun `lo slot dice a voce lo stato e l'azione`() {
        val fermo = descrizioneDelPortiere(context, StatoPortiere.FERMO, "05:00")
        val inCorso = descrizioneDelPortiere(context, StatoPortiere.IN_CORSO, "03:12")
        val scaduto = descrizioneDelPortiere(context, StatoPortiere.SCADUTO, "00:00")

        assertEquals("Portiere, fermo, 05:00. Tocca per avviare", fermo)
        assertEquals("Portiere, in corso, 03:12. Tocca per ripartire da capo, cambio avvenuto", inCorso)
        assertEquals("Portiere, tempo scaduto, cambio. Tocca per ripartire", scaduto)
    }

    @Test
    @Config(qualifiers = "en")
    fun `a voce lo slot segue la lingua`() {
        assertEquals("Keeper, stopped, 05:00. Tap to start", descrizioneDelPortiere(context, StatoPortiere.FERMO, "05:00"))
        assertEquals("CHANGE", context.getString(R.string.label_keeper_change))
    }

    @Test
    fun `CAMBIO e il testo dello slot scaduto`() {
        assertEquals("CAMBIO", context.getString(R.string.label_keeper_change))
    }

    @Test
    fun `il tocco azzera il conto in corso o fermo a meta ma non quello fermo a durata piena o scaduto`() {
        val durata = 300_000L
        assertTrue("in corso: il cambio e' avvenuto, si riparte da capo", toccoDelPortiereAzzera(true, 120_000L, durata))
        assertTrue("fermo a meta' (pausa dall'orologio): il service riprenderebbe dal residuo", toccoDelPortiereAzzera(false, 120_000L, durata))
        assertFalse("fermo a durata piena: niente da azzerare", toccoDelPortiereAzzera(false, durata, durata))
        assertFalse("scaduto a zero: niente da azzerare", toccoDelPortiereAzzera(false, 0L, durata))
    }
}
