package it.vantaggi.scoreboardessential

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
 * L'intestazione del tabellone misurata in JVM, con le metriche vere del carattere.
 *
 * **Perche' qui e non solo in androidTest.** `MainActivity` sotto Robolectric non si monta, ma il
 * layout da solo si': gonfiato con il tema dell'app e misurato alla larghezza di un Pixel 9a
 * (411dp), con `GraphicsMode.NATIVE` le larghezze dei testi sono quelle reali e non zero. Cosi' la
 * prova gira a ogni build e non aspetta un emulatore.
 *
 * **Cosa protegge.** Tempo e START/RESET stanno sulla stessa riga per risparmiare altezza. Con i
 * caratteri ingranditi dall'accessibilita', un tempo a tre cifre e i testi italiani, la riga non
 * basta piu': prima il tempo, compresso dai pulsanti, andava a capo dentro una riga sola e si
 * vedeva tagliato a meta'. Il tempo e' l'informazione che si legge a bordo campo: non deve mai
 * perdere una cifra, e i pulsanti non devono perdere una lettera.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w411dp-h923dp-xxhdpi")
class IntestazioneCronometroTest {
    private fun gonfia(scalaCaratteri: Float): View {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val configurazione = Configuration(app.resources.configuration).apply { fontScale = scalaCaratteri }
        val contesto = ContextThemeWrapper(app.createConfigurationContext(configurazione), R.style.Theme_ScoreboardEssential)
        return LayoutInflater.from(contesto).inflate(R.layout.content_scoreboard_live, null)
    }

    private fun misura(radice: View) {
        val larghezza = (411 * radice.resources.displayMetrics.density).toInt()
        radice.measure(
            View.MeasureSpec.makeMeasureSpec(larghezza, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
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

    /** La vista cade tutta dentro la card, non spinta oltre il bordo. */
    private fun assertDentroLaCard(
        cosa: String,
        vista: View,
        card: ViewGroup,
    ) {
        val rettangolo = Rect(0, 0, vista.width, vista.height)
        card.offsetDescendantRectToMyCoords(vista, rettangolo)
        assertTrue("$cosa esce dalla card: $rettangolo su ${card.width}px", rettangolo.left >= 0 && rettangolo.right <= card.width)
    }

    @Test
    fun conCaratteriIngranditi_tempo_e_comandi_restano_interi() {
        // 1.3 e' "grande" nelle impostazioni, 2.0 il massimo. Tempo a tre cifre e PAUSA, il testo
        // piu' lungo che START assume: il caso peggiore per larghezza.
        for (scala in listOf(1f, 1.3f, 1.5f, 2f)) {
            val radice = gonfia(scala)
            radice.findViewById<TextView>(R.id.timer_textview).text = "100:00"
            radice.findViewById<TextView>(R.id.timer_start_button).setText(R.string.pause_caps)
            misura(radice)

            val card = radice.findViewById<ViewGroup>(R.id.timer_card)
            val tempo = radice.findViewById<TextView>(R.id.timer_textview)
            val start = radice.findViewById<TextView>(R.id.timer_start_button)
            val reset = radice.findViewById<TextView>(R.id.reset_timer_button)
            assertIntero("il tempo a scala $scala", tempo)
            assertIntero("START a scala $scala", start)
            assertIntero("RESET a scala $scala", reset)
            assertDentroLaCard("il tempo a scala $scala", tempo, card)
            assertDentroLaCard("START a scala $scala", start, card)
            assertDentroLaCard("RESET a scala $scala", reset, card)

            val minimo = 48 * radice.resources.displayMetrics.density
            assertTrue("START a scala $scala e' alto ${start.height}px, sotto i 48dp", start.height >= minimo)
        }
    }

    @Test
    fun senzaCronometro_la_riga_del_tempo_non_occupa_spazio() {
        // Padel e tennis: applyCapabilities spegne i figli della riga del tempo, non la riga.
        // Qualunque contenitore la regga deve allora misurare zero, altrimenti torna il vuoto.
        val radice = gonfia(1f)
        radice.findViewById<View>(R.id.match_time_label).visibility = View.GONE
        radice.findViewById<View>(R.id.timer_textview).visibility = View.GONE
        radice.findViewById<View>(R.id.timer_controls_row).visibility = View.GONE
        radice.findViewById<View>(R.id.keeper_timer_label).visibility = View.GONE
        radice.findViewById<View>(R.id.keeper_timer_textview).visibility = View.GONE
        misura(radice)

        // 80 e non 72: la card ha gli angoli tagliati e MaterialCardView aggiunge 6dp sopra e sotto
        // per non farci finire il contenuto (misurato: 76dp). Il layout di prima ne misurava 100.
        val altezza = radice.findViewById<View>(R.id.timer_card).height / radice.resources.displayMetrics.density
        assertTrue("l'intestazione senza cronometro e' alta ${altezza}dp, oltre gli 80 ammessi", altezza <= 80f)
    }

    @Test
    fun normalmente_tempo_e_comandi_stanno_sulla_stessa_riga() {
        // L'altra faccia della prova sui caratteri ingranditi: andare a capo e' il ripiego, non la
        // regola. Con caratteri normali, AVVIA e 00:00 i pulsanti stanno accanto al tempo.
        val radice = gonfia(1f)
        radice.findViewById<View>(R.id.keeper_timer_label).visibility = View.GONE
        radice.findViewById<View>(R.id.keeper_timer_textview).visibility = View.GONE
        misura(radice)

        val card = radice.findViewById<ViewGroup>(R.id.timer_card)
        val tempo = Rect().also { radice.findViewById<View>(R.id.timer_textview).getDrawingRect(it) }
        card.offsetDescendantRectToMyCoords(radice.findViewById(R.id.timer_textview), tempo)
        val start = Rect().also { radice.findViewById<View>(R.id.timer_start_button).getDrawingRect(it) }
        card.offsetDescendantRectToMyCoords(radice.findViewById(R.id.timer_start_button), start)
        assertTrue("START ($start) non sta sulla riga del tempo ($tempo)", start.centerY() in tempo.top..tempo.bottom)
    }
}
