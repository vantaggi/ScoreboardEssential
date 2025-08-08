package com.example.scoreboardessential.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "matches",
    foreignKeys = [
        ForeignKey(
            entity = Team::class,
            parentColumns = ["id"],
            childColumns = ["team1Id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Team::class,
            parentColumns = ["id"],
            childColumns = ["team2Id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["team1Id"]), Index(value = ["team2Id"])]
)
data class Match(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val team1Id: Int,
    val team2Id: Int,
    val team1Score: Int,
    val team2Score: Int,
    val timestamp: Long
)
