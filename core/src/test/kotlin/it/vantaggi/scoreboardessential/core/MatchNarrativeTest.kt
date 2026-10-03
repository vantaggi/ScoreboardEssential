package it.vantaggi.scoreboardessential.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le righe del registro a game sono una funzione del registro del motore: i casi sono quelli di
 * `RacketRulesTest` (le stesse sequenze di lati) e la partita a tre set di
 * `scoreboard/v1-tre-set.json`, dove game e set devono tornare con quelli di `MatchStats`.
 */
class MatchNarrativeTest {
    private val treSet = ScoreboardFixture.load("v1-tre-set.json")

    private fun engine(
        config: SportConfig,
        sides: List<Int>,
    ): MatchEngine {
        val engine = MatchEngine(RacketRules(config = config))
        sides.forEach { engine.apply(ScoringEvent.Point(it)) }
        return engine
    }

    private fun narrative(engine: MatchEngine): MatchNarrative = checkNotNull(MatchNarrative.of(engine))

    // --- le sequenze di RacketRulesTest ---------------------------------------------------------

    @Test
    fun `dodici game alternati danno dodici righe con vincitore e punteggio dopo il game`() {
        val righe = narrative(engine(SportConfig(), sixAll)).games

        assertEquals(12, righe.size)
        assertEquals(List(12) { if (it % 2 == 0) 1 else 2 }, righe.map { it.winner })
        assertEquals(
            listOf(
                listOf(1, 0),
                listOf(1, 1),
                listOf(2, 1),
                listOf(2, 2),
                listOf(3, 2),
                listOf(3, 3),
                listOf(4, 3),
                listOf(4, 4),
                listOf(5, 4),
                listOf(5, 5),
                listOf(6, 5),
                listOf(6, 6),
            ),
            righe.map { it.gamesAfter },
        )
        assertEquals(List(12) { it }, righe.map { it.index })
        assertTrue(righe.none { it.closesSet })
    }

    @Test
    fun `un game ancora aperto non e' una riga`() {
        assertTrue(narrative(engine(SportConfig(), listOf(1, 1, 2, 1))).games.isEmpty())
        // Il quarto punto di fila chiude il game: la riga nasce solo allora.
        assertEquals(1, narrative(engine(SportConfig(), listOf(1, 1, 2, 1, 1, 1, 1))).games.size)
    }

    @Test
    fun `il tie-break e' una riga col suo punteggio e chiude il set e la partita`() {
        val engine = engine(SportConfig(), sixAll + alternate(10) + taps(1, 2))
        val righe = narrative(engine).games

        assertEquals(13, righe.size)
        val tieBreak = righe.last()
        assertEquals(GameOutcome.TIE_BREAK, tieBreak.outcome)
        assertEquals(listOf(7, 5), tieBreak.tieBreakScore)
        assertEquals(listOf(7, 6), tieBreak.gamesAfter)
        assertEquals(1, tieBreak.winner)
        assertTrue(tieBreak.closesSet)
        assertEquals(listOf(1, 0), tieBreak.setsAfter)
        assertTrue(tieBreak.closesMatch)
        // I game prima non hanno tie-break.
        assertTrue(righe.dropLast(1).all { it.tieBreakScore == null && it.outcome != GameOutcome.TIE_BREAK })
    }

    @Test
    fun `il tie-break si vince 8-6 e la riga lo dice`() {
        val righe = narrative(engine(SportConfig(), sixAll + alternate(12) + taps(1, 2))).games

        assertEquals(listOf(8, 6), righe.last().tieBreakScore)
        assertEquals(listOf(7, 6), righe.last().gamesAfter)
    }

    @Test
    fun `un tie-break ancora aperto non e' una riga`() {
        val righe = narrative(engine(SportConfig(), sixAll + alternate(12) + taps(1, 1))).games

        assertEquals(12, righe.size)
        assertEquals(listOf(6, 6), righe.last().gamesAfter)
    }

