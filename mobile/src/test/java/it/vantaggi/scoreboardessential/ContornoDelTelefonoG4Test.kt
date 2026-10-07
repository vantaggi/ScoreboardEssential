package it.vantaggi.scoreboardessential

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchWithTeams
import it.vantaggi.scoreboardessential.database.Team
import it.vantaggi.scoreboardessential.ui.EmptyStateView
import it.vantaggi.scoreboardessential.ui.InsetDividerDecoration
import it.vantaggi.scoreboardessential.ui.MatchHistoryUiState
import it.vantaggi.scoreboardessential.ui.ProgressButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Passo G-4: le schermate del contorno del telefono (impostazioni, storico, statistiche, giocatori,
 * aggiungi e modifica, onboarding e i dialoghi) sui ruoli della UI Constitution. Il test legge i
 * layout veri, quelli gonfiati con il tema, e ogni controllo che potrebbe passare per caso ha la sua
 * falsificazione su un esempio sbagliato.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w411dp-h923dp-xxhdpi")
class ContornoDelTelefonoG4Test {
    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var contesto: Context
    private var densita = 1f

    // Il foglio PARTITA mentre si gioca (content_scoreboard_details, team_player_item, match_event_item,
    // match_game_item), la schermata di gioco e il PDF sono G-6 e G-8, la Cronaca G-5: non sono qui.
    private val layoutDelContorno =
        listOf(
            "activity_match_settings",
            "activity_match_history",
            "match_item",
            "activity_statistics",
            "item_player_stat",
            "activity_players_management",
            "item_player_management",
            "activity_add_edit_player",
            "dialog_create_player",
            "dialog_role_selection",
            "dialog_select_scorer",
            "dialog_color_picker",
            "dialog_team_name",
            "item_role",
            "item_role_header",
            "view_role_chip",
            "scorer_item",
            "activity_onboarding",
            "fragment_onboarding_step",
            "view_empty_state",
            "item_chip_filter",
            "activity_padel_elite",
            "item_padel_elite_group",
        )

    @Before
    fun setUp() {
        contesto = ContextThemeWrapper(app, R.style.Theme_ScoreboardEssential)
        densita = contesto.resources.displayMetrics.density
    }

    private fun gonfia(nome: String): View {
        val id = contesto.resources.getIdentifier(nome, "layout", app.packageName)
        return LayoutInflater.from(contesto).inflate(id, FrameLayout(contesto), false)
    }

    private fun tutti(radice: View): List<View> =
        listOf(radice) + ((radice as? ViewGroup)?.let { g -> (0 until g.childCount).flatMap { tutti(g.getChildAt(it)) } } ?: emptyList())

    private fun sorgente(nome: String) = File("src/main/res/layout/$nome.xml").readText()

