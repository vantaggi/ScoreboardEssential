package com.example.scoreboardessential.database

import androidx.room.Entity

// Tabella di associazione
@Entity(tableName = "player_role_cross_ref", primaryKeys = ["playerId", "roleId"])
data class PlayerRoleCrossRef(
    val playerId: Int,
    val roleId: Int
)
