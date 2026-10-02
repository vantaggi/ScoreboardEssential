package it.vantaggi.scoreboardessential.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Quale giocatore della squadra al servizio batte: 1 pallino se il primo, 2 se il secondo.
 *
 * Il dato e' `servingPlayerSlot`, e sta nel display accanto a `servingSide`: telefono e orologio
 * disegnano un pallino per ogni unita'. La rotazione e' A1, B1, A2, B2, quindi a ogni game i
 * pallini sono 1, 1, 2, 2, e il tie-break e il set dopo il tie-break sono i casi in cui un
 * conteggio ingenuo (il game numero N) sbaglierebbe.
 */
class ServingPlayerSlotTest {
    private val ordine = listOf(11, 22, 33, 44)

    @Test
    fun `la funzione pura segue A1, B1, A2, B2`() {
        assertEquals(listOf(1, 1, 2, 2, 1, 1, 2, 2), (0..7).map { servingPlayerSlot(it) })
    }

    @Test
    fun `per quattro game i pallini seguono 1, 1, 2, 2 e il lato alterna`() {
        // In GAMES un tocco e' un game: la rotazione si legge senza giocare i punti.
        val rules = RacketRules(config = SportConfig(mode = ScoringMode.GAMES, sets = 3, serveOrder = ordine))
        var state = rules.initial()
        val slot = mutableListOf<Int?>()
        val lato = mutableListOf<Int?>()
        repeat(8) {
            val display = rules.display(state)
            slot += display.servingPlayerSlot
            lato += display.servingSide
            // Si alternano i game, cosi' il set non si chiude prima dell'ottavo.
            state = rules.apply(state, ScoringEvent.Point(if (it % 2 == 0) 1 else 2))
        }
        assertEquals(listOf<Int?>(1, 1, 2, 2, 1, 1, 2, 2), slot)
        assertEquals(listOf<Int?>(1, 2, 1, 2, 1, 2, 1, 2), lato)
    }

    @Test
    fun `il padel e' sempre in coppia, anche senza l'ordine di servizio`() {
        val padel = SportRegistry.byId(SportRegistry.PADEL) as RacketRules
        assertEquals(1, padel.display(padel.initial()).servingPlayerSlot)
        val dopoDueGame = play(padel, taps(1, 4) + taps(2, 4))
        assertEquals("terzo game: il secondo giocatore della squadra 1", 2, padel.display(dopoDueGame).servingPlayerSlot)
        assertEquals(1, padel.display(dopoDueGame).servingSide)
    }

    @Test
    fun `nel tie-break il servizio cambia dopo il primo punto e poi ogni due`() {
        val padel = SportRegistry.byId(SportRegistry.PADEL) as RacketRules
        val daSeiPari = play(padel, sixAll)
        assertEquals("dodici game: apre di nuovo il primo giocatore della squadra 1", 1, padel.display(daSeiPari).servingPlayerSlot)
        // Il punto prima di ogni scambio: A1, B1 B1, A2 A2, B2 B2, A1 A1.
        val slot = mutableListOf<Int?>()
        val lato = mutableListOf<Int?>()
        var state: ScoreState = daSeiPari
        listOf(1, 2, 1, 2, 1, 2, 1, 2).forEach { punto ->
            val display = padel.display(state)
            slot += display.servingPlayerSlot
            lato += display.servingSide
            state = padel.apply(state, ScoringEvent.Point(punto))
        }
        assertEquals(listOf<Int?>(1, 1, 1, 2, 2, 2, 2, 1), slot)
        assertEquals(listOf<Int?>(1, 2, 2, 1, 1, 2, 2, 1), lato)
    }

