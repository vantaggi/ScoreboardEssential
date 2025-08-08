package com.example.scoreboardessential.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(player: Player)

    @Update
    suspend fun update(player: Player)

    @Delete
    suspend fun delete(player: Player)

    @Query("SELECT * FROM players ORDER BY playerName ASC")
    fun getAllPlayers(): Flow<List<Player>>
}
