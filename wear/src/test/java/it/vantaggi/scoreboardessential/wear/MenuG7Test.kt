package it.vantaggi.scoreboardessential.wear

import android.graphics.RectF
import android.provider.Settings
import android.util.TypedValue
import android.widget.LinearLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.RoundedCornerTreatment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * G-7: il menu partita sul quadrante e' un gruppo tonale con righe a raggio 8, senza ombra, senza
 * forme tagliate, con la pressione condivisa che col movimento ridotto perde la scala.
 */
@RunWith(RobolectricTestRunner::class)
class MenuG7Test {
    private val input =
        InputMenu(
            inCoda = 0,
            collegato = true,
            partitaIniziata = false,
            haElencoSport = true,
            sport = "Padel",
            risultato = "6–4",
        )

    private fun apri(): MenuActivity =
        Robolectric
            .buildActivity(MenuActivity::class.java, MenuActivity.intent(RuntimeEnvironment.getApplication(), input))
            .setup()
            .get()

    private fun dp(
        attivita: MenuActivity,
        valore: Float,
    ) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valore, attivita.resources.displayMetrics)

    @Test
    fun `le voci sono righe di un gruppo tonale con la linea sottile, senza ombra e a raggio 8`() {
        val attivita = apri()
        val gruppo = attivita.findViewById<LinearLayout>(R.id.menu_voci)

        assertNotNull("il gruppo ha il suo fondo", gruppo.background)
        assertNotNull("e la linea sottile fra le righe", gruppo.dividerDrawable)
        assertEquals(LinearLayout.SHOW_DIVIDER_MIDDLE, gruppo.showDividers)
        assertEquals(2, gruppo.childCount)

        (0 until gruppo.childCount).forEach {
            val riga = gruppo.getChildAt(it) as MaterialCardView
            assertEquals("ombra sulla riga $it", 0f, riga.cardElevation, 0f)
            assertEquals(0, riga.strokeWidth)
            assertEquals(attivita.getColor(R.color.elite_surface), riga.cardBackgroundColor.defaultColor)
            val angolo = riga.shapeAppearanceModel.topLeftCorner
            assertTrue("forma tagliata nella riga $it", angolo is RoundedCornerTreatment)
            assertEquals(dp(attivita, 8f), riga.shapeAppearanceModel.topLeftCornerSize.getCornerSize(RectF(0f, 0f, 200f, 52f)), 0.5f)
            assertNotNull("pressione condivisa sulla riga $it", riga.stateListAnimator)
        }
    }

    @Test
    fun `la pressione risolve a quella intera, e col movimento ridotto a quella senza scala`() {
        val app = RuntimeEnvironment.getApplication()
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val normale = apri()
        val valore = TypedValue()
        normale.theme.resolveAttribute(R.attr.pressFeedback, valore, true)
        assertEquals(R.animator.press_feedback, valore.resourceId)

        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val ridotto = apri()
        val valoreRidotto = TypedValue()
        ridotto.theme.resolveAttribute(R.attr.pressFeedback, valoreRidotto, true)
        assertEquals(R.animator.press_feedback_reduced, valoreRidotto.resourceId)
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }
}
