package it.vantaggi.scoreboardessential.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La Cronaca calcolata dall'app deve dare gli stessi numeri di quella della dashboard. I valori
 * attesi della partita a tre set sono quelli della sezione 3 di
 * `docs/dashboard/SCOREBOARD_CRONACA_APP.md`, scritti dalla dashboard: non si adattano.
 */
class MatchStatsTest {
    private val treSet = ScoreboardFixture.load("v1-tre-set.json")
    private val interrotta = ScoreboardFixture.load("app-reale-interrotta.json")

    private fun stats(fixture: ScoreboardFixture): MatchStats = checkNotNull(MatchStats.of(fixture.engine()))

    // --- la partita a tre set: la tabella della sezione 3 --------------------------------------

    @Test
    fun treSet_setEVincitore() {
        val s = stats(treSet)
        assertEquals(189, s.points.size)
        assertEquals(listOf(listOf(7, 6), listOf(3, 6), listOf(7, 5)), s.sets.map { it.games })
        assertEquals(listOf(7, 5), s.sets[0].tieBreak)
        assertNull(s.sets[1].tieBreak)
        assertNull(s.sets[2].tieBreak)
        assertTrue(s.ended)
        assertEquals(1, s.winnerTeam)
        assertNull(s.current)
        // Il file dice lo stesso: i due motori sono d'accordo sul risultato.
        assertEquals(treSet.setScores, s.sets.map { it.games })
        assertEquals(treSet.winnerTeam, s.winnerTeam)
    }

    @Test
    fun treSet_gameTotali() {
        assertEquals(listOf(17, 17), stats(treSet).gamesWon)
    }

    @Test
    fun treSet_gameServitiETenuti() {
        val serve = checkNotNull(stats(treSet).serve)
        assertEquals(16, serve.bySide[0].games)
        assertEquals(10, serve.bySide[0].held)
        assertEquals(17, serve.bySide[1].games)
        assertEquals(11, serve.bySide[1].held)
        assertEquals(33, serve.byPlayer.sumOf { it.line.games })
        assertEquals(21, serve.byPlayer.sumOf { it.line.held })
        assertEquals(listOf(1, 2, 3, 4), serve.byPlayer.map { it.playerId })
    }

    @Test
    fun treSet_breakConvertiti() {
        val s = stats(treSet)
        assertEquals(listOf(6, 6), s.breaks.converted)
        // Stesso numero visto dai game: un break e' un game perso da chi serviva.
        assertEquals(6, s.games.count { it.hold == false && it.servingSide == 1 })
        assertEquals(6, s.games.count { it.hold == false && it.servingSide == 2 })
    }

    @Test
    fun treSet_puntiDecisivi() {
        val deciding = stats(treSet).deciding
        assertEquals(5, deciding.played)
        assertEquals(listOf(3, 2), deciding.won)
    }

    /** Sul 2-5 del terzo set: prima sul 40-30 per il lato 2, poi sul punto secco del 40-40. */
    @Test
    fun treSet_matchPointAnnullati() {
        val s = stats(treSet)
        assertEquals(listOf(2, 0), s.matchPointsSaved)
        val saved = s.points.filter { it.side == 1 && it.matchPointFor[1] }
        assertEquals(2, saved.size)
        assertTrue(saved.all { it.set == 2 && it.game == saved[0].game })
        assertEquals(listOf(2, 5), s.games[saved[0].game - 1].gamesAfter)
        assertFalse(saved[0].deciding)
        assertTrue(saved[1].deciding)
        assertEquals(s.points.indexOf(saved[0]) + 1, s.points.indexOf(saved[1]))
    }

    @Test
    fun treSet_setPointAnnullati() {
        assertEquals(listOf(0, 0), stats(treSet).setPointsSaved)
    }

    @Test
    fun treSet_rimonte() {
        val s = stats(treSet)
        assertEquals(listOf(SetComeback(set = 2, side = 1, from = listOf(2, 5), to = listOf(7, 5))), s.comebacks)
        assertFalse(s.matchTurnedAround)
    }

    // --- la partita a tre set: il resto -------------------------------------------------------

    /** Il servitore ricavato dal replay e' quello che l'app aveva scritto nel file. */
    @Test
    fun treSet_ilServitoreCoincideConQuelloDelFile() {
        assertEquals(treSet.servers, stats(treSet).points.map { it.server })
    }

    @Test
    fun treSet_andamentoCumulato() {
        val s = stats(treSet)
        var diff = 0
        val expected = treSet.sides.map { side -> if (side == 1) ++diff else --diff }
        assertEquals(expected, s.points.map { it.diff })
        assertEquals(s.pointsWon[0] - s.pointsWon[1], s.points.last().diff)
        assertEquals(189, s.pointsWon.sum())
    }

