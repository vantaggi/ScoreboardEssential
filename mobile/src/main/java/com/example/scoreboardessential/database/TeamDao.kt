package com.example.scoreboardessential.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(team: Team)

    @Update
    suspend fun update(team: Team)

    @Query("SELECT * FROM teams")
    fun getAllTeams(): Flow<List<Team>>

    @Query("SELECT * FROM teams WHERE id = :id")
    fun getTeam(id: Int): Flow<Team>
}
