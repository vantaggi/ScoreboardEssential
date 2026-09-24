package it.vantaggi.scoreboardessential.ui.chronicle

import android.content.res.Resources
import it.vantaggi.scoreboardessential.core.KeyMoment
import it.vantaggi.scoreboardessential.core.SetComeback
import it.vantaggi.scoreboardessential.core.Streak
import it.vantaggi.scoreboardessential.core.TeamInk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Le frasi dei momenti chiave, scritte dal telefono dai dati di MatchStats. In italiano devono
 * dire esattamente cio' che dice la dashboard di Padel Elite (`riferimento-match-log.js`,
 * `moments`): chi guarda la stessa partita sui due lati legge la stessa cronaca.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "it")
class ChronicleTextTest {
    private val res: Resources get() = RuntimeEnvironment.getApplication().resources
    private val teams = listOf("Rossi", "Blu")

    private fun frase(m: KeyMoment) = ChronicleText.moment(res, m, teams)

    @Test
    fun `match point annullati, uno e piu' di uno`() {
        assertEquals("Rossi annullano 2 match point", frase(KeyMoment.MatchPointsSaved(side = 1, count = 2)))
        assertEquals("Blu annullano un match point", frase(KeyMoment.MatchPointsSaved(side = 2, count = 1)))
    }

    @Test
    fun `partita ribaltata e rimonta nel set, con il set contato da 1`() {
        assertEquals("Blu vincono dopo aver perso il primo set", frase(KeyMoment.MatchTurnedAround(side = 2)))
        assertEquals(
            "Rimonta nel 3° set: Rossi da 2-5 a 7-5",
            frase(KeyMoment.SetTurnedAround(SetComeback(set = 2, side = 1, from = listOf(2, 5), to = listOf(7, 5)))),
        )
    }

    @Test
    fun `tie-break e striscia`() {
        assertEquals("Tie-break del 1° set a Rossi, 7-5", frase(KeyMoment.TieBreakWon(set = 0, side = 1, score = listOf(7, 5))))
        assertEquals("6 punti di fila per Blu nel 2° set", frase(KeyMoment.LongStreak(Streak(side = 2, length = 6, set = 1))))
    }

    @Test
    fun `set point annullati, uno e piu' di uno`() {
        assertEquals("Rossi annullano 3 set point", frase(KeyMoment.SetPointsSaved(side = 1, count = 3)))
        assertEquals("Blu annullano un set point", frase(KeyMoment.SetPointsSaved(side = 2, count = 1)))
    }

    /** Il conto e' del lato che ne ha vinti di piu', anche quando e' il lato 2. */
    @Test
    fun `punti decisivi, con un lato avanti o pari`() {
        assertEquals("Punti decisivi: Rossi ne vincono 3 su 5", frase(KeyMoment.DecidingPoints(5, listOf(3, 2), leader = 1)))
        assertEquals("Punti decisivi: Blu ne vincono 3 su 5", frase(KeyMoment.DecidingPoints(5, listOf(2, 3), leader = 2)))
        assertEquals("Punti decisivi: 2 a testa su 4", frase(KeyMoment.DecidingPoints(4, listOf(2, 2), leader = null)))
    }

    @Test
    @Config(qualifiers = "en")
    fun `in inglese le stesse frasi hanno le loro parole`() {
        assertEquals("Blu save a match point", frase(KeyMoment.MatchPointsSaved(side = 2, count = 1)))
        assertEquals("Rossi save 2 match points", frase(KeyMoment.MatchPointsSaved(side = 1, count = 2)))
        assertEquals("Set 1: tie-break to Rossi, 7-5", frase(KeyMoment.TieBreakWon(set = 0, side = 1, score = listOf(7, 5))))
        assertEquals("Deciding points: 4, shared 2-2", frase(KeyMoment.DecidingPoints(4, listOf(2, 2), leader = null)))
    }

    /** La barretta colorata va al lato del momento; i punti decisivi pari non sono di nessuno. */
    @Test
    fun `il lato di ogni momento`() {
        assertEquals(2, ChronicleText.side(KeyMoment.LongStreak(Streak(side = 2, length = 5, set = 0))))
        assertEquals(1, ChronicleText.side(KeyMoment.SetTurnedAround(SetComeback(0, 1, listOf(2, 5), listOf(7, 5)))))
        assertNull(ChronicleText.side(KeyMoment.DecidingPoints(2, listOf(1, 1), leader = null)))
    }

    /**
     * Barrette e linea dell'andamento stanno sulla card #1E1E1E, non sul nero: il blu notte va
     * schiarito fino a 3:1 contro la card; il giallo, che li' ha gia' contrasto, resta il suo.
     */
    @Test
    fun `il colore di squadra come grafica regge sulla card`() {
        val card = 0xFF1E1E1E.toInt()
        val bluNotte = 0xFF1A237E.toInt()
        val giallo = 0xFFFFD600.toInt()

        val schiarito = ChronicleText.graphicOn(bluNotte, card)

        assertTrue("contrasto ${TeamInk.contrast(schiarito, card)}", TeamInk.contrast(schiarito, card) >= 3.0)
        assertEquals(giallo, ChronicleText.graphicOn(giallo, card))
    }

    /** Arrotondate come `formatDuration` della dashboard. */
    @Test
    fun `durate come nella dashboard`() {
        assertEquals("45 s", ChronicleText.duration(res, 45_000))
        assertEquals("59 s", ChronicleText.duration(res, 59_400))
        assertEquals("1 min", ChronicleText.duration(res, 59_600))
        assertEquals("38 min", ChronicleText.duration(res, 38 * 60_000L))
        assertEquals("1 h 05 min", ChronicleText.duration(res, 65 * 60_000L))
        assertEquals("1 h 12 min", ChronicleText.duration(res, 72 * 60_000L))
        assertEquals("—", ChronicleText.duration(res, null))
    }
}
