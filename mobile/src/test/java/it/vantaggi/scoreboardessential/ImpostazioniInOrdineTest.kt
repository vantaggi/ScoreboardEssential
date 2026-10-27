package it.vantaggi.scoreboardessential

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.RelativeCornerSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Le Impostazioni dopo la revisione dei menu (passo 3b): quattro gruppi nell'ordine in cui si usano,
 * Squadre, Regole, Padel Elite, App, e la lingua nel gruppo App accanto all'alto contrasto invece che
 * in mezzo alle regole della partita. Il colore della squadra e' un cerchio pieno senza icona.
 * Rosso se la lingua torna nel gruppo delle regole, se i gruppi cambiano ordine o nome, o se il
 * colore torna un quadrato con la tavolozza, o se Padel Elite torna un gruppo di bottoni.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "it-w411dp-h923dp-xxhdpi")
class ImpostazioniInOrdineTest {
    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private fun gonfiaLeImpostazioni(): View {
        val contesto = ContextThemeWrapper(app, R.style.Theme_ScoreboardEssential)
        return LayoutInflater.from(contesto).inflate(R.layout.activity_match_settings, null)
    }

    private fun titoli(vista: View): List<String> =
        when {
            vista is TextView && vista.isAccessibilityHeading -> listOf(vista.text.toString())
            vista is ViewGroup -> (0 until vista.childCount).flatMap { titoli(vista.getChildAt(it)) }
            else -> emptyList()
        }

    private fun gruppoDi(vista: View): MaterialCardView? {
        var genitore = vista.parent
        while (genitore != null && genitore !is MaterialCardView) genitore = genitore.parent
        return genitore as MaterialCardView?
    }

    @Test
    fun `i gruppi sono Squadre, Regole, Padel Elite e App, in quest'ordine`() {
        assertEquals(listOf("Squadre", "Regole", "Padel Elite", "App"), titoli(gonfiaLeImpostazioni()))
    }

    @Test
    fun `la lingua sta nel gruppo App con l'alto contrasto, non con le regole`() {
        val impostazioni = gonfiaLeImpostazioni()
        val lingua = impostazioni.findViewById<View>(R.id.languageAutoComplete)
        val contrasto = impostazioni.findViewById<View>(R.id.highContrastSwitch)
        val sport = impostazioni.findViewById<View>(R.id.sportRulesText)

        assertSame(gruppoDi(contrasto), gruppoDi(lingua))
        assertTrue("la lingua e' ancora fra le regole", gruppoDi(sport) !== gruppoDi(lingua))
    }

    @Test
    fun `il colore della squadra e' un cerchio senza icona`() {
        val impostazioni = gonfiaLeImpostazioni()
        listOf(R.id.team1ColorButton, R.id.team2ColorButton).forEach { id ->
            val colore = impostazioni.findViewById<MaterialButton>(id)
            assertNull("il colore ha ancora l'icona della tavolozza", colore.icon)
            val angolo = colore.shapeAppearanceModel.topLeftCornerSize
            assertTrue("il colore non e' un cerchio: $angolo", angolo is RelativeCornerSize)
            assertEquals(0.5f, (angolo as RelativeCornerSize).relativePercent, 0.001f)
        }
    }

    /** Passo 4: Accedi, Gruppo ed Esci erano tre bottoni qui; ora la riga porta alla schermata di Padel Elite. */
    @Test
    fun `Padel Elite e' una riga che porta alla sua schermata, senza bottoni`() {
        val impostazioni = gonfiaLeImpostazioni()
        val gruppo = impostazioni.findViewById<ViewGroup>(R.id.padelEliteCard)
        val riga = impostazioni.findViewById<View>(R.id.padelEliteRow)

        assertTrue("la riga di Padel Elite non si tocca", riga.isClickable && riga.isFocusable)
        assertSame(gruppo, gruppoDi(riga))
        assertEquals(emptyList<View>(), bottoniIn(gruppo))
    }

    private fun bottoniIn(vista: View): List<View> =
        when (vista) {
            is MaterialButton -> listOf(vista)
            is ViewGroup -> (0 until vista.childCount).flatMap { bottoniIn(vista.getChildAt(it)) }
            else -> emptyList()
        }
}
