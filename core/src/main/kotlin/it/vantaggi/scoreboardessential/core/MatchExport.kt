package it.vantaggi.scoreboardessential.core

/**
 * Un giocatore cosi' come il tabellone lo conosce: l'id della sua riga locale, il nome e il lato.
 *
 * Non c'e' l'id di Padel Elite. Il collegamento fra i due spazi di identificatori vive nella
 * mappa passata a [MatchExporter.build]: `:core` non conosce Room e non deve conoscerlo, e tenere
 * il collegamento fuori dal roster impedisce di esportare un id preso da una riga stantia.
 */
data class MatchPlayer(
    val localId: Int,
    val name: String,
    /** 1 o 2. */
    val side: Int,
)

/** Il giocatore come finisce nel file: entrambi gli identificatori, cosi' l'import puo' scegliere. */
data class ExportedPlayer(
    val localId: Int,
    val name: String,
    val side: Int,
    val padelPlayerId: Int?,
)

/**
 * Un punto della cronologia.
 *
 * Sono tre campi e non di piu': set, game e punteggio corrente NON vengono duplicati qui perche'
 * si riottengono rigiocando il log con le stesse regole, e una copia che puo' divergere dalla
 * fonte e' peggio di un calcolo in piu' dal lato di chi importa.
 */
data class TimelinePoint(
    /** Il lato che ha vinto il punto: 1 o 2. */
    val side: Int,
    /** Id LOCALE di chi serviva; null se l'ordine di servizio non era impostato. */
    val servingPlayerId: Int?,
    /** Millisecondi dall'inizio della partita; null se lo sport non traccia i tempi. */
    val atMillis: Long?,
)

/**
 * Una partita pronta per essere scritta su file e riletta dalla dashboard di Padel Elite.
 *
 * [scoreTeam1], [scoreTeam2], [winnerTeam] e [setScores] hanno deliberatamente i nomi e la forma
 * delle colonne di `v2_matches`: l'import non deve tradurre niente. Il valore che solo il
 * tabellone puo' dare e' invece [timeline] -- il punto per punto con i tempi e chi serviva, da cui
 * si ricavano andamento, recuperi, durata dei set, punti vinti al servizio e break. Un export
 * senza timeline ripeterebbe soltanto dati che la dashboard gia' possiede.
 */
data class MatchExport(
    val formatVersion: Int,
    val sportId: String,
    val config: SportConfig,
    val players: List<ExportedPlayer>,
    val scoreTeam1: Int,
    val scoreTeam2: Int,
    /** 1, 2 o null se la partita non e' arrivata in fondo. */
    val winnerTeam: Int?,
    /** Un `[gamesTeam1, gamesTeam2]` per ogni set CHIUSO, nell'ordine in cui sono stati giocati. */
    val setScores: List<List<Int>>,
    val timeline: List<TimelinePoint>,
)

/**
 * Cosa manca per poter esportare.
 *
 * Casi tipizzati e non stringhe: l'interfaccia deve poter dire all'utente quali NOMI mancano --
 * "collega Marco e Sara a Padel Elite" e' azionabile, "2 giocatori non collegati" no.
 */
sealed interface ExportProblem {
    /**
     * `create_match()` pretende esattamente quattro giocatori: sia meno sia piu' sono lo stesso
     * difetto, e distinguerli darebbe all'interfaccia due casi da gestire per un solo rimedio.
     */
    data class WrongPlayerCount(
        val found: Int,
    ) : ExportProblem

    /** Due giocatori per lato. Verificato solo quando i giocatori sono gia' quattro. */
    data class UnbalancedSides(
        val side1: Int,
        val side2: Int,
    ) : ExportProblem

    /** Lo stesso giocatore compare piu' volte: i quattro devono essere distinti. */
    data class DuplicatePlayers(
        val names: List<String>,
    ) : ExportProblem

    /** Giocatori senza `padelPlayerId`: la dashboard non saprebbe a chi attribuire la partita. */
    data class UnlinkedPlayers(
        val names: List<String>,
    ) : ExportProblem

    /** Nessun punto valido: un log di sole correzioni, o una partita mai iniziata. */
    data object NoPoints : ExportProblem
}

/** Esito della costruzione: o l'export, o l'elenco di cio' che manca. Non si lancia mai. */
sealed interface ExportResult {
    data class Ready(
        val export: MatchExport,
    ) : ExportResult

    data class Incomplete(
        val problems: List<ExportProblem>,
    ) : ExportResult
}

