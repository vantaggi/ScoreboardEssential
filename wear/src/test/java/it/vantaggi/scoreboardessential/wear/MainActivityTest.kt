package it.vantaggi.scoreboardessential.wear

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import it.vantaggi.scoreboardessential.shared.communication.ConnectionState
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.wear.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
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
        val telefono = Mockito.mock(OptimizedWearDataSync::class.java)
        Mockito.`when`(telefono.connectionState).thenReturn(collegamento)
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
    ) = WearScoreState(
        side1Primary = primo,
        side1Secondary = "",
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
    fun `passando da padel a calcio il periodo lascia il posto al tempo`() {
        applica(stato(hasClock = false, periodo = "Set 1"))
        assertEquals("Set 1", binding.matchTimer.text.toString())

        // Cronometro fermo: nessun tick in arrivo che possa riscrivere la riga da solo.
        applica(stato(hasClock = true))

        assertEquals(viewModel.matchTimer.value, binding.matchTimer.text.toString())
    }

    @Test
    fun `nel padel il tempo non sovrascrive il periodo`() {
        // Il controllo del rimedio: la condizione del collector non deve aprirsi anche senza cronometro.
        applica(stato(hasClock = false, periodo = "Set 2"))

        viewModel.syncMatchTimer(65_000L, isRunning = false)
        idle()

        assertEquals("Set 2", binding.matchTimer.text.toString())
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
    fun `il fondo del quadrante e' nero puro`() {
        val radice = binding.root.background as ColorDrawable
        assertEquals(0xFF000000.toInt(), radice.color)
    }

    private fun passano(secondi: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(secondi))

    private fun riga() = binding.gestureHint.text.toString()

    private fun coloreRiga() = binding.gestureHint.currentTextColor

    private fun colore(risorsa: Int) = ContextCompat.getColor(controller.get(), risorsa)

    private fun collegati() {
        collegamento.value = ConnectionState.Connected(1)
        idle()
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
        assertEquals(colore(R.color.sidewalk_gray), coloreRiga())
    }

    @Test
    fun `all'avvio il collegamento non ancora risposto non lampeggia SCOLLEGATO`() {
        applica(stato(hasClock = false, periodo = "Set 1"))

        assertEquals("HOLD: UNDO", riga())

        // Passati i 2 secondi senza un collegamento, la riga lo dice a parole e in ambra, con
        // l'ora dell'ultimo stato che il telefono ha mandato dal vivo (qui, quello di prima).
        passano(3)
        assertTrue(riga(), Regex("OFFLINE · \\d\\d:\\d\\d").matches(riga()))
        assertEquals(colore(R.color.signal_amber), coloreRiga())
    }

    @Test
    fun `col telefono lontano i punti in coda si contano in ambra`() {
        applica(stato(hasClock = false, periodo = "Set 1"))
        passano(3)
        mettiInCoda(2)

        assertEquals("2 QUEUED", riga())
        assertEquals(colore(R.color.signal_amber), coloreRiga())
    }

    @Test
    fun `da collegati la coda e' INVIO e dopo 10 secondi NON CONSEGNATI`() {
        applica(stato(hasClock = false, periodo = "Set 1"))
        collegati()
        mettiInCoda(1)

        assertEquals("SENDING 1…", riga())
        assertEquals(colore(R.color.stencil_white), coloreRiga())

        passano(10)
        assertEquals("1 NOT DELIVERED", riga())
        assertEquals(colore(R.color.signal_amber), coloreRiga())
    }

    @Test
    fun `a partita finita la riga dice PARTITA FINITA e i lati restano spenti`() {
        applica(stato(hasClock = false, primo = "6", secondo = "4", finita = true))
        collegati()

        assertEquals("MATCH OVER", riga())
        assertEquals(colore(R.color.stencil_white), coloreRiga())
        assertTrue(!binding.team1Container.isClickable)
    }

    private fun mettiInCoda(quanti: Int) {
        val coda = PendingIntents(RuntimeEnvironment.getApplication())
        repeat(quanti) { coda.add(PendingIntent("point", 1, 1_000L + it)) }
        viewModel.refreshPendingCount()
        idle()
    }
}
