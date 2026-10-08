package it.vantaggi.scoreboardessential

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.PadelEliteLink
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.repository.PlayerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * R-1: cancellare un giocatore con lo swipe porta via i suoi collegamenti a Padel Elite (CASCADE),
 * e "Annulla" li deve rimettere insieme al giocatore. Database vero, ViewModel vero.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AnnullaCancellazioneCollegamentiTest {
    private lateinit var db: AppDatabase
    private lateinit var viewModel: PlayersManagementViewModel

    @Before
    fun apri() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        viewModel =
            PlayersManagementViewModel(
                ApplicationProvider.getApplicationContext(),
                PlayerRepository(db.playerDao(), db.padelEliteLinkDao()),
            )
    }

    @After
    fun chiudi() {
        Dispatchers.resetMain()
        db.close()
    }

    private suspend fun attendi(condizione: suspend () -> Boolean) = withTimeout(5_000) { while (!condizione()) delay(20) }

    private suspend fun collegamenti(id: Int) = db.padelEliteLinkDao().linksOfPlayer(id)

    @Test
    fun `annullare la cancellazione rimette il giocatore e i suoi collegamenti in tutti i gruppi`() =
        runBlocking {
            val anna = Player(playerName = "Anna", appearances = 4, goals = 2)
            val id = db.playerDao().insert(anna).toInt()
            val inG1 = PadelEliteLink(id, "g-1", 100, "Anna B.")
            val inG2 = PadelEliteLink(id, "g-2", 7, "Anna")
            db.padelEliteLinkDao().link(inG1)
            db.padelEliteLinkDao().link(inG2)
            val conRuoli = PlayerWithRoles(anna.copy(playerId = id), emptyList())

            viewModel.deletePlayer(conRuoli)
            attendi { collegamenti(id).isEmpty() }
            assertEquals("il giocatore e' sparito", 0, db.playerDao().getPlayersWithRoles(listOf(id)).size)

            viewModel.restorePlayer(conRuoli.player, emptyList())
            attendi { collegamenti(id).size == 2 }

            assertEquals(setOf(inG1, inG2), collegamenti(id).toSet())
            assertEquals(
                "Anna",
                db
                    .playerDao()
                    .getPlayersWithRoles(listOf(id))
                    .single()
                    .player.playerName,
            )
        }

    @Test
    fun `senza annullare i collegamenti restano cancellati e un giocatore senza collegamenti si ripristina come prima`() =
        runBlocking {
            val id = db.playerDao().insert(Player(playerName = "Luca", appearances = 0, goals = 0)).toInt()
            val luca = PlayerWithRoles(Player(playerId = id, playerName = "Luca", appearances = 0, goals = 0), emptyList())

            viewModel.deletePlayer(luca)
            attendi { db.playerDao().getPlayersWithRoles(listOf(id)).isEmpty() }
            viewModel.restorePlayer(luca.player, emptyList())
            attendi { db.playerDao().getPlayersWithRoles(listOf(id)).size == 1 }

            assertEquals(emptyList<PadelEliteLink>(), collegamenti(id))
        }
}
