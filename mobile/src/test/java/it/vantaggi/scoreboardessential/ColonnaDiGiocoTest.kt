package it.vantaggi.scoreboardessential

import android.content.Context
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.card.MaterialCardView
import it.vantaggi.scoreboardessential.core.TeamInk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La colonna di gioco misurata in JVM, con le metriche vere del carattere.
 *
 * **Perche' qui e non solo in androidTest.** `MainActivity` sotto Robolectric non si monta, ma il
 * layout da solo si': gonfiato con il tema dell'app e misurato alla larghezza e all'altezza di un
 * Pixel 9a (411x923dp), con `GraphicsMode.NATIVE` le larghezze dei testi sono quelle reali e non
 * zero. Cosi' la prova gira a ogni build e non aspetta un emulatore.
 *
 * **Cosa protegge.** La barra ha in fila il tempo, il portiere, l'orologio e PARTITA. Con i
 * caratteri ingranditi dall'accessibilita' la riga non basta piu': il tempo e' l'informazione che
 * si legge a bordo campo e non deve perdere una cifra, e nessun comando deve uscire dalla barra.
 * Poi il colore della squadra sulla zona +: glifo TeamInk e stroke quando il colore sul nero e' scuro.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w411dp-h923dp-xxhdpi")
class ColonnaDiGiocoTest {
    private fun gonfia(scalaCaratteri: Float): View {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val configurazione = Configuration(app.resources.configuration).apply { fontScale = scalaCaratteri }
        val contesto = ContextThemeWrapper(app.createConfigurationContext(configurazione), R.style.Theme_ScoreboardEssential)
        return LayoutInflater.from(contesto).inflate(R.layout.content_scoreboard_live, null)
    }

    private fun misura(radice: View) {
        val densita = radice.resources.displayMetrics.density
        radice.measure(
            View.MeasureSpec.makeMeasureSpec((411 * densita).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((923 * densita).toInt(), View.MeasureSpec.EXACTLY),
        )
        radice.layout(0, 0, radice.measuredWidth, radice.measuredHeight)
    }

    /** Il testo e' tutto su una riga, senza puntini, e la riga sta dentro la vista. */
    private fun assertIntero(
        cosa: String,
        vista: TextView,
    ) {
        val layout = vista.layout
        val spazio = vista.width - vista.totalPaddingLeft - vista.totalPaddingRight
        assertEquals("$cosa va a capo: '${vista.text}' in ${vista.width}px", 1, vista.lineCount)
        assertEquals("$cosa perde caratteri: '${vista.text}'", vista.text.length, layout.getLineEnd(0))
        assertEquals("$cosa ha i puntini: '${vista.text}'", 0, layout.getEllipsisCount(0))
        assertTrue(
            "$cosa e' largo ${layout.getLineWidth(0)}px ma ha solo ${spazio}px",
            layout.getLineWidth(0) <= spazio + 1f,
        )
    }

    @Test
    fun nelCalcio_la_barra_tiene_tutto_anche_con_i_caratteri_ingranditi() {
        // 1.3 e' "grande" nelle impostazioni. Tempo a due cifre di minuti e portiere: il caso
        // normale di una partita. Piu' su (2.0, il massimo) la barra non basta e non e' un
        // obiettivo di questo passo: il tempo resta comunque intero, che e' cio' che conta.
        for (scala in listOf(1f, 1.3f)) {
            val radice = gonfia(scala)
            radice.findViewById<TextView>(R.id.timer_start_button).text = "34:12"
            radice.findViewById<TextView>(R.id.keeper_timer_textview).text = "05:00"
            misura(radice)

            val barra = radice.findViewById<ViewGroup>(R.id.game_bar)
            val tempo = radice.findViewById<TextView>(R.id.timer_start_button)
            assertIntero("il tempo a scala $scala", tempo)
            assertIntero("il valore del portiere a scala $scala", radice.findViewById(R.id.keeper_timer_textview))
            for (id in listOf(R.id.timer_start_button, R.id.keeper_slot, R.id.wear_status_icon, R.id.match_sheet_button)) {
                val vista = radice.findViewById<View>(id)
                val nome = radice.resources.getResourceEntryName(id)
                assertTrue("$nome a scala $scala comincia fuori dalla barra: ${vista.left}px", vista.left >= 0)
                assertTrue("$nome a scala $scala finisce a ${vista.right}px, oltre la barra di ${barra.width}px", vista.right <= barra.width)
            }
            val minimo = 48 * radice.resources.displayMetrics.density
            assertTrue("il tempo a scala $scala e' alto ${tempo.height}px, sotto i 48dp", tempo.height >= minimo)
        }
    }

    @Test
    fun laColonna_riempie_lo_schermo_e_le_zone_sono_bersagli_da_48dp() {
        val radice = gonfia(1f)
        misura(radice)
        val densita = radice.resources.displayMetrics.density
        assertEquals("la colonna riempie l'altezza", (923 * densita).toInt(), radice.height)
        for (id in listOf(R.id.team1_add_button_card, R.id.team2_add_button_card)) {
            val zona = radice.findViewById<View>(id)
            assertTrue("la zona e' larga ${zona.width}px, sotto i 48dp", zona.width >= 48 * densita)
            assertEquals("la zona e' alta 112dp", 112 * densita, zona.height.toFloat(), 1f)
        }
    }

    private fun applica(colore: Int): Triple<MaterialCardView, ImageView, View> {
        val radice = gonfia(1f)
        val zona = radice.findViewById<MaterialCardView>(R.id.team2_add_button_card)
        val glifo = radice.findViewById<ImageView>(R.id.team2_plus_icon)
        val barretta = radice.findViewById<View>(R.id.team2_color_bar)
        applicaColoreDiSquadra(zona, glifo, barretta, colore)
        return Triple(zona, glifo, barretta)
    }

    @Test
    fun unColoreScuroSulNero_prende_lo_stroke_e_il_glifo_bianco() {
        // #1A237E sul nero fa 1,59:1: senza contorno la zona quasi non si vede.
        val (zona, glifo, _) = applica(0xFF1A237E.toInt())
        assertTrue("manca lo stroke sulla zona scura", zona.strokeWidth > 0)
        assertEquals("lo stroke e' #E0E0E0", 0xFFE0E0E0.toInt(), zona.strokeColor)
        assertEquals("il glifo e' bianco su un blu notte", TeamInk.BIANCO, glifo.imageTintList?.defaultColor)
        assertEquals("la zona ha il colore vero", 0xFF1A237E.toInt(), zona.cardBackgroundColor.defaultColor)
    }

    @Test
    fun unColoreChiaro_non_ha_stroke_e_il_glifo_nero() {
        val (zona, glifo, barretta) = applica(0xFFFFD600.toInt())
        assertEquals("un giallo sul nero non ha bisogno dello stroke", 0, zona.strokeWidth)
        assertEquals("il glifo e' nero sul giallo", TeamInk.NERO, glifo.imageTintList?.defaultColor)
        assertEquals("la barretta di un colore che regge resta com'e'", 0xFFFFD600.toInt(), (barretta.background as ColorDrawable).color)
    }
}
