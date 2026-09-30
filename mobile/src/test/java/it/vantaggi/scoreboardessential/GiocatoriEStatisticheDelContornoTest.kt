package it.vantaggi.scoreboardessential

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.card.MaterialCardView
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

    // Podio: la card gialla col rank in ciano faceva 1,09:1.
    @Test
    fun `il podio delle statistiche e' rosa pieno con testo nero`() {
        val vista = LayoutInflater.from(tema).inflate(R.layout.item_player_stat, FrameLayout(tema), false)
        val riga = StatisticsAdapter.ViewHolder(vista)
        riga.bind(PlayerStatsDTO(playerId = 1, playerName = "Mario", goals = 12, appearances = 15, winRate = 0.6f), 0)

        val scheda = vista.findViewById<MaterialCardView>(R.id.card_player_stat)
        val rosa = base.getColor(R.color.graffiti_pink)
        assertEquals(rosa, scheda.cardBackgroundColor.defaultColor)
        for (id in listOf(R.id.text_rank, R.id.text_player_name, R.id.text_goals, R.id.text_appearances)) {
            val inchiostro = vista.findViewById<TextView>(id).currentTextColor
            leggibile("testo $id", inchiostro, rosa)
        }
    }

    // Fuori dal podio i gol non sono verdi: il verde e' il colore della squadra 2, non un evidenziatore.
    @Test
    fun `fuori dal podio i gol sono chiari`() {
        val vista = LayoutInflater.from(tema).inflate(R.layout.item_player_stat, FrameLayout(tema), false)
        StatisticsAdapter.ViewHolder(vista).bind(PlayerStatsDTO(2, "Luca", 4, 10, 0.5f), 1)

        assertEquals(base.getColor(R.color.stencil_white), vista.findViewById<TextView>(R.id.text_goals).currentTextColor)
    }

    // Le iniziali erano #E0E0E0 su dodici colori pastello: fino a 1,04:1.
    @Test
    fun `le iniziali dell'avatar si leggono su qualsiasi colore dell'elenco`() {
        val colori = base.resources.getIntArray(R.array.avatar_colors)
        val visti = mutableSetOf<Int>()
        // Nomi diversi pescano colori diversi dall'elenco: se ne provano abbastanza da coprirlo.
        for (n in 0 until 400) {
            val vista = LayoutInflater.from(tema).inflate(R.layout.item_player_management, FrameLayout(tema), false)
            PlayersManagementAdapter.PlayerViewHolder(vista, {}, {}).bind(giocatore("Giocatore $n"))
            val sfondo = vista.findViewById<MaterialCardView>(R.id.player_avatar_card).cardBackgroundColor.defaultColor
            val iniziali = vista.findViewById<TextView>(R.id.player_avatar).currentTextColor
            visti.add(sfondo)
            leggibile("$n: iniziali su ${Integer.toHexString(sfondo)}", iniziali, sfondo)
        }
        assertEquals("la prova non ha coperto tutti i colori", colori.toSet(), visti)
    }

    // I chip dei ruoli: #E0E0E0 su rosa, ciano, giallo e verde faceva 3,17 / 1,17 / 1,07 / 1,01.
    @Test
    fun `i chip dei ruoli si leggono su ogni categoria`() {
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
        for (i in 0 until gruppo.childCount) {
            val chip = gruppo.getChildAt(i) as Chip
            val sfondo = chip.chipBackgroundColor!!.defaultColor
            leggibile("chip $i", chip.currentTextColor, sfondo)
        }
    }

    // Il FAB giocatori: icona #E0E0E0 sul rosa faceva 3,17:1.
    @Test
    fun `l'icona del FAB dei giocatori passa AA sul rosa`() {
        val schermo = LayoutInflater.from(tema).inflate(R.layout.activity_players_management, FrameLayout(tema), false)
        val fab = schermo.findViewById<FloatingActionButton>(R.id.add_player_fab)

        val icona = fab.imageTintList!!.defaultColor
        val fondo = fab.backgroundTintList!!.defaultColor
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
