package it.vantaggi.scoreboardessential.wear

import android.graphics.Rect
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
 * La riga del perche' sta sopra la piega: sul tondo da 192dp, quando il menu mostra una voce
 * spenta, titolo e sottotitolo della prima card si leggono senza scorrere con la corona.
 * Si misura con i bounds veri dopo il layout, con la grafica nativa e a fontScale 1.0.
 *
 * Sul tondo l'area visibile e' circa 77dp (rientro del BoxInsetLayout, titolo PARTITA), e la card
 * con titolo su due righe e sottotitolo su due ne chiedeva piu' di 100.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MenuPiegaTest {
    /** Punti in coda: FINE PARTITA e' spenta e il sottotitolo dice "Prima consegna n punti". */
    private fun conCoda(haElencoSport: Boolean) =
        InputMenu(
            inCoda = 1,
            collegato = true,
            partitaIniziata = true,
            haElencoSport = haElencoSport,
            sport = "Padel",
            risultato = "6–4",
        )

    private fun metriche() = RuntimeEnvironment.getApplication().resources.displayMetrics

    private fun dp(px: Int): Float = px / metriche().density

    /** Il fondo della vista nelle coordinate dell'area visibile dello ScrollView, senza scorrere. */
    private fun fondoVisibile(
        scroll: ScrollView,
        vista: View,
    ): Int {
        val r = Rect(0, 0, vista.width, vista.height)
        scroll.offsetDescendantRectToMyCoords(vista, r)
        return r.bottom - scroll.scrollY
    }

    private fun verificaPrimaCardIntera(
        input: InputMenu,
        tondo: Boolean = true,
        cardIntera: Boolean = true,
    ) {
        val menu =
            Robolectric
                .buildActivity(MenuActivity::class.java, MenuActivity.intent(RuntimeEnvironment.getApplication(), input))
                .setup()
                .get()
        val scroll = menu.findViewById<ScrollView>(R.id.menu_scroll)
        val voci = menu.findViewById<LinearLayout>(R.id.menu_voci)
        val prima = voci.getChildAt(0)
        val sotto = prima.findViewById<TextView>(R.id.sport_current)
        assertEquals("la configurazione non e' quella attesa", tondo, menu.resources.configuration.isScreenRound)
        assertTrue("la scroll non e' stata impaginata", scroll.height > 0 && sotto.height > 0)
        assertEquals("il test misura senza scorrere", 0, scroll.scrollY)

        val visibile = dp(scroll.height)
        val fondoSotto = dp(fondoVisibile(scroll, sotto))
        val fondoCard = dp(fondoVisibile(scroll, prima))
        println(
            "MISURA ${menu.resources.configuration.locales[0]} voci=${voci.childCount}: " +
                "visibile=${visibile}dp card=${dp(prima.height)}dp fondoSottotitolo=${fondoSotto}dp fondoCard=${fondoCard}dp " +
                "sottotitolo=${sotto.lineCount}righe \"${sotto.text}\"",
        )
        assertTrue(
            "il sottotitolo \"${sotto.text}\" finisce a ${fondoSotto}dp, l'area visibile e' ${visibile}dp",
            fondoSotto <= visibile + 0.5f,
        )
        if (cardIntera) assertTrue("la card finisce a ${fondoCard}dp, l'area visibile e' ${visibile}dp", fondoCard <= visibile + 0.5f)
    }

    @Test
    @Config(qualifiers = "it-w192dp-h192dp-round-notnight-xhdpi")
    fun `tondo da 192dp in italiano, la sola FINE PARTITA spenta si legge senza scorrere`() {
        verificaPrimaCardIntera(conCoda(haElencoSport = false))
    }

    @Test
    @Config(qualifiers = "en-w192dp-h192dp-round-notnight-xhdpi")
    fun `tondo da 192dp in inglese, la sola FINE PARTITA spenta si legge senza scorrere`() {
        verificaPrimaCardIntera(conCoda(haElencoSport = false))
    }

    @Test
    @Config(qualifiers = "it-w192dp-h192dp-round-notnight-xhdpi")
    fun `tondo da 192dp in italiano, con SPORT spenta la prima card si legge senza scorrere`() {
        verificaPrimaCardIntera(conCoda(haElencoSport = true))
    }

    @Test
    @Config(qualifiers = "en-w192dp-h192dp-round-notnight-xhdpi")
    fun `tondo da 192dp in inglese, con SPORT spenta la prima card si legge senza scorrere`() {
        verificaPrimaCardIntera(conCoda(haElencoSport = true))
    }

    /**
     * Le misure piccole sono solo del tondo da 192dp: il 227dp e il quadrato da 180dp tengono
     * rientro e padding di sempre. La card intera non si chiede: sul 227dp il suo fondo sfora
     * l'area visibile di 15dp, com'era prima e fuori da questo passo. Il sottotitolo invece sta dentro.
     */
    private fun verificaMisureDiSempre() {
        val risorse = RuntimeEnvironment.getApplication().resources
        assertEquals(16f, risorse.getDimension(R.dimen.menu_box_padding) / risorse.displayMetrics.density, 0.01f)
        assertEquals(8f, risorse.getDimension(R.dimen.voce_padding_verticale) / risorse.displayMetrics.density, 0.01f)
        assertTrue(risorse.getBoolean(R.bool.voce_rientro_angolo))
    }

    @Test
    @Config(qualifiers = "it-w227dp-h227dp-round-notnight-xhdpi")
    fun `tondo da 227dp, le misure del menu restano quelle di sempre`() {
        verificaMisureDiSempre()
        verificaPrimaCardIntera(conCoda(haElencoSport = false), cardIntera = false)
    }

    @Test
    @Config(qualifiers = "it-w180dp-h180dp-notround-notnight-xhdpi")
    fun `quadrato da 180dp, le misure del menu restano quelle di sempre`() {
        verificaMisureDiSempre()
        verificaPrimaCardIntera(conCoda(haElencoSport = false), tondo = false, cardIntera = false)
    }
}
