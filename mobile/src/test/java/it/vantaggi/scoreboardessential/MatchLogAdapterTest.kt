package it.vantaggi.scoreboardessential

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

        val fondo = context.getColor(R.color.concrete_gray)
        assertEquals(context.getColor(R.color.stencil_white), testo.currentTextColor)
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
}
