package it.vantaggi.scoreboardessential.wear

import android.content.Context
import android.content.res.Configuration
import android.os.Looper
import android.os.SystemClock
import android.text.TextPaint
import android.util.TypedValue
import android.view.ViewGroup
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import it.vantaggi.scoreboardessential.shared.communication.ConnectionState
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.wear.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/**
 * La riga di stato sul tondo da 192dp, misurata e non contata: le frasi piu' lunghe, in italiano,
 * devono stare nella larghezza vera della vista al minimo dell'autoSize (10sp) e a fontScale 1.0.
 *
 * Contare i caratteri (StringheStatoTest) non basta: la larghezza dipende da font, spaziatura e
 * dal rientro del BoxInsetLayout sul tondo. Serve la grafica nativa: con quella di default,
 * measureText restituisce un pixel a carattere.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w192dp-h192dp-round-notnight")
class RigaStatoLarghezzaTest {
    private fun inItaliano(contesto: Context): Context {
        val configurazione = Configuration(contesto.resources.configuration).apply { setLocale(Locale.ITALIAN) }
        return contesto.createConfigurationContext(configurazione)
    }

    @Test
    fun `le frasi piu' lunghe entrano nella riga del tondo a 10sp`() {
        val app = RuntimeEnvironment.getApplication()
        val telefono = Mockito.mock(OptimizedWearDataSync::class.java)
        Mockito.`when`(telefono.connectionState).thenReturn(MutableStateFlow(ConnectionState.Disconnected))
        val viewModel = WearViewModel(app, telefono, orologio = { SystemClock.uptimeMillis() + 1_000_000L })
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val fabbrica =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
            }
        ViewModelProvider(controller.get(), fabbrica)[WearViewModel::class.java]
        controller.create().start().visible()
        shadowOf(Looper.getMainLooper()).idle()
        val contenuto = controller.get().findViewById<ViewGroup>(android.R.id.content)
        val riga = ActivityMainBinding.bind(contenuto.getChildAt(0)).gestureHint

        val configurazione = app.resources.configuration
        assertTrue("la configurazione non e' tonda", configurazione.isScreenRound)
        assertEquals(1f, configurazione.fontScale, 0f)

        val disponibile = riga.width - riga.paddingLeft - riga.paddingRight
        assertTrue("la riga non e' stata impaginata (larghezza $disponibile)", disponibile > 0)

        val italiano = inItaliano(app)
        val frasi =
            listOf(
                italiano.getString(R.string.wear_status_offline_at, "18:42"),
                italiano.getString(R.string.wear_status_not_delivered, 999),
                italiano.getString(R.string.wear_status_sport_unchanged),
            )
        // Il carattere come lo disegna la riga (famiglia, grassetto, spaziatura) a 10sp, il minimo dell'autoSize.
        val penna = TextPaint(riga.paint)
        penna.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, app.resources.displayMetrics)
        frasi.forEach { frase ->
            val larghezza = penna.measureText(frase)
            // Un pixel a carattere vorrebbe dire grafica non nativa: la misura non direbbe niente.
            assertTrue("\"$frase\" misura ${larghezza}px: la grafica non e' nativa?", larghezza > frase.length * 2f)
            assertTrue("\"$frase\" e' larga ${larghezza}px e non entra nei ${disponibile}px della riga", larghezza <= disponibile)
        }
    }
}
