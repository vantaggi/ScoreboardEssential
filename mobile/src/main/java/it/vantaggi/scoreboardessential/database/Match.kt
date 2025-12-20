package it.vantaggi.scoreboardessential.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "matches")
data class Match(
    @PrimaryKey(autoGenerate = true)
    val matchId: Int = 0,
    val team1Id: Int,
    val team2Id: Int,
    val team1Score: Int,
    val team2Score: Int,
    val timestamp: Long,
    val isActive: Boolean = false,
    val sport: String = "SOCCER", // Default compatibility
    val scoreDetails: String? = null // JSON blob for Padel sets/games
)
