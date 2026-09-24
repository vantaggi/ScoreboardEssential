package it.vantaggi.scoreboardessential.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId

class MatchExportTest {
    /** Lato 1: Marco e Luca. Lato 2: Anna e Sara. L'ordine e' quello del roster, non del servizio. */
    private val roster =
        listOf(
            MatchPlayer(localId = 1, name = "Marco", side = 1),
            MatchPlayer(localId = 2, name = "Anna", side = 2),
            MatchPlayer(localId = 3, name = "Luca", side = 1),
            MatchPlayer(localId = 4, name = "Sara", side = 2),
        )

    /** Il 23 settembre 2026 alle 21:04 a Roma, ora legale: 19:04 a Greenwich. */
    private val inizio = OffsetDateTime.parse("2026-09-23T21:04:00+02:00").toInstant().toEpochMilli()

    private val origin =
        ExportOrigin(
            matchId = "3f2a9c1e-5b7d-4e8a-9c01-2d4f6a8b0c1e",
            startedAtMillis = inizio,
            zone = ZoneId.of("Europe/Rome"),
            appVersion = "1.4.0",
        )

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
        val export = ready(MatchExporter.build(setVinto(), roster, origin))
        assertEquals(MatchExporter.FORMAT_VERSION, export.formatVersion)
        assertEquals(SportRegistry.PADEL, export.sportId)
        assertEquals(6, export.scoreTeam1)
        assertEquals(0, export.scoreTeam2)
        assertEquals(1, export.winnerTeam)
        assertEquals(listOf(listOf(6, 0)), export.setScores)
        assertEquals(24, export.timeline.size)
        assertEquals(roster, export.players)
        assertEquals(listOf(1, 2, 1, 2), export.players.map { it.side })
    }

    /** Il servitore non e' nel log: si ricava dallo stato PRIMA del punto, e ruota a ogni game. */
    @Test
    fun ilServitoreRuotaAOgniGame() {
        val timeline = ready(MatchExporter.build(setVinto(), roster, origin)).timeline
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
        val export = ready(MatchExporter.build(setVinto(order = emptyList()), roster, origin))
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

        val export = ready(MatchExporter.build(engine, roster, origin))
        assertEquals(24, export.timeline.size)
    }

    @Test
    fun ilJsonContieneICampiCheLaDashboardSiAspetta() {
        val json = MatchExporter.toJson(ready(MatchExporter.build(setVinto(), roster, origin)))
        assertTrue(json, json.startsWith("{\"formatVersion\":2,\"sportId\":\"padel\","))
        assertTrue(json, json.contains("\"config\":{\"mode\":\"POINTS\",\"deuce\":\"GOLDEN_POINT\",\"sets\":1,"))
        assertTrue(json, json.contains("\"serveOrder\":[1,2,3,4]}"))
        assertTrue(json, json.contains("{\"localId\":1,\"name\":\"Marco\",\"side\":1}"))
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
        val json = MatchExporter.toJson(ready(MatchExporter.build(setVinto(), players, origin)))
        assertTrue(json, json.contains("\"name\":\"Nicol\u00F2 \\\"Nico\\\" Ro\\\\ssi\\nfine\\u0001\""))
        assertFalse(json, json.contains("\\u00f2"))
    }

    @Test
    fun menoDiQuattroGiocatori() {
        assertEquals(
            listOf(ExportProblem.WrongPlayerCount(3)),
            MatchExporter.validate(setVinto(), roster.take(3)),
        )
    }

    @Test
    fun giocatoriDuplicati() {
        val conDoppione = listOf(roster[0], roster[1], roster[0], roster[3])
        assertEquals(
            listOf(ExportProblem.DuplicatePlayers(listOf("Marco"))),
            MatchExporter.validate(setVinto(), conDoppione),
        )
    }

    /**
     * Il numero Padel Elite non esiste piu', per scelta del proprietario: l'app e' a se'. Bastano
     * i nomi del tabellone, e nel file non resta traccia del campo: i giocatori li sceglie chi
     * importa, seguendo l'ordine di servizio.
     */
    @Test
    fun bastanoINomiDelTabelloneENelFileNonCeIlNumeroPadelElite() {
        assertEquals(emptyList<ExportProblem>(), MatchExporter.validate(setVinto(), roster))
        val json = MatchExporter.toJson(ready(MatchExporter.build(setVinto(), roster, origin)))
        assertFalse(json, json.contains("padelPlayerId"))
    }

    /** I tre campi del formato 2, nella forma che la dashboard valida. */
    @Test
    fun ilFormato2PortaIdInizioConOffsetEVersione() {
        val export = ready(MatchExporter.build(setVinto(), roster, origin))
        assertEquals(2, export.formatVersion)
        assertEquals("3f2a9c1e-5b7d-4e8a-9c01-2d4f6a8b0c1e", export.matchId)
        assertEquals("2026-09-23T21:04:00+02:00", export.startedAt)
        assertEquals("1.4.0", export.appVersion)
        val json = MatchExporter.toJson(export)
        assertTrue(
            json,
            json.startsWith(
                "{\"formatVersion\":2,\"sportId\":\"padel\",\"matchId\":\"3f2a9c1e-5b7d-4e8a-9c01-2d4f6a8b0c1e\"," +
                    "\"startedAt\":\"2026-09-23T21:04:00+02:00\",\"appVersion\":\"1.4.0\",\"config\":",
            ),
        )
    }

    /**
     * L'offset e' quello del GIORNO della partita, non quello di oggi: d'inverno Roma e' a +01:00.
     * E a Greenwich si scrive `+00:00`, non `Z`: una forma sola per chi legge.
     */
    @Test
    fun lOffsetSegueLOraLegaleEaGreenwichRestaInCifre() {
        val gennaio = OffsetDateTime.parse("2026-01-10T18:30:00Z").toInstant().toEpochMilli()
        val inverno = origin.copy(startedAtMillis = gennaio)
        assertEquals("2026-01-10T19:30:00+01:00", ready(MatchExporter.build(setVinto(), roster, inverno)).startedAt)
        val greenwich = inverno.copy(zone = ZoneId.of("UTC"))
        assertEquals("2026-01-10T18:30:00+00:00", ready(MatchExporter.build(setVinto(), roster, greenwich)).startedAt)
    }

    /**
     * Le partite salvate prima del formato 2 non hanno ne' id ne' inizio: i campi mancano invece
     * di valere null, e chi importa ripiega su cio' che faceva con la versione 1.
     */
    @Test
    fun senzaIdNeInizioICampiMancanoDalFile() {
        val vecchia = origin.copy(matchId = null, startedAtMillis = null)
        val json = MatchExporter.toJson(ready(MatchExporter.build(setVinto(), roster, vecchia)))
        assertFalse(json, json.contains("matchId"))
        assertFalse(json, json.contains("startedAt"))
        assertTrue(json, json.contains(",\"appVersion\":\"1.4.0\","))
    }

    @Test
    fun latiSbilanciati() {
        val treControUno = roster.mapIndexed { i, player -> if (i == 1) player.copy(side = 1) else player }
        assertEquals(
            listOf(ExportProblem.UnbalancedSides(3, 1)),
            MatchExporter.validate(setVinto(), treControUno),
        )
    }

    @Test
    fun partitaSenzaEventi() {
        val engine = MatchEngine(SportRegistry.forMatch(SportRegistry.PADEL, serveOrder))
        assertEquals(listOf(ExportProblem.NoPoints), MatchExporter.validate(engine, roster))
    }

    @Test
    fun unLogDiSoleCorrezioniNonEUnaPartita() {
        val engine = MatchEngine(SportRegistry.forMatch(SportRegistry.PADEL, serveOrder))
        repeat(3) { engine.apply(ScoringEvent.Correction(side = 1)) }
        assertEquals(listOf(ExportProblem.NoPoints), MatchExporter.validate(engine, roster))
    }

    /** La validazione non lancia mai: build restituisce l'elenco completo di cio' che manca. */
    @Test
    fun buildIncompletoElencaTuttiIProblemi() {
        val engine = MatchEngine(SportRegistry.forMatch(SportRegistry.PADEL, serveOrder))
        val result = MatchExporter.build(engine, emptyList(), origin)
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

        val export = ready(MatchExporter.build(engine, roster, origin))
        assertEquals(201, export.timeline.size)
        assertEquals(2, export.scoreTeam1)
        assertEquals(1, export.scoreTeam2)
        assertEquals(listOf(listOf(7, 5), listOf(6, 7), listOf(6, 3)), export.setScores)

        val bytes = MatchExporter.toJson(export).toByteArray(Charsets.UTF_8).size
        assertTrue("export di $bytes byte", bytes in 8_000..16_000)
    }

    /**
     * Il fixture condiviso con la dashboard (richiesta 3 di `docs/dashboard/SCOREBOARD_FORMAT.md`):
     * `src/test/resources/export-v2-sample.json`, che la dashboard copia fra i suoi test e usa
     * come confronto fra i due motori.
     *
     * La partita e' la stessa di `scoreboard/v1-tre-set.json`, la partita a tre set del brief
     * della Cronaca: 189 punti, 7-6 3-6 7-5, i valori attesi scritti nel brief. Qui se ne
     * prendono SOLO i vincitori e i tempi dei punti e la si rigioca col nostro motore: il
     * servitore di ogni punto e i set li calcola `RacketRules`, e devono tornare identici a
     * quelli del file generato dal copione della dashboard. Poi si controlla che, tolti i campi
     * nuovi, il file v2 sia byte per byte il file v1 senza `padelPlayerId`: e' la prova che i
     * campi v1 non hanno cambiato ne' nome ne' significato.
     *
     * Deterministico: id, inizio e versione sono fissi. Se il file committato non coincide con
     * quello prodotto, il test lo riscrive e fallisce: si guarda la differenza e la si committa.
     */
    @Test
    fun ilFixtureV2CondivisoConLaDashboard() {
        val v1 = File("src/test/resources/scoreboard/v1-tre-set.json").readText(Charsets.UTF_8).trim()
        val punti = Regex("\\{\"side\":(\\d),\"servingPlayerId\":(\\d),\"atMillis\":(\\d+)\\}").findAll(v1).toList()
        assertEquals(189, punti.size)

        val rules =
            RacketRules(id = SportRegistry.PADEL, config = SportConfig(sets = 3, serveOrder = serveOrder))
        val engine = MatchEngine(rules)
        punti.forEach { engine.apply(ScoringEvent.Point(side = it.groupValues[1].toInt()), it.groupValues[3].toLong()) }

        val export = ready(MatchExporter.build(engine, roster, origin))
        assertEquals(listOf(listOf(7, 6), listOf(3, 6), listOf(7, 5)), export.setScores)
        assertEquals(1, export.winnerTeam)
        assertEquals(punti.map { it.groupValues[2].toInt() }, export.timeline.map { it.servingPlayerId })

        val json = MatchExporter.toJson(export)
        val intestazioneV2 =
            ",\"matchId\":\"3f2a9c1e-5b7d-4e8a-9c01-2d4f6a8b0c1e\",\"startedAt\":\"2026-09-23T21:04:00+02:00\"," +
                "\"appVersion\":\"1.4.0\""
        assertEquals(
            v1.replace(Regex(",\"padelPlayerId\":\\d+"), ""),
            json.replace(intestazioneV2, "").replaceFirst("\"formatVersion\":2", "\"formatVersion\":1"),
        )

        val fixture = File("src/test/resources/export-v2-sample.json")
        // trimEnd: con core.autocrlf il ritorno a capo finale torna dal checkout come CRLF.
        val committato = if (fixture.exists()) fixture.readText(Charsets.UTF_8).trimEnd() else null
        if (committato != json) {
            fixture.writeText(json + "\n", Charsets.UTF_8)
            fail("export-v2-sample.json era diverso da quello prodotto ed e' stato riscritto: controllalo e committalo")
        }
    }
}
