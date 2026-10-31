package it.vantaggi.scoreboardessential.wear

import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.shared.communication.ConnectionState
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * A fine game la sigla di chi serve sta tre secondi al posto dei pallini, dalla parte di chi serve,
 * poi tornano i pallini; un punteggio che cambia prima la toglie subito. Col movimento ridotto il
 * cambio e' secco, ed e' cosi' che si legge qui senza aspettare le dissolvenze.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "it-w192dp-h192dp-round-notnight")
class SiglaDiChiServeTest {
    private fun gonfia(): View {
        val app = RuntimeEnvironment.getApplication()
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val contesto = ContextThemeWrapper(app, R.style.Theme_ScoreboardEssential_Face)
        return LayoutInflater.from(contesto).inflate(R.layout.activity_main, null)
    }

    /** Rosso se la sigla non compare dalla parte di chi serve, se resta oltre i tre secondi o se i pallini non tornano. */
    @Test
    fun `la sigla sta tre secondi al posto dei pallini, poi tornano i pallini`() {
        val radice = gonfia()
        val sigla = radice.findViewById<TextView>(R.id.team2ServeNext)
        val pallino = radice.findViewById<View>(R.id.team2ServeDot)

        mostraLaSigla(radice, lato = 2, sigla = "VER")
        assertEquals(View.VISIBLE, sigla.visibility)
        assertEquals("VER", sigla.text.toString())
        assertEquals("Serve VER", sigla.contentDescription)
        assertEquals(0f, pallino.alpha)
        assertEquals("dall'altra parte niente", View.GONE, radice.findViewById<View>(R.id.team1ServeNext).visibility)

        ShadowLooper.idleMainLooper(DURATA_SIGLA_DI_CHI_SERVE_MS, TimeUnit.MILLISECONDS)
        assertEquals(View.GONE, sigla.visibility)
        assertEquals(1f, pallino.alpha)
    }

    /** Rosso se un punto entro i tre secondi lascia la sigla, o lascia i pallini spenti. */
    @Test
    fun `un punto prima dei tre secondi toglie la sigla subito`() {
        val radice = gonfia()
        mostraLaSigla(radice, lato = 1, sigla = "ROS")
        togliLaSigla(radice)
        assertEquals(View.GONE, radice.findViewById<View>(R.id.team1ServeNext).visibility)
        assertEquals(1f, radice.findViewById<View>(R.id.team1ServeDot).alpha)
        assertEquals(1f, radice.findViewById<View>(R.id.team1ServeDotSecond).alpha)
    }

    /**
     * Sul quadrante vero: lo stato del telefono che chiude un game fa comparire la sigla di chi serve
     * dopo, presa dalle rose che il telefono manda. Rosso se il quadrante non la mostra, se la mostra
     * dalla parte sbagliata o se la mostra per un punto qualunque.
     */
    @Test
    fun `il quadrante mostra la sigla quando il telefono chiude un game`() {
        val app = RuntimeEnvironment.getApplication()
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val telefono = Mockito.mock(OptimizedWearDataSync::class.java)
        Mockito.`when`(telefono.connectionState).thenReturn(MutableStateFlow(ConnectionState.Connected(1)))
        val viewModel = WearViewModel(app, telefono)
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val fabbrica =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
            }
        ViewModelProvider(controller.get(), fabbrica)[WearViewModel::class.java]
        controller.get().chiediPermessoNotifiche = false
        controller
            .create()
            .start()
            .resume()
            .visible()
        ShadowLooper.idleMainLooper()
        viewModel.applicaCoppie(CoppieDalTelefono(rosa1 = listOf("Rossi", "Bianchi"), rosa2 = listOf("Verdi", "Neri")))
        val activity = controller.get()

        viewModel.applyStateV2(statoPadel(List(3) { ScoringEvent.Point(1) }))
        ShadowLooper.idleMainLooper()
        assertEquals("un punto non e' una fine game", View.GONE, activity.findViewById<View>(R.id.team2ServeNext).visibility)

        viewModel.applyStateV2(statoPadel(game(1)))
        ShadowLooper.idleMainLooper()
        // Dopo il primo game serve la coppia 2 col suo primo giocatore: Verdi.
        val sigla = activity.findViewById<TextView>(R.id.team2ServeNext)
        assertEquals(View.VISIBLE, sigla.visibility)
        assertEquals("VER", sigla.text.toString())
        assertEquals(View.GONE, activity.findViewById<View>(R.id.team1ServeNext).visibility)
    }
}