/**
 * Costruzione e serializzazione dell'export.
 *
 * ## Perche' JSON scritto a mano
 *
 * `:core` e' Kotlin puro con la sola JUnit fra le dipendenze, e non va aperto a `org.json` (che
 * qui non esiste: e' Android) ne' a `kotlinx.serialization` (che porterebbe un plugin del
 * compilatore in un modulo che serve proprio a restare senza). Il formato e' chiuso e piccolo --
 * numeri, booleani, nomi di enum e i nomi dei giocatori -- quindi l'unico punto davvero delicato
 * e' l'escaping delle stringhe, ed e' isolato in [quote].
 */
object MatchExporter {
    /** Versione del formato del file. Un bump la incrementa; chi importa deve controllarla. */
    const val FORMAT_VERSION = 1

    private const val PLAYERS_PER_MATCH = 4

    /**
     * Elenca cio' che manca senza costruire niente: e' la forma che serve all'interfaccia per
     * spegnere il pulsante di export e spiegare perche', mentre la partita e' ancora in corso.
     * Lista vuota significa esportabile.
     */
    fun validate(
        engine: MatchEngine,
        players: List<MatchPlayer>,
        padelIds: Map<Int, Int>,
    ): List<ExportProblem> = problems(players, padelIds, replay(engine).timeline)

    /**
     * Costruisce l'export, o dice cosa manca.
     *
     * Prende [MatchEngine] e non la terna `rules + log + state` perche' il motore e' l'unico posto
     * in cui i tre sono garantiti coerenti: la sua invariante e' `state == log.fold(initial)`.
     * Con tre parametri separati un chiamante potrebbe consegnare il log di una partita e lo stato
     * di un'altra, e l'export ne uscirebbe plausibile e sbagliato. Per la stessa ragione qui il
     * punteggio finale non viene letto da `engine.state` ma dal fold che produce la timeline: la
     * partita esportata e' esattamente quella che i punti elencati raccontano.
     */
    fun build(
        engine: MatchEngine,
        players: List<MatchPlayer>,
        padelIds: Map<Int, Int>,
    ): ExportResult {
        val replayed = replay(engine)
        val missing = problems(players, padelIds, replayed.timeline)
        if (missing.isNotEmpty()) return ExportResult.Incomplete(missing)

        val state = replayed.finalState
        val headline = state.headline()
        val export =
            MatchExport(
                formatVersion = FORMAT_VERSION,
                sportId = engine.rules.id,
                config = engine.rules.config,
                players = players.map { ExportedPlayer(it.localId, it.name, it.side, padelIds[it.localId]) },
                scoreTeam1 = headline.first,
                scoreTeam2 = headline.second,
                winnerTeam = state.wonBy,
                setScores = (state as? RacketScore)?.closedSets?.map { it.games } ?: emptyList(),
                timeline = replayed.timeline,
            )
        return ExportResult.Ready(export)
    }

    /** JSON compatto, senza spazi: il file finisce in una cartella, non sotto gli occhi di nessuno. */
    fun toJson(export: MatchExport): String {
        val sb = StringBuilder(64 * export.timeline.size + 512)
        sb.append("{\"formatVersion\":").append(export.formatVersion)
        sb.append(",\"sportId\":").append(quote(export.sportId))
        sb.append(",\"config\":")
        appendConfig(sb, export.config)
        sb.append(",\"players\":[")
        export.players.forEachIndexed { i, player ->
            if (i > 0) sb.append(',')
            sb.append("{\"localId\":").append(player.localId)
            sb.append(",\"name\":").append(quote(player.name))
            sb.append(",\"side\":").append(player.side)
            sb.append(",\"padelPlayerId\":").append(number(player.padelPlayerId))
            sb.append('}')
        }
        sb.append("],\"scoreTeam1\":").append(export.scoreTeam1)
        sb.append(",\"scoreTeam2\":").append(export.scoreTeam2)
        sb.append(",\"winnerTeam\":").append(number(export.winnerTeam))
        sb.append(",\"setScores\":[")
        export.setScores.forEachIndexed { i, set ->
            if (i > 0) sb.append(',')
            sb.append('[').append(set.joinToString(",")).append(']')
        }
        sb.append("],\"timeline\":[")
        export.timeline.forEachIndexed { i, point ->
            if (i > 0) sb.append(',')
            sb.append("{\"side\":").append(point.side)
            sb.append(",\"servingPlayerId\":").append(number(point.servingPlayerId))
            sb.append(",\"atMillis\":").append(number(point.atMillis))
            sb.append('}')
        }
        sb.append("]}")
        return sb.toString()
    }

    // --- costruzione -------------------------------------------------------------------------

