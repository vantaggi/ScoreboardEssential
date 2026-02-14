package it.vantaggi.scoreboardessential.database

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMatchPlayerCrossRefs(crossRefs: List<MatchPlayerCrossRef>)

    @Transaction
    @Query("SELECT * FROM matches ORDER BY timestamp DESC")
    fun getAllMatchesWithPlayers(): Flow<List<MatchWithPlayers>>

    @Transaction
    @Query("SELECT * FROM matches ORDER BY timestamp DESC")
    fun getAllMatchesWithTeams(): Flow<List<MatchWithTeams>>

    @Delete
    suspend fun delete(match: Match)

    @Query("SELECT * FROM matches WHERE isActive = 1 LIMIT 1")
    fun getActiveMatch(): Flow<Match?>

    @Query(
        """
        SELECT COUNT(*) FROM matches
        INNER JOIN MatchPlayerCrossRef ON matches.matchId = MatchPlayerCrossRef.matchId
        WHERE MatchPlayerCrossRef.playerId = :playerId AND matches.isActive = 0
    """,
    )
    fun getFinishedMatchesCountForPlayer(playerId: Int): Flow<Int>
}