    /** Il primo game: 6 punti fino a 169 s; il secondo comincia a 261 s, e la pausa non e' gioco. */
    @Test
    fun treSet_ilGameVaDalPrimoAllUltimoPunto() {
        val games = stats(treSet).games
        val first = games[0]
        assertEquals(0, first.firstPoint)
        assertEquals(5, first.lastPoint)
        assertEquals(169_000L, first.durationMs)
        assertEquals(1, first.server)
        assertEquals(true, first.hold)
        assertEquals(listOf(4, 2), first.score)
        // Il secondo: dal punto a 261 s all'ultimo a 383 s, tenuto da Anna (lato 2).
        val second = games[1]
        assertEquals(6, second.firstPoint)
        assertEquals(122_000L, second.durationMs)
        assertEquals(2, second.server)
        assertEquals(true, second.hold)
    }

    @Test
    fun treSet_tempi() {
        val s = stats(treSet)
        val times = checkNotNull(s.times)
        val at = treSet.atMillis.map { it!! }
        assertEquals(at.last() - at.first(), times.totalMs)
        assertEquals(3, times.setDurationsMs.size)
        assertEquals(times.totalMs, times.setDurationsMs.sum())
        assertEquals(Math.round((at.last() - at.first()).toDouble() / 188), times.avgPointMs)
        val normal = s.games.filter { !it.tieBreak }
        assertEquals(Math.round(normal.sumOf { it.durationMs!! }.toDouble() / normal.size), times.avgGameMs)
        assertEquals(normal.maxOf { it.durationMs!! }, times.longestGame!!.durationMs)
        assertFalse(times.longestGame!!.tieBreak)
    }

    /** Il tie-break e' un game, ma non ha un servitore che lo "tiene". */
    @Test
    fun treSet_ilTieBreakNonETenutoNeBreak() {
        val tb = stats(treSet).games.single { it.tieBreak }
        assertNull(tb.hold)
        assertEquals(listOf(7, 5), tb.score)
        assertEquals(listOf(7, 6), tb.gamesAfter)
    }

    @Test
    fun treSet_strisceContateAParte() {
        val s = stats(treSet)
        for (side in 1..2) {
            var best = 0
            var run = 0
            for (winner in treSet.sides) {
                run = if (winner == side) run + 1 else 0
                best = maxOf(best, run)
            }
            assertEquals(best, s.streaks[side - 1]!!.length)
        }
    }

    @Test
    fun treSet_momentiChiaveNellOrdine() {
        val s = stats(treSet)
        val expected =
            buildList {
                add(KeyMoment.MatchPointsSaved(side = 1, count = 2))
                add(KeyMoment.SetTurnedAround(SetComeback(2, 1, listOf(2, 5), listOf(7, 5))))
                add(KeyMoment.TieBreakWon(set = 0, side = 1, score = listOf(7, 5)))
                s.streaks
                    .filterNotNull()
                    .filter { it.length >= 5 }
                    .forEach { add(KeyMoment.LongStreak(it)) }
                add(KeyMoment.DecidingPoints(played = 5, won = listOf(3, 2), leader = 1))
            }
        assertEquals(expected, s.moments)
    }

    // --- il file vero dell'app, interrotto --------------------------------------------------

    @Test
    fun interrotta_gameEPuntiDelGameInCorso() {
        val engine = interrotta.engine()
        val current = checkNotNull(checkNotNull(MatchStats.of(engine)).current)
        assertEquals(listOf(2, 1), current.games)
        assertEquals(listOf(1, 0), current.points)
        assertFalse(current.inTieBreak)
        // "15-0" e' il modo in cui il tabellone lo dice: stesse regole, stesso stato.
        val display = engine.rules.display(engine.state)
        assertEquals("15", display.side1Primary)
        assertEquals("0", display.side2Primary)
    }

    @Test
    fun interrotta_servitori() {
        val s = stats(interrotta)
        assertEquals(listOf(1, 4, 2), s.games.map { it.server })
        assertEquals(3, s.current!!.openGame!!.server)
        assertEquals(listOf(1, 0), s.current!!.openGame!!.pointsWon)
        assertEquals(interrotta.servers, s.points.map { it.server })
        assertFalse(s.ended)
        assertNull(s.winnerTeam)
        assertTrue(s.sets.isEmpty())
    }

    // --- casi costruiti -----------------------------------------------------------------------

    private fun engine(
        config: SportConfig,
        vararg sides: Int,
    ): MatchEngine {
        val engine = MatchEngine(RacketRules(config = config))
        sides.forEach { engine.apply(ScoringEvent.Point(it)) }
        return engine
    }

