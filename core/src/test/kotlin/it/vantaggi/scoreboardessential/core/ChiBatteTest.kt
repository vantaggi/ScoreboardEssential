package it.vantaggi.scoreboardessential.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Chi batte, scelto a mano ([ScoringEvent.ServeChoice]): a inizio set la coppia che serve e, in
 * ciascuna coppia, chi batte per primo; a partita in corso la correzione di un errore.
 *
 * L'ordine dei giocatori e' A1, B1, A2, B2 = 11, 22, 33, 44: lato 1 (A) con 11 e 33, lato 2 (B)
 * con 22 e 44. In GAMES un tocco e' un game, cosi' la rotazione si legge senza giocare i punti.
 */
class ChiBatteTest {
    private val ordine = listOf(11, 22, 33, 44)
    private val games = RacketRules(config = SportConfig(mode = ScoringMode.GAMES, sets = 3, serveOrder = ordine))
    private val treSet = RacketRules(config = SportConfig(sets = 3, serveOrder = ordine))

    @Test
    fun `senza scelta la rotazione e' quella di sempre`() {
        (0..40).forEach { i ->
            val stato = RacketScore(serveIndex = i)
            assertEquals("lato al turno $i", i % 2, stato.servingTeam)
            assertEquals("giocatore al turno $i", servingPlayerSlot(i), stato.servingSlot)
            assertEquals("posizione nell'ordine al turno $i", i % 4, stato.servingOrderIndex)
        }
    }

    @Test
    fun `a inizio partita la coppia 2 apre col secondo e la coppia 1 risponde col secondo`() {
        val scelta = games.apply(games.initial(), ScoringEvent.ServeChoice(side = 2, slot1 = 2, slot2 = 2))
        // Quattro game alternati: B2, A2, B1, A1, poi di nuovo B2.
        assertEquals(listOf(44, 33, 22, 11, 44), servitori(games, scelta, 5))
        assertEquals(listOf(2, 1, 2, 1, 2), lati(games, scelta, 5))
    }

    @Test
    fun `a inizio del secondo set si sceglie di nuovo e la rotazione riparte da li'`() {
        // Primo set 6-0 al lato 1: sei game, il set dopo lo aprirebbe il lato 1 col secondo (A2).
        val primoSet = gioca(games, List(6) { 1 })
        assertEquals(33, games.display(primoSet).servingPlayerId)
        val scelta = games.apply(primoSet, ScoringEvent.ServeChoice(side = 2, slot1 = 1, slot2 = 1))
        assertEquals(listOf(22, 11, 44, 33), servitori(games, scelta, 4))
    }

    @Test
    fun `la correzione a partita in corso cambia chi batte da adesso, non i punti gia' giocati`() {
        val engine = MatchEngine(games)
        engine.apply(ScoringEvent.Point(1))
        engine.apply(ScoringEvent.Point(2))
        engine.apply(ScoringEvent.Point(1))
        // Quarto game: secondo la rotazione batte B2 (44). In campo sta battendo B1.
        assertEquals(44, games.display(engine.state).servingPlayerId)
        engine.apply(ScoringEvent.ServeChoice(side = 2, slot1 = 1, slot2 = 1))
        assertEquals(22, games.display(engine.state).servingPlayerId)
        // Il game dopo tocca al lato 1 col primo, poi B2, poi A2.
        assertEquals(listOf(22, 11, 44, 33), servitori(games, engine.state, 4))
        // I game gia' giocati restano a chi li aveva serviti.
        val stats = checkNotNull(MatchStats.of(engine))
        assertEquals(listOf<Int?>(11, 22, 33), stats.points.map { it.server })
    }

    @Test
    fun `correggere il lato fa battere all'altra coppia, al suo turno, chi il tabellone dava adesso`() {
        // Inizio partita: il tabellone dice A1, in campo batte B2.
        val inizio = games.initial() as RacketScore
        val correzione = inizio.serveCorrection(side = 2, slot = 2)
        assertEquals(ScoringEvent.ServeChoice(side = 2, slot1 = 1, slot2 = 2), correzione)
        assertEquals(listOf(44, 11, 22, 33), servitori(games, games.apply(inizio, correzione), 4))
    }

