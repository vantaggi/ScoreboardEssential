package it.vantaggi.scoreboardessential.wear

import android.content.Intent
import android.util.TypedValue
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.wear.widget.WearableRecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Una parola non si spezza mai: i titoli del menu e della selezione dello sport sul tondo da 192dp.
 *
 * Sul tondo il BoxInsetLayout stringe le card a circa 104dp, e "PARTITA" in maiuscolo a 20sp bold
 * non c'entrava: "FINE PA" / "RTITA". Si misura sul Layout vero del TextView, con la grafica
 * nativa (con quella di default measureText vale un pixel a carattere e il test non direbbe niente).
 * A fontScale 1.0, come dice la regola del design.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TitoliIntegriTest {
    private val base =
        InputMenu(
            inCoda = 0,
            collegato = true,
            partitaIniziata = true,
            haElencoSport = true,
            sport = "Padel",
            risultato = "6–4",
        )

    private fun sp(valore: Float): Float = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, valore, metriche())

    private fun metriche() = RuntimeEnvironment.getApplication().resources.displayMetrics

    private fun dp(px: Int): Float = px / metriche().density

    /** Impagina di nuovo la finestra: dopo un cambio di testo il Layout si rifa solo in una passata. */
    private fun impagina(radice: View) {
        radice.measure(
            MeasureSpec.makeMeasureSpec(radice.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(radice.height, MeasureSpec.EXACTLY),
        )
        radice.layout(0, 0, radice.width, radice.height)
    }

    /**
     * Ogni a capo cade su uno spazio, nessuna riga e' piu' larga della vista e il carattere non
     * scende sotto i 14sp.
     */
    private fun verificaIntero(titolo: TextView) {
        val layout = titolo.layout
        assertTrue("\"${titolo.text}\" senza Layout: la vista non e' stata impaginata", layout != null && titolo.width > 0)
        val visibile = layout.text
        val utile = titolo.width - titolo.compoundPaddingLeft - titolo.compoundPaddingRight

        assertTrue("\"$visibile\" a ${titolo.textSize}px, sotto i 14sp", titolo.textSize >= sp(14f) - 0.01f)
        val righe = (0 until layout.lineCount).map { visibile.subSequence(layout.getLineStart(it), layout.getLineEnd(it)) }
        assertTrue("\"$visibile\" va su ${layout.lineCount} righe $righe a ${titolo.textSize}px su $utile", layout.lineCount <= 2)
        (0 until layout.lineCount).forEach { riga ->
            val larghezza = layout.getLineMax(riga)
            assertTrue("riga $riga di \"$visibile\" e' larga $larghezza px e la vista ne da $utile", larghezza <= utile + 0.5f)
        }
        (0 until layout.lineCount - 1).forEach { riga ->
            val fine = layout.getLineEnd(riga)
            val inizio = layout.getLineStart(riga)
            val riga1 = visibile.subSequence(inizio, fine)
            assertTrue(
                "\"$visibile\" va a capo a meta' parola: riga $riga \"$riga1\" a ${dp(titolo.width)}dp",
                visibile[fine - 1].isWhitespace() || (fine < visibile.length && visibile[fine].isWhitespace()),
            )
        }
    }

    private fun titoliDelMenu(menu: MenuActivity): List<TextView> {
        val voci = menu.findViewById<LinearLayout>(R.id.menu_voci)
        return (0 until voci.childCount).map { voci.getChildAt(it).findViewById(R.id.sport_name) }
    }

    private fun apriMenu(): MenuActivity {
        val intent = MenuActivity.intent(RuntimeEnvironment.getApplication(), base)
        return Robolectric.buildActivity(MenuActivity::class.java, intent).setup().get()
    }

    private fun verificaMenu(attesa: String) {
        val menu = apriMenu()
        val titoli = titoliDelMenu(menu)
        val fine = titoli.last()
        assertEquals(attesa, menu.getString(R.string.wear_menu_end))
        assertEquals(attesa, fine.text.toString())
        assertTrue("la card e' larga ${dp(fine.width)}dp: il tondo non e' quello vero?", dp(fine.width) in 40f..140f)
        titoli.forEach(::verificaIntero)

        // Il primo tocco arma la conferma: il titolo cambia e deve restare intero anche cosi'.
        menu.findViewById<LinearLayout>(R.id.menu_voci).getChildAt(1).performClick()
        impagina(menu.window.decorView)
        titoliDelMenu(menu).forEach(::verificaIntero)
    }

    @Test
    @Config(qualifiers = "it-w192dp-h192dp-round-notnight-xhdpi")
    fun `tondo da 192dp in italiano, FINE PARTITA va a capo solo sullo spazio`() {
        val configurazione = RuntimeEnvironment.getApplication().resources.configuration
        assertTrue("la configurazione non e' tonda", configurazione.isScreenRound)
        assertEquals(1f, configurazione.fontScale, 0f)
        verificaMenu("FINE PARTITA")
    }

    @Test
    @Config(qualifiers = "en-w192dp-h192dp-round-notnight-xhdpi")
    fun `tondo da 192dp in inglese, END MATCH va a capo solo sullo spazio`() {
        verificaMenu("END MATCH")
    }

    private fun verificaSelezione() {
        val intent =
            Intent(RuntimeEnvironment.getApplication(), SportSelectionActivity::class.java).apply {
                putStringArrayListExtra(SportSelectionActivity.EXTRA_IDS, arrayListOf("football", "football", "padel", "tennis"))
                putStringArrayListExtra(SportSelectionActivity.EXTRA_LABELS, arrayListOf("Football", "Calcio", "Padel", "Tennis"))
                putExtra(SportSelectionActivity.EXTRA_CURRENT, "Padel")
            }
        val attivita = Robolectric.buildActivity(SportSelectionActivity::class.java, intent).setup().get()
        val lista = attivita.findViewById<WearableRecyclerView>(R.id.sport_list)
        assertTrue("la lista non e' stata impaginata", lista.width > 0)

        (0 until 4).forEach { posizione ->
            // La riga con la larghezza che la lista le da davvero, come la misura il layout manager.
            val holder = lista.adapter!!.createViewHolder(FrameLayout(attivita), 0)
            lista.adapter!!.bindViewHolder(holder, posizione)
            val riga = holder.itemView
            riga.measure(
                MeasureSpec.makeMeasureSpec(lista.width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            riga.layout(0, 0, riga.measuredWidth, riga.measuredHeight)
            verificaIntero(riga.findViewById(R.id.sport_name))
        }
    }

    @Test
    @Config(qualifiers = "it-w192dp-h192dp-round-notnight-xhdpi")
    fun `selezione sport sul tondo da 192dp, nomi interi in italiano`() {
        verificaSelezione()
    }

    @Test
    @Config(qualifiers = "en-w192dp-h192dp-round-notnight-xhdpi")
    fun `selezione sport sul tondo da 192dp, nomi interi in inglese`() {
        verificaSelezione()
    }
}
