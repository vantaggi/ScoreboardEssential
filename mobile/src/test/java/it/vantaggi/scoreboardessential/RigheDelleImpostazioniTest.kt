package it.vantaggi.scoreboardessential

import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import it.vantaggi.scoreboardessential.ui.MatchSettingsActivity
import org.junit.Assert.assertEquals
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
}
