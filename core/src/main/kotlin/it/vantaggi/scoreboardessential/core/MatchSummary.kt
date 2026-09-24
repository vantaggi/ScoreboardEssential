package it.vantaggi.scoreboardessential.core

/**
 * Quale forma ha il report. Si sceglie dalle CAPABILITIES, non dall'id dello sport: cosi' un
 * quinto sport con racchetta eredita il report completo senza toccare questo file, e uno a
 * contatore non finisce a mostrare "break: 0" perche' qualcuno ha dimenticato un ramo.
 */
enum class ReportProfile {
    /** `clock == NONE`: set, game, servizio. Padel e tennis. */
    RALLY,

    /** `clock == COUNT_UP`: punteggio e durata. Calcio. */
    COUNTER,
}

/** Un set chiuso, con la sua durata quando i tempi ci sono. */
data class SetSummary(
    val games: List<Int>,
    val tieBreak: List<Int>? = null,
    val durationMillis: Long? = null,
)

/** Punti giocati al servizio da un giocatore, e quanti ne ha vinti il suo lato. */
data class ServeStat(
    val playerId: Int,
    val name: String,
    val pointsServed: Int,
    val pointsWon: Int,
) {
    /** Percentuale intera, arrotondata all'unita' piu' vicina (24/28 -> 86, non 85). */
    val percent: Int
        get() = if (pointsServed == 0) 0 else (pointsWon * 200 + pointsServed) / (pointsServed * 2)
}

/** La serie piu' lunga di punti consecutivi, e chi l'ha fatta. */
data class StreakSummary(
    val side: Int,
    val points: Int,
)

/** Un marcatore e i suoi punti. Esiste solo quando gli eventi portano un [ScoringEvent.Point.playerId]. */
data class ScorerTally(
    val playerId: Int,
    val name: String,
    val points: Int,
)

/**
 * I dati del riassunto, senza una parola di testo.
 *
 * Cio' che non si applica al profilo e' vuoto o null, non zero: `breaks` vuota significa "questo
 * sport non ha il concetto", `breaks = [0, 0]` significa "nessuno ha strappato il servizio". Sono
 * due cose diverse e il formatter le distingue.
 */
data class MatchSummary(
    val sportId: String,
    val profile: ReportProfile,
    /** Il roster com'e' arrivato: serve al formatter per i nomi dei lati. */
    val players: List<MatchPlayer>,
    /** `ScoreState.headline()`: gol per il calcio, set (o game, a set unico) per la racchetta. */
    val score: List<Int>,
    /** 1, 2 o null se la partita non e' arrivata in fondo. */
    val winnerSide: Int?,
    val sets: List<SetSummary>,
    /**
     * I game del set in corso, solo a partita a racchetta NON finita; null altrimenti.
     *
     * Sta fuori da [sets] perche' quello e' l'elenco dei set chiusi, con le loro durate: un set a
     * meta' non ne ha una. Senza questo campo il riassunto di una partita interrotta perdeva
     * proprio il punteggio che si stava giocando.
     */
    val currentSet: List<Int>?,
    val durationMillis: Long?,
    val totalPoints: Int,
    val serveStats: List<ServeStat>,
    /** Game vinti da chi non serviva, per lato. Vuota fuori dal profilo RALLY. */
    val breaks: List<Int>,
    val longestStreak: StreakSummary?,
    val scorers: List<ScorerTally>,
)

/**
 * Le etichette del report, riempite da `:mobile` dalle risorse.
 *
 * ## Perche' il formatter non contiene testo
 *
 * `:core` e' Kotlin puro e non vede `strings.xml`: se le parole stessero qui, il report sarebbe in
 * una lingua sola e per tradurlo servirebbe duplicare il formatter. Quindi
 * [MatchSummarizer.format] mette solo NUMERI e NOMI DI PERSONA, e tutto il resto arriva da qui.
 * La conseguenza da ricordare: ogni parola nuova nel report e' un campo nuovo di questa classe,
 * non una stringa scritta nel formatter.
 *
 * Nessun campo ha un default: un default sarebbe italiano, e un'etichetta dimenticata uscirebbe
 * in italiano dentro un report inglese senza che nessuno se ne accorga. Meglio l'errore di
 * compilazione.
 *
 * Restano nel formatter solo i simboli che non si traducono: `%`, il trattino del punteggio e i
 * separatori.
 */
