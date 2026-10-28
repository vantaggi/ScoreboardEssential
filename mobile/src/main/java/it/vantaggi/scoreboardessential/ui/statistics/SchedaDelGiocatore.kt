package it.vantaggi.scoreboardessential.ui.statistics

import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerSportCount

/** Un blocco della scheda del giocatore: i numeri di UNO sport. I [gol] ci sono solo dove si dice chi ha segnato. */
data class BloccoDelloSport(
    val sportId: String,
    val partite: Int,
    val vinte: Int,
    val gol: Int?,
)

/**
 * La scheda del giocatore: un blocco per ogni sport che ha giocato, nell'ordine in cui gli sport si
 * mostrano. Uno sport senza partite e senza gol non compare: la scheda dice cosa c'e', non cosa manca.
 * Le partite sono quelle chiuse dello sport ([PlayerSportCount]), non il totale della colonna del
 * giocatore, che somma tutti gli sport.
 */
object SchedaDelGiocatore {
    fun blocchi(
        player: Player,
        conteggi: List<PlayerSportCount>,
    ): List<BloccoDelloSport> =
        SportRegistry.selectable().mapNotNull { regole ->
            val dello = conteggi.firstOrNull { it.playerId == player.playerId && it.sportId == regole.id }
            val gol = if (StatisticsViewModel.contaIGol(regole.id)) player.goals else null
            val blocco = BloccoDelloSport(regole.id, dello?.played ?: 0, dello?.won ?: 0, gol)
            blocco.takeIf { it.partite > 0 || (it.gol ?: 0) > 0 }
        }
}
