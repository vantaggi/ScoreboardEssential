package com.example.scoreboardessential.repository

import com.example.scoreboardessential.database.Player
import com.example.scoreboardessential.database.PlayerDao
import com.example.scoreboardessential.database.PlayerWithRoles
import com.example.scoreboardessential.database.Role
import kotlinx.coroutines.flow.Flow

class PlayerRepository(private val playerDao: PlayerDao) {

    val allPlayers: Flow<List<PlayerWithRoles>> = playerDao.getAllPlayers()

    val allRoles: Flow<List<Role>> = playerDao.getAllRoles()

    suspend fun insertPlayerWithRoles(player: Player, roleIds: List<Int>) {
        val playerId = playerDao.insert(player)
        roleIds.forEach { roleId ->
            playerDao.addRoleToPlayer(com.example.scoreboardessential.database.PlayerRoleCrossRef(playerId.toInt(), roleId))
        }
    }

    suspend fun updatePlayerWithRoles(player: Player, roleIds: List<Int>) {
        playerDao.updatePlayerWithRoles(player, roleIds)
    }

    suspend fun deletePlayer(player: Player) {
        playerDao.delete(player)
    }
}