    private val serveOrder = listOf(1, 2, 3, 4)

    /** Killer point: il primo 40-40 si gioca ai vantaggi, solo il secondo e' punto secco. */
    @Test
    fun killerPoint_decisivoSoloSulSecondo4040() {
        val config = SportConfig(deuce = DeuceRule.KILLER_POINT)
        // 40-40, vantaggio 1, 40-40 di nuovo, punto secco al lato 2.
        val s = checkNotNull(MatchStats.of(engine(config, 1, 1, 1, 2, 2, 2, 1, 2, 2)))
        assertEquals(1, s.deciding.played)
        assertEquals(listOf(0, 1), s.deciding.won)
        assertTrue(s.points[8].deciding)
        assertFalse(s.points[6].deciding)
    }

    @Test
    fun vantaggi_nessunPuntoDecisivo() {
        val config = SportConfig(deuce = DeuceRule.ADVANTAGE)
        val s = checkNotNull(MatchStats.of(engine(config, 1, 1, 1, 2, 2, 2, 1, 2, 1, 2, 2, 2)))
        assertEquals(0, s.deciding.played)
        assertEquals(1, s.games.size)
    }

    /** Palla break: chi riceve chiuderebbe il game. Convertita solo se la vince lui. */
    @Test
    fun pallaBreakEConvertita() {
        val config = SportConfig(serveOrder = serveOrder)
        // Serve il lato 1: 0-40, poi il lato 1 ne salva una e il lato 2 converte la seconda.
        val s = checkNotNull(MatchStats.of(engine(config, 2, 2, 2, 1, 2)))
        assertEquals(listOf(0, 2), s.breaks.chances)
        assertEquals(listOf(0, 1), s.breaks.converted)
        assertEquals(false, s.games.single().hold)
    }

    /** Senza ordine di servizio niente servitore, niente break, niente servizio. */
    @Test
    fun senzaOrdineDiServizio() {
        val s = checkNotNull(MatchStats.of(engine(SportConfig(), 2, 2, 2, 1, 2)))
        assertNull(s.serve)
        assertEquals(listOf(0, 0), s.breaks.chances)
        assertNull(s.games.single().hold)
        assertTrue(s.points.all { it.server == null && it.servingSide == null })
    }

    /** Una partita dello storico ha l'ordine a parte: si passa e il servizio torna. */
    @Test
    fun lOrdineDiServizioSiPuoDareAParte() {
        val s = checkNotNull(MatchStats.of(engine(SportConfig(), 2, 2, 2, 1, 2, 1, 1, 1, 1), serveOrder))
        assertEquals(listOf(1, 1, 1, 1, 1, 2, 2, 2, 2), s.points.map { it.server })
        assertEquals(listOf(1, 1, 1, 1, 1, 2, 2, 2, 2), s.points.map { it.servingSide })
        assertEquals(listOf(false, false), s.games.map { it.hold })
        val serve = checkNotNull(s.serve)
        assertEquals(ServeLine(points = 5, won = 1, games = 1, held = 0), serve.bySide[0])
        assertEquals(ServeLine(points = 4, won = 0, games = 1, held = 0), serve.bySide[1])
        assertEquals(listOf(1, 2, 3, 4), serve.byPlayer.map { it.playerId })
    }

    /** Il punto del tie-break non conta nel servizio: lo serve un giocatore diverso ogni due. */
    @Test
    fun ilServizioNonContaIlTieBreak() {
        val config = SportConfig(serveOrder = serveOrder)
        // Dodici game alternati fino al 6-6, poi un punto di tie-break.
        val games = (0 until 12).flatMap { g -> List(4) { if (g % 2 == 0) 1 else 2 } }
        val s = checkNotNull(MatchStats.of(engine(config, *(games + 1).toIntArray())))
        assertTrue(s.points.last().inTieBreak)
        assertEquals(48, checkNotNull(s.serve).bySide.sumOf { it.points })
        assertTrue(s.current!!.inTieBreak)
        assertEquals(listOf(1, 0), s.current!!.points)
    }

    /**
     * Nel tie-break non ci sono palle break: il servizio ruota ogni due punti. Sul 6-0 serve il
     * lato 2 e il lato 1 chiuderebbe: fuori dal tie-break sarebbe una palla break.
     */
    @Test
    fun nelTieBreakNonCiSonoPalleBreak() {
        val config = SportConfig(serveOrder = serveOrder)
        val games = (0 until 12).flatMap { g -> List(4) { if (g % 2 == 0) 1 else 2 } }
        val s = checkNotNull(MatchStats.of(engine(config, *(games + List(7) { 1 }).toIntArray())))
        val last = s.points.last()
        assertTrue(last.inTieBreak)
        assertEquals(2, last.servingSide)
        assertEquals(listOf(true, false), last.setPointFor)
        assertFalse(last.breakPoint)
        assertEquals(listOf(0, 0), s.breaks.chances)
        assertEquals(listOf(7, 0), s.sets.single().tieBreak)
    }