    @Test
    fun `dopo il tie-break il set lo apre chi ha ricevuto il primo punto, qualunque sia il punteggio`() {
        val tennis = RacketRules(config = SportRegistry.byId(SportRegistry.TENNIS).config.copy(serveOrder = ordine))
        // Il lato 1 batte il primo punto del tie-break col suo primo giocatore. Il set dopo lo
        // apre il lato 2 col primo giocatore, e il game dopo ancora tocca al secondo del lato 1.
        listOf(7 to 2, 7 to 3, 7 to 5, 8 to 6).forEach { (a, b) ->
            val chiuso = sixAll + alternate(2 * b) + taps(1, a - b)
            val primo = tennis.display(play(tennis, chiuso))
            assertEquals("$a-$b: primo game del set dopo, lato", 2, primo.servingSide)
            assertEquals("$a-$b: primo game del set dopo, giocatore", 1, primo.servingPlayerSlot)
            assertEquals("$a-$b: B1 batte", 22, primo.servingPlayerId)
            val secondo = tennis.display(play(tennis, chiuso + taps(1, 4)))
            assertEquals("$a-$b: secondo game del set dopo, lato", 1, secondo.servingSide)
            assertEquals("$a-$b: secondo game del set dopo, giocatore", 2, secondo.servingPlayerSlot)
            assertEquals("$a-$b: A2 batte", 33, secondo.servingPlayerId)
        }
    }

    @Test
    fun `il singolare non ha un secondo giocatore`() {
        val tennis = SportRegistry.byId(SportRegistry.TENNIS) as RacketRules
        var state = tennis.initial()
        repeat(6) {
            val display = tennis.display(state)
            assertNull("game $it: singolare", display.servingPlayerSlot)
            assertEquals("il lato che serve c'e' comunque", it % 2 + 1, display.servingSide)
            state = (0 until 4).fold(state) { s, _ -> tennis.apply(s, ScoringEvent.Point(1)) }
        }
    }

    @Test
    fun `il tennis con quattro giocatori nell'ordine e' in coppia, e l'orologio lo puo' forzare`() {
        val base = SportRegistry.byId(SportRegistry.TENNIS) as RacketRules
        val inCoppia = SportRegistry.forMatch(SportRegistry.TENNIS, ordine) as RacketRules
        val tutti = taps(1, 4) + taps(2, 4)
        assertNull(base.display(play(base, tutti)).servingPlayerSlot)
        assertEquals(2, inCoppia.display(play(inCoppia, tutti)).servingPlayerSlot)
        // L'orologio non ha le rose: rifa' il calcolo con le stesse regole, dette "in coppia".
        assertEquals(2, base.conIlServizioInCoppia().display(play(base, tutti)).servingPlayerSlot)
        assertNull("l'originale non cambia", base.display(play(base, tutti)).servingPlayerSlot)
    }

    @Test
    fun `a partita finita nessuno serve e nessun pallino`() {
        val rules = RacketRules(config = SportConfig(mode = ScoringMode.GAMES, serveOrder = ordine))
        val display = rules.display(play(rules, taps(1, 6)))
        assertEquals(true, display.matchOver)
        assertNull(display.servingSide)
        assertNull(display.servingPlayerSlot)
    }

    @Test
    fun `il calcio non ha ne' lato ne' giocatore al servizio`() {
        val display = FootballRules.display(FootballRules.initial())
        assertNull(display.servingSide)
        assertNull(display.servingPlayerSlot)
    }

    // --- utilita' ---------------------------------------------------------------------------

    private fun play(
        rules: RacketRules,
        sides: List<Int>,
    ): RacketScore =
        sides.fold(rules.initial()) { state, side -> rules.apply(state, ScoringEvent.Point(side)) } as RacketScore

    private companion object {
        fun taps(
            side: Int,
            n: Int,
        ): List<Int> = List(n) { side }

        fun alternate(n: Int): List<Int> = List(n) { if (it % 2 == 0) 1 else 2 }

        /** Sei game per parte con golden point: quattro punti di fila vincono un game. */
        val sixAll: List<Int> = (1..6).flatMap { List(4) { 1 } + List(4) { 2 } }
    }
}
