package it.vantaggi.scoreboardessential.core

/**
 * Un punto giocato, con cio' che c'era in palio PRIMA che si giocasse.
 *
 * Le liste per lato hanno l'indice 0 per il lato 1 e l'indice 1 per il lato 2, come
 * `gamesInSet` e `setsWon` di [RacketScore].
 */
data class PointStat(
    /** Il lato che ha vinto il punto: 1 o 2. */
    val side: Int,
    /** Id di chi serviva; null senza ordine di servizio. */
    val server: Int?,
    /** Lato al servizio; null senza ordine di servizio, come [server]. */
    val servingSide: Int?,
    val atMillis: Long?,
    /** Indice del set, da 0. */
    val set: Int,
    /** Indice del game nella partita, da 0: il tie-break e' un game. */
    val game: Int,
    val inTieBreak: Boolean,
    /** Entrambi i lati chiuderebbero il game con questo punto (punto secco). */
    val deciding: Boolean,
    /** Il lato che riceve chiuderebbe il game con questo punto. */
    val breakPoint: Boolean,
    val setPointFor: List<Boolean>,
    val matchPointFor: List<Boolean>,
    val gameWon: Boolean,
    val setWon: Boolean,
    val matchWon: Boolean,
    /** Andamento: punti vinti dal lato 1 meno punti vinti dal lato 2, compreso questo. */
    val diff: Int,
)

/** Un game chiuso. */
data class GameStat(
    val set: Int,
    /** Chi serviva il PRIMO punto del game; nel tie-break il servizio poi ruota. */
    val server: Int?,
    val servingSide: Int?,
    val tieBreak: Boolean,
    val winner: Int,
    /** Punti vinti da ciascun lato nel game: nel tie-break e' il suo punteggio. */
    val score: List<Int>,
    /** I game del set dopo questo game. */
    val gamesAfter: List<Int>,
    /** Tenuto da chi serviva; null nel tie-break e senza ordine di servizio. */
    val hold: Boolean?,
    val firstPoint: Int,
    val lastPoint: Int,
    /** Dal primo all'ultimo punto del game; null se uno dei due non ha il tempo. */
    val durationMs: Long?,
)

/** Un set chiuso. */
data class SetStat(
    val index: Int,
    val games: List<Int>,
    /** Il punteggio del tie-break, se giocato, lato 1 per primo. */
    val tieBreak: List<Int>?,
    val winner: Int,
)

/** Il game in corso di una partita interrotta, se ha almeno un punto. */
data class OpenGame(
    val pointsWon: List<Int>,
    val server: Int?,
    val servingSide: Int?,
)

/** Il set in corso di una partita interrotta. */
data class OpenSet(
    val index: Int,
    val games: List<Int>,
    val inTieBreak: Boolean,
    /** Conteggio grezzo del game (0, 1, 2, 3...) o punti del tie-break: le parole le mette la schermata. */
    val points: List<Int>,
    val openGame: OpenGame?,
)

/** Il servizio di una coppia o di un giocatore: punti fuori dal tie-break, game normali. */
data class ServeLine(
    val points: Int,
    val won: Int,
    val games: Int,
    val held: Int,
)

data class PlayerServe(
    val playerId: Int,
    val line: ServeLine,
)

/** Il servizio della partita; esiste solo con l'ordine di servizio e in modalita' punti. */
data class ServeStats(
    val bySide: List<ServeLine>,
    /** Nell'ordine di servizio. */
    val byPlayer: List<PlayerServe>,
)

/** Palle break, per lato che RICEVE. */
data class BreakStats(
    val chances: List<Int>,
    val converted: List<Int>,
)

data class DecidingStats(
    val played: Int,
    val won: List<Int>,
)

/** La striscia piu' lunga di un lato, con il set in cui e' cominciata. */
data class Streak(
    val side: Int,
    val length: Int,
    val set: Int,
)

/** "Da 2-5 a 7-5": [from] e [to] sono dal punto di vista di chi ha vinto il set, lui per primo. */
data class SetComeback(
    val set: Int,
    val side: Int,
    val from: List<Int>,
    val to: List<Int>,
)

