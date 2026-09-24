package it.vantaggi.scoreboardessential

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La colonna del minuto misurata con le metriche vere del carattere, come IntestazioneCronometroTest.
 *
 * Era larga 50dp fissi: coi caratteri al 200% il tempo andava a capo. Ora e' wrap_content con un
 * minimo, e resta su una riga anche col minuto a tre cifre.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w411dp-h923dp-xxhdpi")
class MinutoDelRegistroTest {
    private fun riga(scalaCaratteri: Float): View {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val configurazione = Configuration(app.resources.configuration).apply { fontScale = scalaCaratteri }
        val contesto = ContextThemeWrapper(app.createConfigurationContext(configurazione), R.style.Theme_ScoreboardEssential)
        return LayoutInflater.from(contesto).inflate(R.layout.match_event_item, null)
    }

    @Test
    fun conCaratteriIngranditi_il_minuto_resta_intero() {
        for (scala in listOf(1f, 1.3f, 2f)) {
            val radice = riga(scala)
            val minuto = radice.findViewById<TextView>(R.id.event_timestamp)
            minuto.text = "120'"
            val larghezza = (411 * radice.resources.displayMetrics.density).toInt()
            radice.measure(
                View.MeasureSpec.makeMeasureSpec(larghezza, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            radice.layout(0, 0, radice.measuredWidth, radice.measuredHeight)

            val layout = minuto.layout
            val spazio = minuto.width - minuto.totalPaddingLeft - minuto.totalPaddingRight
            assertEquals("a scala $scala va a capo in ${minuto.width}px", 1, minuto.lineCount)
            assertEquals("a scala $scala perde caratteri", minuto.text.length, layout.getLineEnd(0))
            assertTrue(
                "a scala $scala e' largo ${layout.getLineWidth(0)}px ma ha solo ${spazio}px",
                layout.getLineWidth(0) <= spazio + 1f,
            )
        }
    }
}
