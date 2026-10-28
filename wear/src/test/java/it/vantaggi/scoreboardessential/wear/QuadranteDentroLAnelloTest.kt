package it.vantaggi.scoreboardessential.wear

import android.graphics.Rect
import android.graphics.RectF
import android.os.Looper
import android.os.SystemClock
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
import java.io.File
import kotlin.math.hypot
import kotlin.math.max

/**
 * Ogni testo e segno del quadrante sta ad almeno 3dp dal bordo interno dell'anello del portiere, su
 * ogni forma: tondo da 192 e da 227dp, quadrato piccolo e grande.
 *
 * Dalle catture del 28 ottobre 2026 (revisione della home): l'angolo alto del cronometro, la capsula
 * CHI? e le frasi lunghe della riga di stato entravano nell'anello; i pallini del servizio stavano a
 * 3dp dalla cornice, e in ambient lo spostamento anti burn-in (4dp) li portava fuori. Prima c'era solo
 * il conto dei puntini del menu (PuntiniDelMenuTest), e non l'ha visto nessuno.
 *
 * Si misura l'inchiostro vero, con la grafica nativa: il rettangolo dei glifi per i testi, la vista
 * per strisce, pallini e capsula, i tre puntini per il glifo del menu. L'anello e' quello che si
 * disegna: sul tondo un cerchio con bordo interno a larghezza/innerRadiusRatio dal centro (letto dal
 * drawable); sul quadrato il rettangolo arrotondato di AnelloQuadrato, coi suoi dimens. Lo stesso
 * margine vale anche per i pallini, che nel padel stanno sul quadrante senza anello: dentro l'anello
 * restano lontani dalla cornice anche con lo spostamento dell'ambient.
 *
 * Gira dalla cartella del modulo (`wear/`), come PuntiniDelMenuTest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QuadranteDentroLAnelloTest {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: WearViewModel
    private lateinit var fasce: ViewGroup
    private var densita = 1f

    private fun apri() {
        val app = RuntimeEnvironment.getApplication()
        val telefono = Mockito.mock(OptimizedWearDataSync::class.java)
        Mockito.`when`(telefono.connectionState).thenReturn(MutableStateFlow(ConnectionState.Disconnected))
        viewModel = WearViewModel(app, telefono, orologio = { SystemClock.uptimeMillis() + 1_000_000L })
        val controller = Robolectric.buildActivity(MainActivity::class.java)
        val fabbrica =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
            }
        ViewModelProvider(controller.get(), fabbrica)[WearViewModel::class.java]
        controller.create().start().visible()
        idle()
        val contenuto = controller.get().findViewById<ViewGroup>(android.R.id.content)
        binding = ActivityMainBinding.bind(contenuto.getChildAt(0))
        fasce = (binding.root as ViewGroup).getChildAt(1) as ViewGroup
        densita = app.resources.displayMetrics.density
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun applica(stato: WearScoreState) {
        viewModel.applyStateV2(stato)
        idle()
    }

    private fun stato(
        primo: String,
        secondo: String,
        calcio: Boolean,
        servizio: Int = 0,
        giocatore: Int = 0,
    ) = WearScoreState(
        side1Primary = primo,
        side1Secondary = if (calcio) "" else "6-4 · 4-3",
        side2Primary = secondo,
        side2Secondary = "",
        periodLabel = if (calcio) "" else "Set 2",
        hasClock = calcio,
        hasAuxTimer = calcio,
        attributesScorer = calcio,
        decrementIsUndo = !calcio,
        sportId = if (calcio) "football" else "padel",
        sportLabel = "",
        sportIds = emptyList(),
        sportLabels = emptyList(),
        matchInProgress = true,
        matchOver = false,
        eventLog = "",
        servingSide = servizio,
        servingSlot = giocatore,
    )

    /** Il rettangolo di una vista nelle coordinate del quadrante, in px. */
    private fun rettangolo(vista: View): RectF {
        val r = Rect()
        vista.getDrawingRect(r)
        fasce.offsetDescendantRectToMyCoords(vista, r)
        return RectF(r)
    }

    /** L'inchiostro di un testo a una riga, dove la layout lo disegna, nelle coordinate del quadrante. */
    private fun inchiostro(vista: TextView): RectF {
        val testo = vista.text.toString()
        assertTrue("${nome(vista)} e' vuoto", testo.isNotEmpty())
        val layout = vista.layout
        val glifi = Rect()
        vista.paint.getTextBounds(testo, 0, testo.length, glifi)
        val origine = rettangolo(vista)
        val x = origine.left + vista.compoundPaddingLeft + layout.getLineLeft(0)
        val base = origine.top + vista.extendedPaddingTop + layout.getLineBaseline(0)
        return RectF(x + glifi.left, base + glifi.top, x + layout.getLineRight(0) - layout.getLineLeft(0), base + glifi.bottom)
    }

    /** I tre puntini del glifo del menu: a +-8dp dal centro dell'immagine, di 2dp di raggio. */
    private fun puntiniDelMenu(): RectF {
        val r = rettangolo(binding.menuGlyph)
        return RectF(r.centerX() - 10 * densita, r.centerY() - 2 * densita, r.centerX() + 10 * densita, r.centerY() + 2 * densita)
    }

    private fun nome(vista: View) = vista.resources.getResourceEntryName(vista.id)

    private fun dp(px: Float) = px / densita

    /**
     * Quanto un rettangolo sta dentro il bordo interno dell'anello, in dp (negativo: lo attraversa).
     * Sul tondo conta lo spigolo piu' lontano dal centro; sul quadrato il rettangolo arrotondato.
     */
    private fun ariaDallAnello(r: RectF): Float {
        val lato = fasce.width.toFloat()
        val alto = fasce.height.toFloat()
        val risorse = fasce.resources
        if (risorse.configuration.isScreenRound) {
            val rapporto =
                Regex("""innerRadiusRatio="([0-9.]+)"""")
                    .find(File("src/main/res/drawable/circular_progress_bar.xml").readText())!!
                    .groupValues[1]
                    .toFloat()
            val interno = lato / rapporto
            val cx = lato / 2f
            val cy = alto / 2f
            val lontano =
                listOf(r.left to r.top, r.right to r.top, r.left to r.bottom, r.right to r.bottom).maxOf { (x, y) ->
                    hypot(
                        x - cx,
                        y - cy,
                    )
                }
            return dp(interno - lontano)
        }
        val spessore = risorse.getDimension(R.dimen.keeper_ring_thickness)
        val dentro = risorse.getDimension(R.dimen.keeper_ring_square_inset) + spessore
        val curva = risorse.getDimension(R.dimen.keeper_ring_square_radius) - spessore
        val sinistra = r.left - dentro
        val destra = lato - dentro - r.right
        val sopra = r.top - dentro
        val sotto = alto - dentro - r.bottom
        var aria = minOf(sinistra, destra, sopra, sotto)
        // Negli angoli il bordo interno e' un arco di raggio `curva`: lo spigolo deve starci dentro.
        listOf(
            Triple(r.left, r.top, dentro + curva to dentro + curva),
            Triple(r.right, r.top, lato - dentro - curva to dentro + curva),
            Triple(r.left, r.bottom, dentro + curva to alto - dentro - curva),
            Triple(r.right, r.bottom, lato - dentro - curva to alto - dentro - curva),
        ).forEach { (x, y, centro) ->
            val (ax, ay) = centro
            val nellAngoloX = if (x < lato / 2) x < ax else x > ax
            val nellAngoloY = if (y < alto / 2) y < ay else y > ay
            if (nellAngoloX && nellAngoloY) aria = minOf(aria, curva - hypot(x - ax, y - ay))
        }
        return dp(aria)
    }

    private fun verificaDentro(
        cosa: String,
        r: RectF,
    ) {
        val aria = ariaDallAnello(r)
        assertTrue("$cosa sta a %.1fdp dal bordo interno dell'anello, ne servono 3 (%s)".format(aria, r), aria >= 3f - 0.01f)
    }

    /** Il quadrante del calcio col portiere che corre, la frase piu' lunga e, a parte, CHI?. */
    private fun verificaCalcio() {
        apri()
        applica(stato("88", "88", calcio = true))
        viewModel.syncMatchTimer(5_052_000L, isRunning = true)
        viewModel.setKeeperTimerState(KeeperTimerState.Running(252))
        idle()
        assertEquals(View.VISIBLE, binding.keeperProgressBar.visibility)
        assertEquals("84:12", binding.matchTimer.text.toString())
        assertEquals("K 4:12", binding.keeperTimer.text.toString())

        verificaDentro("il cronometro", inchiostro(binding.matchTimer))
        verificaDentro("il portiere", inchiostro(binding.keeperTimer))
        verificaDentro("le cifre della squadra 1", inchiostro(binding.team1Score))
        verificaDentro("le cifre della squadra 2", inchiostro(binding.team2Score))
        verificaDentro("la striscia della squadra 1", rettangolo(binding.team1Stripe))
        verificaDentro("la striscia della squadra 2", rettangolo(binding.team2Stripe))
        verificaDentro("i puntini del menu", puntiniDelMenu())

        // La frase piu' lunga della riga di stato, stretta dall'autoSize come a schermo.
        val app = RuntimeEnvironment.getApplication()
        listOf(
            app.getString(R.string.wear_status_offline_at, "18:42"),
            app.getString(R.string.wear_status_not_delivered, 999),
            app.getString(R.string.wear_status_sport_unchanged),
            app.getString(R.string.wear_status_phone_waiting),
        ).forEach { frase ->
            binding.gestureHint.text = frase
            fasce.requestLayout()
            idle()
            val riga = binding.gestureHint
            assertEquals("\"$frase\" va a capo o si taglia", frase.length, riga.layout.getLineEnd(0))
            assertEquals("\"$frase\" ha i puntini", 0, riga.layout.getEllipsisCount(0))
            verificaDentro("la riga di stato \"$frase\"", inchiostro(riga))
        }

        // CHI? nella sua fascia, al posto della riga di stato.
        binding.chiCapsule.visibility = View.VISIBLE
        fasce.requestLayout()
        idle()
        verificaDentro("la capsula CHI?", rettangolo(binding.chiCapsule))
    }

    /** Il padel con due pallini per lato (batte il secondo giocatore): il caso piu' largo. */
    private fun verificaPallini() {
        apri()
        listOf(1, 2).forEach { lato ->
            applica(stato("AV", "40", calcio = false, servizio = lato, giocatore = 2))
            val pallini =
                if (lato ==
                    1
                ) {
                    listOf(binding.team1ServeDot, binding.team1ServeDotSecond)
                } else {
                    listOf(binding.team2ServeDot, binding.team2ServeDotSecond)
                }
            pallini.forEach {
                assertEquals(View.VISIBLE, it.visibility)
                verificaDentro("il pallino ${nome(it)}", rettangolo(it))
            }
            verificaDentro("i game del set", inchiostro(binding.matchTimer))
        }
    }

    /**
     * Il margine dalla cornice per cio' che resta in ambient (cifre, tempo, pallini): con lo
     * spostamento anti burn-in di 4dp nessuno spigolo esce dallo schermo. Sul tondo il bordo dello
     * schermo e' il cerchio intero; sul quadrato il bordo.
     */
    private fun verificaCornice(r: RectF) {
        val lato = fasce.width.toFloat()
        val spostamento = 4 * densita
        val margine =
            if (fasce.resources.configuration.isScreenRound) {
                val c = lato / 2f
                c -
                    listOf(r.left to r.top, r.right to r.top, r.left to r.bottom, r.right to r.bottom).maxOf { (x, y) ->
                        hypot(x - c, y - c)
                    }
            } else {
                minOf(r.left, r.top, lato - r.right, lato - r.bottom)
            }
        assertTrue(
            "uno spigolo sta a %.1fdp dalla cornice, con il burn-in ne servono piu' di 4".format(dp(margine)),
            margine > max(spostamento, 0f),
        )
    }

    private fun verificaTutto() {
        verificaCalcio()
        verificaPallini()
        listOf(binding.team1ServeDot, binding.team1ServeDotSecond, binding.team2ServeDot, binding.team2ServeDotSecond)
            .filter { it.visibility == View.VISIBLE }
            .forEach { verificaCornice(rettangolo(it)) }
    }

    @Test
    @Config(qualifiers = "w192dp-h192dp-round-notnight")
    fun `tondo da 192dp`() = verificaTutto()

    @Test
    @Config(qualifiers = "w227dp-h227dp-round-notnight")
    fun `tondo da 227dp`() = verificaTutto()

    @Test
    @Config(qualifiers = "w192dp-h192dp-notround-notnight")
    fun `quadrato da 192dp`() = verificaTutto()

    @Test
    @Config(qualifiers = "w227dp-h227dp-notround-notnight")
    fun `quadrato da 227dp`() = verificaTutto()

    @Test
    @Config(qualifiers = "it-w192dp-h192dp-round-notnight")
    fun `tondo da 192dp in italiano`() = verificaCalcio()

    @Test
    @Config(qualifiers = "it-w192dp-h192dp-notround-notnight")
    fun `quadrato da 192dp in italiano`() = verificaCalcio()

    /** Nel calcio la riga di stato sale in D, nella racchetta resta in E: non salta col portiere. */
    @Test
    @Config(qualifiers = "w192dp-h192dp-round-notnight")
    fun `la riga di stato sta in D nel calcio e in E nella racchetta, anche col portiere che parte`() {
        apri()
        applica(stato("0", "0", calcio = false))
        assertEquals(150f, dp(rettangolo(binding.gestureHint).top), 0.6f)
        applica(stato("0", "0", calcio = true))
        assertEquals(132f, dp(rettangolo(binding.gestureHint).top), 0.6f)
        val prima = rettangolo(binding.gestureHint)
        viewModel.setKeeperTimerState(KeeperTimerState.Running(252))
        idle()
        assertEquals("la riga si e' spostata quando e' partito il portiere", prima, rettangolo(binding.gestureHint))
    }
}
