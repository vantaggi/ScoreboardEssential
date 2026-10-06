package it.vantaggi.scoreboardessential.wear

import android.graphics.Typeface
import android.os.Looper
import android.os.SystemClock
import android.text.TextPaint
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import it.vantaggi.scoreboardessential.shared.communication.ConnectionState
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.wear.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import it.vantaggi.scoreboardessential.shared.R as SharedR

/**
 * La misura che decide il carattere delle cifre del quadrante (DESIGN.md, Adattamento alla UI
 * Constitution, G-1b).
 *
 * La regola del proprietario (6 ottobre 2026): i numeri sono Inter 600 con cifre tabulari (tnum), ma
 * sul quadrante SOLO se "AV" e "40" restano nella colonna alla dimensione fissa (58dp sul tondo da
 * 192dp, 68dp sul 227dp); se no, le cifre tengono il condensato e Inter va sul resto. Qui la misura
 * e' fatta sul quadrante vero, con la grafica nativa e le metriche vere dei file in res/font.
 *
 * Esito: Inter NON entra, e non per poco. Con tnum ogni cifra e' larga circa 0,65 em e la "A" e la "V"
 * 0,73 em meno la crenatura: "AV" misura 80dp a 58dp contro i 68dp della colonna e 94dp a 68dp contro
 * gli 80,5dp. Per entrare servirebbe un corpo di 49dp sul 192dp e di 58dp sul 227dp, cioe' il 15% in
 * meno. Le cifre restano condensate. Se un giorno le colonne si allargano o il corpo scende, il test
 * fallisce e dice di rifare la scelta: e' la falsificazione di G-1b, non un valore da ritoccare.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CifreDelQuadranteTest {
    private class Misura(
        val disponibile: Float,
        val interAV: Float,
        val inter40: Float,
        val condensatoAV: Float,
        val condensato40: Float,
        val dellaVista: Float,
    )

    private fun misura(dp: Float): Misura {
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
        val cifre: TextView = ActivityMainBinding.bind(contenuto.getChildAt(0)).team1Score

        val inter = ResourcesCompat.getFont(app, SharedR.font.inter)
        assertNotNull("Inter non e' in res/font", inter)
        val semibold = Typeface.create(inter!!, PESO_SEMIBOLD, false)
        val condensato = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        val metriche = app.resources.displayMetrics

        fun larghezza(
            carattere: Typeface?,
            testo: String,
            tabulare: Boolean = false,
        ): Float {
            val penna = TextPaint(cifre.paint)
            if (carattere != null) penna.typeface = carattere
            if (tabulare) penna.fontFeatureSettings = "tnum"
            penna.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, metriche)
            return penna.measureText(testo)
        }
        return Misura(
            disponibile = (cifre.width - cifre.paddingLeft - cifre.paddingRight).toFloat(),
            interAV = larghezza(semibold, "AV", tabulare = true),
            inter40 = larghezza(semibold, "40", tabulare = true),
            condensatoAV = larghezza(condensato, "AV"),
            condensato40 = larghezza(condensato, "40"),
            dellaVista = larghezza(null, "AV"),
        )
    }

    private fun verifica(
        dp: Float,
        colonnaDp: Float,
    ) {
        val m = misura(dp)
        val densita =
            RuntimeEnvironment
                .getApplication()
                .resources.displayMetrics.density
        val colonna = m.disponibile / densita
        assertEquals("la colonna delle cifre non e' quella attesa (${colonna}dp)", colonnaDp, colonna, 0.6f)
        val informa = "Inter 600 tnum: AV ${m.interAV / densita}dp, 40 ${m.inter40 / densita}dp; colonna ${colonna}dp"
        val piuLarga = maxOf(m.interAV, m.inter40)
        assertTrue("Inter 600 tabulare ora entra ($informa): si puo' passare alle cifre in Inter", piuLarga > m.disponibile)
        // Non e' un fuori-misura per un soffio: il corpo che farebbe entrare Inter e' oltre il 10% piu' piccolo.
        val corpoCheEntra = dp * m.disponibile / piuLarga
        assertTrue("Inter entrerebbe a ${corpoCheEntra}dp, a un soffio dai ${dp}dp: rifare la scelta ($informa)", corpoCheEntra < dp * 0.9f)
        assertTrue(
            "il condensato non entra piu' (AV ${m.condensatoAV}px, 40 ${m.condensato40}px, colonna ${m.disponibile}px)",
            maxOf(m.condensatoAV, m.condensato40) <= m.disponibile,
        )
        // E la vista le disegna davvero col condensato, non con Inter.
        assertEquals("le cifre non sono nel condensato", m.condensatoAV, m.dellaVista, 0.5f)
    }

    @Test
    @Config(qualifiers = "w192dp-h192dp-round-notnight-xhdpi")
    fun `tondo da 192dp, AV e 40 a 58dp in Inter 600 tabulare non stanno nella colonna e il condensato si`() {
        verifica(dp = 58f, colonnaDp = 68f)
    }

    @Test
    @Config(qualifiers = "w227dp-h227dp-round-notnight-xhdpi")
    fun `tondo da 227dp, AV e 40 a 68dp in Inter 600 tabulare non stanno nella colonna e il condensato si`() {
        verifica(dp = 68f, colonnaDp = 81f)
    }

    private companion object {
        const val PESO_SEMIBOLD = 600
    }
}
