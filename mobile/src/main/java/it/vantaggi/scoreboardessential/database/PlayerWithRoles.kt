package it.vantaggi.scoreboardessential.database

import android.os.Parcelable
import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import kotlinx.parcelize.Parcelize

// Data class per query con relazioni
@Parcelize
data class PlayerWithRoles(
    @Embedded val player: Player,
    @Relation(
        parentColumn = "playerId",
        entityColumn = "roleId",
        associateBy = Junction(PlayerRoleCrossRef::class)
    )
    val roles: List<Role>
) : Parcelable {
    fun getRolesText(): String {
        return if (roles.isEmpty()) {
            "No role specified"
        } else {
            roles.joinToString(", ") { it.name }
        }
    }
}
