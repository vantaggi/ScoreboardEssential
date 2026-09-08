package it.vantaggi.scoreboardessential.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Confronto differenziale contro il motore ORIGINALE di Padel Elite.
 *
 * [RacketRules] non e' un progetto nuovo: e' il port di `js/livematch.js`, il motore che il
 * proprietario usa davvero ogni settimana. Una batteria di asserzioni scritte a mano dimostra che
 * il port e' coerente con se stesso; solo un confronto con l'originale dimostra che e' FEDELE.
 *
 * `core/src/test/resources/livescoring-reference.txt` e' generato eseguendo la sezione pura di
 * `window.LiveScoring` sotto Node su 145 sequenze deterministiche, e ne registra lo stato finale.
 * Qui le stesse sequenze passano da [RacketRules] e i due stati devono coincidere.
 *
 * Le sequenze che raggiungono il 6-6 sono escluse alla generazione, e non e' una scorciatoia: e'
 * l'unico punto in cui il port DIVERGE apposta. Nel web `gameDecided()` non ha alcun ramo per il
 * tie-break e `setDecided()` si limita ad accettare il 7-6, quindi sul 6-6 il tredicesimo game
 * viene contato come un game normale. In modalita' "un tocco = un game" e' irrilevante; punto a
 * punto e' sbagliato, ed e' per questo che il port implementa il tie-break vero. Quel
 * comportamento e' coperto da [RacketRulesTest], non da qui.
 *
 * Per rigenerare il fixture: `node scratchpad/genfixture.js <percorso>`.
 */
class RacketRulesDifferentialTest {
    private fun rulesFor(configName: String): RacketRules {
        val sets = if (configName.endsWith("bo3")) 3 else 1
        val mode = if (configName.startsWith("games")) ScoringMode.GAMES else ScoringMode.POINTS
        val deuce =
            when {
                configName.contains("killer") -> DeuceRule.KILLER_POINT
                configName.contains("adv") -> DeuceRule.ADVANTAGE
                else -> DeuceRule.GOLDEN_POINT
            }
        return RacketRules(
            id = configName,
            config = SportConfig(mode = mode, deuce = deuce, sets = sets, tieBreak = true),
        )
    }

    private fun ScoreState.asRacket() = this as RacketScore

    @Test
    fun `il port riproduce il motore di Padel Elite su tutte le sequenze di riferimento`() {
        val resource =
            checkNotNull(javaClass.classLoader.getResourceAsStream("livescoring-reference.txt")) {
                "fixture di riferimento mancante: rigenerarlo con scratchpad/genfixture.js"
            }
        val righe =
            resource
                .bufferedReader()
                .readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") }

        assertTrue("il fixture sembra vuoto o troncato", righe.size >= 100)

        var confrontati = 0
        righe.forEachIndexed { indice, riga ->
            val c = riga.split("|")
            val (configName, sequenza, points, games, sets, storia, finale, vincitore, servizio) =
                listOf(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8])

            val rules = rulesFor(configName)
            var stato = rules.initial()
            // Il fixture usa 0/1 come il JS; ScoringEvent usa 1/2.
            sequenza.forEach { ch ->
                stato = rules.apply(stato, ScoringEvent.Point(side = ch.digitToInt() + 1))
            }
            val r = stato.asRacket()
            val dove = "caso #$indice [$configName] seq=${sequenza.length} punti"

            val puntiAttesi =
                when (val g = r.game) {
                    is GamePoints.Normal -> g.raw.joinToString("-")
                    is GamePoints.TieBreak -> g.points.joinToString("-")
                }
            assertEquals("$dove: punti del game", points, puntiAttesi)
            assertEquals("$dove: game nel set", games, r.gamesInSet.joinToString("-"))
            assertEquals("$dove: set vinti", sets, r.setsWon.joinToString("-"))
            assertEquals(
                "$dove: set chiusi",
                storia,
                r.closedSets.joinToString("/") { "${it.games[0]}-${it.games[1]}" },
            )
            assertEquals("$dove: punteggio finale", finale, r.finalScore?.joinToString("-") ?: "")
            assertEquals("$dove: vincitore", vincitore, r.wonBy?.toString() ?: "")
            // Nel web `server` e' un indice di squadra alternato a ogni game. Qui e' derivato da
            // un contatore monotono: serveIndex % 2 deve riprodurlo esattamente, altrimenti il
            // campo derivato non sarebbe compatibile con cio' che il web gia' scrive.
            assertEquals("$dove: lato al servizio", servizio, (r.serveIndex % 2).toString())
            confrontati++
        }
        println("confronto differenziale: $confrontati sequenze verificate contro js/livematch.js")
    }

    private operator fun <T> List<T>.component6() = this[5]

    private operator fun <T> List<T>.component7() = this[6]

    private operator fun <T> List<T>.component8() = this[7]

    private operator fun <T> List<T>.component9() = this[8]
}
