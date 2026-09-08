package it.vantaggi.scoreboardessential.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * I casi sono una tabella, non asserzioni sparse: ogni riga e' una partita giocata dall'inizio
 * con una sequenza di lati, e le attese sono i campi che quella riga vuole fissare. Aggiungere
 * una regola significa aggiungere una riga, e il nome della riga compare nel messaggio di errore.
 */
class RacketRulesTest {
    private data class Case(
        val name: String,
        val rules: RacketRules,
        val sides: List<Int>,
        val games: List<Int>? = null,
        val sets: List<Int>? = null,
        val game: GamePoints? = null,
        val closedSets: List<SetLine>? = null,
        val primary: Pair<String, String>? = null,
        /** wonBy e finalScore sono sempre verificati: il default null significa "partita in corso". */
        val wonBy: Int? = null,
        val finalScore: List<Int>? = null,
    )

    private val cases: List<Case> =
        listOf(
            // --- chiusura del set (in GAMES un tocco e' un game, quindi le sequenze restano leggibili)
            Case(
                name = "6-0 chiude il set",
                rules = rules(mode = ScoringMode.GAMES),
                sides = taps(1, 6),
                closedSets = listOf(SetLine(listOf(6, 0))),
                wonBy = 1,
                finalScore = listOf(6, 0),
            ),
            Case(
                name = "6-5 non chiude: serve lo scarto di due",
                rules = rules(mode = ScoringMode.GAMES),
                sides = alternate(10) + taps(1, 1),
                games = listOf(6, 5),
            ),
            Case(
                name = "dal 5-5 si chiude 7-5",
                rules = rules(mode = ScoringMode.GAMES),
                sides = alternate(10) + taps(1, 2),
                wonBy = 1,
                finalScore = listOf(7, 5),
            ),
            Case(
                // In GAMES il tie-break non esiste: e' il comportamento del web, e resta.
                name = "in GAMES il 6-6 si risolve col 7-6 senza tie-break",
                rules = rules(mode = ScoringMode.GAMES),
                sides = alternate(12) + taps(1, 1),
                closedSets = listOf(SetLine(listOf(7, 6))),
                wonBy = 1,
                finalScore = listOf(7, 6),
            ),
            // --- risoluzione del 40-40
            Case(
                name = "golden point: il 40-40 si legge 40 su entrambi i lati",
                rules = rules(deuce = DeuceRule.GOLDEN_POINT),
                sides = alternate(6),
                games = listOf(0, 0),
                primary = "40" to "40",
            ),
            Case(
                name = "golden point: il punto dopo il 40-40 vince il game",
                rules = rules(deuce = DeuceRule.GOLDEN_POINT),
                sides = alternate(6) + taps(1, 1),
                games = listOf(1, 0),
                game = GamePoints.Normal(listOf(0, 0)),
            ),
            Case(
                name = "vantaggi: dal 40-40 si va all'AV",
                rules = rules(deuce = DeuceRule.ADVANTAGE),
                sides = alternate(6) + taps(1, 1),
                games = listOf(0, 0),
                primary = "AV" to "40",
            ),
            Case(
                name = "vantaggi: l'AV perso riporta in parita'",
                rules = rules(deuce = DeuceRule.ADVANTAGE),
                sides = alternate(6) + listOf(1, 2),
                games = listOf(0, 0),
                primary = "40" to "40",
            ),
            Case(
                name = "vantaggi: secondo AV e poi game, si vince per due",
                rules = rules(deuce = DeuceRule.ADVANTAGE),
                sides = alternate(6) + listOf(1, 2, 1, 1),
                games = listOf(1, 0),
            ),
            Case(
                name = "killer point: il primo 40-40 gioca un vantaggio",
                rules = rules(deuce = DeuceRule.KILLER_POINT),
                sides = alternate(6) + taps(1, 1),
                games = listOf(0, 0),
                primary = "AV" to "40",
            ),
            Case(
                name = "killer point: il secondo 40-40 e' punto secco su entrambi",
                rules = rules(deuce = DeuceRule.KILLER_POINT),
                sides = alternate(6) + listOf(1, 2),
                games = listOf(0, 0),
                primary = "PV" to "PV",
            ),
            Case(
                name = "killer point: dopo il PV il punto successivo chiude il game",
                rules = rules(deuce = DeuceRule.KILLER_POINT),
                sides = alternate(6) + listOf(1, 2, 2),
                games = listOf(0, 1),
            ),
            // --- tie-break (l'estensione che il web non ha)
            Case(
                name = "il 6-6 apre il tie-break",
                rules = rules(),
                sides = sixAll,
                games = listOf(6, 6),
                game = GamePoints.TieBreak(listOf(0, 0), 7),
                primary = "0" to "0",
            ),
            Case(
                name = "il tie-break si vince 7-5 e chiude il set 7-6",
                rules = rules(),
                sides = sixAll + alternate(10) + taps(1, 2),
                closedSets = listOf(SetLine(listOf(7, 6), listOf(7, 5))),
                sets = listOf(1, 0),
                wonBy = 1,
                finalScore = listOf(7, 6),
            ),
            Case(
                name = "il tie-break NON si vince 7-6: serve lo scarto di due",
                rules = rules(),
                sides = sixAll + alternate(12) + taps(1, 1),
                games = listOf(6, 6),
                game = GamePoints.TieBreak(listOf(7, 6), 7),
            ),
            Case(
                name = "il tie-break si vince 8-6",
                rules = rules(),
                sides = sixAll + alternate(12) + taps(1, 2),
                closedSets = listOf(SetLine(listOf(7, 6), listOf(8, 6))),
                wonBy = 1,
                finalScore = listOf(7, 6),
            ),
            // --- al meglio di 3 e unita' del finalScore
            Case(
                name = "al meglio di 3: un set vinto non chiude la partita",
                rules = rules(mode = ScoringMode.GAMES, sets = 3),
                sides = taps(1, 6),
                sets = listOf(1, 0),
            ),
            Case(
                name = "al meglio di 3: wonBy dopo il secondo set, e finalScore sono i SET",
                rules = rules(mode = ScoringMode.GAMES, sets = 3),
                sides = taps(1, 12),
                sets = listOf(2, 0),
                closedSets = listOf(SetLine(listOf(6, 0)), SetLine(listOf(6, 0))),
                wonBy = 1,
                finalScore = listOf(2, 0),
            ),
        )