    private class Replay(
        val timeline: List<TimelinePoint>,
        val finalState: ScoreState,
    )

    /**
     * Rigioca il log con le regole dello sport.
     *
     * Serve per il servitore -- che non e' nel log ma nello stato PRIMA del punto -- e per la
     * stessa ragione produce anche lo stato finale: una sola passata, una sola verita'.
     *
     * Un evento che non cambia lo stato non entra nella cronologia: sono le correzioni (che negli
     * sport con racchetta il motore ignora), i lati fuori range e i tocchi arrivati a partita gia'
     * finita. Il confronto fra stati basta a riconoscerli tutti perche' [SportRules.apply] e'
     * totale e restituisce lo stato invariato proprio in quei casi.
     */
    private fun replay(engine: MatchEngine): Replay {
        val rules = engine.rules
        val order = rules.config.serveOrder
        // `log` restituisce una copia difensiva a ogni accesso: si legge una volta sola.
        val log = engine.log
        var state = rules.initial()
        val timeline = ArrayList<TimelinePoint>(log.size)
        for (entry in log) {
            val next = rules.apply(state, entry.event)
            if (next == state) continue
            timeline.add(
                TimelinePoint(
                    side = entry.event.side,
                    servingPlayerId = servingPlayerId(state, order),
                    atMillis = entry.atMillis,
                ),
            )
            state = next
        }
        return Replay(timeline, state)
    }

    /**
     * Il servitore al momento del punto.
     *
     * Un ordine di servizio incompleto vale come assente: il campo resta null e l'export e'
     * comunque valido. E' un dato in meno nella cronologia, non un errore che impedisce di
     * salvare una partita gia' giocata.
     */
    private fun servingPlayerId(
        state: ScoreState,
        order: List<Int>,
    ): Int? = if (order.size != PLAYERS_PER_MATCH || state !is RacketScore) null else order[state.serveIndex % PLAYERS_PER_MATCH]

    private fun problems(
        players: List<MatchPlayer>,
        padelIds: Map<Int, Int>,
        timeline: List<TimelinePoint>,
    ): List<ExportProblem> {
        val found = mutableListOf<ExportProblem>()
        if (players.size != PLAYERS_PER_MATCH) {
            found.add(ExportProblem.WrongPlayerCount(players.size))
        } else {
            // Il bilanciamento dei lati si controlla solo a quattro: sotto, direbbe due volte
            // la stessa cosa con parole diverse.
            val side1 = players.count { it.side == 1 }
            val side2 = players.count { it.side == 2 }
            if (side1 != 2 || side2 != 2) found.add(ExportProblem.UnbalancedSides(side1, side2))
        }
        val duplicates =
            players
                .groupBy { it.localId }
                .values
                .filter { it.size > 1 }
                .map { it.first().name }
        if (duplicates.isNotEmpty()) found.add(ExportProblem.DuplicatePlayers(duplicates))
        val unlinked = players.filter { padelIds[it.localId] == null }.map { it.name }
        if (unlinked.isNotEmpty()) found.add(ExportProblem.UnlinkedPlayers(unlinked))
        if (timeline.isEmpty()) found.add(ExportProblem.NoPoints)
        return found
    }

    // --- JSON --------------------------------------------------------------------------------

    private fun appendConfig(
        sb: StringBuilder,
        config: SportConfig,
    ) {
        sb.append("{\"mode\":").append(quote(config.mode.name))
        sb.append(",\"deuce\":").append(quote(config.deuce.name))
        sb.append(",\"sets\":").append(config.sets)
        sb.append(",\"tieBreak\":").append(config.tieBreak)
        sb.append(",\"gamesPerSet\":").append(config.gamesPerSet)
        sb.append(",\"tieBreakTo\":").append(config.tieBreakTo)
        sb.append(",\"serveOrder\":[").append(config.serveOrder.joinToString(",")).append("]}")
    }

    private fun number(value: Number?): String = value?.toString() ?: "null"

    /**
     * Una stringa JSON con l'escaping completo.
     *
     * I nomi dei giocatori li digita una persona: possono contenere virgolette, backslash e --
     * incollati da un'altra app -- ritorni a capo e caratteri di controllo, che in JSON sono
     * illegali nudi e romperebbero il file in modo invisibile a chi lo guarda. Gli accenti no:
     * sono caratteri stampabili, restano tali e il file va scritto in UTF-8.
     */
    private fun quote(text: String): String {
        val sb = StringBuilder(text.length + 2)
        sb.append('"')
        for (c in text) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c == '\b' -> sb.append("\\b")
                c < ' ' -> sb.append("\\u").append("%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }
}
