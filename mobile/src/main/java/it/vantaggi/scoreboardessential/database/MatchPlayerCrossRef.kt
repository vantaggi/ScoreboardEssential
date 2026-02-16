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
)
