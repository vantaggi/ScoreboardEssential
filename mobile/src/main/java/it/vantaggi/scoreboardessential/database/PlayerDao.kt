package it.vantaggi.scoreboardessential.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(player: Player): Long

    @Update
    suspend fun update(player: Player)

    @Delete
    suspend fun delete(player: Player)

    @Transaction
    @Query("SELECT * FROM players ORDER BY playerName ASC")
    fun getAllPlayers(): Flow<List<PlayerWithRoles>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addRoleToPlayer(crossRef: PlayerRoleCrossRef)

    @Delete
    suspend fun removeRoleFromPlayer(crossRef: PlayerRoleCrossRef)

    @Query("SELECT * FROM roles ORDER BY category, name ASC")
    fun getAllRoles(): Flow<List<Role>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRole(role: Role)

    @Transaction
    suspend fun updatePlayerWithRoles(
        player: Player,
        roleIds: List<Int>,
    ) {
        update(player)
        deleteAllRolesForPlayer(player.playerId)
        roleIds.forEach { roleId ->
            addRoleToPlayer(PlayerRoleCrossRef(player.playerId, roleId))
        }
    }

    @Query("DELETE FROM player_role_cross_ref WHERE playerId = :playerId")
    suspend fun deleteAllRolesForPlayer(playerId: Int)

    @Transaction
    @Query("SELECT * FROM players WHERE playerId = :playerId")
    fun getPlayerWithRoles(playerId: Int): Flow<PlayerWithRoles?>
}
