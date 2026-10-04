package it.vantaggi.scoreboardessential.wear

import android.app.Application
import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.google.android.gms.wearable.DataMap
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
private class VibratoreFinto(
    private val ora: () -> Long,
) : WearHaptics {
    val suonati = mutableListOf<LongArray>()

    /** Gli istanti dei pattern in [suonati], uno per uno. */
    val suonatiAlle = mutableListOf<Long>()

    /** Gli istanti dei tick. */
    val ticks = mutableListOf<Long>()

    /** L'ordine di tutto cio' che e' suonato: "tick" oppure "pattern". */
    val ordine = mutableListOf<String>()

    override fun suona(pattern: LongArray) {
        suonati += pattern
        suonatiAlle += ora()
        ordine += "pattern"
    }

    override fun annulla() = Unit

    override fun tick() {
        ticks += ora()
        ordine += "tick"
    }
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
    private val vibratore = VibratoreFinto { inizio + testDispatcher.scheduler.currentTime }

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

                        else -> {
                            Mockito.RETURNS_DEFAULTS.answer(invocazione)
                        }
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
        lastBatchId: Long = 0L,
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
        lastBatchId = lastBatchId,
    )

    private fun avanza(millisecondi: Long) {
        testDispatcher.scheduler.advanceTimeBy(millisecondi)
        testDispatcher.scheduler.runCurrent()
    }

    private fun assestati() = testDispatcher.scheduler.runCurrent()

    /** La conferma di lato lascia al tick del tocco i suoi 250ms: i test che la cercano li aspettano. */
    private fun dopoIlTick() = avanza(WearViewModel.DISTANZA_DAL_TICK_MS)

    private fun vibrazioni(): List<List<Long>> = vibratore.suonati.map { it.toList() }

    private fun invii(path: String) =
        Mockito.mockingDetails(telefono).invocations.count {
            it.method.name == "sendMessage" &&
                it.arguments[0] == path
        }

    // --- La conferma suona quando torna lo stato, e dice il lato ---

    @Test
    fun `un punto consegnato non vibra finche' il telefono non rimanda lo stato, poi conferma destra`() {
        viewModel.incrementScore(2)
        assestati()

        // Consegnato non e' preso: il polso sta zitto (il tick e' un altro canale, non un pattern).
        assertEquals(1, invii(WearConstants.MSG_SCORE_INTENT))
        assertTrue("vibrazioni prima dello stato: ${vibrazioni()}", vibratore.suonati.isEmpty())

        // Il telefono ha applicato: il registro e' cresciuto di uno.
        viewModel.applyStateV2(stato(registro(1)))
        assestati()
        dopoIlTick()

        assertEquals(listOf(WearPatterns.CONFERMA_DESTRA.toList()), vibrazioni())
    }

    @Test
    fun `il lato sinistro suona un impulso e il destro due, mai lo stesso schema`() {
        viewModel.incrementScore(1)
        assestati()
        viewModel.applyStateV2(stato(registro(1)))
        assestati()
        dopoIlTick()
        viewModel.incrementScore(2)
        assestati()
        viewModel.applyStateV2(stato(registro(2)))
        assestati()
        dopoIlTick()

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

    // --- L5 custodia: il telefono ha messo da parte il tocco (la sua app e' chiusa) ---

    /** La sequenza dell'ultimo tocco spedito, letta da quello che il telefono ha ricevuto. */
    private fun sequenzaDelTocco(): Long {
        val invio =
            Mockito
                .mockingDetails(telefono)
                .invocations
                .last { it.method.name == "sendMessage" && it.arguments[0] == WearConstants.MSG_SCORE_INTENT }
        return DataMap.fromByteArray(invio.arguments[1] as ByteArray).getLong(WearConstants.KEY_SEQ)
    }

    @Test
    fun `la custodia del telefono chiude la ricevuta senza NON CONFERMATO e la riga dice IN ATTESA`() {
        viewModel.incrementScore(1)
        assestati()
        val seq = sequenzaDelTocco()

        viewModel.onCustodia(seq)
        assestati()
        dopoIlTick()

        assertEquals(Transitorio.InAttesaDelTelefono, viewModel.statoFiducia.value)
        // Niente colpo di errore: il tocco e' al sicuro, col colpo lungo di "il telefono non l'ha ancora".
        assertEquals(listOf(WearPatterns.IN_CODA_SINISTRA.toList()), vibrazioni())

        // Ne' alla scadenza di prima ne' dopo: la ricevuta e' chiusa, e il tocco non si rimanda.
        avanza(WearViewModel.SCADENZA_RICEVUTA_MS)
        assertEquals(listOf(WearPatterns.IN_CODA_SINISTRA.toList()), vibrazioni())
        assertFalse(viewModel.statoFiducia.value == Transitorio.NonConfermato)
        assertEquals(1, invii(WearConstants.MSG_SCORE_INTENT))
        assertEquals("non e' un tocco in coda locale", 0, coda.size)
    }

    @Test
    fun `la custodia vale solo per il tocco con la sua sequenza, gli altri restano in attesa dello stato`() {
        viewModel.incrementScore(1)
        assestati()
        val primo = sequenzaDelTocco()
        viewModel.incrementScore(2)
        assestati()

        viewModel.onCustodia(primo)
        assestati()

        // Il secondo tocco non e' in custodia: allo scadere dice NON CONFERMATO come sempre.
        avanza(WearViewModel.SCADENZA_RICEVUTA_MS)
        assertEquals(Transitorio.NonConfermato, viewModel.statoFiducia.value)
    }

    @Test
    fun `la frase IN ATTESA TELEFONO dura qualche secondo e poi la riga torna sola`() {
        viewModel.incrementScore(1)
        assestati()
        viewModel.onCustodia(sequenzaDelTocco())
        assestati()
        assertEquals(Transitorio.InAttesaDelTelefono, viewModel.statoFiducia.value)

        avanza(WearViewModel.DURATA_CUSTODIA_MS - 1)
        assertEquals(Transitorio.InAttesaDelTelefono, viewModel.statoFiducia.value)

        avanza(1)
        assertFalse(viewModel.statoFiducia.value is Transitorio)
    }

    @Test
    fun `una custodia arrivata dopo la scadenza corregge il NON CONFERMATO`() {
        viewModel.incrementScore(1)
        assestati()
        val seq = sequenzaDelTocco()
        avanza(WearViewModel.SCADENZA_RICEVUTA_MS)
        assertEquals(Transitorio.NonConfermato, viewModel.statoFiducia.value)

        viewModel.onCustodia(seq)
        assestati()

        assertEquals(Transitorio.InAttesaDelTelefono, viewModel.statoFiducia.value)
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
        dopoIlTick()
        assertEquals(listOf(WearPatterns.IN_CODA_DESTRA.toList()), vibrazioni())
        assertEquals(1, coda.size)

        viewModel.incrementScore(1)
        assestati()
        dopoIlTick()
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
        dopoIlTick()
        assertEquals(listOf(WearPatterns.CONFERMA_SINISTRA.toList()), vibrazioni())

        viewModel.applyStateV2(stato(registro(2)))
        assestati()
        dopoIlTick()
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
        dopoIlTick()
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
        dopoIlTick()

        assertEquals(listOf(WearPatterns.CONFERMA_DESTRA.toList()), vibrazioni())
        avanza(10_000)
        assertEquals(1, vibrazioni().size)
    }

    // --- Il tick e la partita finita ---

    @Test
    fun `il tocco accettato suona il tick, quello scartato a partita finita sta in silenzio`() {
        assertTrue(viewModel.incrementScore(1))
        assestati()
        assertEquals(1, vibratore.ticks.size)
        assertTrue(vibratore.suonati.isEmpty())

        viewModel.applyStateV2(stato(registro(0), finita = true))
        val invia = invii(WearConstants.MSG_SCORE_INTENT)
        val giaSuonato = vibratore.ordine.size

        assertFalse(viewModel.incrementScore(1))
        assestati()
        dopoIlTick()

        // Silenzio al polso piu' la parola PARTITA FINITA (DESIGN.md): ne' tick, ne' colpo lungo.
        // E non parte niente.
        assertEquals(giaSuonato, vibratore.ordine.size)
        assertEquals(invia, invii(WearConstants.MSG_SCORE_INTENT))
    }

    // --- Il tick e la conferma non si fondono ---

    @Test
    fun `il tick suona subito e la conferma di lato non prima di 250ms dallo stesso tocco`() {
        viewModel.incrementScore(1)
        assestati()
        // Il telefono e' velocissimo: lo stato arriva nell'istante del tocco.
        viewModel.applyStateV2(stato(registro(1)))
        assestati()

        assertEquals(listOf(inizio), vibratore.ticks)
        assertTrue("la conferma aspetta il tick: ${vibrazioni()}", vibratore.suonati.isEmpty())

        avanza(WearViewModel.DISTANZA_DAL_TICK_MS - 1)
        assertTrue(vibratore.suonati.isEmpty())

        avanza(1)
        assertEquals(listOf(WearPatterns.CONFERMA_SINISTRA.toList()), vibrazioni())
        assertEquals(listOf("tick", "pattern"), vibratore.ordine)
        assertTrue(vibratore.suonatiAlle.single() - vibratore.ticks.single() >= WearViewModel.DISTANZA_DAL_TICK_MS)
    }

    @Test
    fun `una conferma che arriva dopo i 250ms suona subito, senza altra attesa`() {
        viewModel.incrementScore(2)
        assestati()
        avanza(400)

        viewModel.applyStateV2(stato(registro(1)))
        assestati()

        assertEquals(listOf(WearPatterns.CONFERMA_DESTRA.toList()), vibrazioni())
        assertEquals(inizio + 400, vibratore.suonatiAlle.single())
    }

    @Test
    fun `il pattern in coda aspetta il tick come la conferma`() {
        consegnato = false

        viewModel.incrementScore(1)
        assestati()
        assertTrue(vibratore.suonati.isEmpty())

        avanza(WearViewModel.DISTANZA_DAL_TICK_MS)
        assertEquals(listOf(WearPatterns.IN_CODA_SINISTRA.toList()), vibrazioni())
        assertEquals(listOf("tick", "pattern"), vibratore.ordine)
    }

    // --- Un tocco non confermato con uno stato che e' di un altro ---

    @Test
    fun `lo stato del primo tocco arrivato durante l'invio del secondo non conferma il secondo`() {
        viewModel.incrementScore(1)
        assestati()
        dopoIlTick()
        assertTrue(vibratore.suonati.isEmpty())

        // Lo stato che conferma il PRIMO entra mentre sendMessage del SECONDO e' ancora in volo.
        durantInvio = {
            durantInvio = null
            viewModel.applyStateV2(stato(registro(1)))
        }
        viewModel.incrementScore(2)
        assestati()
        dopoIlTick()

        // Suona il primo, e basta: il secondo aspetta il suo stato, non gli sta addosso quello di un altro.
        assertEquals(listOf(WearPatterns.CONFERMA_SINISTRA.toList()), vibrazioni())

        viewModel.applyStateV2(stato(registro(2)))
        assestati()
        dopoIlTick()
        assertEquals(listOf(WearPatterns.CONFERMA_SINISTRA.toList(), WearPatterns.CONFERMA_DESTRA.toList()), vibrazioni())

        avanza(10_000)
        assertEquals(2, vibrazioni().size)
    }

    @Test
    fun `il secondo tocco senza il suo stato scade da solo anche se un altro stato e' passato durante l'invio`() {
        viewModel.incrementScore(1)
        assestati()
        durantInvio = {
            durantInvio = null
            viewModel.applyStateV2(stato(registro(1)))
        }
        viewModel.incrementScore(2)
        assestati()
        dopoIlTick()
        assertEquals(1, vibrazioni().size)

        avanza(WearViewModel.SCADENZA_RICEVUTA_MS)

        assertEquals(WearPatterns.NON_CONFERMATO.toList(), vibrazioni().last())
        assertEquals(2, vibrazioni().size)
    }

    // --- Un arretrato in volo non e' la conferma di un tocco dal vivo ---

    /**
     * D6: l'ack e lo stato dell'arretrato viaggiano separati. Se l'ack arriva per primo la coda e'
     * vuota, il tocco dal vivo parte e apre la sua ricevuta, e poi arriva lo stato col blocco (L+3):
     * allunga il registro di tre voci, ma non e' la conferma del tocco.
     */
    @Test
    fun `lo stato dell'arretrato arrivato dopo l'ack non chiude la ricevuta di un tocco dal vivo segnato intanto`() {
        consegnato = false
        repeat(3) { viewModel.incrementScore(1) }
        assestati()
        consegnato = true
        viewModel.flushPending()
        assestati()
        val seqBatch = sequenzaDelBatch()
        val idBatch = idDelBatch()
        dopoIlTick()
        vibratore.suonati.clear()

        // L'ack per primo: l'arretrato e' confermato e la coda e' vuota.
        viewModel.onBatchAck(seqBatch, idBatch)
        // Un tocco dal vivo, ora che la coda e' vuota: parte e apre la sua ricevuta.
        viewModel.incrementScore(2)
        assestati()
        dopoIlTick()

        // Lo stato dell'arretrato (L+3): e' il suo, non quello del tocco.
        viewModel.applyStateV2(stato(registro(3), lastBatchId = idBatch))
        assestati()
        dopoIlTick()
        assertTrue("l'arretrato non e' una conferma: ${vibrazioni()}", vibratore.suonati.isEmpty())

        // Lo stato col tocco dal vivo (L+4) chiude la ricevuta.
        viewModel.applyStateV2(stato(registro(4), lastBatchId = idBatch))
        assestati()
        dopoIlTick()
        assertEquals(listOf(WearPatterns.CONFERMA_DESTRA.toList()), vibrazioni())
    }

    /** La sequenza del messaggio dell'arretrato, letta da quello che il telefono ha ricevuto. */
    private fun sequenzaDelBatch(): Long = ultimoBatch().getLong(WearConstants.KEY_SEQ)

    private fun idDelBatch(): Long = ultimoBatch().getLong(WearConstants.KEY_BATCH_ID)

    private fun ultimoBatch(): DataMap {
        val invio =
            Mockito
                .mockingDetails(telefono)
                .invocations
                .last { it.method.name == "sendMessage" && it.arguments[0] == WearConstants.MSG_INTENT_BATCH }
        return DataMap.fromByteArray(invio.arguments[1] as ByteArray)
    }

    // --- Un registro che manca non fa dire NON CONFERMATO ---

    @Test
    fun `un telefono v2 senza registro non apre ricevute, il tocco non dice NON CONFERMATO`() {
        viewModel.applyStateV2(stato(""))
        assestati()

        viewModel.incrementScore(2)
        assestati()
        avanza(10_000)

        // Come con un telefono v1: nessuna ricevuta, quindi nessuna conferma e nessuna smentita.
        assertTrue("vibrazioni: ${vibrazioni()}", vibratore.suonati.isEmpty())
        assertFalse(viewModel.statoFiducia.value is Transitorio)
        assertEquals(1, invii(WearConstants.MSG_SCORE_INTENT))
    }

    @Test
    fun `un registro illeggibile non apre ricevute`() {
        viewModel.applyStateV2(stato("9:zz"))
        assestati()

        viewModel.incrementScore(1)
        assestati()
        avanza(10_000)

        assertTrue("vibrazioni: ${vibrazioni()}", vibratore.suonati.isEmpty())
        assertFalse(viewModel.statoFiducia.value is Transitorio)
    }

    // --- CHIUSURA... non si cancella ---

    @Test
    fun `un tocco non confermato durante la chiusura vibra ma non sostituisce CHIUSURA`() {
        // Prima la chiusura: con una ricevuta aperta il polso non chiude (L4), quindi il tocco
        // non confermato e' uno dato DOPO, mentre CHIUSURA... aspetta.
        assertTrue(viewModel.chiudiPartita())
        assestati()
        assertEquals(Transitorio.Chiusura, viewModel.statoFiducia.value)
        viewModel.incrementScore(2)
        assestati()
        val giaSuonato = vibratore.suonati.size

        avanza(WearViewModel.SCADENZA_RICEVUTA_MS)

        // Il colpo lungo c'e' (il tocco non e' confermato), la scritta no: resta CHIUSURA...
        assertEquals(listOf(WearPatterns.NON_CONFERMATO.toList()), vibrazioni().drop(giaSuonato))
        assertEquals(Transitorio.Chiusura, viewModel.statoFiducia.value)
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
    fun `senza lo stato dello sport chiesto il cambio dice SPORT NON CAMBIATO`() {
        viewModel.requestSport("tennis")
        assestati()

        avanza(WearViewModel.SCADENZA_RICEVUTA_MS)

        assertEquals(listOf(listOf(0L, 400L)), vibrazioni())
        assertEquals(Transitorio.SportNonCambiato, viewModel.statoFiducia.value)

        // E' un messaggio, non uno stato: dopo 3s la riga torna com'era.
        avanza(StatoFiducia.DURATA_TRANSITORIO_MS)
        assertEquals(Frase.TieniAnnulla, viewModel.statoFiducia.value)
    }

    @Test
    fun `lo stato col nuovo sport, arrivato dopo la scadenza, toglie SPORT NON CAMBIATO`() {
        viewModel.requestSport("tennis")
        assestati()
        avanza(WearViewModel.SCADENZA_RICEVUTA_MS)
        assertEquals(Transitorio.SportNonCambiato, viewModel.statoFiducia.value)

        // Lo stato arriva in ritardo, entro i 3s del messaggio: la frase non e' piu' vera.
        viewModel.applyStateV2(stato(registro(0), sportId = "tennis"))
        assestati()

        assertEquals(Frase.TieniAnnulla, viewModel.statoFiducia.value)
    }

    @Test
    fun `la scelta dello sport dice CAMBIO SPORT finche' il quadrante non riceve lo stato nuovo`() {
        viewModel.requestSport("tennis")
        assestati()
        assertEquals(Transitorio.CambioSport, viewModel.statoFiducia.value)

        // Passa piu' del transitorio di sempre (3s) e meno della ricevuta: la riga non torna da sola.
        avanza(WearViewModel.SCADENZA_RICEVUTA_MS - 1)
        assertEquals(Transitorio.CambioSport, viewModel.statoFiducia.value)

        // Uno stato dello sport di prima non dice che il cambio e' fatto.
        viewModel.applyStateV2(stato(registro(0), sportId = "padel"))
        assestati()
        assertEquals(Transitorio.CambioSport, viewModel.statoFiducia.value)

        viewModel.applyStateV2(stato(registro(0), sportId = "tennis"))
        assestati()
        assertEquals(Frase.TieniAnnulla, viewModel.statoFiducia.value)

        // E la scadenza della ricevuta non la riporta indietro.
        avanza(10_000)
        assertEquals(Frase.TieniAnnulla, viewModel.statoFiducia.value)
    }

    @Test
    fun `lo stato col nuovo sport arrivato durante l'invio chiude CAMBIO SPORT`() {
        durantInvio = {
            durantInvio = null
            viewModel.applyStateV2(stato(registro(0), sportId = "tennis"))
        }

        viewModel.requestSport("tennis")
        assestati()

        assertEquals(Frase.TieniAnnulla, viewModel.statoFiducia.value)
        assertEquals(listOf(HapticFeedbackManager.PATTERN_CONFIRM.toList()), vibrazioni())
    }

    @Test
    fun `il cambio sport non consegnato vibra il colpo lungo e non apre nessuna ricevuta`() {
        consegnato = false

        viewModel.requestSport("tennis")
        assestati()
        assertEquals(listOf(listOf(0L, 400L)), vibrazioni())
        // Il messaggio non e' partito: la riga lo dice subito, senza aspettare una ricevuta.
        assertEquals(Transitorio.SportNonCambiato, viewModel.statoFiducia.value)

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
