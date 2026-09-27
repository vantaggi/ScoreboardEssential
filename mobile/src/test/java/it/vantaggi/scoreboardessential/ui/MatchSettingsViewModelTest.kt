package it.vantaggi.scoreboardessential.ui

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchDao
import it.vantaggi.scoreboardessential.repository.MatchSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever

class MatchSettingsViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    /** Un proprietario gia' in primo piano, come l'Activity mentre si tocca il selettore. */
    private class ProprietarioAttivo : LifecycleOwner {
        private val registro = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registro
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Visto su dispositivo: cambiare sport a partita cominciata faceva crashare le impostazioni.
     *
     * `sportChangeBlocked.call()` pubblicava null. L'osservatore dell'Activity e' una lambda Kotlin
     * con parametro Unit non nullo, e il controllo generato dal compilatore lanciava
     * NullPointerException.
     *
     * Contare gli avvisi NON basta a vederlo, e lo ha mostrato una falsificazione: col difetto il
     * test restava verde. Il bytecode spiega perche': la lambda dell'Activity controlla il
     * parametro con checkNotNullParameter, quella scritta qui dentro runTest no, e riceve null
     * senza lamentarsi. Un test che dipende da come il compilatore genera una lambda non protegge
     * niente. Si verifica quindi il contratto: un evento Unit pubblica un valore, non null.
     */
    @Test
    fun `cambiare sport a partita cominciata avvisa senza crash`() =
        runTest(UnconfinedTestDispatcher()) {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val repository = mock(MatchSettingsRepository::class.java)
            whenever(repository.getTeam1Name()).thenReturn("Team 1")
            whenever(repository.getTeam2Name()).thenReturn("Team 2")
            whenever(repository.getTeam1Color()).thenReturn(1)
            whenever(repository.getTeam2Color()).thenReturn(2)
            whenever(repository.getKeeperTimerDuration()).thenReturn(300L)
            whenever(repository.getActiveSport()).thenReturn(SportRegistry.FOOTBALL)
            val matchDao = mock(MatchDao::class.java)
            whenever(matchDao.getActiveMatchOnce()).thenReturn(
                Match(team1Id = 1, team2Id = 2, team1Score = 1, team2Score = 0, timestamp = 0L, isActive = true, eventLog = "1|1"),
            )
            val viewModel = MatchSettingsViewModel(repository, matchDao)
            var avvisi = 0
            viewModel.sportChangeBlocked.observe(ProprietarioAttivo()) { avvisi++ }

            viewModel.saveActiveSport(SportRegistry.PADEL)

            org.junit.Assert.assertNotNull(
                "un evento Unit deve pubblicare Unit: un null fa crashare ogni osservatore Kotlin",
                viewModel.sportChangeBlocked.value,
            )
            assertEquals("il rifiuto deve arrivare una volta, come avviso", 1, avvisi)
            assertEquals("e lo sport resta quello di prima", SportRegistry.FOOTBALL, viewModel.activeSport.value)
        }

    /** Il ViewModel con una riga viva di calcio che porta [eventLog], e il repository finto. */
    private suspend fun conRigaViva(eventLog: String): Pair<MatchSettingsViewModel, MatchSettingsRepository> {
        val repository = mock(MatchSettingsRepository::class.java)
        whenever(repository.getTeam1Name()).thenReturn("Team 1")
        whenever(repository.getTeam2Name()).thenReturn("Team 2")
        whenever(repository.getTeam1Color()).thenReturn(1)
        whenever(repository.getTeam2Color()).thenReturn(2)
        whenever(repository.getKeeperTimerDuration()).thenReturn(300L)
        whenever(repository.getActiveSport()).thenReturn(SportRegistry.FOOTBALL)
        val matchDao = mock(MatchDao::class.java)
        whenever(matchDao.getActiveMatchOnce()).thenReturn(
            Match(team1Id = 1, team2Id = 2, team1Score = 0, team2Score = 0, timestamp = 0L, isActive = true, eventLog = eventLog),
        )
        return MatchSettingsViewModel(repository, matchDao) to repository
    }

    /**
     * Un gol e poi ANNULLA: la riga viva resta con '1|', il registro vuoto codificato. Il motore
     * e' vuoto e selectSport il cambio lo accetta, quindi anche le impostazioni.
     */
    @Test
    fun `dopo un gol annullato il registro vuoto non blocca il cambio sport`() =
        runTest(UnconfinedTestDispatcher()) {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val (viewModel, repository) = conRigaViva("1|")

            viewModel.saveActiveSport(SportRegistry.PADEL)

            assertEquals(SportRegistry.PADEL, viewModel.activeSport.value)
            verify(repository).setActiveSport(SportRegistry.PADEL)
        }

    /**
     * Un registro illeggibile (formato piu' recente, riga corrotta) non dice se la partita e'
     * cominciata. Il ripristino ne recupera il punteggio, quindi si rifiuta.
     */
    @Test
    fun `un registro illeggibile vale come partita cominciata`() =
        runTest(UnconfinedTestDispatcher()) {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val (viewModel, repository) = conRigaViva("9|1,2")

            viewModel.saveActiveSport(SportRegistry.PADEL)

            assertEquals(SportRegistry.FOOTBALL, viewModel.activeSport.value)
            verify(repository, never()).setActiveSport(SportRegistry.PADEL)
        }

    /**
     * La stringa vuota e' il DEFAULT della colonna: il codec la legge come registro vuoto e il
     * ripristino riparte da un motore vuoto, dove selectSport accetta. Stessa risposta qui.
     */
    @Test
    fun `una riga viva col registro vuoto di default non blocca il cambio sport`() =
        runTest(UnconfinedTestDispatcher()) {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            val (viewModel, repository) = conRigaViva("")

            viewModel.saveActiveSport(SportRegistry.PADEL)

            assertEquals(SportRegistry.PADEL, viewModel.activeSport.value)
            verify(repository).setActiveSport(SportRegistry.PADEL)
        }
}
