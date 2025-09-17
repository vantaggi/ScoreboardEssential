package it.vantaggi.scoreboardessential.database

import androidx.room.Entity
import androidx.room.ForeignKey

// Tabella di associazione
@Entity(
    tableName = "player_role_cross_ref", 
    primaryKeys = ["playerId", "roleId"],
    foreignKeys = [
        ForeignKey(
            entity = Player::class,
            parentColumns = arrayOf("playerId"),
            childColumns = arrayOf("playerId"),
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Role::class,
            parentColumns = arrayOf("roleId"),
            childColumns = arrayOf("roleId"),
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class PlayerRoleCrossRef(
    val playerId: Int,
    val roleId: Int
)
