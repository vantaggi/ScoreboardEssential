package it.vantaggi.scoreboardessential.database

import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import it.vantaggi.scoreboardessential.repository.PlayerRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var playerDao: PlayerDao
    private lateinit var playerRepository: PlayerRepository

    @Before
    fun createDb() {
        // Using an in-memory database because the information stored here disappears when the
        // process is killed.
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                )
                // Allowing main thread queries, just for testing.
                .allowMainThreadQueries()
                .build()

        playerDao = db.playerDao()
        playerRepository = PlayerRepository(playerDao)

        // Pre-populate roles for testing FK constraints
        runTest {
            playerDao.insertRole(Role(roleId = 1, name = "Portiere", category = "PORTA"))
            playerDao.insertRole(Role(roleId = 2, name = "Difensore", category = "DIFESA"))
            playerDao.insertRole(Role(roleId = 3, name = "Attaccante", category = "ATTACCO"))
        }
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertPlayerWithRoles_insertsPlayerAndRolesCorrectly() =
        runTest {
            // Arrange
            val newPlayer = Player(playerName = "John Doe", appearances = 0, goals = 0)
            val roleIds = listOf(1, 2)

            // Act
            playerRepository.insertPlayerWithRoles(newPlayer, roleIds)

            // Assert
            val playersWithRoles = playerRepository.allPlayers.first()
            assertThat(playersWithRoles).hasSize(1)
            val insertedPlayerWithRoles = playersWithRoles.first()
            assertThat(insertedPlayerWithRoles.player.playerName).isEqualTo("John Doe")
            // Note: The retrieved player will have an auto-generated ID.
            assertThat(insertedPlayerWithRoles.roles.map { it.roleId }).containsExactlyElementsIn(roleIds)
        }

    @Test
    fun updatePlayerWithRoles_updatesNameAndChangesRoles() =
        runTest {
            // Arrange
            val initialPlayer = Player(playerName = "Jane Doe", appearances = 5, goals = 2)
            val initialRoleIds = listOf(1, 2)
            playerRepository.insertPlayerWithRoles(initialPlayer, initialRoleIds)
            val insertedPlayerId =
                playerRepository.allPlayers
                    .first()
                    .first()
                    .player.playerId

            // Act
            val updatedPlayer = Player(playerId = insertedPlayerId, playerName = "Jane Smith", appearances = 5, goals = 2)
            val updatedRoleIds = listOf(2, 3)
            playerRepository.updatePlayerWithRoles(updatedPlayer, updatedRoleIds)

            // Assert
            val playerWithRoles = playerRepository.getPlayerWithRoles(insertedPlayerId.toLong()).first()
            assertThat(playerWithRoles).isNotNull()
            assertThat(playerWithRoles!!.player.playerName).isEqualTo("Jane Smith")
            assertThat(playerWithRoles.roles.map { it.roleId }).containsExactlyElementsIn(updatedRoleIds)
        }

    @Test
    fun deletePlayer_removesPlayerAndAssociations() =
        runTest {
            // Arrange
            val player = Player(playerName = "Sam Brown", appearances = 10, goals = 5)
            playerRepository.insertPlayerWithRoles(player, listOf(1))
            val insertedPlayer =
                playerRepository.allPlayers
                    .first()
                    .first()
                    .player

            // Act
            playerRepository.deletePlayer(insertedPlayer)

            // Assert
            val players = playerRepository.allPlayers.first()
            assertThat(players).isEmpty()
        }
}
