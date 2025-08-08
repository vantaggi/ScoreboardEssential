package com.example.scoreboardessential.database

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity(tableName = "players")
data class Player(
    @PrimaryKey(autoGenerate = true)
    val playerId: Int = 0,
    val playerName: String,
    val roles: String,
    var appearances: Int,
    var goals: Int
) : Parcelable
