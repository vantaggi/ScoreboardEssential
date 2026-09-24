package it.vantaggi.scoreboardessential.wear

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.wear.databinding.ActivityMainBinding
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
        viewModel =
            WearViewModel(
                app,
                OptimizedWearDataSync(
                    app,
                    Mockito.mock(DataClient::class.java),
                    Mockito.mock(MessageClient::class.java),
                    Mockito.mock(CapabilityClient::class.java),
                    Mockito.mock(NodeClient::class.java),
                ),
            )
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

    @Test
    fun `il fondo del quadrante e' nero puro`() {
        val radice = binding.root.background as ColorDrawable
        assertEquals(0xFF000000.toInt(), radice.color)
    }
}
