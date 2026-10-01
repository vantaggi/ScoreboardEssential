package it.vantaggi.scoreboardessential.wear

import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.os.SystemClock
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import it.vantaggi.scoreboardessential.shared.communication.ConnectionState
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.wear.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import kotlin.math.sqrt

/**
 * Il quadrante a fasce MISURATO: tondo da 192dp, tondo da 227dp e un quadrato.
 *
 * Le misure del design (cifre a 58sp, lati da 78x72dp, nessun testo tagliato, il gruppo
 * cronometro+K dentro l'anello) erano conti sul layout, non misure. Qui si impagina il quadrante
 * vero con la grafica nativa, perche' con quella di default measureText restituisce un pixel a
 * carattere e non direbbe niente (stessa ragione di RigaStatoLarghezzaTest).
 *
 * Il Layout Inspector sull'emulatore resta da fare: qui non c'e' il font di sistema dell'orologio.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuadranteFasceTest {
    private class Quadrante(
        val binding: ActivityMainBinding,
        val viewModel: WearViewModel,
        val densita: Float,
        val radice: ViewGroup,
    ) {
        fun dp(px: Int): Float = px / densita

        /** Il rettangolo di una vista nelle coordinate del quadrante, in dp. */
        fun rettangolo(vista: View): Rect {
            val r = Rect()
            vista.getDrawingRect(r)
            radice.offsetDescendantRectToMyCoords(vista, r)
            return r
        }
    }

    private fun apri(): Quadrante {
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
        val binding = ActivityMainBinding.bind(contenuto.getChildAt(0))
        // La ConstraintLayout delle fasce e' il secondo figlio della radice, dopo l'anello.
        val fasce = (binding.root as ViewGroup).getChildAt(1) as ViewGroup
        return Quadrante(binding, viewModel, app.resources.displayMetrics.density, fasce)
    }

    private fun Quadrante.applica(stato: WearScoreState) {
        viewModel.applyStateV2(stato)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun stato(
        primo: String,
        secondo: String,
        hasClock: Boolean = false,
        giochi: String = "",
        periodo: String = "",
        finita: Boolean = false,
        servizio: Int = 0,
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
        sportId = if (hasClock) "football" else "tennis",
        sportLabel = "",
        sportIds = emptyList(),
        sportLabels = emptyList(),
        matchInProgress = !finita,
        matchOver = finita,
        eventLog = "",
        servingSide = servizio,
    )

    /** Il carattere come lo disegna la vista, alla dimensione data (sp). */
    private fun penna(
        vista: TextView,
        sp: Float,
    ): TextPaint {
        val p = TextPaint(vista.paint)
        p.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, vista.resources.displayMetrics)
        return p
    }

    private fun larghezzaUtile(vista: TextView) = vista.width - vista.paddingLeft - vista.paddingRight

    // --- cifre ---

    private fun verificaCifre(
        quadrante: Quadrante,
        spAttesi: Float,
    ) {
        val b = quadrante.binding
        listOf(b.team1Score, b.team2Score).forEach { cifre ->
            // Dimensione FISSA: con l'autoSize "AV" e "40" uscirebbero a misure diverse.
            assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_NONE, cifre.autoSizeTextType)
            val attesa = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, spAttesi, cifre.resources.displayMetrics)
            assertEquals(attesa, cifre.textSize, 0.01f)

            val disponibile = larghezzaUtile(cifre)
            assertTrue("le cifre non sono state impaginate", disponibile > 0)
            // Il token piu' largo e' "AV" (vantaggio): se entra lui entrano 15, 30, 40, PV e 88.
            listOf("AV", "PV", "40", "15", "30", "88").forEach { testo ->
                val larghezza = penna(cifre, spAttesi).measureText(testo)
                assertTrue("\"$testo\" misura ${larghezza}px: la grafica non e' nativa?", larghezza > testo.length * 2f)
                assertTrue("\"$testo\" a ${spAttesi}sp e' largo ${larghezza}px e non entra nei ${disponibile}px", larghezza <= disponibile)
            }
            // E in altezza: senza il margine di font il testo sta fra ascent e descent.
            val metriche = penna(cifre, spAttesi).fontMetrics
            val altezza = metriche.descent - metriche.ascent
            assertTrue("le cifre sono alte ${altezza}px e la vista ${cifre.height}px", altezza <= cifre.height)
        }
    }

    private fun verificaLati(quadrante: Quadrante) {
        val b = quadrante.binding
        listOf(b.team1Container, b.team2Container).forEach { lato ->
            val larghezza = quadrante.dp(lato.width)
            val altezza = quadrante.dp(lato.height)
            assertTrue("il lato e' largo ${larghezza}dp, ne servono almeno 78", larghezza >= 78f)
            assertTrue("il lato e' alto ${altezza}dp, ne servono almeno 72", altezza >= 72f)
        }
    }

    /** Le strisce stanno sotto il CENTRO delle colonne delle cifre, non alla meta' di qualcos'altro. */
    private fun verificaStrisce(quadrante: Quadrante) {
        val b = quadrante.binding
        listOf(
            Triple(b.team1Container, b.team1Score, b.team1Stripe),
            Triple(b.team2Container, b.team2Score, b.team2Stripe),
        ).forEach { (lato, cifre, striscia) ->
            val centroCifre = lato.left + cifre.left + cifre.width / 2f
            val centroStriscia = striscia.left + striscia.width / 2f
            assertEquals("la striscia non e' centrata sotto le cifre", centroCifre, centroStriscia, 1.5f)
            // Sotto le cifre, mai sovrapposta al bersaglio del lato.
            assertTrue(striscia.top >= lato.bottom)
        }
    }

    /** Un bersaglio non entra nell'altro: le zone morte fra le fasce toccabili sono vere. */
    private fun verificaBersagli(quadrante: Quadrante) {
        val b = quadrante.binding
        val alto = quadrante.rettangolo(b.touchTimer)
        val altoK = quadrante.rettangolo(b.touchKeeper)
        val lato1 = quadrante.rettangolo(b.team1Container)
        val lato2 = quadrante.rettangolo(b.team2Container)
        val menu = quadrante.rettangolo(b.btnMenu)
        assertTrue("cronometro e K non si toccano a meta'", alto.right <= altoK.left)
        assertTrue("i due lati non si sovrappongono", lato1.right <= lato2.left)
        assertTrue("i lati partono sotto i bersagli in alto", lato1.top >= alto.bottom && lato2.top >= altoK.bottom)
        assertTrue("il menu parte sotto i lati", menu.top >= lato1.bottom && menu.top >= lato2.bottom)
        assertTrue("il menu e' alto almeno 48dp", quadrante.dp(menu.height()) >= 48f)
        // D ed E non si toccano da sole: stanno sopra il menu e non intercettano niente.
        listOf(b.faceDetail, b.gestureHint, b.matchTimer, b.keeperTimer).forEach { testo ->
            assertFalse(testo.isClickable)
            assertFalse(testo.isFocusable)
        }
    }

    // --- tondo da 192dp ---

    @Test
    @Config(qualifiers = "w192dp-h192dp-round-notnight")
    fun `tondo da 192dp, cifre a 58sp, lati da 78x72dp, strisce centrate e bersagli separati`() {
        val q = apri()
        assertTrue("la configurazione non e' tonda", RuntimeEnvironment.getApplication().resources.configuration.isScreenRound)
        q.applica(stato("AV", "40", giochi = "6-4 · 4-3", periodo = "Set 2"))

        verificaCifre(q, 58f)
        verificaLati(q)
        verificaStrisce(q)
        verificaBersagli(q)
    }

    @Test
    @Config(qualifiers = "w192dp-h192dp-round-notnight")
    fun `tondo da 192dp, il gruppo cronometro e K sta dentro l'anello`() {
        val q = apri()
        q.applica(stato("1", "0", hasClock = true))
        q.viewModel.syncMatchTimer(5_052_000L, isRunning = false)
        q.viewModel.setKeeperTimerState(KeeperTimerState.Running(252))
        shadowOf(Looper.getMainLooper()).idle()

        val b = q.binding
        val cronometro = b.matchTimer
        val portiere = b.keeperTimer
        assertEquals("84:12", cronometro.text.toString())
        assertEquals("K 4:12", portiere.text.toString())

        val centro = q.dp(b.root.width) / 2f
        // Il cronometro e' allineato a destra, il K a sinistra: dal centro verso fuori.
        val sinistra = q.dp(cronometro.right) - q.dp(penna(cronometro, 20f).measureText("84:12").toInt())
        val destra = q.dp(portiere.left) + q.dp(penna(portiere, 14f).measureText("K 4:12").toInt())
        // Raggio interno dell'anello: la ProgressBar ha innerRadiusRatio 2.3, quindi 192/2.3 = 83.5dp.
        val raggio = centro * 2f / 2.3f
        // Fascia A: 18-48dp, centro a 33; la corda si misura all'altezza del bordo alto del testo.
        val y = q.dp(cronometro.top) + (q.dp(cronometro.height) - 24f) / 2f
        val semicorda = sqrt(raggio * raggio - (centro - y) * (centro - y))
        assertTrue("il cronometro esce a sinistra: $sinistra contro ${centro - semicorda}", sinistra >= centro - semicorda)
        assertTrue("il K esce a destra: $destra contro ${centro + semicorda}", destra <= centro + semicorda)
        // Le posizioni sono fisse: il K non si sposta quando il testo cambia.
        val primaDelCambio = portiere.left
        q.viewModel.setKeeperTimerState(KeeperTimerState.Finished)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(primaDelCambio, portiere.left)
    }

    @Test
    @Config(qualifiers = "w192dp-h192dp-round-notnight")
    fun `nessun testo della fascia D e della fascia A e' tagliato`() {
        val q = apri()
        q.applica(stato("0", "0", giochi = "6-4 · 3-6 · 7-5 · 6-2 · 4-3", periodo = "Set 5"))
        val b = q.binding

        // Un cinque set e' il peggior caso di D: si stringe da sola fino a 9sp, e a 9sp deve entrare.
        val dettaglio = b.faceDetail.text.toString()
        assertEquals("SET 5 · 6-4 · 3-6 · 7-5 · 6-2", dettaglio)
        val minimo = penna(b.faceDetail, 9f).measureText(dettaglio)
        assertTrue("D a 9sp e' larga ${minimo}px, la vista ${larghezzaUtile(b.faceDetail)}px", minimo <= larghezzaUtile(b.faceDetail))

        // Il margine di corda di D (16dp) lascia la meta' superiore del cerchio all'altezza giusta.
        assertEquals(16f, q.dp(b.faceDetail.left), 0.5f)
        // La fascia A, a 20sp, entra nel suo spazio anche col cronometro piu' largo.
        val cronometro = penna(b.matchTimer, 20f).measureText("88:88")
        assertTrue(cronometro <= larghezzaUtile(b.matchTimer))
    }

    @Test
    @Config(qualifiers = "w192dp-h192dp-round-notnight")
    fun `l'etichetta della fascia A senza cronometro e' bianca`() {
        val q = apri()
        q.applica(stato("0", "15", giochi = "6-4 · 4-3", periodo = "Set 2"))

        assertEquals("4 – 3", q.binding.matchTimer.text.toString())
        assertEquals(0xFFFFFFFF.toInt(), q.binding.matchTimer.currentTextColor)
    }

    // --- tondo da 227dp ---

    @Test
    @Config(qualifiers = "w227dp-h227dp-round-notnight")
    fun `tondo da 227dp, cifre a 68sp, pallino da 10dp, strisce da 33x5dp`() {
        val q = apri()
        q.applica(stato("AV", "40", giochi = "6-4", periodo = "Set 2", servizio = 1))

        verificaCifre(q, 68f)
        verificaLati(q)
        verificaStrisce(q)
        verificaBersagli(q)
        val b = q.binding
        assertEquals(10f, q.dp(b.team1ServeDot.width), 0.5f)
        assertEquals(33f, q.dp(b.team1Stripe.width), 0.5f)
        assertEquals(5f, q.dp(b.team1Stripe.height), 0.5f)
    }

    // --- quadrato ---

    @Test
    @Config(qualifiers = "w192dp-h192dp-notround-notnight")
    fun `quadrato, i margini di fascia sono a zero e le misure restano quelle del tondo`() {
        val q = apri()
        q.applica(stato("AV", "40", giochi = "6-4", periodo = "Set 2", servizio = 2))
        val b = q.binding

        assertFalse(RuntimeEnvironment.getApplication().resources.configuration.isScreenRound)
        verificaCifre(q, 58f)
        verificaLati(q)
        verificaStrisce(q)
        verificaBersagli(q)
        // Margini a 0: la riga di stato e il dettaglio arrivano ai bordi dello schermo.
        assertEquals(0, b.gestureHint.left)
        assertEquals(0, b.faceDetail.left)
        // L'anello resta, bianco di nulla: e' la prima vista della radice.
        assertTrue(b.keeperProgressBar.parent === b.root)
    }

    @Test
    @Config(qualifiers = "w227dp-h227dp-notround-notnight")
    fun `quadrato grande, misure del tondo grande e margini a zero`() {
        val q = apri()
        q.applica(stato("AV", "40", giochi = "6-4", periodo = "Set 2"))
        val b = q.binding

        verificaCifre(q, 68f)
        verificaLati(q)
        verificaStrisce(q)
        assertEquals(0, b.gestureHint.left)
    }

    @Test
    @Config(qualifiers = "w192dp-h192dp-round-notnight")
    fun `il fondo del quadrante e' nero puro e il colore delle cifre bianco`() {
        val q = apri()
        val sfondo = (q.binding.root.background as? ColorDrawable)?.color
        assertEquals(null, sfondo)
        assertEquals(0xFFFFFFFF.toInt(), q.binding.team1Score.currentTextColor)
    }
}
