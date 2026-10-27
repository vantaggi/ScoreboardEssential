package it.vantaggi.scoreboardessential.database

/**
 * Proiezione: le partite chiuse di un giocatore in uno sport, giocate e vinte. Si ricava dalle
 * formazioni salvate ([MatchPlayerCrossRef.teamNumber]) e dal risultato, come [PlayerWinCount].
 */
data class PlayerRecord(
    val playerId: Int,
    val playerName: String,
    val played: Int,
    val won: Int,
)
