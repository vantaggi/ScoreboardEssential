package it.vantaggi.scoreboardessential.wear

import android.app.Application
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager
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
import org.junit.Assert.assertArrayEquals
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

/** Il vibratore finto: registra i pattern, cosi' "la conferma destra" e' una cosa che si verifica. */
private class VibratoreFinto : WearHaptics {
    val suonati = mutableListOf<LongArray>()

    override fun suona(pattern: LongArray) {
        suonati += pattern
    }

    override fun annulla() = Unit
}

/**
 * La ricevuta del tocco: il polso vibra la conferma quando il telefono rimanda lo stato, non
 * quando il messaggio e' consegnato.
 *
 * Il tempo e' quello dello scheduler di test, come in [RigaStatoViewModelTest]: i 2,5s di attesa si
 * saltano, non si aspettano. Il telefono e' un finto che risponde sempre alla stessa maniera
 * (consegnato oppure no), cosi' l'esito dell'invio lo sceglie il test e non un thread di I/O.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RicevuteTocchiTest {
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
    private val vibratore = VibratoreFinto()

    /** Come risponde sendMessage: true e' "consegnato al telefono", false "nessun nodo". */
    private var consegnato = true

    /** Se non e' null, viene eseguito dentro sendMessage, prima che questo ritorni. */
    private var durantInvio: (() -> Unit)? = null

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
        coda = PendingIntents(vero)

        // Il canale e' un finto: sendMessage e' sospesa e girerebbe su Dispatchers.IO, qui ritorna
        // subito il valore scelto dal test (un valore di ritorno diretto vale "gia' completata").
        telefono =
            Mockito.mock(
                OptimizedWearDataSync::class.java,
                Answer { invocazione ->
                    when (invocazione.method.name) {
                        "sendMessage" -> {
                            durantInvio?.invoke()
                            consegnato
                        }

                        else -> Mockito.RETURNS_DEFAULTS.answer(invocazione)
                    }
                },
            )
        Mockito.`when`(telefono.connectionState).thenReturn(MutableStateFlow(ConnectionState.Connected(1)))
        viewModel =
            WearViewModel(
                application,
                telefono,
                orologio = { inizio + testDispatcher.scheduler.currentTime },
                haptics = vibratore,
            )
        // Il primo v2 e' gia' arrivato: da li' in poi le ricevute si aprono.
        viewModel.applyStateV2(stato(registro(0)))
        assestati()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun registro(eventi: Int): String = MatchLogCodec.encode(List(eventi) { LoggedEvent(ScoringEvent.Point(side = 1)) })

    private fun stato(
        registro: String,
        sportId: String = "padel",
        finita: Boolean = false,
    ) = WearScoreState(
        side1Primary = "0",
        side1Secondary = "",
        side2Primary = "0",
        side2Secondary = "",
        periodLabel = "",
        hasClock = false,
        hasAuxTimer = false,
        attributesScorer = false,
        decrementIsUndo = true,
        sportId = sportId,
        sportLabel = sportId,
        sportIds = emptyList(),
        sportLabels = emptyList(),
        matchInProgress = true,
        matchOver = finita,
        eventLog = registro,
    )

    private fun avanza(millisecondi: Long) {
        testDispatcher.scheduler.advanceTimeBy(millisecondi)
        testDispatcher.scheduler.runCurrent()
    }

    private fun assestati() = testDispatcher.scheduler.runCurrent()

    private fun vibrazioni(): List<List<Long>> = vibratore.suonati.map { it.toList() }

    private fun invii(path: String) = Mockito.mockingDetails(telefono).invocations.count { it.method.name == "sendMessage" && it.arguments[0] == path }

    // --- La conferma suona quando torna lo stato, e dice il lato ---

    @Test
    fun `un punto consegnato non vibra finche' il telefono non rimanda lo stato, poi conferma destra`() {
        viewModel.incrementScore(2)
        assestati()

        // Consegnato non e' preso: il polso sta zitto, e il tick lo da' la schermata.
        assertEquals(1, invii(WearConstants.MSG_SCORE_INTENT))
        assertTrue("vibrazioni prima dello stato: ${vibrazioni()}", vibratore.suonati.isEmpty())

        // Il telefono ha applicato: il registro e' cresciuto di uno.
        viewModel.applyStateV2(stato(registro(1)))
        assestati()

        assertEquals(listOf(WearPatterns.CONFERMA_DESTRA.toList()), vibrazioni())
    }

    @Test
    fun `il lato sinistro suona un impulso e il destro due, mai lo stesso schema`() {
        viewModel.incrementScore(1)
        assestati()
        viewModel.applyStateV2(stato(registro(1)))
        assestati()
        viewModel.incrementScore(2)
        assestati()
        viewModel.applyStateV2(stato(registro(2)))
        assestati()

        assertEquals(listOf(WearPatterns.CONFERMA_SINISTRA.toList(), WearPatterns.CONFERMA_DESTRA.toList()), vibrazioni())
        assertFalse(WearPatterns.CONFERMA_SINISTRA.contentEquals(WearPatterns.CONFERMA_DESTRA))
        // Il vecchio colpo di errore (60/120/60) coinciderebbe con "destra": non deve essere piu' uno schema del polso.
        assertEquals(listOf(0L, 400L), WearPatterns.NON_CONFERMATO.toList())
    }

    @Test
    fun `un annullamento consegnato suona i tre tick quando il registro si accorcia`() {
        viewModel.applyStateV2(stato(registro(2)))
        assestati()

        viewModel.decrementScore(2)
        assestati()
        assertEquals(1, invii(WearConstants.MSG_SCORE_INTENT))
        assertTrue(vibratore.suonati.isEmpty())

        viewModel.applyStateV2(stato(registro(1)))
        assestati()

        assertEquals(listOf(HapticFeedbackManager.PATTERN_UNDO.toList()), vibrazioni())
    }

    // --- Senza stato: NON CONFERMATO, e niente coda ---

    @Test
    fun `senza stato in 2,5 secondi suona il colpo lungo e dice NON CONFERMATO, la coda non cambia`() {
        viewModel.incrementScore(2)
        assestati()

        avanza(WearViewModel.SCADENZA_RICEVUTA_MS - 1)
        assertTrue("prima della scadenza: ${vibrazioni()}", vibratore.suonati.isEmpty())

        avanza(1)
        assertEquals(listOf(listOf(0L, 400L)), vibrazioni())
        assertEquals(Transitorio.NonConfermato, viewModel.statoFiducia.value)

        // Il tocco NON e' stato messo in coda ne' rimandato: senza id idempotente conterebbe due volte.
        assertEquals(0, coda.size)
        assertEquals(0, viewModel.pendingCount.value)
        avanza(10_000)
        assertEquals(1, invii(WearConstants.MSG_SCORE_INTENT))
    }

    @Test
    fun `NON CONFERMATO sparisce dopo 3 secondi e la riga torna sola`() {
        viewModel.incrementScore(1)
        assestati()
        avanza(WearViewModel.SCADENZA_RICEVUTA_MS)
        assertEquals(Transitorio.NonConfermato, viewModel.statoFiducia.value)

        avanza(StatoFiducia.DURATA_TRANSITORIO_MS)

        assertFalse(viewModel.statoFiducia.value is Transitorio)
    }

    @Test
    fun `uno stato che arriva dopo la scadenza non suona una conferma tardiva`() {
        viewModel.incrementScore(2)
        assestati()
        avanza(WearViewModel.SCADENZA_RICEVUTA_MS)
        assertEquals(1, vibrazioni().size)

        viewModel.applyStateV2(stato(registro(1)))
        assestati()

        // Il polso ha gia' detto NON CONFERMATO: una conferma dopo lo smentirebbe a meta' frase.
        assertEquals(1, vibrazioni().size)
    }

    @Test
    fun `uno stato con lo stesso registro non e' una conferma`() {
        viewModel.incrementScore(2)
        assestati()

        // Un v2 qualunque (il telefono manda stati anche per il cronometro): il registro non e' cambiato.
        viewModel.applyStateV2(stato(registro(0)))
        assestati()
        assertTrue(vibratore.suonati.isEmpty())

        avanza(WearViewModel.SCADENZA_RICEVUTA_MS)
        assertEquals(listOf(listOf(0L, 400L)), vibrazioni())
    }

    @Test
    fun `uno stato riletto dai DataItem al risveglio non chiude la ricevuta`() {
        viewModel.incrementScore(2)
        assestati()

        // Una copia, non un momento in cui il telefono ha parlato: non puo' dire "preso".
        viewModel.applyStateV2(stato(registro(1)), dalVivo = false)
        assestati()
        assertTrue(vibratore.suonati.isEmpty())

        avanza(WearViewModel.SCADENZA_RICEVUTA_MS)
        assertEquals(listOf(listOf(0L, 400L)), vibrazioni())
    }

    // --- Invio fallito: la coda di sempre, col pattern del lato ---

    @Test
    fun `invio fallito suona il pattern in coda del lato e il tocco resta in coda`() {
        consegnato = false

        viewModel.incrementScore(2)
        assestati()
        assertEquals(listOf(WearPatterns.IN_CODA_DESTRA.toList()), vibrazioni())
        assertEquals(1, coda.size)

        viewModel.incrementScore(1)
        assestati()
        assertEquals(WearPatterns.IN_CODA_SINISTRA.toList(), vibrazioni().last())
        assertEquals(2, coda.size)

        // Nessuna ricevuta: il punto e' in coda, e non c'e' niente che possa "non essere confermato".
        avanza(10_000)
        assertEquals(2, vibrazioni().size)
    }

    // --- Due tocchi ravvicinati ---

    @Test
    fun `due tocchi ravvicinati hanno una ricevuta ciascuno, chiusa dal proprio stato`() {
        viewModel.incrementScore(1)
        viewModel.incrementScore(2)
        assestati()

        viewModel.applyStateV2(stato(registro(1)))
        assestati()
        assertEquals(listOf(WearPatterns.CONFERMA_SINISTRA.toList()), vibrazioni())

        viewModel.applyStateV2(stato(registro(2)))
        assestati()
        assertEquals(listOf(WearPatterns.CONFERMA_SINISTRA.toList(), WearPatterns.CONFERMA_DESTRA.toList()), vibrazioni())

        avanza(10_000)
        assertEquals(2, vibrazioni().size)
    }

    @Test
    fun `due eventi in un solo stato chiudono due ricevute e nessuna scade a vuoto`() {
        viewModel.incrementScore(1)
        viewModel.incrementScore(2)
        assestati()

        // Il Data Layer puo' coalescere: un solo DataItem, registro cresciuto di due.
        viewModel.applyStateV2(stato(registro(2)))
        assestati()
        assertEquals(listOf(WearPatterns.CONFERMA_DESTRA.toList()), vibrazioni())

        avanza(10_000)
        assertEquals(1, vibrazioni().size)
    }

    @Test
    fun `lo stato arrivato prima che sendMessage torni conferma subito`() {
        // Il telefono e' piu' veloce dell'attesa dell'esito: lo stato entra mentre la consegna e' ancora in volo.
        durantInvio = { viewModel.applyStateV2(stato(registro(1))) }

        viewModel.incrementScore(2)
        assestati()

        assertEquals(listOf(WearPatterns.CONFERMA_DESTRA.toList()), vibrazioni())
        avanza(10_000)
        assertEquals(1, vibrazioni().size)
    }

    // --- Il tick e la partita finita ---

    @Test
    fun `il tocco accettato lo dice alla schermata, quello scartato a partita finita vibra il colpo lungo`() {
        assertTrue(viewModel.incrementScore(1))
        assestati()
        assertTrue(vibratore.suonati.isEmpty())

        viewModel.applyStateV2(stato(registro(0), finita = true))
        val invia = invii(WearConstants.MSG_SCORE_INTENT)

        assertFalse(viewModel.incrementScore(1))
        assestati()

        // Scartato ma sentito: un colpo solo, non la conferma di un lato. E non parte niente.
        assertEquals(listOf(WearPatterns.NON_CONFERMATO.toList()), vibrazioni())
        assertEquals(invia, invii(WearConstants.MSG_SCORE_INTENT))
    }

    // --- Il cambio sport ---

    @Test
    fun `il cambio sport e' confermato quando arriva lo stato con lo sport chiesto`() {
        viewModel.requestSport("tennis")
        assestati()
        assertEquals(1, invii(WearConstants.MSG_SPORT_INTENT))
        assertTrue("consegnato non e' cambiato: ${vibrazioni()}", vibratore.suonati.isEmpty())

        // Uno stato dello sport di prima non e' la risposta.
        viewModel.applyStateV2(stato(registro(0), sportId = "padel"))
        assestati()
        assertTrue(vibratore.suonati.isEmpty())

        viewModel.applyStateV2(stato(registro(0), sportId = "tennis"))
        assestati()
        assertEquals(listOf(HapticFeedbackManager.PATTERN_CONFIRM.toList()), vibrazioni())

        avanza(10_000)
        assertEquals(1, vibrazioni().size)
    }

    @Test
    fun `senza lo stato dello sport chiesto il cambio dice NON CONFERMATO`() {
        viewModel.requestSport("tennis")
        assestati()

        avanza(WearViewModel.SCADENZA_RICEVUTA_MS)

        assertEquals(listOf(listOf(0L, 400L)), vibrazioni())
        assertEquals(Transitorio.NonConfermato, viewModel.statoFiducia.value)
    }

    @Test
    fun `il cambio sport non consegnato vibra il colpo lungo e non apre nessuna ricevuta`() {
        consegnato = false

        viewModel.requestSport("tennis")
        assestati()
        assertEquals(listOf(listOf(0L, 400L)), vibrazioni())

        avanza(10_000)
        assertEquals(1, vibrazioni().size)
    }

    @Test
    fun `i pattern in coda sono la conferma del lato seguita da un colpo lungo`() {
        assertArrayEquals(longArrayOf(0, 70, 120, 350), WearPatterns.IN_CODA_SINISTRA)
        assertArrayEquals(longArrayOf(0, 70, 90, 70, 120, 350), WearPatterns.IN_CODA_DESTRA)
        assertArrayEquals(longArrayOf(0, 70), WearPatterns.CONFERMA_SINISTRA)
        assertArrayEquals(longArrayOf(0, 70, 90, 70), WearPatterns.CONFERMA_DESTRA)
        assertArrayEquals(longArrayOf(0, 30, 50, 30, 50, 30), HapticFeedbackManager.PATTERN_UNDO)
    }
}
