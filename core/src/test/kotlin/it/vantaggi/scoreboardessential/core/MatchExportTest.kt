package it.vantaggi.scoreboardessential.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchExportTest {
    /** Lato 1: Marco e Luca. Lato 2: Anna e Sara. L'ordine e' quello del roster, non del servizio. */
    private val roster =
        listOf(
            MatchPlayer(localId = 1, name = "Marco", side = 1),
            MatchPlayer(localId = 2, name = "Anna", side = 2),
            MatchPlayer(localId = 3, name = "Luca", side = 1),
            MatchPlayer(localId = 4, name = "Sara", side = 2),
        )

    private val padelIds = mapOf(1 to 101, 2 to 102, 3 to 103, 4 to 104)

    /** A1, B1, A2, B2: id locali, come li sceglie l'utente a inizio partita. */
    private val serveOrder = listOf(1, 2, 3, 4)

    /** Set unico di padel vinto 6-0 dal lato 1: col punto secco un game sono esattamente 4 punti. */
    private fun setVinto(order: List<Int> = serveOrder): MatchEngine {
        val engine = MatchEngine(SportRegistry.forMatch(SportRegistry.PADEL, order))
        repeat(24) { engine.apply(ScoringEvent.Point(side = 1), atMillis = it * 30_000L) }
        return engine
    }

    private fun ready(result: ExportResult): MatchExport {
        assertTrue("atteso Ready, ottenuto $result", result is ExportResult.Ready)
        return (result as ExportResult.Ready).export
    }

    @Test
    fun costruzioneDaUnSetVinto() {
        val export = ready(MatchExporter.build(setVinto(), roster, padelIds))
        assertEquals(MatchExporter.FORMAT_VERSION, export.formatVersion)
        assertEquals(SportRegistry.PADEL, export.sportId)
        assertEquals(6, export.scoreTeam1)
        assertEquals(0, export.scoreTeam2)
        assertEquals(1, export.winnerTeam)
        assertEquals(listOf(listOf(6, 0)), export.setScores)
        assertEquals(24, export.timeline.size)
        assertEquals(listOf(101, 102, 103, 104), export.players.map { it.padelPlayerId })
        assertEquals(listOf(1, 2, 1, 2), export.players.map { it.side })
    }

    /** Il servitore non e' nel log: si ricava dallo stato PRIMA del punto, e ruota a ogni game. */
    @Test
    fun ilServitoreRuotaAOgniGame() {
        val timeline = ready(MatchExporter.build(setVinto(), roster, padelIds)).timeline
        assertEquals(1, timeline[0].servingPlayerId)
        assertEquals(1, timeline[3].servingPlayerId)
        assertEquals(2, timeline[4].servingPlayerId)
        assertEquals(3, timeline[8].servingPlayerId)
        assertEquals(4, timeline[12].servingPlayerId)
        assertEquals(1, timeline[16].servingPlayerId)
        assertEquals(0L, timeline[0].atMillis)
        assertEquals(690_000L, timeline[23].atMillis)
    }

    @Test
    fun senzaOrdineDiServizioIlCampoENulloMaLExportResta() {
        val export = ready(MatchExporter.build(setVinto(order = emptyList()), roster, padelIds))
        assertTrue(export.timeline.all { it.servingPlayerId == null })
        assertEquals(1, export.winnerTeam)
        assertTrue(MatchExporter.toJson(export).contains("\"servingPlayerId\":null"))
    }

    /** Correzioni e tocchi a partita finita restano nel log ma non sono punti giocati. */
    @Test
    fun soloCioCheHaCambiatoIlPunteggioEntraNellaTimeline() {
        // Il motore non scrive piu' gli eventi senza effetto, ma un registro SALVATO dalle
        // versioni che lo facevano puo' contenerli: l'export deve continuare a saltarli, o quelle
        // partite arriverebbero a Padel Elite con punti fantasma. Si ricostruisce quel caso.
        val engine = setVinto()
        val conRumore =
            engine.log +
                listOf(
                    LoggedEvent(ScoringEvent.Correction(side = 1)),
                    LoggedEvent(ScoringEvent.Point(side = 2), 900_000L),
                )
        engine.restoreLog(conRumore)
        assertEquals(26, engine.log.size)

        val export = ready(MatchExporter.build(engine, roster, padelIds))
        assertEquals(24, export.timeline.size)
    }

    @Test
    fun ilJsonContieneICampiCheLaDashboardSiAspetta() {
        val json = MatchExporter.toJson(ready(MatchExporter.build(setVinto(), roster, padelIds)))
        assertTrue(json, json.startsWith("{\"formatVersion\":1,\"sportId\":\"padel\","))
        assertTrue(json, json.contains("\"config\":{\"mode\":\"POINTS\",\"deuce\":\"GOLDEN_POINT\",\"sets\":1,"))
        assertTrue(json, json.contains("\"serveOrder\":[1,2,3,4]}"))
        assertTrue(json, json.contains("{\"localId\":1,\"name\":\"Marco\",\"side\":1,\"padelPlayerId\":101}"))
        assertTrue(json, json.contains("\"scoreTeam1\":6,\"scoreTeam2\":0,\"winnerTeam\":1"))
        assertTrue(json, json.contains("\"setScores\":[[6,0]]"))
        assertTrue(json, json.contains("\"timeline\":[{\"side\":1,\"servingPlayerId\":1,\"atMillis\":0},"))
        assertTrue(json, json.endsWith("}]}"))
    }

    /**
     * L'escaping e' il punto in cui una serializzazione fatta a mano sbaglia: virgolette e
     * backslash vanno protetti, i caratteri di controllo diventano `\uXXXX`, gli accenti NO --
     * sono stampabili e restano tali, il file si scrive in UTF-8.
     */
    @Test
    fun lEscapingDelleStringheRegge() {
        // Il sorgente resta in puro ASCII come il resto del modulo: l'accento e' un escape
        // qui, ma a runtime e' un carattere vero, ed e' quello che il test verifica.
        val nome = "Nicol\u00F2 \"Nico\" Ro\\ssi\nfine\u0001"
        val players = roster.map { if (it.localId == 1) it.copy(name = nome) else it }
        val json = MatchExporter.toJson(ready(MatchExporter.build(setVinto(), players, padelIds)))
        assertTrue(json, json.contains("\"name\":\"Nicol\u00F2 \\\"Nico\\\" Ro\\\\ssi\\nfine\\u0001\""))
        assertFalse(json, json.contains("\\u00f2"))
    }

    @Test
    fun menoDiQuattroGiocatori() {
        assertEquals(
            listOf(ExportProblem.WrongPlayerCount(3)),
            MatchExporter.validate(setVinto(), roster.take(3), padelIds),
        )
    }

    @Test
    fun giocatoriDuplicati() {
        val conDoppione = listOf(roster[0], roster[1], roster[0], roster[3])
        assertEquals(
            listOf(ExportProblem.DuplicatePlayers(listOf("Marco"))),
            MatchExporter.validate(setVinto(), conDoppione, padelIds),
        )
    }

    /** L'interfaccia deve poter dire CHI collegare, non quanti: qui si verificano i nomi. */
    @Test
    fun giocatoriNonCollegatiAPadelElite() {
        assertEquals(
            listOf(ExportProblem.UnlinkedPlayers(listOf("Anna", "Sara"))),
            MatchExporter.validate(setVinto(), roster, mapOf(1 to 101, 3 to 103)),
        )
    }

    @Test
    fun latiSbilanciati() {
        val treControUno = roster.mapIndexed { i, player -> if (i == 1) player.copy(side = 1) else player }
        assertEquals(
            listOf(ExportProblem.UnbalancedSides(3, 1)),
            MatchExporter.validate(setVinto(), treControUno, padelIds),
        )
    }

    @Test
    fun partitaSenzaEventi() {
        val engine = MatchEngine(SportRegistry.forMatch(SportRegistry.PADEL, serveOrder))
        assertEquals(listOf(ExportProblem.NoPoints), MatchExporter.validate(engine, roster, padelIds))
    }

    @Test
    fun unLogDiSoleCorrezioniNonEUnaPartita() {
        val engine = MatchEngine(SportRegistry.forMatch(SportRegistry.PADEL, serveOrder))
        repeat(3) { engine.apply(ScoringEvent.Correction(side = 1)) }
        assertEquals(listOf(ExportProblem.NoPoints), MatchExporter.validate(engine, roster, padelIds))
    }

    /** La validazione non lancia mai: build restituisce l'elenco completo di cio' che manca. */
    @Test
    fun buildIncompletoElencaTuttiIProblemi() {
        val engine = MatchEngine(SportRegistry.forMatch(SportRegistry.PADEL, serveOrder))
        val result = MatchExporter.build(engine, emptyList(), emptyMap())
        assertTrue("atteso Incomplete, ottenuto $result", result is ExportResult.Incomplete)
        val problems = (result as ExportResult.Incomplete).problems
        assertEquals(listOf(ExportProblem.WrongPlayerCount(0), ExportProblem.NoPoints), problems)
    }

    /**
     * Una partita di padel vera, al meglio di tre set con un tie-break: 201 punti.
     *
     * La sequenza dei vincitori viene da un generatore lineare a seme fisso, quindi il risultato
     * e' riproducibile ed e' lecito asserirlo esatto. Serve a misurare il file: la cronologia e'
     * il grosso dell'export, e va verificato che resti nell'ordine dei kilobyte -- se un giorno
     * qualcuno duplicasse set e game dentro ogni punto, questo test se ne accorgerebbe.
     */
    @Test
    fun unaPartitaRealisticaStaInPochiKilobyte() {
        val rules = RacketRules(id = SportRegistry.PADEL, config = SportConfig(sets = 3, serveOrder = serveOrder))
        val engine = MatchEngine(rules)
        var seed = 48L
        var i = 0
        while (engine.state.wonBy == null && i < 400) {
            seed = (seed * 1_103_515_245L + 12_345L) and 0x7FFFFFFFL
            engine.apply(ScoringEvent.Point(side = if (seed % 100 < 50) 1 else 2), atMillis = i * 25_000L)
            i++
        }

        val export = ready(MatchExporter.build(engine, roster, padelIds))
        assertEquals(201, export.timeline.size)
        assertEquals(2, export.scoreTeam1)
        assertEquals(1, export.scoreTeam2)
        assertEquals(listOf(listOf(7, 5), listOf(6, 7), listOf(6, 3)), export.setScores)

        val bytes = MatchExporter.toJson(export).toByteArray(Charsets.UTF_8).size
        assertTrue("export di $bytes byte", bytes in 8_000..16_000)
    }
}
