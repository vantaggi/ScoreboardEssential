package it.vantaggi.scoreboardessential.database

/**
 * Proiezione: le partite chiuse di un giocatore in UNO sport, giocate e vinte. Si ricava dalle
 * formazioni salvate ([MatchPlayerCrossRef.teamNumber]) e dal risultato, come [PlayerRecord], ma
 * con lo sport in colonna: da una sola lettura si ricavano la classifica di ogni sport e, per un
 * giocatore, un blocco per sport.
 *
 * Esiste perche' [Player.appearances] e' un totale unico, che somma calcio, padel e tennis: chi
 * gioca a piu' sport vedeva nel calcio anche le partite di padel.
 */
data class PlayerSportCount(
    val playerId: Int,
    val sportId: String,
    val played: Int,
    val won: Int,
)

/** Le partite giocate in [sportId], per giocatore. Chi non ne ha giocate non compare. */
fun List<PlayerSportCount>.playedIn(sportId: String): Map<Int, Int> =
    filter { it.sportId == sportId }.associate { it.playerId to it.played }

/** Le partite vinte in [sportId], per giocatore. */
fun List<PlayerSportCount>.wonIn(sportId: String): Map<Int, Int> =
    filter { it.sportId == sportId }.associate { it.playerId to it.won }
