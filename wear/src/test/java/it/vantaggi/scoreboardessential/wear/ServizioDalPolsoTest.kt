package it.vantaggi.scoreboardessential.wear

import android.app.Application
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.android.gms.wearable.DataMap
import it.vantaggi.scoreboardessential.core.MatchEngine
import it.vantaggi.scoreboardessential.core.RacketScore
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

private class SenzaVibrare : WearHaptics {
    override fun suona(pattern: LongArray) = Unit

    override fun annulla() = Unit

    override fun tick() = Unit
}

/**
 * Chi serve dal polso, nel ViewModel: SERVE? offerto per 8s a inizio set sullo stato del telefono, e
 * le due intenzioni (la scelta e la correzione) che partono verso il telefono. Il tempo e' quello
 * dello scheduler di test, come in [FinestraChiTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ServizioDalPolsoTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var application: Application

    @Mock
    private lateinit var packageManager: android.content.pm.PackageManager

    private lateinit var telefono: OptimizedWearDataSync
    private lateinit var collegamento: MutableStateFlow<ConnectionState>
    private lateinit var viewModel: WearViewModel
    private val inizio = 1_700_000_000_000L

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
        collegamento = MutableStateFlow(ConnectionState.Connected(1))
        telefono =
            Mockito.mock(
                OptimizedWearDataSync::class.java,
                Answer { invocazione ->
                    if (invocazione.method.name == "sendMessage") true else Mockito.RETURNS_DEFAULTS.answer(invocazione)
                },
            )
        Mockito.`when`(telefono.connectionState).thenReturn(collegamento)
        viewModel =
            WearViewModel(
                application,
                telefono,
                orologio = { inizio + testDispatcher.scheduler.currentTime },
                haptics = SenzaVibrare(),
            )
        assestati()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun assestati() = testDispatcher.scheduler.runCurrent()

    private fun avanza(millisecondi: Long) {
        testDispatcher.scheduler.advanceTimeBy(millisecondi)
        testDispatcher.scheduler.runCurrent()
    }

    private fun intenzioni() =
        Mockito
            .mockingDetails(telefono)
            .invocations
            .filter { it.method.name == "sendMessage" && it.arguments[0] == WearConstants.MSG_SCORE_INTENT }
            .map { DataMap.fromByteArray(it.arguments[1] as ByteArray) }

    // --- SERVE? ---

    /** Rosso se a inizio set, col telefono collegato, la capsula non si offre o non scade dopo 8s. */
    @Test
    fun `a inizio set SERVE e' offerto per 8 secondi`() {
        viewModel.applyStateV2(statoPadel(emptyList()))
        assestati()
        assertTrue(viewModel.offertaServizio.value)
        avanza(WearViewModel.DURATA_FINESTRA_SERVIZIO_MS + 1)
        assertFalse(viewModel.offertaServizio.value)
    }

    /** Rosso se lo stesso inizio di set (un ritorno dal menu, uno stato ripetuto) riapre la capsula. */
    @Test
    fun `lo stesso inizio di set non si offre due volte`() {
        val stato = statoPadel(emptyList())
        viewModel.applyStateV2(stato)
        assestati()
        avanza(WearViewModel.DURATA_FINESTRA_SERVIZIO_MS + 1)
        viewModel.applyStateV2(stato.copy(stateVersion = 2))
        assestati()
        assertFalse(viewModel.offertaServizio.value)
    }

    /** Rosso se SERVE? si offre da scollegati, a meta' game o dopo una scelta gia' fatta. */
    @Test
    fun `scollegati, a meta' game o a scelta fatta non si offre`() {
        collegamento.value = ConnectionState.Disconnected
        viewModel.applyStateV2(statoPadel(emptyList()))
        assestati()
        assertFalse("scollegati", viewModel.offertaServizio.value)

        collegamento.value = ConnectionState.Connected(1)
        viewModel.applyStateV2(statoPadel(listOf(ScoringEvent.Point(1)), uuid = "b"))
        assestati()
        assertFalse("a 15-0", viewModel.offertaServizio.value)

        viewModel.applyStateV2(statoPadel(listOf(ScoringEvent.ServeChoice(2, 1, 1)), uuid = "c"))
        assestati()
        assertFalse("scelta gia' fatta", viewModel.offertaServizio.value)
    }

    /** Rosso se un punto segnato dal polso lascia la capsula in piedi. */
    @Test
    fun `un punto segnato dal polso chiude SERVE`() {
        viewModel.applyStateV2(statoPadel(emptyList()))
        assestati()
        assertTrue(viewModel.offertaServizio.value)
        viewModel.incrementScore(1)
        assestati()
        assertFalse(viewModel.offertaServizio.value)
    }

    // --- Le intenzioni ---

    /**
     * Rosso se la scelta non parte come intenzione serve con i tre numeri e la sua sequenza, se porta
     * un lato (il telefono vecchio la prenderebbe per un punto) o se lascia la capsula aperta.
     */
    @Test
    fun `la scelta parte come intenzione serve senza lato e chiude SERVE`() {
        viewModel.applyStateV2(statoPadel(emptyList()))
        assestati()
        viewModel.scegliChiServe(lato = 2, slot1 = 2, slot2 = 1)
        assestati()
        val dati = intenzioni().single()
        assertEquals(WearConstants.INTENT_SERVE, dati.getString(WearConstants.KEY_INTENT_KIND))
        assertEquals(2, dati.getInt(WearConstants.KEY_SERVE_SIDE))
        assertEquals(2, dati.getInt(WearConstants.KEY_SERVE_SLOT1))
        assertEquals(1, dati.getInt(WearConstants.KEY_SERVE_SLOT2))
        assertTrue(dati.getLong(WearConstants.KEY_SEQ) > 0L)
        assertFalse("nessun lato", dati.containsKey(WearConstants.KEY_SIDE))
        assertFalse(viewModel.offertaServizio.value)
    }

    /**
     * La correzione la calcola il motore sul registro del telefono, come sul telefono. Rosso se il polso
     * manda una scelta diversa da serveCorrection, o se la proposta non e' il prossimo di ogni coppia.
     */
    @Test
    fun `la correzione e le proposte vengono dal registro del telefono`() {
        val eventi = game(1) + game(2) + game(1) + listOf(ScoringEvent.Point(2))
        viewModel.applyStateV2(statoPadel(eventi))
        assestati()
        val regole = SportRegistry.byId(SportRegistry.PADEL)
        val motore = MatchEngine(regole)
        eventi.forEach { motore.apply(it) }
        val atteso = (motore.state as RacketScore).serveCorrection(side = 1, slot = 1)
        assertEquals((motore.state as RacketScore).nextSlot(1), viewModel.prossimoAServire(1))
        assertEquals((motore.state as RacketScore).nextSlot(2), viewModel.prossimoAServire(2))

        viewModel.correggiChiServe(lato = 1, slot = 1)
        assestati()
        val dati = intenzioni().single()
        assertEquals(atteso.side, dati.getInt(WearConstants.KEY_SERVE_SIDE))
        assertEquals(atteso.slot1, dati.getInt(WearConstants.KEY_SERVE_SLOT1))
        assertEquals(atteso.slot2, dati.getInt(WearConstants.KEY_SERVE_SLOT2))
    }
}
