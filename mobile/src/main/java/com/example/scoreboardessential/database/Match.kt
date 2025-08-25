package com.example.scoreboardessential.database

import androidx.room.Entity
import androidx.room.PrimaryKey

import androidx.room.ColumnInfo

@Entity(tableName = "matches")
data class Match(
    @PrimaryKey(autoGenerate = true)
    val matchId: Long = 0,
    val team1Id: Int,
    val team2Id: Int,
    val team1Score: Int,
    val team2Score: Int,
    val timestamp: Long,
    @ColumnInfo(defaultValue = "0")
    val isActive: Boolean = false
)
