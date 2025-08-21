package com.example.scoreboardessential.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(match: Match): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMatchPlayerCrossRef(crossRef: MatchPlayerCrossRef)

    @Transaction
    @Query("SELECT * FROM matches ORDER BY timestamp DESC")
    fun getAllMatchesWithPlayers(): Flow<List<MatchWithPlayers>>

    @Transaction
    @Query("SELECT * FROM matches ORDER BY timestamp DESC")
    fun getAllMatchesWithTeams(): Flow<List<MatchWithTeams>>

    @Delete
    suspend fun delete(match: Match)
}
