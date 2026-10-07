package it.vantaggi.scoreboardessential.wear

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataItemBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.shared.communication.ConnectionState
import it.vantaggi.scoreboardessential.shared.communication.NodoLocale
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import it.vantaggi.scoreboardessential.wear.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.time.Duration

/**
 * Il quadrante vero, con i suoi collector: i difetti di L7 stavano nel passaggio dal ViewModel
 * alla vista, che i test del solo ViewModel non vedono.
 *
 * Si arriva fino a STARTED e non oltre: i collector partono li' (repeatOnLifecycle), mentre
 * onResume rilegge i DataItem con il client GMS vero, che sotto Robolectric non vive. Il
 * ViewModel si mette nello store PRIMA di onCreate, con i client finti, per la stessa ragione.
 */
@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var viewModel: WearViewModel
    private lateinit var binding: ActivityMainBinding
    private lateinit var collegamento: MutableStateFlow<ConnectionState>
    private lateinit var telefono: OptimizedWearDataSync

    @Before
    fun setup() {
        val app = RuntimeEnvironment.getApplication()
        listOf("wear_pending_intents", "wear_last_known_match").forEach { nome ->
            app
                .getSharedPreferences(nome, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
        // Il collegamento lo decide il test, come farebbe il listener della capability. L'orologio
        // del ViewModel e' quello di Robolectric, che si sposta con idleFor: niente attese vere.
        collegamento = MutableStateFlow(ConnectionState.Disconnected)
        telefono = Mockito.mock(OptimizedWearDataSync::class.java)
        Mockito.`when`(telefono.connectionState).thenReturn(collegamento)
        // La chiusura dal polso e' un messaggio: il finto lo consegna, senza un nullo da spacchettare.
        runBlocking { Mockito.`when`(telefono.sendMessage(Mockito.anyString(), Mockito.any())).thenReturn(true) }
        viewModel = WearViewModel(app, telefono, orologio = { SystemClock.uptimeMillis() + 1_000_000L })
        controller = Robolectric.buildActivity(MainActivity::class.java)
        val fabbrica =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
            }
        ViewModelProvider(controller.get(), fabbrica)[WearViewModel::class.java]
        controller.create().start()
        idle()
        val contenuto = controller.get().findViewById<ViewGroup>(android.R.id.content)
        binding = ActivityMainBinding.bind(contenuto.getChildAt(0))
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun stato(
        hasClock: Boolean,
        primo: String = "0",
        secondo: String = "0",
        periodo: String = "",
        finita: Boolean = false,
        giochi: String = "",
    ) = WearScoreState(
        side1Primary = primo,
        side1Secondary = giochi,
        side2Primary = secondo,
        side2Secondary = "",
        periodLabel = periodo,
        hasClock = hasClock,
        hasAuxTimer = hasClock,
        attributesScorer = false,
        decrementIsUndo = !hasClock,
        sportId = if (hasClock) "soccer" else "padel",
        sportLabel = "",
        sportIds = emptyList(),
        sportLabels = emptyList(),
        matchInProgress = !finita,
        matchOver = finita,
        eventLog = "",
    )

    private fun applica(stato: WearScoreState) {
        viewModel.applyStateV2(stato)
        idle()
    }

    @Test
    fun `nel calcio col v2 il cronometro avanza`() {
        applica(stato(hasClock = true))

        // Il tempo arriva dal telefono per il suo canale, non dentro lo stato v2.
        viewModel.syncMatchTimer(65_000L, isRunning = false)
        idle()

        assertEquals("01:05", binding.matchTimer.text.toString())
    }

    @Test
    fun `passando da padel a calcio i game del set lasciano il posto al tempo`() {
        applica(stato(hasClock = false, periodo = "Set 1", giochi = "2-1"))
        assertEquals("2 \u2013 1", binding.matchTimer.text.toString())

        // Cronometro fermo: nessun tick in arrivo che possa riscrivere la fascia da solo.
        applica(stato(hasClock = true))

        assertEquals(viewModel.matchTimer.value, binding.matchTimer.text.toString())
    }

    @Test
    fun `nel padel il tempo non sovrascrive i game del set`() {
        // Il controllo del rimedio: il collector del tempo non deve scrivere anche senza cronometro.
        applica(stato(hasClock = false, periodo = "Set 2", giochi = "3-2"))

        viewModel.syncMatchTimer(65_000L, isRunning = false)
        idle()

        assertEquals("3 \u2013 2", binding.matchTimer.text.toString())
    }

    @Test
    fun `la fascia D porta il periodo e i set chiusi`() {
        applica(stato(hasClock = false, periodo = "Set 2", giochi = "6-4 \u00B7 4-3"))

        assertEquals("4 \u2013 3", binding.matchTimer.text.toString())
        assertEquals("SET 2 \u00B7 6-4", binding.faceDetail.text.toString())
    }

    @Test
    fun `il cronometro e' bianco se corre e grigio se e' fermo`() {
        applica(stato(hasClock = true))

        viewModel.syncMatchTimer(65_000L, isRunning = false)
        idle()
        assertEquals(colore(R.color.elite_text_secondary), binding.matchTimer.currentTextColor)

        viewModel.syncMatchTimer(65_000L, isRunning = true)
        idle()
        assertEquals(colore(R.color.ink_white), binding.matchTimer.currentTextColor)
        // Fermalo: il test non lascia un cronometro vivo nel ViewModel.
        viewModel.syncMatchTimer(65_000L, isRunning = false)
        idle()
    }

    @Test
    fun `a partita finita il risultato resta a piena intensita'`() {
        applica(stato(hasClock = false, primo = "6", secondo = "4", finita = true))

        assertEquals(1f, binding.team1Container.alpha)
        assertEquals(1f, binding.team2Container.alpha)
        // I lati restano spenti per il tocco breve: e' la parte che non cambia.
        assertTrue(!binding.team1Container.isClickable)
    }

    @Test
    fun `a partita finita lo sconfitto e' grigio e il vincitore bianco`() {
        applica(stato(hasClock = false, primo = "6", secondo = "4", finita = true))
        assertEquals(colore(R.color.ink_white), binding.team1Score.currentTextColor)
        assertEquals(colore(R.color.elite_text_secondary), binding.team2Score.currentTextColor)

        applica(stato(hasClock = false, primo = "1", secondo = "2", finita = true))
        assertEquals(colore(R.color.elite_text_secondary), binding.team1Score.currentTextColor)
        assertEquals(colore(R.color.ink_white), binding.team2Score.currentTextColor)
    }

    @Test
    fun `senza un vincitore le due cifre restano bianche e riannullando la partita torna tutto bianco`() {
        applica(stato(hasClock = false, primo = "6", secondo = "6", finita = true))
        assertEquals(colore(R.color.ink_white), binding.team1Score.currentTextColor)
        assertEquals(colore(R.color.ink_white), binding.team2Score.currentTextColor)

        // Un annullamento riapre la partita: lo sconfitto di prima non resta grigio.
        applica(stato(hasClock = false, primo = "6", secondo = "4", finita = true))
        applica(stato(hasClock = false, primo = "30", secondo = "15"))
        assertEquals(colore(R.color.ink_white), binding.team1Score.currentTextColor)
        assertEquals(colore(R.color.ink_white), binding.team2Score.currentTextColor)
    }

    @Test
    fun `il colore della squadra e' una striscia portata a 3 a 1 sul nero, le cifre restano bianche`() {
        // Il blu notte scelto dall'utente sul nero fa 1.31:1: diventa #4F4FA7.
        viewModel.setTeamColor(1, 0xFF000080.toInt())
        // Il giallo dei default regge da solo e non si tocca.
        viewModel.setTeamColor(2, 0xFFFFD600.toInt())
        idle()

        assertEquals(0xFF4F4FA7.toInt(), (binding.team1Stripe.background as ColorDrawable).color)
        assertEquals(0xFFFFD600.toInt(), (binding.team2Stripe.background as ColorDrawable).color)
        assertEquals(colore(R.color.ink_white), binding.team1Score.currentTextColor)
        assertEquals(colore(R.color.ink_white), binding.team2Score.currentTextColor)
    }

    @Test
    fun `il pallino del servizio sta sul lato di chi serve e nel calcio non c'e'`() {
        applica(stato(hasClock = false, periodo = "Set 1").copy(servingSide = 1))
        assertEquals(View.VISIBLE, binding.team1ServeDot.visibility)
        assertEquals(View.GONE, binding.team2ServeDot.visibility)

        applica(stato(hasClock = false, periodo = "Set 1").copy(servingSide = 2))
        assertEquals(View.GONE, binding.team1ServeDot.visibility)
        assertEquals(View.VISIBLE, binding.team2ServeDot.visibility)

        applica(stato(hasClock = true))
        assertEquals(View.GONE, binding.team1ServeDot.visibility)
        assertEquals(View.GONE, binding.team2ServeDot.visibility)
    }

    @Test
    fun `senza un cronometro il bersaglio in alto non risponde`() {
        applica(stato(hasClock = false, periodo = "Set 1", giochi = "1-0"))
        assertFalse(binding.touchTimer.isClickable)

        applica(stato(hasClock = true))
        assertTrue(binding.touchTimer.isClickable)
    }

    @Test
    fun `il K scaduto dice K 0 00 in rosso`() {
        viewModel.setKeeperTimerState(KeeperTimerState.Finished)
        idle()

        assertEquals("K 0:00", binding.keeperTimer.text.toString())
        assertEquals(colore(R.color.elite_error), binding.keeperTimer.currentTextColor)
    }

    @Test
    fun `TalkBack legge il nome e il punteggio del lato`() {
        applica(stato(hasClock = false, primo = "40", secondo = "15"))

        // Nome non ancora arrivato: il ripiego nella lingua dell'orologio.
        assertTrue(binding.team1Container.contentDescription.startsWith("Team 1, 40."))
        assertTrue(binding.team2Container.contentDescription.startsWith("Team 2, 15."))

        viewModel.setTeamNames("ROSSI", "BLU")
        idle()
        assertTrue(binding.team1Container.contentDescription.startsWith("ROSSI, 40."))
        assertTrue(binding.team2Container.contentDescription.startsWith("BLU, 15."))
    }

    @Test
    fun `anche senza v2 la descrizione dice il punteggio`() {
        viewModel.updateScoresFromMobile(2, 1)
        idle()

        assertTrue(binding.team1Container.contentDescription.startsWith("Team 1, 2."))
        assertTrue(binding.team2Container.contentDescription.startsWith("Team 2, 1."))
    }

    @Test
    fun `le cifre annunciano da sole quando cambiano`() {
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, binding.team1Score.accessibilityLiveRegion)
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, binding.team2Score.accessibilityLiveRegion)
    }

    @Test
    fun `il K mostra il tempo che resta mentre corre`() {
        viewModel.setKeeperTimerState(KeeperTimerState.Running(252))
        idle()

        assertEquals("K 4:12", binding.keeperTimer.text.toString())
    }

    /** L8: l'anello aveva il massimo fisso a 300, e con 600 s restava pieno per cinque minuti. */
    @Test
    fun `il massimo dell'anello e' la durata del portiere`() {
        viewModel.applyKeeperFromPhone(600_000L, running = true, durationMillis = 600_000L)
        idle()

        assertEquals(600, binding.keeperProgressBar.max)
        assertEquals(600, binding.keeperProgressBar.progress)

        viewModel.applyKeeperFromPhone(60_000L, running = true, durationMillis = 60_000L)
        idle()
        assertEquals(60, binding.keeperProgressBar.max)
        assertEquals(60, binding.keeperProgressBar.progress)
    }

    @Test
    fun `il fondo del quadrante e' nero puro e lo da' il tema, non il layout`() {
        // Il layout non dipinge un secondo fondo: sarebbe l'Overdraw di lint.
        assertEquals(null, binding.root.background)
        val valore = android.util.TypedValue()
        assertTrue(controller.get().theme.resolveAttribute(android.R.attr.windowBackground, valore, true))
        assertEquals(0xFF000000.toInt(), valore.data)
    }

    private fun passano(secondi: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(secondi))

    private fun riga() = binding.gestureHint.text.toString()

    private fun coloreRiga() = binding.gestureHint.currentTextColor

    private fun colore(risorsa: Int) = ContextCompat.getColor(controller.get(), risorsa)

    private fun collegati() {
        collegamento.value = ConnectionState.Connected(1)
        idle()
    }

    // --- La colla fra il quadrante e il menu partita ---

    private fun statoSport(
        sportId: String,
        inCorso: Boolean = true,
    ) = WearScoreState(
        side1Primary = "6",
        side1Secondary = "",
        side2Primary = "4",
        side2Secondary = "",
        periodLabel = "",
        hasClock = false,
        hasAuxTimer = false,
        attributesScorer = false,
        decrementIsUndo = true,
        sportId = sportId,
        sportLabel = sportId,
        sportIds = listOf("padel", "football"),
        sportLabels = listOf("Padel", "Calcio"),
        matchInProgress = inCorso,
        matchOver = false,
        eventLog = "",
    )

    private fun apriMenu(): Intent {
        binding.btnMenu.performClick()
        return shadowOf(controller.get()).nextStartedActivityForResult.intent
    }

    private fun sceglieFine(richiesta: Intent) {
        shadowOf(controller.get()).receiveResult(
            richiesta,
            Activity.RESULT_OK,
            Intent().putExtra(MenuActivity.EXTRA_AZIONE, MenuActivity.AZIONE_FINE),
        )
        idle()
    }

    @Test
    fun `la fine partita scelta nel menu porta il ViewModel a CHIUSURA`() {
        collegati()
        applica(statoSport("padel"))

        sceglieFine(apriMenu())

        assertEquals(Transitorio.Chiusura, viewModel.statoFiducia.value)
    }

    /** L4: nel calcio la voce e' accesa come negli altri sport, e la chiusura parte (era spenta). */
    @Test
    fun `nel calcio la fine partita scelta nel menu porta il ViewModel a CHIUSURA`() {
        collegati()
        applica(statoSport("football"))

        sceglieFine(apriMenu())

        assertEquals(Transitorio.Chiusura, viewModel.statoFiducia.value)
    }

    @Test
    fun `se dopo l'apertura del menu arrivano punti in coda la chiusura non parte e il menu dice perche'`() {
        collegati()
        applica(statoSport("padel"))
        val richiesta = apriMenu()

        // Un tocco dato al polso mentre il menu era aperto e non consegnato: la coda non e' vuota.
        PendingIntents(RuntimeEnvironment.getApplication()).add(PendingIntent("point", 1, 1_000L))
        viewModel.refreshPendingCount()
        sceglieFine(richiesta)

        assertFalse(viewModel.statoFiducia.value is Transitorio.Chiusura)
        // Il menu si riapre: la voce spenta ha il suo motivo scritto nel sottotitolo.
        val riaperto = shadowOf(controller.get()).nextStartedActivityForResult.intent
        assertEquals(MenuActivity::class.java.name, riaperto.component?.className)
    }

    /**
     * La voce FINE PARTITA e' accesa nel menu (nessun punto in coda), ma chiudiPartita() rifiuta:
     * un punto appena dato e consegnato aspetta ancora lo stato del telefono (ricevuta aperta), cosa
     * che il menu non sa. Il comando non parte e il menu si riapre invece di restare muto.
     */
    @Test
    fun `con la voce accesa ma chiudiPartita che rifiuta il menu si riapre`() {
        collegati()
        val registroConUnPunto = MatchLogCodec.encode(listOf(LoggedEvent(ScoringEvent.Point(side = 1))))
        applica(statoSport("padel").copy(eventLog = registroConUnPunto))
        val richiesta = apriMenu()

        // Un tocco dato mentre il menu era aperto e consegnato: ricevuta aperta, coda vuota.
        viewModel.incrementScore(1)
        sceglieFine(richiesta)

        assertFalse(viewModel.statoFiducia.value is Transitorio.Chiusura)
        val riaperto = shadowOf(controller.get()).nextStartedActivityForResult.intent
        assertEquals(MenuActivity::class.java.name, riaperto.component?.className)
    }

    @Test
    fun `se dopo l'apertura del menu il registro e' gia' vuoto la chiusura non parte`() {
        collegati()
        applica(statoSport("padel"))
        val richiesta = apriMenu()

        applica(statoSport("padel", inCorso = false))
        sceglieFine(richiesta)

        assertFalse(viewModel.statoFiducia.value is Transitorio.Chiusura)
    }

    @Test
    fun `la riga di stato e' una sola e parla a voce cortese`() {
        assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, binding.gestureHint.accessibilityLiveRegion)
    }

    @Test
    fun `senza anomalie la riga suggerisce il gesto in grigio, senza pallino ne' SCOLLEGATO`() {
        applica(stato(hasClock = false, periodo = "Set 1"))
        collegati()

        // Padel: il tocco lungo annulla.
        assertEquals("HOLD: UNDO", riga())
        assertEquals(colore(R.color.elite_text_secondary), coloreRiga())
    }

    @Test
    fun `all'avvio il collegamento non ancora risposto non lampeggia SCOLLEGATO`() {
        applica(stato(hasClock = false, periodo = "Set 1"))

        assertEquals("HOLD: UNDO", riga())

        // Passati i 2 secondi senza un collegamento, la riga lo dice a parole e in ambra, con
        // l'ora dell'ultimo stato che il telefono ha mandato dal vivo (qui, quello di prima).
        passano(3)
        assertTrue(riga(), Regex("OFFLINE · \\d\\d:\\d\\d").matches(riga()))
        assertEquals(colore(R.color.elite_warning), coloreRiga())
    }

    @Test
    fun `col telefono lontano i punti in coda si contano in ambra`() {
        applica(stato(hasClock = false, periodo = "Set 1"))
        passano(3)
        mettiInCoda(2)

        assertEquals("2 QUEUED", riga())
        assertEquals(colore(R.color.elite_warning), coloreRiga())
    }

    @Test
    fun `da collegati la coda e' INVIO e dopo 10 secondi NON CONSEGNATI`() {
        applica(stato(hasClock = false, periodo = "Set 1"))
        collegati()
        mettiInCoda(1)

        assertEquals("SENDING 1…", riga())
        assertEquals(colore(R.color.elite_text_primary), coloreRiga())

        passano(10)
        assertEquals("1 NOT DELIVERED", riga())
        assertEquals(colore(R.color.elite_warning), coloreRiga())
    }

    @Test
    fun `a partita finita la riga dice PARTITA FINITA e i lati restano spenti`() {
        applica(stato(hasClock = false, primo = "6", secondo = "4", finita = true))
        collegati()

        assertEquals("MATCH OVER", riga())
        assertEquals(colore(R.color.elite_text_primary), coloreRiga())
        assertTrue(!binding.team1Container.isClickable)
    }

    @Test
    fun `il giro dei 15 secondi non parte da STARTED, serve la schermata in primo piano`() {
        // Partita in corso, schermo visibile ma non in primo piano (STARTED senza RESUMED: schermo
        // spento, quadrante di sistema sopra): nessuno guarda la riga, niente richieste.
        applica(stato(hasClock = false, periodo = "Set 1"))

        passano(60)

        runBlocking { Mockito.verify(telefono, Mockito.never()).refreshConnection() }
    }

    /**
     * L6: al risveglio l'orologio rilegge i DataItem presenti, e fra questi ci sono anche quelli che
     * ha scritto lui (match_state): rigiocarli in ordine arbitrario puo' azzerare il cronometro. Si
     * rigioca solo cio' che ha scritto il telefono.
     */
    @Test
    fun `al risveglio non si rigioca un DataItem scritto dall'orologio, uno del telefono si`() {
        fun dataItem(
            host: String,
            path: String,
            mappa: DataMap,
        ): DataItem {
            val item = Mockito.mock(DataItem::class.java)
            Mockito.`when`(item.uri).thenReturn(Uri.parse("wear://$host$path"))
            Mockito.`when`(item.data).thenReturn(mappa.toByteArray())
            // DataMapItem rilegge l'item congelato: il finto ritorna se stesso.
            Mockito.`when`(item.freeze()).thenReturn(item)
            return item
        }
        val propri =
            dataItem(
                "orologio-locale",
                WearConstants.PATH_MATCH_STATE,
                DataMap().apply { putBoolean(WearConstants.KEY_MATCH_ACTIVE, false) },
            )
        val delTelefono =
            dataItem(
                "telefono",
                WearConstants.PATH_TIMER_STATE,
                DataMap().apply {
                    putLong(WearConstants.KEY_TIMER_MILLIS, 5_000L)
                    putBoolean(WearConstants.KEY_TIMER_RUNNING, false)
                },
            )
        val buffer = Mockito.mock(DataItemBuffer::class.java)
        Mockito.`when`(buffer.iterator()).thenAnswer { mutableListOf(propri, delTelefono).iterator() }
        val dataClient = Mockito.mock(DataClient::class.java)
        Mockito.`when`(dataClient.dataItems).thenReturn(Tasks.forResult(buffer))
        val nodo = Mockito.mock(Node::class.java)
        Mockito.`when`(nodo.id).thenReturn("orologio-locale")
        val nodeClient = Mockito.mock(NodeClient::class.java)
        Mockito.`when`(nodeClient.localNode).thenReturn(Tasks.forResult(nodo))

        val ricevute = mutableListOf<String>()
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    intent.action?.let { ricevute.add(it) }
                }
            }
        val manager = LocalBroadcastManager.getInstance(RuntimeEnvironment.getApplication())
        manager.registerReceiver(
            receiver,
            IntentFilter().apply {
                addAction(WearDataLayerService.ACTION_MATCH_STATE_UPDATE)
                addAction(WearDataLayerService.ACTION_TIMER_UPDATE)
            },
        )
        NodoLocale.azzera()
        try {
            Mockito.mockStatic(Wearable::class.java).use { wearable ->
                wearable
                    .`when`<DataClient> { Wearable.getDataClient(ArgumentMatchers.any(Activity::class.java)) }
                    .thenReturn(dataClient)
                wearable
                    .`when`<NodeClient> { Wearable.getNodeClient(ArgumentMatchers.any(Context::class.java)) }
                    .thenReturn(nodeClient)

                controller.resume()
                // L'id del nodo e poi i DataItem arrivano su listener del thread principale.
                idle()
                idle()
            }
        } finally {
            manager.unregisterReceiver(receiver)
            NodoLocale.azzera()
        }

        assertEquals(
            "rigiocato solo il timer del telefono, non il match_state dell'orologio: $ricevute",
            listOf(WearDataLayerService.ACTION_TIMER_UPDATE),
            ricevute,
        )
    }

    private fun mettiInCoda(quanti: Int) {
        val coda = PendingIntents(RuntimeEnvironment.getApplication())
        repeat(quanti) { coda.add(PendingIntent("point", 1, 1_000L + it)) }
        viewModel.refreshPendingCount()
        idle()
    }
}
