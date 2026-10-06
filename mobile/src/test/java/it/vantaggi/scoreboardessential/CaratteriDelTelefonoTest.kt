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
 * I caratteri del telefono (DESIGN.md, Adattamento alla UI Constitution, G-1 e G-1b): Inter per il
 * testo e anche per i punteggi, questi con la feature tnum (cifre tabulari); tre pesi, 400, 500, 600.
 * JetBrains Mono e' uscito dall'app.
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
    fun `i tre file di Inter si caricano, hanno pesi diversi e JetBrains Mono non c'e' piu'`() {
        listOf(SharedR.font.inter_regular, SharedR.font.inter_medium, SharedR.font.inter_semibold).forEach { carattere(it) }
        carattere(SharedR.font.inter)
        // Inter e' proporzionale: "iiii" e "MMMM" non misurano uguale (in un monospazio si').
        assertNotEquals(
            larghezza(carattere(SharedR.font.inter_regular), "iiii"),
            larghezza(carattere(SharedR.font.inter_regular), "MMMM"),
            1f,
        )
        // Il peso cambia il disegno: ogni peso e' piu' largo del precedente.
        val regolare = larghezza(carattere(SharedR.font.inter_regular), "Padel")
        val medio = larghezza(carattere(SharedR.font.inter_medium), "Padel")
        val semibold = larghezza(carattere(SharedR.font.inter_semibold), "Padel")
        assertTrue("400 $regolare, 500 $medio", medio > regolare)
        assertTrue("500 $medio, 600 $semibold", semibold > medio)
        // Falsificazione della decisione "JetBrains Mono esce dall'app": ne' i file ne' la famiglia esistono piu'.
        listOf("jetbrains_mono", "jetbrains_mono_bold", "jetbrains_mono_extrabold", "inter_bold", "inter_black").forEach { nome ->
            assertEquals("il carattere $nome non doveva restare in res/font", 0, app.resources.getIdentifier(nome, "font", app.packageName))
        }
    }

    @Test
    fun `chi chiede il grassetto cade sul 600 senza grassetto finto`() {
        // Con tre pesi, textStyle bold (700) sceglie il 600, il piu' vicino: stessa larghezza del file semibold.
        val semibold = larghezza(carattere(SharedR.font.inter_semibold), "Padel 12/09")
        assertEquals(semibold, larghezza(carattere(SharedR.font.inter, 700), "Padel 12/09"), 0.01f)
        assertEquals(semibold, larghezza(carattere(SharedR.font.inter, 600), "Padel 12/09"), 0.01f)
        // Sul 500 non cade: il 600 e' un disegno diverso dal medio.
        assertNotEquals(larghezza(carattere(SharedR.font.inter_medium), "Padel 12/09"), semibold, 0.01f)
    }

    @Test
    fun `senza tnum le cifre di Inter sono proporzionali, con tnum stanno in fila`() {
        val libero =
            Paint().apply {
                typeface = carattere(SharedR.font.inter, 600)
                textSize = 100f
            }
        val tabulare = Paint(libero).apply { fontFeatureSettings = "tnum" }
        // Falsificazione: senza la feature "11" e "88" misurano diverso, ed e' per questo che serve tnum.
        assertNotEquals(libero.measureText("11"), libero.measureText("88"), 1f)
        assertEquals(tabulare.measureText("11"), tabulare.measureText("88"), 0.01f)
        assertEquals(tabulare.measureText("00"), tabulare.measureText("77"), 0.01f)
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
        // Nel layout e' textFontWeight 600: Inter semibold.
        val inter600 = carattere(SharedR.font.inter, 600)
        assertEquals(600, data.paint.typeface.weight)
        // La vista ha la sua spaziatura (0,12 em): il confronto la porta anche sul file.
        assertEquals(larghezza(inter600, "PADEL 12/09", data.letterSpacing), larghezzaDellaVista(data, "PADEL 12/09"), 0.5f)
        val condensato = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        assertNotEquals(larghezza(condensato, "PADEL 12/09", data.letterSpacing), larghezzaDellaVista(data, "PADEL 12/09"), 1f)

        // Il testo dei giocatori: Inter 400.
        val giocatori = riga.findViewById<TextView>(R.id.players_textview)
        assertEquals(
            larghezza(carattere(SharedR.font.inter, 400), "Marco, Luca", giocatori.letterSpacing),
            larghezzaDellaVista(giocatori, "Marco, Luca"),
            0.5f,
        )
    }

    /** Il pennello di Inter 600 con cifre tabulari, come lo vuole lo stile del punteggio. */
    private fun inter600Tabulare(spaziatura: Float) =
        Paint().apply {
            typeface = carattere(SharedR.font.inter, 600)
            textSize = 100f
            letterSpacing = spaziatura
            fontFeatureSettings = "tnum"
        }

    @Test
    fun `un punteggio dello storico e' in Inter 600 con numeri tabulari`() {
        val riga = gonfia(R.layout.match_item)
        listOf(R.id.team1_score_textview, R.id.team2_score_textview).forEach { id ->
            val punti = riga.findViewById<TextView>(id)
            assertEquals("tnum", punti.fontFeatureSettings)
            assertEquals(600, punti.paint.typeface.weight)
            // Inter 600 con tnum, non un'altra famiglia: la larghezza di "10" e' quella del file con la feature.
            assertEquals(inter600Tabulare(punti.letterSpacing).measureText("10"), larghezzaDellaVista(punti, "10"), 0.5f)
            // Tabulari: "11" e "88" occupano lo stesso spazio, le colonne di punteggi stanno in fila.
            assertEquals(larghezzaDellaVista(punti, "11"), larghezzaDellaVista(punti, "88"), 0.01f)
        }
        // Il risultato dei set e' un punteggio anche lui, in 600.
        val set = riga.findViewById<TextView>(R.id.sets_textview)
        assertEquals("tnum", set.fontFeatureSettings)
        assertEquals(600, set.paint.typeface.weight)
        assertEquals(larghezzaDellaVista(set, "11-11"), larghezzaDellaVista(set, "88-88"), 0.01f)
        assertEquals(inter600Tabulare(set.letterSpacing).measureText("6-4"), larghezzaDellaVista(set, "6-4"), 0.5f)
    }

    @Test
    fun `il minuto del registro e' Inter 500 e le cifre delle statistiche Inter 600, tutti e due tabulari`() {
        val minuto = gonfia(R.layout.match_event_item).findViewById<TextView>(R.id.event_timestamp)
        assertEquals("tnum", minuto.fontFeatureSettings)
        assertEquals(500, minuto.paint.typeface.weight)
        assertEquals(larghezzaDellaVista(minuto, "11'"), larghezzaDellaVista(minuto, "88'"), 0.01f)

        val statistica = gonfia(R.layout.item_player_stat)
        listOf(R.id.text_rank, R.id.text_goals).forEach { id ->
            val cifra = statistica.findViewById<TextView>(id)
            assertEquals("tnum", cifra.fontFeatureSettings)
            assertEquals(600, cifra.paint.typeface.weight)
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
     * La misura delle cifre giganti con Inter 600 e tnum (G-1b). Non c'e' un corpo fisso: la regola
     * prende il piu' piccolo fra il tetto (150dp) e cio' che entra nella mezza colonna, per larghezza e
     * per altezza. Misurato a 411dp: "88" (calcio) 133,5dp, "AV" (padel e tennis) 124,8dp, cioe' l'89% e
     * l'83% del tetto; a 360dp 113,8dp e 106,5dp. Con le cifre tabulari di Inter "AV" e' il token piu'
     * largo (in JetBrains Mono erano uguali). Alle altezze: le cifre di Inter sono alte 0,73 em, quindi
     * "AV" a 124,8dp vale 91dp di altezza contro i 105dp del mono a 144,6dp: costa il 13% di altezza.
     */
    @Test
    fun `le cifre giganti del gioco sono in Inter 600 con tnum e 88 e AV entrano nella mezza colonna del Pixel 9a`() {
        val radice = misuraLaColonna(411)
        val densita = radice.resources.displayMetrics.density
        listOf(R.id.team1_score_textview, R.id.team2_score_textview).forEach { id ->
            val cifre = radice.findViewById<TextView>(id)
            assertEquals("tnum", cifre.fontFeatureSettings)
            assertEquals(600, cifre.paint.typeface.weight)
        }
        val mezzaColonna = radice.findViewById<View>(R.id.score_row).width / 2f - 16 * densita
        val punti = radice.findViewById<TextView>(R.id.team1_score_textview)
        val corpi =
            listOf("88" to 130f, "AV" to 120f).associate { (token, minimoDp) ->
                val dimensione = dimensioneDelNumero(radice, token)
                val larghezzaDelToken = Paint(punti.paint).apply { textSize = dimensione }.measureText(token)
                assertTrue(
                    "\"$token\" e' largo ${larghezzaDelToken}px, la mezza colonna ne da $mezzaColonna",
                    larghezzaDelToken <= mezzaColonna + 0.5f,
                )
                val corpoDp = dimensione / densita
                assertTrue("\"$token\" scende a ${corpoDp}dp: sotto i ${minimoDp}dp misurati", corpoDp >= minimoDp)
                token to dimensione
            }
        // Falsificazione della misura: con le cifre tabulari di Inter "AV" e' piu' largo di "88", quindi il corpo
        // per le racchette e' piu' piccolo di quello del calcio. Se fossero uguali la misura non guarderebbe il token.
        assertTrue("AV ${corpi["AV"]} dovrebbe stare sotto 88 ${corpi["88"]}", corpi.getValue("AV") < corpi.getValue("88"))
    }

    @Test
    fun `sul telefono stretto da 360dp le cifre giganti scendono ma restano sopra il minimo di 72dp`() {
        val radice = misuraLaColonna(360)
        val densita = radice.resources.displayMetrics.density
        val minimo = radice.resources.getDimension(R.dimen.score_text_min)
        listOf("88" to 110f, "AV" to 103f).forEach { (token, minimoDp) ->
            val dimensione = dimensioneDelNumero(radice, token)
            assertTrue("\"$token\" a 360dp scende a ${dimensione / densita}dp, sotto il minimo", dimensione >= minimo)
            val corpoDp = dimensione / densita
            assertTrue("\"$token\" a 360dp scende a ${corpoDp}dp: sotto i ${minimoDp}dp misurati", corpoDp >= minimoDp)
        }
    }
}