    @Test
    fun `col punteggio a game un tocco e' un game e il set si chiude al sesto`() {
        val config = SportConfig(mode = ScoringMode.GAMES, sets = 3)
        val righe = narrative(engine(config, taps(1, 12))).games

        assertEquals(12, righe.size)
        assertEquals(listOf(5, 11), righe.filter { it.closesSet }.map { it.index })
        assertEquals(listOf(6, 0), righe[5].gamesAfter)
        assertEquals(listOf(1, 0), righe[5].setsAfter)
        assertEquals(listOf(2, 0), righe[11].setsAfter)
        assertEquals(listOf(11), righe.filter { it.closesMatch }.map { it.index })
        // Un tocco e' un game intero: niente tenuto ne' break.
        assertTrue(righe.all { it.outcome == GameOutcome.UNKNOWN })
        assertEquals(listOf(0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1), righe.map { it.set })
    }

    // --- tenuto o break: solo quando il lato al servizio e' noto ------------------------------------

    @Test
    fun `con l'ordine di servizio ogni game e' tenuto o un break`() {
        val config = SportConfig(serveOrder = listOf(1, 2, 3, 4))
        // Serve il lato 1 e perde: break. Poi serve il lato 2 e tiene, poi serve di nuovo il lato 1 e perde.
        val righe = narrative(engine(config, taps(2, 4) + taps(2, 4) + taps(2, 4))).games

        assertEquals(listOf(GameOutcome.BROKEN, GameOutcome.HELD, GameOutcome.BROKEN), righe.map { it.outcome })
        assertEquals(listOf(1, 2, 1), righe.map { it.servingSide })
    }

    @Test
    fun `senza ordine di servizio nel padel non si sa chi serviva`() {
        val righe = narrative(engine(SportConfig(), taps(2, 4))).games

        assertEquals(GameOutcome.UNKNOWN, righe.single().outcome)
        assertNull(righe.single().servingSide)
    }

    @Test
    fun `nel tennis singolare il lato al servizio viene dall'alternanza`() {
        val engine = MatchEngine(SportRegistry.byId(SportRegistry.TENNIS))
        (taps(2, 4) + taps(1, 4)).forEach { engine.apply(ScoringEvent.Point(it)) }
        val righe = narrative(engine).games

        // Serve il lato 1 il primo game, e lo perde: break. Il secondo lo serve il lato 2 e lo perde.
        assertEquals(listOf(GameOutcome.BROKEN, GameOutcome.BROKEN), righe.map { it.outcome })
        assertEquals(listOf(1, 2), righe.map { it.servingSide })
    }

    // --- il registro e' la sorgente: annullare, ripristinare, eventi inerti ------------------------

    @Test
    fun `annullare il punto che chiude un game toglie la riga e riapre il game`() {
        val engine = engine(SportConfig(), taps(1, 4) + taps(2, 4))
        assertEquals(2, narrative(engine).games.size)

        engine.undo()

        val righe = narrative(engine).games
        assertEquals(1, righe.size)
        assertEquals(listOf(1, 0), righe.single().gamesAfter)
    }

    @Test
    fun `la riga dopo un ripristino e' quella di prima`() {
        val live = engine(SportConfig(), sixAll + alternate(10) + taps(1, 2))
        val ripreso = MatchEngine(RacketRules(config = SportConfig()))
        ripreso.restoreLog(live.log)

        assertEquals(narrative(live), narrative(ripreso))
    }

    @Test
    fun `lastLogIndex e' la posizione nel registro anche con eventi inerti in mezzo`() {
        val rules = RacketRules(config = SportConfig())
        val voci =
            listOf<ScoringEvent>(ScoringEvent.Correction(side = 1)) + // senza effetto a 0-0
                taps(1, 4).map { ScoringEvent.Point(it) } +
                listOf(ScoringEvent.Correction(side = 2)) + // inerte nel padel
                taps(2, 4).map { ScoringEvent.Point(it) }
        val engine = MatchEngine(rules).apply { restore(voci) }

        val righe = narrative(engine).games

        assertEquals(listOf(4, 9), righe.map { it.lastLogIndex })
        righe.forEach { assertEquals(it.winner, engine.log[it.lastLogIndex].event.side) }
    }

