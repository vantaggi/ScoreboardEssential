package it.vantaggi.scoreboardessential

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.constraintlayout.helper.widget.Flow
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.test.core.app.ApplicationProvider
import it.vantaggi.scoreboardessential.ui.TavolozzaDiSquadra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prova a schermo del 28 ottobre 2026 sul telefono, a 360dp e al 200%: il dialogo del colore mostrava due
 * cerchi su otto e lo Storico perdeva il cestino. Le due strutture restano a pesi o a capo.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LayoutAStrettoTest {
    private val contesto =
        ApplicationProvider.getApplicationContext<android.content.Context>().let {
            androidx.appcompat.view.ContextThemeWrapper(it, R.style.Theme_ScoreboardEssential)
        }

    @Test
    fun `i cerchi del colore stanno in otto celle di pari larghezza su due righe`() {
        val vista = LayoutInflater.from(contesto).inflate(R.layout.dialog_team_palette, null)
        val griglia = vista.findViewById<ViewGroup>(R.id.team_palette_grid)

        assertTrue("la tavolozza non e' a righe", griglia is LinearLayout)
        assertEquals(2, griglia.childCount)
        for (id in TavolozzaDiSquadra.cerchi) {
            val cella = vista.findViewById<View>(id).parent as View
            assertTrue("il cerchio non sta in una cella", cella is FrameLayout)
            assertEquals(1f, (cella.layoutParams as LinearLayout.LayoutParams).weight, 0f)
        }
        assertEquals(8, TavolozzaDiSquadra.cerchi.size)
    }

    @Test
    fun `i comandi della scheda vanno a capo e il cestino ne fa parte`() {
        val scheda = LayoutInflater.from(contesto).inflate(R.layout.match_item, null)
        val cestino = scheda.findViewById<View>(R.id.delete_match_button)
        val contenitore = cestino.parent as ConstraintLayout

        val flow = (0 until contenitore.childCount).map { contenitore.getChildAt(it) }.filterIsInstance<Flow>().single()
        assertTrue(
            "il cestino non e' tra i comandi che vanno a capo",
            flow.referencedIds.contains(R.id.delete_match_button),
        )
        assertTrue(flow.referencedIds.contains(R.id.export_match_button))
    }
}