    @Test
    fun `correggere solo il giocatore lascia l'altra coppia com'era`() {
        // Terzo game: batte A2 (33), poi B2 (44). In campo batte A1.
        val terzo = gioca(games, listOf(1, 2))
        assertEquals(33, games.display(terzo).servingPlayerId)
        val correzione = terzo.serveCorrection(side = 1, slot = 1)
        assertEquals(listOf(11, 44, 33, 22), servitori(games, games.apply(terzo, correzione), 4))
    }

    @Test
    fun `la scelta che coincide con la rotazione non cambia niente e non entra nel registro`() {
        val engine = MatchEngine(games)
        val prima = engine.state
        // All'inizio batte A1 e, al turno dopo, B1: la stessa cosa detta a mano.
        assertSame(prima, engine.apply(ScoringEvent.ServeChoice(side = 1, slot1 = 1, slot2 = 1)))
        assertTrue(engine.log.isEmpty())
    }

    @Test
    fun `annullare la scelta torna alla rotazione di prima`() {
        val engine = MatchEngine(games)
        engine.apply(ScoringEvent.Point(1))
        val prima = engine.state
        engine.apply(ScoringEvent.ServeChoice(side = 1, slot1 = 2, slot2 = 1))
        assertEquals(2, engine.log.size)
        assertEquals(prima, engine.undo())
        assertEquals(22, games.display(engine.state).servingPlayerId)
    }

    @Test
    fun `una scelta fatta durante il tie-break vale anche per il set dopo`() {
        // 6-6 in punti: apre il tie-break il lato 1 col primo (A1), al turno 12.
        val padel = treSet
        val seiPari = gioca(padel, seiPari())
        assertEquals(1, padel.display(seiPari).servingSide)
        // Dopo il primo punto batte il lato 2. In campo pero' batte di nuovo il lato 1: cioe' il
        // primo punto l'aveva battuto il lato 2, e il set dopo lo apre chi l'ha ricevuto, il lato 1.
        val dopoUno = padel.apply(seiPari, ScoringEvent.Point(1)) as RacketScore
        assertEquals(2, padel.display(dopoUno).servingSide)
        val corretto = padel.apply(dopoUno, ScoringEvent.ServeChoice(side = 1, slot1 = 1, slot2 = 1)) as RacketScore
        assertEquals(1, padel.display(corretto).servingSide)
        // Il lato 1 chiude il tie-break 7-0.
        val chiuso = gioca(padel, List(6) { 1 }, corretto)
        assertEquals(1, chiuso.closedSets.size)
        assertEquals(listOf(0, 0), chiuso.gamesInSet)
        assertEquals("il set dopo lo apre chi ha ricevuto il primo punto", 1, padel.display(chiuso).servingSide)
    }

    @Test
    fun `nel singolare conta solo il lato`() {
        val tennis = SportRegistry.byId(SportRegistry.TENNIS) as RacketRules
        val inizio = tennis.initial()
        assertSame(inizio, tennis.apply(inizio, ScoringEvent.ServeChoice(side = 1, slot1 = 2, slot2 = 2)))
        val scelta = tennis.apply(inizio, ScoringEvent.ServeChoice(side = 2))
        val display = tennis.display(scelta)
        assertEquals(2, display.servingSide)
        assertNull(display.servingPlayerSlot)
    }

    @Test
    fun `scelte senza senso, a partita finita e nel calcio non hanno effetto`() {
        val inizio = games.initial()
        assertSame(inizio, games.apply(inizio, ScoringEvent.ServeChoice(side = 3)))
        assertSame(inizio, games.apply(inizio, ScoringEvent.ServeChoice(side = 2, slot1 = 0, slot2 = 1)))
        assertSame(inizio, games.apply(inizio, ScoringEvent.ServeChoice(side = 2, slot1 = 1, slot2 = 3)))
        val finita = gioca(games, List(12) { 1 })
        assertTrue(finita.wonBy != null)
        assertSame(finita, games.apply(finita, ScoringEvent.ServeChoice(side = 2)))
        val calcio = FootballRules.initial()
        assertSame(calcio, FootballRules.apply(calcio, ScoringEvent.ServeChoice(side = 2)))
    }

