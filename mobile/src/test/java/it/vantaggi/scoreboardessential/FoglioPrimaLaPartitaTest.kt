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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Il foglio PARTITA dopo la revisione dei menu (passo 3a): in fondo prima i comandi di questa
 * partita, con Fine partita unico primario, poi i posti dell'app come righe di un gruppo con la
 * freccia. Rosso se torna la griglia di sei bottoni uguali (Storico, Statistiche, Giocatori o
 * Impostazioni di nuovo bottoni) o se i posti dell'app risalgono sopra Fine partita.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "it-w411dp-h923dp-xxhdpi")
class FoglioPrimaLaPartitaTest {
    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private val posti =
        listOf(
            R.id.match_history_button to "Storico",
            R.id.statistics_button to "Statistiche",
            R.id.players_button to "Giocatori",
            R.id.sheet_settings_button to "Impostazioni",
        )

    private fun gonfiaIlFoglio(): View {
        val contesto = ContextThemeWrapper(app, R.style.Theme_ScoreboardEssential)
        return LayoutInflater.from(contesto).inflate(R.layout.content_scoreboard_details, null)
    }

    private fun testi(vista: View): List<String> =
        when (vista) {
            is TextView -> listOf(vista.text.toString())
            is ViewGroup -> (0 until vista.childCount).flatMap { testi(vista.getChildAt(it)) }
            else -> emptyList()
        }

    @Test
    fun `i posti dell'app sono righe del gruppo App, non bottoni`() {
        val foglio = gonfiaIlFoglio()
        val gruppo = foglio.findViewById<MaterialCardView>(R.id.app_places_card)

        posti.forEach { (id, nome) ->
            val riga = foglio.findViewById<View>(id)
            assertFalse("$nome e' ancora un bottone", riga is MaterialButton)
            assertTrue("$nome non si tocca", riga.isClickable && riga.isFocusable)
            var genitore = riga.parent
            while (genitore != null && genitore !== gruppo) genitore = genitore.parent
            assertTrue("$nome non sta nel gruppo App", genitore === gruppo)
            assertEquals(listOf(nome), testi(riga))
        }
    }

    @Test
    fun `fine partita e' l'unico primario e sta sopra i posti dell'app`() {
        val foglio = gonfiaIlFoglio()
        val azioni = foglio.findViewById<ViewGroup>(R.id.match_actions)
        val ordine = (0 until azioni.childCount).map { azioni.getChildAt(it).id }

        val fine = ordine.indexOf(R.id.reset_scores_button)
        val gruppo = ordine.indexOf(R.id.app_places_card)
        assertTrue("Fine partita non e' fra le azioni", fine >= 0)
        assertTrue("i posti dell'app devono stare sotto Fine partita", gruppo > fine)
        // Nelle azioni restano solo due secondari, quelli di questa partita.
        val secondari = mutableListOf<Int>()

        fun cerca(v: View) {
            if (v is MaterialButton && v.id != R.id.reset_scores_button) secondari += v.id
            if (v is ViewGroup) (0 until v.childCount).forEach { cerca(v.getChildAt(it)) }
        }

        cerca(azioni)
        assertEquals(listOf(R.id.share_match_button, R.id.sheet_reset_timer_button), secondari)
    }
}
