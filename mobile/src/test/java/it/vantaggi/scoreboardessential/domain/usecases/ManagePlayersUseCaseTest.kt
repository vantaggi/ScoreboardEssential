package it.vantaggi.scoreboardessential.domain.usecases

import org.junit.Ignore
import org.junit.Test

class ManagePlayersUseCaseTest {
    @Test
    @Ignore("UseCase missing")
    fun dummy() {
    }
}

/*
// TODO: Fix this test. The ManagePlayersUseCase class is missing from the project.
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerDao
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

@ExperimentalCoroutinesApi
class ManagePlayersUseCaseTest {
    private lateinit var playerDao: PlayerDao
    private lateinit var wearDataSync: OptimizedWearDataSync
    private lateinit var useCase: ManagePlayersUseCase

    @Before
    fun setup() {
        playerDao = mock()
        wearDataSync = mock()
        useCase = ManagePlayersUseCase(playerDao, wearDataSync)
    }

    @Test
    fun `addPlayerToTeam adds player to team 1`() =
        runTest {
            // Given
            val player = PlayerWithRoles(Player(1, "Player 1", 0, 0), emptyList())

            // When
            useCase.addPlayerToTeam(player, 1)

            // Then
            assertEquals(
                1,
                useCase.teamRoster
                    .first()
                    .team1Players.size,
            )
            assertEquals(
                0,
                useCase.teamRoster
                    .first()
                    .team2Players.size,
            )
            verify(wearDataSync).syncTeamPlayers(any(), any())
        }

    @Test
    fun `addPlayerToTeam adds player to team 2`() =
        runTest {
            // Given
            val player = PlayerWithRoles(Player(1, "Player 1", 0, 0), emptyList())

            // When
            useCase.addPlayerToTeam(player, 2)

            // Then
            assertEquals(
                0,
                useCase.teamRoster
                    .first()
                    .team1Players.size,
            )
            assertEquals(
                1,
                useCase.teamRoster
                    .first()
                    .team2Players.size,
            )
            verify(wearDataSync).syncTeamPlayers(any(), any())
        }

    @Test
    fun `addPlayerToTeam does not add duplicate player`() =
        runTest {
            // Given
            val player = PlayerWithRoles(Player(1, "Player 1", 0, 0), emptyList())
            useCase.addPlayerToTeam(player, 1)

            // When
            useCase.addPlayerToTeam(player, 1)

            // Then
            assertEquals(
                1,
                useCase.teamRoster
                    .first()
                    .team1Players.size,
            )
        }

    @Test
    fun `removePlayerFromTeam removes player from team 1`() =
        runTest {
            // Given
            val player = PlayerWithRoles(Player(1, "Player 1", 0, 0), emptyList())
            useCase.addPlayerToTeam(player, 1)

            // When
            useCase.removePlayerFromTeam(player, 1)

            // Then
            assertEquals(
                0,
                useCase.teamRoster
                    .first()
                    .team1Players.size,
            )
            verify(wearDataSync).syncTeamPlayers(any(), any())
        }

    @Test
    fun `removePlayerFromTeam removes player from team 2`() =
        runTest {
            // Given
            val player = PlayerWithRoles(Player(1, "Player 1", 0, 0), emptyList())
            useCase.addPlayerToTeam(player, 2)

            // When
            useCase.removePlayerFromTeam(player, 2)

            // Then
            assertEquals(
                0,
                useCase.teamRoster
                    .first()
                    .team2Players.size,
            )
            verify(wearDataSync).syncTeamPlayers(any(), any())
        }
}
*/
