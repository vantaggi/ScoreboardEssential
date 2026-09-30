package it.vantaggi.scoreboardessential

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import it.vantaggi.scoreboardessential.core.TeamInk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val radice = LayoutInflater.from(contesto).inflate(R.layout.content_scoreboard_live, null)
        // Senza finestra la direzione non e' risolta e le icone dei pulsanti (compound drawable
        // relativi) non contano ne' nella larghezza ne' nel padding: il tempo con la sua icona
        // sembrava piu' stretto di 26dp e ANNULLA piu' largo di quanto e' davvero.
        radice.layoutDirection = View.LAYOUT_DIRECTION_LTR
        return radice
    }

    private fun misura(
        radice: View,
        larghezzaDp: Int = 411,
    ) {
        val densita = radice.resources.displayMetrics.density
        radice.measure(
            View.MeasureSpec.makeMeasureSpec((larghezzaDp * densita).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((923 * densita).toInt(), View.MeasureSpec.EXACTLY),
        )
        radice.layout(0, 0, radice.measuredWidth, radice.measuredHeight)
    }

    /**
     * La larghezza che resta al testo: la vista meno i padding e, in un MaterialButton con l'icona,
     * meno l'icona e la sua distanza dal testo. Si conta a mano perche' non si deve dipendere da
     * come il pulsante distribuisce l'icona: e' il conto che decide se "ANNULLA" entra.
     */
    private fun spazioPerIlTesto(vista: TextView): Int {
        val dopoIPadding = vista.width - vista.totalPaddingLeft - vista.totalPaddingRight
        if (vista !is MaterialButton || vista.icon == null) return dopoIPadding
        val conIcona = vista.width - vista.paddingLeft - vista.paddingRight - vista.iconSize - vista.iconPadding
        return minOf(dopoIPadding, conIcona)
    }

    /** Il testo e' tutto su una riga, senza puntini, e la riga (larga e alta) sta dentro la vista. */
    private fun assertIntero(
        cosa: String,
        vista: TextView,
    ) {
        val layout = vista.layout
        val spazio = spazioPerIlTesto(vista)
        assertEquals("$cosa va a capo: '${vista.text}' in ${vista.width}px", 1, vista.lineCount)
        assertEquals("$cosa perde caratteri: '${vista.text}'", vista.text.length, layout.getLineEnd(0))
        assertEquals("$cosa ha i puntini: '${vista.text}'", 0, layout.getEllipsisCount(0))
        assertTrue(
            "$cosa e' largo ${layout.getLineWidth(0)}px ma ha solo ${spazio}px",
            layout.getLineWidth(0) <= spazio + 1f,
        )
        // L'altezza si misura sull'inchiostro dei glifi e non sulla riga: la riga di un 32sp
        // ha un'aria sopra e sotto che il pulsante da 48dp puo' tagliare senza toccare le cifre.
        val testo = layout.text.toString()
        val inchiostro = Rect().also { vista.paint.getTextBounds(testo, 0, testo.length, it) }
        val altezzaUtile = vista.height - vista.totalPaddingTop - vista.totalPaddingBottom
        assertTrue(
            "$cosa e' alto ${inchiostro.height()}px ma ha solo ${altezzaUtile}px",
            inchiostro.height() <= altezzaUtile,
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
                val fine = vista.right
                assertTrue("$nome a scala $scala finisce a ${fine}px, oltre la barra di ${barra.width}px", fine <= barra.width)
            }
            val minimo = 48 * radice.resources.displayMetrics.density
            assertTrue("il tempo a scala $scala e' alto ${tempo.height}px, sotto i 48dp", tempo.height >= minimo)
        }
    }

    /**
     * La barra del calcio a 360dp, il telefono stretto piu' diffuso: 328dp utili. Il tempo e il
     * portiere sono fissi, la molla e' il testo del periodo, ed e' l'unico che puo' restringersi:
     * il pulsante ≡ e' l'unico modo di aprire il foglio e non deve mai uscire dal bordo. Il tempo
     * e' quello piu' largo, 100:00, perche' oltre i 99 minuti il pulsante prende una cifra in piu'.
     */
    @Test
    fun nelCalcio_a_360dp_il_pulsante_del_foglio_resta_intero_e_dentro_lo_schermo() {
        for (scala in listOf(1f, 1.3f)) {
            val radice = gonfia(scala)
            radice.findViewById<TextView>(R.id.timer_start_button).text = "100:00"
            radice.findViewById<TextView>(R.id.keeper_timer_textview).text = "05:00"
            // Il caso peggiore per la molla: il periodo ha un testo lungo da contendere.
            radice.findViewById<TextView>(R.id.match_period_textview).text = "SECONDO TEMPO"
            misura(radice, larghezzaDp = 360)

            val densita = radice.resources.displayMetrics.density
            val barra = radice.findViewById<ViewGroup>(R.id.game_bar)
            val foglio = radice.findViewById<TextView>(R.id.match_sheet_button)
            assertIntero("il pulsante del foglio a 360dp e scala $scala", foglio)
            assertIntero("il tempo a 360dp e scala $scala", radice.findViewById(R.id.timer_start_button))
            assertIntero("il valore del portiere a 360dp e scala $scala", radice.findViewById(R.id.keeper_timer_textview))
            assertTrue("il pulsante del foglio a scala $scala e' largo ${foglio.width}px, sotto i 48dp", foglio.width >= 48 * densita)
            // Ridotto al glifo, a voce dice comunque cosa apre: e' il suo unico nome.
            assertEquals(
                "il pulsante del foglio ridotto al glifo non ha la descrizione",
                radice.context.getString(R.string.cd_open_match_sheet),
                foglio.contentDescription?.toString(),
            )
            assertTrue("il pulsante del foglio a scala $scala e' alto ${foglio.height}px, sotto i 48dp", foglio.height >= 48 * densita)
            val comandi = listOf(R.id.timer_start_button, R.id.keeper_slot, R.id.wear_status_icon, R.id.match_sheet_button)
            for (id in comandi) {
                val vista = radice.findViewById<View>(id)
                val nome = radice.resources.getResourceEntryName(id)
                assertTrue(
                    "$nome a 360dp e scala $scala finisce a ${vista.right}px, oltre la barra di ${barra.width}px",
                    vista.right <= barra.width,
                )
            }
            // In fila e senza sovrapporsi: se manca lo spazio a cedere e' il testo del periodo.
            comandi.zipWithNext().forEach { (prima, dopo) ->
                val a = radice.findViewById<View>(prima)
                val b = radice.findViewById<View>(dopo)
                assertTrue(
                    "${radice.resources.getResourceEntryName(prima)} (fino a ${a.right}px) tocca ${radice.resources.getResourceEntryName(dopo)} (da ${b.left}px) a scala $scala",
                    a.right <= b.left,
                )
            }
            assertTrue("la barra a scala $scala esce dallo schermo: ${barra.right}px", barra.right <= radice.width)
        }
    }

    /**
     * Il tutorial indicava il pulsante con la sua parola ("≡ PARTITA"): ridotto al glifo, la parola
     * non c'e' piu' e il testo non deve mandare a cercarla.
     */
    @Test
    fun ilTutorial_non_nomina_la_parola_che_il_pulsante_non_ha_piu() {
        val radice = gonfia(1f)
        val contesto = radice.context
        val glifo = radice.findViewById<TextView>(R.id.match_sheet_button).text.toString()
        val parola = contesto.getString(R.string.label_match_sheet)
        assertFalse("il pulsante non e' piu' solo il glifo: '$glifo'", glifo.contains(parola))
        for (id in listOf(R.string.onboarding_score_description, R.string.onboarding_timers_description)) {
            val testo = contesto.getString(id)
            assertFalse("il tutorial nomina ancora '$glifo $parola': $testo", testo.contains("$glifo $parola"))
        }
    }

    /**
     * Il pulsante del tempo ha la larghezza che gli serve per 100:00 anche quando mostra 34:12:
     * passare i 99 minuti non deve allargarlo e spostare il portiere. La misura e' a 360dp, il
     * caso in cui ogni dp e' contato, e il tempo e' quello nuovo sulla stessa vista.
     */
    @Test
    fun ilPulsanteDelTempo_non_cambia_larghezza_fra_34_12_e_100_00() {
        for (scala in listOf(1f, 1.3f)) {
            val radice = gonfia(scala)
            val tempo = radice.findViewById<MaterialButton>(R.id.timer_start_button)
            tempo.minWidth = larghezzaMinimaDelTempo(tempo)
            tempo.text = "100:00"
            misura(radice, larghezzaDp = 360)
            val conCentoMinuti = tempo.width
            tempo.text = "34:12"
            misura(radice, larghezzaDp = 360)
            assertEquals("il tempo a scala $scala cambia larghezza passando da 100:00 a 34:12", conCentoMinuti, tempo.width)
            assertEquals("il tempo a scala $scala ha i puntini", 0, tempo.layout.getEllipsisCount(0))
        }
    }

    @Test
    fun la_didascalia_del_dettaglio_a_partita_finita_e_SET() {
        assertEquals(R.string.caption_set_game, didascaliaDelDettaglio("6-4 · 3-2", partitaFinita = false))
        assertEquals(R.string.caption_game, didascaliaDelDettaglio("3-2", partitaFinita = false))
        // A partita finita c'e' solo la sequenza dei set chiusi, senza separatore.
        assertEquals(R.string.caption_set, didascaliaDelDettaglio("6-4 6-3", partitaFinita = true))
        assertEquals(null, didascaliaDelDettaglio("", partitaFinita = true))
        assertEquals(null, didascaliaDelDettaglio(null, partitaFinita = false))
    }

    @Test
    fun nelCalcio_ANNULLA_entra_intero_nel_suo_pulsante_in_italiano() {
        for (scala in listOf(1f, 1.3f)) {
            val radice = gonfia(scala)
            misura(radice)
            val densita = radice.resources.displayMetrics.density
            val annulla = radice.findViewById<TextView>(R.id.undo_goal_button)
            assertEquals("il testo del pulsante non e' quello atteso", "Annulla", annulla.text.toString())
            assertIntero("ANNULLA a scala $scala", annulla)
            assertEquals("la striscia resta alta 56dp", 56 * densita, radice.findViewById<View>(R.id.last_action_strip).height.toFloat(), 1f)
            assertTrue("ANNULLA a scala $scala e' largo ${annulla.width}px, sotto i 96dp", annulla.width >= 96 * densita - 1f)
            val striscia = radice.findViewById<ViewGroup>(R.id.last_action_strip)
            assertTrue("ANNULLA a scala $scala esce dalla striscia", annulla.right <= striscia.width)
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
        val coloreBarretta = (barretta.background as ColorDrawable).color
        assertEquals("la barretta di un colore che regge resta com'e'", 0xFFFFD600.toInt(), coloreBarretta)
    }
}
