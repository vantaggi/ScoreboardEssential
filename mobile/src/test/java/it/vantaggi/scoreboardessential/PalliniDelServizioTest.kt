package it.vantaggi.scoreboardessential

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import it.vantaggi.scoreboardessential.core.RacketRules
import it.vantaggi.scoreboardessential.core.ScoreDisplay
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1 pallino o 2 pallini, senza numeri ne' nomi: quale dei due giocatori della squadra batte.
 *
 * Il layout della colonna di gioco gonfiato da solo, come in `ColonnaDiGiocoTest`: `MainActivity`
 * sotto Robolectric non si monta, ma `mostraIlServizio` lavora sulle viste e non sull'Activity.
 * Si prova anche che lo slot non cambi misura: e' quello che tiene ferma la colonna.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-w411dp-h923dp-xxhdpi")
class PalliniDelServizioTest {
    private fun gonfia(): View {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val contesto = ContextThemeWrapper(app, R.style.Theme_ScoreboardEssential)
        val radice = LayoutInflater.from(contesto).inflate(R.layout.content_scoreboard_live, null)
        radice.layoutDirection = View.LAYOUT_DIRECTION_LTR
        return radice
    }

    private fun misura(radice: View) {
        val densita = radice.resources.displayMetrics.density
        radice.measure(
            View.MeasureSpec.makeMeasureSpec((411 * densita).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((923 * densita).toInt(), View.MeasureSpec.EXACTLY),
        )
        radice.layout(0, 0, radice.measuredWidth, radice.measuredHeight)
    }

    private fun display(
        lato: Int?,
        giocatore: Int?,
    ) = ScoreDisplay(side1Primary = "0", side2Primary = "0", servingSide = lato, servingPlayerSlot = giocatore)

    /** Quanti pallini sono visibili accanto alla squadra [squadra]. */
    private fun pallini(
        radice: View,
        squadra: Int,
    ): Int {
        val ids = if (squadra == 1) listOf(R.id.team1_serve_dot, R.id.team1_serve_dot_second) else listOf(R.id.team2_serve_dot, R.id.team2_serve_dot_second)
        return ids.count { radice.findViewById<View>(it).visibility == View.VISIBLE }
    }

    @Test
    fun `batte il primo giocatore, un pallino dalla parte di chi serve`() {
        val radice = gonfia()
        mostraIlServizio(radice, display(lato = 1, giocatore = 1), "ROSSI", "BIANCHI")
        assertEquals(1, pallini(radice, 1))
        assertEquals("l'altra squadra non ne ha", 0, pallini(radice, 2))
    }

    @Test
    fun `batte il secondo giocatore, due pallini dalla parte di chi serve`() {
        val radice = gonfia()
        mostraIlServizio(radice, display(lato = 2, giocatore = 2), "ROSSI", "BIANCHI")
        assertEquals(2, pallini(radice, 2))
        assertEquals(0, pallini(radice, 1))
    }

    @Test
    fun `nel singolare c'e' sempre un pallino`() {
        val radice = gonfia()
        mostraIlServizio(radice, display(lato = 1, giocatore = null), "ROSSI", "BIANCHI")
        assertEquals(1, pallini(radice, 1))
    }

    @Test
    fun `calcio e fine partita non hanno pallini, e quelli spenti sono INVISIBLE e non GONE`() {
        val radice = gonfia()
        mostraIlServizio(radice, display(lato = 1, giocatore = 2), "ROSSI", "BIANCHI")
        mostraIlServizio(radice, display(lato = null, giocatore = null), "ROSSI", "BIANCHI")
        listOf(
            R.id.team1_serve_dot,
            R.id.team1_serve_dot_second,
            R.id.team2_serve_dot,
            R.id.team2_serve_dot_second,
        ).forEach { assertEquals(View.INVISIBLE, radice.findViewById<View>(it).visibility) }
        assertNull("niente descrizione se nessuno serve", radice.findViewById<View>(R.id.team1_serve_slot).contentDescription)
    }

    @Test
    fun `lo slot non cambia misura in nessuno stato e non sposta il nome`() {
        val radice = gonfia()
        val stati =
            listOf(
                display(null, null),
                display(1, 1),
                display(1, 2),
                display(2, 1),
                display(2, 2),
                display(1, null),
            )
        val misure =
            stati.map { stato ->
                mostraIlServizio(radice, stato, "ROSSI", "BIANCHI")
                misura(radice)
                listOf(
                    radice.findViewById<View>(R.id.team1_serve_slot).let { it.width to it.height },
                    radice.findViewById<View>(R.id.team2_serve_slot).let { it.width to it.height },
                    radice.findViewById<View>(R.id.team1_name_textview).left,
                    radice.findViewById<View>(R.id.team2_name_textview).left,
                )
            }
        assertEquals("tutti gli stati hanno le stesse misure", 1, misure.toSet().size)
        val densita = radice.resources.displayMetrics.density
        // Due pallini da 12dp e uno spazio da 4dp: lo slot e' largo 28dp anche con un pallino solo.
        assertEquals(28f, radice.findViewById<View>(R.id.team1_serve_slot).width / densita, 0.5f)
    }

    @Test
    fun `TalkBack dice chi serve, e quale giocatore`() {
        val radice = gonfia()
        val slot1 = radice.findViewById<View>(R.id.team1_serve_slot)
        mostraIlServizio(radice, display(1, 1), "ROSSI", "BIANCHI")
        assertEquals("ROSSI serving, first player", slot1.contentDescription)
        mostraIlServizio(radice, display(1, 2), "ROSSI", "BIANCHI")
        assertEquals("ROSSI serving, second player", slot1.contentDescription)
        mostraIlServizio(radice, display(1, null), "ROSSI", "BIANCHI")
        assertEquals("ROSSI serving", slot1.contentDescription)
        mostraIlServizio(radice, display(2, 2), "ROSSI", "BIANCHI")
        assertNull("la squadra che non serve non annuncia nulla", slot1.contentDescription)
        assertEquals("BIANCHI serving, second player", radice.findViewById<View>(R.id.team2_serve_slot).contentDescription)
    }

    @Test
    fun `il contenitore cliccabile del nome dice anche chi serve`() {
        val radice = gonfia()
        mostraIlServizio(radice, display(2, 2), "ROSSI", "BIANCHI")
        assertEquals("Edit the name of ROSSI", radice.findViewById<View>(R.id.team1_name_container).contentDescription)
        assertEquals(
            "Edit the name of BIANCHI. BIANCHI serving, second player",
            radice.findViewById<View>(R.id.team2_name_container).contentDescription,
        )
    }

    @Test
    @Config(qualifiers = "it-w411dp-h923dp-xxhdpi")
    fun `TalkBack in italiano`() {
        val radice = gonfia()
        mostraIlServizio(radice, display(1, 2), "ROSSI", "BIANCHI")
        assertEquals("ROSSI al servizio, secondo giocatore", radice.findViewById<View>(R.id.team1_serve_slot).contentDescription)
        mostraIlServizio(radice, display(1, 1), "ROSSI", "BIANCHI")
        assertEquals("ROSSI al servizio, primo giocatore", radice.findViewById<View>(R.id.team1_serve_slot).contentDescription)
    }

    @Test
    fun `dal motore quattro game di padel danno 1, 1, 2, 2 pallini`() {
        val padel = SportRegistry.byId(SportRegistry.PADEL) as RacketRules
        val radice = gonfia()
        var stato = padel.initial()
        val visti = mutableListOf<Int>()
        repeat(4) { game ->
            mostraIlServizio(radice, padel.display(stato), "ROSSI", "BIANCHI")
            visti += pallini(radice, 1) + pallini(radice, 2)
            // Un game a testa, alternati: quattro punti di fila al lato che lo vince.
            repeat(4) { stato = padel.apply(stato, ScoringEvent.Point(if (game % 2 == 0) 1 else 2)) }
        }
        assertEquals(listOf(1, 1, 2, 2), visti)
    }
}
