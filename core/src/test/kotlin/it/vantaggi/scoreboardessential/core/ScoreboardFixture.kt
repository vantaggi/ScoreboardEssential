package it.vantaggi.scoreboardessential.core

/**
 * Una partita di prova in `core/src/test/resources/scoreboard/`, riletta dal file.
 *
 * Il motore si ricostruisce come farebbe chi importa: [RacketRules] con la config del file e un
 * punto per elemento della timeline, col suo `atMillis`. Il servitore scritto nel file NON entra
 * nel motore: resta in [servers] per confrontarlo con quello che il replay ricava da solo.
 */
class ScoreboardFixture(
    val config: SportConfig,
    val setScores: List<List<Int>>,
    val winnerTeam: Int?,
    val sides: List<Int>,
    val servers: List<Int?>,
    val atMillis: List<Long?>,
) {
    fun engine(): MatchEngine {
        val engine = MatchEngine(RacketRules(config = config))
        sides.forEachIndexed { i, side -> engine.apply(ScoringEvent.Point(side), atMillis[i]) }
        return engine
    }

    companion object {
        fun load(name: String): ScoreboardFixture {
            val text =
                checkNotNull(ScoreboardFixture::class.java.classLoader.getResourceAsStream("scoreboard/$name")) {
                    "manca la risorsa scoreboard/$name"
                }.use { it.readBytes().toString(Charsets.UTF_8) }
            val root = MiniJson.parse(text) as Map<*, *>
            val c = root["config"] as Map<*, *>
            val config =
                SportConfig(
                    mode = ScoringMode.valueOf(c["mode"] as String),
                    deuce = DeuceRule.valueOf(c["deuce"] as String),
                    sets = (c["sets"] as Long).toInt(),
                    tieBreak = c["tieBreak"] as Boolean,
                    gamesPerSet = (c["gamesPerSet"] as Long).toInt(),
                    tieBreakTo = (c["tieBreakTo"] as Long).toInt(),
                    serveOrder = (c["serveOrder"] as List<*>).map { (it as Long).toInt() },
                )
            val timeline = (root["timeline"] as List<*>).map { it as Map<*, *> }
            return ScoreboardFixture(
                config = config,
                setScores = (root["setScores"] as List<*>).map { set -> (set as List<*>).map { (it as Long).toInt() } },
                winnerTeam = (root["winnerTeam"] as Long?)?.toInt(),
                sides = timeline.map { (it["side"] as Long).toInt() },
                servers = timeline.map { (it["servingPlayerId"] as Long?)?.toInt() },
                atMillis = timeline.map { it["atMillis"] as Long? },
            )
        }
    }
}

/**
 * Un lettore JSON minimo, solo per i test: `:core` non ha librerie JSON e non ne deve avere.
 * Oggetti come [Map], array come [List], interi come [Long], decimali come [Double].
 */
object MiniJson {
    fun parse(text: String): Any? {
        val reader = Reader(text)
        val value = reader.value()
        reader.skipSpaces()
        require(reader.pos == text.length) { "testo in piu' alla posizione ${reader.pos}" }
        return value
    }

    private class Reader(
        val s: String,
    ) {
        var pos = 0

        fun skipSpaces() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun value(): Any? {
            skipSpaces()
            return when (val c = s[pos]) {
                '{' -> obj()
                '[' -> array()
                '"' -> string()
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                else -> if (c == '-' || c.isDigit()) number() else error("carattere inatteso '$c' alla posizione $pos")
            }
        }

        private fun obj(): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            pos++
            skipSpaces()
            if (s[pos] == '}') return out.also { pos++ }
            while (true) {
                skipSpaces()
                val key = string()
                skipSpaces()
                expect(':')
                out[key] = value()
                skipSpaces()
                if (s[pos] == '}') return out.also { pos++ }
                expect(',')
            }
        }

        private fun array(): List<Any?> {
            val out = ArrayList<Any?>()
            pos++
            skipSpaces()
            if (s[pos] == ']') return out.also { pos++ }
            while (true) {
                out.add(value())
                skipSpaces()
                if (s[pos] == ']') return out.also { pos++ }
                expect(',')
            }
        }

        private fun string(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                val c = s[pos++]
                when (c) {
                    '"' -> {
                        return sb.toString()
                    }

                    '\\' -> {
                        when (val e = s[pos++]) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> sb.append(s.substring(pos, pos + 4).toInt(16).toChar()).also { pos += 4 }
                            else -> sb.append(e)
                        }
                    }

                    else -> {
                        sb.append(c)
                    }
                }
            }
        }

        private fun number(): Any {
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] in "+-.eE")) pos++
            val raw = s.substring(start, pos)
            return if (raw.any { it in ".eE" }) raw.toDouble() else raw.toLong()
        }

        private fun literal(
            word: String,
            result: Any?,
        ): Any? {
            require(s.startsWith(word, pos)) { "atteso $word alla posizione $pos" }
            pos += word.length
            return result
        }

        private fun expect(c: Char) {
            require(s[pos] == c) { "atteso '$c' alla posizione $pos, trovato '${s[pos]}'" }
            pos++
        }
    }
}
