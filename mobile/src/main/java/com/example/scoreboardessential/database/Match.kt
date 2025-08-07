package com.example.scoreboardessential.database

import androidx.room.Entity
import androidx.room.PrimaryKey

import androidx.room.ForeignKey

@Entity(tableName = "matches",
    foreignKeys = [
        ForeignKey(entity = Team::class,
            parentColumns = ["id"],
            childColumns = ["team1Id"],
            onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Team::class,
            parentColumns = ["id"],
            childColumns = ["team2Id"],
            onDelete = ForeignKey.CASCADE)
    ])
data class Match(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val team1Id: Int,
    val team2Id: Int,
    val team1Score: Int,
    val team2Score: Int,
    val timestamp: Long
)