data class ReportLabels(
    /** "Vince" / "Winner". */
    val vince: String,
    /** "Durata" / "Duration". */
    val durata: String,
    /** "Punti giocati" / "Points played". */
    val punti: String,
    /** "Al servizio" / "On serve". */
    val alServizio: String,
    /** "Break". Il nome del campo non e' `break`: e' una parola chiave di Kotlin. */
    val breakVinti: String,
    /** "Serie migliore" / "Best streak". */
    val serieMigliore: String,
    /** "Marcatori" / "Scorers". */
    val marcatori: String,
    /** Unita' brevi attaccate al numero: "h" / "h". */
    val unitaOre: String,
    /** "min" / "min". */
    val unitaMinuti: String,
    /**
     * Come chiamare il lato 1 quando i nomi non bastano.
     *
     * Nel profilo COUNTER e' sempre questo: il roster del calcio sono undici persone, non il nome
     * della squadra, e la squadra la conosce solo `:mobile`.
     */
    val squadra1: String,
    /** Come sopra, lato 2. */
    val squadra2: String,
)

/**
 * Calcola il riassunto di una partita e lo impagina per una chat.
 *
 * ## Definizioni scelte, dove la statistica era ambigua
 *
 * - **Break**: un game vinto da chi NON serviva. Il lato al servizio si legge da
 *   `RacketScore.serveIndex % 2`, che il motore tiene sempre -- anche senza `serveOrder`, ed e'
 *   lo stesso valore che il tabellone ha mostrato in campo durante la partita. Quindi i break si
 *   contano comunque: senza `serveOrder` manca il NOME di chi serviva, non il lato.
 * - **Il tie-break non e' un break.** Nel tie-break il servizio cambia ogni due punti: non c'e'
 *   un lato che "teneva il servizio" da strappare, quindi quel game non entra nel conteggio ne'
 *   in un senso ne' nell'altro.
 * - **Durata**: `atMillis` e' misurato dall'inizio della partita, quindi la durata e' l'ultimo
 *   timestamp, non una differenza. La durata di un set e' la distanza fra il suo ultimo punto e
 *   l'ultimo punto del set precedente (zero per il primo): cosi' le durate dei set sommano
 *   esattamente alla durata totale, invece di lasciare buchi inspiegabili fra un set e l'altro.
 * - **Serie**: punti consecutivi vinti dallo stesso lato, attraverso i confini di game e di set.
 *   Una serie interrotta e ripresa non si somma.
 */
object MatchSummarizer {
    private const val PLAYERS_PER_MATCH = 4

    /** Middot in escape, per tenere il sorgente in puro ASCII come nel resto di :core. */
    private const val SEPARATOR = " · "
    private const val NAME_SEPARATOR = " / "

