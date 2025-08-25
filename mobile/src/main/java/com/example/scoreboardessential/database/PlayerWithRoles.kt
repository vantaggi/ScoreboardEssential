package com.example.scoreboardessential.database

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class PlayerWithRoles(
    @Embedded val player: Player,
    @Relation(
        parentColumn = "playerId",
        entityColumn = "roleId",
        associateBy = Junction(PlayerRoleCrossRef::class)
    )
    val roles: List<Role>
)
