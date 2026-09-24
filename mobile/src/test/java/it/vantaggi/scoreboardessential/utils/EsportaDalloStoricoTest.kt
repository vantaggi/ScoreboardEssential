package it.vantaggi.scoreboardessential.utils

import it.vantaggi.scoreboardessential.core.ExportProblem
import it.vantaggi.scoreboardessential.core.ExportResult
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchExport
import it.vantaggi.scoreboardessential.core.MatchExporter
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.MatchPlayer
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchWithTeams
import it.vantaggi.scoreboardessential.ui.MatchHistoryUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * L'export di una partita gia' chiusa: la riga dello storico e il suo schieramento bastano a
 * rifare lo stesso file che si sarebbe esportato dal vivo.
 */
class EsportaDalloStoricoTest {
    private val schieramento =
        listOf(
            MatchPlayer(localId = 3, name = "Marco", side = 1),
            MatchPlayer(localId = 8, name = "Luca", side = 1),
            MatchPlayer(localId = 7, name = "Anna", side = 2),
            MatchPlayer(localId = 4, name = "Sara", side = 2),
        )

    /** Due game vinti dal lato 1: col punto secco un game sono esattamente 4 punti. */
    private val registro =
        MatchLogCodec.encode(List(8) { LoggedEvent(ScoringEvent.Point(side = 1), it * 30_000L) })

    private val chiusa =
        Match(
            matchId = 12,
            team1Id = 1,
            team2Id = 2,
            team1Score = 0,
            team2Score = 0,
            timestamp = 1_790_193_000_000L,
            sportId = SportRegistry.PADEL,
            eventLog = registro,
            serveOrder = "3,7,8,4",
            startedAt = 1_790_190_240_000L,
            matchUuid = "3f2a9c1e-5b7d-4e8a-9c01-2d4f6a8b0c1e",
        )

    private fun ready(result: ExportResult): MatchExport {
        assertTrue("atteso Ready, ottenuto $result", result is ExportResult.Ready)
        return (result as ExportResult.Ready).export
    }

    private fun esporta(match: Match) = MatchExportUtils.savedMatchExport(match, schieramento, "1.0", ZoneId.of("Europe/Rome"))

    /**
     * Il servitore non e' nel registro: si ricava con l'ordine salvato sulla riga. Senza, dallo
     * storico ogni punto avrebbe servitore null anche se la partita l'aveva.
     */
    @Test
    fun `il motore si rifa' con l'ordine di servizio e l'id salvati`() {
        val export = ready(esporta(chiusa))
        assertEquals(listOf(3, 7, 8, 4), export.config.serveOrder)
        assertEquals(3, export.timeline[0].servingPlayerId)
        assertEquals(7, export.timeline[4].servingPlayerId)
        assertEquals("3f2a9c1e-5b7d-4e8a-9c01-2d4f6a8b0c1e", export.matchId)
        // 1_790_190_240_000 e' il 23 settembre 2026 alle 19:04 di Greenwich, 21:04 a Roma.
        assertEquals("2026-09-23T21:04:00+02:00", export.startedAt)
        assertEquals(schieramento, export.players)
        assertEquals(8, export.timeline.size)
    }

    /** Una partita salvata prima della versione 14: si esporta, senza i campi che non si sanno. */
    @Test
    fun `una partita di prima della 14 si esporta senza id ne' inizio ne' servitori`() {
        val vecchia = chiusa.copy(serveOrder = "", startedAt = null, matchUuid = null)
        val export = ready(esporta(vecchia))
        assertTrue(export.timeline.all { it.servingPlayerId == null })
        val json = MatchExporter.toJson(export)
        assertFalse(json, json.contains("matchId"))
        assertFalse(json, json.contains("startedAt"))
    }

    /** Meglio nessun file che una partita ricostruita a meta'. */
    @Test
    fun `un registro illeggibile non produce un file`() {
        val rotta = chiusa.copy(eventLog = "99|qualcosa")
        assertEquals(ExportResult.Incomplete(listOf(ExportProblem.NoPoints)), esporta(rotta))
    }

    @Test
    fun `l'ordine di servizio si scrive e si rilegge uguale, e un valore rotto vale come assente`() {
        assertEquals("3,7,8,4", Match.encodeServeOrder(listOf(3, 7, 8, 4)))
        assertEquals(listOf(3, 7, 8, 4), Match.decodeServeOrder("3,7,8,4"))
        assertEquals(emptyList<Int>(), Match.decodeServeOrder(""))
        assertEquals(emptyList<Int>(), Match.decodeServeOrder("3,x,8,4"))
    }

    /** Il comando "Esporta" dello storico: padel, chiusa, con un registro. */
    @Test
    fun `lo storico offre l'export solo per il padel chiuso con registro`() {
        fun offre(match: Match) = MatchHistoryUiState(MatchWithTeams(match, null, null, emptyList()), "").canExport
        assertTrue(offre(chiusa))
        assertFalse("calcio", offre(chiusa.copy(sportId = SportRegistry.FOOTBALL)))
        assertFalse("partita ancora viva", offre(chiusa.copy(isActive = true)))
        assertFalse("solo punteggio finale", offre(chiusa.copy(eventLog = "")))
    }
}
