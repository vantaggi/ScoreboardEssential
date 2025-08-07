package com.example.scoreboardessential.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(match: Match)

    @Query("SELECT * FROM matches ORDER BY timestamp DESC")
    fun getAllMatches(): Flow<List<Match>>

    @Delete
    suspend fun delete(match: Match)
}
