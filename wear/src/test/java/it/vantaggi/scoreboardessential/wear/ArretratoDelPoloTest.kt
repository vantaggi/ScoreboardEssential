package it.vantaggi.scoreboardessential.wear

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.MessageEvent
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.ScoringEvent
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.mockito.stubbing.Answer
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Il vibratore che non fa niente: qui interessa la coda, non cio' che si sente al polso. */
private object VibratoreDelArretrato : WearHaptics {
    override fun suona(pattern: LongArray) = Unit

    override fun annulla() = Unit

    override fun tick() = Unit
}

/**
 * L5: l'arretrato dell'orologio con identita' e base. Un test per ogni rilievo alto, e ognuno
 * cade se si toglie la correzione (verificato per falsificazione, vedi l'esito del lotto).
 *
 * Il telefono e' un finto che risponde sempre allo stesso modo all'invio ([consegnato]): la
 * risposta del telefono (ack, NACK, stato col suo id) la dice il test chiamando i metodi del
 * ViewModel, come farebbe la schermata. Il tempo e' quello dello scheduler di test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ArretratoDelPoloTest {
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
    private var consegnato = true
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
        telefono =
            Mockito.mock(
                OptimizedWearDataSync::class.java,
                Answer { invocazione ->
                    if (invocazione.method.name == "sendMessage") consegnato else Mockito.RETURNS_DEFAULTS.answer(invocazione)
                },
            )
        Mockito.`when`(telefono.connectionState).thenReturn(MutableStateFlow(ConnectionState.Connected(1)))
        viewModel = nuovoViewModel()
        viewModel.applyStateV2(stato(registro(0)))
        assestati()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * LocalBroadcastManager e' un singleton di processo: un ricevitore lasciato da un altro test
     * (una Activity non distrutta) farebbe sembrare che "qualcuno ascolta" anche qui. Il servizio
     * decide dal suo valore di ritorno, quindi il test parte da nessun ricevitore.
     */
    private fun senzaRicevitori() {
        val manager = LocalBroadcastManager.getInstance(RuntimeEnvironment.getApplication())
        val azioni = LocalBroadcastManager::class.java.getDeclaredField("mActions")
        azioni.isAccessible = true
        (azioni.get(manager) as MutableMap<*, *>).clear()
    }

    /** Un ViewModel sullo stesso disco: e' quello che si ritrova dopo la morte del processo. */
    private fun nuovoViewModel() =
        WearViewModel(
            application,
            telefono,
            orologio = { inizio + testDispatcher.scheduler.currentTime },
            haptics = VibratoreDelArretrato,
        )

    private fun registro(eventi: Int): String = MatchLogCodec.encode(List(eventi) { LoggedEvent(ScoringEvent.Point(side = 1)) })

    private fun stato(
        registro: String,
        lastBatchId: Long = 0L,
        matchUuid: String = "",
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
        sportId = "padel",
        sportLabel = "padel",
        sportIds = emptyList(),
        sportLabels = emptyList(),
        matchInProgress = true,
        matchOver = false,
        eventLog = registro,
        lastBatchId = lastBatchId,
        matchUuid = matchUuid,
    )

    private fun avanza(millisecondi: Long) {
        testDispatcher.scheduler.advanceTimeBy(millisecondi)
        testDispatcher.scheduler.runCurrent()
    }

    private fun assestati() = testDispatcher.scheduler.runCurrent()

    /** Tre tocchi segnati mentre il telefono non c'era: in coda, con la base del registro di allora. */
    private fun segnaOffline(tocchi: Int = 3) {
        consegnato = false
        repeat(tocchi) { viewModel.incrementScore(1) }
        assestati()
        consegnato = true
    }

    private fun batchSpediti(): List<DataMap> =
        Mockito
            .mockingDetails(telefono)
            .invocations
            .filter { it.method.name == "sendMessage" && it.arguments[0] == WearConstants.MSG_INTENT_BATCH }
            .map { DataMap.fromByteArray(it.arguments[1] as ByteArray) }

    private fun idDi(batch: DataMap) = batch.getLong(WearConstants.KEY_BATCH_ID)

    // --- Rilievo 1: il batch porta la base su cui il polso ha calcolato ---

    @Test
    fun `il batch porta la base del registro che il polso vedeva quando la coda e' nata`() {
        viewModel.applyStateV2(stato(registro(3)))
        segnaOffline(2)

        viewModel.flushPending()
        assestati()

        val batch = batchSpediti().single()
        assertEquals(MatchLogCodec.impronta(registro(3)), batch.getString(WearConstants.KEY_BATCH_BASE))
        assertTrue("l'id c'e'", idDi(batch) > 0L)
    }

    @Test
    fun `senza uno stato del telefono la base e' il registro vuoto`() {
        viewModel = nuovoViewModel()
        segnaOffline(1)

        viewModel.flushPending()
        assestati()

        assertEquals("0", batchSpediti().single().getString(WearConstants.KEY_BATCH_BASE))
    }

    @Test
    fun `la base resta quella in cui la coda e' nata anche se il telefono passa a un'altra partita`() {
        viewModel.applyStateV2(stato(registro(3)))
        segnaOffline(1)
        // Il telefono ha chiuso e ha cominciato una partita nuova: lo stato arriva col registro vuoto.
        viewModel.applyStateV2(stato(registro(0)))
        assestati()

        viewModel.flushPending()
        assestati()

        // Dire "0" sarebbe applicare in silenzio i tocchi di una partita alla successiva (rilievo 4).
        assertEquals(MatchLogCodec.impronta(registro(3)), batchSpediti().single().getString(WearConstants.KEY_BATCH_BASE))
    }

    /** D1: la base e' anche l'identita' della partita, quella che il polso vedeva quando la coda e' nata. */
    @Test
    fun `il batch porta l'identita' della partita su cui la coda e' nata`() {
        viewModel.applyStateV2(stato(registro(3), matchUuid = "partita-A"))
        segnaOffline(1)
        // Il telefono passa a un'altra partita: l'identita' del batch resta quella di prima.
        viewModel.applyStateV2(stato(registro(1), matchUuid = "partita-B"))
        assestati()

        viewModel.flushPending()
        assestati()

        assertEquals("partita-A", batchSpediti().single().getString(WearConstants.KEY_MATCH_UUID))
    }

    @Test
    fun `lo stato del telefono porta l'identita' della partita e un telefono vecchio la lascia vuota`() {
        val nuovo = DataMap().apply { putString(WearConstants.KEY_MATCH_UUID, "partita-A") }
        assertEquals("partita-A", WearScoreState.fromDataMap(nuovo).matchUuid)
        assertEquals("", WearScoreState.fromDataMap(DataMap()).matchUuid)
    }

    // --- Rilievo 2: un arretrato mai confermato non blocca l'orologio ---

    @Test
    fun `un arretrato in volo congela il quadrante solo fino alla scadenza`() {
        segnaOffline(1)
        viewModel.flushPending()
        assestati()

        // Lo stato del telefono arriva mentre l'arretrato e' in volo: non si ridisegna.
        viewModel.applyStateV2(stato(registro(2)))
        assertEquals("in volo il quadrante resta com'e'", "15", viewModel.scoreState.value?.side1Primary)

        avanza(WearViewModel.TIMEOUT_BATCH_MS)
        // Scaduto: un punto del telefono e uno in coda, 30. Prima lo schermo restava com'era finche'
        // il ViewModel viveva, qualunque stato arrivasse.
        viewModel.applyStateV2(stato(registro(1)))

        assertEquals("30", viewModel.scoreState.value?.side1Primary)
    }

    @Test
    fun `il passaggio a Connected chiude il tentativo senza risposta e il blocco riparte con lo stesso id`() {
        segnaOffline(2)
        viewModel.flushPending()
        assestati()
        viewModel.flushPending()
        assestati()
        assertEquals("un tentativo in volo non se ne fa un secondo", 1, batchSpediti().size)

        viewModel.flushPending(collegatoDiNuovo = true)
        assestati()

        val inviati = batchSpediti()
        assertEquals(2, inviati.size)
        assertEquals("stesso blocco, stessa identita'", idDi(inviati[0]), idDi(inviati[1]))
        assertNotEquals(
            "la sequenza e' nuova, o il servizio lo scarterebbe",
            inviati[0].getLong(WearConstants.KEY_SEQ),
            inviati[1].getLong(WearConstants.KEY_SEQ),
        )
    }

    @Test
    fun `un NACK passeggero libera il blocco, che si rimanda con lo stesso id e le stesse voci`() {
        segnaOffline(2)
        viewModel.flushPending()
        assestati()
        val primo = batchSpediti().single()
        // Un tocco segnato mentre l'arretrato e' in volo: va nel blocco dopo, non in questo.
        segnaOffline(1)

        viewModel.onBatchNack(idDi(primo), WearConstants.NACK_RETRY)
        viewModel.flushPending()
        assestati()

        val secondo = batchSpediti()[1]
        assertEquals(idDi(primo), idDi(secondo))
        assertEquals(
            "il rinvio porta le prime due voci, non la terza",
            2,
            secondo
                .getString(WearConstants.KEY_INTENT_BATCH)
                .orEmpty()
                .split(WearConstants.BATCH_SEPARATOR)
                .size,
        )
        assertFalse("un NACK passeggero non e' un rifiuto", viewModel.rifiutati.value > 0)
    }

    // --- Rilievo 3: ack perso ---

    @Test
    fun `l'identita' del blocco sopravvive alla morte del processo`() {
        segnaOffline(2)
        viewModel.flushPending()
        assestati()
        val primo = batchSpediti().single()

        // Il processo muore: un ViewModel nuovo rilegge coda e identita' dal disco.
        viewModel = nuovoViewModel()
        viewModel.refreshPendingCount()
        viewModel.flushPending(collegatoDiNuovo = true)
        assestati()

        val rinvio = batchSpediti()[1]
        assertEquals(idDi(primo), idDi(rinvio))
        assertEquals(primo.getString(WearConstants.KEY_INTENT_BATCH), rinvio.getString(WearConstants.KEY_INTENT_BATCH))
    }

    @Test
    fun `lo stato col suo id toglie le voci dalla coda anche se l'ack si e' perso`() {
        segnaOffline(3)
        viewModel.flushPending()
        assestati()
        val id = idDi(batchSpediti().single())

        // L'ack non arriva mai. Arriva lo stato che contiene l'arretrato e porta il suo id.
        viewModel.applyStateV2(stato(registro(3), lastBatchId = id))
        assestati()

        assertEquals("la coda sul disco e' vuota", 0, coda.size)
        assertEquals(0, viewModel.pendingCount.value)
        // Niente doppio conteggio: il quadrante mostra lo stato del telefono (3 punti), non 3+3.
        assertEquals(registro(3), viewModel.scoreState.value?.eventLog)
        assertEquals(null, coda.batchInVolo())
    }

    @Test
    fun `un ack con l'id di un tentativo precedente chiude comunque il blocco`() {
        segnaOffline(2)
        viewModel.flushPending()
        assestati()
        val id = idDi(batchSpediti().single())
        viewModel.flushPending(collegatoDiNuovo = true)
        assestati()

        // L'ack del PRIMO tentativo arriva dopo il rinvio: la sua sequenza non e' quella dell'ultimo.
        viewModel.onBatchAck(seq = 1L, batchId = id)

        assertEquals(0, coda.size)
        assertEquals(0, viewModel.pendingCount.value)
    }

    @Test
    fun `l'ack di un telefono non aggiornato porta solo la sequenza e vale ancora`() {
        segnaOffline(2)
        viewModel.flushPending()
        assestati()
        val seq = batchSpediti().single().getLong(WearConstants.KEY_SEQ)

        viewModel.onBatchAck(seq)

        assertEquals(0, coda.size)
    }

    @Test
    fun `il servizio dell'orologio toglie il blocco dal disco anche senza Activity`() {
        segnaOffline(2)
        viewModel.flushPending()
        assestati()
        val id = idDi(batchSpediti().single())
        assertEquals(2, coda.size)

        // Nessun ricevitore registrato: la schermata e' chiusa. Il servizio e' tutto cio' che c'e'.
        senzaRicevitori()
        val servizio = Robolectric.buildService(WearDataLayerService::class.java).get()
        servizio.onMessageReceived(
            messaggio(
                WearConstants.MSG_BATCH_ACK,
                DataMap().apply {
                    putLong(WearConstants.KEY_SEQ, 1L)
                    putLong(WearConstants.KEY_BATCH_ID, id)
                },
            ),
        )

        assertEquals("senza Activity la coda esce lo stesso", 0, coda.size)
        assertEquals(null, coda.batchInVolo())
    }

    @Test
    fun `con una Activity in ascolto il servizio non tocca il disco, ci pensa il ViewModel`() {
        segnaOffline(2)
        viewModel.flushPending()
        assestati()
        val id = idDi(batchSpediti().single())
        val ricevuti = mutableListOf<Intent>()
        senzaRicevitori()
        val manager = LocalBroadcastManager.getInstance(RuntimeEnvironment.getApplication())
        val ricevitore =
            object : android.content.BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    ricevuti += intent
                }
            }
        manager.registerReceiver(ricevitore, IntentFilter(WearDataLayerService.ACTION_BATCH_ACK))
        try {
            val servizio = Robolectric.buildService(WearDataLayerService::class.java).get()
            servizio.onMessageReceived(
                messaggio(
                    WearConstants.MSG_BATCH_ACK,
                    DataMap().apply {
                        putLong(WearConstants.KEY_SEQ, 1L)
                        putLong(WearConstants.KEY_BATCH_ID, id)
                    },
                ),
            )
            org.robolectric.Shadows
                .shadowOf(android.os.Looper.getMainLooper())
                .idle()
        } finally {
            manager.unregisterReceiver(ricevitore)
        }

        assertEquals(1, ricevuti.size)
        assertEquals(id, ricevuti[0].getLongExtra(WearConstants.KEY_BATCH_ID, 0L))
        assertEquals("a toglierle e' chi ascolta", 2, coda.size)
    }

    private fun messaggio(
        path: String,
        dati: DataMap,
    ): MessageEvent {
        val evento = Mockito.mock(MessageEvent::class.java)
        Mockito.`when`(evento.path).thenReturn(path)
        Mockito.`when`(evento.data).thenReturn(dati.toByteArray())
        return evento
    }

    // --- Rilievo 4: una coda rifiutata non si applica mai alla partita dopo ---

    @Test
    fun `un NACK definitivo ferma la coda, la dice e non la rimanda piu'`() {
        viewModel.applyStateV2(stato(registro(3)))
        segnaOffline(2)
        viewModel.flushPending()
        assestati()
        val id = idDi(batchSpediti().single())

        viewModel.onBatchNack(id, WearConstants.NACK_REJECTED)
        assestati()

        assertEquals(2, viewModel.rifiutati.value)
        assertEquals(Frase.Rifiutati(2), viewModel.statoFiducia.value)
        assertEquals("le voci non si perdono da sole: stanno da parte", 2, coda.rifiutateSize)
        assertEquals("la coda viva e' vuota", 0, coda.size)
        // Ne' al collegamento successivo ne' dopo la morte del processo.
        viewModel.flushPending(collegatoDiNuovo = true)
        viewModel = nuovoViewModel()
        viewModel.refreshPendingCount()
        viewModel.flushPending(collegatoDiNuovo = true)
        assestati()
        assertEquals("nessun rinvio", 1, batchSpediti().size)
        assertEquals("anche un ViewModel nuovo le dice rifiutate", 2, viewModel.rifiutati.value)
    }

    /** D2: dopo un rifiuto i tocchi nuovi non finiscono fra i rifiutati: sono una coda nuova, spedibile. */
    @Test
    fun `dopo un NACK definitivo i tocchi nuovi aprono una coda nuova che si calcola e si spedisce`() {
        viewModel.applyStateV2(stato(registro(3), matchUuid = "partita-A"))
        segnaOffline(2)
        viewModel.flushPending()
        assestati()
        val primo = batchSpediti().single()
        viewModel.onBatchNack(idDi(primo), WearConstants.NACK_REJECTED)
        assestati()
        // Il telefono e' su un'altra partita: stato con registro vuoto.
        viewModel.applyStateV2(stato(registro(0), matchUuid = "partita-B"))
        assestati()

        segnaOffline(3)

        assertEquals("tre tocchi nuovi in coda", 3, viewModel.pendingCount.value)
        assertEquals("le rifiutate restano due, separate", 2, viewModel.rifiutati.value)
        assertEquals(2, coda.rifiutateSize)
        assertEquals("il quadrante mostra telefono piu' coda nuova", "40", viewModel.scoreState.value?.side1Primary)

        viewModel.flushPending()
        assestati()

        val nuovo = batchSpediti()[1]
        assertNotEquals("un blocco nuovo, con un'identita' nuova", idDi(primo), idDi(nuovo))
        assertEquals("0", nuovo.getString(WearConstants.KEY_BATCH_BASE))
        assertEquals("partita-B", nuovo.getString(WearConstants.KEY_MATCH_UUID))
        assertEquals(
            "solo i tre nuovi, non le rifiutate",
            3,
            nuovo
                .getString(WearConstants.KEY_INTENT_BATCH)
                .orEmpty()
                .split(WearConstants.BATCH_SEPARATOR)
                .size,
        )
    }

    /** D2: SCARTA butta solo le rifiutate, la coda nuova resta e si spedisce. */
    @Test
    fun `scartare dopo un rifiuto butta solo le rifiutate e lascia la coda nuova`() {
        segnaOffline(2)
        viewModel.flushPending()
        assestati()
        viewModel.onBatchNack(idDi(batchSpediti().single()), WearConstants.NACK_REJECTED)
        segnaOffline(3)

        viewModel.scartaCoda()
        assestati()

        assertEquals(0, viewModel.rifiutati.value)
        assertEquals(0, coda.rifiutateSize)
        assertEquals("la coda nuova non si tocca", 3, coda.size)
        assertEquals(3, viewModel.pendingCount.value)
    }

    @Test
    fun `con la coda rifiutata il quadrante torna a mostrare solo il telefono`() {
        viewModel.applyStateV2(stato(registro(2)))
        segnaOffline(1)
        assertEquals("col conto locale: 2 punti del telefono e uno in coda", "40", viewModel.scoreState.value?.side1Primary)
        viewModel.flushPending()
        assestati()

        viewModel.onBatchNack(idDi(batchSpediti().single()), WearConstants.NACK_REJECTED)
        assestati()
        viewModel.applyStateV2(stato(registro(2)))

        assertEquals("i tocchi rifiutati non si sommano a un'altra partita", "0", viewModel.scoreState.value?.side1Primary)
    }

    @Test
    fun `scartare la coda rifiutata la butta, e solo quella`() {
        segnaOffline(2)
        viewModel.flushPending()
        assestati()

        // Non rifiutata: scartare farebbe perdere punti che il telefono potrebbe ancora accettare.
        viewModel.scartaCoda()
        assertEquals(2, coda.size)

        viewModel.onBatchNack(idDi(batchSpediti().single()), WearConstants.NACK_REJECTED)
        viewModel.scartaCoda()
        assestati()

        assertEquals(0, coda.size)
        assertEquals(0, viewModel.pendingCount.value)
        assertEquals(0, viewModel.rifiutati.value)
        assertEquals(null, coda.batchInVolo())
        // E la partita dopo riparte pulita: niente id vecchio, niente base vecchia.
        segnaOffline(1)
        viewModel.flushPending()
        assestati()
        assertNotEquals(idDi(batchSpediti()[0]), idDi(batchSpediti()[1]))
    }

    @Test
    fun `un arretrato calcolato su un'altra partita non parte con la base della partita nuova`() {
        viewModel.applyStateV2(stato(registro(3)))
        segnaOffline(2)
        // Il telefono chiude e ne comincia un'altra: lo stato arriva a registro vuoto.
        viewModel.applyStateV2(stato(registro(0)))
        assestati()

        viewModel.flushPending(collegatoDiNuovo = true)
        assestati()

        assertNotEquals(
            "con la base nuova il telefono applicherebbe a una partita che non e' la sua",
            "0",
            batchSpediti().single().getString(WearConstants.KEY_BATCH_BASE),
        )
    }
}
