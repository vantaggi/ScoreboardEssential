package it.vantaggi.scoreboardessential

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.card.MaterialCardView
import it.vantaggi.scoreboardessential.core.TeamInk
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.domain.models.MatchEvent
import it.vantaggi.scoreboardessential.domain.models.MatchEventType
import it.vantaggi.scoreboardessential.domain.models.MatchReportData
import it.vantaggi.scoreboardessential.utils.MatchReportUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import it.vantaggi.scoreboardessential.shared.R as SharedR

/**
 * Passo G-8, il report PDF. Esce dal telefono e si stampa, quindi sta su CARTA CHIARA (DESIGN.md,
 * conflitto 14): gli stessi ruoli del tema scuro nei valori `print_*`. Qui si controlla che la
 * pagina non abbia colori Street, colori scritti a mano o maiuscolo, che ogni testo sia Inter con
 * cifre tabulari e al massimo tre pesi, che i colori dei lati siano quelli della partita (come nella
 * Cronaca) solo come barretta, che il testo regga 4,5:1 sul fondo scelto e che la pagina stia in un
 * foglio A4. Ogni controllo ha la sua falsificazione.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w411dp-h923dp-xxhdpi")
class ReportPdfG8Test {
    private val base: Context get() = ApplicationProvider.getApplicationContext()
    private val tema: Context get() = ContextThemeWrapper(base, R.style.Theme_ScoreboardEssential)

    private fun file(percorso: String) = File("src/main/$percorso")

    private val carta get() = base.getColor(R.color.print_background)
    private val gruppo get() = base.getColor(R.color.print_surface)

    private fun giocatore(nome: String) = PlayerWithRoles(Player(playerName = nome, appearances = 3, goals = 2), emptyList())

    private fun gol(
        nome: String,
        id: Int,
    ) = MatchEvent(timestamp = "10:00", event = "GOL", team = 1, player = nome, type = MatchEventType.SCORE, playerId = id)

    private fun partita(
        giocatori1: Int = 11,
        giocatori2: Int = 11,
        colore1: Int? = null,
        colore2: Int? = null,
    ) = MatchReportData(
        team1Name = "Rossi 1",
        team1Score = 3,
        team1Color = colore1,
        team1Players = List(giocatori1) { giocatore("Giocatore Uno $it") },
        team2Name = "Blu 2",
        team2Score = 2,
        team2Color = colore2,
        team2Players = List(giocatori2) { giocatore("Giocatore Due $it") },
        matchEvents = listOf(gol("Giocatore Uno 3", 3), gol("Giocatore Uno 3", 3), gol("Giocatore Due 5", 15)),
    )

    private fun pagina(dati: MatchReportData = partita()) = MatchReportUtils.buildReportView(tema, dati, attributesScorer = true)

    private fun tutti(radice: View): List<View> =
        listOf(radice) + ((radice as? ViewGroup)?.let { g -> (0 until g.childCount).flatMap { tutti(g.getChildAt(it)) } } ?: emptyList())

    private fun testi(radice: View) = tutti(radice).filterIsInstance<TextView>()

    // --- Niente Street, niente colori scritti a mano, niente maiuscolo ---

    private val street =
        "concrete_gray|stencil_white|sidewalk_gray|graffiti_[a-z_]+|asphalt_[a-z]+|outline_gray|neon_cyan|" +
            "team_spray_yellow|team_electric_green|error_red|error_text"

    /** Toglie i commenti (che possono nominare i colori di prima o un esadecimale) prima di cercare i difetti. */
    private fun senzaCommenti(testo: String) =
        testo
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("(?m)^\\s*//.*$"), "")

    private fun difetti(sorgente: String): List<String> {
        val s = senzaCommenti(sorgente)
        val trovati = mutableListOf<String>()
        Regex("@color/($street)|R\\.color\\.($street)").findAll(s).forEach { trovati += it.value }
        // Un colore scritto a mano: #RRGGBB o #AARRGGBB, oppure Color.rgb / parseColor / 0xFF...
        Regex("#[0-9A-Fa-f]{6,8}\\b|Color\\.(rgb|argb|parseColor)|0x[Ff][Ff][0-9A-Fa-f]{6}").findAll(s).forEach { trovati += it.value }
        if (s.contains("textAllCaps=\"true\"") || s.contains("isAllCaps") || s.contains(".uppercase(") || s.contains(".toUpperCase(")) {
            trovati += "maiuscolo"
        }
        if (s.contains("Street\"") || s.contains(".Street") || s.contains("_Street")) trovati += "stile Street"
        if (Regex("(android:elevation|app:cardElevation)=\"(?!0dp)[^\"]+\"").containsMatchIn(s)) trovati += "ombra"
        return trovati
    }

    @Test
    fun `nessun colore Street ne' colore scritto a mano ne' maiuscolo ne' ombra nei file del PDF`() {
        val file =
            listOf(
                "res/layout/pdf_match_report.xml",
                "res/drawable/bg_print_group.xml",
                "res/drawable/print_divider.xml",
                "java/it/vantaggi/scoreboardessential/utils/MatchReportUtils.kt",
            )
        for (percorso in file) {
            val sorgente = file(percorso).readText()
            assertEquals("$percorso: ${difetti(sorgente)}", emptyList<String>(), difetti(sorgente))
        }
    }

    // Falsificazione: ogni difetto, messo in un esempio, viene trovato; e un commento che lo nomina no.
    @Test
    fun `il controllo del PDF vede ciascun difetto`() {
        assertTrue(difetti("""<TextView android:textColor="@color/stencil_white" />""").isNotEmpty())
        assertTrue(difetti("""<View android:background="@color/asphalt_dark" />""").isNotEmpty())
        assertTrue(difetti("""setTextColor(context.getColor(R.color.sidewalk_gray))""").isNotEmpty())
        assertTrue(difetti("""<View android:background="#121212" />""").isNotEmpty())
        assertTrue(difetti("""setBackgroundColor(Color.parseColor("#E0E0E0"))""").isNotEmpty())
        assertTrue(difetti("""<TextView android:textAllCaps="true" />""").isNotEmpty())
        assertTrue(difetti("""titolo.text = titolo.text.toString().uppercase()""").isNotEmpty())
        assertTrue(difetti("""setTextAppearance(R.style.TextAppearance_App_BodyLarge_Street)""").isNotEmpty())
        assertTrue(difetti("""<View android:elevation="4dp" />""").isNotEmpty())
        val commento = """<!-- il vecchio #121212 e @color/stencil_white --> <View android:elevation="0dp" />"""
        assertEquals(emptyList<String>(), difetti(commento))
    }

    @Test
    fun `le stringhe del PDF non sono in maiuscolo in nessuna lingua`() {
        fun maiuscolo(valore: String) = valore.any { it.isLetter() } && valore == valore.uppercase()
        for (lingua in listOf("values", "values-it")) {
            val xml = file("res/$lingua/strings.xml").readText()
            val stringhe =
                Regex("<string name=\"((?:report_pdf_[a-z_]+)|label_formations)\">([^<]*)</string>")
                    .findAll(xml)
                    .associate { it.groupValues[1] to it.groupValues[2] }
            assertTrue("$lingua: poche stringhe ${stringhe.keys}", stringhe.size >= 6)
            val gridate = stringhe.filterValues { maiuscolo(it) }
            assertEquals("$lingua: ${gridate.keys}", emptyMap<String, String>(), gridate)
        }
        // Falsificazione: i testi di prima erano tutti maiuscoli e il controllo li vede.
        assertTrue(maiuscolo("MATCH REPORT"))
        assertTrue(maiuscolo("TABELLINO MARCATORI"))
        assertTrue(!maiuscolo("Match report"))
        assertTrue(!maiuscolo("Tabellino marcatori"))
    }

    @Test
    fun `i colori Street del PDF non esistono piu' in colors xml`() {
        val colori = file("res/values/colors.xml").readText()
        for (nome in listOf("asphalt_black", "stencil_white", "sidewalk_gray")) {
            assertTrue("$nome e' ancora definito", !colori.contains("name=\"$nome\""))
        }
        // La carta e' fatta di token con nome di ruolo, nessuno e' un colore di prima.
        for (nome in listOf("print_background", "print_surface", "print_border", "print_text_primary", "print_text_secondary")) {
            assertTrue("$nome manca", colori.contains("name=\"$nome\""))
        }
    }

    // --- Cifre tabulari, Inter, tre pesi ---

    @Test
    fun `ogni testo della pagina ha le cifre tabulari`() {
        val vista = pagina()
        val tutte = testi(vista)

        assertTrue("pochi testi: ${tutte.size}", tutte.size >= 30)
        val senza = tutte.filter { it.fontFeatureSettings?.contains("tnum") != true }.map { it.text.toString() }
        assertEquals(emptyList<String>(), senza)
        // Tabulari davvero: "11" e "88" occupano lo stesso spazio, nel punteggio e nelle righe di testo.
        val punteggio = vista.findViewById<TextView>(R.id.pdf_team1_score)
        val nome = vista.findViewById<LinearLayout>(R.id.pdf_team1_players_list).getChildAt(0) as TextView
        for (testo in listOf(punteggio, nome)) {
            assertEquals(larghezza(testo, "11"), larghezza(testo, "88"), 0.01f)
        }
        // Falsificazione: senza la feature le cifre di Inter sono proporzionali e "11" e "88" misurano diverso.
        val libero = TextView(tema).apply { typeface = nome.typeface }
        assertNull(libero.fontFeatureSettings)
        assertTrue(Math.abs(larghezza(libero, "11") - larghezza(libero, "88")) > 1f)
    }

    private fun larghezza(
        vista: TextView,
        testo: String,
    ): Float = Paint(vista.paint).apply { textSize = 100f }.measureText(testo)

    private fun inter(
        peso: Int,
        vista: TextView,
    ) = Paint().apply {
        typeface = Typeface.create(ResourcesCompat.getFont(base, SharedR.font.inter)!!, peso, false)
        textSize = 100f
        letterSpacing = vista.letterSpacing
        fontFeatureSettings = "tnum"
    }

    @Test
    fun `il testo e' Inter in al massimo tre pesi fra 400, 500 e 600`() {
        val vista = pagina()

        val pesi = testi(vista).map { it.paint.typeface.weight }.toSet()
        assertTrue("pesi usati: $pesi", pesi.isNotEmpty() && pesi.size <= 3 && pesi.all { it in setOf(400, 500, 600) })
        // I punteggi e i nomi delle squadre sono 600, le righe 400: non c'e' il 700 ne' il 900 di prima.
        val punteggio = vista.findViewById<TextView>(R.id.pdf_team1_score)
        val nomeSquadra = vista.findViewById<TextView>(R.id.pdf_team1_name)
        val giocatore = vista.findViewById<LinearLayout>(R.id.pdf_team1_players_list).getChildAt(0) as TextView
        assertEquals(600, punteggio.paint.typeface.weight)
        assertEquals(600, nomeSquadra.paint.typeface.weight)
        assertEquals(400, giocatore.paint.typeface.weight)
        // Inter e non un'altra famiglia: la larghezza di una stringa e' quella del file Inter con la feature.
        assertEquals(inter(600, punteggio).measureText("32"), larghezza(punteggio, "32"), 0.5f)
        assertEquals(inter(400, giocatore).measureText("Giocatore Uno 3"), larghezza(giocatore, "Giocatore Uno 3"), 0.5f)
        // Falsificazione: un grassetto di sistema ha peso 700 e il controllo lo scarta.
        assertTrue(Typeface.DEFAULT_BOLD.weight !in setOf(400, 500, 600))
    }

    // --- Colori dei lati: quelli della partita, come nella Cronaca, solo barretta ---

    private fun barretta(vista: View): Int = ((vista.background as LayerDrawable).getDrawable(0) as GradientDrawable).color!!.defaultColor

    @Test
    fun `i colori dei lati sono quelli della partita, scuriti a 3 a 1 sul gruppo solo se serve`() {
        // Colori scelti dall'utente: il blu notte regge e resta com'e', il rosa pieno anche, il giallo no.
        val bluNotte = 0xFF1A237E.toInt()
        val giallo = 0xFFFFD600.toInt()
        val vista = pagina(partita(colore1 = bluNotte, colore2 = giallo))

        assertEquals(bluNotte, barretta(vista.findViewById(R.id.pdf_team1_name)))
        assertEquals(bluNotte, barretta(vista.findViewById(R.id.pdf_team1_players_title)))
        val scuritoGiallo = barretta(vista.findViewById(R.id.pdf_team2_name))
        assertEquals(TeamInk.graphicOnLight(giallo, gruppo), scuritoGiallo)
        assertEquals(scuritoGiallo, barretta(vista.findViewById(R.id.pdf_team2_players_title)))
        assertTrue(TeamInk.contrast(scuritoGiallo, gruppo) >= 3.0)
        // Falsificazione: il giallo schiarito, come sul fondo scuro, su carta non regge 3:1.
        assertTrue(TeamInk.contrast(TeamInk.graphicOn(giallo, gruppo), gruppo) < 3.0)
    }

    @Test
    fun `senza colore scelto i lati sono lime e ciano come nella Cronaca`() {
        assertEquals(base.getColor(R.color.elite_lime), base.getColor(R.color.team_side_1))
        assertEquals(base.getColor(R.color.elite_cyan), base.getColor(R.color.team_side_2))

        val vista = pagina(partita())
        val lime = barretta(vista.findViewById(R.id.pdf_team1_name))
        val ciano = barretta(vista.findViewById(R.id.pdf_team2_name))
        // Sono ancora lime e ciano (la tinta resta) ma scuriti: il lime puro fa 1,2:1 sul gruppo e non si vedrebbe.
        assertEquals(TeamInk.graphicOnLight(base.getColor(R.color.elite_lime), gruppo), lime)
        assertEquals(TeamInk.graphicOnLight(base.getColor(R.color.elite_cyan), gruppo), ciano)
        assertTrue(TeamInk.contrast(base.getColor(R.color.elite_lime), gruppo) < 3.0)
        assertTrue(TeamInk.contrast(lime, gruppo) >= 3.0 && TeamInk.contrast(ciano, gruppo) >= 3.0)
    }

    @Test
    fun `le squadre sono una barretta e non un blocco pieno con il testo sopra`() {
        val vista = pagina(partita(colore1 = 0xFFFFD600.toInt(), colore2 = 0xFF1A237E.toInt()))

        for ((rigaId, nomeId) in listOf(R.id.pdf_team1_band to R.id.pdf_team1_name, R.id.pdf_team2_band to R.id.pdf_team2_name)) {
            // La riga non ha riempimento di squadra: l'unico colore e' lo strato stretto sotto il nome.
            assertNull(vista.findViewById<View>(rigaId).background)
            val strati = vista.findViewById<TextView>(nomeId).background as LayerDrawable
            assertEquals(1, strati.numberOfLayers)
            assertEquals(4, strati.getLayerWidth(0))
        }
        // Falsificazione: un blocco pieno avrebbe uno sfondo sulla riga, e il controllo lo trova.
        assertNotNull(LinearLayout(tema).apply { background = ColorDrawable(0xFFFFD600.toInt()) }.background)
    }

    // --- Contrasto del testo sul fondo scelto ---

    @Test
    fun `i token della carta reggono 4,5 a 1 il testo e il testo non e' mai il colore della squadra`() {
        val primario = base.getColor(R.color.print_text_primary)
        val secondario = base.getColor(R.color.print_text_secondary)
        for (fondo in listOf(carta, gruppo)) {
            assertTrue(TeamInk.contrast(primario, fondo) >= 4.5)
            assertTrue(TeamInk.contrast(secondario, fondo) >= 4.5)
        }
        // Falsificazione: i testi del tema scuro su carta non passano, e nemmeno il lime come testo.
        assertTrue(TeamInk.contrast(base.getColor(R.color.elite_text_primary), carta) < 4.5)
        assertTrue(TeamInk.contrast(base.getColor(R.color.elite_text_secondary), carta) < 4.5)
        assertTrue(TeamInk.contrast(base.getColor(R.color.elite_lime), carta) < 4.5)
    }

    @Test
    fun `ogni testo della pagina e' leggibile sulla carta e sul gruppo con qualunque colore di squadra`() {
        for (colore in listOf(null, 0xFFFFD600.toInt(), 0xFF1A237E.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt())) {
            val vista = pagina(partita(colore1 = colore, colore2 = colore))
            for (testo in testi(vista)) {
                for (fondo in listOf(carta, gruppo)) {
                    val c = TeamInk.contrast(testo.currentTextColor, fondo)
                    assertTrue("\"${testo.text}\" con squadra $colore fa %.2f:1".format(c), c >= 4.5)
                }
            }
            // E la barretta si stacca dal gruppo anche per il bianco e il nero.
            assertTrue(TeamInk.contrast(barretta(vista.findViewById(R.id.pdf_team1_name)), gruppo) >= 3.0)
        }
    }

    // --- Gruppi tonali, niente card, niente ombre; la pagina e' la carta ---

    @Test
    fun `la pagina e' carta chiara con gruppi tonali, senza card ne' ombre`() {
        val vista = pagina()

        assertEquals(carta, (vista.background as ColorDrawable).color)
        assertTrue(tutti(vista).none { it is MaterialCardView })
        assertTrue(tutti(vista).all { it.elevation == 0f })
        for (id in listOf(R.id.pdf_formations, R.id.pdf_scorers_list)) {
            val sfondo = vista.findViewById<View>(id).background as GradientDrawable
            assertEquals(gruppo, sfondo.color!!.defaultColor)
        }
        // Il risultato sta in un gruppo: la riga di una squadra ha il genitore col fondo del gruppo.
        val risultato = vista.findViewById<View>(R.id.pdf_team1_band).parent as View
        assertEquals(gruppo, (risultato.background as GradientDrawable).color!!.defaultColor)
    }

    // --- La pagina sta in un A4, anche col carattere ingrandito del telefono ---

    @Test
    fun `la pagina si disegna a densita 1 e carattere 100 per cento anche su un telefono denso`() {
        // Questo test gira a xxhdpi (densita 3): con quella la pagina da 595 punti conterrebbe venti caratteri per riga.
        assertEquals(3f, base.resources.displayMetrics.density, 0.01f)
        val vista = pagina()

        assertEquals(1f, vista.resources.displayMetrics.density, 0.001f)
        assertEquals(1f, vista.resources.configuration.fontScale, 0.001f)
        assertEquals(16, vista.resources.getDimensionPixelSize(R.dimen.space_16))
    }

    @Test
    fun `un report di calcio a undici contro undici sta in un foglio A4`() {
        val vista = pagina(partita(giocatori1 = 11, giocatori2 = 11))
        vista.measure(
            View.MeasureSpec.makeMeasureSpec(595, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(842, View.MeasureSpec.UNSPECIFIED),
        )

        assertTrue("la pagina e' alta ${vista.measuredHeight} su 842", vista.measuredHeight in 300..842)
        // Falsificazione: con quindici giocatori per lato la pagina cresce, quindi il controllo misura davvero il contenuto.
        val lunga = pagina(partita(giocatori1 = 15, giocatori2 = 15))
        lunga.measure(
            View.MeasureSpec.makeMeasureSpec(595, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(842, View.MeasureSpec.UNSPECIFIED),
        )
        assertTrue(lunga.measuredHeight > vista.measuredHeight)
    }

    // --- Stati: vuoto, e il tabellino solo dove c'e' un marcatore ---

    @Test
    fun `una squadra senza giocatori ha lo stato vuoto in testo secondario`() {
        val vista = pagina(partita(giocatori1 = 0, giocatori2 = 2))

        val vuota = vista.findViewById<LinearLayout>(R.id.pdf_team1_players_list)
        assertEquals(1, vuota.childCount)
        val stato = vuota.getChildAt(0) as TextView
        assertEquals(base.getString(R.string.report_pdf_no_players), stato.text.toString())
        assertEquals(base.getColor(R.color.print_text_secondary), stato.currentTextColor)
        assertEquals(2, vista.findViewById<LinearLayout>(R.id.pdf_team2_players_list).childCount)
    }

    @Test
    fun `il tabellino elenca i marcatori attribuiti e non compare dove non si attribuisce`() {
        val conMarcatori = pagina()
        val elenco = conMarcatori.findViewById<LinearLayout>(R.id.pdf_scorers_list)
        assertEquals(
            listOf("Giocatore Uno 3 (2)", "Giocatore Due 5 (1)"),
            (0 until elenco.childCount).map { (elenco.getChildAt(it) as TextView).text.toString() },
        )

        val padel = MatchReportUtils.buildReportView(tema, partita(), attributesScorer = false)
        for (id in listOf(R.id.pdf_formations_title, R.id.pdf_formations, R.id.pdf_scorers_title, R.id.pdf_scorers_list)) {
            assertEquals(View.GONE, padel.findViewById<View>(id).visibility)
        }
    }
}
