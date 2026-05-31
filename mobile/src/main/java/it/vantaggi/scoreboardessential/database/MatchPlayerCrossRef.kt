package it.vantaggi.scoreboardessential.database

import androidx.room.Entity
import androidx.room.Index

@Entity(
    primaryKeys = ["matchId", "playerId"],
    indices = [Index(value = ["playerId"]), Index(value = ["matchId"])],
)
data class MatchPlayerCrossRef(
    val matchId: Int,
    val playerId: Int,
    /** Which team the player was on in this match (1 or 2). 0 = unknown (legacy rows). */
    val teamNumber: Int = 0,
)
