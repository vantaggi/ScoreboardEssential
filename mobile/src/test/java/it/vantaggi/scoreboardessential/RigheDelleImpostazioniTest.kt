package it.vantaggi.scoreboardessential

import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputLayout
import it.vantaggi.scoreboardessential.ui.MatchSettingsActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * Impostazioni a righe (bozza B di Cioti, 28 ottobre 2026): sport, regola del 40-40 e lingua sono righe
 * col nome a sinistra e il valore a destra; il tocco apre un foglio con la spunta su quello in uso.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RigheDelleImpostazioniTest {
    private fun impostazioni(): MatchSettingsActivity = Robolectric.buildActivity(MatchSettingsActivity::class.java).setup().get()

    /** Lo sport attivo arriva dalle impostazioni salvate, in una coroutine: si aspetta che il valore sia a schermo. */
    private fun conLoSportCaricato(): MatchSettingsActivity {
        val schermata = impostazioni()
        repeat(100) {
            shadowOf(Looper.getMainLooper()).idle()
            if (schermata.findViewById<TextView>(R.id.sportValue).text.isNotEmpty()) return schermata
            Thread.sleep(50)
        }
        return schermata
    }

    @Test
    fun `le righe mostrano il valore in uso e non un campo da scrivere`() {
        val schermata = conLoSportCaricato()

        assertTrue("lo sport non e' una riga", schermata.findViewById<View>(R.id.sportRow).isClickable)
        assertTrue("la lingua non e' una riga", schermata.findViewById<View>(R.id.languageRow).isClickable)
        assertTrue(
            "il valore dello sport e' vuoto",
            schermata.findViewById<TextView>(R.id.sportValue).text.isNotEmpty(),
        )
        assertTrue(
            "il valore della lingua e' vuoto",
            schermata.findViewById<TextView>(R.id.languageValue).text.isNotEmpty(),
        )
    }

    @Test
    fun `il tocco sullo sport apre il foglio con la spunta sullo sport in uso`() {
        val schermata = conLoSportCaricato()
        schermata.findViewById<View>(R.id.sportRow).performClick()

        val foglio = ShadowDialog.getLatestDialog() as AlertDialog
        val valore = schermata.findViewById<TextView>(R.id.sportValue).text.toString()
        val spuntata = foglio.listView.checkedItemPosition

        assertTrue("nessuna voce spuntata", spuntata >= 0)
        assertEquals(
            valore,
            foglio.listView.adapter
                .getItem(spuntata)
                .toString(),
        )
    }

    /**
     * Bozza B1 di Cioti: la durata del portiere ha l'etichetta sopra il campo, che va a capo, e non
     * nel bordo del campo, dove a 360dp col testo al 200% si tagliava. Rosso se l'etichetta torna
     * dentro il campo (hint), se si stacca dal campo o se non puo' andare a capo.
     */
    @Test
    fun `la durata del portiere ha l'etichetta sopra il campo, intera`() {
        val schermata = impostazioni()
        val etichetta = schermata.findViewById<TextView>(R.id.keeperTimerLabel)
        val campo = schermata.findViewById<TextInputLayout>(R.id.keeperTimerLayout)
        val gruppo = schermata.findViewById<View>(R.id.keeperTimerField)

        assertEquals(schermata.getString(R.string.keeper_timer_minutes), etichetta.text.toString())
        assertNull("l'etichetta e' ancora nel bordo del campo", campo.hint)
        assertEquals("l'etichetta non dice a TalkBack di che campo e'", R.id.keeperTimerEdittext, etichetta.labelFor)
        assertSame("etichetta e campo si mostrano e si nascondono insieme", gruppo, etichetta.parent)
        assertSame(gruppo, campo.parent)
        assertEquals("l'etichetta deve poter andare a capo", Int.MAX_VALUE, etichetta.maxLines)
        assertNull("l'etichetta non deve avere i puntini", etichetta.ellipsize)
    }
}
