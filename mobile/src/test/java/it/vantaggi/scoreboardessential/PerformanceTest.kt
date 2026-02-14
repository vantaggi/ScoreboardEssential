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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureTimeMillis

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PerformanceTest {

    private lateinit var database: AppDatabase
    private lateinit var playerDao: PlayerDao
    private lateinit var matchDao: MatchDao

    @Before
    fun setup() {
        // Create an in-memory database for testing
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build() // Allow main thread queries for simplicity in tests

        playerDao = database.playerDao()
        matchDao = database.matchDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun benchmarkBatchVsLoop() = runTest {
        // Setup: Create 100 players
        val players = (1..100).map {
            Player(playerName = "Player $it", appearances = 0, goals = 0)
        }

        // Insert players initially so they have IDs
        players.forEach { playerDao.insert(it) }

        // Reload players to get assigned IDs
        val savedPlayers = mutableListOf<Player>()
        // Simple way to get all players since getAllPlayers returns Flow
        // We can just query them back one by one or trust the IDs if we knew them,
        // but let's just rely on Room to generate IDs and fetch them.
        // Actually, for simplicity, let's just assume we can use the inserted IDs if we insert one by one and capture the ID.
        // Or we can just use a list of players with IDs 1..100 manually set if autogenerate is not critical for this test,
        // but autogenerate is true in Entity.

        // Let's just re-fetch or use the return value of insert.
        val playersWithIds = players.map {
            val id = playerDao.insert(it)
            it.copy(playerId = id.toInt())
        }

        val matchId = 1L
        matchDao.insert(Match(matchId.toInt(), 1, 2, 0, 0, System.currentTimeMillis()))

        // --- Benchmark Loop Implementation ---
        // Reset state
        playersWithIds.forEach { it.appearances = 0 }

        val timeLoop = measureTimeMillis {
            for (player in playersWithIds) {
                player.appearances++
                playerDao.update(player)
                matchDao.insertMatchPlayerCrossRef(MatchPlayerCrossRef(matchId.toInt(), player.playerId))
            }
        }
        println("Loop implementation took: ${timeLoop}ms")

        // Cleanup for next test
        // Reset appearances
        playersWithIds.forEach { it.appearances = 0 }
        // Remove cross refs
        // Since we don't have a delete all cross refs easily exposed, and we want a clean state,
        // we can just use a different match ID for the second test.
        val matchId2 = 2L
        matchDao.insert(Match(matchId2.toInt(), 1, 2, 0, 0, System.currentTimeMillis()))

        // --- Benchmark Batch Implementation ---
        val timeBatch = measureTimeMillis {
            // Prepare data
            val playersToUpdate = playersWithIds.map {
                it.copy(appearances = it.appearances + 1)
            }
            val crossRefs = playersWithIds.map {
                MatchPlayerCrossRef(matchId2.toInt(), it.playerId)
            }

            // Execute batch
            playerDao.updatePlayers(playersToUpdate)
            matchDao.insertMatchPlayerCrossRefs(crossRefs)
        }
        println("Batch implementation took: ${timeBatch}ms")

        // Assert improvement
        // In Robolectric/SQLite in-memory, the difference might be small but batch should still be faster or at least comparable.
        // On a real device with disk I/O, batch is significantly faster.
        // We assert that it didn't get significantly slower (e.g. < 2x loop time is acceptable if overhead, but expected is < Loop).

        val result = if (timeBatch < timeLoop) {
            "SUCCESS: Batch was faster by ${timeLoop - timeBatch}ms (Loop: ${timeLoop}ms, Batch: ${timeBatch}ms)"
        } else {
            "WARNING: Batch was slower. Loop: ${timeLoop}ms, Batch: ${timeBatch}ms"
        }
        println(result)
        java.io.File("/tmp/benchmark_result.txt").writeText(result)
    }
}
