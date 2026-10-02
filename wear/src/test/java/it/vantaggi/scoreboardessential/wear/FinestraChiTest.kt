package it.vantaggi.scoreboardessential.wear

import android.app.Application
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.shared.PlayerData
import it.vantaggi.scoreboardessential.shared.communication.ConnectionState
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

/** Un vibratore che non fa niente: qui si guarda la finestra CHI?, non il vocabolario aptico. */
private class VibratoreMuto : WearHaptics {
    override fun suona(pattern: LongArray) = Unit

    override fun annulla() = Unit

    override fun tick() = Unit
}

/**
 * La finestra CHI?: dopo la ricevuta del gol si offre per 8s il marcatore, solo da collegati.
 *
 * Il tempo e' quello dello scheduler di test (come in [RicevuteTocchiTest]): gli 8 secondi si
 * saltano, non si aspettano, e l'orologio del ViewModel e' lo stesso. Il telefono e' un finto che
 * risponde subito, cosi' l'esito dell'invio lo sceglie il test e non un thread di I/O.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FinestraChiTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var application: Application

    @Mock
    private lateinit var packageManager: android.content.pm.PackageManager

    private lateinit var viewModel: WearViewModel
    private lateinit var collegamento: MutableStateFlow<ConnectionState>

    /** Come risponde sendMessage: true e' "consegnato al telefono", false "nessun nodo". */
    private var consegnato = true

    private val inizio = 1_700_000_000_000L

    private val rosa = listOf(PlayerData(id = 7, name = "Rossi", roles = emptyList()))

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
        val telefono =
            Mockito.mock(
                OptimizedWearDataSync::class.java,
                Answer { invocazione ->
                    if (invocazione.method.name == "sendMessage") consegnato else Mockito.RETURNS_DEFAULTS.answer(invocazione)
                },
            )
        Mockito.`when`(telefono.connectionState).thenReturn(collegamento)
        viewModel =
            WearViewModel(
                application,
                telefono,
                orologio = { inizio + testDispatcher.scheduler.currentTime },
                haptics = VibratoreMuto(),
            )
        viewModel.setAllPlayers(rosa)
        // Il primo v2 e' gia' arrivato: da li' in poi le ricevute si aprono.
        viewModel.applyStateV2(statoCalcio(registro(0)))
        assestati()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun registro(eventi: Int): String = MatchLogCodec.encode(List(eventi) { LoggedEvent(ScoringEvent.Point(side = 1)) })

    private fun statoCalcio(
        registro: String,
        sportId: String = "football",
        attribuisce: Boolean = true,
        punti: Pair<String, String> = "0" to "0",
        finita: Boolean = false,
    ) = WearScoreState(
        side1Primary = punti.first,
        side1Secondary = "",
        side2Primary = punti.second,
        side2Secondary = "",
        periodLabel = "",
        hasClock = true,
        hasAuxTimer = true,
        attributesScorer = attribuisce,
        decrementIsUndo = false,
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

    /** Un tocco sul lato, consegnato, e il telefono che rimanda lo stato col gol dentro. */
    private fun golConfermato(
        lato: Int = 1,
        registroNuovo: Int = 1,
        stato: WearScoreState = statoCalcio(registro(registroNuovo), punti = "1" to "0"),
    ) {
        viewModel.incrementScore(lato)
        assestati()
        viewModel.applyStateV2(stato)
        assestati()
    }

    // --- Si apre dopo la ricevuta, non prima ---

    @Test
    fun `dopo la ricevuta del gol nel calcio con una rosa la finestra CHI e' aperta`() {
        golConfermato(lato = 2)

        val finestra = viewModel.finestraChi.value
        assertNotNull("la finestra CHI? non si e' aperta", finestra)
        assertEquals(2, finestra!!.lato)
        // Il punteggio e' quello DOPO il gol offerto, fissato adesso.
        assertEquals("1–0", finestra.risultato)
    }

    @Test
    fun `al tocco e alla consegna la finestra non c'e' ancora, aspetta lo stato del telefono`() {
        viewModel.incrementScore(1)
        assestati()
        avanza(1_000)

        // Consegnato non e' preso: senza lo stato col registro cresciuto non c'e' un gol da attribuire.
        assertNull(viewModel.finestraChi.value)
    }

    @Test
    fun `uno stato con lo stesso registro non apre la finestra`() {
        viewModel.incrementScore(1)
        assestati()

        viewModel.applyStateV2(statoCalcio(registro(0)))
        assestati()

        assertNull(viewModel.finestraChi.value)
    }

    @Test
    fun `se il registro si accorcia e' un annullamento, non un gol da attribuire`() {
        viewModel.applyStateV2(statoCalcio(registro(2)))
        assestati()
        viewModel.incrementScore(1)
        assestati()

        // Nello stesso istante il telefono toglie un evento: il registro scende, nessun gol nuovo.
        viewModel.applyStateV2(statoCalcio(registro(1)))
        assestati()

        assertNull(viewModel.finestraChi.value)
    }

    // --- Quando non si offre ---

    @Test
    fun `da scollegati la finestra non compare, il punto va in coda`() {
        collegamento.value = ConnectionState.Disconnected
        consegnato = false
        assestati()

        viewModel.incrementScore(1)
        assestati()
        // Anche se per un caso lo stato arrivasse lo stesso (un DataItem in ritardo), da scollegati
        // la scelta non partirebbe verso nessuno.
        viewModel.applyStateV2(statoCalcio(registro(1)))
        assestati()

        assertEquals(1, viewModel.pendingCount.value)
        assertNull(viewModel.finestraChi.value)
    }

    @Test
    fun `se il telefono risulta scollegato quando arriva la ricevuta la finestra non si apre`() {
        viewModel.incrementScore(1)
        assestati()
        collegamento.value = ConnectionState.Disconnected
        assestati()

        viewModel.applyStateV2(statoCalcio(registro(1)))
        assestati()

        assertNull(viewModel.finestraChi.value)
    }

    @Test
    fun `nel padel la finestra non si apre`() {
        // Il padel non attribuisce: attributesScorer e' falso, anche con una rosa piena.
        viewModel.applyStateV2(statoCalcio(registro(0), sportId = "padel", attribuisce = false))
        assestati()

        golConfermato(stato = statoCalcio(registro(1), sportId = "padel", attribuisce = false))

        assertNull(viewModel.finestraChi.value)
    }

    @Test
    fun `senza una rosa la finestra non si apre`() {
        viewModel.setAllPlayers(emptyList())

        golConfermato()

        assertNull(viewModel.finestraChi.value)
    }

    @Test
    fun `un annullamento dall'orologio non offre il marcatore`() {
        viewModel.applyStateV2(statoCalcio(registro(1)))
        assestati()

        viewModel.decrementScore(1)
        assestati()
        viewModel.applyStateV2(statoCalcio(registro(2)))
        assestati()

        // Il registro e' cresciuto, ma l'intento era una correzione e non un punto.
        assertNull(viewModel.finestraChi.value)
    }

    // --- Quanto dura ---

    @Test
    fun `dopo 8 secondi la finestra si chiude`() {
        golConfermato()
        assertNotNull(viewModel.finestraChi.value)

        avanza(8_000L - 1)
        assertNotNull("chiusa un istante prima degli 8s", viewModel.finestraChi.value)

        avanza(1)
        assertNull("ancora aperta dopo gli 8s", viewModel.finestraChi.value)
    }

    @Test
    fun `un secondo gol sostituisce l'offerta e riparte da 8 secondi`() {
        golConfermato(lato = 1, registroNuovo = 1)
        avanza(5_000)

        golConfermato(lato = 2, registroNuovo = 2, stato = statoCalcio(registro(2), punti = "1" to "1"))

        assertEquals(2, viewModel.finestraChi.value!!.lato)
        assertEquals("1–1", viewModel.finestraChi.value!!.risultato)
        // I 5s del primo gol non contano piu': il secondo ha i suoi 8.
        avanza(8_000L - 1)
        assertNotNull(viewModel.finestraChi.value)
        avanza(1)
        assertNull(viewModel.finestraChi.value)
    }

    @Test
    fun `se il telefono cade mentre CHI e' offerto la finestra si chiude`() {
        golConfermato()
        assertNotNull(viewModel.finestraChi.value)

        collegamento.value = ConnectionState.Disconnected
        assestati()

        assertNull(viewModel.finestraChi.value)
    }

    @Test
    fun `un annullamento chiude l'offerta del gol che toglieva`() {
        golConfermato()
        assertNotNull(viewModel.finestraChi.value)

        viewModel.decrementScore(1)

        assertNull(viewModel.finestraChi.value)
    }

    // --- Non piu' vera: il gol offerto non e' piu' quello che il telefono attribuirebbe ---

    @Test
    fun `se il registro si accorcia dopo l'offerta (un ANNULLA) la finestra si chiude`() {
        golConfermato(registroNuovo = 2, stato = statoCalcio(registro(2), punti = "2" to "0"))
        assertNotNull(viewModel.finestraChi.value)

        // L'ANNULLA parte dal telefono: nessun tocco del polso, solo uno stato col registro corto.
        viewModel.applyStateV2(statoCalcio(registro(1), punti = "1" to "0"))
        assestati()

        assertNull("CHI? e' rimasto sul gol annullato", viewModel.finestraChi.value)
    }

    @Test
    fun `se il telefono da' il nome al gol (stessa lunghezza, registro diverso) la finestra si chiude`() {
        golConfermato()
        assertNotNull(viewModel.finestraChi.value)

        // Stessa lunghezza, ma l'evento ora ha un marcatore: lo ha attribuito il telefono.
        val conNome = MatchLogCodec.encode(listOf(LoggedEvent(ScoringEvent.Point(side = 1, playerId = 7))))
        viewModel.applyStateV2(statoCalcio(conNome, punti = "1" to "0"))
        assestati()

        assertNull("il nome andrebbe a un gol gia' attribuito", viewModel.finestraChi.value)
    }

    @Test
    fun `lo stesso registro in uno stato nuovo lascia la finestra aperta`() {
        golConfermato()

        // Il telefono rimanda lo stato (il cronometro e' andato avanti): il gol e' sempre quello.
        viewModel.applyStateV2(statoCalcio(registro(1), punti = "1" to "0"))
        assestati()

        assertNotNull(viewModel.finestraChi.value)
    }

    @Test
    fun `a partita finita la finestra si chiude`() {
        golConfermato()
        assertNotNull(viewModel.finestraChi.value)

        viewModel.applyStateV2(statoCalcio(registro(1), punti = "1" to "0", finita = true))
        assestati()

        assertNull("CHI? e' rimasto sopra PARTITA FINITA", viewModel.finestraChi.value)
    }

    @Test
    fun `una partita nuova (registro vuoto) chiude la finestra`() {
        golConfermato()

        viewModel.applyStateV2(statoCalcio(registro(0)))
        assestati()

        assertNull(viewModel.finestraChi.value)
    }

    @Test
    fun `se la rosa si svuota mentre CHI e' offerto la finestra si chiude`() {
        golConfermato()
        assertNotNull(viewModel.finestraChi.value)

        viewModel.setAllPlayers(emptyList())

        assertNull("la lista avrebbe solo SALTA", viewModel.finestraChi.value)
    }

    @Test
    fun `usata la finestra non resta in piedi`() {
        golConfermato()

        viewModel.chiudiFinestraChi()
        // Il timer degli 8s non deve riaprire niente ne' toccare un'offerta successiva.
        avanza(8_000L)

        assertNull(viewModel.finestraChi.value)
    }
}
