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

    /**
     * Butta via una partita viva.
     *
     * Per ID e non "quella attiva": l'id lo possiede gia' il ViewModel, e cancellare per
     * condizione significherebbe rileggere quale sia la riga attiva un istante prima di
     * distruggerla. Una riga viva non ha ancora MatchPlayerCrossRef -- quelle nascono solo
     * quando la partita viene chiusa e salvata -- quindi non resta niente di orfano.
     */
    @Query("DELETE FROM matches WHERE matchId = :matchId")
    suspend fun deleteById(matchId: Int)

    @Query("SELECT * FROM matches WHERE isActive = 1 LIMIT 1")
    fun getActiveMatch(): Flow<Match?>

    /**
     * La partita in corso, letta una volta sola.
     *
     * `isActive` esisteva gia' ma non veniva MAI messa a 1: nessuna riga la valorizzava, quindi
     * [getActiveMatch] poteva soltanto emettere null e non aveva chiamanti. Da qui diventa il
     * meccanismo che ripristina una partita dopo la morte del processo.
     */
    @Query("SELECT * FROM matches WHERE isActive = 1 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getActiveMatchOnce(): Match?

    /** Aggiorna la riga viva: una scrittura per punto, mirata alle sole colonne che cambiano. */
    @Query(
        """
        UPDATE matches SET team1Score = :team1, team2Score = :team2, eventLog = :eventLog
        WHERE matchId = :matchId
    """,
    )
    suspend fun updateLiveMatch(
        matchId: Int,
        team1: Int,
        team2: Int,
        eventLog: String,
    )

    /** Chiude la partita viva: smette di essere attiva e fissa il risultato finale. */
    @Query(
        """
        UPDATE matches SET isActive = 0, team1Score = :team1, team2Score = :team2,
            eventLog = :eventLog, timestamp = :timestamp
        WHERE matchId = :matchId
    """,
    )
    suspend fun finalizeMatch(
        matchId: Int,
        team1: Int,
        team2: Int,
        eventLog: String,
        timestamp: Long,
    )

    /**
     * Una presenza in piu' a ciascun giocatore, incrementata nel database.
     *
     * Sta qui e non in [PlayerDao] perche' deve girare nella stessa transazione di [closeMatch].
     * Stessa ragione di [PlayerDao.incrementGoals]: un @Update di riga intera fatto dalla copia
     * tenuta nella rosa riscriveva anche i gol, riportandoli a prima della partita.
     */
    @Query("UPDATE players SET appearances = appearances + 1 WHERE playerId IN (:playerIds)")
    suspend fun incrementAppearances(playerIds: List<Int>)

    /**
     * Salva una partita finita: la riga, le presenze e le formazioni, tutto o niente.
     *
     * Erano tre scritture separate: se il processo moriva dopo la prima, la partita restava
     * nello storico senza giocatori e [getPlayerWinCounts] non la contava.
     *
     * Con [Match.matchId] diverso da zero chiude quella riga viva invece di inserirne una nuova.
     */
    @Transaction
    suspend fun closeMatch(
        match: Match,
        team1PlayerIds: List<Int>,
        team2PlayerIds: List<Int>,
    ) {
        val matchId =
            if (match.matchId != 0) {
                finalizeMatch(match.matchId, match.team1Score, match.team2Score, match.eventLog, match.timestamp)
                match.matchId
            } else {
                insert(match).toInt()
            }
        incrementAppearances(team1PlayerIds + team2PlayerIds)
        insertMatchPlayerCrossRefs(
            team1PlayerIds.map { MatchPlayerCrossRef(matchId, it, teamNumber = 1) } +
                team2PlayerIds.map { MatchPlayerCrossRef(matchId, it, teamNumber = 2) },
        )
    }

    @Query(
        """
        SELECT COUNT(*) FROM matches
        INNER JOIN MatchPlayerCrossRef ON matches.matchId = MatchPlayerCrossRef.matchId
        WHERE MatchPlayerCrossRef.playerId = :playerId AND matches.isActive = 0
    """,
    )
    fun getFinishedMatchesCountForPlayer(playerId: Int): Flow<Int>

    /**
     * Number of matches each player won, derived from their team affiliation
     * ([MatchPlayerCrossRef.teamNumber]) and the final match score. Players on the
     * winning side of a finished match are counted once per match.
     */
    @Query(
        """
        SELECT cr.playerId AS playerId, COUNT(*) AS wins
        FROM MatchPlayerCrossRef cr
        INNER JOIN matches m ON cr.matchId = m.matchId
        WHERE (cr.teamNumber = 1 AND m.team1Score > m.team2Score)
           OR (cr.teamNumber = 2 AND m.team2Score > m.team1Score)
        GROUP BY cr.playerId
    """,
    )
    fun getPlayerWinCounts(): Flow<List<PlayerWinCount>>
}