/** Solo con tutti i tempi. */
data class MatchTimes(
    val totalMs: Long,
    /** Per set chiuso: dalla fine del set prima (o dal primo punto) alla fine di questo. */
    val setDurationsMs: List<Long>,
    /** Media degli intervalli fra punti consecutivi. */
    val avgPointMs: Long,
    /** Media dei game normali: il tie-break non e' un game medio. */
    val avgGameMs: Long?,
    val longestGame: GameStat?,
)

/**
 * I momenti chiave, come dati. Le frasi le scrive la schermata: `:core` non ha stringhe da
 * tradurre, e il testo di un momento e' una scelta d'interfaccia, non una regola del padel.
 */
sealed interface KeyMoment {
    data class MatchPointsSaved(
        val side: Int,
        val count: Int,
    ) : KeyMoment

    /** Il vincitore ha perso il primo set. */
    data class MatchTurnedAround(
        val side: Int,
    ) : KeyMoment

    data class SetTurnedAround(
        val comeback: SetComeback,
    ) : KeyMoment

    /** [score] dal punto di vista di chi l'ha vinto, lui per primo: "7-5". */
    data class TieBreakWon(
        val set: Int,
        val side: Int,
        val score: List<Int>,
    ) : KeyMoment

    data class LongStreak(
        val streak: Streak,
    ) : KeyMoment

    data class SetPointsSaved(
        val side: Int,
        val count: Int,
    ) : KeyMoment

    /** [leader] null quando i due lati ne hanno vinti tanti uguali. */
    data class DecidingPoints(
        val played: Int,
        val won: List<Int>,
        val leader: Int?,
    ) : KeyMoment
}

/**
 * Le statistiche della Cronaca, con le definizioni della dashboard di Padel Elite
 * (`docs/dashboard/SCOREBOARD_CRONACA_APP.md`, sezione 2): i due lati devono contare allo stesso
 * modo, o la stessa partita avrebbe due cronache diverse.
 */
