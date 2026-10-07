package it.vantaggi.scoreboardessential

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.shape.MaterialShapeDrawable
import it.vantaggi.scoreboardessential.core.TeamInk
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.database.Role
import it.vantaggi.scoreboardessential.domain.model.PlayerStatsDTO
import it.vantaggi.scoreboardessential.domain.models.MatchReportData
import it.vantaggi.scoreboardessential.ui.statistics.StatisticsAdapter
import it.vantaggi.scoreboardessential.utils.MatchReportUtils
import it.vantaggi.scoreboardessential.utils.setRoles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Il contorno, schermate: statistiche, gestione giocatori e report PDF. Una squadra #1A237E (e
 * ogni altro colore) deve restare leggibile dove il colore e' calcolato: le prove sono sui
 * rapporti WCAG fra l'inchiostro scritto e il riempimento, non su colori fissi.
 */
@RunWith(AndroidJUnit4::class)
class GiocatoriEStatisticheDelContornoTest {
    private val base: Context get() = ApplicationProvider.getApplicationContext()
    private val tema: Context get() = ContextThemeWrapper(base, R.style.Theme_ScoreboardEssential)

    private val bluNotte = 0xFF1A237E.toInt()
    private val giallo = 0xFFFFD600.toInt()

    private fun sfondoPieno(vista: View): Int = (vista.background as MaterialShapeDrawable).fillColor!!.defaultColor

    /** AA per il testo normale: almeno 4,5:1. */
    private fun leggibile(
        cosa: String,
        inchiostro: Int,
        sfondo: Int,
    ) {
        val rapporto = TeamInk.contrast(inchiostro, sfondo)
        assertTrue("$cosa a %.2f:1".format(rapporto), rapporto >= 4.5)
    }

    private fun giocatore(nome: String) = PlayerWithRoles(Player(playerName = nome, appearances = 3, goals = 2), emptyList())

    // Prima il podio era una card rosa piena con testo nero; ora e' una riga come le altre e solo i gol sono lime.
    @Test
    fun `il primo della classifica ha i gol in lime e il resto e' una riga normale`() {
        val vista = LayoutInflater.from(tema).inflate(R.layout.item_player_stat, FrameLayout(tema), false)
        val riga = StatisticsAdapter.ViewHolder(vista)
        riga.bind(PlayerStatsDTO(playerId = 1, playerName = "Mario", goals = 12, appearances = 15, winRate = 0.6f), 0)

        val lime = base.getColor(R.color.elite_lime)
        assertEquals(lime, vista.findViewById<TextView>(R.id.text_goals).currentTextColor)
        // Lime su superficie e nome in testo primario: tutti e due leggibili sul fondo del gruppo.
        val fondoDelGruppo = base.getColor(R.color.elite_surface)
        leggibile("gol del primo", lime, fondoDelGruppo)
        leggibile("nome", vista.findViewById<TextView>(R.id.text_player_name).currentTextColor, fondoDelGruppo)
        assertEquals("12 goals", vista.findViewById<TextView>(R.id.text_goals).text.toString())
    }

    // Falsificazione: dal secondo in poi il lime non c'e' (se fosse su tutte le righe non direbbe piu' niente).
    @Test
    fun `fuori dal primo posto i gol sono in testo primario`() {
        val vista = LayoutInflater.from(tema).inflate(R.layout.item_player_stat, FrameLayout(tema), false)
        StatisticsAdapter.ViewHolder(vista).bind(PlayerStatsDTO(2, "Luca", 4, 10, 0.5f), 1)

        assertEquals(base.getColor(R.color.elite_text_primary), vista.findViewById<TextView>(R.id.text_goals).currentTextColor)
    }

    // Rango e presenze non sono a vista: TalkBack le sente nella descrizione della riga.
    @Test
    fun `la riga della classifica dice rango nome gol e presenze a TalkBack`() {
        val vista = LayoutInflater.from(tema).inflate(R.layout.item_player_stat, FrameLayout(tema), false)
        StatisticsAdapter.ViewHolder(vista).bind(PlayerStatsDTO(2, "Luca", 4, 10, 0.5f), 1)

        assertEquals("2. Luca, 4 goals, 10 appearances", vista.contentDescription.toString())
    }

    // Una riga dei giocatori e' il nome e i ruoli: niente avatar colorato, niente numeri.
    @Test
    fun `la riga dei giocatori ha nome e ruoli e basta`() {
        val vista = LayoutInflater.from(tema).inflate(R.layout.item_player_management, FrameLayout(tema), false)
        PlayersManagementAdapter.PlayerViewHolder(vista, {}, {}).bind(
            PlayerWithRoles(Player(playerName = "Mario Rossi", appearances = 3, goals = 2), listOf(Role(1, "Portiere", "PORTA"))),
        )

        assertEquals("Mario Rossi", vista.findViewById<TextView>(R.id.player_name).text.toString())
        val ruoli = vista.findViewById<ChipGroup>(R.id.player_roles_group)
        assertEquals(1, ruoli.childCount)
        assertEquals("POR", (ruoli.getChildAt(0) as Chip).text.toString())
        assertEquals("Portiere", ruoli.getChildAt(0).contentDescription.toString())
        // I numeri e l'avatar non sono in riga: stanno nel dettaglio.
        assertEquals(0, vista.resources.getIdentifier("player_goals", "id", base.packageName))
        assertEquals(0, vista.resources.getIdentifier("player_avatar", "id", base.packageName))
    }

