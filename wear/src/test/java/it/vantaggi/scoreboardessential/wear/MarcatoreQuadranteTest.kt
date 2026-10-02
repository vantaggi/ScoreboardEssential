package it.vantaggi.scoreboardessential.wear

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import it.vantaggi.scoreboardessential.core.LoggedEvent
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.shared.PlayerData
import it.vantaggi.scoreboardessential.shared.communication.ConnectionState
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import it.vantaggi.scoreboardessential.wear.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.stubbing.Answer
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import java.time.Duration

/**
 * Il bersaglio inferiore del quadrante vero: per 8s dopo un gol confermato dice CHI? e apre la
 * lista del marcatore, altrimenti resta il menu. Come [MainActivityTest], ma col telefono che
 * risponde "consegnato", perche' la finestra nasce dalla ricevuta di un tocco.
 */
@RunWith(RobolectricTestRunner::class)
class MarcatoreQuadranteTest {
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
        val telefono =
            Mockito.mock(
                OptimizedWearDataSync::class.java,
                Answer { invocazione ->
                    if (invocazione.method.name == "sendMessage") true else Mockito.RETURNS_DEFAULTS.answer(invocazione)
                },
            )
        Mockito.`when`(telefono.connectionState).thenReturn(MutableStateFlow(ConnectionState.Connected(1)))
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
        binding = ActivityMainBinding.bind(controller.get().findViewById<ViewGroup>(android.R.id.content).getChildAt(0))
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun registro(eventi: Int): String = MatchLogCodec.encode(List(eventi) { LoggedEvent(ScoringEvent.Point(side = 1)) })

    private fun statoCalcio(eventi: Int) =
        WearScoreState(
            side1Primary = eventi.toString(),
            side1Secondary = "",
            side2Primary = "0",
            side2Secondary = "",
            periodLabel = "",
            hasClock = true,
            hasAuxTimer = true,
            attributesScorer = true,
            decrementIsUndo = false,
            sportId = "football",
            sportLabel = "Calcio",
            sportIds = emptyList(),
            sportLabels = emptyList(),
            matchInProgress = true,
            matchOver = false,
            eventLog = registro(eventi),
        )

    /** Un gol della squadra 1 confermato dal telefono, con la rosa gia' al polso. */
    private fun golConfermato() {
        viewModel.setAllPlayers(listOf(PlayerData(id = 7, name = "Rossi", roles = emptyList())))
        viewModel.applyStateV2(statoCalcio(0))
        idle()
        viewModel.incrementScore(1)
        idle()
        viewModel.applyStateV2(statoCalcio(1))
        idle()
    }

    private fun avanza(millisecondi: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millisecondi))

    @Test
    fun `senza un gol da attribuire il bersaglio inferiore e' il menu`() {
        assertEquals(View.GONE, binding.chiCapsule.visibility)
        assertEquals(View.VISIBLE, binding.menuGlyph.visibility)
        assertEquals(controller.get().getString(R.string.cd_menu), binding.btnMenu.contentDescription)

        binding.btnMenu.performClick()

        val avviata = shadowOf(controller.get()).nextStartedActivityForResult.intent
        assertEquals(MenuActivity::class.java.name, avviata.component?.className)
    }

    @Test
    fun `dopo il gol confermato il glifo lascia il posto a CHI e il bersaglio lo dice`() {
        golConfermato()

        assertEquals(View.VISIBLE, binding.chiCapsule.visibility)
        assertEquals(controller.get().getString(R.string.wear_who_short), binding.chiCapsule.text.toString())
        assertEquals(View.INVISIBLE, binding.menuGlyph.visibility)
        assertEquals(controller.get().getString(R.string.wear_who_scored), binding.btnMenu.contentDescription)
    }

    @Test
    fun `il tocco su CHI apre la lista col lato, il colore e il punteggio del gol`() {
        golConfermato()
        viewModel.setTeamColor(1, 0xFF1A237E.toInt())

        binding.btnMenu.performClick()

        val avviata = shadowOf(controller.get()).nextStartedActivity
        assertNotNull("il tocco su CHI? non ha aperto niente", avviata)
        assertEquals(PlayerSelectionActivity::class.java.name, avviata.component?.className)
        assertEquals(1, avviata.getIntExtra(WearConstants.EXTRA_TEAM_NUMBER, 0))
        assertEquals(0xFF1A237E.toInt(), avviata.getIntExtra(PlayerSelectionActivity.EXTRA_COLOR, 0))
        assertEquals("1–0", avviata.getStringExtra(PlayerSelectionActivity.EXTRA_RISULTATO))
        assertEquals(
            listOf("Rossi"),
            PlayerData.decodeList(avviata.getStringExtra(WearDataLayerService.EXTRA_PLAYERS)).map { it.name },
        )
        // Usata l'offerta, il bersaglio e' di nuovo il menu.
        assertNull(viewModel.finestraChi.value)
        idle()
        assertEquals(View.VISIBLE, binding.menuGlyph.visibility)
    }

    @Test
    fun `dopo 8 secondi CHI sparisce e il tocco riapre il menu`() {
        golConfermato()

        avanza(8_000L)

        assertEquals(View.GONE, binding.chiCapsule.visibility)
        assertEquals(View.VISIBLE, binding.menuGlyph.visibility)
        binding.btnMenu.performClick()
        assertEquals(MenuActivity::class.java.name, shadowOf(controller.get()).nextStartedActivityForResult.intent.component?.className)
    }

    @Test
    fun `il quadrante non apre mai la lista da solo`() {
        golConfermato()
        avanza(8_000L + 1_000)

        // Prima la lista si apriva da sola dopo ogni gol: ora niente parte senza un tocco.
        assertNull(shadowOf(controller.get()).nextStartedActivity)
    }
}