data class MatchStats(
    val pointsMode: Boolean,
    val ended: Boolean,
    val winnerTeam: Int?,
    val points: List<PointStat>,
    val games: List<GameStat>,
    val sets: List<SetStat>,
    /** Null a partita finita. */
    val current: OpenSet?,
    val pointsWon: List<Int>,
    val gamesWon: List<Int>,
    val serve: ServeStats?,
    val breaks: BreakStats,
    val deciding: DecidingStats,
    val setPointsSaved: List<Int>,
    val matchPointsSaved: List<Int>,
    /** Per lato; null se quel lato non ha vinto nemmeno un punto. */
    val streaks: List<Streak?>,
    val comebacks: List<SetComeback>,
    val matchTurnedAround: Boolean,
    val times: MatchTimes?,
    val moments: List<KeyMoment>,
) {
    companion object {
        private const val PLAYERS_PER_MATCH = 4
        private const val MIN_COMEBACK = 2
        private const val MIN_STREAK_MOMENT = 5
        private const val MIN_DECIDING_MOMENT = 2

        /**
         * Rigioca il registro e ne ricava le statistiche, in una passata.
         *
         * Prende [MatchEngine] per la stessa ragione di [MatchExporter.build]: e' l'unico posto in
         * cui regole, registro e stato sono garantiti coerenti. L'ordine di servizio si puo' dare a
         * parte perche' una partita riletta dallo storico lo conserva accanto al registro, non
         * dentro le regole; il servitore si ricava comunque dallo stato PRIMA del punto.
         *
         * Il lato al servizio e' `serveIndex % 2 + 1`, come in [RacketRules.display]: l'ordine e'
         * A1, B1, A2, B2 con A il lato 1, e il telefono lo costruisce cosi'. Senza un ordine di
         * quattro giocatori il servitore resta ignoto, e con lui palle break e game tenuti, come
         * nella dashboard.
         *
         * Null per gli sport che non sono con racchetta: la Cronaca e' fatta di game e set.
         */
        fun of(
            engine: MatchEngine,
            serveOrder: List<Int> = engine.rules.config.serveOrder,
        ): MatchStats? {
            val rules = engine.rules
            var state = rules.initial() as? RacketScore ?: return null
            val pointsMode = rules.config.mode == ScoringMode.POINTS
            val order = serveOrder.takeIf { it.size == PLAYERS_PER_MATCH }

            val points = ArrayList<PointStat>()
            val games = ArrayList<GameStat>()
            val sets = ArrayList<SetStat>()
            // I game del set in corso dopo ogni game, per le rimonte.
            val progressBySet = ArrayList<List<List<Int>>>()
            var progress = ArrayList<List<Int>>()

            val won = intArrayOf(0, 0)
            val sideServe = Array(2) { ServeCounter() }
            val playerServe = LinkedHashMap<Int, ServeCounter>()
            order?.forEach { playerServe.getOrPut(it) { ServeCounter() } }
            val breakChances = intArrayOf(0, 0)
            val breaksConverted = intArrayOf(0, 0)
            var decidingPlayed = 0
            val decidingWon = intArrayOf(0, 0)
            val setPointsSaved = intArrayOf(0, 0)
            val matchPointsSaved = intArrayOf(0, 0)
            val streaks = arrayOfNulls<Streak>(2)
            var run: Streak? = null

            var setNo = 0
            var gameNo = 0
            var gameFirst = -1
            val gamePoints = intArrayOf(0, 0)

            // `log` restituisce una copia difensiva a ogni accesso: si legge una volta sola.
            for (entry in engine.log) {
                val next = rules.apply(state, entry.event) as RacketScore
                // Correzioni, lati fuori range, tocchi a partita finita: non sono punti giocati.
                if (next == state) continue
                val side = entry.event.side
                val team = side - 1
                val i = points.size
                val inTieBreak = state.game is GamePoints.TieBreak
                val server = order?.get(state.serveIndex % PLAYERS_PER_MATCH)
                val servingSide = if (order == null) null else state.serveIndex % 2 + 1

                // Che cosa c'era in palio: si prova a dare il punto a ciascuno dei due lati.
                val probe = listOf(1, 2).map { rules.apply(state, ScoringEvent.Point(it)) as RacketScore }
                val gameFor = probe.map { gamesPlayed(it) > gamesPlayed(state) }
                val setPointFor = probe.map { it.closedSets.size > state.closedSets.size }
                val matchPointFor = probe.map { it.wonBy != null }
                val deciding = pointsMode && !inTieBreak && gameFor[0] && gameFor[1]
                val receiver = servingSide?.let { 3 - it }
                val breakPoint = pointsMode && !inTieBreak && receiver != null && gameFor[receiver - 1]

                val gameWon = gamesPlayed(next) > gamesPlayed(state)
                val setWon = next.closedSets.size > state.closedSets.size
                won[team]++

                if (gameFirst < 0) {
                    gameFirst = i
                    gamePoints.fill(0)
                }
                gamePoints[team]++

                if (breakPoint) {
                    breakChances[receiver!! - 1]++
                    // Non serve chiedere se il game si chiude: e' la definizione di palla break.
                    if (side == receiver) breaksConverted[receiver - 1]++
                }
                if (deciding) {
                    decidingPlayed++
                    decidingWon[team]++
                }
                val other = 1 - team
                // Chi vince un punto che all'altro avrebbe dato il set o la partita lo "annulla".
                if (matchPointFor[other]) {
                    matchPointsSaved[team]++
                } else if (setPointFor[other]) {
                    setPointsSaved[team]++
                }
                if (servingSide != null && !inTieBreak) {
                    val held = side == servingSide
                    sideServe[servingSide - 1].point(held)
                    playerServe[server]?.point(held)
                }

                val current = run
                run = if (current != null && current.side == side) current.copy(length = current.length + 1) else Streak(side, 1, setNo)
                if (run.length > (streaks[team]?.length ?: 0)) streaks[team] = run

                points.add(
                    PointStat(
                        side = side,
                        server = server,
                        servingSide = servingSide,
                        atMillis = entry.atMillis,
                        set = setNo,
                        game = gameNo,
                        inTieBreak = inTieBreak,
                        deciding = deciding,
                        breakPoint = breakPoint,
                        setPointFor = setPointFor,
                        matchPointFor = matchPointFor,
                        gameWon = gameWon,
                        setWon = setWon,
                        matchWon = next.wonBy != null,
                        diff = won[0] - won[1],
                    ),
                )

                if (gameWon) {
                    val first = points[gameFirst]
                    val closedSet = if (setWon) next.closedSets.last() else null
                    val gamesAfter = closedSet?.games ?: next.gamesInSet
                    // Il servitore del game e' quello del suo PRIMO punto: nel tie-break ruota.
                    val hold = if (inTieBreak || first.servingSide == null) null else first.servingSide == side
                    val startMs = first.atMillis
                    val endMs = entry.atMillis
                    val game =
                        GameStat(
                            set = setNo,
                            server = first.server,
                            servingSide = first.servingSide,
                            tieBreak = inTieBreak,
                            winner = side,
                            score = gamePoints.toList(),
                            gamesAfter = gamesAfter,
                            hold = hold,
                            firstPoint = gameFirst,
                            lastPoint = i,
                            // Dal primo all'ultimo punto del game: le pause di cambio campo non
                            // sono gioco.
                            durationMs = if (startMs != null && endMs != null) endMs - startMs else null,
                        )
                    games.add(game)
                    if (hold != null) {
                        sideServe[first.servingSide!! - 1].game(hold)
                        playerServe[first.server]?.game(hold)
                    }
                    progress.add(gamesAfter)
                    gameFirst = -1
                    gameNo++
                    if (closedSet != null) {
                        sets.add(SetStat(setNo, closedSet.games, closedSet.tieBreak, side))
                        progressBySet.add(progress)
                        progress = ArrayList()
                        setNo++
                    }
                }
                state = next
            }

            val ended = state.wonBy != null
            val current =
                if (ended) {
                    null
                } else {
                    val openFirst = points.getOrNull(gameFirst)
                    OpenSet(
                        index = setNo,
                        games = state.gamesInSet,
                        inTieBreak = state.game is GamePoints.TieBreak,
                        points =
                            when (val game = state.game) {
                                is GamePoints.Normal -> game.raw
                                is GamePoints.TieBreak -> game.points
                            },
                        openGame = openFirst?.let { OpenGame(gamePoints.toList(), it.server, it.servingSide) },
                    )
                }

            val comebacks = comebacks(sets, progressBySet)
            val matchTurnedAround = ended && sets.size > 1 && sets[0].winner != state.wonBy
            val serve =
                if (order != null && pointsMode && points.isNotEmpty()) {
                    ServeStats(
                        bySide = sideServe.map { it.line() },
                        byPlayer = playerServe.map { (id, counter) -> PlayerServe(id, counter.line()) },
                    )
                } else {
                    null
                }

            val stats =
                MatchStats(
                    pointsMode = pointsMode,
                    ended = ended,
                    winnerTeam = state.wonBy,
                    points = points,
                    games = games,
                    sets = sets,
                    current = current,
                    pointsWon = won.toList(),
                    gamesWon = listOf(games.count { it.winner == 1 }, games.count { it.winner == 2 }),
                    serve = serve,
                    breaks = BreakStats(breakChances.toList(), breaksConverted.toList()),
                    deciding = DecidingStats(decidingPlayed, decidingWon.toList()),
                    setPointsSaved = setPointsSaved.toList(),
                    matchPointsSaved = matchPointsSaved.toList(),
                    streaks = streaks.toList(),
                    comebacks = comebacks,
                    matchTurnedAround = matchTurnedAround,
                    times = times(points, games),
                    moments = emptyList(),
                )
            return stats.copy(moments = moments(stats))
        }

        /** Tutti i game giocati, compresi quelli dei set chiusi: il tie-break vale un game. */
        private fun gamesPlayed(state: RacketScore): Int = state.gamesInSet.sum() + state.closedSets.sumOf { it.games.sum() }

        /**
         * Per chi ha vinto il set, il massimo svantaggio in game (almeno due) dopo un game
         * qualsiasi del set. A parita' di svantaggio vale il primo: e' quello da cui e' ripartito.
         */
        private fun comebacks(
            sets: List<SetStat>,
            progressBySet: List<List<List<Int>>>,
        ): List<SetComeback> =
            sets.mapNotNull { set ->
                val w = set.winner - 1
                val l = 1 - w
                var worst: List<Int>? = null
                for (g in progressBySet[set.index]) {
                    val deficit = g[l] - g[w]
                    if (deficit >= MIN_COMEBACK && (worst == null || deficit > worst[1] - worst[0])) worst = listOf(g[w], g[l])
                }
                worst?.let { SetComeback(set.index, set.winner, it, listOf(set.games[w], set.games[l])) }
            }

        /**
         * I tempi, solo se ogni punto ha il suo: con un buco, durate e medie sarebbero calcolate
         * su una partita diversa da quella giocata. Serve almeno un intervallo.
         */
        private fun times(
            points: List<PointStat>,
            games: List<GameStat>,
        ): MatchTimes? {
            if (points.size < 2 || points.any { it.atMillis == null }) return null
            val at = points.map { it.atMillis!! }
            var setStart = at.first()
            val setDurations = ArrayList<Long>()
            points.forEachIndexed { i, point ->
                if (point.setWon) {
                    setDurations.add(at[i] - setStart)
                    setStart = at[i]
                }
            }
            val intervals = (1 until at.size).map { at[it] - at[it - 1] }
            val timed = games.filter { !it.tieBreak && it.durationMs != null }
            return MatchTimes(
                totalMs = at.last() - at.first(),
                setDurationsMs = setDurations,
                avgPointMs = average(intervals),
                avgGameMs = if (timed.isEmpty()) null else average(timed.map { it.durationMs!! }),
                // A parita' vale il primo, come nella dashboard.
                longestGame = timed.fold(null as GameStat?) { a, g -> if (a == null || g.durationMs!! > a.durationMs!!) g else a },
            )
        }

        /** Arrotondata come `Math.round` di JavaScript sui positivi: mezzo va in su. */
        private fun average(values: List<Long>): Long = Math.round(values.sum().toDouble() / values.size)

        /** Nell'ordine della dashboard, e solo quando c'e' il dato. */
        private fun moments(s: MatchStats): List<KeyMoment> {
            val out = ArrayList<KeyMoment>()
            for (side in 1..2) {
                val n = s.matchPointsSaved[side - 1]
                if (n > 0) out.add(KeyMoment.MatchPointsSaved(side, n))
            }
            if (s.matchTurnedAround) out.add(KeyMoment.MatchTurnedAround(s.winnerTeam!!))
            s.comebacks.forEach { out.add(KeyMoment.SetTurnedAround(it)) }
            s.sets.forEach { set ->
                val tb = set.tieBreak ?: return@forEach
                val w = set.winner - 1
                out.add(KeyMoment.TieBreakWon(set.index, set.winner, listOf(tb[w], tb[1 - w])))
            }
            if (s.pointsMode) {
                s.streaks.forEach { st -> if (st != null && st.length >= MIN_STREAK_MOMENT) out.add(KeyMoment.LongStreak(st)) }
            }
            for (side in 1..2) {
                val n = s.setPointsSaved[side - 1]
                if (n > 0) out.add(KeyMoment.SetPointsSaved(side, n))
            }
            if (s.deciding.played >= MIN_DECIDING_MOMENT) {
                val (a, b) = s.deciding.won
                val leader =
                    when {
                        a == b -> null
                        a > b -> 1
                        else -> 2
                    }
                out.add(KeyMoment.DecidingPoints(s.deciding.played, s.deciding.won, leader))
            }
            return out
        }
    }

    /** Contatore mutabile di una sola passata; fuori esce come [ServeLine]. */
    private class ServeCounter {
        private var points = 0
        private var won = 0
        private var games = 0
        private var held = 0

        fun point(won: Boolean) {
            points++
            if (won) this.won++
        }

        fun game(held: Boolean) {
            games++
            if (held) this.held++
        }

        fun line() = ServeLine(points, won, games, held)
    }
}
