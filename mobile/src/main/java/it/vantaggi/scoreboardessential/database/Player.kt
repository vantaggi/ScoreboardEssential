package it.vantaggi.scoreboardessential.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "players")
data class Player(
    @PrimaryKey(autoGenerate = true)
    val playerId: Int = 0,
    val playerName: String,
    var appearances: Int,
    var goals: Int,
)