    /**
     * Rigioca il log con le regole dello sport e ne ricava le statistiche.
     *
     * Prende [MatchEngine] per la stessa ragione di [MatchExporter.build]: e' l'unico posto in cui
     * log, regole e stato sono garantiti coerenti. Un evento che non ha cambiato lo stato --
     * correzione, lato fuori range, tocco a partita finita -- non e' un punto giocato e non entra
     * in nessun conteggio.
     *
     * [elapsedMillis] e' la durata secondo il cronometro del telefono, e serve al profilo COUNTER:
     * il calcio non scrive `atMillis` nel log, quindi senza questo parametro l'unica statistica
     * che quel profilo ha oltre al punteggio non esisterebbe mai. I tempi del log, quando ci sono,
     * hanno la precedenza: sono i tempi dei punti davvero giocati.
     */
    fun summarize(
        engine: MatchEngine,
        players: List<MatchPlayer>,
        elapsedMillis: Long? = null,
    ): MatchSummary {
        val rules = engine.rules
        val order = rules.config.serveOrder
        val hasServeOrder = order.size == PLAYERS_PER_MATCH
        val profile = if (rules.capabilities.clock == ClockMode.NONE) ReportProfile.RALLY else ReportProfile.COUNTER

        val breaks = intArrayOf(0, 0)
        val serveTally = HashMap<Int, IntArray>()
        val scorerTally = LinkedHashMap<Int, Int>()
        val setLines = mutableListOf<SetLine>()
        val setEnds = mutableListOf<Long?>()
        var totalPoints = 0
        var lastMillis: Long? = null
        var runSide = 0
        var runLength = 0
        var bestSide = 0
        var bestLength = 0
        var state = rules.initial()

        for (entry in engine.log) {
            val next = rules.apply(state, entry.event)
            if (next == state) continue
            val event = entry.event

            totalPoints++
            if (entry.atMillis != null) lastMillis = entry.atMillis
            if (event.side == runSide) {
                runLength++
            } else {
                runSide = event.side
                runLength = 1
            }
            if (runLength > bestLength) {
                bestLength = runLength
                bestSide = event.side
            }
            if (event is ScoringEvent.Point && event.playerId != null) {
                scorerTally[event.playerId] = (scorerTally[event.playerId] ?: 0) + event.weight
            }

            val before = state as? RacketScore
            val after = next as? RacketScore
            if (before != null && after != null) {
                val servingSide = before.serveIndex % 2 + 1
                if (hasServeOrder) {
                    val tally = serveTally.getOrPut(order[before.serveIndex % PLAYERS_PER_MATCH]) { IntArray(2) }
                    tally[0]++
                    if (event.side == servingSide) tally[1]++
                }
                if (closesGame(before, after) && before.game !is GamePoints.TieBreak && event.side != servingSide) {
                    breaks[event.side - 1]++
                }
                if (after.closedSets.size > before.closedSets.size) {
                    setLines.add(after.closedSets.last())
                    setEnds.add(entry.atMillis)
                }
            }
            state = next
        }

        val headline = state.headline()
        val unfinished = (state as? RacketScore)?.takeIf { it.wonBy == null }
        return MatchSummary(
            sportId = rules.id,
            profile = profile,
            players = players,
            score = listOf(headline.first, headline.second),
            winnerSide = state.wonBy,
            sets = sets(setLines, setEnds),
            currentSet = unfinished?.gamesInSet,
            durationMillis = lastMillis ?: elapsedMillis,
            totalPoints = totalPoints,
            serveStats = serveStats(order, players, serveTally),
            breaks = if (profile == ReportProfile.RALLY) listOf(breaks[0], breaks[1]) else emptyList(),
            longestStreak = if (profile == ReportProfile.RALLY && bestLength > 0) StreakSummary(bestSide, bestLength) else null,
            scorers = scorers(players, scorerTally),
        )
    }

    /**
     * Impagina il riassunto per WhatsApp.
     *
     * Solo `*grassetto*`: niente tabelle e niente markdown, che in chat si vedrebbero come
     * caratteri. Una riga per statistica, e le righe senza dati semplicemente non ci sono -- un
     * campo mancante non e' un errore, e "Durata: --" su un telefono e' rumore.
     */
    fun format(
        summary: MatchSummary,
        labels: ReportLabels,
    ): String {
        val side1 = sideName(summary, 1, labels)
        val side2 = sideName(summary, 2, labels)
        val lines = mutableListOf<String>()
        // Finche' nessun set e' chiuso i set vinti sono 0-0 per forza e non dicono niente: in
        // testa vanno allora i game del set in corso, che a set unico sono proprio l'unita' del
        // punteggio finale. `score` resta headline(), su cui si contano le vittorie.
        val current = summary.currentSet
        val header = if (summary.sets.isEmpty() && current != null) current else summary.score
        lines.add("*$side1 ${header[0]}-${header[1]} $side2*")

        // Un solo set ripeterebbe l'intestazione: a set unico il punteggio finale E' quel set. Il
        // set in corso conta come un set in piu', cosi' 6-4 e poi 3-2 non perdono il 3-2.
        val setTexts = summary.sets.map { setText(it) } + listOfNotNull(current?.let { "${it[0]}-${it[1]}" })
        if (setTexts.size > 1) {
            lines.add(setTexts.joinToString(SEPARATOR))
        }
        summary.winnerSide?.let { lines.add(line(labels.vince, if (it == 1) side1 else side2)) }
        summary.durationMillis?.let { lines.add(line(labels.durata, durationText(it, summary, labels))) }

        if (summary.profile == ReportProfile.RALLY) {
            lines.add(line(labels.punti, summary.totalPoints.toString()))
            if (summary.serveStats.isNotEmpty()) {
                lines.add(line(labels.alServizio, summary.serveStats.joinToString(SEPARATOR) { "${it.name} ${it.percent}%" }))
            }
            if (summary.breaks.size == 2) {
                lines.add(line(labels.breakVinti, "$side1 ${summary.breaks[0]}$SEPARATOR$side2 ${summary.breaks[1]}"))
            }
            summary.longestStreak?.let {
                lines.add(line(labels.serieMigliore, "${it.points} (${if (it.side == 1) side1 else side2})"))
            }
        } else if (summary.scorers.isNotEmpty()) {
            lines.add(line(labels.marcatori, summary.scorers.joinToString(SEPARATOR) { "${it.name} ${it.points}" }))
        }
        return lines.joinToString("\n")
    }