    /** Che cosa un layout non dovrebbe avere piu': maiuscolo forzato, colori Street o rosa, ripple di sistema, ombre scritte a mano. */
    private fun difetti(xml: String): List<String> {
        val trovati = mutableListOf<String>()
        if (xml.contains("textAllCaps=\"true\"")) trovati += "textAllCaps"
        Regex(
            "@color/(concrete_gray|stencil_white|sidewalk_gray|graffiti_[a-z_]+|asphalt_[a-z]+|outline_gray|neon_cyan|team_spray_yellow|team_electric_green|error_red|error_text|elite_text_tertiary|elite_cyan)",
        ).findAll(xml)
            .forEach { trovati += it.value }
        if (xml.contains("selectableItemBackground")) trovati += "ripple di sistema"
        Regex("""(android:elevation|app:cardElevation)="(?!0dp)[^"]+"""").findAll(xml).forEach { trovati += it.value }
        return trovati
    }

    // --- Niente maiuscolo, rosa, colori Street, ombre ---

    @Test
    fun `nessun layout del contorno usa maiuscolo forzato, rosa, colori Street o ombre`() {
        for (nome in layoutDelContorno) {
            assertEquals("$nome: ${difetti(sorgente(nome))}", emptyList<String>(), difetti(sorgente(nome)))
        }
    }

    // Falsificazione: lo stesso controllo trova ciascun difetto se c'e'.
    @Test
    fun `il controllo dei layout vede il maiuscolo, il rosa, il ripple e l'ombra`() {
        assertTrue(difetti("""<TextView android:textAllCaps="true" />""").isNotEmpty())
        assertTrue(difetti("""<View android:background="@color/graffiti_pink" />""").isNotEmpty())
        assertTrue(difetti("""<View android:textColor="@color/stencil_white" />""").isNotEmpty())
        assertTrue(difetti("""<View android:background="?attr/selectableItemBackground" />""").isNotEmpty())
        assertTrue(difetti("""<View android:elevation="4dp" />""").isNotEmpty())
        assertTrue(difetti("""<View app:cardElevation="2dp" />""").isNotEmpty())
        // E non scambia per difetti un'ombra a zero.
        assertEquals(emptyList<String>(), difetti("""<View android:elevation="0dp" />"""))
        // La schermata di gioco ne ha ancora (G-6): il controllo la vede.
        assertTrue(difetti(sorgente("content_scoreboard_live")).isNotEmpty())
    }

    @Test
    fun `i layout gonfiati non hanno ombre salvo il tasto che galleggia`() {
        // view_empty_state e' un merge: lo gonfia EmptyStateView, e i layout che la usano la contengono.
        for (nome in layoutDelContorno - "view_empty_state") {
            for (v in tutti(gonfia(nome))) {
                if (v is FloatingActionButton) continue
                assertEquals("$nome: ${v.javaClass.simpleName} ha un'ombra", 0f, v.elevation, 0f)
                if (v is MaterialCardView) assertEquals("$nome: card con elevazione", 0f, v.cardElevation, 0f)
            }
        }
    }

    @Test
    fun `il rosa e il terziario non sono piu' citati dal contorno ne' dal codice che lo disegna`() {
        val colors = File("src/main/res/values/colors.xml").readText()
        assertFalse("elite_text_tertiary e' stato tolto", colors.contains("elite_text_tertiary"))
        val daControllare =
            listOf("src/main/res/values", "src/main/res/color", "src/main/res/drawable", "src/main/res/menu") +
                listOf("src/main/java/it/vantaggi/scoreboardessential/ui", "src/main/java/it/vantaggi/scoreboardessential/utils")
        for (cartella in daControllare) {
            File(cartella).walkTopDown().filter { it.isFile && it.name != "colors.xml" }.forEach {
                val testo = it.readText()
                assertFalse("${it.name} cita elite_text_tertiary", testo.contains("elite_text_tertiary"))
            }
        }
        // Le categorie dei ruoli non hanno piu' un colore: ne' rosa, ne' ciano.
        val ruoli = File("src/main/java/it/vantaggi/scoreboardessential/utils/RoleUtils.kt").readText()
        assertFalse(ruoli.contains("graffiti_pink"))
        assertFalse(ruoli.contains("neon_cyan"))
    }

    @Test
    fun `gli stili di testo del contorno non sono in maiuscolo`() {
        val themes = File("src/main/res/values/themes.xml").readText()
        for (stile in listOf(
            "TextAppearance.App.SectionHeading",
            "TextAppearance.App.RowTitle",
            "TextAppearance.App.Caption",
            "TextAppearance.App.HeadlineMedium.Street",
        )) {
            val corpo = Regex("""<style name="${Regex.escape(stile)}".*?</style>""", RegexOption.DOT_MATCHES_ALL).find(themes)!!.value
            assertFalse("$stile e' in maiuscolo", corpo.contains("textAllCaps\">true"))
        }
        val styles = File("src/main/res/values/styles.xml").readText()
        assertFalse("la scheda delle statistiche e' rosa", styles.contains("graffiti_pink"))
    }

    // --- Pressione uguale ovunque sulle righe cliccabili ---

    private fun premi(vista: View) {
        vista.isPressed = true
        vista.stateListAnimator?.jumpToCurrentState()
    }

    @Test
    fun `le righe cliccabili hanno la pressione condivisa e non il ripple`() {
        val cliccabili =
            mapOf(
                "item_player_management" to gonfia("item_player_management"),
                "scorer_item" to gonfia("scorer_item").also { it.isClickable = true },
                "match_item" to gonfia("match_item"),
            )
        for ((nome, vista) in cliccabili) {
            assertNotNull("$nome: manca la pressione", vista.stateListAnimator)
            premi(vista)
            assertEquals("$nome: scala", 0.97f, vista.scaleX, 0.001f)
            assertEquals("$nome: opacita'", 0.85f, vista.alpha, 0.001f)
            vista.isPressed = false
            vista.stateListAnimator?.jumpToCurrentState()
            assertEquals("$nome: al rilascio torna com'era", 1f, vista.scaleX, 0.001f)
        }
        // I comandi di sola icona delle righe la hanno anch'essi.
        for (id in listOf(R.id.stats_button)) {
            assertNotNull(gonfia("item_player_management").findViewById<View>(id).stateListAnimator)
        }
        assertNotNull(gonfia("match_item").findViewById<View>(R.id.delete_match_button).stateListAnimator)
    }

    // Falsificazione: una riga cliccabile senza lo stile non ha la pressione, quindi il controllo la distingue.
    @Test
    fun `una riga cliccabile senza lo stile non ha la pressione`() {
        val nuda = FrameLayout(contesto).apply { isClickable = true }
        assertNull(nuda.stateListAnimator)
    }

    // --- Stati vuoti: titolo, motivo e strada ---

    private fun vuoto(nome: String): EmptyStateView = tutti(gonfia(nome)).filterIsInstance<EmptyStateView>().single()

    @Test
    fun `gli stati vuoti dicono cosa manca, perche' e cosa fare`() {
        for (nome in listOf("activity_match_history", "activity_statistics", "activity_players_management")) {
            val stato = vuoto(nome)
            val titolo = stato.findViewById<android.widget.TextView>(R.id.empty_title).text.toString()
            val motivo = stato.findViewById<android.widget.TextView>(R.id.empty_reason).text.toString()
            assertTrue("$nome: titolo", titolo.isNotBlank())
            assertTrue("$nome: motivo", motivo.length > 30)
            assertEquals("$nome: ha una strada", View.VISIBLE, stato.actionButton.visibility)
            assertTrue("$nome: l'azione ha un'etichetta", stato.actionButton.text.isNotBlank())
            assertFalse("$nome: niente punto esclamativo", (titolo + motivo).contains('!'))
        }
        // Dice cosa e' vuoto (nome della cosa nel titolo), non un generico "niente".
        assertTrue(vuoto("activity_match_history").findViewById<android.widget.TextView>(R.id.empty_title).text.contains("partita"))
        assertTrue(vuoto("activity_players_management").findViewById<android.widget.TextView>(R.id.empty_title).text.contains("giocatore"))
    }

    @Test
    fun `lo stato vuoto non e' un tono da marketing ne' in italiano ne' in inglese`() {
        val resto = listOf("values", "values-it")
        for (cartella in resto) {
            val testo = File("src/main/res/$cartella/strings.xml").readText()
            assertFalse("$cartella: il King", testo.contains("the King"))
            assertFalse("$cartella: il King", testo.contains("e' il King") || testo.contains("è il King"))
        }
    }

    // Falsificazione: uno stato vuoto senza strada non ha il bottone in vista.
    @Test
    fun `uno stato vuoto senza azione non mostra il bottone`() {
        val stato = EmptyStateView(contesto)
        assertEquals(View.GONE, stato.actionButton.visibility)
    }

    // Il motore del vuoto della ricerca e quello dell'elenco vuoto dicono cose diverse.
    @Test
    fun `lo stato vuoto cambia messaggio quando la ricerca non trova`() {
        val stato = vuoto("activity_players_management")
        val prima = stato.findViewById<android.widget.TextView>(R.id.empty_title).text.toString()
        stato.setMessage(contesto.getString(R.string.no_players_found), contesto.getString(R.string.no_players_found_hint))
        assertNotEquals(prima, stato.findViewById<android.widget.TextView>(R.id.empty_title).text.toString())
    }

    // --- Il bottone che lavora tiene la larghezza ---

    private fun misura(vista: View): Int {
        if (vista.layoutParams ==
            null
        ) {
            vista.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        vista.measure(
            View.MeasureSpec.makeMeasureSpec((300 * densita).toInt(), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        vista.layout(0, 0, vista.measuredWidth, vista.measuredHeight)
        return vista.measuredWidth
    }

    @Test
    fun `il ProgressButton tiene la larghezza, mostra l'icona e non accetta altri tocchi`() {
        val bottone =
            ProgressButton(contesto).apply {
                text = "Salva giocatore"
                loadingLabel = "Salvataggio"
            }
        val prima = misura(bottone)

        bottone.setLoading(true)
        assertTrue(bottone.isLoading)
        assertEquals("Salvataggio", bottone.text.toString())
        assertNotNull("l'icona che gira", bottone.icon)
        assertFalse("non accetta altri tocchi", bottone.isClickable)
        assertTrue("la larghezza non scende", misura(bottone) >= prima)

        bottone.setLoading(false)
        assertEquals("Salva giocatore", bottone.text.toString())
        assertNull(bottone.icon)
        assertTrue(bottone.isClickable)
    }

    // Falsificazione: un bottone qualunque che cambia testo si stringe, quindi la misura distingue.
    @Test
    fun `un bottone qualunque con un testo piu' corto si stringe`() {
        val comune = MaterialButton(contesto).apply { text = "Salva giocatore" }
        val prima = misura(comune)
        comune.text = "Salvataggio"
        assertTrue(misura(comune) < prima)
    }

    // --- Linee fra le righe di un gruppo ---

    private fun disegna(righe: Int): Bitmap {
        val lista = RecyclerView(contesto)
        lista.layoutManager = LinearLayoutManager(contesto)
        lista.adapter =
            object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(
                    parent: ViewGroup,
                    viewType: Int,
                ) = object : RecyclerView.ViewHolder(View(parent.context).apply { layoutParams = RecyclerView.LayoutParams(-1, 100) }) {}

                override fun onBindViewHolder(
                    holder: RecyclerView.ViewHolder,
                    position: Int,
                ) {}

                override fun getItemCount() = righe
            }
        lista.addItemDecoration(InsetDividerDecoration(contesto))
        lista.measure(
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.EXACTLY),
        )
        lista.layout(0, 0, 600, 1000)
        val bitmap = Bitmap.createBitmap(600, 1000, Bitmap.Config.ARGB_8888)
        lista.draw(Canvas(bitmap))
        return bitmap
    }

    @Test
    fun `la linea fra le righe e' rientrata al testo e non c'e' dopo l'ultima`() {
        val bitmap = disegna(3)
        val linea = contesto.getColor(R.color.elite_border_strong)
        val inset = (16 * densita).toInt()

        // Fra la prima e la seconda riga: dal bordo del testo in poi, non prima.
        assertEquals(linea, bitmap.getPixel(300, 98))
        assertNotEquals("la linea e' rientrata", linea, bitmap.getPixel(inset / 2, 98))
        assertEquals(linea, bitmap.getPixel(inset + 5, 98))
        // Dopo l'ultima riga niente.
        assertNotEquals(linea, bitmap.getPixel(300, 298))
        // Falsificazione: dentro la riga, lontano dai bordi, non c'e' linea.
        assertNotEquals(linea, bitmap.getPixel(300, 50))
    }

    // --- Schede delle statistiche: un solo indicatore lime, non rosa ---

    @Test
    fun `le schede hanno testo primario sulla scelta, secondario sulle altre e l'indicatore lime`() {
        val tabs = tutti(gonfia("activity_statistics")).filterIsInstance<TabLayout>().single()
        val colori = tabs.tabTextColors!!
        assertEquals(contesto.getColor(R.color.elite_text_primary), colori.getColorForState(intArrayOf(android.R.attr.state_selected), 0))
        assertEquals(contesto.getColor(R.color.elite_text_secondary), colori.defaultColor)
        val stile = File("src/main/res/values/styles.xml").readText()
        val corpo = Regex("""<style name="Widget.App.TabLayout".*?</style>""", RegexOption.DOT_MATCHES_ALL).find(stile)!!.value
        assertTrue(corpo.contains("tabIndicatorColor\">@color/elite_lime"))
    }

    // --- Storico: la scheda si apre sul dettaglio, e lo stato dell'invio resta in vista ---

    private fun partita(id: Int): MatchWithTeams {
        val match =
            Match(
                matchId = id,
                team1Id = 1,
                team2Id = 2,
                team1Score = 6,
                team2Score = 4,
                timestamp = 1_790_193_000_000L,
                sportId = "padel",
                eventLog = MatchLogCodec.encode(emptyList<LoggedEvent>()),
            )
        return MatchWithTeams(
            match,
            Team(id = 1, name = "Blu", color = 0xFF1A237E.toInt(), logoUri = null),
            Team(id = 2, name = "Gialli", color = 0xFFFFD600.toInt(), logoUri = null),
            emptyList(),
        )
    }

    @Test
    fun `la scheda dello storico si apre e si chiude sul dettaglio e tiene lo stato per partita`() {
        val adapter = MatchHistoryAdapter({}, {}, {}, {})
        adapter.submitList(listOf(MatchHistoryUiState(partita(1), "Mario, Luca", null), MatchHistoryUiState(partita(2), "", null)))
        val riga = adapter.onCreateViewHolder(FrameLayout(contesto), 0)
        val vista = riga.itemView
        val dettaglio = vista.findViewById<View>(R.id.match_detail)

        adapter.onBindViewHolder(riga, 0)
        assertEquals("chiusa di norma", View.GONE, dettaglio.visibility)
        // In vista restano le due squadre e il punteggio.
        assertEquals(View.VISIBLE, vista.findViewById<View>(R.id.team1_score_textview).visibility)

        vista.performClick()
        assertEquals(View.VISIBLE, dettaglio.visibility)
        assertEquals(
            "Dettaglio aperto",
            androidx.core.view.ViewCompat
                .getStateDescription(vista)
                ?.toString(),
        )

        // La vista si ricicla su un'altra partita: quella e' chiusa. Tornando alla prima, e' ancora aperta.
        adapter.onBindViewHolder(riga, 1)
        assertEquals(View.GONE, dettaglio.visibility)
        adapter.onBindViewHolder(riga, 0)
        assertEquals(View.VISIBLE, dettaglio.visibility)

        vista.performClick()
        assertEquals(View.GONE, dettaglio.visibility)
        assertEquals(
            "Dettaglio chiuso",
            androidx.core.view.ViewCompat
                .getStateDescription(vista)
                ?.toString(),
        )
    }

    @Test
    fun `lo stato dell'invio e il comando non stanno nel dettaglio chiuso`() {
        val vista = gonfia("match_item")
        val dettaglio = vista.findViewById<View>(R.id.match_detail)
        for (id in listOf(R.id.send_status_textview, R.id.send_match_button)) {
            var genitore = vista.findViewById<View>(id).parent
            while (genitore !== vista && genitore != null) {
                assertNotEquals("il dettaglio nasconderebbe lo stato", dettaglio, genitore)
                genitore = (genitore as View).parent
            }
        }
        // E invece i comandi di questa partita, i set e i giocatori ci sono dentro.
        for (id in listOf(R.id.sets_textview, R.id.players_textview, R.id.export_match_button, R.id.delete_match_button)) {
            var dentro = false
            var genitore = vista.findViewById<View>(id).parent
            while (genitore != null) {
                if (genitore === dettaglio) dentro = true
                genitore = (genitore as? View)?.parent
            }
            assertTrue("$id nel dettaglio", dentro)
        }
    }
}
