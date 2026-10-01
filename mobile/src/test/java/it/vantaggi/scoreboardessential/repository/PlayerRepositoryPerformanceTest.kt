package it.vantaggi.scoreboardessential.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.Role
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerRepositoryPerformanceTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: PlayerRepository
    private val roleIds = mutableListOf<Int>()

    @Before
    fun createDb() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            db =
                Room
                    .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                    .allowMainThreadQueries()
                    .build()

            // Populate roles
            for (i in 1..20) {
                val role = Role(name = "Role$i", category = "Cat")
                db.playerDao().insertRole(role)
                roleIds.add(i)
            }

            repository = PlayerRepository(db.playerDao())
        }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `cento giocatori inseriti con venti ruoli ciascuno restano cento, ognuno con i suoi venti`() =
        runTest {
            val player = Player(playerName = "Test Player", appearances = 0, goals = 0)

            for (i in 0 until 100) {
                repository.insertPlayerWithRoles(player.copy(playerName = "Player $i", playerId = 0), roleIds)
            }

            val inseriti = db.playerDao().getAllPlayers().first()
            assertEquals("i giocatori inseriti", 100, inseriti.size)

            // Verification
            // Since we are running in a transaction/test, we assume IDs start at 1 if fresh DB.
            // We inserted 100 players.
            // Check a few players to ensure they have 20 roles.

            val count1 = db.playerDao().getRoleCountForPlayer(1)
            assertEquals("Player 1 should have 20 roles", 20, count1)

            val count50 = db.playerDao().getRoleCountForPlayer(50)
            assertEquals("Player 50 should have 20 roles", 20, count50)

            val count100 = db.playerDao().getRoleCountForPlayer(100)
            assertEquals("Player 100 should have 20 roles", 20, count100)
        }
}
