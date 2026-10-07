package it.vantaggi.scoreboardessential.ui.chronicle

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.View.MeasureSpec
import androidx.test.core.app.ApplicationProvider
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.core.TeamInk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Il grafico dell'andamento (G-5), disegnato davvero su una bitmap: il lato 1 sta sopra lo zero in
 * lime, il lato 2 sotto in ciano, ogni lato ha il suo nome e le linee tratteggiate chiudono i set.
 * Il colore non e' l'unico segno, e ogni prova ha la sua falsificazione.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w411dp-h923dp-xxhdpi")
class MomentumViewTest {
    private val lime = 0xFFC8F135.toInt()
    private val ciano = 0xFF00E5FF.toInt()
    private val ctx get() = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Application>(), R.style.Theme_ScoreboardEssential)

    // Avanti il lato 1 fino al 3, poi il lato 2 fino al -3; il set finisce dopo il quinto punto.
    private val diffs = listOf(1, 2, 3, 2, 1, 0, -1, -2, -3, -2)

    private fun vista(
        c1: Int = lime,
        c2: Int = ciano,
        punti: List<Int> = diffs,
        fineSet: List<Int> = listOf(4),
    ): MomentumView =
        MomentumView(ctx).apply {
            setData(punti, fineSet, c1, c2, "Rossi", "Blu")
            measure(MeasureSpec.makeMeasureSpec(LARGHEZZA, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            layout(0, 0, measuredWidth, measuredHeight)
        }

    private fun disegna(v: MomentumView): Bitmap =
        Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888).also { v.draw(Canvas(it)) }

    private fun righeCon(
        b: Bitmap,
        colore: Int,
    ): List<Int> = (0 until b.height).filter { y -> (0 until b.width).any { x -> b.getPixel(x, y) == colore } }

    /** Vero se il colore del lato 1 sta tutto sopra la meta' e quello del lato 2 tutto sotto (a meno dello spessore della linea). */
    private fun latoSopraELatoSotto(
        b: Bitmap,
        c1: Int,
        c2: Int,
    ): Boolean {
        val sopra = righeCon(b, c1)
        val sotto = righeCon(b, c2)
        val tolleranza = 3 * b.height / 100
        return sopra.size > 20 && sotto.size > 20 && sopra.max() <= b.height / 2 + tolleranza && sotto.min() >= b.height / 2 - tolleranza
    }

    @Test
    fun `il lato 1 e' lime sopra lo zero e il lato 2 e' ciano sotto`() {
        val v = vista()
        assertEquals(lime to ciano, v.sideColors)
        assertTrue(latoSopraELatoSotto(disegna(v), lime, ciano))
        // Falsificazione: coi colori scambiati il lato 1 e' sempre sopra, ma non piu' lime: la prova lo vede.
        val scambiato = vista(c1 = ciano, c2 = lime)
        assertFalse(latoSopraELatoSotto(disegna(scambiato), lime, ciano))
    }

    @Test
    fun `lime e ciano reggono 3 a 1 sul gruppo`() {
        val gruppo = ctx.getColor(R.color.elite_surface)
        assertTrue(TeamInk.contrast(lime, gruppo) >= 3.0)
        assertTrue(TeamInk.contrast(ciano, gruppo) >= 3.0)
        assertEquals(lime, ctx.getColor(R.color.elite_lime))
        assertEquals(ciano, ctx.getColor(R.color.elite_cyan))
    }

    /** Il secondo segno oltre al colore: il nome di ciascun lato, con il suo vantaggio massimo se c'e'. */
    @Test
    fun `ogni lato ha il suo nome e il vantaggio massimo`() {
        assertEquals(listOf("Rossi +3", "Blu +3"), vista().labels)
        // Un lato mai avanti ha solo il nome: un +0 non direbbe niente.
        assertEquals(listOf("Rossi +3", "Blu"), vista(punti = listOf(1, 2, 3)).labels)
        // Senza punti non c'e' niente da nominare.
        assertEquals(emptyList<String>(), MomentumView(ctx).labels)
    }

    /** Il tratteggio: lungo la colonna del set si alternano pieno e vuoto; senza fine set resta solo l'asse. */
    @Test
    fun `le linee di fine set sono tratteggiate`() {
        val outline = ctx.getColor(R.color.elite_outline)

        fun tratti(b: Bitmap): Int {
            // La vista lascia lo spessore della linea (2dp) a sinistra e a destra.
            val pad = 2 * ctx.resources.displayMetrics.density
            val larghezza = b.width - 2 * pad
            val x = (pad + larghezza * 5.5f / diffs.size).toInt()
            var pieni = 0
            var prima = false
            for (y in 0 until b.height) {
                val ora = b.getPixel(x, y) == outline
                if (ora && !prima) pieni++
                prima = ora
            }
            return pieni
        }
        assertTrue("tratti con il fine set: ${tratti(disegna(vista()))}", tratti(disegna(vista())) >= 5)
        // Falsificazione: senza fine set sulla colonna c'e' al piu' l'incrocio con l'asse.
        assertTrue(tratti(disegna(vista(fineSet = emptyList()))) <= 1)
    }

    /** Le fasce dei nomi crescono con il carattere: al 200% il testo non esce dal grafico. */
    @Test
    fun `la fascia dei nomi cresce col carattere`() {
        val normale = vista().height
        RuntimeEnvironment.setFontScale(2f)
        val grande = vista().height
        assertTrue("normale $normale, al 200% $grande", grande > normale)
    }

    private companion object {
        const val LARGHEZZA = 1000
    }
}