    @Test
    fun `il calcio non ha righe di game`() {
        assertNull(MatchNarrative.of(MatchEngine(SportRegistry.byId(SportRegistry.FOOTBALL))))
    }

    // --- v1-tre-set: game e set devono tornare con MatchStats ---------------------------------

    @Test
    fun `tre set, le righe tornano con i game e i set di MatchStats`() {
        val engine = treSet.engine()
        val righe = narrative(engine).games
        val stats = checkNotNull(MatchStats.of(engine))

        assertEquals(34, righe.size)
        assertEquals(stats.games.size, righe.size)
        assertEquals(stats.games.map { it.winner }, righe.map { it.winner })
        assertEquals(stats.games.map { it.gamesAfter }, righe.map { it.gamesAfter })
        assertEquals(stats.games.map { it.set }, righe.map { it.set })
        assertEquals(listOf(17, 17), listOf(1, 2).map { lato -> righe.count { it.winner == lato } })

        // I set: la riga che chiude e' quella del game che chiude, coi game finali del set.
        val chiusure = righe.filter { it.closesSet }
        assertEquals(treSet.setScores, chiusure.map { it.gamesAfter })
        assertEquals(stats.sets.map { it.winner }, chiusure.map { it.winner })
        assertEquals(listOf(listOf(1, 0), listOf(1, 1), listOf(2, 1)), chiusure.map { it.setsAfter })
        assertEquals(listOf(righe.last()), righe.filter { it.closesMatch })
        assertEquals(treSet.winnerTeam, righe.last().winner)

        // Il tie-break del primo set, 7-5, e' l'unico.
        assertEquals(listOf(listOf(7, 5)), righe.mapNotNull { it.tieBreakScore })
        assertEquals(stats.sets[0].tieBreak, righe.single { it.tieBreakScore != null }.tieBreakScore)
    }

    @Test
    fun `tre set, tenuti e break tornano con il servizio di MatchStats`() {
        val righe = narrative(treSet.engine()).games
        val serve = checkNotNull(checkNotNull(MatchStats.of(treSet.engine())).serve)

        for (lato in 1..2) {
            val serviti = righe.filter { it.servingSide == lato && it.outcome != GameOutcome.TIE_BREAK }
            assertEquals(serve.bySide[lato - 1].games, serviti.size)
            assertEquals(serve.bySide[lato - 1].held, serviti.count { it.outcome == GameOutcome.HELD })
        }
        // 16 game serviti dal lato 1 con 10 tenuti, 17 dal lato 2 con 11: la tabella della Cronaca.
        assertEquals(6, righe.count { it.outcome == GameOutcome.BROKEN && it.servingSide == 1 })
        assertEquals(6, righe.count { it.outcome == GameOutcome.BROKEN && it.servingSide == 2 })
        assertEquals(1, righe.count { it.outcome == GameOutcome.TIE_BREAK })
    }

    @Test
    fun `tre set, il punto che chiude ogni riga e' davvero quello del registro`() {
        val engine = treSet.engine()
        val righe = narrative(engine).games
        val rules = engine.rules

        righe.forEach { riga ->
            // Rigiocando il registro fino a quel punto, i game giocati sono esattamente quelli della riga.
            val stato = engine.log.take(riga.lastLogIndex + 1).fold(rules.initial()) { s, v -> rules.apply(s, v.event) } as RacketScore
            val giocati = stato.gamesInSet.sum() + stato.closedSets.sumOf { it.games.sum() }
            assertEquals(riga.index + 1, giocati)
            assertEquals(riga.winner, engine.log[riga.lastLogIndex].event.side)
        }
        assertFalse(righe.isEmpty())
    }

    private companion object {
        fun taps(
            side: Int,
            n: Int,
        ): List<Int> = List(n) { side }

        fun alternate(n: Int): List<Int> = List(n) { if (it % 2 == 0) 1 else 2 }

        val sixAll: List<Int> = (1..6).flatMap { List(4) { 1 } + List(4) { 2 } }
    }
}
