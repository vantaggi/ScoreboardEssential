package it.vantaggi.scoreboardessential

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchDao
import it.vantaggi.scoreboardessential.database.MatchPlayerCrossRef
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class PerformanceTest {
    private lateinit var database: AppDatabase
    private lateinit var playerDao: PlayerDao
    private lateinit var matchDao: MatchDao

    @Before
    fun setup() {
        // Create an in-memory database for testing
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .build() // Allow main thread queries for simplicity in tests

        playerDao = database.playerDao()
        matchDao = database.matchDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Il percorso a lotto (updatePlayers + insertMatchPlayerCrossRefs) deve lasciare nel database
     * lo stesso stato del ciclo che scrive un giocatore alla volta. Prima questo test misurava i
     * millisecondi e non asseriva nulla sul codice vero: un lotto che perdesse righe sarebbe
     * passato lo stesso.
     */
    @Test
    fun `il lotto e il ciclo lasciano lo stesso stato nel database`() =
        runTest {
            val iniziali = (1..20).map { Player(playerName = "Player $it", appearances = 0, goals = 0) }
            val conId = iniziali.map { it.copy(playerId = playerDao.insert(it).toInt()) }

            val matchCiclo = 1
            val matchLotto = 2
            matchDao.insert(Match(matchCiclo, 1, 2, 0, 0, 0L))
            matchDao.insert(Match(matchLotto, 1, 2, 0, 0, 0L))

            // Ciclo: una scrittura per giocatore.
            for (player in conId) {
                playerDao.update(player.copy(appearances = 1))
                matchDao.insertMatchPlayerCrossRef(MatchPlayerCrossRef(matchCiclo, player.playerId))
            }
            val dopoCiclo = playerDao.getAllPlayers().first().map { it.player.appearances }
            val lineupCiclo = matchDao.getMatchLineup(matchCiclo).map { it.localId }.sorted()

            // Riporto le presenze a zero, poi scrivo a lotto.
            playerDao.updatePlayers(conId.map { it.copy(appearances = 0) })
            assertEquals(List(20) { 0 }, playerDao.getAllPlayers().first().map { it.player.appearances })

            playerDao.updatePlayers(conId.map { it.copy(appearances = 1) })
            matchDao.insertMatchPlayerCrossRefs(conId.map { MatchPlayerCrossRef(matchLotto, it.playerId) })
            val dopoLotto = playerDao.getAllPlayers().first().map { it.player.appearances }
            val lineupLotto = matchDao.getMatchLineup(matchLotto).map { it.localId }.sorted()

            assertEquals(List(20) { 1 }, dopoCiclo)
            assertEquals(dopoCiclo, dopoLotto)
            assertEquals(conId.map { it.playerId }.sorted(), lineupCiclo)
            assertEquals(lineupCiclo, lineupLotto)
        }
}
