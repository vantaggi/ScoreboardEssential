package it.vantaggi.scoreboardessential.database

/** Projection: number of matches won by a given player. */
data class PlayerWinCount(
    val playerId: Int,
    val wins: Int,
)
