package it.vantaggi.scoreboardessential

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Lo slot del portiere sul telefono (passo 13): in che stato e' e cosa dice a voce. Lo slot rosso
 * montato davvero si prova su emulatore (MainActivityLayoutTest).
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
}
