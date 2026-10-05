package it.vantaggi.scoreboardessential

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.google.android.material.R as MaterialR
import it.vantaggi.scoreboardessential.shared.R as SharedR

/**
 * I caratteri del telefono (DESIGN.md, pista Coerenza con Padel Elite, G-1): Inter per il testo,
 * JetBrains Mono con cifre tabulari per i punteggi.
 *
 * Si guarda il carattere che la vista usa DAVVERO: la larghezza di una stringa col pennello della
 * vista contro quella del file in res/font. Serve la grafica nativa (con quella di default
 * measureText restituisce un pixel a carattere e non direbbe niente).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w411dp-h923dp-xxhdpi")
class CaratteriDelTelefonoTest {
    private val app: Context get() = ApplicationProvider.getApplicationContext()

    private fun contesto() = ContextThemeWrapper(app, R.style.Theme_ScoreboardEssential)

    private fun carattere(
        id: Int,
        peso: Int? = null,
    ): Typeface {
        val base = ResourcesCompat.getFont(app, id)
        assertNotNull("il carattere ${app.resources.getResourceEntryName(id)} non si carica", base)
        return if (peso == null) base!! else Typeface.create(base!!, peso, false)
    }

    private fun larghezza(
        carattere: Typeface,
        testo: String,
        spaziatura: Float = 0f,
    ): Float =
        Paint()
            .apply {
                typeface = carattere
                textSize = 100f
                letterSpacing = spaziatura
            }.measureText(testo)

    private fun larghezzaDellaVista(
        vista: TextView,
        testo: String,
    ): Float = Paint(vista.paint).apply { textSize = 100f }.measureText(testo)

    private fun gonfia(layout: Int): View {
        val radice = LayoutInflater.from(contesto()).inflate(layout, null)
        radice.layoutDirection = View.LAYOUT_DIRECTION_LTR
        return radice
    }

    @Test
    fun `i sei file e le due famiglie di res-font si caricano e sono diversi fra loro`() {
        val file =
            listOf(
                SharedR.font.inter_regular,
                SharedR.font.inter_medium,
                SharedR.font.inter_bold,
                SharedR.font.inter_black,
                SharedR.font.jetbrains_mono_bold,
                SharedR.font.jetbrains_mono_extrabold,
            )
        file.forEach { carattere(it) }
        carattere(SharedR.font.inter)
        carattere(SharedR.font.jetbrains_mono)
        // Inter e' proporzionale, JetBrains Mono no: "iiii" e "MMMM" misurano uguale solo nel secondo.
        assertNotEquals(
            larghezza(carattere(SharedR.font.inter_regular), "iiii"),
            larghezza(carattere(SharedR.font.inter_regular), "MMMM"),
            1f,
        )
        val mono = carattere(SharedR.font.jetbrains_mono_extrabold)
        assertEquals(larghezza(mono, "iiii"), larghezza(mono, "MMMM"), 0.01f)
        // Il peso cambia il disegno: il nero di Inter e' piu' largo del regolare.
        assertTrue(larghezza(carattere(SharedR.font.inter_black), "Padel") > larghezza(carattere(SharedR.font.inter_regular), "Padel"))
    }

    @Test
    fun `tutta la scala di Material 3 del tema ha Inter come carattere`() {
        val contesto = contesto()
        val ruoli: List<Int> =
            listOf(
                MaterialR.attr.textAppearanceDisplayLarge,
                MaterialR.attr.textAppearanceDisplayMedium,
                MaterialR.attr.textAppearanceDisplaySmall,
                MaterialR.attr.textAppearanceHeadlineLarge,
                MaterialR.attr.textAppearanceHeadlineMedium,
                MaterialR.attr.textAppearanceHeadlineSmall,
                MaterialR.attr.textAppearanceTitleLarge,
                MaterialR.attr.textAppearanceTitleMedium,
                MaterialR.attr.textAppearanceTitleSmall,
                MaterialR.attr.textAppearanceBodyLarge,
                MaterialR.attr.textAppearanceBodyMedium,
                MaterialR.attr.textAppearanceBodySmall,
                MaterialR.attr.textAppearanceLabelLarge,
                MaterialR.attr.textAppearanceLabelMedium,
                MaterialR.attr.textAppearanceLabelSmall,
            )
        ruoli.forEach { attributo -> assertEquals("ruolo ${nome(attributo)}", SharedR.font.inter, fontDelRuolo(contesto, attributo)) }
    }

    private fun nome(
        @AttrRes attributo: Int,
    ) = app.resources.getResourceEntryName(attributo)

    private fun fontDelRuolo(
        contesto: Context,
        @AttrRes attributo: Int,
    ): Int {
        val valore = TypedValue()
        assertTrue("ruolo ${nome(attributo)} non risolto dal tema", contesto.theme.resolveAttribute(attributo, valore, true))
        val stile = contesto.obtainStyledAttributes(valore.resourceId, intArrayOf(android.R.attr.fontFamily))
        try {
            return stile.getResourceId(0, 0)
        } finally {
            stile.recycle()
        }
    }

    @Test
    fun `una TextView del contorno e' in Inter, non nel condensato di sistema`() {
        val riga = gonfia(R.layout.match_item)
        val data = riga.findViewById<TextView>(R.id.timestamp_textview)
        // Nel layout e' bold: Inter 700.
        val inter700 = carattere(SharedR.font.inter, 700)
        // La vista ha la sua spaziatura (0,12 em): il confronto la porta anche sul file.
        assertEquals(larghezza(inter700, "PADEL 12/09", data.letterSpacing), larghezzaDellaVista(data, "PADEL 12/09"), 0.5f)
        val condensato = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        assertNotEquals(larghezza(condensato, "PADEL 12/09", data.letterSpacing), larghezzaDellaVista(data, "PADEL 12/09"), 1f)

        // Il nome della squadra, maiuscolo e bold, e il testo dei giocatori: Inter 700 e Inter 400.
        val giocatori = riga.findViewById<TextView>(R.id.players_textview)
        assertEquals(
            larghezza(carattere(SharedR.font.inter, 400), "Marco, Luca", giocatori.letterSpacing),
            larghezzaDellaVista(giocatori, "Marco, Luca"),
            0.5f,
        )
    }

    @Test
    fun `un punteggio dello storico e' in JetBrains Mono 800 con numeri tabulari`() {
        val riga = gonfia(R.layout.match_item)
        listOf(R.id.team1_score_textview, R.id.team2_score_textview).forEach { id ->
            val punti = riga.findViewById<TextView>(id)
            assertEquals("tnum", punti.fontFeatureSettings)
            assertEquals(800, punti.paint.typeface.weight)
            val mono800 = carattere(SharedR.font.jetbrains_mono, 800)
            assertEquals(larghezza(mono800, "10", punti.letterSpacing), larghezzaDellaVista(punti, "10"), 0.5f)
            // Tabulari: "11" e "88" occupano lo stesso spazio, le colonne di punteggi stanno in fila.
            assertEquals(larghezzaDellaVista(punti, "11"), larghezzaDellaVista(punti, "88"), 0.01f)
        }
        // Il risultato dei set e' un punteggio anche lui, in 700.
        val set = riga.findViewById<TextView>(R.id.sets_textview)
        assertEquals("tnum", set.fontFeatureSettings)
        assertEquals(700, set.paint.typeface.weight)
        assertEquals(
            larghezzaDellaVista(set, "6-4"),
            larghezza(carattere(SharedR.font.jetbrains_mono, 700), "6-4", set.letterSpacing),
            0.5f,
        )
    }

    @Test
    fun `il minuto del registro e le cifre delle statistiche sono in JetBrains Mono`() {
        val minuto = gonfia(R.layout.match_event_item).findViewById<TextView>(R.id.event_timestamp)
        assertEquals("tnum", minuto.fontFeatureSettings)
        assertEquals(700, minuto.paint.typeface.weight)
        assertEquals(larghezzaDellaVista(minuto, "11'"), larghezzaDellaVista(minuto, "88'"), 0.01f)

        val statistica = gonfia(R.layout.item_player_stat)
        listOf(R.id.text_rank, R.id.text_goals).forEach { id ->
            val cifra = statistica.findViewById<TextView>(id)
            assertEquals("tnum", cifra.fontFeatureSettings)
            assertEquals(800, cifra.paint.typeface.weight)
            assertEquals(larghezzaDellaVista(cifra, "11"), larghezzaDellaVista(cifra, "88"), 0.01f)
        }
    }

    // --- le cifre giganti della schermata di gioco ---

    private fun misuraLaColonna(larghezzaDp: Int): View {
        val radice = gonfia(R.layout.content_scoreboard_live)
        val densita = radice.resources.displayMetrics.density
        radice.measure(
            View.MeasureSpec.makeMeasureSpec((larghezzaDp * densita).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((923 * densita).toInt(), View.MeasureSpec.EXACTLY),
        )
        radice.layout(0, 0, radice.measuredWidth, radice.measuredHeight)
        return radice
    }

    /** La stessa regola di MainActivity.dimensioneDelNumero, applicata al pennello vero della vista. */
    private fun dimensioneDelNumero(
        radice: View,
        token: String,
    ): Float {
        val risorse = radice.resources
        val punti = radice.findViewById<TextView>(R.id.team1_score_textview)
        val riga = radice.findViewById<View>(R.id.score_row)
        val prova = Paint(punti.paint).apply { textSize = 100f }
        val metriche = prova.fontMetrics
        val disponibile = riga.width / 2f - 16 * risorse.displayMetrics.density
        val perLarghezza = 100f * disponibile / prova.measureText(token)
        val perAltezza = 100f * riga.height / (metriche.descent - metriche.ascent)
        val massimo = risorse.getDimension(R.dimen.score_text_max)
        val minimo = risorse.getDimension(R.dimen.score_text_min)
        return minOf(massimo, perLarghezza, perAltezza).coerceAtLeast(minimo)
    }

    /**
     * La misura che decide se le cifre giganti passano a JetBrains Mono (G-1). In JetBrains Mono ogni
     * carattere e' largo 0,6 em: "88" e "AV" misurano 1,2 em e a 150dp (180dp) NON entrano nei 173,7dp
     * della mezza colonna del Pixel 9a. Non e' un ostacolo: la misura delle cifre non usa un corpo
     * fisso, prende il piu' piccolo fra il tetto e cio' che entra, e qui entra a 144,6dp (il 96% del
     * tetto). Le cifre di JetBrains Mono sono piu' alte di quelle del condensato (0,73 em contro 0,71),
     * quindi a 144,6dp si leggono alte quanto prima a 150dp.
     */
    @Test
    fun `le cifre giganti del gioco sono in JetBrains Mono 800 con tnum e 88 e AV entrano nella mezza colonna del Pixel 9a`() {
        val radice = misuraLaColonna(411)
        val densita = radice.resources.displayMetrics.density
        listOf(R.id.team1_score_textview, R.id.team2_score_textview).forEach { id ->
            val cifre = radice.findViewById<TextView>(id)
            assertEquals("tnum", cifre.fontFeatureSettings)
            assertEquals(800, cifre.paint.typeface.weight)
        }
        val mezzaColonna = radice.findViewById<View>(R.id.score_row).width / 2f - 16 * densita
        val punti = radice.findViewById<TextView>(R.id.team1_score_textview)
        // Tutti i token a due caratteri misurano uguale (mono): la prova vale per 88, AV, 40, 15, PV.
        listOf("88", "AV").forEach { token ->
            val dimensione = dimensioneDelNumero(radice, token)
            val larghezzaDelToken = Paint(punti.paint).apply { textSize = dimensione }.measureText(token)
            assertTrue(
                "\"$token\" e' largo ${larghezzaDelToken}px, la mezza colonna ne da $mezzaColonna",
                larghezzaDelToken <= mezzaColonna + 0.5f,
            )
            assertTrue("\"$token\" scende a ${dimensione / densita}dp: piu' del 5% sotto il tetto", dimensione >= 142 * densita)
        }
    }

    @Test
    fun `sul telefono stretto da 360dp le cifre giganti scendono ma restano sopra il minimo di 72dp`() {
        val radice = misuraLaColonna(360)
        val densita = radice.resources.displayMetrics.density
        val minimo = radice.resources.getDimension(R.dimen.score_text_min)
        listOf("88", "AV").forEach { token ->
            val dimensione = dimensioneDelNumero(radice, token)
            assertTrue("\"$token\" a 360dp scende a ${dimensione / densita}dp, sotto il minimo", dimensione >= minimo)
        }
    }
}
