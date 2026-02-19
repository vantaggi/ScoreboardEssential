package it.vantaggi.scoreboardessential.repository

import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerDao
import it.vantaggi.scoreboardessential.database.PlayerRoleCrossRef
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.database.Role
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing Player data.
 * Acts as a single source of truth for player-related data, handling interactions with the database through [PlayerDao].
 *
 * @property playerDao The Data Access Object for player operations.
 */
class PlayerRepository(
    private val playerDao: PlayerDao,
) {
    /** Flow of all players currently in the database, including their assigned roles. */
    val allPlayers: Flow<List<PlayerWithRoles>> = playerDao.getAllPlayers()

    /** Flow of all available roles in the database. */
    val allRoles: Flow<List<Role>> = playerDao.getAllRoles()

    /**
     * Inserts a new player and assigns them the specified roles.
     * This operation is transactional: it inserts the player first, then creates the cross-reference entries for roles.
     *
     * @param player The [Player] entity to insert.
     * @param roleIds A list of role IDs to associate with the new player.
     */
    suspend fun insertPlayerWithRoles(
        player: Player,
        roleIds: List<Int>,
    ) {
        val playerId = playerDao.insert(player)
        val crossRefs =
            roleIds.map { roleId ->
                PlayerRoleCrossRef(playerId.toInt(), roleId)
            }
        playerDao.addRolesToPlayer(crossRefs)
    }

    /**
     * Updates an existing player's information and their role assignments.
     *
     * @param player The [Player] entity with updated information.
     * @param roleIds The new list of role IDs to associate with the player. Previous role associations are replaced.
     */
    suspend fun updatePlayerWithRoles(
        player: Player,
        roleIds: List<Int>,
    ) {
        playerDao.updatePlayerWithRoles(player, roleIds)
    }

    /**
     * Retrieves a specific player and their roles by ID.
     *
     * @param playerId The unique ID of the player.
     * @return A [Flow] emitting the [PlayerWithRoles] object, or null if not found.
     */
    fun getPlayerWithRoles(playerId: Long): Flow<PlayerWithRoles?> = playerDao.getPlayerWithRoles(playerId.toInt())

    /**
     * Deletes a player from the database.
     *
     * @param player The [Player] entity to delete.
     */
    suspend fun deletePlayer(player: Player) {
        playerDao.delete(player)
    }
}
