package it.vantaggi.scoreboardessential.repository

import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerDao
import it.vantaggi.scoreboardessential.database.PlayerRoleCrossRef
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.database.Role
import kotlinx.coroutines.flow.Flow

class PlayerRepository(
    private val playerDao: PlayerDao,
) {
    val allPlayers: Flow<List<PlayerWithRoles>> = playerDao.getAllPlayers()
    val allRoles: Flow<List<Role>> = playerDao.getAllRoles()

    suspend fun insertPlayerWithRoles(
        player: Player,
        roleIds: List<Int>,
    ) {
        val playerId = playerDao.insert(player)
        val crossRefs = roleIds.map { roleId ->
            PlayerRoleCrossRef(playerId.toInt(), roleId)
        }
        playerDao.addRolesToPlayer(crossRefs)
    }

    suspend fun updatePlayerWithRoles(
        player: Player,
        roleIds: List<Int>,
    ) {
        playerDao.updatePlayerWithRoles(player, roleIds)
    }

    fun getPlayerWithRoles(playerId: Long): Flow<PlayerWithRoles?> = playerDao.getPlayerWithRoles(playerId.toInt())

    suspend fun deletePlayer(player: Player) {
        playerDao.delete(player)
    }
}
