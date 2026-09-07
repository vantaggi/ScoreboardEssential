package it.vantaggi.scoreboardessential.database

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

// Data class per query con relazioni
data class PlayerWithRoles(
    @Embedded val player: Player,
    @Relation(
        parentColumn = "playerId",
        entityColumn = "roleId",
        associateBy = Junction(PlayerRoleCrossRef::class),
    )
    val roles: List<Role>,
) {
    fun getRolesText(): String =
        if (roles.isEmpty()) {
            "No role specified"
        } else {
            roles.joinToString(", ") { it.name }
        }
}
