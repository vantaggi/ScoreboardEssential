package it.vantaggi.scoreboardessential.wear

import android.graphics.Matrix
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Il menu del polso a tutta larghezza (revisione dei menu, 27 ottobre 2026): le voci vanno da un
 * margine del 5% all'altro, non piu' nel quadrato inscritto del BoxInsetLayout; sul tondo quelle
 * vicino al bordo alto e basso si stringono ([ListaTonda]) e quelle al centro sono intere; sul
 * quadrato restano tutte intere. Il titolo sta dentro la colonna e scorre via con lei.
 *
 * Rosso se il menu torna nel quadrato inscritto (righe strette) o se [ListaTonda] smette di
 * stringere le righe sul bordo.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ListeATuttaLarghezzaTest {
    private val base =
        InputMenu(
            inCoda = 0,
            collegato = true,
            partitaIniziata = true,
            haElencoSport = true,
            sport = "Padel",
            risultato = "6–4",
            regola40 = "Killer point",
        )

    private fun apri(): MenuActivity {
        val intent = MenuActivity.intent(RuntimeEnvironment.getApplication(), base)
        return Robolectric.buildActivity(MenuActivity::class.java, intent).setup().get()
    }

    private fun righe(menu: MenuActivity): List<View> =
        listOf(R.id.menu_voci, R.id.menu_impostazioni).flatMap { id ->
            val gruppo = menu.findViewById<LinearLayout>(id)
            (0 until gruppo.childCount).map(gruppo::getChildAt)
        }

    private fun scala(riga: View): Float {
        val matrice = riga.animationMatrix ?: return 1f
        val valori = FloatArray(9).also(matrice::getValues)
        return valori[Matrix.MSCALE_X]
    }

    private fun dp(
        menu: MenuActivity,
        px: Int,
    ): Float = px / menu.resources.displayMetrics.density

    @Test
    @Config(qualifiers = "it-w192dp-h192dp-round-notnight-xhdpi")
    fun `sul tondo da 192dp le voci vanno a tutta larghezza e si stringono solo vicino al bordo`() {
        val menu = apri()
        shadowOf(menu.mainLooper).idle()
        val scroll = menu.findViewById<ScrollView>(R.id.menu_scroll)
        val voci = righe(menu)

        // Larghe quanto lo schermo meno il 5% per lato: nel quadrato inscritto erano circa 130dp.
        voci.forEach { assertEquals(172f, dp(menu, it.width), 1f) }
        // Il titolo e' dentro la colonna che scorre, non fermo sopra.
        assertNotNull(scroll.findViewById<View>(R.id.menu_titolo))

        // L'ultima impostazione sta sotto lo schermo: alla scala minima. Nessuna e' piu' stretta.
        val ultima = voci.last()
        assertNotNull("l'ultima riga, fuori dallo schermo, non si e' stretta", ultima.animationMatrix)
        assertEquals(BordoTondo.SCALA_MINIMA, scala(ultima), 0.001f)
        voci.forEach { assertTrue(scala(it) >= BordoTondo.SCALA_MINIMA - 0.001f) }

        // Scorrendo fino in fondo l'ultima arriva verso il centro e torna intera.
        scroll.scrollTo(0, scroll.getChildAt(0).height - scroll.height)
        shadowOf(menu.mainLooper).idle()
        assertNull("in fondo alla lista l'ultima riga deve essere intera", ultima.animationMatrix)
    }

    @Test
    @Config(qualifiers = "it-w180dp-h180dp-notround-notnight-xhdpi")
    fun `sul quadrato le voci restano intere`() {
        val menu = apri()
        shadowOf(menu.mainLooper).idle()

        righe(menu).forEach { assertNull(it.animationMatrix) }
    }
}
