package it.vantaggi.scoreboardessential

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.vantaggi.scoreboardessential.domain.models.MatchEvent
import it.vantaggi.scoreboardessential.domain.models.MatchEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
