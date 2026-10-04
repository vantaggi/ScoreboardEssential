package it.vantaggi.scoreboardessential

import android.app.Application
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Looper
import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import it.vantaggi.scoreboardessential.core.ExportResult
import it.vantaggi.scoreboardessential.core.LoggedEvent
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
import it.vantaggi.scoreboardessential.repository.ColorRepository
import it.vantaggi.scoreboardessential.repository.MatchRepository
import it.vantaggi.scoreboardessential.repository.MatchSettings
import it.vantaggi.scoreboardessential.repository.MatchSettingsRepository
import it.vantaggi.scoreboardessential.repository.UserPreferencesRepository
import it.vantaggi.scoreboardessential.service.MatchTimerService
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import org.mockito.Mockito.mockingDetails
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

    // I ViewModel costruiti oltre a [viewModel]: anche il loro viewModelScope va chiuso a fine test.
    private val altriViewModel = mutableListOf<MainViewModel>()
    private lateinit var mockApplication: Application
    private lateinit var mockRepository: MatchRepository
    private lateinit var mockUserPreferencesRepository: UserPreferencesRepository
    private lateinit var mockMatchSettingsRepository: MatchSettingsRepository
    private lateinit var mockMatchTimerService: MatchTimerService

    // I finti che ogni ViewModel del test riceve dal costruttore, mai quelli veri.
    private lateinit var mockPlayerDao: PlayerDao
    private lateinit var mockMatchDao: MatchDao
    private lateinit var mockConnectionManager: OptimizedWearDataSync

    // Gli stati del portiere del service finto: i test di L8 li muovono.
    private val portiereInCorso = kotlinx.coroutines.flow.MutableStateFlow(false)
    private val portiereScaduto = kotlinx.coroutines.flow.MutableSharedFlow<Unit>()
    private val portiereValore = kotlinx.coroutines.flow.MutableStateFlow(0L)

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
        whenever(mockMatchTimerService.keeperTimerValue).thenReturn(portiereValore)
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

        // DAO e connection manager finti vanno al costruttore: sostituirli dopo la costruzione
        // lascerebbe aprire il database vero e partire il refresh vero su Dispatchers.IO, che a
        // fine test tornano su Dispatchers.Main mentre tearDown lo azzera.
        mockPlayerDao =
            mock(PlayerDao::class.java).apply {
                // Mock getAllPlayers to return empty flow to avoid NPEs if used
                `when`(getAllPlayers()).thenReturn(kotlinx.coroutines.flow.flowOf(emptyList()))
            }
        mockMatchDao = mock(MatchDao::class.java)
        mockConnectionManager = mock(OptimizedWearDataSync::class.java)
        // Stub connectionState flow to return empty or mock state
        whenever(
            mockConnectionManager.connectionState,
        ).thenReturn(
            kotlinx.coroutines.flow.MutableStateFlow(it.vantaggi.scoreboardessential.shared.communication.ConnectionState.Disconnected),
        )

        viewModel = creaViewModel()
        iniettaServizio(viewModel)

        // Mock insert to return a valid ID using whenever and runBlocking
        kotlinx.coroutines.runBlocking {
            whenever(mockMatchDao.insert(any())).thenReturn(1L)
            // La riga viva nasce con le rose in una transazione: sul finto il metodo di default
            // non gira, e senza stub restituirebbe null.
            whenever(mockMatchDao.insertLiveMatch(any(), any(), any())).thenReturn(1L)
            // Stub other suspend functions just in case
            whenever(mockMatchDao.insertMatchPlayerCrossRef(any())).thenReturn(Unit)
            whenever(mockMatchDao.insertMatchPlayerCrossRefs(any())).thenReturn(Unit)
            // Il ripristino legge le rose della riga: il finto, di suo, restituirebbe null.
            whenever(mockMatchDao.getMatchLineup(any())).thenReturn(emptyList())
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
        chiudiViewModel()
        Dispatchers.resetMain()
    }

    /**
     * Chiude il viewModelScope di tutti i ViewModel del test.
     *
     * Senza, i collettori lanciati in init e le scritture in fila della riga viva restano vivi dopo
     * il test: se il database e' gia' chiuso, riprendono sul looper principale e la loro eccezione
     * viene addebitata al PROSSIMO runTest (UncaughtExceptionsBeforeTest) di un test senza colpa.
     */
    private fun chiudiViewModel() {
        viewModel.viewModelScope.cancel()
        altriViewModel.forEach { it.viewModelScope.cancel() }
        altriViewModel.clear()
    }

    /**
     * Un ViewModel con tutto finto: database e connection manager veri non si creano mai, perche'
     * hanno thread propri (executor di Room, Dispatchers.IO) che sopravvivono al test.
     */
    private fun creaViewModel(
        colorRepository: ColorRepository = ColorRepository(mockApplication),
        playerDao: PlayerDao = mockPlayerDao,
        matchDao: MatchDao = mockMatchDao,
        connectionManager: OptimizedWearDataSync = mockConnectionManager,
    ): MainViewModel =
        MainViewModel(
            mockRepository,
            mockUserPreferencesRepository,
            mockMatchSettingsRepository,
            mockApplication,
            colorRepository,
            playerDao,
            matchDao,
            connectionManager,
        )

    /** Il service finto al posto di quello legato, come se onServiceConnected fosse gia' arrivato. */
    private fun iniettaServizio(vm: MainViewModel) {
        val serviceField: Field = MainViewModel::class.java.getDeclaredField("matchTimerService")
        serviceField.isAccessible = true
        serviceField.set(vm, mockMatchTimerService)

        val isBoundField: Field = MainViewModel::class.java.getDeclaredField("isServiceBound")
        isBoundField.isAccessible = true
        isBoundField.set(vm, true)
    }

    /**
     * Rifa [viewModel] con questi DAO (di solito quelli di un database in memoria), al posto di
     * sostituirli per riflessione dopo la costruzione.
     *
     * Le coroutine di init girano subito, sul database ancora vuoto: e' quello che succedeva con i
     * finti prima del corpo del test. Le righe che il test scrive dopo non vengono ripristinate da
     * init, ma dal ripristino che il test lancia a mano.
     */
    private fun usaDao(
        playerDao: PlayerDao = mockPlayerDao,
        matchDao: MatchDao = mockMatchDao,
    ) {
        viewModel.viewModelScope.cancel()
        viewModel = creaViewModel(playerDao = playerDao, matchDao = matchDao)
        iniettaServizio(viewModel)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    /** Il ViewModel va chiuso PRIMA del database, che altrimenti sparisce sotto le sue scritture. */
    private fun chiudiDatabase(db: AppDatabase) {
        chiudiViewModel()
        db.close()
    }

    /**
     * L'instabilita' di MainViewModelTest (MIGRATION_PLAN.md, "Causa probabile"): il costruttore
     * apriva l'AppDatabase vero e creava un OptimizedWearDataSync vero (refresh su Dispatchers.IO)
     * PRIMA che il test li sostituisse per riflessione. I loro thread tornavano su Main mentre
     * tearDown lo azzerava. Con le dipendenze al costruttore non nasce piu' nessuno dei due.
     */
    @Test
    fun `i ViewModel dei test non creano ne' l'AppDatabase vero ne' il connection manager vero`() {
        val istanza = AppDatabase::class.java.getDeclaredField("instance").apply { isAccessible = true }
        val precedente = istanza.get(null)
        istanza.set(null, null)
        try {
            val nuovo = creaViewModel()
            altriViewModel.add(nuovo)

            assertEquals("il singleton del database non e' stato aperto", null, istanza.get(null))
            assertTrue("il connection manager e' un finto", mockingDetails(nuovo.connectionManager).isMock)
            assertTrue("e' proprio quello passato", nuovo.connectionManager === mockConnectionManager)
            assertTrue("anche il ViewModel del setup", mockingDetails(viewModel.connectionManager).isMock)
        } finally {
            istanza.set(null, precedente)
        }
    }

    // Passo 10: i valori iniziali erano arancio e lime scritti nel ViewModel, mentre ColorRepository
    // (e il resto dell'app) dice giallo e verde. Una sola fonte: il ViewModel la chiede al repository.
    @Test
    fun `i colori iniziali delle squadre vengono da ColorRepository`() {
        val coloriFinti =
            mock(ColorRepository::class.java).apply {
                `when`(getTeam1DefaultColor()).thenReturn(0xFF112233.toInt())
                `when`(getTeam2DefaultColor()).thenReturn(0xFF445566.toInt())
            }

        val conRepositoryFinto =
            creaViewModel(colorRepository = coloriFinti)
        altriViewModel.add(conRepositoryFinto)

        assertEquals(0xFF112233.toInt(), conRepositoryFinto.team1Color.value)
        assertEquals(0xFF445566.toInt(), conRepositoryFinto.team2Color.value)
    }

    @Test
    fun `al primo avvio le squadre sono gialla e verde, non arancio e lime`() {
        val predefiniti = ColorRepository(ApplicationProvider.getApplicationContext<Application>())

        assertEquals(predefiniti.getTeam1DefaultColor(), viewModel.team1Color.value)
        assertEquals(predefiniti.getTeam2DefaultColor(), viewModel.team2Color.value)
        assertEquals(0xFFFFD600.toInt(), viewModel.team1Color.value)
        assertEquals(0xFF76FF03.toInt(), viewModel.team2Color.value)
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
            verify(injectedMatchDao).insertLiveMatch(any(), any(), any())

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
                usaDao(playerDao = playerDao, matchDao = matchDao)
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
                chiudiDatabase(db)
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
                usaDao(playerDao = playerDao, matchDao = matchDao)
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

                // Dallo storico l'export lo fa il repository: la cronologia non ha un MainViewModel.
                val app = ApplicationProvider.getApplicationContext<Application>()
                val dalloStorico = MatchRepository(matchDao, app, ColorRepository(app)).buildSavedExport(viva.matchId)
                assertTrue("atteso Ready, ottenuto $dalloStorico", dalloStorico is ExportResult.Ready)
                assertEquals(fileDalVivo, MatchExporter.toJson((dalloStorico as ExportResult.Ready).export))
                // Nell'ordine dei roster, come dal vivo: prima il lato 1, poi il 2.
                assertEquals(listOf("Marco", "Luca", "Anna", "Sara"), dalloStorico.export.players.map { it.name })
                assertEquals(listOf(1, 1, 2, 2), dalloStorico.export.players.map { it.side })
            } finally {
                chiudiDatabase(db)
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
                usaDao(matchDao = db.matchDao())
                val ore18 = 1_757_000_000_000L
                val punto = WearConstants.INTENT_POINT
                val sep = WearConstants.BATCH_FIELD_SEPARATOR
                val batch =
                    listOf("$punto${sep}1$sep$ore18", "$punto${sep}2$sep${ore18 + 30_000L}")
                        .joinToString(WearConstants.BATCH_SEPARATOR)
                ricevi(
                    Intent(SimplifiedDataLayerListenerService.ACTION_INTENT_BATCH)
                        .putExtra(WearConstants.KEY_INTENT_BATCH, batch)
                        .putExtra(WearConstants.KEY_SEQ, 1L),
                )
                advanceUntilIdle()

                assertEquals(ore18, db.matchDao().getActiveMatchOnce()?.startedAt)
            } finally {
                chiudiDatabase(db)
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

            val conPadel = creaViewModel()
            altriViewModel.add(conPadel)

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
        sportId: String = SportRegistry.FOOTBALL,
        serveOrder: String = "",
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
                sportId = sportId,
                serveOrder = serveOrder,
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

    private fun righeDelRegistro(): List<String> =
        viewModel.matchEvents.value
            .orEmpty()
            .map { it.event }

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
     * Un registro scritto da una versione precedente contiene ancora tocchi inerti: qui un '-' a
     * 0 (la correzione 'c2' sul lato 2 a zero, che non cambia lo stato). La ricostruzione lo salta,
     * e le righe devono portare l'indice della voce nel registro del MOTORE e non la loro
     * posizione nella lista, altrimenti ANNULLA su una riga toglie la voce sbagliata.
     */
    @Test
    fun `dopo la ripresa con un evento inerte in mezzo le righe hanno l'indice del motore e ANNULLA toglie la voce giusta`() =
        runTest {
            val luigi = PlayerWithRoles(Player(8, "Luigi", 2, 0), emptyList())
            // Indici del motore: 0 punto di Mario, 1 correzione inerte, 2 punto di Luigi, 3 punto senza marcatore.
            val playerDao = partitaSalvata("1|1:7,c2,2:8,1", rosa = listOf(mario, luigi))
            val eventiObserver = Observer<List<MatchEvent>> {}
            val scoreObserver = Observer<Int> {}
            viewModel.matchEvents.observeForever(eventiObserver)
            viewModel.team1Score.observeForever(scoreObserver)
            advanceUntilIdle()

            // In testa il piu' recente; l'indice 1 non ha riga e la posizione 1 non e' l'indice 1.
            assertEquals(listOf(3, 2, 0), righeDiPunto().map { it.engineIndex })
            assertEquals(listOf(null, 8, 7), righeDiPunto().map { it.playerId })

            // Primo ANNULLA: il punto senza marcatore, nessun gol da togliere.
            assertEquals(true, viewModel.undoLastGoal())
            advanceUntilIdle()
            assertEquals(listOf(2, 0), righeDiPunto().map { it.engineIndex })
            assertEquals(1, viewModel.team1Score.value)
            verify(playerDao, times(0)).decrementGoals(any())

            // Secondo: il punto di Luigi (indice 2), non quello di Mario.
            assertEquals(true, viewModel.undoLastGoal())
            advanceUntilIdle()
            assertEquals(listOf(0), righeDiPunto().map { it.engineIndex })
            verify(playerDao).decrementGoals(8)
            verify(playerDao, times(0)).decrementGoals(7)

            // Terzo: salta la correzione inerte in coda e toglie il punto di Mario.
            assertEquals(true, viewModel.undoLastGoal())
            advanceUntilIdle()
            assertEquals(emptyList<Int?>(), righeDiPunto().map { it.engineIndex })
            assertEquals(0, viewModel.team1Score.value)
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

    /**
     * L11: nel padel e nel tennis il tasto START e' nascosto, ma il registro diceva sempre
     * "press START to begin". Due strade portano alla riga: il cambio di sport (la riga scritta
     * all'avvio, col calcio di default, resta) e la partita nuova dopo una scartata.
     */
    @Test
    fun `nel padel il registro non chiede di premere START`() =
        runTest {
            val eventiObserver = Observer<List<MatchEvent>> {}
            viewModel.matchEvents.observeForever(eventiObserver)

            assertEquals(true, viewModel.selectSport(SportRegistry.PADEL))
            advanceUntilIdle()
            val dopoIlCambio = righeDelRegistro()
            assertTrue("dopo il cambio di sport: $dopoIlCambio", dopoIlCambio.none { it.contains("START") })

            viewModel.addScore(1)
            advanceUntilIdle()
            assertEquals(true, viewModel.discardMatch())
            advanceUntilIdle()
            val dopoLoScarto = righeDelRegistro()
            assertTrue("dopo lo scarto: $dopoLoScarto", dopoLoScarto.none { it.contains("START") })
            // La riga d'apertura c'e' ancora: cambia la frase, non sparisce.
            assertTrue("dopo lo scarto: $dopoLoScarto", dopoLoScarto.any { it.contains("New match ready") })

            viewModel.matchEvents.removeObserver(eventiObserver)
        }

    /** L11: la riga della ripresa era scritta in italiano nel codice, anche col telefono in inglese. */
    @Test
    fun `la riga della ripresa segue la lingua del telefono`() =
        runTest {
            partitaSalvata("1|1,2")
            val eventiObserver = Observer<List<MatchEvent>> {}
            viewModel.matchEvents.observeForever(eventiObserver)
            advanceUntilIdle()

            // Robolectric gira in inglese.
            val righe = righeDelRegistro()
            assertTrue("righe: $righe", "Match resumed" in righe)
            assertTrue("righe: $righe", "Partita ripresa" !in righe)

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
     * Il riepilogo dell'arretrato dice «N punti». Contava ogni voce applicata, quindi un arretrato
     * con due punti e un annullamento diceva «3 punti»: annullamenti e correzioni non sono punti.
     */
    @Test
    fun `il riepilogo dell'arretrato conta i punti e non gli annullamenti`() =
        runTest {
            val notizie = mutableListOf<WatchNotice?>()
            val osservatore = Observer<WatchNotice?> { notizie.add(it) }
            viewModel.watchNotice.observeForever(osservatore)
            val connessione = campo("connectionManager") as OptimizedWearDataSync
            kotlinx.coroutines.runBlocking { whenever(connessione.sendMessage(any(), any())).thenReturn(true) }

            ricevi(
                Intent(SimplifiedDataLayerListenerService.ACTION_INTENT_BATCH)
                    .putExtra(
                        WearConstants.KEY_INTENT_BATCH,
                        listOf(
                            voceDiArretrato(WearConstants.INTENT_POINT, 1, 1000),
                            voceDiArretrato(WearConstants.INTENT_POINT, 1, 2000),
                            voceDiArretrato(WearConstants.INTENT_POINT, 2, 3000),
                            voceDiArretrato(WearConstants.INTENT_UNDO, 2, 4000),
                            voceDiArretrato(WearConstants.INTENT_CORRECTION, 1, 5000),
                        ).joinToString(WearConstants.BATCH_SEPARATOR),
                    ).putExtra(WearConstants.KEY_SEQ, 4L),
            )
            advanceUntilIdle()

            // Il primo valore e' lo stato iniziale, null: poi la notizia, con i soli punti.
            assertEquals("tre punti, non cinque voci", listOf(null, WatchNotice.Applied(3)), notizie)
            assertEquals("e il riepilogo di 3 secondi dice gli stessi tre punti", 3, viewModel.takeWatchSummary())
            viewModel.watchNotice.removeObserver(osservatore)
        }

    @Test
    fun `un arretrato senza punti non scrive nessun riepilogo`() =
        runTest {
            val notizie = mutableListOf<WatchNotice?>()
            val osservatore = Observer<WatchNotice?> { notizie.add(it) }
            viewModel.watchNotice.observeForever(osservatore)
            val connessione = campo("connectionManager") as OptimizedWearDataSync
            kotlinx.coroutines.runBlocking { whenever(connessione.sendMessage(any(), any())).thenReturn(true) }

            ricevi(
                Intent(SimplifiedDataLayerListenerService.ACTION_INTENT_BATCH)
                    .putExtra(WearConstants.KEY_INTENT_BATCH, voceDiArretrato(WearConstants.INTENT_UNDO, 1, 1000))
                    .putExtra(WearConstants.KEY_SEQ, 4L),
            )
            advanceUntilIdle()

            assertEquals("«0 PUNTI» non e' una notizia: resta solo lo stato iniziale", listOf<WatchNotice?>(null), notizie)
            assertEquals("e non c'e' nessun riepilogo da dire", null, viewModel.takeWatchSummary())
            viewModel.watchNotice.removeObserver(osservatore)
        }

    private fun consegnaUnArretratoConUnPunto() {
        val connessione = campo("connectionManager") as OptimizedWearDataSync
        kotlinx.coroutines.runBlocking { whenever(connessione.sendMessage(any(), any())).thenReturn(true) }
        ricevi(
            Intent(SimplifiedDataLayerListenerService.ACTION_INTENT_BATCH)
                .putExtra(WearConstants.KEY_INTENT_BATCH, voceDiArretrato(WearConstants.INTENT_POINT, 1, 1000))
                .putExtra(WearConstants.KEY_SEQ, 4L),
        )
    }

    /**
     * Piano, passo 9: dopo un batch rifiutato watchNotice e' Rejected, e resta tale. Era un
     * SingleLiveEvent: chi guardava il campo quando la Snackbar e' passata non ne sapeva niente, e
     * dopo una rotazione l'avviso non tornava. Uno stato lo rilegge chiunque si iscriva dopo, come
     * fa l'Activity nuova dopo una ricreazione (il ViewModel e' lo stesso).
     */
    @Test
    fun `un arretrato rifiutato lascia watchNotice su Rejected anche per chi arriva dopo`() =
        runTest {
            viewModel.addScore(1)
            advanceUntilIdle()

            consegnaUnArretratoConUnPunto()
            advanceUntilIdle()

            val primaActivity = mutableListOf<WatchNotice?>()
            val primo = Observer<WatchNotice?> { primaActivity.add(it) }
            viewModel.watchNotice.observeForever(primo)
            assertEquals(listOf<WatchNotice?>(WatchNotice.Rejected), primaActivity)
            viewModel.watchNotice.removeObserver(primo)

            // L'Activity ricreata si iscrive di nuovo al ViewModel: la notizia deve esserci ancora.
            val dopoLaRicreazione = mutableListOf<WatchNotice?>()
            val secondo = Observer<WatchNotice?> { dopoLaRicreazione.add(it) }
            viewModel.watchNotice.observeForever(secondo)
            assertEquals("dopo la ricreazione", listOf<WatchNotice?>(WatchNotice.Rejected), dopoLaRicreazione)
            assertEquals("lo stato non e' un riepilogo di punti", null, viewModel.takeWatchSummary())
            viewModel.watchNotice.removeObserver(secondo)
        }

    /** Il badge dura fino alla partita nuova: lo azzera startNewMatch, non il tempo. */
    @Test
    fun `una partita nuova azzera watchNotice`() =
        runTest {
            viewModel.addScore(1)
            advanceUntilIdle()
            consegnaUnArretratoConUnPunto()
            advanceUntilIdle()
            assertEquals(WatchNotice.Rejected, viewModel.watchNotice.value)

            val avviaUnaPartitaNuova = MainViewModel::class.java.getDeclaredMethod("startNewMatch")
            avviaUnaPartitaNuova.isAccessible = true
            avviaUnaPartitaNuova.invoke(viewModel)
            advanceUntilIdle()

            assertEquals(null, viewModel.watchNotice.value)
        }

    /**
     * Il riepilogo di 3 secondi si dice una volta sola: lo stato Applied resta (una nuova Activity
     * lo rilegge) ma il messaggio nella striscia non deve ripartire a ogni ricreazione.
     */
    @Test
    fun `il riepilogo dei punti dall'orologio si prende una volta sola`() =
        runTest {
            consegnaUnArretratoConUnPunto()
            advanceUntilIdle()

            assertEquals(WatchNotice.Applied(1), viewModel.watchNotice.value)
            assertEquals(1, viewModel.takeWatchSummary())
            assertEquals("la seconda volta e' gia' stato detto", null, viewModel.takeWatchSummary())
            assertEquals("ma la notizia resta", WatchNotice.Applied(1), viewModel.watchNotice.value)
        }

    /** Un arretrato con la sola voce data, consegnato con il numero d'ordine dato. */
    private fun consegna(
        voce: String,
        seq: Long,
    ) {
        val connessione = campo("connectionManager") as OptimizedWearDataSync
        kotlinx.coroutines.runBlocking { whenever(connessione.sendMessage(any(), any())).thenReturn(true) }
        ricevi(
            Intent(SimplifiedDataLayerListenerService.ACTION_INTENT_BATCH)
                .putExtra(WearConstants.KEY_INTENT_BATCH, voce)
                .putExtra(WearConstants.KEY_SEQ, seq),
        )
    }

    /** Un punto, poi il rifiuto, poi ANNULLA fino al registro vuoto: Rejected resta, e lo sport si puo' cambiare. */
    private fun rifiutaESvuotaIlRegistro() {
        viewModel.addScore(1)
        consegnaUnArretratoConUnPunto()
        assertEquals(WatchNotice.Rejected, viewModel.watchNotice.value)
        assertEquals("ANNULLA toglie il punto", true, viewModel.undoLastGoal())
    }

    /**
     * Rilievo 2 della revisione: applySport crea un motore nuovo senza passare da startNewMatch, e un
     * Rejected, possibile dopo ANNULLA fino a registro vuoto, sopravviveva al cambio di sport.
     */
    @Test
    fun `il cambio di sport azzera watchNotice`() =
        runTest {
            rifiutaESvuotaIlRegistro()
            advanceUntilIdle()

            assertEquals("a registro vuoto il cambio si puo' fare", true, viewModel.selectSport(SportRegistry.PADEL))
            advanceUntilIdle()

            assertEquals(null, viewModel.watchNotice.value)
        }

    /**
     * Rilievo 3: dopo un Rejected, un arretrato accettato ma senza punti (qui una correzione) viene
     * applicato e confermato, e lasciava Rejected: la card diceva che i punti non erano entrati
     * quando l'arretrato era entrato.
     */
    @Test
    fun `un arretrato senza punti dopo un rifiuto esce dallo stato Rejected`() =
        runTest {
            rifiutaESvuotaIlRegistro()
            advanceUntilIdle()

            consegna(voceDiArretrato(WearConstants.INTENT_CORRECTION, 1, 2000), 5L)
            advanceUntilIdle()

            assertEquals("l'arretrato e' entrato: non e' piu' rifiutato", null, viewModel.watchNotice.value)
        }

    /** Fine partita e scarto sono due strade alla partita nuova: entrambe spengono la notizia. */
    @Test
    fun `endMatch e discardMatch spengono watchNotice`() =
        runTest {
            viewModel.selectSport(SportRegistry.PADEL)
            advanceUntilIdle()
            repeat(3) { viewModel.addScore(1) }
            consegna(voceDiArretrato(WearConstants.INTENT_POINT, 1, 1000), 4L)
            assertEquals(WatchNotice.Rejected, viewModel.watchNotice.value)

            assertEquals(true, viewModel.endMatch())
            advanceUntilIdle()
            assertEquals("endMatch", null, viewModel.watchNotice.value)

            repeat(3) { viewModel.addScore(1) }
            consegna(voceDiArretrato(WearConstants.INTENT_POINT, 1, 1000), 5L)
            assertEquals(WatchNotice.Rejected, viewModel.watchNotice.value)

            assertEquals(true, viewModel.discardMatch())
            advanceUntilIdle()
            assertEquals("discardMatch", null, viewModel.watchNotice.value)
        }

    /** Applied poi Rejected: il secondo arretrato trova la partita che il primo ha riempito. */
    @Test
    fun `da Applied a Rejected e da Rejected ad Applied`() =
        runTest {
            val notizie = mutableListOf<WatchNotice?>()
            val osservatore = Observer<WatchNotice?> { notizie.add(it) }
            viewModel.watchNotice.observeForever(osservatore)

            consegna(voceDiArretrato(WearConstants.INTENT_POINT, 1, 1000), 4L)
            advanceUntilIdle()
            consegna(voceDiArretrato(WearConstants.INTENT_POINT, 2, 2000), 5L)
            advanceUntilIdle()
            assertEquals(
                "il primo entra, il secondo trova la partita gia' cominciata",
                listOf(null, WatchNotice.Applied(1), WatchNotice.Rejected),
                notizie,
            )
            assertEquals("Rejected non e' un riepilogo di punti", null, viewModel.takeWatchSummary())

            // ANNULLA fino a registro vuoto: un arretrato nuovo ora entra, e Rejected lascia il posto.
            while (viewModel.undoLastGoal()) advanceUntilIdle()
            consegna(voceDiArretrato(WearConstants.INTENT_POINT, 1, 3000), 6L)
            advanceUntilIdle()
            assertEquals(WatchNotice.Applied(1), viewModel.watchNotice.value)
            assertEquals("e il riepilogo torna a dirsi", 1, viewModel.takeWatchSummary())
            viewModel.watchNotice.removeObserver(osservatore)
        }

    private fun voceDiArretrato(
        intento: String,
        lato: Int,
        quando: Long,
    ) = listOf(intento, lato, quando).joinToString(WearConstants.BATCH_FIELD_SEPARATOR)

    // --- L5: arretrato con base, id e risposta sempre ---

    /** Un arretrato come lo manda un orologio aggiornato: con id, base e nodo. */
    private fun arretratoConBase(
        voci: String,
        seq: Long,
        batchId: Long,
        base: String?,
        nodo: String = "polso-1",
        uuidPartita: String? = null,
        sportId: String? = null,
    ) = Intent(SimplifiedDataLayerListenerService.ACTION_INTENT_BATCH)
        .putExtra(WearConstants.KEY_INTENT_BATCH, voci)
        .putExtra(WearConstants.KEY_SEQ, seq)
        .putExtra(WearConstants.KEY_BATCH_ID, batchId)
        .putExtra(SimplifiedDataLayerListenerService.EXTRA_NODE_ID, nodo)
        .apply {
            if (base != null) putExtra(WearConstants.KEY_BATCH_BASE, base)
            if (uuidPartita != null) putExtra(WearConstants.KEY_MATCH_UUID, uuidPartita)
            if (sportId != null) putExtra(WearConstants.KEY_SPORT_ID, sportId)
        }

    /** L5 sport nel batch: a registro vuoto il telefono passa allo sport del polso prima di applicare. */
    @Test
    fun `un arretrato di un altro sport a registro vuoto porta il telefono a quello sport`() =
        runTest {
            kotlinx.coroutines.runBlocking { whenever(mockConnectionManager.sendMessage(any(), any())).thenReturn(true) }
            assertEquals("si parte dal calcio", SportRegistry.FOOTBALL, viewModel.activeSport.value)

            ricevi(
                arretratoConBase(
                    voceDiArretrato(WearConstants.INTENT_POINT, 1, 1000),
                    4L,
                    81L,
                    "0",
                    sportId = SportRegistry.PADEL,
                ),
            )
            advanceUntilIdle()

            assertEquals("passato al padel", SportRegistry.PADEL, viewModel.activeSport.value)
            assertEquals(1, motore().log.size)
            assertEquals("il primo punto di padel e' 15, non un gol", "15", viewModel.scoreDisplay.value?.side1Primary)
            assertEquals(81L, risposte(WearConstants.MSG_BATCH_ACK).single().getLong(WearConstants.KEY_BATCH_ID))
            assertTrue(risposte(WearConstants.MSG_BATCH_NACK).isEmpty())
        }

    @Test
    fun `un arretrato di un altro sport con una partita in corso viene rifiutato`() =
        runTest {
            cominciaUnPadelConTrePunti()
            advanceUntilIdle()
            val base = improntaDelMotore()

            ricevi(
                arretratoConBase(
                    voceDiArretrato(WearConstants.INTENT_POINT, 1, 1000),
                    4L,
                    82L,
                    base,
                    sportId = SportRegistry.FOOTBALL,
                ),
            )
            advanceUntilIdle()

            assertEquals("la partita di padel non e' toccata", 3, motore().log.size)
            assertEquals(SportRegistry.PADEL, viewModel.activeSport.value)
            val nack = risposte(WearConstants.MSG_BATCH_NACK).single()
            assertEquals(WearConstants.NACK_REJECTED, nack.getString(WearConstants.KEY_BATCH_NACK_REASON))
            assertTrue(risposte(WearConstants.MSG_BATCH_ACK).isEmpty())
        }

    @Test
    fun `un arretrato dello stesso sport a partita cominciata entra come prima`() =
        runTest {
            cominciaUnPadelConTrePunti()
            advanceUntilIdle()
            val base = improntaDelMotore()

            ricevi(
                arretratoConBase(
                    voceDiArretrato(WearConstants.INTENT_POINT, 2, 1000),
                    4L,
                    83L,
                    base,
                    sportId = SportRegistry.PADEL,
                ),
            )
            advanceUntilIdle()

            assertEquals(4, motore().log.size)
            assertEquals(83L, risposte(WearConstants.MSG_BATCH_ACK).single().getLong(WearConstants.KEY_BATCH_ID))
        }

    /**
     * L5, sport nel batch (media): il cambio sport passava da sendStateV2, che catturava il registro
     * vuoto in modo sincrono ma leggeva l'ultimo id applicato dentro la coroutine, dopo
     * registraBatchApplicato. Usciva uno stato col registro vuoto e l'id del blocco: il polso lo prende
     * come base delle voci rimaste e lo salva su disco, senza Activity.
     */
    @Test
    fun `durante un batch con cambio sport nessuno stato porta l'id del blocco col registro vuoto`() =
        runTest {
            kotlinx.coroutines.runBlocking { whenever(mockConnectionManager.sendMessage(any(), any())).thenReturn(true) }
            advanceUntilIdle()

            ricevi(
                arretratoConBase(
                    voceDiArretrato(WearConstants.INTENT_POINT, 1, 1000),
                    4L,
                    95L,
                    "0",
                    sportId = SportRegistry.PADEL,
                ),
            )
            advanceUntilIdle()

            val stati = statiV2().filter { it[WearConstants.KEY_SPORT_ID] == SportRegistry.PADEL }
            assertTrue("almeno lo stato del cambio e quello dopo il blocco", stati.size >= 2)
            stati.forEach { stato ->
                val vuoto = MatchLogCodec.decode(registroDelloStato(stato)).orEmpty().isEmpty()
                val conIdDelBlocco = stato[WearConstants.KEY_LAST_BATCH_ID] == 95L
                assertTrue("uno stato col registro vuoto non porta l'id del blocco", !(vuoto && conIdDelBlocco))
            }
            assertEquals("l'ultimo stato e' quello dopo il blocco", 95L, stati.last()[WearConstants.KEY_LAST_BATCH_ID])
            assertEquals(1, MatchLogCodec.decode(registroDelloStato(stati.last()))?.size)
        }

    /** L5, sport nel batch (media): un blocco senza voci applicabili non cambia lo sport, risponde NACK. */
    @Test
    fun `un arretrato inapplicabile di un altro sport riceve un NACK e lo sport resta com'e'`() =
        runTest {
            kotlinx.coroutines.runBlocking { whenever(mockConnectionManager.sendMessage(any(), any())).thenReturn(true) }
            advanceUntilIdle()

            ricevi(arretratoConBase("illeggibile,x,y", 4L, 96L, "0", sportId = SportRegistry.PADEL))
            advanceUntilIdle()

            assertEquals("lo sport e' quello di prima", SportRegistry.FOOTBALL, viewModel.activeSport.value)
            verify(mockMatchSettingsRepository, never()).setActiveSport(any())
            assertTrue("nessuno stato col padel", statiV2().none { it[WearConstants.KEY_SPORT_ID] == SportRegistry.PADEL })
            assertEquals(
                WearConstants.NACK_REJECTED,
                risposte(WearConstants.MSG_BATCH_NACK).single().getString(WearConstants.KEY_BATCH_NACK_REASON),
            )
            assertTrue(risposte(WearConstants.MSG_BATCH_ACK).isEmpty())
        }

    @Test
    fun `un arretrato senza sport (orologio non aggiornato) non cambia lo sport del telefono`() =
        runTest {
            kotlinx.coroutines.runBlocking { whenever(mockConnectionManager.sendMessage(any(), any())).thenReturn(true) }

            ricevi(arretratoConBase(voceDiArretrato(WearConstants.INTENT_POINT, 1, 1000), 4L, 84L, "0"))
            advanceUntilIdle()

            assertEquals(SportRegistry.FOOTBALL, viewModel.activeSport.value)
            assertEquals(1, motore().log.size)
            assertEquals(84L, risposte(WearConstants.MSG_BATCH_ACK).single().getLong(WearConstants.KEY_BATCH_ID))
        }

    /** Cio' che il telefono ha risposto sul path dato, un DataMap per messaggio, nell'ordine. */
    private fun risposte(path: String): List<com.google.android.gms.wearable.DataMap> =
        mockingDetails(mockConnectionManager)
            .invocations
            .filter { it.method.name == "sendMessage" && it.arguments[0] == path }
            .map {
                com.google.android.gms.wearable.DataMap
                    .fromByteArray(it.arguments[1] as ByteArray)
            }

    private fun improntaDelMotore() = MatchLogCodec.impronta(MatchLogCodec.encode(motore().log))

    private fun cominciaUnPadelConTrePunti() {
        kotlinx.coroutines.runBlocking { whenever(mockConnectionManager.sendMessage(any(), any())).thenReturn(true) }
        assertEquals(true, viewModel.selectSport(SportRegistry.PADEL))
        repeat(3) { viewModel.addScore(1) }
    }

    /** Rilievo 1: la partita cominciata col telefono e' il caso per cui il calcolo offline esiste. */
    @Test
    fun `un arretrato calcolato sul registro del telefono entra anche a partita cominciata`() =
        runTest {
            cominciaUnPadelConTrePunti()
            advanceUntilIdle()
            val base = improntaDelMotore()

            ricevi(arretratoConBase(voceDiArretrato(WearConstants.INTENT_POINT, 2, 1000), 4L, 77L, base))
            advanceUntilIdle()

            assertEquals("tre del telefono e uno del polso", 4, motore().log.size)
            assertEquals(WatchNotice.Applied(1), viewModel.watchNotice.value)
            assertEquals("e l'orologio lo sa", 77L, risposte(WearConstants.MSG_BATCH_ACK).single().getLong(WearConstants.KEY_BATCH_ID))
            assertTrue(risposte(WearConstants.MSG_BATCH_NACK).isEmpty())
        }

    @Test
    fun `un arretrato calcolato su un'altra partita viene rifiutato con un NACK definitivo`() =
        runTest {
            cominciaUnPadelConTrePunti()
            advanceUntilIdle()

            // La base e' quella di un'altra partita (tre punti del lato 2): il telefono ne ha tre del lato 1.
            val altra = MatchLogCodec.impronta(List(3) { LoggedEvent(ScoringEvent.Point(side = 2)) })
            ricevi(arretratoConBase(voceDiArretrato(WearConstants.INTENT_POINT, 2, 1000), 4L, 78L, altra))
            advanceUntilIdle()

            assertEquals("non si fonde", 3, motore().log.size)
            assertEquals(WatchNotice.Rejected, viewModel.watchNotice.value)
            val nack = risposte(WearConstants.MSG_BATCH_NACK).single()
            assertEquals(WearConstants.NACK_REJECTED, nack.getString(WearConstants.KEY_BATCH_NACK_REASON))
            assertEquals(78L, nack.getLong(WearConstants.KEY_BATCH_ID))
            assertTrue("e nessun ack", risposte(WearConstants.MSG_BATCH_ACK).isEmpty())
        }

    /** D1: i tocchi dal vivo arrivati prima dell'arretrato non lo fanno piu' rifiutare. */
    @Test
    fun `un tocco dal vivo applicato prima dell'arretrato non lo fa rifiutare, entra dopo`() =
        runTest {
            cominciaUnPadelConTrePunti()
            advanceUntilIdle()
            val base = improntaDelMotore()
            // Il polso ha calcolato sui tre punti; intanto un suo tocco dal vivo e' arrivato per primo.
            ricevi(puntoDallOrologio(2))
            advanceUntilIdle()
            assertEquals(4, motore().log.size)

            ricevi(arretratoConBase(voceDiArretrato(WearConstants.INTENT_POINT, 2, 1000), 5L, 83L, base))
            advanceUntilIdle()

            assertEquals("dopo il tocco dal vivo", 5, motore().log.size)
            assertEquals(WatchNotice.Applied(1), viewModel.watchNotice.value)
            assertEquals(83L, risposte(WearConstants.MSG_BATCH_ACK).single().getLong(WearConstants.KEY_BATCH_ID))
            assertTrue("nessun rifiuto", risposte(WearConstants.MSG_BATCH_NACK).isEmpty())
        }

    /** D1: una partita con un'altra identita' e' un rifiuto definitivo, anche se il prefisso coincide. */
    @Test
    fun `un arretrato di un'altra partita viene rifiutato anche se il prefisso del registro coincide`() =
        runTest {
            cominciaUnPadelConTrePunti()
            advanceUntilIdle()
            imposta("matchUuid", "partita-A")
            val base = improntaDelMotore()

            ricevi(arretratoConBase(voceDiArretrato(WearConstants.INTENT_POINT, 2, 1000), 4L, 84L, base, uuidPartita = "partita-B"))
            advanceUntilIdle()

            assertEquals(3, motore().log.size)
            assertEquals(
                WearConstants.NACK_REJECTED,
                risposte(WearConstants.MSG_BATCH_NACK).single().getString(WearConstants.KEY_BATCH_NACK_REASON),
            )
            // La stessa partita, con la stessa base, entra.
            ricevi(arretratoConBase(voceDiArretrato(WearConstants.INTENT_POINT, 2, 1000), 5L, 85L, base, uuidPartita = "partita-A"))
            advanceUntilIdle()
            assertEquals(4, motore().log.size)
        }

    /** D1: un registro riscritto (un ANNULLA sul telefono) non ha piu' come prefisso la base del polso. */
    @Test
    fun `un arretrato calcolato su un registro che il telefono ha riscritto viene rifiutato`() =
        runTest {
            cominciaUnPadelConTrePunti()
            advanceUntilIdle()
            val base = improntaDelMotore()
            viewModel.undoLastGoal()
            advanceUntilIdle()
            assertEquals(2, motore().log.size)

            ricevi(arretratoConBase(voceDiArretrato(WearConstants.INTENT_POINT, 2, 1000), 4L, 86L, base))
            advanceUntilIdle()

            assertEquals(2, motore().log.size)
            assertEquals(1, risposte(WearConstants.MSG_BATCH_NACK).size)
        }

    /** D1: il telefono manda l'identita' della partita nello stato v2 (vuota finche' non ne ha una). */
    @Test
    fun `lo stato v2 porta l'identita' della partita`() =
        runTest {
            cominciaUnPadelConTrePunti()
            advanceUntilIdle()
            imposta("matchUuid", "partita-C")

            viewModel.addScore(1)
            advanceUntilIdle()

            val ultimoStato =
                mockingDetails(mockConnectionManager)
                    .invocations
                    .filter { it.method.name == "sendData" && it.arguments[0] == WearConstants.PATH_STATE_V2 }
                    .last()
                    .arguments[1] as Map<*, *>
            assertEquals("partita-C", ultimoStato[WearConstants.KEY_MATCH_UUID])
        }

    /** Gli stati v2 che il telefono ha spedito, nell'ordine. */
    private fun statiV2(): List<Map<*, *>> =
        mockingDetails(mockConnectionManager)
            .invocations
            .filter { it.method.name == "sendData" && it.arguments[0] == WearConstants.PATH_STATE_V2 }
            .map { it.arguments[1] as Map<*, *> }

    private fun registroDelloStato(stato: Map<*, *>) = stato[WearConstants.KEY_EVENT_LOG] as String

    /** L5, identita': il primo stato di una partita che ha un evento porta gia' il suo uuid, lo stesso della riga. */
    @Test
    fun `lo stato del primo punto porta gia' l'identita' della partita, la stessa della riga`() =
        runTest {
            viewModel.addScore(1)
            advanceUntilIdle()

            val primoConPunto = statiV2().first { MatchLogCodec.decode(registroDelloStato(it))?.isNotEmpty() == true }
            val uuid = primoConPunto[WearConstants.KEY_MATCH_UUID] as String
            assertTrue("l'uuid non e' vuoto col primo punto", uuid.isNotEmpty())
            val riga =
                mockingDetails(campo("matchDao") as MatchDao)
                    .invocations
                    .filter { it.method.name == "insertLiveMatch" }
                    .map { it.arguments[0] as Match }
                    .single()
            assertEquals("la riga porta lo stesso id", uuid, riga.matchUuid)
        }

    /** Prima del primo punto non c'e' una partita da identificare: l'uuid resta vuoto. */
    @Test
    fun `lo stato di una partita senza eventi non ha ancora un'identita'`() =
        runTest {
            advanceUntilIdle()
            assertTrue(statiV2().all { (it[WearConstants.KEY_MATCH_UUID] as String).isEmpty() })
        }

    /**
     * L5, identita' (media). Telefono: primo punto al lato 1. Polso offline: tre punti, con la base "1:..." e
     * l'uuid di QUELLA partita, presi dallo stato come fa il polso. Sul telefono la partita si salva e se ne
     * apre una nuova che comincia allo stesso modo (primo punto al lato 1): l'impronta ignora orario e
     * marcatore e le due basi coincidono. Al ritorno l'arretrato va RIFIUTATO, non applicato in silenzio.
     */
    @Test
    fun `un arretrato di una partita salvata non entra in quella nuova che comincia allo stesso modo`() =
        runTest {
            kotlinx.coroutines.runBlocking { whenever(mockConnectionManager.sendMessage(any(), any())).thenReturn(true) }
            viewModel.addScore(1)
            advanceUntilIdle()
            val statoDelPolso = statiV2().last()
            val uuidDelPolso = statoDelPolso[WearConstants.KEY_MATCH_UUID] as String
            val base = MatchLogCodec.impronta(registroDelloStato(statoDelPolso))

            assertTrue("la partita si salva", viewModel.endMatch())
            advanceUntilIdle()
            viewModel.addScore(1)
            advanceUntilIdle()
            assertEquals("la partita nuova ha la stessa base", base, improntaDelMotore())

            val tre = List(3) { voceDiArretrato(WearConstants.INTENT_POINT, 1, 1000L + it) }.joinToString(WearConstants.BATCH_SEPARATOR)
            ricevi(arretratoConBase(tre, 4L, 90L, base, uuidPartita = uuidDelPolso))
            advanceUntilIdle()

            assertEquals("nessun punto del polso nella partita nuova", 1, motore().log.size)
            assertEquals(WatchNotice.Rejected, viewModel.watchNotice.value)
            val nack = risposte(WearConstants.MSG_BATCH_NACK).single()
            assertEquals(WearConstants.NACK_REJECTED, nack.getString(WearConstants.KEY_BATCH_NACK_REASON))
            assertTrue("e nessun ack", risposte(WearConstants.MSG_BATCH_ACK).isEmpty())
        }

    /**
     * L5, identita': una base di n > 0 eventi con uuid VUOTO (il polso non ha mai visto l'identita') su un
     * telefono con una partita identificata non si puo' dire della stessa partita: rifiuto. E' la regola
     * che resta quando l'uuid manca per un motivo che oggi non si prevede.
     */
    @Test
    fun `un arretrato con base non vuota e uuid vuoto non entra in una partita identificata`() =
        runTest {
            viewModel.addScore(1)
            advanceUntilIdle()
            val base = improntaDelMotore()

            ricevi(arretratoConBase(voceDiArretrato(WearConstants.INTENT_POINT, 1, 1000), 4L, 91L, base, uuidPartita = ""))
            advanceUntilIdle()

            assertEquals(1, motore().log.size)
            assertEquals(1, risposte(WearConstants.MSG_BATCH_NACK).size)
            assertTrue(risposte(WearConstants.MSG_BATCH_ACK).isEmpty())
        }

    /** L5, identita': il caso normale della partita avviata dal polso, base "0" e uuid vuoto, non si rifiuta. */
    @Test
    fun `un arretrato con base vuota e uuid vuoto entra su un telefono a registro vuoto`() =
        runTest {
            kotlinx.coroutines.runBlocking { whenever(mockConnectionManager.sendMessage(any(), any())).thenReturn(true) }
            advanceUntilIdle()

            ricevi(arretratoConBase(voceDiArretrato(WearConstants.INTENT_POINT, 1, 1000), 4L, 92L, "0", uuidPartita = ""))
            advanceUntilIdle()

            assertEquals(1, motore().log.size)
            assertEquals(92L, risposte(WearConstants.MSG_BATCH_ACK).single().getLong(WearConstants.KEY_BATCH_ID))
            assertTrue(risposte(WearConstants.MSG_BATCH_NACK).isEmpty())
            // E lo stato dopo il blocco porta gia' l'identita': le voci rimaste ereditano un uuid, non "".
            val dopo = statiV2().last()
            assertTrue("lo stato dopo il blocco ha un uuid", (dopo[WearConstants.KEY_MATCH_UUID] as String).isNotEmpty())
        }

    @Test
    fun `senza base un orologio non aggiornato vale ancora la regola del registro vuoto e anche li si risponde`() =
        runTest {
            cominciaUnPadelConTrePunti()
            advanceUntilIdle()

            ricevi(arretratoConBase(voceDiArretrato(WearConstants.INTENT_POINT, 2, 1000), 4L, 0L, base = null))
            advanceUntilIdle()

            assertEquals(3, motore().log.size)
            assertEquals(1, risposte(WearConstants.MSG_BATCH_NACK).size)
        }

    /** Rilievo 2: il telefono risponde SEMPRE, anche quando il blocco non ha voci applicabili. */
    @Test
    fun `un arretrato senza voci applicabili riceve un NACK invece del silenzio`() =
        runTest {
            kotlinx.coroutines.runBlocking { whenever(mockConnectionManager.sendMessage(any(), any())).thenReturn(true) }

            ricevi(arretratoConBase("illeggibile,x,y", 4L, 79L, base = "0"))
            advanceUntilIdle()

            assertEquals(0, motore().log.size)
            assertEquals(
                WearConstants.NACK_REJECTED,
                risposte(WearConstants.MSG_BATCH_NACK).single().getString(WearConstants.KEY_BATCH_NACK_REASON),
            )
        }

    /** Rilievo 3: l'ack si e' perso, il polso rinvia lo stesso blocco: si riconferma, non si riapplica. */
    @Test
    fun `lo stesso blocco rinviato non si conta due volte e si riconferma`() =
        runTest {
            kotlinx.coroutines.runBlocking { whenever(mockConnectionManager.sendMessage(any(), any())).thenReturn(true) }
            val voci = voceDiArretrato(WearConstants.INTENT_POINT, 1, 1000)

            ricevi(arretratoConBase(voci, 4L, 80L, base = "0"))
            advanceUntilIdle()
            assertEquals(1, motore().log.size)

            // Il rinvio: sequenza nuova, stesso id, e la base e' ancora quella di prima (registro vuoto).
            ricevi(arretratoConBase(voci, 5L, 80L, base = "0"))
            advanceUntilIdle()

            assertEquals("non raddoppia", 1, motore().log.size)
            assertEquals("due ack, uno per tentativo", 2, risposte(WearConstants.MSG_BATCH_ACK).size)
            assertTrue(risposte(WearConstants.MSG_BATCH_NACK).isEmpty())
        }

    @Test
    fun `l'ultimo id applicato sopravvive alla morte del processo e ogni nodo ha il suo`() =
        runTest {
            kotlinx.coroutines.runBlocking { whenever(mockConnectionManager.sendMessage(any(), any())).thenReturn(true) }
            val voci = voceDiArretrato(WearConstants.INTENT_POINT, 1, 1000)
            ricevi(arretratoConBase(voci, 4L, 81L, base = "0"))
            advanceUntilIdle()

            // Il processo muore: il ViewModel nuovo non conosce il blocco, il disco si'.
            viewModel.viewModelScope.cancel()
            viewModel = creaViewModel()
            iniettaServizio(viewModel)
            advanceUntilIdle()
            assertEquals(0, motore().log.size)
            ricevi(arretratoConBase(voci, 5L, 81L, base = "0"))
            advanceUntilIdle()

            assertEquals("gia' applicato: non rientra", 0, motore().log.size)
            // Lo stesso id da un ALTRO orologio e' un altro blocco.
            ricevi(arretratoConBase(voci, 1L, 81L, base = "0", nodo = "polso-2"))
            advanceUntilIdle()
            assertEquals(1, motore().log.size)
        }

    @Test
    fun `lo stato v2 porta l'id dell'ultimo arretrato applicato`() =
        runTest {
            kotlinx.coroutines.runBlocking { whenever(mockConnectionManager.sendMessage(any(), any())).thenReturn(true) }

            ricevi(arretratoConBase(voceDiArretrato(WearConstants.INTENT_POINT, 1, 1000), 4L, 82L, base = "0"))
            advanceUntilIdle()

            val ultimoStato =
                mockingDetails(mockConnectionManager)
                    .invocations
                    .filter { it.method.name == "sendData" && it.arguments[0] == WearConstants.PATH_STATE_V2 }
                    .last()
                    .arguments[1] as Map<*, *>
            assertEquals(82L, ultimoStato[WearConstants.KEY_LAST_BATCH_ID])
        }

    /** Rilievo 5: i tocchi arrivati quando il ViewModel non c'era li applica il ViewModel che nasce. */
    @Test
    fun `i tocchi messi da parte dal servizio si applicano alla creazione, una volta sola`() =
        runTest {
            val app = ApplicationProvider.getApplicationContext<Application>()
            val quando = System.currentTimeMillis()
            IntentiInAttesa(app).aggiungi(WearConstants.INTENT_POINT, 1, quando - 2000)
            IntentiInAttesa(app).aggiungi(WearConstants.INTENT_POINT, 2, quando - 1000)

            val nuovo = creaViewModel()
            altriViewModel.add(nuovo)
            advanceUntilIdle()
            val log =
                (
                    MainViewModel::class.java
                        .getDeclaredField("engine")
                        .also { it.isAccessible = true }
                        .get(nuovo) as MatchEngine
                ).log
            assertEquals("due punti, nell'ordine in cui sono arrivati", listOf(1, 2), log.map { (it.event as ScoringEvent.Point).side })

            // Un terzo ViewModel non li ritrova: sono stati consumati.
            val ancora = creaViewModel()
            altriViewModel.add(ancora)
            advanceUntilIdle()
            val logAncora =
                (
                    MainViewModel::class.java
                        .getDeclaredField(
                            "engine",
                        ).also { it.isAccessible = true }
                        .get(ancora) as MatchEngine
                ).log
            assertEquals(0, logAncora.size)
        }

    /**
     * Come in produzione postValue NON e' immediato: arriva in un messaggio successivo del looper
     * principale. InstantTaskExecutorRule lo esegue subito e nasconde chi scrive una cifra con
     * postValue e poi, nello stesso messaggio, con setValue: la prima, in ritardo, vince. Qui i
     * post si accodano e si svuotano a mano, con la funzione restituita.
     */
    private fun codaDelMain(): () -> Unit {
        val coda = mutableListOf<Runnable>()
        ArchTaskExecutor.getInstance().setDelegate(
            object : TaskExecutor() {
                override fun executeOnDiskIO(runnable: Runnable) = runnable.run()

                override fun postToMainThread(runnable: Runnable) {
                    coda.add(runnable)
                }

                override fun isMainThread() = true
            },
        )
        return {
            while (coda.isNotEmpty()) coda.removeAt(0).run()
        }
    }

    /**
     * Visto sugli emulatori: app chiusa, due tocchi dal polso, app riaperta. Con la lettura della
     * riga attiva sospesa (il ritardo del database vero, che con i finti non si apre mai) la riga
     * ripresa deve restare una sola con i due punti sopra, e lo schermo deve dire 3-2: il
     * ripristino pubblicava il 3-0 con postValue, che arrivava dopo il 3-2 del primo tocco.
     */
    @Test
    fun `i tocchi messi da parte vanno sulla riga ripresa e le cifre dicono 3-2`() =
        runTest {
            val db = databaseInMemoria()
            val svuota = codaDelMain()
            try {
                val dao = DaoCheSospende(db.matchDao(), cancelloInsert = CompletableDeferred(Unit), cancelloLettura = CompletableDeferred())
                db.matchDao().insert(
                    Match(team1Id = 1, team2Id = 2, team1Score = 3, team2Score = 0, timestamp = 0L, isActive = true, eventLog = "1|1,1,1"),
                )
                val app = ApplicationProvider.getApplicationContext<Application>()
                val quando = System.currentTimeMillis()
                IntentiInAttesa(app).aggiungi(WearConstants.INTENT_POINT, 2, quando - 2000)
                IntentiInAttesa(app).aggiungi(WearConstants.INTENT_POINT, 2, quando - 1000)

                viewModel.viewModelScope.cancel()
                viewModel = creaViewModel(matchDao = dao)
                iniettaServizio(viewModel)
                advanceUntilIdle()
                dao.cancelloLettura.complete(Unit)
                advanceUntilIdle()
                svuota()

                assertEquals(listOf(Riga(true, SportRegistry.FOOTBALL, 3, 2, 5)), righe(db))
                assertEquals(5, motore().log.size)
                assertEquals("3", viewModel.scoreDisplay.value?.side1Primary)
                assertEquals("2", viewModel.scoreDisplay.value?.side2Primary)
                assertEquals(3, viewModel.team1Score.value)
                assertEquals(2, viewModel.team2Score.value)
            } finally {
                ArchTaskExecutor.getInstance().setDelegate(null)
                chiudiDatabase(db)
            }
        }

    /**
     * Visto sugli emulatori: i tocchi messi da parte entravano nel registro col tempo @0 invece del
     * loro istante ("1|1@0,1@2567,1@5133,2@0,2@0"). Dopo il ripristino l'orologio della partita e'
     * spostato apposta (il tempo ad app chiusa non e' di gioco), e un tocco di PRIMA del ripristino
     * veniva letto con un tempo negativo, schiacciato a zero. Ora si calcola dall'inizio vero della
     * partita (la riga ripresa), e l'orologio riparte da li' per i tocchi dal vivo.
     */
    @Test
    fun `i tocchi messi da parte entrano nel registro col loro istante e non a zero`() =
        runTest {
            val db = databaseInMemoria()
            val svuota = codaDelMain()
            try {
                val dao = DaoCheSospende(db.matchDao(), cancelloInsert = CompletableDeferred(Unit), cancelloLettura = CompletableDeferred())
                val adesso = System.currentTimeMillis()
                // Una partita cominciata un'ora fa: tre punti, a 0, 2s e 4s dall'inizio.
                val inizio = adesso - 3_600_000L
                db.matchDao().insert(
                    Match(
                        team1Id = 1,
                        team2Id = 2,
                        team1Score = 3,
                        team2Score = 0,
                        timestamp = 0L,
                        isActive = true,
                        startedAt = inizio,
                        eventLog =
                            MatchLogCodec.encode(
                                listOf(0L, 2_000L, 4_000L).map { LoggedEvent(ScoringEvent.Point(side = 1), it) },
                            ),
                    ),
                )
                val app = ApplicationProvider.getApplicationContext<Application>()
                // Due tocchi dati a 60s e a 75s dall'inizio, mentre l'app era chiusa.
                IntentiInAttesa(app).aggiungi(WearConstants.INTENT_POINT, 2, inizio + 60_000L)
                IntentiInAttesa(app).aggiungi(WearConstants.INTENT_POINT, 2, inizio + 75_000L)

                viewModel.viewModelScope.cancel()
                viewModel = creaViewModel(matchDao = dao)
                iniettaServizio(viewModel)
                advanceUntilIdle()
                dao.cancelloLettura.complete(Unit)
                advanceUntilIdle()
                svuota()

                assertEquals(
                    "i tempi veri, non zero",
                    listOf(0L, 2_000L, 4_000L, 60_000L, 75_000L),
                    motore().log.map { it.atMillis },
                )
            } finally {
                ArchTaskExecutor.getInstance().setDelegate(null)
                chiudiDatabase(db)
            }
        }

    /**
     * L5, tempi dell'arretrato (bassa): applyWatchBatch leggeva gli istanti con matchClock.relative, che
     * dopo la ripresa dal DB e' spostato apposta. Un batch con istanti PRIMA della ripresa usciva con
     * tempi a zero, sotto l'ultimo evento del registro. Ora vale la regola dei tocchi in custodia.
     */
    @Test
    fun `un arretrato su una partita ripresa dal database entra con i suoi istanti, mai sotto l'ultimo evento`() =
        runTest {
            val db = databaseInMemoria()
            try {
                kotlinx.coroutines.runBlocking { whenever(mockConnectionManager.sendMessage(any(), any())).thenReturn(true) }
                val inizio = System.currentTimeMillis() - 3_600_000L
                db.matchDao().insert(
                    Match(
                        team1Id = 1,
                        team2Id = 2,
                        team1Score = 3,
                        team2Score = 0,
                        timestamp = 0L,
                        isActive = true,
                        startedAt = inizio,
                        eventLog =
                            MatchLogCodec.encode(
                                listOf(0L, 2_000L, 4_000L).map { LoggedEvent(ScoringEvent.Point(side = 1), it) },
                            ),
                    ),
                )
                usaDao(matchDao = db.matchDao())
                assertEquals(3, motore().log.size)
                val base = improntaDelMotore()

                // Il polso ha segnato a 60s e a 75s dall'inizio, e consegna ora.
                val voci =
                    listOf(60_000L, 75_000L)
                        .joinToString(WearConstants.BATCH_SEPARATOR) { voceDiArretrato(WearConstants.INTENT_POINT, 2, inizio + it) }
                ricevi(arretratoConBase(voci, 4L, 97L, base))
                advanceUntilIdle()

                assertEquals(
                    "i tempi veri, non zero",
                    listOf(0L, 2_000L, 4_000L, 60_000L, 75_000L),
                    motore().log.map { it.atMillis },
                )
            } finally {
                chiudiDatabase(db)
            }
        }

    /** Senza una partita da riprendere i tocchi messi da parte aprono la partita, una riga sola. */
    @Test
    fun `senza partita attiva i tocchi messi da parte aprono una riga sola`() =
        runTest {
            val db = databaseInMemoria()
            val svuota = codaDelMain()
            try {
                val dao = DaoCheSospende(db.matchDao(), cancelloInsert = CompletableDeferred(Unit), cancelloLettura = CompletableDeferred())
                val app = ApplicationProvider.getApplicationContext<Application>()
                val quando = System.currentTimeMillis()
                IntentiInAttesa(app).aggiungi(WearConstants.INTENT_POINT, 2, quando - 2000)
                IntentiInAttesa(app).aggiungi(WearConstants.INTENT_POINT, 2, quando - 1000)

                viewModel.viewModelScope.cancel()
                viewModel = creaViewModel(matchDao = dao)
                iniettaServizio(viewModel)
                advanceUntilIdle()
                dao.cancelloLettura.complete(Unit)
                advanceUntilIdle()
                svuota()

                assertEquals(listOf(Riga(true, SportRegistry.FOOTBALL, 0, 2, 2)), righe(db))
                assertEquals("0", viewModel.scoreDisplay.value?.side1Primary)
                assertEquals("2", viewModel.scoreDisplay.value?.side2Primary)
            } finally {
                ArchTaskExecutor.getInstance().setDelegate(null)
                chiudiDatabase(db)
            }
        }

    /** Il messaggio «ANNULLATO» dipende da questo esito: vero solo se e' stato tolto qualcosa. */
    @Test
    fun `undoLastGoal dice se ha tolto qualcosa`() =
        runTest {
            assertEquals("niente da annullare", false, viewModel.undoLastGoal())

            viewModel.addScore(1)
            assertEquals(true, viewModel.undoLastGoal())
            assertEquals("e adesso non c'e' piu' niente", false, viewModel.undoLastGoal())
            advanceUntilIdle()
        }

    /** La striscia scrive «ANNULLATO: PUNTO ROSSI»: serve sapere di che lato era l'evento tolto. */
    @Test
    fun `annullaUltimaAzione restituisce l'evento tolto e il suo lato`() =
        runTest {
            assertEquals("niente da annullare", null, viewModel.annullaUltimaAzione())

            viewModel.addScore(2)
            val tolto = viewModel.annullaUltimaAzione()
            assertEquals(ScoringEvent.Point(side = 2), tolto)
            assertEquals("e adesso non c'e' piu' niente", null, viewModel.annullaUltimaAzione())
            advanceUntilIdle()
        }

    @Test
    fun `undoLastGoal durante il ripristino non dice di aver tolto niente`() =
        runTest {
            val scoreObserver = Observer<Int> {}
            viewModel.team1Score.observeForever(scoreObserver)
            viewModel.addScore(1)
            advanceUntilIdle()
            imposta("ripristinoInCorso", true)

            assertEquals("il tocco e' solo rimandato", false, viewModel.undoLastGoal())
            assertEquals("e il punto e' ancora li'", 1, viewModel.team1Score.value)
            imposta("ripristinoInCorso", false)
            viewModel.team1Score.removeObserver(scoreObserver)
        }

    /**
     * Il telefono non rimanda ANNULLA: a fine ripristino toglierebbe un punto che chi tocca non ha mai
     * visto, e la striscia non ha modo di dirlo. Si scarta, e finito il ripristino non succede niente.
     */
    @Test
    fun `annullaUltimaAzione non rimandabile durante il ripristino viene scartata`() =
        runTest {
            val scoreObserver = Observer<Int> {}
            viewModel.team1Score.observeForever(scoreObserver)
            viewModel.addScore(1)
            advanceUntilIdle()
            imposta("ripristinoInCorso", true)

            assertEquals("scartato, non rimandato", null, viewModel.annullaUltimaAzione(rimandabile = false))
            // Chiude la finestra del ripristino e applica i tocchi tenuti da parte (privato: via reflection).
            MainViewModel::class.java
                .getDeclaredMethod("fineRipristino")
                .apply { isAccessible = true }
                .invoke(viewModel)

            assertEquals("a ripristino finito il punto e' ancora li'", 1, viewModel.team1Score.value)
            viewModel.team1Score.removeObserver(scoreObserver)
        }

    @Test
    fun `attributeScorer dice se ha attribuito`() =
        runTest {
            assertEquals("nessun punto a quell'indice", false, viewModel.attributeScorer(0, mario))

            viewModel.addScore(1)
            assertEquals(true, viewModel.attributeScorer(0, mario))
            assertEquals("il punto e' gia' attribuito", false, viewModel.attributeScorer(0, mario))
            advanceUntilIdle()
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

    /** Lascia arrivare onServiceConnected, che fa partire i collector del service finto. */
    private fun collegaIlService() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Passo 13: da fermo il tocco avvia dalla durata piena, senza azzerare niente prima. */
    @Test
    fun `il tocco sul portiere fermo avvia dalla durata piena`() =
        runTest {
            collegaIlService()
            advanceUntilIdle()
            viewModel.setKeeperTimer(300)

            viewModel.toccaIlPortiere()

            verify(mockMatchTimerService).restartKeeperTimer(300_000L)
            verify(mockMatchTimerService, never()).startKeeperTimer(any(), any())
            verify(mockMatchTimerService, never()).resetKeeperTimer(any())
        }

    /**
     * Passo 13: in corso il tocco riparte da capo con l'operazione unica del service, non con un
     * azzeramento e un avvio separati: due invii all'orologio sullo stesso path, e vinceva l'ultimo.
     */
    @Test
    fun `il tocco sul portiere in corso riparte da capo con una operazione sola`() =
        runTest {
            collegaIlService()
            advanceUntilIdle()
            viewModel.setKeeperTimer(300)
            portiereInCorso.value = true
            advanceUntilIdle()

            viewModel.toccaIlPortiere()

            verify(mockMatchTimerService).restartKeeperTimer(300_000L)
            verify(mockMatchTimerService, never()).resetKeeperTimer(any())
            verify(mockMatchTimerService, never()).startKeeperTimer(any(), any())
        }

    /**
     * Pausa dall'orologio con un residuo, poi il cambio di durata nelle impostazioni: la copia nel
     * ViewModel dice "fermo a durata piena" (setKeeperTimer la sovrascrive) mentre il service ha
     * ancora il residuo. Il tocco deve partire dalla durata piena lo stesso, senza dedurre dalla copia.
     */
    @Test
    fun `il tocco dopo una pausa dall'orologio e un cambio di durata riparte dalla durata piena`() =
        runTest {
            collegaIlService()
            advanceUntilIdle()
            viewModel.setKeeperTimer(300)
            portiereInCorso.value = true
            advanceUntilIdle()
            portiereValore.value = 120_000L
            portiereInCorso.value = false
            advanceUntilIdle()
            viewModel.setKeeperTimer(300)

            viewModel.toccaIlPortiere()

            verify(mockMatchTimerService).restartKeeperTimer(300_000L)
            verify(mockMatchTimerService, never()).startKeeperTimer(any(), any())
        }

    /**
     * Passo 13: lo stato SCADUTO nasce dall'evento di scadenza (non da pausa o azzeramento) e dura
     * finche' non si tocca. Il tocco avvia dalla durata piena e lo toglie.
     */
    @Test
    fun `il portiere e scaduto solo dopo l'evento e il tocco lo toglie`() =
        runTest {
            collegaIlService()
            advanceUntilIdle()
            viewModel.setKeeperTimer(300)
            assertEquals(false, viewModel.isKeeperTimerExpired.value)

            portiereInCorso.value = true
            advanceUntilIdle()
            portiereInCorso.value = false
            advanceUntilIdle()
            assertEquals("una pausa non e' una scadenza", false, viewModel.isKeeperTimerExpired.value)

            portiereScaduto.emit(Unit)
            advanceUntilIdle()
            assertEquals(true, viewModel.isKeeperTimerExpired.value)

            viewModel.toccaIlPortiere()

            verify(mockMatchTimerService).restartKeeperTimer(300_000L)
            verify(mockMatchTimerService, never()).resetKeeperTimer(any())
            assertEquals("il tocco toglie SCADUTO", false, viewModel.isKeeperTimerExpired.value)
        }

    @Test
    fun `l'azzeramento e la partita nuova tolgono lo stato scaduto`() =
        runTest {
            collegaIlService()
            advanceUntilIdle()

            portiereScaduto.emit(Unit)
            advanceUntilIdle()
            assertEquals(true, viewModel.isKeeperTimerExpired.value)
            viewModel.resetKeeperTimer(fromRemote = true)
            assertEquals("l'azzeramento (anche dall'orologio) toglie SCADUTO", false, viewModel.isKeeperTimerExpired.value)

            portiereScaduto.emit(Unit)
            advanceUntilIdle()
            assertEquals(true, viewModel.isKeeperTimerExpired.value)
            // Scartare una partita iniziata la riporta a quella nuova (startNewMatch).
            viewModel.addScore(1)
            assertEquals(true, viewModel.discardMatch())
            advanceUntilIdle()
            assertEquals("la partita nuova toglie SCADUTO", false, viewModel.isKeeperTimerExpired.value)
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
            val nuovo = creaViewModel()
            altriViewModel.add(nuovo)
            val servizio = MainViewModel::class.java.getDeclaredField("matchTimerService").apply { isAccessible = true }
            assertEquals("in init il service non e' ancora legato", null, servizio.get(nuovo))

            shadowOf(Looper.getMainLooper()).idle()
            advanceUntilIdle()

            assertEquals(mockMatchTimerService, servizio.get(nuovo))
            verify(mockMatchTimerService, never()).resetTimer(any())
            verify(mockMatchTimerService, never()).resetKeeperTimer(any())
        }

    // --- L2: riga viva (creazione, cambio sport, ripristino) ---

    /**
     * Un MatchDao vero che SOSPENDE davvero davanti a due cancelli: l'insert e la lettura della
     * riga attiva. Con i DAO finti o con gli esecutori diretti l'insert ritorna subito, e la
     * finestra in cui currentMatchId e' ancora null non si apre mai (VALIDAZIONE, nota di L12).
     */
    private class DaoCheSospende(
        private val vero: MatchDao,
        val cancelloInsert: CompletableDeferred<Unit> = CompletableDeferred(),
        // var: la lettura si richiude DOPO la costruzione del ViewModel, perche' le coroutine di init
        // la attraversano e restarebbero sospese.
        var cancelloLettura: CompletableDeferred<Unit> = CompletableDeferred(Unit),
    ) : MatchDao by vero {
        // La riga viva nasce da insertLiveMatch: delegato a [vero], chiamerebbe l'insert di
        // [vero] e non passerebbe dal cancello.
        override suspend fun insertLiveMatch(
            match: Match,
            team1PlayerIds: List<Int>,
            team2PlayerIds: List<Int>,
        ): Long {
            cancelloInsert.await()
            return vero.insertLiveMatch(match, team1PlayerIds, team2PlayerIds)
        }

        override suspend fun getActiveMatchOnce(): Match? {
            cancelloLettura.await()
            return vero.getActiveMatchOnce()
        }
    }

    private data class Riga(
        val attiva: Boolean,
        val sportId: String,
        val team1Score: Int,
        val team2Score: Int,
        val eventi: Int,
    )

    /** Tutte le righe della tabella, lette in SQL: la query della cronologia puo' filtrarle. */
    private fun righe(db: AppDatabase): List<Riga> =
        db.openHelper.readableDatabase
            .query("SELECT isActive, sportId, team1Score, team2Score, eventLog FROM matches ORDER BY matchId")
            .use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val eventi = MatchLogCodec.decode(c.getString(4))?.size ?: -1
                        add(Riga(c.getInt(0) == 1, c.getString(1), c.getInt(2), c.getInt(3), eventi))
                    }
                }
            }

    private fun rispondeAgliAck() {
        val connessione = campo("connectionManager") as OptimizedWearDataSync
        kotlinx.coroutines.runBlocking { whenever(connessione.sendMessage(any(), any())).thenReturn(true) }
    }

    /**
     * Rilievo L2 (alta): applyWatchBatch chiamava persistLiveMatch due volte (una dentro
     * publishEngineState). Entrambe leggevano currentMatchId null prima che l'insert tornasse, e
     * a ogni consegna dall'orologio nascevano due righe attive.
     */
    @Test
    fun `l'arretrato dall'orologio crea una riga viva sola`() =
        runTest {
            val db = databaseInMemoria()
            try {
                val dao = DaoCheSospende(db.matchDao())
                usaDao(matchDao = dao)
                rispondeAgliAck()

                ricevi(
                    Intent(SimplifiedDataLayerListenerService.ACTION_INTENT_BATCH)
                        .putExtra(WearConstants.KEY_INTENT_BATCH, "point,1,1000;point,2,2000")
                        .putExtra(WearConstants.KEY_SEQ, 1L),
                )
                advanceUntilIdle()
                dao.cancelloInsert.complete(Unit)
                advanceUntilIdle()

                assertEquals(listOf(Riga(true, SportRegistry.FOOTBALL, 1, 1, 2)), righe(db))
            } finally {
                chiudiDatabase(db)
            }
        }

    /** Stessa finestra con due punti ravvicinati: il secondo aggiorna la riga del primo. */
    @Test
    fun `due punti ravvicinati con l'insert sospeso fanno una riga sola, aggiornata`() =
        runTest {
            val db = databaseInMemoria()
            try {
                val dao = DaoCheSospende(db.matchDao())
                usaDao(matchDao = dao)

                viewModel.addScore(1)
                viewModel.addScore(1)
                advanceUntilIdle()
                dao.cancelloInsert.complete(Unit)
                advanceUntilIdle()

                assertEquals(listOf(Riga(true, SportRegistry.FOOTBALL, 2, 0, 2)), righe(db))
            } finally {
                chiudiDatabase(db)
            }
        }

    /**
     * END MATCH subito dopo il primo punto: endMatch legge currentMatchId dentro la sua coroutine,
     * e con l'insert ancora sospeso closeMatch ne inseriva una seconda. Restavano una partita
     * chiusa e una attiva, ripresa al riavvio.
     */
    @Test
    fun `END MATCH subito dopo il primo punto chiude la riga viva invece di inserirne un'altra`() =
        runTest {
            val db = databaseInMemoria()
            try {
                val dao = DaoCheSospende(db.matchDao())
                usaDao(matchDao = dao)

                viewModel.addScore(1)
                assertEquals(true, viewModel.endMatch())
                advanceUntilIdle()
                dao.cancelloInsert.complete(Unit)
                advanceUntilIdle()

                assertEquals(listOf(Riga(false, SportRegistry.FOOTBALL, 1, 0, 1)), righe(db))
                assertEquals(null, campo("currentMatchId"))
            } finally {
                chiudiDatabase(db)
            }
        }

    /**
     * SCARTA con l'insert sospeso: deleteById non partiva e la riga nata dopo restava orfana.
     * Prima il caso semplice, con la riga gia' scritta: l'id ora si legge dentro la fila, e
     * nessuno deve azzerarlo prima che la cancellazione lo legga.
     */
    @Test
    fun `scartare cancella la riga viva, anche con l'insert sospeso`() =
        runTest {
            val db = databaseInMemoria()
            try {
                usaDao(matchDao = db.matchDao())
                viewModel.addScore(1)
                advanceUntilIdle()
                assertEquals(1, righe(db).size)
                assertEquals(true, viewModel.discardMatch())
                advanceUntilIdle()
                assertEquals(emptyList<Riga>(), righe(db))

                val dao = DaoCheSospende(db.matchDao())
                usaDao(matchDao = dao)

                viewModel.addScore(1)
                assertEquals(true, viewModel.discardMatch())
                advanceUntilIdle()
                dao.cancelloInsert.complete(Unit)
                advanceUntilIdle()

                assertEquals(emptyList<Riga>(), righe(db))
                assertEquals(null, campo("currentMatchId"))

                // E la partita dopo ha la sua riga, non quella di prima.
                viewModel.addScore(2)
                advanceUntilIdle()
                assertEquals(listOf(Riga(true, SportRegistry.FOOTBALL, 0, 1, 1)), righe(db))
            } finally {
                chiudiDatabase(db)
            }
        }

    /**
     * Rilievo L2 (media): gol e ANNULLA lasciano la riga viva con '1|'. Il cambio sport
     * dall'orologio dimenticava la riga senza cancellarla: restava attiva e orfana, e la partita
     * di padel ne apriva una seconda.
     */
    @Test
    fun `il cambio sport a registro vuoto cancella la riga viva senza eventi`() =
        runTest {
            val db = databaseInMemoria()
            try {
                usaDao(matchDao = db.matchDao())
                viewModel.addScore(1)
                viewModel.undoLastGoal()
                advanceUntilIdle()
                assertEquals(listOf(Riga(true, SportRegistry.FOOTBALL, 0, 0, 0)), righe(db))

                ricevi(
                    Intent(SimplifiedDataLayerListenerService.ACTION_SPORT_INTENT)
                        .putExtra(WearConstants.KEY_SPORT_ID, SportRegistry.PADEL),
                )
                advanceUntilIdle()
                assertEquals(SportRegistry.PADEL, viewModel.activeSport.value)
                assertEquals(emptyList<Riga>(), righe(db))

                viewModel.addScore(1)
                advanceUntilIdle()
                assertEquals(listOf(Riga(true, SportRegistry.PADEL, 0, 0, 1)), righe(db))
            } finally {
                chiudiDatabase(db)
            }
        }

    /**
     * Rilievo L2 (media): il ripristino non guardava lo sport della riga. Un padel ripreso col
     * calcio in vigore veniva ripiegato con le regole del calcio (due punti = 2-0 invece di
     * 30-0), e l'ordine di servizio salvato non tornava.
     */
    @Test
    fun `alla ripresa valgono lo sport e l'ordine di servizio della riga`() =
        runTest {
            partitaSalvata("1|1,1", sportId = SportRegistry.PADEL, serveOrder = "1,2,3,4")
            advanceUntilIdle()

            assertEquals(SportRegistry.PADEL, viewModel.activeSport.value)
            assertEquals(SportRegistry.PADEL, motore().rules.id)
            assertEquals(listOf(1, 2, 3, 4), motore().rules.config.serveOrder)
            assertEquals("30", viewModel.scoreDisplay.value?.side1Primary)
            verify(mockMatchSettingsRepository).setActiveSport(SportRegistry.PADEL)
        }

    /** Il ViewModel di [viewModel] rifatto da capo, con i suoi finti, e queste impostazioni. */
    private fun nuovoViewModel(sportSalvato: String): MainViewModel {
        whenever(mockMatchSettingsRepository.getSettingsFlow()).thenReturn(
            flowOf(MatchSettings("Team 1", "Team 2", Color.RED, Color.BLUE, 300L, sportSalvato)),
        )
        // Gli stessi DAO e lo stesso connection manager di [viewModel], dal costruttore: le
        // coroutine di init li usano fin dal primo giro.
        val nuovo =
            creaViewModel(
                playerDao = campo("playerDao") as PlayerDao,
                matchDao = campo("matchDao") as MatchDao,
                connectionManager = campo("connectionManager") as OptimizedWearDataSync,
            )
        altriViewModel.add(nuovo)
        return nuovo
    }

    /**
     * Il collettore delle impostazioni chiama applySport quando lo sport salvato non e' il calcio,
     * e ora applySport cancella la riga viva. Se la sua prima emissione cade durante il
     * ripristino, non deve cancellare la partita che sta tornando ne' rimetterle sopra lo sport
     * vecchio: vince la riga, e le impostazioni si allineano.
     */
    @Test
    fun `lo sport delle impostazioni non cancella ne' cambia la partita che si sta ripristinando`() =
        runTest {
            val matchDao = campo("matchDao") as MatchDao
            val calcio =
                Match(
                    matchId = 5,
                    team1Id = 1,
                    team2Id = 2,
                    team1Score = 2,
                    team2Score = 0,
                    timestamp = 0L,
                    isActive = true,
                    eventLog = "1|1,1",
                )
            whenever(matchDao.getActiveMatchOnce()).thenReturn(calcio)

            val nuovo = nuovoViewModel(sportSalvato = SportRegistry.PADEL)
            advanceUntilIdle()

            assertEquals(SportRegistry.FOOTBALL, nuovo.activeSport.value)
            assertEquals(2, nuovo.team1Score.value)
            verify(matchDao, never()).deleteById(any())
            verify(matchDao, never()).deleteLiveMatch(any())
            verify(mockMatchSettingsRepository).setActiveSport(SportRegistry.FOOTBALL)
        }

    /** Senza una partita da ripristinare lo sport salvato si applica, come prima. */
    @Test
    fun `senza partita da ripristinare lo sport delle impostazioni si applica`() =
        runTest {
            val nuovo = nuovoViewModel(sportSalvato = SportRegistry.PADEL)
            advanceUntilIdle()

            assertEquals(SportRegistry.PADEL, nuovo.activeSport.value)
        }

    /**
     * Rilievo L2 (bassa): un punto dall'orologio arrivato mentre il ripristino aspetta il
     * database veniva applicato al motore vuoto. Creava una seconda riga attiva, e poi restoreLog
     * lo cancellava dal tabellone. Ora aspetta la fine del ripristino e si somma alla partita.
     */
    @Test
    fun `un punto dall'orologio arrivato durante il ripristino si applica dopo, sulla partita ripresa`() =
        runTest {
            val db = databaseInMemoria()
            try {
                val dao = DaoCheSospende(db.matchDao(), cancelloInsert = CompletableDeferred(Unit))
                usaDao(matchDao = dao)
                dao.cancelloLettura = CompletableDeferred()
                db.matchDao().insert(
                    Match(team1Id = 1, team2Id = 2, team1Score = 2, team2Score = 0, timestamp = 0L, isActive = true, eventLog = "1|1,1"),
                )
                val ripristino = MainViewModel::class.java.getDeclaredMethod("restoreActiveMatchIfAny")
                ripristino.isAccessible = true
                ripristino.invoke(viewModel)
                advanceUntilIdle()

                ricevi(puntoDallOrologio(2))
                advanceUntilIdle()
                dao.cancelloLettura.complete(Unit)
                advanceUntilIdle()

                assertEquals(2, viewModel.team1Score.value)
                assertEquals(1, viewModel.team2Score.value)
                assertEquals(3, motore().log.size)
                assertEquals(listOf(Riga(true, SportRegistry.FOOTBALL, 2, 1, 3)), righe(db))
            } finally {
                chiudiDatabase(db)
            }
        }

    /**
     * Rilievo L2 (media, revisione di be20e97): il ripristino scrive currentMatchId fuori dalla
     * fila, e i tocchi del telefono non erano rimandati come quelli dell'orologio. Un +1 dato
     * mentre la lettura della riga attiva e' sospesa apriva una seconda riga; il suo insert,
     * tornato dopo il ripristino, gli rubava currentMatchId, e la partita ripresa restava attiva
     * ma non piu' seguita. Ora il tocco aspetta la fine del ripristino e si somma alla partita.
     */
    @Test
    fun `un tocco sul telefono durante il ripristino si applica dopo, sulla riga ripresa`() =
        runTest {
            val db = databaseInMemoria()
            try {
                val dao = DaoCheSospende(db.matchDao())
                usaDao(matchDao = dao)
                dao.cancelloLettura = CompletableDeferred()
                val calcio =
                    Match(
                        team1Id = 1,
                        team2Id = 2,
                        team1Score = 2,
                        team2Score = 0,
                        timestamp = 0L,
                        isActive = true,
                        eventLog = "1|1,1",
                    )
                val ripresa = db.matchDao().insert(calcio)
                val ripristino = MainViewModel::class.java.getDeclaredMethod("restoreActiveMatchIfAny")
                ripristino.isAccessible = true
                ripristino.invoke(viewModel)
                advanceUntilIdle()

                viewModel.addScore(1)
                advanceUntilIdle()
                dao.cancelloLettura.complete(Unit)
                advanceUntilIdle()
                dao.cancelloInsert.complete(Unit)
                advanceUntilIdle()

                assertEquals(listOf(Riga(true, SportRegistry.FOOTBALL, 3, 0, 3)), righe(db))
                assertEquals(ripresa, campo("currentMatchId"))
                assertEquals(3, viewModel.team1Score.value)
            } finally {
                chiudiDatabase(db)
            }
        }

    /** Le formazioni in tabella per [matchId], in SQL e nell'ordine di scrittura: (id, lato). */
    private fun formazioni(
        db: AppDatabase,
        matchId: Int,
    ): List<Pair<Int, Int>> =
        db.openHelper.readableDatabase
            .query("SELECT playerId, teamNumber FROM MatchPlayerCrossRef WHERE matchId = $matchId ORDER BY rowid")
            .use { c -> buildList { while (c.moveToNext()) add(c.getInt(0) to c.getInt(1)) } }

    /** Marco e Luca nel roster 1, Anna e Sara nel 2: servono Marco, Anna, Luca, Sara. */
    private suspend fun quattroDelPadel(playerDao: PlayerDao): List<Int> {
        val nomi = listOf("Marco", "Anna", "Luca", "Sara")
        val ids = nomi.map { playerDao.insert(Player(playerName = it, appearances = 0, goals = 0)).toInt() }
        nomi.forEachIndexed { i, nome ->
            viewModel.addPlayerToTeam(PlayerWithRoles(Player(ids[i], nome, 0, 0), emptyList()), if (i % 2 == 0) 1 else 2)
        }
        return ids
    }

    /**
     * Rilievo L2 (media): le rose vivevano solo in memoria. Dopo la morte del processo la partita
     * tornava senza giocatori: END MATCH salvava senza presenze ne' formazioni, e l'export del
     * padel diceva che ne mancavano quattro. Ora tornano dalla riga, nel loro ordine, e l'ordine
     * di servizio resta quello della riga.
     */
    @Test
    fun `le rose tornano con un ViewModel nuovo, e END MATCH salva presenze e formazioni una volta`() =
        runTest {
            val db = databaseInMemoria()
            try {
                val playerDao = db.playerDao()
                usaDao(playerDao = playerDao, matchDao = db.matchDao())
                viewModel.selectSport(SportRegistry.PADEL)
                advanceUntilIdle()
                val (marco, anna, luca, sara) = quattroDelPadel(playerDao)
                repeat(3) { viewModel.addScore(1) }
                advanceUntilIdle()
                val viva = db.matchDao().getActiveMatchOnce()!!

                val nuovo = nuovoViewModel(sportSalvato = SportRegistry.PADEL)
                advanceUntilIdle()

                assertEquals(listOf(marco, luca), nuovo.team1Players.value?.map { it.player.playerId })
                assertEquals(listOf(anna, sara), nuovo.team2Players.value?.map { it.player.playerId })
                val campoMotore = MainViewModel::class.java.getDeclaredField("engine")
                campoMotore.isAccessible = true
                val regole = (campoMotore.get(nuovo) as MatchEngine).rules
                assertEquals(listOf(marco, anna, luca, sara), regole.config.serveOrder)
                val esito = nuovo.buildExport()
                assertTrue("atteso Ready, ottenuto $esito", esito is ExportResult.Ready)
                assertEquals(listOf("Marco", "Luca", "Anna", "Sara"), (esito as ExportResult.Ready).export.players.map { it.name })

                assertEquals(true, nuovo.endMatch())
                advanceUntilIdle()

                assertEquals(
                    "una presenza a testa, non di piu'",
                    listOf(1, 1, 1, 1),
                    playerDao.getAllPlayers().first().map { it.player.appearances },
                )
                assertEquals(
                    listOf(marco to 1, luca to 1, anna to 2, sara to 2),
                    formazioni(db, viva.matchId),
                )
            } finally {
                chiudiDatabase(db)
            }
        }

    /** Cambiare la rosa a partita viva riscrive le formazioni della riga, nel nuovo ordine. */
    @Test
    fun `a partita viva aggiungere e togliere un giocatore riscrive le rose della riga`() =
        runTest {
            val db = databaseInMemoria()
            try {
                val playerDao = db.playerDao()
                usaDao(playerDao = playerDao, matchDao = db.matchDao())
                val (marco, anna, luca, sara) = quattroDelPadel(playerDao)
                viewModel.addScore(1)
                advanceUntilIdle()
                val id = db.matchDao().getActiveMatchOnce()!!.matchId
                assertEquals(
                    "le rose nascono con la riga",
                    listOf(marco to 1, luca to 1, anna to 2, sara to 2),
                    formazioni(db, id),
                )

                viewModel.removePlayerFromTeam(viewModel.team1Players.value!!.first(), 1)
                viewModel.addPlayerToTeam(PlayerWithRoles(Player(marco, "Marco", 0, 0), emptyList()), 1)
                advanceUntilIdle()

                assertEquals(listOf(luca to 1, marco to 1, anna to 2, sara to 2), formazioni(db, id))
            } finally {
                chiudiDatabase(db)
            }
        }

    /** Gli id nell'ordine delle rose di [squadra]. */
    private fun rosa(
        vm: MainViewModel,
        squadra: Int,
    ): List<Int>? = (if (squadra == 1) vm.team1Players.value else vm.team2Players.value)?.map { it.player.playerId }

    /**
     * COPPIE, passo 14: lo scambio a registro vuoto inverte l'ordine dei due, e con lui chi serve
     * per primo. Qui la riga viva esiste gia' (un punto, poi annullato a zero): le formazioni
     * si riscrivono nel nuovo ordine, e con loro l'ordine di servizio della riga. Senza la
     * riscrittura dell'ordine la riga tornava con le rose nuove e l'ordine vecchio.
     */
    @Test
    fun `lo scambio a registro vuoto cambia chi serve per primo e riscrive rose e ordine della riga`() =
        runTest {
            val db = databaseInMemoria()
            try {
                val playerDao = db.playerDao()
                usaDao(playerDao = playerDao, matchDao = db.matchDao())
                viewModel.selectSport(SportRegistry.PADEL)
                advanceUntilIdle()
                val (marco, anna, luca, sara) = quattroDelPadel(playerDao)
                viewModel.addScore(1)
                advanceUntilIdle()
                val id = db.matchDao().getActiveMatchOnce()!!.matchId
                viewModel.annullaUltimaAzione(rimandabile = false)
                advanceUntilIdle()
                assertTrue("registro di nuovo vuoto", motore().log.isEmpty())

                viewModel.swapPlayers(1)
                advanceUntilIdle()

                assertEquals(listOf(luca, marco), rosa(viewModel, 1))
                assertEquals(listOf(anna, sara), rosa(viewModel, 2))
                assertEquals(listOf(luca, anna, marco, sara), motore().rules.config.serveOrder)
                assertEquals(listOf(luca to 1, marco to 1, anna to 2, sara to 2), formazioni(db, id))
                assertEquals(listOf(luca, anna, marco, sara).joinToString(","), db.matchDao().getMatchById(id)!!.serveOrder)

                // Anche l'altra squadra, e un secondo scambio rimette le cose com'erano.
                viewModel.swapPlayers(2)
                advanceUntilIdle()
                assertEquals(listOf(luca, sara, marco, anna), motore().rules.config.serveOrder)
                viewModel.swapPlayers(1)
                viewModel.swapPlayers(2)
                advanceUntilIdle()
                assertEquals(listOf(marco, anna, luca, sara), motore().rules.config.serveOrder)
                assertEquals(listOf(marco to 1, luca to 1, anna to 2, sara to 2), formazioni(db, id))
            } finally {
                chiudiDatabase(db)
            }
        }

    /** Lo scambio prima del primo punto (nessuna riga viva): la riga nasce gia' nell'ordine nuovo. */
    @Test
    fun `lo scambio prima del primo punto resta dopo la morte del processo`() =
        runTest {
            val db = databaseInMemoria()
            try {
                val playerDao = db.playerDao()
                usaDao(playerDao = playerDao, matchDao = db.matchDao())
                viewModel.selectSport(SportRegistry.PADEL)
                advanceUntilIdle()
                val (marco, anna, luca, sara) = quattroDelPadel(playerDao)

                viewModel.swapPlayers(2)
                repeat(2) { viewModel.addScore(1) }
                advanceUntilIdle()

                val nuovo = nuovoViewModel(sportSalvato = SportRegistry.PADEL)
                advanceUntilIdle()

                assertEquals(listOf(marco, luca), rosa(nuovo, 1))
                assertEquals("l'ordine delle rose torna com'era dopo lo scambio", listOf(sara, anna), rosa(nuovo, 2))
                val campoMotore = MainViewModel::class.java.getDeclaredField("engine")
                campoMotore.isAccessible = true
                assertEquals(
                    listOf(marco, sara, luca, anna),
                    (campoMotore.get(nuovo) as MatchEngine).rules.config.serveOrder,
                )
            } finally {
                chiudiDatabase(db)
            }
        }

    /** Dal primo punto l'ordine e' quello della partita: lo scambio non fa niente, ne' in memoria ne' sulla riga. */
    @Test
    fun `lo scambio a partita iniziata non fa niente`() =
        runTest {
            val db = databaseInMemoria()
            try {
                val playerDao = db.playerDao()
                usaDao(playerDao = playerDao, matchDao = db.matchDao())
                viewModel.selectSport(SportRegistry.PADEL)
                advanceUntilIdle()
                val (marco, anna, luca, sara) = quattroDelPadel(playerDao)
                viewModel.addScore(1)
                advanceUntilIdle()
                val id = db.matchDao().getActiveMatchOnce()!!.matchId

                viewModel.swapPlayers(1)
                viewModel.swapPlayers(2)
                advanceUntilIdle()

                assertEquals(listOf(marco, luca), rosa(viewModel, 1))
                assertEquals(listOf(anna, sara), rosa(viewModel, 2))
                assertEquals(listOf(marco, anna, luca, sara), motore().rules.config.serveOrder)
                assertEquals(listOf(marco to 1, luca to 1, anna to 2, sara to 2), formazioni(db, id))
            } finally {
                chiudiDatabase(db)
            }
        }

    /** Con un solo giocatore non c'e' niente da scambiare. */
    @Test
    fun `lo scambio con meno di due giocatori non fa niente`() =
        runTest {
            val db = databaseInMemoria()
            try {
                val playerDao = db.playerDao()
                usaDao(playerDao = playerDao, matchDao = db.matchDao())
                val marco = playerDao.insert(Player(playerName = "Marco", appearances = 0, goals = 0)).toInt()
                viewModel.addPlayerToTeam(PlayerWithRoles(Player(marco, "Marco", 0, 0), emptyList()), 1)

                viewModel.swapPlayers(1)
                viewModel.swapPlayers(2)
                advanceUntilIdle()

                assertEquals(listOf(marco), rosa(viewModel, 1))
                assertEquals(emptyList<Int>(), rosa(viewModel, 2))
            } finally {
                chiudiDatabase(db)
            }
        }

    /**
     * L'export di Padel Elite con 2+2 giocatori non dice piu' che ne servono quattro, ne' prima
     * ne' dopo lo scambio: e' pronto, e porta i giocatori nell'ordine nuovo.
     */
    @Test
    fun `l'export del padel con due giocatori per lato e' pronto, anche dopo lo scambio`() =
        runTest {
            val db = databaseInMemoria()
            try {
                val playerDao = db.playerDao()
                usaDao(playerDao = playerDao, matchDao = db.matchDao())
                viewModel.selectSport(SportRegistry.PADEL)
                advanceUntilIdle()
                quattroDelPadel(playerDao)
                viewModel.swapPlayers(1)
                repeat(3) { viewModel.addScore(2) }
                advanceUntilIdle()

                val esito = viewModel.buildExport()

                assertTrue("atteso Ready, ottenuto $esito", esito is ExportResult.Ready)
                assertEquals(listOf("Luca", "Marco", "Anna", "Sara"), (esito as ExportResult.Ready).export.players.map { it.name })
            } finally {
                chiudiDatabase(db)
            }
        }

    /**
     * Il tennis e' un singolare finche' le rose non hanno due giocatori per lato: allora e' un
     * doppio, e togliendone uno torna singolare. I giocatori per lato seguono le rose.
     */
    @Test
    fun `i giocatori per lato del tennis seguono le rose`() =
        runTest {
            val db = databaseInMemoria()
            try {
                val playerDao = db.playerDao()
                usaDao(playerDao = playerDao, matchDao = db.matchDao())
                viewModel.selectSport(SportRegistry.TENNIS)
                advanceUntilIdle()
                assertEquals(1, viewModel.sportCapabilities.value?.playersPerSide)

                quattroDelPadel(playerDao)
                assertEquals(2, viewModel.sportCapabilities.value?.playersPerSide)

                viewModel.removePlayerFromTeam(viewModel.team2Players.value!!.last(), 2)
                assertEquals("con un lato a uno e' di nuovo un singolare", 1, viewModel.sportCapabilities.value?.playersPerSide)
                assertEquals(emptyList<Int>(), motore().rules.config.serveOrder)
            } finally {
                chiudiDatabase(db)
            }
        }

    /**
     * Rilievo della revisione (media): applySport rimetteva le regole base, con l'ordine di
     * servizio vuoto, mentre le rose restavano in memoria. Con 2+2 e cambio a tennis i giocatori
     * per lato restavano 1 (un singolare), e tornando al padel la card COPPIE prometteva un primo
     * servente che l'ordine non conteneva.
     */
    @Test
    fun `il cambio di sport con le rose a due per lato riallinea l'ordine di servizio`() =
        runTest {
            val db = databaseInMemoria()
            try {
                val playerDao = db.playerDao()
                usaDao(playerDao = playerDao, matchDao = db.matchDao())
                viewModel.selectSport(SportRegistry.PADEL)
                advanceUntilIdle()
                val (marco, anna, luca, sara) = quattroDelPadel(playerDao)
                val ordine = listOf(marco, anna, luca, sara)

                viewModel.selectSport(SportRegistry.TENNIS)
                advanceUntilIdle()
                assertEquals("con 2+2 il tennis e' un doppio", 2, viewModel.sportCapabilities.value?.playersPerSide)
                assertEquals(ordine, motore().rules.config.serveOrder)

                viewModel.selectSport(SportRegistry.PADEL)
                advanceUntilIdle()
                assertEquals(2, viewModel.sportCapabilities.value?.playersPerSide)
                assertEquals("il primo servente promesso dalla card c'e'", ordine, motore().rules.config.serveOrder)
            } finally {
                chiudiDatabase(db)
            }
        }

    /** Non ci sono FK in cascata: scartare la riga viva lasciava orfane le sue formazioni. */
    @Test
    fun `scartare la riga viva cancella anche le sue formazioni`() =
        runTest {
            val db = databaseInMemoria()
            try {
                val playerDao = db.playerDao()
                usaDao(playerDao = playerDao, matchDao = db.matchDao())
                quattroDelPadel(playerDao)
                viewModel.addScore(1)
                advanceUntilIdle()
                val id = db.matchDao().getActiveMatchOnce()!!.matchId
                assertEquals(4, formazioni(db, id).size)

                assertEquals(true, viewModel.discardMatch())
                advanceUntilIdle()

                assertEquals(emptyList<Riga>(), righe(db))
                assertEquals(emptyList<Pair<Int, Int>>(), formazioni(db, id))
            } finally {
                chiudiDatabase(db)
            }
        }

    /**
     * La riga viva ha le formazioni dal primo punto: le statistiche non devono contarla finche'
     * non e' chiusa. getPlayerWinCounts non guardava isActive, e un 1-0 in corso era una vittoria.
     */
    @Test
    fun `le statistiche non contano la riga viva`() =
        runTest {
            val db = databaseInMemoria()
            try {
                val playerDao = db.playerDao()
                val matchDao = db.matchDao()
                usaDao(playerDao = playerDao, matchDao = matchDao)
                val mario = playerDao.insert(Player(playerName = "Mario", appearances = 0, goals = 0)).toInt()
                viewModel.addPlayerToTeam(PlayerWithRoles(Player(mario, "Mario", 0, 0), emptyList()), 1)
                viewModel.addScore(1)
                advanceUntilIdle()
                assertEquals(1, formazioni(db, matchDao.getActiveMatchOnce()!!.matchId).size)

                assertEquals(emptyList<PlayerWinCount>(), matchDao.getPlayerWinCounts().first())
                assertEquals(0, matchDao.getFinishedMatchesCountForPlayer(mario).first())
                assertEquals(
                    0,
                    playerDao
                        .getAllPlayers()
                        .first()
                        .single()
                        .player.appearances,
                )

                assertEquals(true, viewModel.endMatch())
                advanceUntilIdle()

                assertEquals(listOf(PlayerWinCount(mario, 1)), matchDao.getPlayerWinCounts().first())
                assertEquals(1, matchDao.getFinishedMatchesCountForPlayer(mario).first())
                assertEquals(
                    1,
                    playerDao
                        .getAllPlayers()
                        .first()
                        .single()
                        .player.appearances,
                )
            } finally {
                chiudiDatabase(db)
            }
        }

    /**
     * Rilievo L2 (media, revisione di c7d7979): un giocatore aggiunto mentre il ripristino aspetta
     * il database spariva in silenzio. Il ripristino riscriveva poi le rose con quelle della riga,
     * e la riscrittura in fila non trovava ancora currentMatchId. Ora il cambio di rosa aspetta la
     * fine del ripristino, come i punti, e si applica alla rosa ripresa.
     */
    @Test
    fun `un giocatore aggiunto durante il ripristino resta nella rosa ripresa e sulla riga`() =
        runTest {
            val db = databaseInMemoria()
            try {
                val playerDao = db.playerDao()
                val dao = DaoCheSospende(db.matchDao())
                usaDao(playerDao = playerDao, matchDao = dao)
                dao.cancelloLettura = CompletableDeferred()
                val marco = playerDao.insert(Player(playerName = "Marco", appearances = 0, goals = 0)).toInt()
                val anna = playerDao.insert(Player(playerName = "Anna", appearances = 0, goals = 0)).toInt()
                val viva =
                    Match(
                        team1Id = 1,
                        team2Id = 2,
                        team1Score = 1,
                        team2Score = 0,
                        timestamp = 0L,
                        isActive = true,
                        eventLog = "1|1",
                    )
                val ripresa = db.matchDao().insert(viva).toInt()
                db.matchDao().replaceLineup(ripresa, listOf(marco), emptyList())
                val ripristino = MainViewModel::class.java.getDeclaredMethod("restoreActiveMatchIfAny")
                ripristino.isAccessible = true
                ripristino.invoke(viewModel)
                advanceUntilIdle()

                viewModel.addPlayerToTeam(PlayerWithRoles(Player(anna, "Anna", 0, 0), emptyList()), 2)
                advanceUntilIdle()
                dao.cancelloLettura.complete(Unit)
                advanceUntilIdle()

                assertEquals(listOf(marco), viewModel.team1Players.value?.map { it.player.playerId })
                assertEquals(listOf(anna), viewModel.team2Players.value?.map { it.player.playerId })
                assertEquals(listOf(marco to 1, anna to 2), formazioni(db, ripresa))
            } finally {
                chiudiDatabase(db)
            }
        }

    /**
     * Rosa cambiata a partita viva, dopo la nascita della riga: END MATCH lascia in tabella
     * esattamente la rosa finale, nel suo ordine, e le presenze salgono solo per chi c'e'.
     */
    @Test
    fun `END MATCH dopo un cambio di rosa salva solo la rosa finale`() =
        runTest {
            val db = databaseInMemoria()
            try {
                val playerDao = db.playerDao()
                usaDao(playerDao = playerDao, matchDao = db.matchDao())
                val (marco, anna, luca, sara) = quattroDelPadel(playerDao)
                val piero = playerDao.insert(Player(playerName = "Piero", appearances = 0, goals = 0)).toInt()
                viewModel.addScore(1)
                advanceUntilIdle()
                val id = db.matchDao().getActiveMatchOnce()!!.matchId

                viewModel.removePlayerFromTeam(viewModel.team1Players.value!!.first(), 1)
                viewModel.addPlayerToTeam(PlayerWithRoles(Player(piero, "Piero", 0, 0), emptyList()), 1)
                assertEquals(true, viewModel.endMatch())
                advanceUntilIdle()

                assertEquals(listOf(luca to 1, piero to 1, anna to 2, sara to 2), formazioni(db, id))
                assertEquals(
                    mapOf(marco to 0, anna to 1, luca to 1, sara to 1, piero to 1),
                    playerDao.getAllPlayers().first().associate { it.player.playerId to it.player.appearances },
                )
            } finally {
                chiudiDatabase(db)
            }
        }

    // --- registro a game del padel e del tennis (passo 15) -------------------------------------------

    private fun righeDelFoglio(): List<MatchEvent> = viewModel.registroDelFoglio.value.orEmpty()

    private fun righeDeiGame(): List<MatchEvent> = righeDelFoglio().filter { it.type == MatchEventType.GAME }

    /** Un game da zero: il punto secco a 40-40 lo chiude in quattro punti. */
    private fun giocaUnGame(lato: Int) = repeat(4) { viewModel.addScore(lato) }

    @Test
    fun `nel padel il registro del foglio ha una riga per game e nessuna per punto`() =
        runTest {
            val foglioObserver = Observer<List<MatchEvent>> {}
            viewModel.registroDelFoglio.observeForever(foglioObserver)
            assertEquals(true, viewModel.selectSport(SportRegistry.PADEL))

            viewModel.addScore(1)
            viewModel.addScore(1)
            advanceUntilIdle()
            assertEquals("un game aperto non e' una riga", 0, righeDeiGame().size)
            assertEquals("i punti non sono piu' nel foglio", 0, righeDelFoglio().count { it.type == MatchEventType.SCORE })

            viewModel.addScore(1)
            viewModel.addScore(1)
            giocaUnGame(2)
            advanceUntilIdle()

            // In testa il piu' recente.
            val game = righeDeiGame()
            assertEquals(listOf(2, 1), game.map { it.team })
            assertEquals(listOf(listOf(1, 1), listOf(1, 0)), game.map { it.game!!.gamesAfter })
            assertEquals(listOf(7, 3), game.map { it.engineIndex })
            // La striscia e il report leggono ancora una riga per punto.
            assertEquals(8, righeDiPunto().size)

            viewModel.registroDelFoglio.removeObserver(foglioObserver)
        }

    @Test
    fun `annullare il punto che chiude un game toglie la riga del game riaperto`() =
        runTest {
            val foglioObserver = Observer<List<MatchEvent>> {}
            viewModel.registroDelFoglio.observeForever(foglioObserver)
            assertEquals(true, viewModel.selectSport(SportRegistry.PADEL))
            giocaUnGame(1)
            advanceUntilIdle()
            assertEquals(1, righeDeiGame().size)

            viewModel.undoLastGoal()
            advanceUntilIdle()

            assertEquals("il game e' riaperto: la riga non c'e' piu'", 0, righeDeiGame().size)
            assertEquals("40", viewModel.scoreDisplay.value?.side1Primary)

            // Rigiocando il punto la riga torna, e con lo stesso contenuto.
            viewModel.addScore(1)
            advanceUntilIdle()
            assertEquals(listOf(listOf(1, 0)), righeDeiGame().map { it.game!!.gamesAfter })

            viewModel.registroDelFoglio.removeObserver(foglioObserver)
        }

    @Test
    fun `le righe informative restano al loro posto fra le righe dei game`() =
        runTest {
            val foglioObserver = Observer<List<MatchEvent>> {}
            viewModel.registroDelFoglio.observeForever(foglioObserver)
            assertEquals(true, viewModel.selectSport(SportRegistry.PADEL))
            giocaUnGame(1)
            viewModel.addScore(2)
            viewModel.undoLastGoal()
            giocaUnGame(2)
            advanceUntilIdle()

            val tipi = righeDelFoglio().map { it.type }
            // Dal piu' recente: game 2, ANNULLATO (dopo il game 1 e prima del game 2), game 1, "partita pronta".
            assertEquals(listOf(MatchEventType.GAME, MatchEventType.INFO, MatchEventType.GAME), tipi.take(3))
            assertTrue(righeDelFoglio()[1].event.startsWith("Undo"))

            viewModel.registroDelFoglio.removeObserver(foglioObserver)
        }

    @Test
    fun `nel calcio il registro del foglio e' quello dei punti, com'era`() =
        runTest {
            val foglioObserver = Observer<List<MatchEvent>> {}
            val eventiObserver = Observer<List<MatchEvent>> {}
            viewModel.registroDelFoglio.observeForever(foglioObserver)
            viewModel.matchEvents.observeForever(eventiObserver)

            viewModel.addScore(1)
            viewModel.addScore(2)
            advanceUntilIdle()

            assertEquals(viewModel.matchEvents.value, righeDelFoglio())
            assertEquals(2, righeDiPunto().size)
            assertEquals(0, righeDeiGame().size)

            viewModel.registroDelFoglio.removeObserver(foglioObserver)
            viewModel.matchEvents.removeObserver(eventiObserver)
        }

    @Test
    fun `dopo il ripristino le righe dei game sono quelle del percorso dal vivo`() =
        runTest {
            val foglioObserver = Observer<List<MatchEvent>> {}
            viewModel.registroDelFoglio.observeForever(foglioObserver)
            assertEquals(true, viewModel.selectSport(SportRegistry.PADEL))
            giocaUnGame(1)
            giocaUnGame(2)
            giocaUnGame(1)
            viewModel.addScore(2)
            advanceUntilIdle()
            val dalVivo = righeDeiGame().map { it.game }
            assertEquals(3, dalVivo.size)

            // La stessa partita come arriva dal database: stesso registro, righe da ricostruire.
            val registro = MatchLogCodec.encode(motore().log)
            val matchDao = campo("matchDao") as MatchDao
            kotlinx.coroutines.runBlocking {
                whenever(matchDao.getActiveMatchOnce()).thenReturn(
                    Match(
                        matchId = 5,
                        team1Id = 1,
                        team2Id = 2,
                        team1Score = 0,
                        team2Score = 0,
                        timestamp = 0L,
                        isActive = true,
                        eventLog = registro,
                        sportId = SportRegistry.PADEL,
                    ),
                )
            }
            // Il ripristino parte da solo nell'init del ViewModel nuovo, come all'apertura dell'app.
            val ripreso = nuovoViewModel(SportRegistry.PADEL)
            val ripresoObserver = Observer<List<MatchEvent>> {}
            ripreso.registroDelFoglio.observeForever(ripresoObserver)
            advanceUntilIdle()

            assertEquals(
                dalVivo,
                ripreso.registroDelFoglio.value
                    .orEmpty()
                    .filter { it.type == MatchEventType.GAME }
                    .map { it.game },
            )

            ripreso.registroDelFoglio.removeObserver(ripresoObserver)
            viewModel.registroDelFoglio.removeObserver(foglioObserver)
        }

    @Test
    fun `la partita consegnata dall'orologio ha le righe dei game`() =
        runTest {
            val foglioObserver = Observer<List<MatchEvent>> {}
            viewModel.registroDelFoglio.observeForever(foglioObserver)
            val connessione = campo("connectionManager") as OptimizedWearDataSync
            kotlinx.coroutines.runBlocking { whenever(connessione.sendMessage(any(), any())).thenReturn(true) }
            assertEquals(true, viewModel.selectSport(SportRegistry.PADEL))
            advanceUntilIdle()

            // Un game al lato 1 (quattro punti) e uno al lato 2, piu' un punto del lato 2.
            ricevi(
                Intent(SimplifiedDataLayerListenerService.ACTION_INTENT_BATCH)
                    .putExtra(
                        WearConstants.KEY_INTENT_BATCH,
                        "point,1,1000;point,1,2000;point,1,3000;point,1,4000;point,2,5000;point,2,6000;point,2,7000;point,2,8000;point,2,9000",
                    ).putExtra(WearConstants.KEY_SEQ, 4L),
            )
            advanceUntilIdle()

            assertEquals(listOf(2, 1), righeDeiGame().map { it.team })
            assertEquals(listOf(7, 3), righeDeiGame().map { it.engineIndex })

            viewModel.registroDelFoglio.removeObserver(foglioObserver)
        }

    /** Il ViewModel di [viewModel] rifatto da capo e fatto ripartire dalla partita che [registro] racconta. */
    private fun ripristinaDa(registro: String): MainViewModel {
        val matchDao = campo("matchDao") as MatchDao
        kotlinx.coroutines.runBlocking {
            whenever(matchDao.getActiveMatchOnce()).thenReturn(
                Match(
                    matchId = 5,
                    team1Id = 1,
                    team2Id = 2,
                    team1Score = 0,
                    team2Score = 0,
                    timestamp = 0L,
                    isActive = true,
                    eventLog = registro,
                    sportId = SportRegistry.PADEL,
                ),
            )
        }
        // Il ripristino parte da solo nell'init del ViewModel nuovo, come all'apertura dell'app.
        return nuovoViewModel(SportRegistry.PADEL)
    }

    /**
     * Quante volte cambia il registro del foglio, e quello dei punti, mentre un ViewModel nuovo
     * ripristina [registro]: il conto parte dopo la costruzione e arriva a coroutine smaltite.
     */
    private fun TestScope.pubblicazioniDelRipristino(registro: String): Triple<Int, Int, Int> {
        val ripreso = ripristinaDa(registro)
        var cambiDelFoglio = 0
        var cambiDeiPunti = 0
        val foglioObserver = Observer<List<MatchEvent>> { cambiDelFoglio++ }
        val puntiObserver = Observer<List<MatchEvent>> { cambiDeiPunti++ }
        ripreso.registroDelFoglio.observeForever(foglioObserver)
        ripreso.matchEvents.observeForever(puntiObserver)
        // L'osservatore riceve subito il valore corrente: si conta da qui in poi.
        cambiDelFoglio = 0
        cambiDeiPunti = 0

        advanceUntilIdle()

        ripreso.registroDelFoglio.removeObserver(foglioObserver)
        ripreso.matchEvents.removeObserver(puntiObserver)
        val righeDeiGame =
            ripreso.registroDelFoglio.value
                .orEmpty()
                .count { it.type == MatchEventType.GAME }
        return Triple(cambiDelFoglio, cambiDeiPunti, righeDeiGame)
    }

    /**
     * Rilievo basso del passo 15: rebuildEventsAndUndo aggiungeva una riga alla volta e ognuna
     * ripubblicava l'intero registro (registroAGame piu' MatchNarrative.of): O(n^2) sul main thread
     * su una partita lunga. Si pubblica una volta sola, a ricostruzione finita: il numero di
     * pubblicazioni non dipende da quanti punti ha la partita.
     */
    @Test
    fun `il ripristino pubblica il registro un numero fisso di volte, non una per riga`() =
        runTest {
            assertEquals(true, viewModel.selectSport(SportRegistry.PADEL))
            giocaUnGame(1)
            advanceUntilIdle()
            val corto = pubblicazioniDelRipristino(MatchLogCodec.encode(motore().log))

            // A game alternati il set unico non si chiude: tutti i 40 punti entrano nel registro.
            giocaUnGame(2)
            repeat(8) { giocaUnGame(if (it % 2 == 0) 1 else 2) }
            advanceUntilIdle()
            assertEquals(40, motore().log.size)
            val lungo = pubblicazioniDelRipristino(MatchLogCodec.encode(motore().log))

            assertEquals("pubblicazioni del foglio", corto.first, lungo.first)
            assertEquals("pubblicazioni dei punti", corto.second, lungo.second)
            // La ricostruzione non e' saltata: il registro ripreso ha tutti i game.
            assertEquals(listOf(1, 10), listOf(corto.third, lungo.third))
        }

    @Test
    fun `rinominare una squadra rinomina le righe dei game`() =
        runTest {
            val foglioObserver = Observer<List<MatchEvent>> {}
            viewModel.registroDelFoglio.observeForever(foglioObserver)
            assertEquals(true, viewModel.selectSport(SportRegistry.PADEL))
            giocaUnGame(1)
            advanceUntilIdle()

            viewModel.setTeam1Name("Rossi")
            advanceUntilIdle()

            assertEquals("Rossi", righeDeiGame().single().player)

            viewModel.registroDelFoglio.removeObserver(foglioObserver)
        }

    // --- L4: la fine partita dal polso e' un'intenzione, e lo 0-0 v1 non svuota il motore ---

    private fun fineDalPolso() =
        Intent(SimplifiedDataLayerListenerService.ACTION_SCORE_INTENT)
            .putExtra(WearConstants.KEY_INTENT_KIND, WearConstants.INTENT_END_MATCH)

    /** Le partite scritte dal telefono con closeMatch, nell'ordine. */
    private fun partiteSalvate(): List<Match> =
        mockingDetails(campo("matchDao") as MatchDao)
            .invocations
            .filter { it.method.name == "closeMatch" }
            .map { it.arguments[0] as Match }

    /** Calcio sul 3-2 con i punti segnati al telefono: cinque eventi nel registro. */
    private fun calcio3a2() {
        val punteggio = Observer<Int> {}
        viewModel.team1Score.observeForever(punteggio)
        viewModel.team2Score.observeForever(punteggio)
        repeat(3) { viewModel.addScore(1) }
        repeat(2) { viewModel.addScore(2) }
    }

    /**
     * Rilievo L4 (alta): calcio 3-2, FINE PARTITA dal polso. L'intenzione fa eseguire endMatch al
     * telefono sul PROPRIO stato: salva 3-2 col suo registro. E una riconsegna (la stessa
     * intenzione due volte) non chiude due partite: la seconda trova la partita nuova, vuota.
     */
    @Test
    fun `la fine partita dal polso salva 3-2 col registro, una volta sola anche se arriva due volte`() =
        runTest {
            calcio3a2()
            advanceUntilIdle()
            assertEquals(5, motore().log.size)

            ricevi(fineDalPolso())
            advanceUntilIdle()
            // La riconsegna: il servizio la scarta per sequenza (ProtocolloV2DelTelefonoTest); se
            // arrivasse comunque, trova la partita nuova e vuota e endMatch dice no.
            ricevi(fineDalPolso())
            advanceUntilIdle()

            val salvate = partiteSalvate()
            assertEquals("una sola partita salvata", 1, salvate.size)
            assertEquals(3, salvate.single().team1Score)
            assertEquals(2, salvate.single().team2Score)
            assertEquals("il registro e' quello del telefono", 5, MatchLogCodec.decode(salvate.single().eventLog)?.size)
            assertEquals("partita nuova: motore vuoto", 0, motore().log.size)
        }

    /**
     * Visto sugli emulatori abbinati: dopo la chiusura il telefono mostrava ancora 3-2 a cifre
     * grandi con "nessun gol" sotto, mentre l'orologio era gia' a 0-0. Le cifre leggono
     * scoreDisplay, e la partita nuova azzerava il motore senza ripubblicarlo.
     */
    @Test
    fun `dopo la fine partita le cifre del telefono tornano a 0-0`() =
        runTest {
            calcio3a2()
            advanceUntilIdle()
            assertEquals("3", viewModel.scoreDisplay.value?.side1Primary)

            assertEquals(true, viewModel.endMatch())
            advanceUntilIdle()

            assertEquals("0", viewModel.scoreDisplay.value?.side1Primary)
            assertEquals("0", viewModel.scoreDisplay.value?.side2Primary)
        }

    /**
     * Rilievo L4: il v1 di un orologio vecchio in chiusura manda lo 0-0 e POI il MATCH_STATE. Lo
     * 0-0 a registro pieno non e' un tocco: se svuotava il motore, endMatch trovava la partita
     * vuota e il 3-2 andava perso.
     */
    @Test
    fun `uno 0-0 v1 a registro pieno non svuota il motore e la chiusura salva ancora 3-2`() =
        runTest {
            calcio3a2()
            advanceUntilIdle()

            ricevi(
                Intent(SimplifiedDataLayerListenerService.ACTION_SCORE_UPDATE)
                    .putExtra(WearConstants.KEY_TEAM1_SCORE, 0)
                    .putExtra(WearConstants.KEY_TEAM2_SCORE, 0),
            )
            advanceUntilIdle()
            assertEquals("il motore e' intatto", 5, motore().log.size)

            ricevi(
                Intent(SimplifiedDataLayerListenerService.ACTION_MATCH_STATE_UPDATE)
                    .putExtra(WearConstants.KEY_MATCH_ACTIVE, false),
            )
            advanceUntilIdle()

            val salvate = partiteSalvate()
            assertEquals(1, salvate.size)
            assertEquals(3, salvate.single().team1Score)
            assertEquals(2, salvate.single().team2Score)
        }

    @Test
    fun `un punteggio v1 a registro vuoto si applica ancora, e' l'orologio senza v2`() =
        runTest {
            val punteggio = Observer<Int> {}
            viewModel.team1Score.observeForever(punteggio)

            ricevi(
                Intent(SimplifiedDataLayerListenerService.ACTION_SCORE_UPDATE)
                    .putExtra(WearConstants.KEY_TEAM1_SCORE, 2)
                    .putExtra(WearConstants.KEY_TEAM2_SCORE, 1),
            )
            advanceUntilIdle()

            assertEquals(3, motore().log.size)
            assertEquals(2, viewModel.team1Score.value)
        }

    /**
     * Rilievo della revisione L4 (media): la guardia di endMatch si legge subito, il motore si
     * svuota dentro la fila. Due chiusure nello stesso tick (END MATCH sul telefono e end_match
     * dal polso, o end_match e MATCH_STATE=false) passavano entrambe, e la seconda inseriva una
     * partita 0-0 con le presenze contate due volte. Qui NIENTE advanceUntilIdle fra le due.
     */
    @Test
    fun `due chiusure nello stesso tick salvano una partita sola, 3-2`() =
        runTest {
            calcio3a2()
            advanceUntilIdle()

            ricevi(fineDalPolso())
            ricevi(fineDalPolso())
            advanceUntilIdle()

            val salvate = partiteSalvate()
            assertEquals("una sola partita salvata: $salvate", 1, salvate.size)
            assertEquals(3, salvate.single().team1Score)
            assertEquals(2, salvate.single().team2Score)
        }

    @Test
    fun `fine dal polso e MATCH_STATE falso nello stesso tick salvano una partita sola, 3-2`() =
        runTest {
            calcio3a2()
            advanceUntilIdle()

            ricevi(fineDalPolso())
            ricevi(
                Intent(SimplifiedDataLayerListenerService.ACTION_MATCH_STATE_UPDATE)
                    .putExtra(WearConstants.KEY_MATCH_ACTIVE, false),
            )
            advanceUntilIdle()

            val salvate = partiteSalvate()
            assertEquals("una sola partita salvata: $salvate", 1, salvate.size)
            assertEquals(3, salvate.single().team1Score)
            assertEquals(2, salvate.single().team2Score)
        }

    /** END MATCH sul telefono e fine dal polso insieme: la seconda dice si, la partita si sta salvando. */
    @Test
    fun `la seconda chiusura nello stesso tick dice si per lo snackbar e non salva due volte`() =
        runTest {
            calcio3a2()
            advanceUntilIdle()

            assertEquals(true, viewModel.endMatch())
            assertEquals("si sta gia' salvando", true, viewModel.endMatch())
            advanceUntilIdle()

            assertEquals(1, partiteSalvate().size)
            assertEquals("a chiusura finita e partita nuova, non c'e' niente da salvare", false, viewModel.endMatch())
        }

    private fun punteggioV1(
        uno: Int,
        due: Int,
    ) = Intent(SimplifiedDataLayerListenerService.ACTION_SCORE_UPDATE)
        .putExtra(WearConstants.KEY_TEAM1_SCORE, uno)
        .putExtra(WearConstants.KEY_TEAM2_SCORE, due)

    /**
     * Rilievo della revisione L4 (media): un orologio v1 che corregge 1-0 in 0-0 (col '-') mandava
     * uno 0-0 legittimo, e la guardia a registro pieno lo scartava. Un decremento v1 cambia un
     * solo lato di 1: lo 0-0 si ignora solo se la somma di testata e' maggiore di 1.
     */
    @Test
    fun `da 1-0 uno 0-0 v1 e' la correzione di un orologio vecchio e si applica`() =
        runTest {
            val punteggio = Observer<Int> {}
            viewModel.team1Score.observeForever(punteggio)
            viewModel.addScore(1)
            advanceUntilIdle()
            assertEquals(1, motore().log.size)

            ricevi(punteggioV1(0, 0))
            advanceUntilIdle()

            assertEquals(0, viewModel.team1Score.value)
            assertEquals("il registro e' stato riallineato", 0, motore().log.size)
        }

    @Test
    fun `da 3-2 uno 0-0 v1 si ignora, non puo' essere un decremento`() =
        runTest {
            calcio3a2()
            advanceUntilIdle()

            ricevi(punteggioV1(0, 0))
            advanceUntilIdle()

            assertEquals(3, viewModel.team1Score.value)
            assertEquals(5, motore().log.size)
        }

    /** Buco trovato dalla falsificazione: senza questo, "registro pieno => ignora tutto" passava ogni test. */
    @Test
    fun `un v1 non 0-0 a registro pieno si applica ancora`() =
        runTest {
            val punteggio = Observer<Int> {}
            viewModel.team1Score.observeForever(punteggio)
            viewModel.team2Score.observeForever(punteggio)
            viewModel.addScore(1)
            advanceUntilIdle()
            assertEquals(1, motore().log.size)

            ricevi(punteggioV1(2, 1))
            advanceUntilIdle()

            assertEquals(2, viewModel.team1Score.value)
            assertEquals(1, viewModel.team2Score.value)
            assertEquals(3, motore().log.size)
        }
}