    @Test
    fun `la scelta non e' un punto per statistiche, riassunto e file`() {
        // Qui l'ordine sono gli id del roster, che il file vuole.
        val engine = MatchEngine(SportRegistry.forMatch(SportRegistry.PADEL, listOf(1, 2, 3, 4)))
        engine.apply(ScoringEvent.ServeChoice(side = 2, slot1 = 2, slot2 = 1), atMillis = 0L)
        repeat(8) { engine.apply(ScoringEvent.Point(side = 1), atMillis = (it + 1) * 30_000L) }
        assertEquals(9, engine.log.size)

        val stats = checkNotNull(MatchStats.of(engine))
        assertEquals(8, stats.points.size)
        // Primo game: batte B1 (2), il lato 2, che perde il game: e' un break.
        assertEquals(2, stats.points.first().server)
        assertEquals(2, stats.points.first().servingSide)
        // Secondo game: il lato 1 col secondo, A2 (3).
        assertEquals(3, stats.points[4].server)

        val summary = MatchSummarizer.summarize(engine, roster)
        assertEquals(8, summary.totalPoints)

        val export = ready(MatchExporter.buildPartial(engine, roster, origin))
        assertEquals(8, export.timeline.size)
        assertEquals(List(4) { 2 } + List(4) { 3 }, export.timeline.map { it.servingPlayerId })
    }

    @Test
    fun `il registro scrive e rilegge la scelta, e l'impronta la conta`() {
        val log =
            listOf(
                LoggedEvent(ScoringEvent.ServeChoice(side = 2, slot1 = 1, slot2 = 2), atMillis = 0L),
                LoggedEvent(ScoringEvent.Point(side = 1), atMillis = 30_000L),
                LoggedEvent(ScoringEvent.ServeChoice(side = 1, slot1 = 2, slot2 = 1)),
            )
        val testo = MatchLogCodec.encode(log)
        assertEquals("1|s212@0,1@30000,s121", testo)
        assertEquals(log, MatchLogCodec.decode(testo))
        assertNotEquals(MatchLogCodec.impronta(log.drop(1)), MatchLogCodec.impronta(log))
        listOf("1|s", "1|s1", "1|s12", "1|s312", "1|s102", "1|s121x", "1|s121@", "1|s121@x", "1|s121:4").forEach {
            assertNull("'$it' non e' un registro", MatchLogCodec.decode(it))
        }
    }

    // --- utilita' ---------------------------------------------------------------------------

    private val roster =
        listOf(
            MatchPlayer(localId = 1, name = "Marco", side = 1),
            MatchPlayer(localId = 2, name = "Anna", side = 2),
            MatchPlayer(localId = 3, name = "Luca", side = 1),
            MatchPlayer(localId = 4, name = "Sara", side = 2),
        )

    private val origin =
        ExportOrigin(
            matchId = "3f2a9c1e-5b7d-4e8a-9c01-2d4f6a8b0c1e",
            startedAtMillis = 0L,
            zone = ZoneId.of("Europe/Rome"),
            appVersion = "1.4.0",
        )

    private fun ready(result: ExportResult): MatchExport {
        assertTrue("atteso Ready, ottenuto $result", result is ExportResult.Ready)
        return (result as ExportResult.Ready).export
    }

    /** Chi serve nei prossimi [n] game, alternando i vincitori perche' il set non si chiuda. */
    private fun servitori(
        rules: RacketRules,
        da: ScoreState,
        n: Int,
    ): List<Int?> = turni(rules, da, n).map { it.servingPlayerId }

    private fun lati(
        rules: RacketRules,
        da: ScoreState,
        n: Int,
    ): List<Int?> = turni(rules, da, n).map { it.servingSide }

    private fun turni(
        rules: RacketRules,
        da: ScoreState,
        n: Int,
    ): List<ScoreDisplay> {
        var stato: ScoreState = da
        return List(n) {
            val display = rules.display(stato)
            stato = rules.apply(stato, ScoringEvent.Point(if (it % 2 == 0) 1 else 2))
            display
        }
    }

    private fun gioca(
        rules: RacketRules,
        lati: List<Int>,
        da: ScoreState = rules.initial(),
    ): RacketScore = lati.fold(da) { stato, lato -> rules.apply(stato, ScoringEvent.Point(lato)) } as RacketScore

    /** Dodici game alternati in punti, quattro punti secchi ciascuno: 6-6. */
    private fun seiPari(): List<Int> = (0 until 12).flatMap { g -> List(4) { if (g % 2 == 0) 1 else 2 } }
}
