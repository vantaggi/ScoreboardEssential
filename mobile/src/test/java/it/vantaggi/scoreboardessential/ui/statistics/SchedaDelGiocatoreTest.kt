package it.vantaggi.scoreboardessential.ui.statistics

import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerSportCount
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La scheda del giocatore: un blocco per sport giocato, con i numeri di quello sport e i gol solo
 * dove si contano. Rosso se un blocco somma gli sport, se compare uno sport senza partite, se il padel
 * ha i gol, o se si leggono i numeri di un altro giocatore.
 */
class SchedaDelGiocatoreTest {
    private val marco = Player(playerId = 1, playerName = "Marco", appearances = 99, goals = 9)

    @Test
    fun `un blocco per sport giocato, nell'ordine degli sport, senza sommarli`() {
        val conteggi =
            listOf(
                PlayerSportCount(1, SportRegistry.PADEL, played = 8, won = 5),
                PlayerSportCount(1, SportRegistry.FOOTBALL, played = 12, won = 7),
            )

        assertEquals(
            listOf(
                BloccoDelloSport(SportRegistry.FOOTBALL, partite = 12, vinte = 7, gol = 9),
                BloccoDelloSport(SportRegistry.PADEL, partite = 8, vinte = 5, gol = null),
            ),
            SchedaDelGiocatore.blocchi(marco, conteggi),
        )
    }

    @Test
    fun `uno sport senza partite non compare, e i numeri degli altri giocatori non contano`() {
        val conteggi = listOf(PlayerSportCount(2, SportRegistry.TENNIS, played = 3, won = 1))

        assertEquals(
            listOf(BloccoDelloSport(SportRegistry.FOOTBALL, partite = 0, vinte = 0, gol = 9)),
            SchedaDelGiocatore.blocchi(marco, conteggi),
        )
        assertEquals(
            emptyList<BloccoDelloSport>(),
            SchedaDelGiocatore.blocchi(marco.copy(goals = 0), conteggi),
        )
    }
}
