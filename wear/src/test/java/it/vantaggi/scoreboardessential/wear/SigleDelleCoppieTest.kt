package it.vantaggi.scoreboardessential.wear

import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.shared.communication.ConnectionState
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Le sigle delle due coppie sopra le cifre, sempre, con chi batte in lime (bozza A di chi batte,
 * 3 novembre 2026): al posto dei pallini, e in ambient senza colore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "it-w192dp-h192dp-round-notnight")
class SigleDelleCoppieTest {
    private val coppie = listOf("VAN", "GLO") to listOf("ROS", "BIA")

    private fun gonfia(): View {
        val contesto = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_ScoreboardEssential_Face)
        return LayoutInflater.from(contesto).inflate(R.layout.activity_main, null)
    }

    /** Il colore di ciascuna sigla del testo, nell'ordine. */
    private fun colori(vista: TextView): List<Int> {
        val testo = vista.text as Spanned
        return testo
            .getSpans(0, testo.length, ForegroundColorSpan::class.java)
            .sortedBy { testo.getSpanStart(it) }
            .map { it.foregroundColor }
    }

    private fun colore(id: Int) = ContextCompat.getColor(RuntimeEnvironment.getApplication(), id)

    /** Rosso se una coppia non ha le sue sigle, se il lime non e' su chi batte o se tocca un altro. */
    @Test
    fun `le due coppie sempre, chi batte in lime e gli altri in grigio`() {
        val radice = gonfia()
        assertTrue(mostraLeCoppie(radice, coppie, servingSide = 2, servingSlot = 2, ambient = false))
        val uno = radice.findViewById<TextView>(R.id.team1ServeNext)
        val due = radice.findViewById<TextView>(R.id.team2ServeNext)
        assertEquals(View.VISIBLE, uno.visibility)
        assertEquals("VAN GLO", uno.text.toString())
        assertEquals("ROS BIA", due.text.toString())
        val grigio = colore(R.color.text_secondary)
        assertEquals("la coppia che non batte e' tutta grigia", listOf(grigio, grigio), colori(uno))
        assertEquals("batte Bianchi, il secondo", listOf(grigio, colore(R.color.accent_default)), colori(due))
    }

    /** Rosso se in ambient resta il lime, o se chi batte non si distingue piu' dagli altri. */
    @Test
    fun `in ambient chi batte e' bianco`() {
        val radice = gonfia()
        mostraLeCoppie(radice, coppie, servingSide = 1, servingSlot = 1, ambient = true)
        assertEquals(
            listOf(colore(R.color.text_on_watch), colore(R.color.text_secondary)),
            colori(radice.findViewById(R.id.team1ServeNext)),
        )
    }

    /**
     * Rosso se un pallino resta acceso accanto alle sigle, o se il primo pallino diventa GONE: e'
     * l'ancora delle sigle, e un GONE le porterebbe in cima alla colonna.
     */
    @Test
    fun `con le sigle i pallini si spengono ma restano al loro posto`() {
        val radice = gonfia()
        mostraLeCoppie(radice, coppie, servingSide = 1, servingSlot = 2, ambient = false)
        listOf(R.id.team1ServeDot, R.id.team1ServeDotSecond, R.id.team2ServeDot, R.id.team2ServeDotSecond).forEach {
            assertEquals(View.INVISIBLE, radice.findViewById<View>(it).visibility)
        }
    }

    /** Rosso se le sigle da quattro lettere non scendono alla misura piccola, che e' quella che entra. */
    @Test
    fun `le sigle da quattro lettere sono piu' piccole`() {
        val radice = gonfia()
        mostraLeCoppie(radice, coppie, 1, 1, false)
        val normale = radice.findViewById<TextView>(R.id.team1ServeNext).textSize
        mostraLeCoppie(radice, listOf("ROSS", "MOR") to listOf("ROSE", "BIA"), 1, 1, false)
        val lunghe = radice.findViewById<TextView>(R.id.team1ServeNext).textSize
        assertEquals(radice.resources.getDimension(R.dimen.face_coppie_text_size), normale)
        assertEquals(radice.resources.getDimension(R.dimen.face_coppie_text_size_lunghe), lunghe)
    }

    /**
     * Sul quadrante vero: senza rose ci sono i pallini; arrivate le rose dal telefono compaiono le
     * sigle e i pallini si spengono; nel calcio niente sigle. Rosso se una di queste tre cose manca.
     */
    @Test
    fun `il quadrante mostra le sigle quando arrivano le rose, solo con la racchetta`() {
        val app = RuntimeEnvironment.getApplication()
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
        val activity = controller.get()
        val sigle = activity.findViewById<TextView>(R.id.team2ServeNext)

        viewModel.applyStateV2(statoPadel(game(1)))
        ShadowLooper.idleMainLooper()
        assertEquals("senza rose niente sigle", View.GONE, sigle.visibility)
        assertEquals("e il pallino di chi serve", View.VISIBLE, activity.findViewById<View>(R.id.team2ServeDot).visibility)

        viewModel.applicaCoppie(CoppieDalTelefono(rosa1 = listOf("Rossi", "Bianchi"), rosa2 = listOf("Verdi", "Neri")))
        ShadowLooper.idleMainLooper()
        assertEquals(View.VISIBLE, sigle.visibility)
        // Dopo il primo game serve la coppia 2 col suo primo giocatore: Verdi.
        assertEquals("VER NER", sigle.text.toString())
        assertEquals(colore(R.color.accent_default), colori(sigle).first())
        assertEquals(View.INVISIBLE, activity.findViewById<View>(R.id.team2ServeDot).visibility)

        viewModel.applyStateV2(statoPadel(game(1) + ScoringEvent.Point(1)).copy(hasClock = true, sportId = "football", servingSide = 0))
        ShadowLooper.idleMainLooper()
        assertEquals("nel calcio niente sigle", View.GONE, sigle.visibility)
    }
}
