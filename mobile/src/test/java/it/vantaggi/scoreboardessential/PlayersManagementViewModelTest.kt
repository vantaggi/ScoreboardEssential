package it.vantaggi.scoreboardessential

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.database.Role
import it.vantaggi.scoreboardessential.repository.PlayerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class PlayersManagementViewModelTest {
    // Rule for running tasks synchronously
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    // Test dispatcher for coroutines
    private val testDispatcher = StandardTestDispatcher()

    // Mock repository
    private lateinit var playerRepository: PlayerRepository
    private lateinit var viewModel: PlayersManagementViewModel

    // Test data
    private val roleGoalkeeper = Role(1, "Goalkeeper", "PORTA")
    private val roleDefender = Role(2, "Defender", "DIFESA")
    private val roleMidfielder = Role(3, "Midfielder", "CENTROCAMPO")
    private val roleForward = Role(4, "Forward", "ATTACCO")

    private val player1 =
        PlayerWithRoles(
            Player(1, "Zidane", 10, 5),
            listOf(roleMidfielder),
        )
    private val player2 =
        PlayerWithRoles(
            Player(2, "Materazzi", 5, 8),
            listOf(roleDefender),
        )
    private val player3 =
        PlayerWithRoles(
            Player(3, "Buffon", 15, 1),
            listOf(roleGoalkeeper, roleForward),
        )

    private val allPlayers = listOf(player1, player2, player3)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        playerRepository = mock()

        // Mock the repository to return a flow of our test data
        whenever(playerRepository.allPlayers).thenReturn(flowOf(allPlayers))
        whenever(playerRepository.allRoles).thenReturn(flowOf(listOf(roleGoalkeeper, roleDefender, roleMidfielder, roleForward)))

        val application = RuntimeEnvironment.getApplication()
        viewModel = PlayersManagementViewModel(application, playerRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Sorting Tests
    @Test
    fun `sortByName sorts players alphabetically`() =
        runTest {
            // The ViewModel initializes with a sorted list by name. Let's check initial state.
            var players = viewModel.players.first()
            assertEquals("Buffon", players[0].player.playerName)
            assertEquals("Materazzi", players[1].player.playerName)
            assertEquals("Zidane", players[2].player.playerName)

            // Change sort order to goals and then back to name
            viewModel.sortByGoals()
            viewModel.sortByName()

            players = viewModel.players.first()
            assertEquals("Buffon", players[0].player.playerName)
            assertEquals("Materazzi", players[1].player.playerName)
            assertEquals("Zidane", players[2].player.playerName)
        }

    @Test
    fun `sortByGoals sorts players by goals descending`() =
        runTest {
            viewModel.sortByGoals()
            val players = viewModel.players.first()
            assertEquals("Materazzi", players[0].player.playerName) // 8 goals
            assertEquals("Zidane", players[1].player.playerName) // 5 goals
            assertEquals("Buffon", players[2].player.playerName) // 1 goal
        }

    @Test
    fun `sortByAppearances sorts players by appearances descending`() =
        runTest {
            viewModel.sortByAppearances()
            val players = viewModel.players.first()
            assertEquals("Buffon", players[0].player.playerName) // 15 appearances
            assertEquals("Zidane", players[1].player.playerName) // 10 appearances
            assertEquals("Materazzi", players[2].player.playerName) // 5 appearances
        }

    // Filtering Tests
    @Test
    fun `setRoleFilter shows only players with the selected role`() =
        runTest {
            // Filter by Defender
            viewModel.setRoleFilter(roleDefender.roleId)
            var players = viewModel.players.first()
            assertEquals(1, players.size)
            assertEquals("Materazzi", players[0].player.playerName)

            // Filter by Forward (player with multiple roles)
            viewModel.setRoleFilter(roleForward.roleId)
            players = viewModel.players.first()
            assertEquals(1, players.size)
            assertEquals("Buffon", players[0].player.playerName)
        }

    @Test
    fun `setRoleFilter with non-matching role returns empty list`() =
        runTest {
            // A role that no player has
            viewModel.setRoleFilter(99)
            val players = viewModel.players.first()
            assertEquals(0, players.size)
        }

    @Test
    fun `setRoleFilter with null shows all players`() =
        runTest {
            // First, apply a filter
            viewModel.setRoleFilter(roleDefender.roleId)
            var players = viewModel.players.first()
            assertEquals(1, players.size) // Ensure filter was applied

            // Now, reset the filter
            viewModel.setRoleFilter(null)
            players = viewModel.players.first()
            assertEquals(allPlayers.size, players.size)
        }

    // CRUD Tests
    @Test
    fun `createPlayer calls repository's insertPlayerWithRoles`() =
        runTest {
            val playerName = "Ronaldo"
            val roleIds = listOf(roleForward.roleId)
            viewModel.createPlayer(playerName, roleIds)

            // We need to capture the arguments passed to the repository mock
            val playerCaptor = ArgumentCaptor.forClass(Player::class.java)
            val rolesCaptor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<Int>>

            // Advance the dispatcher to allow the coroutine in createPlayer to execute
            testDispatcher.scheduler.advanceUntilIdle()

            verify(playerRepository).insertPlayerWithRoles(playerCaptor.capture(), rolesCaptor.capture())

            assertEquals(playerName, playerCaptor.value.playerName)
            assertEquals(roleIds, rolesCaptor.value)
        }

    @Test
    fun `deletePlayer calls repository's deletePlayer`() =
        runTest {
            viewModel.deletePlayer(player1)

            // Advance the dispatcher to allow the coroutine in deletePlayer to execute
            testDispatcher.scheduler.advanceUntilIdle()

            verify(playerRepository).deletePlayer(player1.player)
        }
}