    @Test
    fun `la tabella dei casi`() {
        cases.forEach { case ->
            val state = play(case.rules, case.sides)
            case.games?.let { assertEquals("${case.name}: game del set", it, state.gamesInSet) }
            case.sets?.let { assertEquals("${case.name}: set vinti", it, state.setsWon) }
            case.game?.let { assertEquals("${case.name}: punteggio del game", it, state.game) }
            case.closedSets?.let { assertEquals("${case.name}: set chiusi", it, state.closedSets) }
            case.primary?.let {
                val display = case.rules.display(state)
                assertEquals("${case.name}: primario lato 1", it.first, display.side1Primary)
                assertEquals("${case.name}: primario lato 2", it.second, display.side2Primary)
            }
            assertEquals("${case.name}: vincitore", case.wonBy, state.wonBy)
            assertEquals("${case.name}: punteggio finale", case.finalScore, state.finalScore)
        }
    }

    @Test
    fun `un evento senza senso lascia lo stato invariato`() {
        val rules = rules(mode = ScoringMode.GAMES)
        val mid = play(rules, taps(1, 2))
        assertEquals("lato 0 fuori range", mid, rules.apply(mid, ScoringEvent.Point(0)))
        assertEquals("lato 3 fuori range", mid, rules.apply(mid, ScoringEvent.Point(3)))
        // decrementIsUndo = true: l'annullamento e' un rifacimento del fold, non un evento.
        assertEquals("correzione ignorata", mid, rules.apply(mid, ScoringEvent.Correction(1)))
        val ended = play(rules, taps(1, 6))
        assertEquals("punto a partita finita", ended, rules.apply(ended, ScoringEvent.Point(2)))
        assertEquals("punto al vincitore a partita finita", ended, rules.apply(ended, ScoringEvent.Point(1)))
        // Purezza: stesso stato + stesso evento = stesso risultato.
        assertEquals(rules.apply(mid, ScoringEvent.Point(2)), rules.apply(mid, ScoringEvent.Point(2)))
    }

