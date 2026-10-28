package it.vantaggi.scoreboardessential

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Le intestazioni delle formazioni nel foglio PARTITA a 360dp. Dal giro di prove del 28 ottobre
 * 2026: col testo al 200% le due colonne affiancate erano piu' strette di una parola e
 * «Squadra 1: nessuna formazione» andava a capo dentro «formazione». Dal 150% in su le formazioni
 * vanno una sotto l'altra (disponiLeFormazioni). Rosso se un'intestazione va a capo dentro una
 * parola, o se a testo normale le formazioni non sono piu' affiancate.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w360dp-h800dp-xxhdpi")
class FormazioniATestoGrandeTest {
    private fun gonfia(scala: Float): View {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val configurazione = Configuration(app.resources.configuration).apply { fontScale = scala }
        val contesto = ContextThemeWrapper(app.createConfigurationContext(configurazione), R.style.Theme_ScoreboardEssential)
        val foglio = LayoutInflater.from(contesto).inflate(R.layout.content_scoreboard_details, null)
        foglio.layoutDirection = View.LAYOUT_DIRECTION_LTR
        disponiLeFormazioni(foglio.findViewById(R.id.formations_row), scala)
        listOf(R.id.team1_formation_label to "Squadra 1", R.id.team2_formation_label to "Squadra 2").forEach { (id, squadra) ->
            foglio.findViewById<TextView>(id).text = contesto.getString(R.string.formation_none, squadra)
        }
        val densita = foglio.resources.displayMetrics.density
        foglio.measure(
            View.MeasureSpec.makeMeasureSpec((360 * densita).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        foglio.layout(0, 0, foglio.measuredWidth, foglio.measuredHeight)
        return foglio
    }

    @Test
    fun `le intestazioni delle formazioni vanno a capo solo fra le parole`() {
        for (scala in listOf(1f, 1.3f, 2f)) {
            val foglio = gonfia(scala)
            listOf(R.id.team1_formation_label, R.id.team2_formation_label).forEach { id ->
                val etichetta = foglio.findViewById<TextView>(id)
                val testo = etichetta.text.toString()
                val layout = etichetta.layout
                for (riga in 0 until layout.lineCount - 1) {
                    assertTrue(
                        "a scala $scala '$testo' va a capo dentro una parola",
                        testo[layout.getLineEnd(riga) - 1].isWhitespace(),
                    )
                }
                assertEquals("a scala $scala '$testo' perde caratteri", testo.length, layout.getLineEnd(layout.lineCount - 1))
            }
        }
    }

    @Test
    fun `a testo normale le formazioni restano affiancate, a testo grande una sotto l'altra`() {
        assertEquals(LinearLayout.HORIZONTAL, gonfia(1f).findViewById<LinearLayout>(R.id.formations_row).orientation)
        assertEquals(LinearLayout.HORIZONTAL, gonfia(1.3f).findViewById<LinearLayout>(R.id.formations_row).orientation)
        val grande = gonfia(2f)
        val riga = grande.findViewById<LinearLayout>(R.id.formations_row)
        assertEquals(LinearLayout.VERTICAL, riga.orientation)
        val seconda = riga.getChildAt(1)
        assertEquals("la seconda formazione non e' a tutta larghezza", riga.width, seconda.width)
        assertTrue("la seconda formazione non sta sotto la prima", seconda.top >= riga.getChildAt(0).bottom)
    }
}
