package it.vantaggi.scoreboardessential.wear

import android.app.Application
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.android.gms.wearable.DataMap
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.shared.communication.ConnectionState
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.stubbing.Answer
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

private class SenzaVibrazione : WearHaptics {
    override fun suona(pattern: LongArray) = Unit

    override fun annulla() = Unit

    override fun tick() = Unit
}

/**
 * L4: la fine partita e i comandi dal polso. Il telefono e' un finto che registra cio' che il
 * polso scrive (DataItem) e cio' che manda (messaggi): qui si prova COSA parte e su quale canale.
 *
 * Il tempo e' quello dello scheduler di test, come in [RicevuteTocchiTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FinePartitaDalPolsoTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var application: Application

    @Mock
    private lateinit var packageManager: android.content.pm.PackageManager

    private lateinit var telefono: OptimizedWearDataSync
    private lateinit var coda: PendingIntents
    private lateinit var viewModel: WearViewModel
    private val inizio = 1_700_000_000_000L

    /** Come risponde sendMessage: true e' "consegnato al telefono". */
    private var consegnato = true

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        MockitoAnnotations.openMocks(this)
        val vero = RuntimeEnvironment.getApplication()
        Mockito.`when`(application.packageManager).thenReturn(packageManager)
        Mockito.`when`(packageManager.hasSystemFeature(Mockito.anyString())).thenReturn(false)
        Mockito.`when`(application.applicationContext).thenReturn(application)
        Mockito
            .`when`(application.getSharedPreferences(Mockito.anyString(), Mockito.anyInt()))
            .thenAnswer { inv -> vero.getSharedPreferences(inv.getArgument(0), inv.getArgument(1)) }
        listOf("wear_pending_intents", "wear_last_known_match").forEach { nome ->
            vero
                .getSharedPreferences(nome, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
        coda = PendingIntents(vero)
        telefono =
            Mockito.mock(
                OptimizedWearDataSync::class.java,
                Answer { invocazione ->
                    if (invocazione.method.name == "sendMessage") consegnato else Mockito.RETURNS_DEFAULTS.answer(invocazione)
                },
            )
        Mockito.`when`(telefono.connectionState).thenReturn(MutableStateFlow(ConnectionState.Connected(1)))
        viewModel = nuovoViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun nuovoViewModel() =
        WearViewModel(
            application,
            telefono,
            orologio = { inizio + testDispatcher.scheduler.currentTime },
            haptics = SenzaVibrazione(),
        )

    private fun assestati() = testDispatcher.scheduler.runCurrent()

    private fun registro(eventi: Int): String = MatchLogCodec.encode(List(eventi) { LoggedEvent(ScoringEvent.Point(side = 1)) })

    /** Calcio sul 3-2: il registro ha cinque punti. */
    private fun calcio3a2() =
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
            sportId = SportRegistry.FOOTBALL,
            sportLabel = "Calcio",
            sportIds = emptyList(),
            sportLabels = emptyList(),
            matchInProgress = true,
            matchOver = false,
            eventLog = registro(5),
        )

    private fun dataItem(path: String) =
        Mockito.mockingDetails(telefono).invocations.filter { it.method.name == "sendData" && it.arguments[0] == path }

    private fun urgenti(path: String) = dataItem(path).map { it.arguments[2] as Boolean }

    private fun messaggi(path: String) =
        Mockito.mockingDetails(telefono).invocations.filter { it.method.name == "sendMessage" && it.arguments[0] == path }

    private fun contenuto(invocazione: org.mockito.invocation.Invocation) = DataMap.fromByteArray(invocazione.arguments[1] as ByteArray)

    // --- Rilievo 1: la fine partita e' un'intenzione con sequenza ---

    @Test
    fun `con il v2 FINE PARTITA e' l'intenzione end_match con la sua sequenza e il polso non scrive il v1`() {
        viewModel.applyStateV2(calcio3a2())
        viewModel.syncMatchTimer(40 * 60_000L, true)
        viewModel.setKeeperTimerState(KeeperTimerState.Running(120))

        viewModel.chiudiPartita()
        assestati()

        val fine = messaggi(WearConstants.MSG_SCORE_INTENT)
        assertEquals("un solo messaggio di chiusura", 1, fine.size)
        val dati = contenuto(fine.single())
        assertEquals(WearConstants.INTENT_END_MATCH, dati.getString(WearConstants.KEY_INTENT_KIND))
        assertTrue("la sequenza e' quella del nodo, strettamente crescente", dati.getLong(WearConstants.KEY_SEQ) > inizio)
        // Lo 0-0 v1, il timer e il portiere azzerati, e il MATCH_STATE: nessuno dei quattro.
        assertEquals("niente 0-0 v1", 0, dataItem(WearConstants.PATH_SCORE).size)
        assertEquals("niente timer azzerato", 0, dataItem(WearConstants.PATH_TIMER_STATE).size)
        assertEquals("niente portiere azzerato", 0, dataItem(WearConstants.PATH_KEEPER_TIMER).size)
        assertEquals("niente MATCH_STATE", 0, dataItem(WearConstants.PATH_MATCH_STATE).size)
    }

    @Test
    fun `due chiusure consecutive hanno due sequenze diverse`() {
        viewModel.applyStateV2(calcio3a2())
        viewModel.chiudiPartita()
        assestati()
        viewModel.chiudiPartita()
        assestati()

        val seq = messaggi(WearConstants.MSG_SCORE_INTENT).map { contenuto(it).getLong(WearConstants.KEY_SEQ) }
        assertEquals(2, seq.size)
        assertTrue("le sequenze crescono: $seq", seq[1] > seq[0])
    }

    @Test
    fun `senza v2 l'orologio vecchio continua a chiudere col v1 urgente, e nessuna intenzione`() {
        viewModel.syncMatchTimer(60_000L, false)

        viewModel.chiudiPartita()
        assestati()

        assertEquals(listOf(true), urgenti(WearConstants.PATH_MATCH_STATE))
        assertEquals(listOf(true), urgenti(WearConstants.PATH_SCORE))
        assertEquals(0, messaggi(WearConstants.MSG_SCORE_INTENT).size)
    }

    @Test
    fun `se la chiusura non parte la riga lo dice subito e non aspetta i 10 secondi`() {
        viewModel.applyStateV2(calcio3a2())
        consegnato = false

        viewModel.chiudiPartita()
        assestati()

        assertEquals(Transitorio.ChiusuraNonConfermata, viewModel.statoFiducia.value)
    }

    // --- Rilievo 2: con la coda piena la partita non si chiude, due partite non si fondono ---

    @Test
    fun `con punti in coda il polso non chiude, ne' intenzione ne' v1`() {
        viewModel.applyStateV2(calcio3a2())
        coda.add(PendingIntent("point", 1, inizio))
        viewModel.refreshPendingCount()
        assestati()

        val accettata = viewModel.chiudiPartita()
        assestati()

        assertFalse("la chiusura e' rifiutata", accettata)
        assertEquals(0, messaggi(WearConstants.MSG_SCORE_INTENT).size)
        assertEquals(0, dataItem(WearConstants.PATH_MATCH_STATE).size)
        assertEquals(0, dataItem(WearConstants.PATH_SCORE).size)
    }

    /**
     * Rilievo della revisione L4 (bassa): un punto consegnato e non ancora confermato e' una
     * ricevuta aperta. MessageClient non garantisce l'ordine: un end_match che supera l'ultimo gol
     * lo farebbe perdere. Come con la coda e l'arretrato in volo, la chiusura si rifiuta.
     */
    @Test
    fun `con una ricevuta aperta il polso non chiude`() {
        viewModel.applyStateV2(calcio3a2())
        viewModel.incrementScore(1)
        assestati()
        assertEquals("il punto e' partito, in attesa dello stato del telefono", 1, messaggi(WearConstants.MSG_SCORE_INTENT).size)

        val accettata = viewModel.chiudiPartita()
        assestati()

        assertFalse("la chiusura e' rifiutata", accettata)
        assertEquals("nessun end_match oltre al punto", 1, messaggi(WearConstants.MSG_SCORE_INTENT).size)
        assertEquals(0, dataItem(WearConstants.PATH_MATCH_STATE).size)
    }

    @Test
    fun `con un arretrato in volo il polso non chiude`() {
        viewModel.applyStateV2(calcio3a2())
        // Coda vuota (le voci sono state consegnate e tolte), ma l'ack dell'arretrato non e' arrivato.
        val campo = WearViewModel::class.java.getDeclaredField("batchInVolo")
        campo.isAccessible = true
        campo.set(viewModel, 7L to 2)

        val accettata = viewModel.chiudiPartita()
        assestati()

        assertFalse("la chiusura e' rifiutata", accettata)
        assertEquals(0, messaggi(WearConstants.MSG_SCORE_INTENT).size)
    }

    @Test
    fun `con la coda vuota la chiusura e' accettata`() {
        viewModel.applyStateV2(calcio3a2())

        assertTrue(viewModel.chiudiPartita())
    }

    // --- Rilievo 3: un solo canale prima del primo v2 ---

    @Test
    fun `con un v1 dal telefono e nessun v2 un tocco va solo come punteggio v1, non anche come intenzione`() {
        // Un telefono non aggiornato: ha mandato un punteggio v1 e nessuno stato v2.
        viewModel.updateScoresFromMobile(0, 0)

        viewModel.incrementScore(1)
        assestati()

        assertEquals(0, messaggi(WearConstants.MSG_SCORE_INTENT).size)
        assertEquals(1, dataItem(WearConstants.PATH_SCORE).size)
    }

    @Test
    fun `con un v2 gia' visto un tocco va solo come intenzione`() {
        viewModel.applyStateV2(calcio3a2())

        viewModel.incrementScore(1)
        assestati()

        assertEquals(1, messaggi(WearConstants.MSG_SCORE_INTENT).size)
        assertEquals(0, dataItem(WearConstants.PATH_SCORE).size)
    }

    @Test
    fun `un v2 che c'e' solo sul disco basta, il tocco a freddo va solo come intenzione`() {
        // L'orologio e' stato riavviato: nessun stato riletto ancora, ma il disco ricorda il telefono v2.
        LastKnownMatch(RuntimeEnvironment.getApplication()).save(SportRegistry.FOOTBALL, registro(5), 0)

        viewModel.incrementScore(1)
        assestati()

        assertEquals(1, messaggi(WearConstants.MSG_SCORE_INTENT).size)
        assertEquals("niente PATH_SCORE a freddo", 0, dataItem(WearConstants.PATH_SCORE).size)
    }

    /**
     * Rilievo della revisione L4 (bassa): lo stato letto dal disco e' una copia, forse vecchia, e
     * la lunghezza del suo registro non e' una base per le ricevute. Disco a 4 eventi, telefono
     * che in realta' ne aveva 3: il tocco a freddo porta il registro a 4 e, con la base dal disco,
     * lo stato vero (4) sembrerebbe "nessun cambiamento", e la ricevuta direbbe NON CONFERMATO a
     * un punto preso. Senza stato dal vivo il tocco a freddo non apre una ricevuta.
     */
    @Test
    fun `un tocco a freddo non prende la base delle ricevute dal registro su disco`() {
        LastKnownMatch(RuntimeEnvironment.getApplication()).save(SportRegistry.FOOTBALL, registro(4), 0)

        viewModel.incrementScore(1)
        assestati()
        // Il telefono risponde con lo stato vero: 4 eventi, quelli del disco erano di una partita vecchia.
        viewModel.applyStateV2(calcio3a2().copy(eventLog = registro(4)))
        assestati()
        testDispatcher.scheduler.advanceTimeBy(WearViewModel.SCADENZA_RICEVUTA_MS + 1)
        assestati()

        assertFalse(
            "nessun NON CONFERMATO a un punto preso: ${viewModel.statoFiducia.value}",
            viewModel.statoFiducia.value == Transitorio.NonConfermato,
        )
        // Da quando il telefono ha parlato dal vivo la base e' la sua: il tocco dopo apre la ricevuta
        // (lo si vede dal fatto che la chiusura e' rifiutata finche' lo stato non la chiude).
        viewModel.incrementScore(1)
        assestati()
        assertFalse(viewModel.chiudiPartita())
        viewModel.applyStateV2(calcio3a2().copy(eventLog = registro(5)))
        assestati()
        assertTrue(viewModel.chiudiPartita())
    }

    /**
     * Buco trovato dalla falsificazione: con un v2 il reset del polso non scrive niente sul filo
     * (ne' lo 0-0 v1, ne' i timer, ne' il MATCH_STATE): svuoterebbe il motore del telefono prima
     * di qualunque chiusura. Nessun test proteggeva `senzaInvii = fromRemote || protocolV2Seen`.
     */
    @Test
    fun `con un v2 il reset dal polso non scrive niente sul filo`() {
        viewModel.applyStateV2(calcio3a2())
        viewModel.syncMatchTimer(40 * 60_000L, true)

        viewModel.resetMatch()
        assestati()

        assertEquals("niente 0-0 v1", 0, dataItem(WearConstants.PATH_SCORE).size)
        assertEquals("niente timer azzerato", 0, dataItem(WearConstants.PATH_TIMER_STATE).size)
        assertEquals("niente portiere azzerato", 0, dataItem(WearConstants.PATH_KEEPER_TIMER).size)
        assertEquals("niente MATCH_STATE", 0, dataItem(WearConstants.PATH_MATCH_STATE).size)
        assertEquals("niente intenzione", 0, messaggi(WearConstants.MSG_SCORE_INTENT).size)
    }

    // --- Rilievo 4: i comandi sono DataItem urgenti ---

    @Test
    fun `cronometro e portiere dal polso partono come DataItem urgenti`() {
        viewModel.toggleTimer()
        viewModel.resetMatchTimer()
        viewModel.toggleKeeperTimer()
        viewModel.resetKeeperTimer()
        assestati()

        assertEquals(listOf(true, true), urgenti(WearConstants.PATH_TIMER_STATE))
        assertEquals(listOf(true, true), urgenti(WearConstants.PATH_KEEPER_TIMER))
    }
}
