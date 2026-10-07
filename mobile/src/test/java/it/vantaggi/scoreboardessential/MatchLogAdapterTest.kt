package it.vantaggi.scoreboardessential

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.vantaggi.scoreboardessential.core.GameLine
import it.vantaggi.scoreboardessential.core.GameOutcome
import it.vantaggi.scoreboardessential.core.TeamInk
import it.vantaggi.scoreboardessential.domain.models.MatchEvent
import it.vantaggi.scoreboardessential.domain.models.MatchEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Un punto del padel non e' un gol. Visto su emulatore: la riga diceva "GOAL! Team 1 - tap to add
 * the scorer" e toccarla apriva la scelta di un marcatore in uno sport che non ne ha.
 */
@RunWith(AndroidJUnit4::class)
class MatchLogAdapterTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.setTheme(R.style.Theme_ScoreboardEssential)
    }

    private fun rigaDiUnPuntoSenzaMarcatore(adapter: MatchLogAdapter): View {
        adapter.submitList(
            listOf(MatchEvent("00:00", "Goal", team = 1, player = "Team 1", type = MatchEventType.SCORE, engineIndex = 0)),
        )
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.onBindViewHolder(holder, 0)
        return holder.itemView
    }

    @Test
    fun `in padel la riga dice punto e non si offre al tocco`() {
        val adapter = MatchLogAdapter().apply { attribuisceMarcatore = false }

        val riga = rigaDiUnPuntoSenzaMarcatore(adapter)

        assertEquals(
            context.getString(R.string.log_point, "Team 1"),
            riga.findViewById<TextView>(R.id.event_description).text.toString(),
        )
        assertFalse("senza marcatori non c'e' niente da scegliere", riga.isClickable)
    }

    @Test
    fun `nel calcio la riga chiede il marcatore`() {
        val riga = rigaDiUnPuntoSenzaMarcatore(MatchLogAdapter())

        assertEquals(
            context.getString(R.string.log_goal_unattributed, "Team 1"),
            riga.findViewById<TextView>(R.id.event_description).text.toString(),
        )
        assertTrue(riga.isClickable)
    }

    private fun testoDellaRiga(
        adapter: MatchLogAdapter,
        evento: MatchEvent,
    ): TextView {
        adapter.submitList(listOf(evento))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.onBindViewHolder(holder, 0)
        return holder.itemView.findViewById(R.id.event_description)
    }

    // Prima il testo di un punto prendeva il colore della squadra: col blu notte #1A237E su
    // #1E1E1E faceva 1,26:1 e la riga spariva. Il colore ora sta solo sulla barretta.
    @Test
    fun `la riga di un punto resta leggibile e il colore va sulla barretta`() {
        val bluNotte = 0xFF1A237E.toInt()
        val adapter = MatchLogAdapter().apply { team2Color = bluNotte }
        adapter.submitList(
            listOf(MatchEvent("1'", "Goal", team = 2, player = "Team 2", type = MatchEventType.SCORE, engineIndex = 0)),
        )
        val holder = adapter.onCreateViewHolder(FrameLayout(context), 0)
        adapter.onBindViewHolder(holder, 0)
        val testo = holder.itemView.findViewById<TextView>(R.id.event_description)
        val barretta = holder.itemView.findViewById<View>(R.id.team_indicator)

        // Il testo viene dal tema (colorOnSurface) e la riga sta sulla card del foglio (elite_surface).
        val fondo = context.getColor(R.color.elite_surface)
        assertEquals(context.getColor(R.color.elite_text_primary), testo.currentTextColor)
        assertTrue(TeamInk.contrast(testo.currentTextColor, fondo) >= 4.5)
        assertEquals(bluNotte, (barretta.background as ColorDrawable).color)
    }

    @Test
    fun `una riga informativa non prende il colore della squadra`() {
        val coloreSquadra1 = 0xFF123456.toInt()
        val adapter = MatchLogAdapter().apply { team1Color = coloreSquadra1 }

        val testo = testoDellaRiga(adapter, MatchEvent("00:00", "Match started", team = 1))

        assertNotEquals(coloreSquadra1, testo.currentTextColor)
    }

    // --- la riga di un game (padel e tennis, passo 15) ----------------------------------------------

    private fun game(
        winner: Int = 1,
        gamesAfter: List<Int> = listOf(3, 2),
        outcome: GameOutcome = GameOutcome.UNKNOWN,
        tieBreakScore: List<Int>? = null,
        closesSet: Boolean = false,
        closesMatch: Boolean = false,
    ) = GameLine(
        index = 4,
        set = 0,
        winner = winner,
        gamesAfter = gamesAfter,
        servingSide = null,
        outcome = outcome,
        tieBreakScore = tieBreakScore,
        closesSet = closesSet,
        setsAfter = if (closesSet) listOf(1, 0) else null,
        closesMatch = closesMatch,
        lastLogIndex = 17,
    )

    private fun rigaDelGame(
        game: GameLine,
        nome: String = "Rossi",
        adapter: MatchLogAdapter = MatchLogAdapter(),
    ): View {
        adapter.submitList(
            listOf(
                MatchEvent("", "Game 5", team = game.winner, player = nome, type = MatchEventType.GAME, engineIndex = 17, game = game),
            ),
        )
        val holder = adapter.onCreateViewHolder(FrameLayout(context), adapter.getItemViewType(0))
        adapter.onBindViewHolder(holder, 0)
        return holder.itemView
    }

    private fun testo(
        riga: View,
        id: Int,
    ): TextView = riga.findViewById(id)

    @Test
    fun `la riga di un game dice chi l'ha vinto e i game del set`() {
        val riga = rigaDelGame(game(winner = 2, gamesAfter = listOf(2, 3)), nome = "Bianchi")

        assertEquals("Game Bianchi · 2-3", testo(riga, R.id.game_title).text.toString())
        assertEquals(View.GONE, testo(riga, R.id.game_detail).visibility)
        assertEquals(View.GONE, testo(riga, R.id.game_closing).visibility)
    }

    @Test
    fun `un game tenuto o un break lo dice nel dettaglio`() {
        val tenuto = rigaDelGame(game(outcome = GameOutcome.HELD))
        val rotto = rigaDelGame(game(outcome = GameOutcome.BROKEN))

        assertEquals("Held", testo(tenuto, R.id.game_detail).text.toString())
        assertEquals("Break", testo(rotto, R.id.game_detail).text.toString())
        assertEquals(View.VISIBLE, testo(rotto, R.id.game_detail).visibility)
    }

    @Test
    fun `il tie-break porta il suo punteggio e il set chiuso dice SET`() {
        val riga =
            rigaDelGame(
                game(
                    gamesAfter = listOf(7, 6),
                    outcome = GameOutcome.TIE_BREAK,
                    tieBreakScore = listOf(7, 5),
                    closesSet = true,
                ),
            )

        assertEquals("Game Rossi · 7-6", testo(riga, R.id.game_title).text.toString())
        assertEquals("Tie-break 7-5", testo(riga, R.id.game_detail).text.toString())
        assertEquals("Set Rossi · 7-6", testo(riga, R.id.game_closing).text.toString())
    }

    @Test
    fun `l'ultimo game della partita dice PARTITA`() {
        val riga = rigaDelGame(game(gamesAfter = listOf(6, 4), closesSet = true, closesMatch = true))

        assertEquals("Match Rossi", testo(riga, R.id.game_closing).text.toString())
    }

    @Test
    fun `TalkBack legge un game come una frase sola e un solo nodo`() {
        val riga = rigaDelGame(game(gamesAfter = listOf(6, 4), outcome = GameOutcome.BROKEN, closesSet = true))

        assertEquals(
            "Game won by Rossi, 6 to 4 in the set. Break of serve. Set won by Rossi, 6 to 4",
            riga.contentDescription.toString(),
        )
        assertTrue(riga.isScreenReaderFocusable)
        for (id in listOf(R.id.game_title, R.id.game_detail, R.id.game_closing)) {
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, riga.findViewById<View>(id).importantForAccessibility)
        }
        assertFalse("nel padel non c'e' niente da scegliere", riga.isClickable)
    }

    // Il titolo a schermo ha il lato 1 per primo come il tabellone; la frase di TalkBack parte da chi
    // e' nominato: «Game won by Bianchi, 2 to 3» fa pensare che Bianchi sia indietro.
    @Test
    fun `TalkBack legge il punteggio partendo dal vincitore anche quando e' il lato 2`() {
        val game = game(winner = 2, gamesAfter = listOf(4, 6), outcome = GameOutcome.BROKEN, closesSet = true)
        val riga = rigaDelGame(game, nome = "Bianchi")

        assertEquals(
            "Game won by Bianchi, 6 to 4 in the set. Break of serve. Set won by Bianchi, 6 to 4",
            riga.contentDescription.toString(),
        )
        assertEquals("Game Bianchi · 4-6", testo(riga, R.id.game_title).text.toString())
    }

    @Test
    fun `TalkBack legge il tie-break del lato 2 partendo dal vincitore`() {
        val game =
            game(
                winner = 2,
                gamesAfter = listOf(6, 7),
                outcome = GameOutcome.TIE_BREAK,
                tieBreakScore = listOf(5, 7),
                closesSet = true,
            )
        val riga = rigaDelGame(game, nome = "Bianchi")

        assertEquals(
            "Game won by Bianchi, 7 to 6 in the set. Tie-break won by Bianchi, 7 to 5. Set won by Bianchi, 7 to 6",
            riga.contentDescription.toString(),
        )
    }

    @Test
    fun `la barretta della riga di un game ha il colore di chi l'ha vinto`() {
        val verde = 0xFF00AA00.toInt()
        val adapter = MatchLogAdapter().apply { team2Color = verde }
        val riga = rigaDelGame(game(winner = 2), adapter = adapter)

        assertEquals(verde, (riga.findViewById<View>(R.id.team_indicator).background as ColorDrawable).color)
        assertEquals(context.getColor(R.color.elite_text_primary), testo(riga, R.id.game_title).currentTextColor)
    }

    @Test
    fun `un game e le altre righe hanno tipi di vista diversi e il calcio resta com'era`() {
        val adapter = MatchLogAdapter()
        adapter.submitList(
            listOf(
                MatchEvent("", "Game 1", team = 1, player = "Rossi", type = MatchEventType.GAME, engineIndex = 3, game = game()),
                MatchEvent("1'", "Goal", team = 1, player = "Rossi", type = MatchEventType.SCORE, engineIndex = 0),
                MatchEvent("", "Match started"),
            ),
        )

        assertNotEquals(adapter.getItemViewType(0), adapter.getItemViewType(1))
        assertEquals(adapter.getItemViewType(1), adapter.getItemViewType(2))
        val holder = adapter.onCreateViewHolder(FrameLayout(context), adapter.getItemViewType(1))
        adapter.onBindViewHolder(holder, 1)
        assertEquals(
            context.getString(R.string.log_goal_unattributed, "Rossi"),
            holder.itemView
                .findViewById<TextView>(R.id.event_description)
                .text
                .toString(),
        )
    }
}
