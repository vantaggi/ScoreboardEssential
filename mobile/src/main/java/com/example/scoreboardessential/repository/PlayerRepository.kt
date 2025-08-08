package com.example.scoreboardessential.repository

import com.example.scoreboardessential.database.Player
import com.example.scoreboardessential.database.PlayerDao
import kotlinx.coroutines.flow.Flow

class PlayerRepository(private val playerDao: PlayerDao) {

    val allPlayers: Flow<List<Player>> = playerDao.getAllPlayers()

    suspend fun insert(player: Player) {
        playerDao.insert(player)
    }
}
