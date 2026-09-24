package it.vantaggi.scoreboardessential.wear

import android.app.Application
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import it.vantaggi.scoreboardessential.shared.PlayerData
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class WearViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var application: Application

    @Mock
    private lateinit var vibrator: Vibrator

    @Mock
    private lateinit var packageManager: android.content.pm.PackageManager

    private lateinit var viewModel: WearViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        MockitoAnnotations.openMocks(this)

        Mockito.`when`(application.getSystemService(Context.VIBRATOR_SERVICE)).thenReturn(vibrator)
        // Il ViewModel lo chiede per classe (ContextCompat), non per nome: senza questa riga il
        // suo vibratore e' null e nessun test puo' sentire che cosa dice il polso.
        Mockito.`when`(application.getSystemService(Vibrator::class.java)).thenReturn(vibrator)
        Mockito.`when`(application.packageManager).thenReturn(packageManager)
        Mockito.`when`(packageManager.hasSystemFeature(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(application.applicationContext).thenReturn(application)

        // L'Application e' un mock, quindi getSharedPreferences ritorna null e la coda dei tocchi
        // non consegnati esplode al primo uso. Si restituisce quella VERA di Robolectric: cosi' la
        // coda viene esercitata sul serio invece di essere aggirata.
        Mockito
            .`when`(application.getSharedPreferences(Mockito.anyString(), Mockito.anyInt()))
            .thenAnswer { invocazione ->
                org.robolectric.RuntimeEnvironment
                    .getApplication()
                    .getSharedPreferences(invocazione.getArgument(0), invocazione.getArgument(1))
            }

        // I client GMS sono mockati: il costruttore reale li istanzia davvero e il
        // loro GoogleApiHandler muore sul looper di Robolectric, facendo fallire il
        // primo test che tocca il ViewModel.
        viewModel =
            WearViewModel(
                application,
                OptimizedWearDataSync(
                    application,
                    Mockito.mock(DataClient::class.java),
                    Mockito.mock(MessageClient::class.java),
                    Mockito.mock(CapabilityClient::class.java),
                    Mockito.mock(NodeClient::class.java),
                ),
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setKeeperTimerState DOES NOT restart timer when update is small`() =
        runTest {
            // Arrange

            // Act
            viewModel.setKeeperTimerState(KeeperTimerState.Running(10))

            val timerField = WearViewModel::class.java.getDeclaredField("keeperCountDownTimer")
            timerField.isAccessible = true
            val timer1 = timerField.get(viewModel)

            // Act again with same value
            viewModel.setKeeperTimerState(KeeperTimerState.Running(10))
            val timer2 = timerField.get(viewModel)

            // Assert: Timer should be the SAME instance
            assertTrue("Timer should NOT be recreated on small/identical update", timer1 === timer2)

            // Act again with small difference (1 second)
            // Note: Running(9) vs Running(10) is 1 sec diff.
            // Wait, if 10 is current, and we receive 9. 10-9 = 1. 1 < 2. Should be ignored?
            // Yes, if we receive an update that is very close, we assume our local timer is fine.
            viewModel.setKeeperTimerState(KeeperTimerState.Running(9))
            val timer3 = timerField.get(viewModel)
            assertTrue("Timer should NOT be recreated on small update", timer1 === timer3)
        }

    @Test
    fun `setKeeperTimerState restarts timer when update is large`() =
        runTest {
            // Arrange
            viewModel.setKeeperTimerState(KeeperTimerState.Running(10))

            val timerField = WearViewModel::class.java.getDeclaredField("keeperCountDownTimer")
            timerField.isAccessible = true
            val timer1 = timerField.get(viewModel)

            // Act again with large difference (5 seconds)
            viewModel.setKeeperTimerState(KeeperTimerState.Running(5))
            val timer2 = timerField.get(viewModel)

            // Assert: Timer should be a NEW instance
            assertTrue("Timer SHOULD be recreated on large update", timer1 !== timer2)
        }

    @Test
    fun `incrementScore increases team score`() =
        runTest {
            // Arrange
            viewModel.updateScoresFromMobile(0, 0)

            // Act
            viewModel.incrementScore(1)
            viewModel.incrementScore(2)
            viewModel.incrementScore(2)

            // Assert
            assertTrue("Team 1 score should be 1", viewModel.team1Score.value == 1)
            assertTrue("Team 2 score should be 2", viewModel.team2Score.value == 2)
        }

    @Test
    fun `decrementScore decreases team score but not below zero`() =
        runTest {
            // Arrange
            viewModel.updateScoresFromMobile(2, 1)

            // Act
            viewModel.decrementScore(1)
            viewModel.decrementScore(2)
            viewModel.decrementScore(2)
            viewModel.decrementScore(2) // Should stay at 0

            // Assert
            assertTrue("Team 1 score should be 1", viewModel.team1Score.value == 1)
            assertTrue("Team 2 score should be 0", viewModel.team2Score.value == 0)
        }

    @Test
    fun `incrementScore with invalid team ID does not change scores`() =
        runTest {
            // Arrange
            viewModel.updateScoresFromMobile(0, 0)

            // Act
            viewModel.incrementScore(3) // Invalid team ID

            // Assert
            assertTrue("Team 1 score should remain 0", viewModel.team1Score.value == 0)
            assertTrue("Team 2 score should remain 0", viewModel.team2Score.value == 0)
        }

    @Test
    fun `syncMatchTimer updates state and controls internal timer`() =
        runTest {
            // Arrange
            val timeMillis = 60000L // 1 minute
            val expectedTime = "01:00"

            // Act: Start Timer
            viewModel.syncMatchTimer(timeMillis, true)

            // Assert
            assertTrue("Timer value should be updated", viewModel.matchTimer.value == expectedTime)

            // Access private field to check if job is active
            val timerJobField = WearViewModel::class.java.getDeclaredField("matchTimerJob")
            timerJobField.isAccessible = true
            val job = timerJobField.get(viewModel) as? kotlinx.coroutines.Job
            assertTrue("Timer job should be active", job?.isActive == true)

            // Act: Stop Timer
            viewModel.syncMatchTimer(timeMillis, false)

            // Assert
            val jobStopped = timerJobField.get(viewModel) as? kotlinx.coroutines.Job
            assertTrue("Timer job should be cancelled", jobStopped?.isActive == false || jobStopped?.isCancelled == true)
        }

    @Test
    fun `resetMatchTimer resets timer state`() =
        runTest {
            // Arrange
            viewModel.syncMatchTimer(60000L, true) // 01:00, running

            // Act
            viewModel.resetMatchTimer()

            // Assert
            assertEquals("00:00", viewModel.matchTimer.value)

            val matchTimeField = WearViewModel::class.java.getDeclaredField("matchTimeInSeconds")
            matchTimeField.isAccessible = true
            assertEquals(0L, matchTimeField.get(viewModel))

            val isRunningField = WearViewModel::class.java.getDeclaredField("isMatchTimerRunning")
            isRunningField.isAccessible = true
            assertEquals(false, isRunningField.get(viewModel))

            val timerJobField = WearViewModel::class.java.getDeclaredField("matchTimerJob")
            timerJobField.isAccessible = true
            val job = timerJobField.get(viewModel) as? kotlinx.coroutines.Job
            assertTrue("Timer job should be cancelled", job?.isActive != true)
        }

    @Test
    fun `resetMatch resets scores and timers`() =
        runTest {
            // Arrange
            viewModel.updateScoresFromMobile(2, 3)
            viewModel.syncMatchTimer(60000L, true)

            // Act
            viewModel.resetMatch(fromRemote = true)

            // Assert
            assertEquals(0, viewModel.team1Score.value)
            assertEquals(0, viewModel.team2Score.value)
            assertEquals("00:00", viewModel.matchTimer.value)

            val matchTimeField = WearViewModel::class.java.getDeclaredField("matchTimeInSeconds")
            matchTimeField.isAccessible = true
            assertEquals(0L, matchTimeField.get(viewModel))

            val isRunningField = WearViewModel::class.java.getDeclaredField("isMatchTimerRunning")
            isRunningField.isAccessible = true
            assertEquals(false, isRunningField.get(viewModel))
        }

    /** Uno stato v2 del calcio, come lo manda il telefono. */
    private fun statoCalcio(finita: Boolean) =
        WearScoreState(
            side1Primary = "3",
            side1Secondary = "",
            side2Primary = "2",
            side2Secondary = "",
            periodLabel = "",
            hasClock = true,
            hasAuxTimer = true,
            attributesScorer = true,
            decrementIsUndo = false,
            sportId = "soccer",
            sportLabel = "Calcio",
            sportIds = emptyList(),
            sportLabels = emptyList(),
            matchInProgress = !finita,
            matchOver = finita,
            eventLog = "",
        )

    private fun sequenza(): Long {
        val campo = WearViewModel::class.java.getDeclaredField("intentSequence")
        campo.isAccessible = true
        return campo.getLong(viewModel)
    }

    @Test
    fun `a partita finita il tocco non parte e non chiede il marcatore`() {
        // Con la rosa piena e il marcatore attivo: se il tocco passasse, si vedrebbe subito.
        viewModel.setAllPlayers(listOf(PlayerData(id = 1, name = "Rossi", roles = emptyList())))
        viewModel.applyStateV2(statoCalcio(finita = true))
        val prima = sequenza()

        viewModel.incrementScore(1)

        // La sequenza si consuma PRIMA dell'invio: se non si e' mossa, nessuna intenzione e'
        // partita e nessuna e' finita in coda. Il telefono non vede niente, niente riga fantasma.
        assertEquals(prima, sequenza())
        assertNull(viewModel.showPlayerSelection.value)
    }

    @Test
    fun `a partita in corso lo stesso tocco parte`() {
        // Il controllo del test precedente: la guardia non deve spegnere il tocco normale.
        viewModel = viewModelConTelefono(collegato = true)
        viewModel.setAllPlayers(listOf(PlayerData(id = 1, name = "Rossi", roles = emptyList())))
        viewModel.applyStateV2(statoCalcio(finita = false))
        val prima = sequenza()

        viewModel.incrementScore(1)
        // La scelta si apre dopo l'esito dell'invio, non insieme al tocco.
        aspettaChe { viewModel.showPlayerSelection.value != null }

        assertEquals(prima + 1, sequenza())
        assertEquals(1, viewModel.showPlayerSelection.value)
    }

    /**
     * Un ViewModel con un telefono finto: collegato davvero, oppure nessun nodo.
     *
     * Il canale e' quello vero, con i soli client GMS finti: cosi' l'esito e' deciso dallo stesso
     * criterio del pallino (capability E connectedNodes), non da un booleano inventato dal test.
     */
    private fun viewModelConTelefono(collegato: Boolean): WearViewModel {
        val nodo = Mockito.mock(Node::class.java)
        Mockito.`when`(nodo.id).thenReturn("telefono")
        val info = Mockito.mock(CapabilityInfo::class.java)
        Mockito.`when`(info.nodes).thenReturn(if (collegato) setOf(nodo) else emptySet())
        val capability = Mockito.mock(CapabilityClient::class.java)
        Mockito.`when`(capability.getCapability(Mockito.anyString(), Mockito.anyInt())).thenReturn(Tasks.forResult(info))
        val nodi = Mockito.mock(NodeClient::class.java)
        Mockito.`when`(nodi.connectedNodes).thenReturn(Tasks.forResult(if (collegato) listOf(nodo) else emptyList()))
        val messaggi = Mockito.mock(MessageClient::class.java)
        Mockito
            .`when`(messaggi.sendMessage(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
            .thenReturn(Tasks.forResult(1))
        return WearViewModel(
            application,
            OptimizedWearDataSync(application, Mockito.mock(DataClient::class.java), messaggi, capability, nodi),
        )
    }

    /**
     * L'invio passa da Dispatchers.IO, un thread vero che il dispatcher di test non governa: si
     * fa girare il Main finto finche' l'esito non e' tornato, con un limite.
     */
    private fun aspettaChe(condizione: () -> Boolean) {
        val limite = System.currentTimeMillis() + 5_000
        while (!condizione() && System.currentTimeMillis() < limite) {
            testDispatcher.scheduler.advanceUntilIdle()
            Thread.sleep(10)
        }
        assertTrue("L'esito dell'invio non e' tornato in tempo", condizione())
    }

    @Test
    fun `senza telefono il punto va in coda e la scelta del marcatore non si apre`() {
        // Calcio col telefono in borsa: il nome scelto al polso non arriverebbe a nessuno. Il
        // punto resta, in coda, e il marcatore si attribuira' dal registro del telefono.
        viewModel = viewModelConTelefono(collegato = false)
        viewModel.setAllPlayers(listOf(PlayerData(id = 1, name = "Rossi", roles = emptyList())))
        viewModel.applyStateV2(statoCalcio(finita = false))

        viewModel.incrementScore(1)
        aspettaChe { viewModel.pendingCount.value == 1 }

        assertNull(viewModel.showPlayerSelection.value)
    }

    @Test
    fun `senza telefono niente scelta del marcatore neanche prima del primo stato v2`() {
        // Un orologio riacceso senza telefono non ha ancora visto un v2, ma la rosa puo' esserci
        // (i DataItem restano sul polso): il vecchio percorso apriva la scelta insieme al tocco.
        viewModel = viewModelConTelefono(collegato = false)
        viewModel.setAllPlayers(listOf(PlayerData(id = 1, name = "Rossi", roles = emptyList())))

        viewModel.incrementScore(1)
        aspettaChe { viewModel.pendingCount.value == 1 }

        assertNull(viewModel.showPlayerSelection.value)
    }

    @Test
    fun `a partita finita il tocco rifiutato vibra da errore, a partita in corso no`() {
        // Il padel e' dove la partita finisce davvero: il calcio non ha matchOver.
        viewModel.applyStateV2(statoCalcio(finita = true).copy(sportId = "padel", hasClock = false))

        viewModel.incrementScore(1)

        // Scartato ma sentito: il doppio colpo di errore, subito, senza aspettare il telefono.
        Mockito.verify(vibrator, Mockito.times(1)).vibrate(Mockito.any(VibrationEffect::class.java))

        // Controllo: a partita in corso la guardia non vibra. La conferma del tocco normale arriva
        // solo dopo l'invio, in una coroutine che qui non viene fatta girare.
        Mockito.clearInvocations(vibrator)
        viewModel.applyStateV2(statoCalcio(finita = false).copy(sportId = "padel", hasClock = false))
        viewModel.incrementScore(1)
        Mockito.verify(vibrator, Mockito.never()).vibrate(Mockito.any(VibrationEffect::class.java))
    }

    @Test
    fun `dopo AZZERA dal polso il blocco cade con la partita nuova del telefono`() {
        // Il contratto su cui si regge la guardia: AZZERA non tocca lo stato v2 (difetto noto,
        // fuori da questa voce), quindi fino alla risposta del telefono il tocco resta rifiutato.
        // Il telefono risponde: MATCH_STATE=false porta a endMatch e startNewMatch, che rimanda
        // uno stato v2 a partita non finita. Da li' il tocco riparte.
        viewModel.applyStateV2(statoCalcio(finita = true).copy(sportId = "padel", hasClock = false))
        viewModel.resetMatch()
        val prima = sequenza()

        viewModel.incrementScore(1)
        assertEquals(prima, sequenza())

        viewModel.applyStateV2(statoCalcio(finita = false).copy(sportId = "padel", hasClock = false))
        viewModel.incrementScore(1)
        assertEquals(prima + 1, sequenza())
    }

    @Test
    fun `chi serve arriva dallo stato v2, e senza chiave vale 0`() {
        val conServizio = DataMap().apply { putInt(WearConstants.KEY_SERVING_SIDE, 2) }
        assertEquals(2, WearScoreState.fromDataMap(conServizio).servingSide)

        // Un telefono che non lo manda (calcio, o una versione precedente) non deve rompere niente.
        assertEquals(0, WearScoreState.fromDataMap(DataMap()).servingSide)
    }
}
