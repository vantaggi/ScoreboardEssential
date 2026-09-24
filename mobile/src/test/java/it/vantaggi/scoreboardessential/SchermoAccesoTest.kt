package it.vantaggi.scoreboardessential

import it.vantaggi.scoreboardessential.domain.models.MatchEvent
import it.vantaggi.scoreboardessential.domain.models.MatchEventType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Senza FLAG_KEEP_SCREEN_ON lo schermo si spegneva fra un punto e l'altro. Qui si prova la
 * decisione; che l'Activity la applichi alla finestra si guarda su emulatore (timeout a 15s).
 */
class SchermoAccesoTest {
    private val avvio = MatchEvent("1'", "New match ready")
    private val punto = MatchEvent("12'", "Goal", team = 1, type = MatchEventType.SCORE, engineIndex = 0)

    @Test
    fun `con un punto e la partita aperta lo schermo resta acceso`() {
        assertTrue(schermoDaTenereAcceso(listOf(punto, avvio), partitaFinita = false))
    }

    @Test
    fun `senza punti lo schermo segue il sistema`() {
        assertFalse(schermoDaTenereAcceso(null, partitaFinita = false))
        assertFalse(schermoDaTenereAcceso(emptyList(), partitaFinita = false))
        assertFalse(schermoDaTenereAcceso(listOf(avvio), partitaFinita = false))
    }

    @Test
    fun `a partita finita lo schermo torna a spegnersi`() {
        assertFalse(schermoDaTenereAcceso(listOf(punto, avvio), partitaFinita = true))
    }
}
