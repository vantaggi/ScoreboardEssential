package it.vantaggi.scoreboardessential.core

/**
 * Padel e tennis.
 *
 * **Questo non e' un motore nuovo: e' il port di uno gia' in produzione.** L'originale e'
 * `window.LiveScoring` in `padel/js/livematch.js` (defaultConfig, setsToWin, initState,
 * pointLabel, gameDecided, setDecided, applyScore). Ogni scelta che qui sembra arbitraria --
 * "PV" invece di "punto decisivo", il set accreditato a chi ha appena vinto il game, il
 * finalScore che cambia unita' fra set unico e al meglio di N -- e' comportamento che l'utente
 * ha gia' validato sul campo, e va lasciato dov'e'.
 *
 * Due cose il web NON le ha, e sono aggiunte qui:
 *
 * 1. **Il tie-break.** Nel web `gameDecided()` non ha alcun ramo per il tie-break e
 *    `setDecided()` si limita ad accettare il 7-6: sul 6-6 il tredicesimo game viene contato
 *    come un game qualsiasi. In modalita' GAMES e' innocuo (un tocco = un game, e il 7-6
 *    chiude), in POINTS e' sbagliato. Qui il 6-6 apre un vero tie-break a [SportConfig.tieBreakTo]
 *    con scarto di due, e il punteggio finisce in [SetLine.tieBreak].
 *
 * 2. **Il servizio.** Nel web `server` e' un indice di squadra marcato "cosmetic", parte sempre
 *    da 0 e si limita a `1 - server` a ogni game. Qui [RacketScore.serveIndex] e' un contatore
 *    monotono da cui si derivano sia il giocatore (`serveOrder[serveIndex % 4]`) sia il lato
 *    (`serveIndex % 2`): il lato riproduce esattamente l'alternanza del web, quindi il campo
 *    derivato resta compatibile.
 */