    // Un giocatore senza ruoli e' il nome e basta: niente chip "N/A".
    @Test
    fun `senza ruoli non c'e' nessun chip`() {
        val gruppo = ChipGroup(tema)
        gruppo.setRoles(emptyList())
        assertEquals(0, gruppo.childCount)
    }

    // I chip dei ruoli: solo bordo e testo, lo stesso per ogni categoria. Prima rosa, ciano, giallo e verde.
    @Test
    fun `i chip dei ruoli sono neutri e si leggono su ogni categoria`() {
        val gruppo = ChipGroup(tema)
        gruppo.setRoles(
            listOf(
                Role(1, "Centravanti", "ATTACCO"),
                Role(2, "Mediano", "CENTROCAMPO"),
                Role(3, "Libero", "DIFESA"),
                Role(4, "Portiere", "PORTA"),
            ),
        )
        assertEquals(4, gruppo.childCount)
        val sfondo = base.getColor(R.color.elite_surface)
        val testi = mutableSetOf<Int>()
        for (i in 0 until gruppo.childCount) {
            val chip = gruppo.getChildAt(i) as Chip
            testi.add(chip.currentTextColor)
            leggibile("chip $i", chip.currentTextColor, sfondo)
            assertEquals("il chip non e' dipinto", 0, chip.chipBackgroundColor!!.defaultColor ushr 24)
        }
        assertEquals("tutte le categorie hanno lo stesso colore di testo", 1, testi.size)
    }

    // Il tasto "aggiungi" e' lime con l'icona sul fondo: l'unico primario dell'elenco, mai rosa.
    @Test
    fun `l'icona del FAB dei giocatori passa AA sul lime`() {
        val schermo = LayoutInflater.from(tema).inflate(R.layout.activity_players_management, FrameLayout(tema), false)
        val fab = schermo.findViewById<FloatingActionButton>(R.id.add_player_fab)

        val icona = fab.imageTintList!!.defaultColor
        val fondo = fab.backgroundTintList!!.defaultColor
        assertEquals(base.getColor(R.color.elite_lime), fondo)
        leggibile("icona del FAB", icona, fondo)
    }

    private fun rapporto(
        colore1: Int,
        colore2: Int,
    ): MatchReportData =
        MatchReportData(
            team1Name = "BLU",
            team1Score = 10,
            team1Color = colore1,
            team1Players = emptyList(),
            team2Name = "GIALLI",
            team2Score = 8,
            team2Color = colore2,
            team2Players = emptyList(),
            matchEvents = emptyList(),
        )

    // Il blu #0D47A1 come testo su #121212 faceva 2,17:1. Ora il colore e' la banda e il testo e' TeamInk.
    @Test
    fun `nel PDF ogni squadra e' una banda con nome e punteggio leggibili`() {
        val vista = MatchReportUtils.buildReportView(tema, rapporto(bluNotte, giallo), attributesScorer = true)

        val bande =
            listOf(
                Triple(R.id.pdf_team1_band, bluNotte, listOf(R.id.pdf_team1_name, R.id.pdf_team1_score)),
                Triple(R.id.pdf_team2_band, giallo, listOf(R.id.pdf_team2_name, R.id.pdf_team2_score)),
            )
        for ((bandaId, colore, testi) in bande) {
            assertEquals(colore, sfondoPieno(vista.findViewById(bandaId)))
            for (id in testi) {
                val inchiostro = vista.findViewById<TextView>(id).currentTextColor
                assertEquals(TeamInk.on(colore), inchiostro)
                assertTrue(TeamInk.contrast(inchiostro, colore) >= 4.5)
            }
        }
        assertEquals("BLU", vista.findViewById<TextView>(R.id.pdf_team1_name).text.toString())
        assertEquals("10", vista.findViewById<TextView>(R.id.pdf_team1_score).text.toString())
    }

    @Test
    fun `nel PDF senza colore la banda ripiega su giallo e verde`() {
        val senzaColori = rapporto(0, 0).copy(team1Color = null, team2Color = null)
        val vista = MatchReportUtils.buildReportView(tema, senzaColori, attributesScorer = true)

        assertEquals(base.getColor(R.color.team_spray_yellow), sfondoPieno(vista.findViewById(R.id.pdf_team1_band)))
        assertEquals(base.getColor(R.color.team_electric_green), sfondoPieno(vista.findViewById(R.id.pdf_team2_band)))
    }
}
