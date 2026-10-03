package it.vantaggi.scoreboardessential.wear

import android.app.Application
import android.content.Context
import android.os.Vibrator
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import it.vantaggi.scoreboardessential.shared.communication.ConnectionState
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn

/**
 * Gli input della riga di stato, dal ViewModel: il tempo si sposta sullo scheduler di test, e
 * l'orologio del ViewModel e' lo stesso scheduler, quindi "da 10 secondi" non aspetta niente.
 *
 * La priorita' fra le frasi sta in [StatoFiduciaTest]: qui si prova che il ViewModel le dia i dati
 * giusti al momento giusto.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RigaStatoViewModelTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    @Mock
    private lateinit var application: Application

    @Mock
    private lateinit var vibrator: Vibrator

    @Mock
    private lateinit var packageManager: android.content.pm.PackageManager

    private lateinit var collegamento: MutableStateFlow<ConnectionState>
    private lateinit var telefono: OptimizedWearDataSync
    private lateinit var coda: PendingIntents
    private lateinit var viewModel: WearViewModel

    /** Un'epoca vera: l'ora dell'ultimo dato vivo, a zero, si leggerebbe come "mai". */
    private val inizio = 1_700_000_000_000L

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

        // Il collegamento lo decide il test: si sposta il flusso, come farebbe il listener.
        collegamento = MutableStateFlow(ConnectionState.Disconnected)
        telefono = Mockito.mock(OptimizedWearDataSync::class.java)
        Mockito.`when`(telefono.connectionState).thenReturn(collegamento)
        // La chiusura dal polso e' un messaggio: il finto lo consegna, senza un nullo da spacchettare.
        runBlocking { Mockito.`when`(telefono.sendMessage(Mockito.anyString(), Mockito.any())).thenReturn(true) }
        viewModel = nuovoViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun nuovoViewModel() = WearViewModel(application, telefono, orologio = { inizio + testDispatcher.scheduler.currentTime })

    private fun avanza(millisecondi: Long) {
        testDispatcher.scheduler.advanceTimeBy(millisecondi)
        testDispatcher.scheduler.runCurrent()
    }

    private fun assestati() = testDispatcher.scheduler.runCurrent()

    private fun frase() = viewModel.statoFiducia.value

    private fun mettiInCoda(quanti: Int) {
        repeat(quanti) { coda.add(PendingIntent("point", 1, inizio + it)) }
        viewModel.refreshPendingCount()
        assestati()
    }

    private fun stato(
        finita: Boolean = false,
        inCorso: Boolean = true,
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
        sportLabel = "Padel",
        sportIds = emptyList(),
        sportLabels = emptyList(),
        matchInProgress = inCorso,
        matchOver = finita,
        eventLog = "",
    )

    private fun collegati() {
        collegamento.value = ConnectionState.Connected(1)
        assestati()
    }

    @Test
    fun `all'avvio la riga non dice SCOLLEGATO per i primi 2 secondi`() {
        assestati()
        // Il collegamento vale Disconnected finche' non arriva la risposta: e' il lampeggio che
        // la verifica toglie.
        assertEquals(Frase.TieniMeno, frase())

        avanza(1_999)
        assertEquals(Frase.TieniMeno, frase())

        avanza(1)
        assertEquals(Frase.Scollegato(null), frase())
    }

    @Test
    fun `la verifica finisce alla risposta, senza aspettare i 2 secondi`() {
        assestati()

        // Il telefono risponde subito (il finto ritorna all'istante): "scollegato" si dice adesso.
        viewModel.refreshConnection()
        assestati()

        assertEquals(Frase.Scollegato(null), frase())
    }

    @Test
    fun `da collegati la coda e' INVIO e dopo 10 secondi NON CONSEGNATI`() {
        collegati()
        mettiInCoda(2)
        assertEquals(Frase.Invio(2), frase())

        avanza(9_999)
        assertEquals(Frase.Invio(2), frase())

        // Nessun evento in questo istante: a cambiare la riga e' la scadenza che il ViewModel si e' dato.
        avanza(1)
        assertEquals(Frase.NonConsegnati(2), frase())
    }

    @Test
    fun `il conto dei 10 secondi parte dal collegamento e non dal primo tocco`() {
        avanza(StatoFiducia.DURATA_VERIFICA_MS)
        mettiInCoda(1)
        avanza(60_000)
        assertEquals(Frase.InCoda(1), frase())

        collegati()
        // Un minuto scollegati non contano: per la riga, il telefono e' tornato adesso.
        assertEquals(Frase.Invio(1), frase())

        avanza(10_000)
        assertEquals(Frase.NonConsegnati(1), frase())
    }

    @Test
    fun `se il telefono riparte il conto si azzera`() {
        collegati()
        mettiInCoda(1)
        avanza(8_000)

        collegamento.value = ConnectionState.Disconnected
        avanza(StatoFiducia.DURATA_VERIFICA_MS)
        assertEquals(Frase.InCoda(1), frase())

        collegati()
        avanza(9_999)
        assertEquals(Frase.Invio(1), frase())
    }

    @Test
    fun `l'ultimo stato vivo da' l'ora di SCOLLEGATO, la rilettura no`() {
        collegati()
        avanza(5_000)
        viewModel.applyStateV2(stato())
        val vivo = inizio + 5_000

        avanza(5_000)
        // Un v2 riletto al risveglio e' una copia: non e' il momento in cui il telefono ha parlato.
        viewModel.applyStateV2(stato(), dalVivo = false)

        collegamento.value = ConnectionState.Disconnected
        avanza(StatoFiducia.DURATA_VERIFICA_MS)
        assertEquals(Frase.Scollegato(vivo), frase())
    }

    @Test
    fun `l'ora dell'ultimo stato vivo sopravvive al riavvio`() {
        collegati()
        avanza(5_000)
        viewModel.applyStateV2(stato())
        val vivo = inizio + 5_000

        // L'ora sul disco la scrive WearDataLayerService, non il ViewModel: qui ne fa le veci.
        LastKnownMatch(RuntimeEnvironment.getApplication()).segnaStatoVivo(vivo)

        // Orologio riavviato col telefono in borsa: un ViewModel nuovo, la stessa memoria su disco.
        avanza(60_000)
        collegamento.value = ConnectionState.Disconnected
        viewModel = nuovoViewModel()
        viewModel.refreshPendingCount()
        avanza(StatoFiducia.DURATA_VERIFICA_MS)

        assertEquals(Frase.Scollegato(vivo), frase())
    }

    @Test
    fun `senza nessuno stato vivo SCOLLEGATO non ha l'ora`() {
        avanza(StatoFiducia.DURATA_VERIFICA_MS)

        assertEquals(Frase.Scollegato(null), frase())
    }

    @Test
    fun `un messaggio transitorio dura 3 secondi e poi la riga torna sola`() {
        collegati()
        viewModel.mostraTransitorio(Transitorio.ChiusuraNonConfermata)
        assertEquals(Transitorio.ChiusuraNonConfermata, frase())

        avanza(StatoFiducia.DURATA_TRANSITORIO_MS - 1)
        assertEquals(Transitorio.ChiusuraNonConfermata, frase())

        avanza(1)
        assertEquals(Frase.TieniMeno, frase())
    }

    @Test
    fun `chiudere dal polso dice CHIUSURA fino al v2 a partita non cominciata`() {
        collegati()
        viewModel.applyStateV2(stato())
        viewModel.chiudiPartita()
        assestati()
        assertEquals(Transitorio.Chiusura, frase())

        // Piu' dei 3 secondi di un transitorio qualunque: la riga aspetta il telefono, non il tempo.
        avanza(StatoFiducia.DURATA_TRANSITORIO_MS + 1)
        assertEquals(Transitorio.Chiusura, frase())

        viewModel.applyStateV2(stato(inCorso = false))
        assestati()
        assertEquals(Frase.TieniAnnulla, frase())

        // E NON CONFERMATA non scatta piu' a risposta arrivata.
        avanza(WearViewModel.DURATA_ATTESA_CHIUSURA_MS)
        assertEquals(Frase.TieniAnnulla, frase())
    }

    @Test
    fun `senza risposta in 10 secondi CHIUSURA diventa NON CONFERMATA, poi la riga torna sola`() {
        collegati()
        viewModel.applyStateV2(stato())
        viewModel.chiudiPartita()

        avanza(WearViewModel.DURATA_ATTESA_CHIUSURA_MS - 1)
        assertEquals(Transitorio.Chiusura, frase())

        avanza(1)
        assertEquals(Transitorio.ChiusuraNonConfermata, frase())

        avanza(StatoFiducia.DURATA_TRANSITORIO_MS)
        assertEquals(Frase.TieniAnnulla, frase())
    }

    @Test
    fun `un v2 a partita ancora cominciata non chiude CHIUSURA`() {
        collegati()
        viewModel.applyStateV2(stato())
        viewModel.chiudiPartita()

        // Un v2 vecchio, arrivato prima che il telefono abbia applicato la chiusura.
        viewModel.applyStateV2(stato(inCorso = true))
        assestati()

        assertEquals(Transitorio.Chiusura, frase())
    }

    @Test
    fun `un v2 a partita non cominciata che c'era gia' prima del comando non e' la conferma`() {
        collegati()
        // Il registro era vuoto al comando: un v2 vuoto non dice niente sulla chiusura.
        viewModel.applyStateV2(stato(inCorso = false))
        viewModel.chiudiPartita()

        viewModel.applyStateV2(stato(inCorso = false))
        assestati()
        assertEquals(Transitorio.Chiusura, frase())

        avanza(WearViewModel.DURATA_ATTESA_CHIUSURA_MS)
        assertEquals(Transitorio.ChiusuraNonConfermata, frase())
    }

    @Test
    fun `la conferma di chiusura toglie solo CHIUSURA, non un altro messaggio`() {
        collegati()
        viewModel.applyStateV2(stato())
        viewModel.chiudiPartita()
        // Un tocco senza ricevuta, mentre si aspetta il telefono: il messaggio e' un altro.
        viewModel.mostraTransitorio(Transitorio.NonConfermato)

        viewModel.applyStateV2(stato(inCorso = false))
        assestati()

        assertEquals(Transitorio.NonConfermato, frase())
    }

    private fun invii(
        path: String,
        urgente: Boolean,
    ) = Mockito
        .mockingDetails(telefono)
        .invocations
        .count { it.method.name == "sendData" && it.arguments[0] == path && it.arguments[2] == urgente }

    @Test
    fun `senza v2 chiudere dal polso manda il MATCH_STATE urgente, il reset normale no`() {
        // Un telefono che non parla v2: resta il v1 di sempre. Col v2 la chiusura e' un'intenzione
        // e nessun MATCH_STATE parte (FinePartitaDalPolsoTest).
        collegati()

        viewModel.resetMatch()
        assestati()
        // Il reset di sempre resta non urgente: il protocollo non cambia.
        assertEquals(1, invii(WearConstants.PATH_MATCH_STATE, urgente = false))
        assertEquals(0, invii(WearConstants.PATH_MATCH_STATE, urgente = true))

        viewModel.chiudiPartita()
        assestati()
        assertEquals(1, invii(WearConstants.PATH_MATCH_STATE, urgente = true))
    }

    @Test
    fun `la conferma del telefono svuota la coda e dice CONSEGNATI per qualche secondo`() {
        collegati()
        mettiInCoda(2)
        viewModel.applyStateV2(stato())
        // Come dopo flushPending: l'arretrato e' in viaggio con la sua sequenza.
        val campo = WearViewModel::class.java.getDeclaredField("batchInVolo")
        campo.isAccessible = true
        campo.set(viewModel, 7L to 2)

        viewModel.onBatchAck(7L)
        assestati()

        assertEquals(Transitorio.Consegnati(2), frase())
        avanza(StatoFiducia.DURATA_TRANSITORIO_MS)
        assertEquals(Frase.TieniAnnulla, frase())
    }

    @Test
    fun `partita finita senza anomalie la riga dice PARTITA FINITA`() {
        collegati()
        viewModel.applyStateV2(stato(finita = true))
        assestati()

        assertEquals(Frase.PartitaFinita, frase())
    }

    @Test
    fun `il controllo periodico chiede il collegamento solo a partita in corso`() {
        // Nessuno stato e niente in coda: nessuna partita, niente da controllare.
        viewModel.refreshConnectionSePartitaInCorso()
        assestati()
        verificheRichieste(0)

        viewModel.applyStateV2(stato(finita = true))
        viewModel.refreshConnectionSePartitaInCorso()
        assestati()
        verificheRichieste(0)

        viewModel.applyStateV2(stato(finita = false))
        viewModel.refreshConnectionSePartitaInCorso()
        assestati()
        verificheRichieste(1)
    }

    /**
     * Il Data Layer che non risponde finche' il test non lo dice: la richiesta resta sospesa, ma
     * annullabile, come quella vera (che e' sospesa sul Task di GMS).
     */
    private fun dataLayerSospeso(): CompletableDeferred<Unit> {
        val risposta = CompletableDeferred<Unit>()
        runBlocking {
            Mockito
                .doAnswer { invocazione ->
                    @Suppress("UNCHECKED_CAST")
                    val continuazione = invocazione.rawArguments.last() as Continuation<Unit>
                    val attesa: suspend () -> Unit = { risposta.await() }
                    attesa.startCoroutineUninterceptedOrReturn(continuazione)
                }.`when`(telefono)
                .refreshConnection()
        }
        return risposta
    }

    @Test
    fun `a collegamento gia' noto il rinfresco non fa sparire SCOLLEGATO ne' IN CODA`() {
        // Il primo rinfresco risponde (il finto ritorna all'istante): da qui il collegamento e' noto.
        viewModel.refreshConnection()
        assestati()
        assertEquals(Frase.Scollegato(null), frase())

        dataLayerSospeso()
        viewModel.refreshConnection()
        assestati()
        // La richiesta e' in volo: la riga non cede il posto al suggerimento del gesto.
        assertEquals(Frase.Scollegato(null), frase())

        mettiInCoda(1)
        assertEquals(Frase.InCoda(1), frase())

        avanza(StatoFiducia.DURATA_VERIFICA_MS - 1)
        assertEquals(Frase.InCoda(1), frase())
    }

    @Test
    fun `una richiesta alla volta, e dopo il timeout ne parte un'altra`() {
        dataLayerSospeso()

        viewModel.refreshConnection()
        viewModel.refreshConnection()
        assestati()
        // La seconda non parte: la prima e' ancora in volo.
        verificheRichieste(1)

        // Il Data Layer non risponde mai: la richiesta si da' per persa poco sopra i 2s.
        avanza(WearViewModel.TIMEOUT_RICHIESTA_MS)
        viewModel.refreshConnection()
        assestati()
        verificheRichieste(2)
    }

    @Test
    fun `una richiesta scaduta chiude la verifica, e il giro dei 15 secondi non nasconde piu' nulla`() {
        dataLayerSospeso()
        viewModel.refreshConnection()
        avanza(WearViewModel.TIMEOUT_RICHIESTA_MS)
        assertEquals(Frase.Scollegato(null), frase())

        // Il collegamento e' ormai deciso, anche se come "scollegato": il rinfresco non lo nasconde.
        viewModel.refreshConnection()
        assestati()
        assertEquals(Frase.Scollegato(null), frase())
    }

    private fun verificheRichieste(quante: Int) {
        runBlocking { Mockito.verify(telefono, Mockito.times(quante)).refreshConnection() }
    }
}
