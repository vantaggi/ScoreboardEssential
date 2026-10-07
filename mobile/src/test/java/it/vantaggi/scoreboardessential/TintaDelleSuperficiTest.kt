package it.vantaggi.scoreboardessential

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.shape.MaterialShapeDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.google.android.material.R as M3

/**
 * Difetto visto a schermo (G-3): il dialogo "Termina partita" aveva il fondo tinto di verde oliva
 * invece di `background-elevated` (#1E1E22). E' la tinta di elevazione di Material 3: colorSurfaceTint
 * segue colorPrimary, che nel tema e' il lime, e Material la mescola nelle superfici sollevate.
 *
 * Si misura il colore vero: il fondo del dialogo del builder M3 viene disegnato su una bitmap e si
 * legge un pixel. La falsificazione rimette la tinta (e l'overlay di elevazione) e la misura deve vedere
 * il fondo cambiare, altrimenti il test non distinguerebbe nulla.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w411dp-h923dp-xxhdpi")
class TintaDelleSuperficiTest {
    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private fun contesto() = ContextThemeWrapper(app, R.style.Theme_ScoreboardEssential)

    /** Il colore del centro del fondo del dialogo, cosi' come lo vede chi guarda lo schermo. */
    private fun fondoDelDialogo(contesto: Context): Int {
        val dialogo = MaterialAlertDialogBuilder(contesto).setTitle("Termina partita?").setMessage("Messaggio").create()
        dialogo.show()
        var sfondo: Drawable = dialogo.window!!.decorView.background
        if (sfondo is InsetDrawable) sfondo = sfondo.drawable!!
        val forma = sfondo as MaterialShapeDrawable
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        forma.setBounds(0, 0, 200, 200)
        forma.draw(Canvas(bitmap))
        dialogo.dismiss()
        return bitmap.getPixel(100, 100)
    }

    @Test
    fun `il fondo del dialogo e' background-elevated senza tinta del lime`() {
        val contesto = contesto()

        assertEquals(contesto.getColor(R.color.elite_surface_raised), fondoDelDialogo(contesto))
    }

    @Test
    fun `nessuna superficie sollevata prende la tinta di colorPrimary`() {
        val contesto = contesto()
        val a = contesto.obtainStyledAttributes(intArrayOf(M3.attr.elevationOverlayColor, M3.attr.elevationOverlayEnabled))

        assertEquals("l'overlay di elevazione e' spento", false, a.getBoolean(1, true))
        a.recycle()
    }

    /** Un fondo con la stessa elevazione del dialogo, disegnato come lo disegna Material, letto a pixel. */
    private fun pixelDiUnaSuperficieSollevata(contesto: Context): Pair<Int, Int> {
        val superficie = MaterialColors.getColor(contesto, M3.attr.colorSurface, 0)
        val forma = MaterialShapeDrawable.createWithElevationOverlay(contesto, 24f * contesto.resources.displayMetrics.density)
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        forma.setBounds(0, 0, 200, 200)
        forma.draw(Canvas(bitmap))
        return superficie to bitmap.getPixel(100, 100)
    }

    @Test
    fun `una superficie sollevata del tema del telefono resta del suo tono`() {
        val (superficie, pixel) = pixelDiUnaSuperficieSollevata(contesto())

        assertEquals(superficie, pixel)
    }

    @Test
    fun `falsificazione - il tema stock di Material 3 con l'overlay acceso alza e tinge la superficie`() {
        val stock = ContextThemeWrapper(app, M3.style.Theme_Material3_Dark_NoActionBar)
        val (superficie, pixel) = pixelDiUnaSuperficieSollevata(stock)

        assertNotEquals("la misura non distingue la tinta", superficie, pixel)
    }
}
