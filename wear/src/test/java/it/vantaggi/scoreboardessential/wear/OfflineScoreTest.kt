package it.vantaggi.scoreboardessential.wear

import android.app.Application
import android.content.Context
import android.os.Vibrator
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import it.vantaggi.scoreboardessential.core.MatchEngine
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Il punteggio che il polso mostra da solo deve essere quello che il telefono calcolera'.
 *
 * La promessa non e' "somigliante": e' IDENTICO per costruzione, perche' i due lati chiamano la
 * stessa funzione di :core sugli stessi eventi. Questi test la verificano sul caso che conta --
 * una partita cominciata col telefono presente e proseguita senza -- invece di darla per buona.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class OfflineScoreTest {
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
    private lateinit var coda: PendingIntents

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        MockitoAnnotations.openMocks(this)
        val vero = RuntimeEnvironment.getApplication()

        Mockito.`when`(application.getSystemService(Context.VIBRATOR_SERVICE)).thenReturn(vibrator)
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

    /** Un padel a set unico e golden point, come lo configura il registro degli sport. */
    private fun motorePadel() = MatchEngine(SportRegistry.byId(SportRegistry.PADEL))

    private fun statoDalTelefono(
        registro: String,
        primo: String = "0",
        secondo: String = "0",
    ) = WearScoreState(
        side1Primary = primo,
        side1Secondary = "",
        side2Primary = secondo,
        side2Secondary = "",
        periodLabel = "",
        hasClock = false,
        hasAuxTimer = false,
        attributesScorer = false,
        decrementIsUndo = true,
        sportId = SportRegistry.PADEL,
        sportLabel = "Padel",
        sportIds = listOf(SportRegistry.PADEL),
        sportLabels = listOf("Padel"),
        matchInProgress = registro.isNotEmpty(),
        matchOver = false,
        eventLog = registro,
    )

    @Test
    fun `senza tocchi in coda vale cio' che dice il telefono`() {
        viewModel.applyStateV2(statoDalTelefono(registro = "", primo = "40", secondo = "30"))

        // Nessun calcolo locale: il telefono e' presente e autoritativo.
        assertEquals("40", viewModel.scoreState.value?.side1Primary)
        assertEquals("30", viewModel.scoreState.value?.side2Primary)
    }

    @Test
    fun `il punteggio offline continua la partita, non ne comincia una nuova`() {
        // Tre punti segnati col telefono in mano: 40-0.
        val telefono = motorePadel()
        repeat(3) { telefono.apply(ScoringEvent.Point(side = 1)) }
        assertEquals("40", SportRegistry.byId(SportRegistry.PADEL).display(telefono.state).side1Primary)

        // Il telefono sparisce e arriva un quarto punto, che col golden point chiude il game.
        coda.add(PendingIntent(WearConstants.INTENT_POINT, 1, 1_000L))
        viewModel.applyStateV2(statoDalTelefono(MatchLogCodec.encode(telefono.log), primo = "40"))

        // La verita' di confronto: lo stesso motore con lo stesso quarto punto.
        telefono.apply(ScoringEvent.Point(side = 1))
        val atteso = SportRegistry.byId(SportRegistry.PADEL).display(telefono.state)

        assertEquals(atteso.side1Primary, viewModel.scoreState.value?.side1Primary)
        assertEquals(atteso.side1Secondary.orEmpty(), viewModel.scoreState.value?.side1Secondary)
        assertEquals(atteso.side2Primary, viewModel.scoreState.value?.side2Primary)
    }

    @Test
    fun `una partita intera segnata offline coincide con quella del telefono`() {
        // Nessun evento sul telefono: il polso e' rimasto solo dall'inizio.
        val sequenza = listOf(1, 1, 2, 1, 2, 2, 2, 1, 1, 1)
        sequenza.forEachIndexed { i, lato ->
            coda.add(PendingIntent(WearConstants.INTENT_POINT, lato, (i + 1) * 1_000L))
        }
        viewModel.applyStateV2(statoDalTelefono(registro = ""))

        val telefono = motorePadel()
        sequenza.forEach { telefono.apply(ScoringEvent.Point(side = it)) }
        val atteso = SportRegistry.byId(SportRegistry.PADEL).display(telefono.state)

        assertEquals(atteso.side1Primary, viewModel.scoreState.value?.side1Primary)
        assertEquals(atteso.side2Primary, viewModel.scoreState.value?.side2Primary)
    }

    @Test
    fun `l'annullamento in coda toglie il punto anche nel conto locale`() {
        val telefono = motorePadel()
        repeat(2) { telefono.apply(ScoringEvent.Point(side = 1)) }

        coda.add(PendingIntent(WearConstants.INTENT_POINT, 1, 1_000L))
        coda.add(PendingIntent(WearConstants.INTENT_UNDO, 1, 2_000L))
        viewModel.applyStateV2(statoDalTelefono(MatchLogCodec.encode(telefono.log), primo = "30"))

        // Punto piu' annullamento: si torna dov'era il telefono.
        val atteso = SportRegistry.byId(SportRegistry.PADEL).display(telefono.state)
        assertEquals(atteso.side1Primary, viewModel.scoreState.value?.side1Primary)
    }

    @Test
    fun `un orologio riavviato senza telefono ritrova il punteggio`() {
        // Il telefono aveva raccontato una partita a 30-0, poi e' sparito e sono arrivati due
        // punti. Qui l'orologio si riavvia: la memoria e' vuota, resta solo il disco.
        val telefono = motorePadel()
        repeat(2) { telefono.apply(ScoringEvent.Point(side = 1)) }
        LastKnownMatch(RuntimeEnvironment.getApplication())
            .save(SportRegistry.PADEL, MatchLogCodec.encode(telefono.log))
        coda.add(PendingIntent(WearConstants.INTENT_POINT, 1, 1_000L))
        coda.add(PendingIntent(WearConstants.INTENT_POINT, 2, 2_000L))

        // E' cio' che fa MainActivity in onCreate.
        viewModel.refreshPendingCount()

        telefono.apply(ScoringEvent.Point(side = 1))
        telefono.apply(ScoringEvent.Point(side = 2))
        val atteso = SportRegistry.byId(SportRegistry.PADEL).display(telefono.state)
        assertEquals(atteso.side1Primary, viewModel.scoreState.value?.side1Primary)
        assertEquals(atteso.side2Primary, viewModel.scoreState.value?.side2Primary)
    }

    @Test
    fun `il punto che chiude la partita spegne i comandi anche offline`() {
        // Col telefono lontano, il polso deve sapere DA SOLO che la partita e' finita: altrimenti
        // i due lati resterebbero premibili a vuoto proprio dove e' piu' facile toccare senza
        // guardare. E' lo stesso calcolo che fara' il telefono, quindi dice la stessa cosa.
        val telefono = motorePadel()
        // 23 punti: manca l'ultimo per chiudere 6-0 con il golden point.
        repeat(23) { telefono.apply(ScoringEvent.Point(side = 1)) }
        coda.add(PendingIntent(WearConstants.INTENT_POINT, 1, 1_000L))

        viewModel.applyStateV2(statoDalTelefono(MatchLogCodec.encode(telefono.log)))

        assertTrue("il 24esimo punto chiude la partita", viewModel.scoreState.value?.matchOver == true)
    }

    @Test
    fun `senza sport non si calcola niente e resta cio' che il telefono aveva mandato`() {
        // Telefono che parla una bozza precedente del v2: nessuno sportId.
        coda.add(PendingIntent(WearConstants.INTENT_POINT, 1, 1_000L))
        viewModel.applyStateV2(statoDalTelefono(registro = "", primo = "7").copy(sportId = ""))

        // Non si inventa un calcolo con regole indovinate: si mostra l'ultimo dato vero.
        assertEquals("7", viewModel.scoreState.value?.side1Primary)
    }
}