    // --- calcolo -----------------------------------------------------------------------------

    private fun closesGame(
        before: RacketScore,
        after: RacketScore,
    ): Boolean = after.closedSets.size > before.closedSets.size || after.gamesInSet.sum() > before.gamesInSet.sum()

    private fun sets(
        lines: List<SetLine>,
        ends: List<Long?>,
    ): List<SetSummary> {
        var previous: Long? = 0L
        return lines.mapIndexed { i, line ->
            val end = ends[i]
            val duration = if (end != null && previous != null) end - previous!! else null
            previous = end
            SetSummary(games = line.games, tieBreak = line.tieBreak, durationMillis = duration)
        }
    }

    /**
     * I giocatori nell'ordine di servizio, ma coi compagni vicini: `serveOrder` e' A1, B1, A2, B2 e
     * a schermo alternerebbe le due squadre riga per riga.
     *
     * Un id che non e' nel roster viene saltato: senza nome non c'e' niente da scrivere in chat, e
     * inventarlo sarebbe peggio che ometterlo.
     */
    private fun serveStats(
        order: List<Int>,
        players: List<MatchPlayer>,
        tally: Map<Int, IntArray>,
    ): List<ServeStat> =
        order
            .withIndex()
            .sortedBy { it.index % 2 }
            .mapNotNull { (_, id) ->
                val counts = tally[id] ?: return@mapNotNull null
                val name = players.firstOrNull { it.localId == id }?.name ?: return@mapNotNull null
                ServeStat(playerId = id, name = name, pointsServed = counts[0], pointsWon = counts[1])
            }

    /**
     * I marcatori, dal piu' prolifico.
     *
     * Le correzioni non tolgono niente a nessuno: il "-1" non dice quale gol annullare, e
     * sottrarlo all'ultimo marcatore sarebbe un'invenzione. Il totale dei marcatori puo' quindi
     * non combaciare col punteggio in una partita corretta a mano.
     */
    private fun scorers(
        players: List<MatchPlayer>,
        tally: Map<Int, Int>,
    ): List<ScorerTally> =
        tally
            .mapNotNull { (id, points) ->
                val name = players.firstOrNull { it.localId == id }?.name ?: return@mapNotNull null
                ScorerTally(playerId = id, name = name, points = points)
            }.sortedByDescending { it.points }

    // --- impaginazione -----------------------------------------------------------------------

    private fun line(
        label: String,
        value: String,
    ): String = "$label: $value"

    /**
     * Il nome di un lato.
     *
     * Nel profilo COUNTER e' sempre l'etichetta: il roster del calcio sono i giocatori, e
     * "Marco / Luca" non e' il nome di una squadra di undici. Negli sport con racchetta sono i due
     * compagni, che e' esattamente come il gruppo li chiama in chat.
     */
    private fun sideName(
        summary: MatchSummary,
        side: Int,
        labels: ReportLabels,
    ): String {
        val fallback = if (side == 1) labels.squadra1 else labels.squadra2
        if (summary.profile != ReportProfile.RALLY) return fallback
        val names = summary.players.filter { it.side == side }.map { it.name }
        return if (names.isEmpty() || names.size > 2) fallback else names.joinToString(NAME_SEPARATOR)
    }

    private fun setText(set: SetSummary): String {
        val games = "${set.games[0]}-${set.games[1]}"
        return if (set.tieBreak == null) games else "$games (${set.tieBreak[0]}-${set.tieBreak[1]})"
    }

    /** La durata totale, e fra parentesi quella dei set quando ci sono tutte. */
    private fun durationText(
        total: Long,
        summary: MatchSummary,
        labels: ReportLabels,
    ): String {
        val clock = clockText(total, labels)
        val perSet = summary.sets.mapNotNull { it.durationMillis }
        if (summary.sets.size < 2 || perSet.size != summary.sets.size) return clock
        return "$clock (${perSet.joinToString(SEPARATOR) { clockText(it, labels) }})"
    }

    private fun clockText(
        millis: Long,
        labels: ReportLabels,
    ): String {
        val minutes = millis / 60_000L
        val hours = minutes / 60
        val rest = minutes % 60
        return if (hours > 0) "$hours${labels.unitaOre} $rest${labels.unitaMinuti}" else "$rest${labels.unitaMinuti}"
    }
}