    /** Set point annullato da chi lo subiva; se era anche match point conta solo come tale. */
    @Test
    fun setPointEMatchPointAnnullati() {
        val config = SportConfig(sets = 3, mode = ScoringMode.GAMES)
        // A game: il lato 1 va 5-0, il lato 2 annulla il set point, poi il lato 1 chiude.
        val setUno = listOf(1, 1, 1, 1, 1, 2, 1)
        val s = checkNotNull(MatchStats.of(engine(config, *setUno.toIntArray())))
        assertEquals(listOf(0, 1), s.setPointsSaved)
        assertEquals(listOf(0, 0), s.matchPointsSaved)
        assertEquals(listOf(true, false), s.points[5].setPointFor)
        // Secondo set: sul 5-0 ogni punto del lato 1 e' match point.
        val t = checkNotNull(MatchStats.of(engine(config, *(setUno + listOf(1, 1, 1, 1, 1, 2, 1)).toIntArray())))
        assertEquals(listOf(0, 1), t.setPointsSaved)
        assertEquals(listOf(0, 1), t.matchPointsSaved)
        assertTrue(t.points.last().matchWon)
        assertEquals(listOf(true, false), t.points[12].matchPointFor)
    }

    /** Ribaltata: perso il primo set, vinta la partita. A game non ci sono strisce da raccontare. */
    @Test
    fun partitaRibaltata() {
        val config = SportConfig(sets = 3, mode = ScoringMode.GAMES)
        val s = checkNotNull(MatchStats.of(engine(config, *(List(6) { 2 } + List(12) { 1 }).toIntArray())))
        assertTrue(s.matchTurnedAround)
        assertEquals(12, s.streaks[0]!!.length)
        assertEquals(1, s.streaks[0]!!.set)
        assertEquals(listOf<KeyMoment>(KeyMoment.MatchTurnedAround(1)), s.moments)
    }

    /** Rimonta nel set: il primo svantaggio massimo, dal punto di vista di chi vince. */
    @Test
    fun rimontaNelSet() {
        val config = SportConfig(mode = ScoringMode.GAMES)
        val s = checkNotNull(MatchStats.of(engine(config, *(List(3) { 2 } + List(6) { 1 }).toIntArray())))
        assertEquals(listOf(SetComeback(0, 1, listOf(0, 3), listOf(6, 3))), s.comebacks)
        // Uno svantaggio di un solo game non e' una rimonta.
        val t = checkNotNull(MatchStats.of(engine(config, *(listOf(2) + List(6) { 1 }).toIntArray())))
        assertTrue(t.comebacks.isEmpty())
    }

    @Test
    fun senzaTuttiITempiNienteTempi() {
        val engine = MatchEngine(RacketRules())
        engine.apply(ScoringEvent.Point(1), 0L)
        engine.apply(ScoringEvent.Point(1), null)
        engine.apply(ScoringEvent.Point(1), 9_000L)
        assertNull(checkNotNull(MatchStats.of(engine)).times)
        engine.undo()
        engine.undo()
        engine.apply(ScoringEvent.Point(1), 4_000L)
        engine.apply(ScoringEvent.Point(1), 9_000L)
        assertNotNull(checkNotNull(MatchStats.of(engine)).times)
    }

    /** Un registro salvato dalle versioni vecchie puo' avere correzioni e tocchi inerti. */
    @Test
    fun gliEventiSenzaEffettoNonSonoPunti() {
        val engine = MatchEngine(RacketRules())
        engine.restoreLog(
            listOf(
                LoggedEvent(ScoringEvent.Point(1)),
                LoggedEvent(ScoringEvent.Correction(1)),
                LoggedEvent(ScoringEvent.Point(3)),
                LoggedEvent(ScoringEvent.Point(2)),
            ),
        )
        val s = checkNotNull(MatchStats.of(engine))
        assertEquals(listOf(1, 2), s.points.map { it.side })
        assertEquals(listOf(1, 0), s.points.map { it.diff })
    }

    @Test
    fun ilCalcioNonHaCronaca() {
        assertNull(MatchStats.of(MatchEngine(SportRegistry.byId(SportRegistry.FOOTBALL))))
    }
}
