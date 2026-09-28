package it.vantaggi.scoreboardessential.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** [MatchDao.closeMatch]: la riga, le presenze e le formazioni di una partita finita. */
@RunWith(RobolectricTestRunner::class)
class ChiusuraPartitaTest {
    private lateinit var db: AppDatabase
    private lateinit var matchDao: MatchDao
    private lateinit var playerDao: PlayerDao

    @Before
    fun createDb() {
        db =
            Room
                .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        matchDao = db.matchDao()
        playerDao = db.playerDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    /**
     * La seconda scrittura fallisce, come se il processo morisse dopo la prima. Senza la
     * transazione la partita risultava chiusa, nello storico e senza giocatori.
     */
    @Test
    fun `se una scrittura fallisce la partita resta viva`() =
        runTest {
            val marioId = playerDao.insert(Player(playerName = "Mario", appearances = 2, goals = 5)).toInt()
            val vivaId =
                matchDao
                    .insert(Match(team1Id = 1, team2Id = 2, team1Score = 0, team2Score = 0, timestamp = 0L, isActive = true))
                    .toInt()
            db.openHelper.writableDatabase.execSQL(
                """
                CREATE TRIGGER blocca_presenze BEFORE UPDATE OF appearances ON players
                BEGIN SELECT RAISE(ABORT, 'interrotta'); END
                """,
            )

            val esito =
                runCatching {
                    matchDao.closeMatch(
                        Match(matchId = vivaId, team1Id = 1, team2Id = 2, team1Score = 1, team2Score = 0, timestamp = 10L),
                        team1PlayerIds = listOf(marioId),
                        team2PlayerIds = emptyList(),
                    )
                }

            assertThat(esito.isFailure).isTrue()
            assertThat(matchDao.getActiveMatchOnce()?.matchId).isEqualTo(vivaId)
        }

    /** Le formazioni in tabella per [matchId], nell'ordine di scrittura: (id, lato). */
    private fun formazioni(matchId: Int): List<Pair<Int, Int>> =
        db.openHelper.readableDatabase
            .query("SELECT playerId, teamNumber FROM MatchPlayerCrossRef WHERE matchId = $matchId ORDER BY rowid")
            .use { c -> buildList { while (c.moveToNext()) add(c.getInt(0) to c.getInt(1)) } }

    /**
     * La riga viva arriva a END MATCH con formazioni vecchie: la rosa e' cambiata dopo l'ultima
     * riscrittura. Aggiungendo con IGNORE restava chi era stato tolto, e l'ordine era quello
     * vecchio con i nuovi in coda. La chiusura le riscrive con la rosa finale.
     */
    @Test
    fun `la chiusura riscrive formazioni vecchie con la rosa finale`() =
        runTest {
            val (marco, anna, luca, sara, piero) =
                listOf("Marco", "Anna", "Luca", "Sara", "Piero").map {
                    playerDao.insert(Player(playerName = it, appearances = 0, goals = 0)).toInt()
                }
            val vivaId =
                matchDao
                    .insert(Match(team1Id = 1, team2Id = 2, team1Score = 0, team2Score = 0, timestamp = 0L, isActive = true))
                    .toInt()
            matchDao.replaceLineup(vivaId, listOf(marco, luca), listOf(anna, sara))

            matchDao.closeMatch(
                Match(matchId = vivaId, team1Id = 1, team2Id = 2, team1Score = 1, team2Score = 0, timestamp = 10L),
                team1PlayerIds = listOf(luca, piero),
                team2PlayerIds = listOf(anna, sara),
            )

            assertThat(formazioni(vivaId)).containsExactly(luca to 1, piero to 1, anna to 2, sara to 2).inOrder()
            assertThat(playerDao.getAllPlayers().first().associate { it.player.playerId to it.player.appearances })
                .containsExactly(marco, 0, anna, 1, luca, 1, sara, 1, piero, 1)
        }
}