class RacketRules(
    override val id: String = "padel",
    override val config: SportConfig = SportConfig(),
) : SportRules {
    override val capabilities: SportCapabilities =
        SportCapabilities(
            clock = ClockMode.NONE,
            hasAuxCountdown = false,
            hasRoles = false,
            attributesScorer = false,
            decrementIsUndo = true,
            scoreEventKey = "point",
        )

    override fun initial(): ScoreState = RacketScore()

    override fun apply(
        state: ScoreState,
        event: ScoringEvent,
    ): ScoreState {
        if (state !is RacketScore || state.wonBy != null) return state
        // Correction non ha semantica qui: decrementIsUndo = true, quindi il "-1" e' un
        // annullamento che il chiamante ottiene rifacendo il fold, non un evento per il motore.
        if (event !is ScoringEvent.Point) return state
        val team = event.side - 1
        if (team != 0 && team != 1) return state
        // `weight` e' ignorato: negli sport con racchetta un punto vale sempre uno.
        return if (config.mode == ScoringMode.GAMES) winGame(state, team) else scorePoint(state, team)
    }

    override fun display(state: ScoreState): ScoreDisplay {
        if (state !is RacketScore) return ScoreDisplay(side1Primary = "", side2Primary = "")
        val ended = state.wonBy != null
        val tieBreak = state.game as? GamePoints.TieBreak
        return ScoreDisplay(
            side1Primary = primary(state, 0),
            side1Secondary = secondary(state, 0),
            side2Primary = primary(state, 1),
            side2Secondary = secondary(state, 1),
            periodLabel =
                when {
                    ended -> null
                    tieBreak != null -> "Tie-break"
                    config.sets > 1 -> "Set ${state.closedSets.size + 1}"
                    else -> null
                },
            servingSide = if (ended) null else state.serveIndex % 2 + 1,
            servingPlayerId =
                if (ended || config.serveOrder.size != 4) null else config.serveOrder[state.serveIndex % 4],
            matchOver = ended,
        )
    }

    // --- motore ------------------------------------------------------------------------------

    private fun scorePoint(
        state: RacketScore,
        team: Int,
    ): RacketScore =
        when (val game = state.game) {
            is GamePoints.Normal -> {
                val raw = bump(game.raw, team)
                if (gameDecided(raw[0], raw[1])) {
                    winGame(state, team)
                } else {
                    state.copy(game = GamePoints.Normal(raw))
                }
            }

            is GamePoints.TieBreak -> {
                val points = bump(game.points, team)
                // Nel tie-break il servizio cambia dopo il primo punto e poi ogni due: cioe' a
                // ogni totale dispari. Sommato al +1 del game concluso, chi ha aperto il
                // tie-break si ritrova in risposta a inizio set successivo, come da regolamento.
                val serveIndex = if ((points[0] + points[1]) % 2 == 1) state.serveIndex + 1 else state.serveIndex
                if (tieBreakDecided(points[0], points[1], game.target)) {
                    winGame(state.copy(serveIndex = serveIndex), team, tieBreakPoints = points)
                } else {
                    state.copy(game = GamePoints.TieBreak(points, game.target), serveIndex = serveIndex)
                }
            }
        }

    private fun winGame(
        state: RacketScore,
        team: Int,
        tieBreakPoints: List<Int>? = null,
    ): RacketScore {
        val games = bump(state.gamesInSet, team)
        val serveIndex = state.serveIndex + 1
        if (!setDecided(games[0], games[1])) {
            val opensTieBreak =
                config.mode == ScoringMode.POINTS &&
                    config.tieBreak &&
                    games[0] == config.gamesPerSet &&
                    games[1] == config.gamesPerSet
            return state.copy(
                gamesInSet = games,
                game = if (opensTieBreak) GamePoints.TieBreak(target = config.tieBreakTo) else GamePoints.Normal(),
                serveIndex = serveIndex,
            )
        }
        // Il set va a chi ha appena vinto il game, come nel web: con queste soglie non esiste uno
        // stato in cui setDecided() scatti a favore dell'altro lato.
        val setsWon = bump(state.setsWon, team)
        val closedSets = state.closedSets + SetLine(games, tieBreakPoints)
        val closed =
            state.copy(
                setsWon = setsWon,
                closedSets = closedSets,
                gamesInSet = listOf(0, 0),
                game = GamePoints.Normal(),
                serveIndex = serveIndex,
            )
        if (setsWon[team] < setsToWin()) return closed
        // Unita' persistita: a set unico sono i game di quel set, altrimenti i set vinti.
        val finalScore = if (config.sets == 1) closedSets.last().games else setsWon
        return closed.copy(finalScore = finalScore, wonBy = team + 1)
    }

    private fun setsToWin(): Int = config.sets / 2 + 1

    private fun gameDecided(
        a: Int,
        b: Int,
    ): Boolean {
        val max = maxOf(a, b)
        val min = minOf(a, b)
        if (max < 4) return false
        return when (config.deuce) {
            DeuceRule.GOLDEN_POINT -> max - min >= 1

            // Un ciclo di vantaggi, poi punto secco: si vince per due finche' entrambi non
            // tornano in parita' a 4 (il secondo 40-40), dopo di che basta un punto.
            DeuceRule.KILLER_POINT -> max - min >= 2 || (min >= 4 && max - min >= 1)

            DeuceRule.ADVANTAGE -> max - min >= 2
        }
    }

    private fun setDecided(
        g0: Int,
        g1: Int,
    ): Boolean {
        val max = maxOf(g0, g1)
        val min = minOf(g0, g1)
        if (max >= config.gamesPerSet && max - min >= 2) return true
        return config.tieBreak && max == config.gamesPerSet + 1 && min == config.gamesPerSet
    }

    private fun tieBreakDecided(
        a: Int,
        b: Int,
        target: Int,
    ): Boolean = maxOf(a, b) >= target && maxOf(a, b) - minOf(a, b) >= 2

    // --- resa a schermo ----------------------------------------------------------------------

    private fun primary(
        state: RacketScore,
        index: Int,
    ): String =
        when {
            state.wonBy != null -> {
                (state.finalScore ?: state.setsWon)[index].toString()
            }

            // In modalita' GAMES non esiste un punteggio di game da mostrare: il numero grande
            // e' quello dei game del set in corso, come nel tabellone web.
            config.mode == ScoringMode.GAMES -> {
                state.gamesInSet[index].toString()
            }

            else -> {
                when (val game = state.game) {
                    is GamePoints.Normal -> pointLabel(game.raw[index], game.raw[1 - index])
                    is GamePoints.TieBreak -> game.points[index].toString()
                }
            }
        }

    /** Ogni lato legge la riga dal proprio punto di vista: il suo punteggio sempre per primo. */
    private fun secondary(
        state: RacketScore,
        index: Int,
    ): String? {
        val closed = state.closedSets.map { "${it.games[index]}-${it.games[1 - index]}" }
        val parts =
            if (state.wonBy != null) {
                closed
            } else {
                closed + "${state.gamesInSet[index]}-${state.gamesInSet[1 - index]}"
            }
        return if (parts.isEmpty()) null else parts.joinToString(SEPARATOR)
    }

    private fun pointLabel(
        a: Int,
        b: Int,
    ): String {
        if (config.deuce == DeuceRule.GOLDEN_POINT) {
            // Il ramo "a > b" e' irraggiungibile (a 4-3 il game e' gia' chiuso) ma e' nel web e
            // resta qui: se un giorno la soglia cambia, i due motori sbaglieranno allo stesso modo.
            if (a >= 3 && b >= 3) return if (a > b) "PV" else "40"
            return pointLabels[minOf(a, 3)]
        }
        if (config.deuce == DeuceRule.KILLER_POINT && a >= 4 && b >= 4) return "PV"
        if (a >= 3 && b >= 3) return if (a > b) "AV" else "40"
        return pointLabels[minOf(a, 3)]
    }

    private fun bump(
        values: List<Int>,
        index: Int,
    ): List<Int> = values.mapIndexed { i, v -> if (i == index) v + 1 else v }

    private companion object {
        val pointLabels = listOf("0", "15", "30", "40")

        /** Middot come separatore, in escape per tenere il sorgente in puro ASCII. */
        const val SEPARATOR = " \u00B7 "
    }
}