    @Test
    fun `il servizio ruota su quattro e non si azzera al confine di set`() {
        val rules = rules(mode = ScoringMode.GAMES, sets = 3, serveOrder = listOf(11, 22, 33, 44))
        var state = rules.initial() as RacketScore
        val servers = mutableListOf<Int?>()
        val sides = mutableListOf<Int?>()
        val web = mutableListOf<Int?>()
        var webServer = 0 // l'alternanza `server = 1 - server` del web, come riferimento
        repeat(8) {
            val display = rules.display(state)
            servers += display.servingPlayerId
            sides += display.servingSide
            web += webServer + 1
            state = rules.apply(state, ScoringEvent.Point(1)) as RacketScore
            webServer = 1 - webServer
        }
        // I game 7 e 8 sono gia' nel secondo set: la rotazione prosegue da dove era rimasta.
        assertEquals(listOf<Int?>(11, 22, 33, 44, 11, 22, 33, 44), servers)
        assertEquals("il lato che serve resta compatibile col web", web, sides)
        assertEquals("un set chiuso", 1, state.closedSets.size)
        assertEquals("contatore monotono, mai azzerato", 8, state.serveIndex)
    }

    @Test
    fun `il servizio nel tie-break avanza dopo il primo punto e poi ogni due`() {
        val rules = rules()
        var state = play(rules, sixAll)
        assertEquals("dodici game conclusi", 12, state.serveIndex)
        val observed = mutableListOf<Int>()
        listOf(1, 2, 1, 2, 1).forEach { side ->
            state = rules.apply(state, ScoringEvent.Point(side)) as RacketScore
            observed += state.serveIndex
        }
        assertEquals(listOf(13, 13, 14, 14, 15), observed)
    }

    @Test
    fun `display impagina set chiusi, game correnti e periodo`() {
        val rules = rules(mode = ScoringMode.GAMES, sets = 3)
        // 6-4 nel primo set, poi 3-2 nel secondo.
        val state = play(rules, alternate(8) + taps(1, 2) + listOf(1, 2, 1, 2, 1))
        val display = rules.display(state)
        // In modalita' GAMES il numero grande sono i game del set in corso, non un punteggio di game.
        assertEquals("3", display.side1Primary)
        assertEquals("6-4 \u00B7 3-2", display.side1Secondary)
        assertEquals("2", display.side2Primary)
        assertEquals("4-6 \u00B7 2-3", display.side2Secondary)
        assertEquals("Set 2", display.periodLabel)
    }

    @Test
    fun `display segnala il tie-break e chiude senza servizio a partita finita`() {
        val points = rules()
        assertEquals("Tie-break", points.display(play(points, sixAll)).periodLabel)
        val games = rules(mode = ScoringMode.GAMES)
        val display = games.display(play(games, taps(1, 6)))
        // A partita finita il primario e' il finalScore, che a set unico e' in game.
        assertEquals("6", display.side1Primary)
        assertEquals("0", display.side2Primary)
        assertNull(display.periodLabel)
        assertNull(display.servingSide)
    }

    // --- utilita' ---------------------------------------------------------------------------

    private fun rules(
        mode: ScoringMode = ScoringMode.POINTS,
        deuce: DeuceRule = DeuceRule.GOLDEN_POINT,
        sets: Int = 1,
        tieBreak: Boolean = true,
        serveOrder: List<Int> = emptyList(),
    ): RacketRules =
        RacketRules(
            config =
                SportConfig(
                    mode = mode,
                    deuce = deuce,
                    sets = sets,
                    tieBreak = tieBreak,
                    serveOrder = serveOrder,
                ),
        )

    private fun play(
        rules: RacketRules,
        sides: List<Int>,
    ): RacketScore =
        sides.fold(rules.initial()) { state, side ->
            rules.apply(state, ScoringEvent.Point(side))
        } as RacketScore

    private companion object {
        /** n punti (o game, in modalita' GAMES) di fila allo stesso lato. */
        fun taps(
            side: Int,
            n: Int,
        ): List<Int> = List(n) { side }

        /** n eventi alternati partendo dal lato 1. */
        fun alternate(n: Int): List<Int> = List(n) { if (it % 2 == 0) 1 else 2 }

        /**
         * Sei game per parte in modalita' POINTS con golden point: quattro punti di fila vincono
         * un game, e alternando i game si arriva al 6-6 senza che il set si chiuda prima.
         */
        val sixAll: List<Int> = (1..6).flatMap { List(4) { 1 } + List(4) { 2 } }
    }
}
