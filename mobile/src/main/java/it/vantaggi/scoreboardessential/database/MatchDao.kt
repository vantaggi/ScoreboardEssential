package it.vantaggi.scoreboardessential.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import it.vantaggi.scoreboardessential.core.MatchPlayer
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchDao {
    @Query("SELECT * FROM matches WHERE matchId = :matchId")
    suspend fun getMatchById(matchId: Int): Match?

    /** Una partita con le sue squadre, per la Cronaca: nomi e colori con cui si e' giocato. */
    @Transaction
    @Query("SELECT * FROM matches WHERE matchId = :matchId")
    suspend fun getMatchWithTeams(matchId: Int): MatchWithTeams?

    /**
     * I giocatori di una partita CON IL LORO LATO, nella forma che l'export si aspetta: di una
     * partita chiusa, e di quella viva per ripristinarne le rose.
     *
     * [MatchWithPlayers] non basta: la relazione attraverso la tabella ponte restituisce le
     * righe dei giocatori e perde `teamNumber`, cioe' proprio il lato. L'ordine e' quello in cui
     * [replaceLineup] le ha scritte, prima il roster 1 e poi il 2, come nell'export dal vivo:
     * ogni riscrittura cancella e reinserisce, quindi i rowid seguono l'ordine delle rose.
     */
    @Query(
        """
        SELECT p.playerId AS localId, p.playerName AS name, cr.teamNumber AS side
        FROM MatchPlayerCrossRef cr
        INNER JOIN players p ON p.playerId = cr.playerId
        WHERE cr.matchId = :matchId
        ORDER BY cr.teamNumber, cr.rowid
    """,
    )
    suspend fun getMatchLineup(matchId: Int): List<MatchPlayer>

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
     * Cancella la sola riga, per ID e non "quella attiva": l'id lo possiede gia' il ViewModel, e
     * cancellare per condizione significherebbe rileggere quale sia la riga attiva un istante
     * prima di distruggerla. Per buttare via una partita viva c'e' [deleteLiveMatch].
     */
    @Query("DELETE FROM matches WHERE matchId = :matchId")
    suspend fun deleteById(matchId: Int)

    /** Le formazioni di una partita. Non ci sono FK in cascata: chi cancella la riga le cancella. */
    @Query("DELETE FROM MatchPlayerCrossRef WHERE matchId = :matchId")
    suspend fun deleteLineup(matchId: Int)

    /**
     * Butta via una partita viva con le sue formazioni, tutto o niente.
     *
     * La riga viva ha le sue MatchPlayerCrossRef fin dal primo punto (servono a ripristinarne le
     * rose): cancellata la sola riga, restavano orfane.
     */
    @Transaction
    suspend fun deleteLiveMatch(matchId: Int) {
        deleteLineup(matchId)
        deleteById(matchId)
    }

    /**
     * Riscrive le formazioni di una partita nell'ordine delle rose: prima il roster 1, poi il 2.
     *
     * Cancella e reinserisce invece di aggiungere: un giocatore tolto dalla rosa deve sparire, e
     * i rowid nuovi tengono l'ordine che [getMatchLineup] rilegge.
     */
    @Transaction
    suspend fun replaceLineup(
        matchId: Int,
        team1PlayerIds: List<Int>,
        team2PlayerIds: List<Int>,
    ) {
        deleteLineup(matchId)
        insertMatchPlayerCrossRefs(
            team1PlayerIds.map { MatchPlayerCrossRef(matchId, it, teamNumber = 1) } +
                team2PlayerIds.map { MatchPlayerCrossRef(matchId, it, teamNumber = 2) },
        )
    }

    /**
     * Fa nascere la riga viva con le sue formazioni, tutto o niente; restituisce l'id della riga.
     *
     * Erano due scritture separate: se il processo moriva tra le due, la partita tornava col
     * ripristino ma senza rose.
     */
    @Transaction
    suspend fun insertLiveMatch(
        match: Match,
        team1PlayerIds: List<Int>,
        team2PlayerIds: List<Int>,
    ): Long {
        val id = insert(match)
        replaceLineup(id.toInt(), team1PlayerIds, team2PlayerIds)
        return id
    }

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
        // La riga viva ha gia' le formazioni scritte dal primo punto: si riscrivono con le rose
        // di adesso, senza doppioni e nello stesso ordine.
        replaceLineup(matchId, team1PlayerIds, team2PlayerIds)
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
     *
     * Solo partite chiuse: la riga viva ha le sue formazioni fin dal primo punto, e un 1-0 in
     * corso contava gia' come vittoria.
     */
    @Query(
        """
        SELECT cr.playerId AS playerId, COUNT(*) AS wins
        FROM MatchPlayerCrossRef cr
        INNER JOIN matches m ON cr.matchId = m.matchId
        WHERE m.isActive = 0
          AND ((cr.teamNumber = 1 AND m.team1Score > m.team2Score)
            OR (cr.teamNumber = 2 AND m.team2Score > m.team1Score))
        GROUP BY cr.playerId
    """,
    )
    fun getPlayerWinCounts(): Flow<List<PlayerWinCount>>
}
