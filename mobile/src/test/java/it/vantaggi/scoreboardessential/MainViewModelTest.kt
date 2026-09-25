package it.vantaggi.scoreboardessential

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Looper
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import it.vantaggi.scoreboardessential.core.ExportResult
import it.vantaggi.scoreboardessential.core.MatchEngine
import it.vantaggi.scoreboardessential.core.MatchExporter
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchDao
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerDao
import it.vantaggi.scoreboardessential.database.PlayerWinCount
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.domain.models.MatchEvent
import it.vantaggi.scoreboardessential.domain.models.MatchEventType
import it.vantaggi.scoreboardessential.repository.MatchRepository
import it.vantaggi.scoreboardessential.repository.MatchSettings
import it.vantaggi.scoreboardessential.repository.MatchSettingsRepository
import it.vantaggi.scoreboardessential.repository.UserPreferencesRepository
import it.vantaggi.scoreboardessential.service.MatchTimerService
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.lang.reflect.Field

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class MainViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MainViewModel
    private lateinit var mockApplication: Application
    private lateinit var mockRepository: MatchRepository
    private lateinit var mockUserPreferencesRepository: UserPreferencesRepository
    private lateinit var mockMatchSettingsRepository: MatchSettingsRepository
    private lateinit var mockMatchTimerService: MatchTimerService

    // Gli stati del portiere del service finto: i test di L8 li muovono.
    private val portiereInCorso = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val portiereScaduto = kotlinx.coroutines.flow.MutableSharedFlow<Unit>()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Get the real application from Robolectric
        val app = ApplicationProvider.getApplicationContext<Application>()

        // Setup Binder for Service - MUST be done before ViewModel init because init calls bindService
        val mockBinder = mock(MatchTimerService.MatchTimerBinder::class.java)
        mockMatchTimerService = mock(MatchTimerService::class.java)
        whenever(mockBinder.getService()).thenReturn(mockMatchTimerService)

        // Mock StateFlows to avoid NPE in onServiceConnected
        whenever(mockMatchTimerService.matchTimerValue).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(0L))
        whenever(mockMatchTimerService.isMatchTimerRunning).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(false))
        whenever(mockMatchTimerService.keeperTimerValue).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(0L))
        whenever(mockMatchTimerService.isKeeperTimerRunning).thenReturn(portiereInCorso)
        whenever(mockMatchTimerService.keeperTimerExpired).thenReturn(portiereScaduto)

        // Register the mock binder with Robolectric so bindService returns it
        val componentName = ComponentName(app, MatchTimerService::class.java)
        shadowOf(app).setComponentNameAndServiceForBindService(componentName, mockBinder)

        // Use a spy on the real application so we can verify interactions but also let AppDatabase work
        mockApplication = spy(app)

        mockRepository =
            mock(MatchRepository::class.java).apply {
                `when`(allMatches).thenReturn(emptyFlow())
            }
        mockUserPreferencesRepository =
            mock(UserPreferencesRepository::class.java).apply {
                `when`(hasSeenTutorial).thenReturn(emptyFlow())
            }
        mockMatchSettingsRepository = mock(MatchSettingsRepository::class.java)
        whenever(mockMatchSettingsRepository.getSettingsFlow()).thenReturn(emptyFlow())

        viewModel = MainViewModel(mockRepository, mockUserPreferencesRepository, mockMatchSettingsRepository, mockApplication)

        // Use reflection to inject the mock service and set isServiceBound to true
        // Although bindService in init should now work, we ensure it's set for tests that rely on it immediately
        val serviceField: Field = MainViewModel::class.java.getDeclaredField("matchTimerService")
        serviceField.isAccessible = true
        serviceField.set(viewModel, mockMatchTimerService)

        val isBoundField: Field = MainViewModel::class.java.getDeclaredField("isServiceBound")
        isBoundField.isAccessible = true
        isBoundField.set(viewModel, true)

        // Inject mock PlayerDao and MatchDao
        val playerDaoField = MainViewModel::class.java.getDeclaredField("playerDao")
        playerDaoField.isAccessible = true
        val mockPlayerDao =
            mock(PlayerDao::class.java).apply {
                // Mock getAllPlayers to return empty flow to avoid NPEs if used
                `when`(getAllPlayers()).thenReturn(kotlinx.coroutines.flow.flowOf(emptyList()))
            }
        playerDaoField.set(viewModel, mockPlayerDao)

        val matchDaoField = MainViewModel::class.java.getDeclaredField("matchDao")
        matchDaoField.isAccessible = true
        val mockMatchDao = mock(MatchDao::class.java)
        matchDaoField.set(viewModel, mockMatchDao)

        // Mock OptimizedWearDataSync connectionManager to prevent crashes in Robolectric
        val connectionManagerField = MainViewModel::class.java.getDeclaredField("connectionManager")
        connectionManagerField.isAccessible = true
        val mockConnectionManager = mock(OptimizedWearDataSync::class.java)
        // Stub connectionState flow to return empty or mock state
        whenever(
            mockConnectionManager.connectionState,
        ).thenReturn(
            kotlinx.coroutines.flow.MutableStateFlow(it.vantaggi.scoreboardessential.shared.communication.ConnectionState.Disconnected),
        )

        connectionManagerField.set(viewModel, mockConnectionManager)

        // Mock insert to return a valid ID using whenever and runBlocking
        kotlinx.coroutines.runBlocking {
            whenever(mockMatchDao.insert(any())).thenReturn(1L)
            // Stub other suspend functions just in case
            whenever(mockMatchDao.insertMatchPlayerCrossRef(any())).thenReturn(Unit)
            whenever(mockMatchDao.insertMatchPlayerCrossRefs(any())).thenReturn(Unit)
            whenever(mockPlayerDao.update(any())).thenReturn(Unit)
            whenever(mockPlayerDao.updatePlayers(any())).thenReturn(Unit)
            // Stub sendData
            whenever(mockConnectionManager.sendData(any(), any(), any())).thenReturn(Unit)

            // Stub MatchSettingsRepository to avoid NPE in init
            whenever(mockMatchSettingsRepository.getTeam1Name()).thenReturn("Team 1")
            whenever(mockMatchSettingsRepository.getTeam2Name()).thenReturn("Team 2")
            whenever(mockMatchSettingsRepository.getTeam1Color()).thenReturn(Color.RED)
            whenever(mockMatchSettingsRepository.getTeam2Color()).thenReturn(Color.BLUE)
            whenever(mockMatchSettingsRepository.getKeeperTimerDuration()).thenReturn(300L)
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setTeam1Color updates team1Color LiveData`() {
        // Arrange
        val colorObserver = Observer<Int> {}
        viewModel.team1Color.observeForever(colorObserver)
        val color = Color.RED

        // Act
        viewModel.setTeamColor(1, color)

        // Assert
        assertEquals(color, viewModel.team1Color.value)
        viewModel.team1Color.removeObserver(colorObserver)
    }

    @Test
    fun `setTeam2Color updates team2Color LiveData`() {
        // Arrange
        val colorObserver = Observer<Int> {}
        viewModel.team2Color.observeForever(colorObserver)
        val color = Color.BLUE

        // Act
        viewModel.setTeamColor(2, color)

        // Assert
        assertEquals(color, viewModel.team2Color.value)
        viewModel.team2Color.removeObserver(colorObserver)
    }

    @Test
    fun `service should unbind correctly on viewmodel clear`() {
        // Arrange
        // Use reflection to set isServiceBound to true
        val isServiceBoundField = viewModel::class.java.getDeclaredField("isServiceBound")
        isServiceBoundField.isAccessible = true
        isServiceBoundField.set(viewModel, true)

        // Act
        // Call protected onCleared() method using reflection
        // MainViewModel -> AndroidViewModel -> ViewModel
        val onClearedMethod = androidx.lifecycle.ViewModel::class.java.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true
        onClearedMethod.invoke(viewModel)

        // Assert
        verify(mockApplication, times(1)).unbindService(any())
    }

    @Test
    fun `addScore(1) segna il punto e NON interrompe con il dialogo del marcatore`() =
        runTest {
            // Il dialogo si apriva qui, subito, nel momento di massima attenzione. Ora il gol si
            // registra senza marcatore e la riga del registro resta attribuibile quando si vuole.
            val scoreObserver = Observer<Int> {}
            val eventiObserver = Observer<List<MatchEvent>> {}
            viewModel.team1Score.observeForever(scoreObserver)
            viewModel.matchEvents.observeForever(eventiObserver)
            viewModel.addPlayerToTeam(PlayerWithRoles(Player(1, "Player 1", 0, 0), emptyList()), 1)

            viewModel.addScore(1)
            // La riga del registro nasce dentro una coroutine: senza questo il test misurerebbe
            // il momento prima che esista.
            advanceUntilIdle()

            assertEquals(1, viewModel.team1Score.value)

            val gol =
                viewModel.matchEvents.value
                    .orEmpty()
                    .first { it.type == MatchEventType.SCORE }
            assertEquals("il gol e' registrato ma non attribuito", null, gol.playerId)
            assertEquals("e sa a quale punto del motore si riferisce", 0, gol.engineIndex)

            viewModel.team1Score.removeObserver(scoreObserver)
            viewModel.matchEvents.removeObserver(eventiObserver)
        }

    @Test
    fun `addScore(2) segna il punto e NON interrompe con il dialogo del marcatore`() =
        runTest {
            // Il dialogo si apriva qui, subito, nel momento di massima attenzione. Ora il gol si
            // registra senza marcatore e la riga del registro resta attribuibile quando si vuole.
            val scoreObserver = Observer<Int> {}
            val eventiObserver = Observer<List<MatchEvent>> {}
            viewModel.team2Score.observeForever(scoreObserver)
            viewModel.matchEvents.observeForever(eventiObserver)
            viewModel.addPlayerToTeam(PlayerWithRoles(Player(2, "Player 2", 0, 0), emptyList()), 2)

            viewModel.addScore(2)
            // La riga del registro nasce dentro una coroutine: senza questo il test misurerebbe
            // il momento prima che esista.
            advanceUntilIdle()

            assertEquals(1, viewModel.team2Score.value)

            val gol =
                viewModel.matchEvents.value
                    .orEmpty()
                    .first { it.type == MatchEventType.SCORE }
            assertEquals("il gol e' registrato ma non attribuito", null, gol.playerId)
            assertEquals("e sa a quale punto del motore si riferisce", 0, gol.engineIndex)

            viewModel.team2Score.removeObserver(scoreObserver)
            viewModel.matchEvents.removeObserver(eventiObserver)
        }

    @Test
    fun `subtractScore(1) decrements score when greater than zero`() {
        // Arrange
        val scoreObserver = Observer<Int> {}
        viewModel.team1Score.observeForever(scoreObserver)
        viewModel.addScore(1) // Score is now 1

        // Act
        viewModel.subtractScore(1)

        // Assert
        assertEquals(0, viewModel.team1Score.value)

        // Cleanup
        viewModel.team1Score.removeObserver(scoreObserver)
    }

    @Test
    fun `subtractScore(2) decrements score when greater than zero`() {
        // Arrange
        val scoreObserver = Observer<Int> {}
        viewModel.team2Score.observeForever(scoreObserver)
        viewModel.addScore(2) // Score is now 1

        // Act
        viewModel.subtractScore(2)

        // Assert
        assertEquals(0, viewModel.team2Score.value)

        // Cleanup
        viewModel.team2Score.removeObserver(scoreObserver)
    }

    @Test
    fun `subtractScore(1) does not decrement score when zero`() {
        // Arrange
        val scoreObserver = Observer<Int> {}
        viewModel.team1Score.observeForever(scoreObserver)

        // Act
        viewModel.subtractScore(1)

        // Assert
        assertEquals(0, viewModel.team1Score.value)

        // Cleanup
        viewModel.team1Score.removeObserver(scoreObserver)
    }

    @Test
    fun `subtractScore(2) does not decrement score when zero`() {
        // Arrange
        val scoreObserver = Observer<Int> {}
        viewModel.team2Score.observeForever(scoreObserver)

        // Act
        viewModel.subtractScore(2)

        // Assert
        assertEquals(0, viewModel.team2Score.value)

        // Cleanup
        viewModel.team2Score.removeObserver(scoreObserver)
    }

    @Test
    fun `setTeam1Name updates team1Name LiveData`() {
        // Arrange
        val nameObserver = Observer<String> {}
        viewModel.team1Name.observeForever(nameObserver)
        val newName = "New Team 1"

        // Act
        viewModel.setTeam1Name(newName)

        // Assert
        assertEquals(newName, viewModel.team1Name.value)

        // Cleanup
        viewModel.team1Name.removeObserver(nameObserver)
    }

    @Test
    fun `setTeam2Name updates team2Name LiveData`() {
        // Arrange
        val nameObserver = Observer<String> {}
        viewModel.team2Name.observeForever(nameObserver)
        val newName = "New Team 2"

        // Act
        viewModel.setTeam2Name(newName)

        // Assert
        assertEquals(newName, viewModel.team2Name.value)

        // Cleanup
        viewModel.team2Name.removeObserver(nameObserver)
    }

    @Test
    fun `startStopMatchTimer calls startTimer when timer is not running`() {
        // Arrange
        // Mock the internal live data or service behavior
        val isRunningLiveData = androidx.lifecycle.MutableLiveData<Boolean>()
        isRunningLiveData.value = false
        `when`(mockMatchTimerService.isMatchTimerRunning).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(false))

        // We also need to manipulate the ViewModel's internal LiveData which mirrors the service state
        // Reflection to set _isMatchTimerRunning
        val isMatchTimerRunningField = viewModel.javaClass.getDeclaredField("_isMatchTimerRunning")
        isMatchTimerRunningField.isAccessible = true
        val mutableLiveData = isMatchTimerRunningField.get(viewModel) as androidx.lifecycle.MutableLiveData<Boolean>
        mutableLiveData.postValue(false)

        // Act
        viewModel.startStopMatchTimer()

        // Assert
        verify(mockMatchTimerService).startTimer()
    }

    @Test
    fun `startStopMatchTimer calls pauseTimer when timer is running`() {
        // Arrange
        val isMatchTimerRunningField = viewModel.javaClass.getDeclaredField("_isMatchTimerRunning")
        isMatchTimerRunningField.isAccessible = true
        val mutableLiveData = isMatchTimerRunningField.get(viewModel) as androidx.lifecycle.MutableLiveData<Boolean>
        mutableLiveData.postValue(true)

        `when`(mockMatchTimerService.isMatchTimerRunning).thenReturn(kotlinx.coroutines.flow.MutableStateFlow(true))

        // Act
        viewModel.startStopMatchTimer()

        // Assert
        verify(mockMatchTimerService).pauseTimer()
    }

    @Test
    fun `resetMatchTimer calls stopTimer`() {
        // Act
        viewModel.resetMatchTimer()

        // Assert
        verify(mockMatchTimerService).resetTimer()
    }

    @Test
    fun `startKeeperTimer calls startKeeperTimer on service`() {
        // Arrange
        val duration = 10000L
        viewModel.setKeeperTimer(duration / 1000)

        // Act
        viewModel.startKeeperTimer()

        // Assert
        verify(mockMatchTimerService).startKeeperTimer(duration)
    }

    @Test
    fun `resetKeeperTimer calls resetKeeperTimer on service`() {
        // Act
        viewModel.resetKeeperTimer()

        // Assert
        verify(mockMatchTimerService).resetKeeperTimer()
    }

    @Test
    fun `endMatch resets scores and stops timer`() =
        runTest {
            // Arrange
            val score1Observer = Observer<Int> {}
            val score2Observer = Observer<Int> {}
            val eventsObserver = Observer<List<MatchEvent>> {}
            viewModel.team1Score.observeForever(score1Observer)
            viewModel.team2Score.observeForever(score2Observer)
            viewModel.matchEvents.observeForever(eventsObserver)

            // Set initial scores by calling add score methods
            viewModel.addScore(1)
            viewModel.addScore(2)
            advanceUntilIdle() // Allow suspend functions in addScore to complete

            // Ensure timer service is set to running and has non-zero value
            // We must update the mock service flows because ViewModel collects them
            val timerFlow = kotlinx.coroutines.flow.MutableStateFlow(1000L)
            whenever(mockMatchTimerService.matchTimerValue).thenReturn(timerFlow)

            // Also update ViewModel LiveData directly to be sure, as collection happens in coroutine
            val matchTimerValueField = viewModel.javaClass.getDeclaredField("_matchTimerValue")
            matchTimerValueField.isAccessible = true
            val timerLiveData = matchTimerValueField.get(viewModel) as androidx.lifecycle.MutableLiveData<Long>
            timerLiveData.postValue(1000L)

            val isMatchTimerRunningField = viewModel.javaClass.getDeclaredField("_isMatchTimerRunning")
            isMatchTimerRunningField.isAccessible = true
            val mutableLiveData = isMatchTimerRunningField.get(viewModel) as androidx.lifecycle.MutableLiveData<Boolean>
            mutableLiveData.postValue(true)

            shadowOf(Looper.getMainLooper()).idle()
            advanceUntilIdle() // Process flow collection updates

            // Act
            val result = viewModel.endMatch()

            // If endMatch returned false, assert failure immediately with helpful message
            assert(result) { "endMatch() returned false, probably because it thinks match hasn't started" }

            // Idle main looper for MutableLiveData posts inside endMatch/startNewMatch
            shadowOf(Looper.getMainLooper()).idle()
            advanceUntilIdle() // Allow suspend functions in endMatch to complete

            // Allow postValue to propagate
            shadowOf(Looper.getMainLooper()).idle()

            // Assert
            // Verify DAO interactions to debug why score didn't reset
            // Retrieve mock DAO via reflection since it's private
            val matchDaoField = MainViewModel::class.java.getDeclaredField("matchDao")
            matchDaoField.isAccessible = true
            val injectedMatchDao = matchDaoField.get(viewModel) as MatchDao
            verify(injectedMatchDao).insert(any())

            // Verify startNewMatch was called
            verify(mockMatchTimerService).resetTimer()

            assertEquals(0, viewModel.team1Score.value)
            assertEquals(0, viewModel.team2Score.value)
            verify(mockMatchTimerService, atLeastOnce()).stopTimer()

            // "Match ended" event is added but then cleared by startNewMatch which adds "New match ready"
            // So we check for the new match event instead
            val newMatchEvent = viewModel.matchEvents.value?.find { it.event.contains("New match ready") }
            assert(newMatchEvent != null)

            // Cleanup
            viewModel.team1Score.removeObserver(score1Observer)
            viewModel.team2Score.removeObserver(score2Observer)
            viewModel.matchEvents.removeObserver(eventsObserver)
        }

    /**
     * Nel padel il punteggio di testata sono i set vinti: 0-0 finche' non se ne chiude uno, e
     * senza orologio il cronometro resta fermo. La guardia su quei due numeri rifiutava di
     * salvare qualunque partita interrotta prima della fine del primo set.
     */
    @Test
    fun `padel interrotto prima della fine del primo set si salva`() =
        runTest {
            val matchDao = campo("matchDao") as MatchDao
            viewModel.selectSport(SportRegistry.PADEL)
            advanceUntilIdle()
            assertEquals("senza punti non c'e' niente da salvare", false, viewModel.endMatch())

            repeat(3) { viewModel.addScore(1) }
            advanceUntilIdle()
            assertEquals("40-0 al primo game: nessun set vinto", 0, viewModel.team1Score.value)

            assertEquals(true, viewModel.endMatch())
            advanceUntilIdle()
            verify(matchDao).closeMatch(any(), any(), any())
        }

    /**
     * Rilievo della validazione (L1): la rosa tiene la copia del giocatore letta quando e' stato aggiunto, e la
     * fine partita la riscriveva per intero con un @Update per contare la presenza. Il gol
     * segnato nella partita, gia' scritto nel database, tornava indietro.
     *
     * Database vero in memoria, perche' il difetto sta proprio nella riga che finisce su disco.
     * Esecutori diretti, cosi' che le scritture lanciate dal ViewModel finiscano dentro
     * advanceUntilIdle invece che su un thread che il test non aspetta.
     */
    @Test
    fun `fine partita conta la presenza senza riportare indietro i gol scritti nel database`() =
        runTest {
            val db =
                Room
                    .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
                    .allowMainThreadQueries()
                    .setQueryExecutor { it.run() }
                    .setTransactionExecutor { it.run() }
                    .build()
            try {
                val playerDao = db.playerDao()
                val matchDao = db.matchDao()
                imposta("playerDao", playerDao)
                imposta("matchDao", matchDao)
                val marioId = playerDao.insert(Player(playerName = "Mario", appearances = 2, goals = 5)).toInt()
                viewModel.addPlayerToTeam(PlayerWithRoles(Player(marioId, "Mario", 2, 5), emptyList()), 1)

                viewModel.addScore(1)
                advanceUntilIdle()
                // Il gol attribuito a Mario: e' la scrittura che fa l'attribuzione dal registro.
                playerDao.incrementGoals(marioId)

                assertEquals(true, viewModel.endMatch())
                advanceUntilIdle()

                val mario =
                    playerDao
                        .getAllPlayers()
                        .first()
                        .single()
                        .player
                assertEquals("il gol della partita resta", 6, mario.goals)
                assertEquals("e la presenza si conta una volta", 3, mario.appearances)
                assertEquals("la riga viva e' chiusa", null, matchDao.getActiveMatchOnce())
                assertEquals(
                    "e Mario risulta nella squadra che ha vinto",
                    listOf(PlayerWinCount(marioId, 1)),
                    matchDao.getPlayerWinCounts().first(),
                )
            } finally {
                db.close()
            }
        }

    /** Database vero in memoria con esecutori diretti, come nel test delle presenze qui sopra. */
    private fun databaseInMemoria(): AppDatabase =
        Room
            .inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()

    /**
     * Richieste 1 e 2 di docs/dashboard/SCOREBOARD_FORMAT.md: una partita di padel chiusa si
     * esporta dallo storico, ed e' lo STESSO file che si sarebbe esportato dal vivo, id compreso.
     *
     * Prima della 14 l'export esisteva solo dal vivo: endMatch azzera il motore, e la riga chiusa
     * non aveva ne' l'ordine di servizio ne' l'inizio. Il confronto e' sul JSON intero: id e
     * inizio salvati al primo punto, ordine di servizio, giocatori con il loro lato, punti.
     */
    @Test
    fun `una partita di padel chiusa si esporta dallo storico con lo stesso file e lo stesso id`() =
        runTest {
            val db = databaseInMemoria()
            try {
                val playerDao = db.playerDao()
                val matchDao = db.matchDao()
                imposta("playerDao", playerDao)
                imposta("matchDao", matchDao)
                viewModel.selectSport(SportRegistry.PADEL)
                advanceUntilIdle()
                val nomi = listOf("Marco", "Anna", "Luca", "Sara")
                val ids = nomi.map { playerDao.insert(Player(playerName = it, appearances = 0, goals = 0)).toInt() }
                // Marco e Luca nel roster 1, Anna e Sara nel 2: l'ordine di servizio che ne
                // deriva e' Marco, Anna, Luca, Sara.
                nomi.forEachIndexed { i, nome ->
                    viewModel.addPlayerToTeam(PlayerWithRoles(Player(ids[i], nome, 0, 0), emptyList()), if (i % 2 == 0) 1 else 2)
                }

                repeat(5) { viewModel.addScore(1) }
                repeat(2) { viewModel.addScore(2) }
                advanceUntilIdle()

                val viva = matchDao.getActiveMatchOnce()!!
                assertEquals("l'ordine si salva al primo punto", ids.joinToString(","), viva.serveOrder)
                assertTrue("l'inizio si salva al primo punto", viva.startedAt != null)
                assertTrue("l'id si salva al primo punto", viva.matchUuid != null)

                val dalVivo = viewModel.buildExport()
                assertTrue("atteso Ready, ottenuto $dalVivo", dalVivo is ExportResult.Ready)
                val fileDalVivo = MatchExporter.toJson((dalVivo as ExportResult.Ready).export)
                assertEquals(viva.matchUuid, dalVivo.export.matchId)

                assertEquals(true, viewModel.endMatch())
                advanceUntilIdle()
                assertTrue("endMatch azzera il motore: dal vivo non resta niente", viewModel.buildExport() is ExportResult.Incomplete)

                val dalloStorico = viewModel.buildSavedExport(viva.matchId)
                assertTrue("atteso Ready, ottenuto $dalloStorico", dalloStorico is ExportResult.Ready)
                assertEquals(fileDalVivo, MatchExporter.toJson((dalloStorico as ExportResult.Ready).export))
                // Nell'ordine dei roster, come dal vivo: prima il lato 1, poi il 2.
                assertEquals(listOf("Marco", "Luca", "Anna", "Sara"), dalloStorico.export.players.map { it.name })
                assertEquals(listOf(1, 1, 2, 2), dalloStorico.export.players.map { it.side })
            } finally {
                db.close()
            }
        }

    /**
     * Una partita segnata dal solo orologio e consegnata piu' tardi e' una partita di QUANDO la
     * si e' giocata: l'inizio salvato e' il primo tocco, non l'ora della consegna.
     */
    @Test
    fun `una partita consegnata dall'orologio salva come inizio il primo tocco`() =
        runTest {
            val db = databaseInMemoria()
            try {
                imposta("matchDao", db.matchDao())
                val ore18 = 1_757_000_000_000L
                val punto = WearConstants.INTENT_POINT
                val sep = WearConstants.BATCH_FIELD_SEPARATOR
                val batch =
                    listOf("$punto${sep}1$sep$ore18", "$punto${sep}2$sep${ore18 + 30_000L}")
                        .joinToString(WearConstants.BATCH_SEPARATOR)
                val applica = MainViewModel::class.java.getDeclaredMethod("applyWatchBatch", String::class.java, Long::class.java)
                applica.isAccessible = true
                applica.invoke(viewModel, batch, 1L)
                advanceUntilIdle()

                assertEquals(ore18, db.matchDao().getActiveMatchOnce()?.startedAt)
            } finally {
                db.close()
            }
        }

    /**
     * Con uno sport diverso dal calcio gia' salvato, il ViewModel si costruisce senza crash.
     *
     * Visto su emulatore: con `active_sport = padel` l'app crashava a OGNI apertura. Il
     * collettore delle impostazioni, lanciato in init, riceve la prima emissione subito e chiama
     * applySport, che scriveva _scoreDisplay quando era ancora null perche' dichiarato dopo init.
     *
     * Tre dettagli, e ciascuno serve a far fallire questo test quando il difetto c'e':
     *
     * - il dispatcher e' IMMEDIATO. Con lo StandardTestDispatcher del setup la coroutine di init
     *   partirebbe solo dopo il costruttore, quando tutti i campi esistono gia'. In produzione
     *   Main.immediate la esegue DENTRO il costruttore;
     * - il corpo sta dentro `runTest`. L'NPE nasce in una coroutine di viewModelScope: fuori da
     *   runTest finisce nel gestore delle eccezioni non catturate e il test passa lo stesso.
     *   runTest invece raccoglie le eccezioni non gestite anche fuori dal proprio scope e fallisce;
     * - l'asserzione su activeSport da sola NON basta: applySport lo scrive prima della riga che
     *   crashava. La prima stesura di questo test si fermava li', e una falsificazione -- difetto
     *   rimesso, test ancora verde -- ha dimostrato che non proteggeva niente.
     */
    @Test
    fun `con padel salvato il ViewModel si costruisce senza crash`() =
        runTest(UnconfinedTestDispatcher()) {
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            whenever(mockMatchSettingsRepository.getSettingsFlow()).thenReturn(
                flowOf(
                    MatchSettings(
                        team1Name = "Team 1",
                        team2Name = "Team 2",
                        team1Color = Color.RED,
                        team2Color = Color.BLUE,
                        keeperTimerDuration = 300L,
                        activeSport = SportRegistry.PADEL,
                    ),
                ),
            )

            val conPadel =
                MainViewModel(mockRepository, mockUserPreferencesRepository, mockMatchSettingsRepository, mockApplication)

            assertEquals(SportRegistry.PADEL, conPadel.activeSport.value)
        }

    /**
     * Una partita ripresa dopo la chiusura dell'app resta la STESSA partita anche nel file: id e
     * inizio si rileggono dalla riga, altrimenti l'export dopo la ripresa ne mancherebbe e chi
     * importa non riconoscerebbe il doppione con un export fatto prima.
     */
    @Test
    fun `alla ripresa l'export porta l'id e l'inizio salvati sulla riga`() =
        runTest {
            val ore18 = 1_757_000_000_000L
            partitaSalvata("1|1@0,2@30000", matchUuid = "3f2a9c1e-5b7d-4e8a-9c01-2d4f6a8b0c1e", startedAt = ore18)
            advanceUntilIdle()
            listOf(1, 2, 3, 4).forEach { id ->
                viewModel.addPlayerToTeam(PlayerWithRoles(Player(id, "G$id", 0, 0), emptyList()), if (id <= 2) 1 else 2)
            }

            val esito = viewModel.buildExport()
            assertTrue("atteso Ready, ottenuto $esito", esito is ExportResult.Ready)
            val export = (esito as ExportResult.Ready).export
            assertEquals("3f2a9c1e-5b7d-4e8a-9c01-2d4f6a8b0c1e", export.matchId)
            // Il fuso e' quello della macchina che fa girare il test: si confronta l'istante.
            val inizio = java.time.OffsetDateTime.parse(export.startedAt)
            assertEquals(ore18, inizio.toInstant().toEpochMilli())
        }

    /**
     * Prepara una partita aperta con [eventLog] come se l'app fosse stata chiusa e riaperta.
     *
     * Va chiamata dentro runTest, e seguita da advanceUntilIdle: il ripristino gira in coroutine.
     */
    private fun partitaSalvata(
        eventLog: String,
        rosa: List<PlayerWithRoles> = emptyList(),
        matchUuid: String? = null,
        startedAt: Long? = null,
    ): PlayerDao {
        val playerDao = campo("playerDao") as PlayerDao
        val matchDao = campo("matchDao") as MatchDao
        whenever(playerDao.getAllPlayers()).thenReturn(flowOf(rosa))
        val salvata =
            Match(
                matchId = 5,
                team1Id = 1,
                team2Id = 2,
                team1Score = 0,
                team2Score = 0,
                timestamp = 0L,
                isActive = true,
                eventLog = eventLog,
                startedAt = startedAt,
                matchUuid = matchUuid,
            )
        kotlinx.coroutines.runBlocking {
            whenever(matchDao.getActiveMatchOnce()).thenReturn(salvata)
        }
        // Il ripristino lanciato in init e' gia' girato, a vuoto, quando runTest ha smaltito le
        // coroutine accodate prima del corpo del test. Lo si rilancia sopra la partita appena
        // azzerata: e' lo stesso ordine della produzione (startNewMatch, poi ripristino).
        val ripristino = MainViewModel::class.java.getDeclaredMethod("restoreActiveMatchIfAny")
        ripristino.isAccessible = true
        ripristino.invoke(viewModel)
        return playerDao
    }

    private fun campo(nome: String): Any? {
        val field = MainViewModel::class.java.getDeclaredField(nome)
        field.isAccessible = true
        return field.get(viewModel)
    }

    private fun imposta(
        nome: String,
        valore: Any,
    ) {
        val field = MainViewModel::class.java.getDeclaredField(nome)
        field.isAccessible = true
        field.set(viewModel, valore)
    }

    private fun righeDiPunto(): List<MatchEvent> =
        viewModel.matchEvents.value
            .orEmpty()
            .filter { it.type == MatchEventType.SCORE }

    /**
     * Visto su emulatore: partita salvata a 15-0, app riaperta, punteggio giusto ma nel registro
     * solo "Partita ripresa" e nessun annullamento. I punti recuperati erano diventati definitivi.
     */
    @Test
    fun `alla ripresa il registro ha una riga per punto e si puo' annullare`() =
        runTest {
            val mario = PlayerWithRoles(Player(7, "Mario", 3, 0), emptyList())
            val playerDao = partitaSalvata("1|1:7,2", rosa = listOf(mario))
            val eventiObserver = Observer<List<MatchEvent>> {}
            val undoObserver = Observer<Boolean> {}
            viewModel.matchEvents.observeForever(eventiObserver)
            viewModel.canUndo.observeForever(undoObserver)

            advanceUntilIdle()

            val punti = righeDiPunto()
            assertEquals(2, punti.size)
            // In testa il piu' recente, come per i punti segnati dal vivo.
            assertEquals(2, punti[0].team)
            assertEquals(1, punti[0].engineIndex)
            assertEquals(null, punti[0].playerId)
            assertEquals(1, punti[1].team)
            assertEquals(0, punti[1].engineIndex)
            assertEquals(7, punti[1].playerId)
            assertEquals("Mario", punti[1].player)
            assertEquals(true, viewModel.canUndo.value)
            // Il gol di Mario era gia' stato contato quando e' stato attribuito.
            verify(playerDao, times(0)).incrementGoals(any())

            viewModel.matchEvents.removeObserver(eventiObserver)
            viewModel.canUndo.removeObserver(undoObserver)
        }

    @Test
    fun `annullare dopo la ripresa toglie il punto, la riga e il gol del giocatore`() =
        runTest {
            val mario = PlayerWithRoles(Player(7, "Mario", 3, 0), emptyList())
            val playerDao = partitaSalvata("1|1,1:7", rosa = listOf(mario))
            val eventiObserver = Observer<List<MatchEvent>> {}
            val scoreObserver = Observer<Int> {}
            viewModel.matchEvents.observeForever(eventiObserver)
            viewModel.team1Score.observeForever(scoreObserver)
            advanceUntilIdle()
            assertEquals(2, viewModel.team1Score.value)

            viewModel.undoLastGoal()
            advanceUntilIdle()

            assertEquals(1, viewModel.team1Score.value)
            val punti = righeDiPunto()
            assertEquals(1, punti.size)
            assertEquals(0, punti[0].engineIndex)
            verify(playerDao).decrementGoals(7)

            viewModel.matchEvents.removeObserver(eventiObserver)
            viewModel.team1Score.removeObserver(scoreObserver)
        }

    /**
     * Il registro trattava il tempo trascorso come una data: SimpleDateFormat("mm:ss") su
     * Date(ms). Un gol al 65:10 diventava "05:10", e in India (+5:30) "35:10". Il fuso qui e'
     * quello che rompeva di piu': senza il rimedio questa riga non dice mai 66'.
     */
    @Test
    fun `il minuto del registro viene dai millisecondi, oltre l'ora e in ogni fuso`() =
        runTest {
            val prima = java.util.TimeZone.getDefault()
            try {
                java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Kolkata"))
                val eventiObserver = Observer<List<MatchEvent>> {}
                viewModel.matchEvents.observeForever(eventiObserver)
                // Prima si lascia arrivare lo 0 del servizio finto, poi si porta il tempo al 65:10.
                shadowOf(Looper.getMainLooper()).idle()
                advanceUntilIdle()
                @Suppress("UNCHECKED_CAST")
                (campo("_matchTimerValue") as androidx.lifecycle.MutableLiveData<Long>).value = 3_910_000L

                viewModel.addScore(1)
                advanceUntilIdle()

                assertEquals("66'", righeDiPunto().first().timestamp)
                viewModel.matchEvents.removeObserver(eventiObserver)
            } finally {
                java.util.TimeZone.setDefault(prima)
            }
        }

    /** Senza cronometro un minuto sarebbe sempre 1': la colonna resta vuota. */
    @Test
    fun `nel padel il registro non scrive un minuto`() =
        runTest {
            val eventiObserver = Observer<List<MatchEvent>> {}
            viewModel.matchEvents.observeForever(eventiObserver)
            assertEquals(true, viewModel.selectSport(SportRegistry.PADEL))

            viewModel.addScore(1)
            advanceUntilIdle()

            assertEquals("", righeDiPunto().first().timestamp)
            viewModel.matchEvents.removeObserver(eventiObserver)
        }

    // --- L3: annullamento e attribuzione legati al registro del motore ---

    private val mario = PlayerWithRoles(Player(7, "Mario", 3, 0), emptyList())

    /** Un messaggio dall'orologio, consegnato al ricevitore di QUESTO ViewModel e di nessun altro. */
    private fun ricevi(intent: Intent) {
        (campo("broadcastReceiver") as BroadcastReceiver).onReceive(ApplicationProvider.getApplicationContext(), intent)
    }

    private fun puntoDallOrologio(side: Int) =
        Intent(SimplifiedDataLayerListenerService.ACTION_SCORE_INTENT)
            .putExtra(WearConstants.KEY_SIDE, side)
            .putExtra(WearConstants.KEY_INTENT_KIND, WearConstants.INTENT_POINT)

    private fun motore() = campo("engine") as MatchEngine

    /**
     * Rilievo L3 (alta): nel calcio il '-' e' una correzione nel motore, ma la pila degli
     * annullamenti non la conteneva. Gol di Mario, gol, '-': ANNULLA toglieva dal motore la
     * correzione e dal registro e dalle statistiche un gol vero, con Mario a -1.
     */
    @Test
    fun `nel calcio ANNULLA dopo una correzione toglie la correzione e non un gol`() =
        runTest {
            val playerDao = campo("playerDao") as PlayerDao
            val eventiObserver = Observer<List<MatchEvent>> {}
            val scoreObserver = Observer<Int> {}
            viewModel.matchEvents.observeForever(eventiObserver)
            viewModel.team1Score.observeForever(scoreObserver)

            viewModel.addScore(1)
            viewModel.attributeScorer(0, mario)
            viewModel.addScore(1)
            viewModel.subtractScore(1)
            advanceUntilIdle()
            assertEquals(1, viewModel.team1Score.value)

            viewModel.undoLastGoal()
            advanceUntilIdle()

            assertEquals("si annulla la correzione: si torna a 2-0", 2, viewModel.team1Score.value)
            assertEquals("i due gol restano nel registro", listOf(1, 0), righeDiPunto().map { it.engineIndex })
            assertEquals(
                "la riga della correzione se ne va",
                null,
                viewModel.matchEvents.value
                    .orEmpty()
                    .find { it.event.startsWith("Score correction") },
            )
            verify(playerDao, never()).decrementGoals(any())

            viewModel.undoLastGoal()
            viewModel.undoLastGoal()
            advanceUntilIdle()

            assertEquals(0, viewModel.team1Score.value)
            assertEquals(emptyList<MatchEvent>(), righeDiPunto())
            verify(playerDao, times(1)).decrementGoals(7)
            assertEquals(false, viewModel.canUndo.value)

            viewModel.matchEvents.removeObserver(eventiObserver)
            viewModel.team1Score.removeObserver(scoreObserver)
        }

    /**
     * Rilievo L3: la pila teneva il gol senza marcatore anche dopo l'attribuzione dal registro,
     * quindi ANNULLA riportava il punteggio a 0-0 e lasciava a Mario il gol.
     */
    @Test
    fun `attribuire dal registro e poi annullare toglie il gol al giocatore`() =
        runTest {
            val playerDao = campo("playerDao") as PlayerDao
            val scoreObserver = Observer<Int> {}
            viewModel.team1Score.observeForever(scoreObserver)

            viewModel.addScore(1)
            viewModel.attributeScorer(0, mario)
            advanceUntilIdle()
            viewModel.undoLastGoal()
            advanceUntilIdle()

            assertEquals(0, viewModel.team1Score.value)
            verify(playerDao).decrementGoals(7)
            viewModel.team1Score.removeObserver(scoreObserver)
        }

    /**
     * Rilievo L3 (alta): padel segnato dal solo orologio e consegnato. Punteggio giusto, registro
     * vuoto, e il '-' dell'orologio (un annullamento) non faceva niente.
     */
    @Test
    fun `la partita consegnata dall'orologio ha le sue righe e si annulla dal polso`() =
        runTest {
            val eventiObserver = Observer<List<MatchEvent>> {}
            val undoObserver = Observer<Boolean> {}
            viewModel.matchEvents.observeForever(eventiObserver)
            viewModel.canUndo.observeForever(undoObserver)
            val connessione = campo("connectionManager") as OptimizedWearDataSync
            kotlinx.coroutines.runBlocking { whenever(connessione.sendMessage(any(), any())).thenReturn(true) }
            assertEquals(true, viewModel.selectSport(SportRegistry.PADEL))
            advanceUntilIdle()

            ricevi(
                Intent(SimplifiedDataLayerListenerService.ACTION_INTENT_BATCH)
                    .putExtra(WearConstants.KEY_INTENT_BATCH, "point,1,1000;point,1,2000;point,2,3000")
                    .putExtra(WearConstants.KEY_SEQ, 4L),
            )
            advanceUntilIdle()

            assertEquals(3, motore().log.size)
            assertEquals(listOf(2, 1, 0), righeDiPunto().map { it.engineIndex })
            assertEquals(true, viewModel.canUndo.value)

            ricevi(
                Intent(SimplifiedDataLayerListenerService.ACTION_SCORE_INTENT)
                    .putExtra(WearConstants.KEY_SIDE, 2)
                    .putExtra(WearConstants.KEY_INTENT_KIND, WearConstants.INTENT_UNDO),
            )
            advanceUntilIdle()

            assertEquals(2, motore().log.size)
            assertEquals(listOf(1, 0), righeDiPunto().map { it.engineIndex })

            viewModel.matchEvents.removeObserver(eventiObserver)
            viewModel.canUndo.removeObserver(undoObserver)
        }

    /**
     * Rilievo L3: il marcatore scelto al polso andava all'ultimo punto del motore al suo arrivo.
     * Se nel frattempo sul telefono aveva segnato l'altra squadra, finiva su quel punto; e con le
     * squadre vuote nasceva anche una seconda riga per lo stesso gol.
     */
    @Test
    fun `il marcatore dall'orologio va al suo punto anche se nel frattempo ha segnato l'altra squadra`() =
        runTest {
            val playerDao = campo("playerDao") as PlayerDao
            val eventiObserver = Observer<List<MatchEvent>> {}
            viewModel.matchEvents.observeForever(eventiObserver)
            advanceUntilIdle()

            ricevi(puntoDallOrologio(1))
            viewModel.addScore(2)
            // Subito prima della scelta: l'archivio letto in init potrebbe riscriverlo a vuoto.
            @Suppress("UNCHECKED_CAST")
            (campo("_allPlayers") as MutableLiveData<List<PlayerWithRoles>>).value = listOf(mario)
            ricevi(
                Intent(SimplifiedDataLayerListenerService.ACTION_SCORER_SELECTED)
                    .putExtra(WearConstants.KEY_PLAYER_NAME, "Mario")
                    .putExtra(WearConstants.EXTRA_TEAM_NUMBER, 1)
                    .putExtra(WearConstants.KEY_PLAYER_ID, 7),
            )
            advanceUntilIdle()

            val punti = motore().events.filterIsInstance<ScoringEvent.Point>()
            assertEquals("il punto della squadra 1 e' di Mario", 7, punti[0].playerId)
            assertEquals("quello della squadra 2 resta senza marcatore", null, punti[1].playerId)
            val righe = righeDiPunto()
            assertEquals("una riga per punto", 2, righe.size)
            assertEquals(7, righe.single { it.engineIndex == 0 }.playerId)
            assertEquals(null, righe.single { it.engineIndex == 1 }.playerId)
            verify(playerDao, times(1)).incrementGoals(7)

            viewModel.matchEvents.removeObserver(eventiObserver)
        }

    /**
     * Rilievo L3: con la rosa della squadra piena il telefono aspettava la scelta dal polso e non
     * registrava il punto. Scegliendo NESSUNO il punto restava senza riga e senza annullamento.
     */
    @Test
    fun `con la rosa piena il punto dall'orologio ha comunque la sua riga e si annulla`() =
        runTest {
            val eventiObserver = Observer<List<MatchEvent>> {}
            val scoreObserver = Observer<Int> {}
            viewModel.matchEvents.observeForever(eventiObserver)
            viewModel.team1Score.observeForever(scoreObserver)
            viewModel.addPlayerToTeam(mario, 1)

            ricevi(puntoDallOrologio(1))
            advanceUntilIdle()
            assertEquals(listOf(0), righeDiPunto().map { it.engineIndex })

            viewModel.undoLastGoal()
            advanceUntilIdle()

            assertEquals(0, viewModel.team1Score.value)
            assertEquals(emptyList<MatchEvent>(), righeDiPunto())

            viewModel.matchEvents.removeObserver(eventiObserver)
            viewModel.team1Score.removeObserver(scoreObserver)
        }

    /**
     * Ora il punto dall'orologio ha sempre la sua riga, e quindi serve la guardia di addScore: a
     * partita finita il motore ignora il tocco, e senza la guardia nasceva una riga che puntava
     * all'ultimo punto vero, il cui annullamento avrebbe tolto quel punto e riaperto la partita.
     */
    @Test
    fun `a partita finita il punto dall'orologio non crea una riga`() =
        runTest {
            val eventiObserver = Observer<List<MatchEvent>> {}
            viewModel.matchEvents.observeForever(eventiObserver)
            assertEquals(true, viewModel.selectSport(SportRegistry.PADEL))
            advanceUntilIdle()
            var tocchi = 0
            while (viewModel.scoreDisplay.value?.matchOver != true && tocchi < 500) {
                viewModel.addScore(1)
                tocchi++
            }
            advanceUntilIdle()
            assertEquals(true, viewModel.scoreDisplay.value?.matchOver)
            val righe = righeDiPunto().size

            ricevi(puntoDallOrologio(2))
            advanceUntilIdle()

            assertEquals(righe, righeDiPunto().size)
            viewModel.matchEvents.removeObserver(eventiObserver)
        }

    /**
     * Rilievo L3: il dialogo del marcatore resta aperto quanto si vuole. Se nel frattempo il
     * punto e' stato annullato, o all'indice c'e' ormai un punto gia' attribuito, scegliere un
     * giocatore gli dava comunque un gol in piu'.
     */
    @Test
    fun `attribuire un punto che non c'e' piu' non da' un gol in piu'`() =
        runTest {
            val playerDao = campo("playerDao") as PlayerDao
            val luigi = PlayerWithRoles(Player(8, "Luigi", 1, 0), emptyList())

            viewModel.addScore(1)
            viewModel.undoLastGoal()
            viewModel.attributeScorer(0, mario)
            advanceUntilIdle()
            verify(playerDao, never()).incrementGoals(any())

            viewModel.addScore(1)
            viewModel.attributeScorer(0, mario)
            viewModel.attributeScorer(0, luigi)
            advanceUntilIdle()
            verify(playerDao, times(1)).incrementGoals(7)
            verify(playerDao, never()).incrementGoals(8)
        }

    /**
     * Il ripristino dopo la morte del processo deve lasciare le stesse righe e lo stesso
     * annullamento del percorso dal vivo. In particolare la correzione ricostruita deve essere
     * annullabile come quella dal vivo, togliendo la sua riga e non quella di un gol.
     */
    @Test
    fun `il ripristino lascia le stesse righe e lo stesso annullamento del percorso dal vivo`() =
        runTest {
            val eventiObserver = Observer<List<MatchEvent>> {}
            val scoreObserver = Observer<Int> {}
            val undoObserver = Observer<Boolean> {}
            viewModel.matchEvents.observeForever(eventiObserver)
            viewModel.team1Score.observeForever(scoreObserver)
            viewModel.canUndo.observeForever(undoObserver)

            viewModel.addScore(1)
            viewModel.attributeScorer(0, mario)
            viewModel.addScore(2)
            viewModel.addScore(1)
            viewModel.subtractScore(1)
            advanceUntilIdle()

            fun legateAlMotore() =
                viewModel.matchEvents.value
                    .orEmpty()
                    .filter { it.engineIndex != null }
                    .map { it.copy(timestamp = "") }
            val dalVivo = legateAlMotore()
            val registro = MatchLogCodec.encode(motore().log)
            assertEquals(4, dalVivo.size)

            val nuova = MainViewModel::class.java.getDeclaredMethod("startNewMatch")
            nuova.isAccessible = true
            nuova.invoke(viewModel)
            advanceUntilIdle()
            val playerDao = partitaSalvata(registro, rosa = listOf(mario))
            advanceUntilIdle()

            assertEquals(dalVivo, legateAlMotore())
            assertEquals(true, viewModel.canUndo.value)

            viewModel.undoLastGoal()
            advanceUntilIdle()

            assertEquals("si annulla la correzione", 2, viewModel.team1Score.value)
            assertEquals(listOf(2, 1, 0), righeDiPunto().map { it.engineIndex })
            verify(playerDao, never()).decrementGoals(any())

            viewModel.matchEvents.removeObserver(eventiObserver)
            viewModel.team1Score.removeObserver(scoreObserver)
            viewModel.canUndo.removeObserver(undoObserver)
        }

    private fun righeScadenza() =
        viewModel.matchEvents.value
            .orEmpty()
            .filter { it.event == "Keeper timer expired!" }

    /**
     * L8: il collector trattava ogni passaggio da "in corso" a "fermo" come una scadenza, e una
     * pausa dalla notifica o dall'orologio, o END MATCH, scriveva una riga falsa. Ora la riga
     * nasce solo dall'evento di scadenza del service.
     */
    @Test
    fun `pausa e azzeramento del portiere non scrivono la scadenza, la scadenza si`() =
        runTest {
            val eventiObserver = Observer<List<MatchEvent>> {}
            viewModel.matchEvents.observeForever(eventiObserver)
            // Si lascia arrivare onServiceConnected, che fa partire i collector.
            shadowOf(Looper.getMainLooper()).idle()
            advanceUntilIdle()

            portiereInCorso.value = true
            advanceUntilIdle()
            portiereInCorso.value = false
            advanceUntilIdle()
            assertEquals("la pausa non e' una scadenza", 0, righeScadenza().size)

            portiereScaduto.emit(Unit)
            advanceUntilIdle()
            assertEquals(1, righeScadenza().size)
            viewModel.matchEvents.removeObserver(eventiObserver)
        }

    /**
     * L8: il telefono manda il residuo alla ripresa, e l'orologio lo rimanda indietro quando
     * riparte da una pausa. Salvato come durata, dopo una pausa a 2:00 ogni conto successivo
     * durava 2 minuti. Con la chiave nuova la durata configurata arriva a parte.
     */
    @Test
    fun `dall'orologio il residuo di una ripresa non diventa la durata del portiere`() =
        runTest {
            shadowOf(Looper.getMainLooper()).idle()
            advanceUntilIdle()

            val ripresa =
                Intent(SimplifiedDataLayerListenerService.ACTION_KEEPER_TIMER_UPDATE).apply {
                    putExtra(WearConstants.KEY_KEEPER_MILLIS, 120_000L)
                    putExtra(WearConstants.KEY_KEEPER_RUNNING, true)
                    putExtra(WearConstants.KEY_KEEPER_DURATION, 300_000L)
                }
            androidx.localbroadcastmanager.content.LocalBroadcastManager
                .getInstance(ApplicationProvider.getApplicationContext())
                .sendBroadcast(ripresa)
            shadowOf(Looper.getMainLooper()).idle()

            verify(mockMatchTimerService).startKeeperTimer(300_000L, true)
            assertEquals(300_000L, campo("keeperTimerDuration"))
        }

    /** Un orologio vecchio non manda la durata: alla partenza manda la piena, e vale come prima. */
    @Test
    fun `senza la chiave nuova la partenza dall'orologio vecchio porta la durata come oggi`() =
        runTest {
            shadowOf(Looper.getMainLooper()).idle()
            advanceUntilIdle()

            val partenza =
                Intent(SimplifiedDataLayerListenerService.ACTION_KEEPER_TIMER_UPDATE).apply {
                    putExtra(WearConstants.KEY_KEEPER_MILLIS, 600_000L)
                    putExtra(WearConstants.KEY_KEEPER_RUNNING, true)
                }
            androidx.localbroadcastmanager.content.LocalBroadcastManager
                .getInstance(ApplicationProvider.getApplicationContext())
                .sendBroadcast(partenza)
            shadowOf(Looper.getMainLooper()).idle()

            verify(mockMatchTimerService).startKeeperTimer(600_000L, true)
        }

    /**
     * L8, B3: bindService prima di startNewMatch non rende il service disponibile in init.
     * onServiceConnected arriva in un messaggio successivo, quindi durante la costruzione il
     * service e' null in qualunque ordine. E alla connessione non si azzera niente: cronometro e
     * portiere salvati dal service appartengono alla partita che il ViewModel sta ripristinando.
     */
    @Test
    fun `alla costruzione il service non c'e' ancora, e alla connessione non si azzera niente`() =
        runTest {
            val nuovo = MainViewModel(mockRepository, mockUserPreferencesRepository, mockMatchSettingsRepository, mockApplication)
            val servizio = MainViewModel::class.java.getDeclaredField("matchTimerService").apply { isAccessible = true }
            assertEquals("in init il service non e' ancora legato", null, servizio.get(nuovo))

            shadowOf(Looper.getMainLooper()).idle()
            advanceUntilIdle()

            assertEquals(mockMatchTimerService, servizio.get(nuovo))
            verify(mockMatchTimerService, never()).resetTimer(any())
            verify(mockMatchTimerService, never()).resetKeeperTimer(any())
        }
}
