package it.vantaggi.scoreboardessential.wear

import android.graphics.PointF
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.hypot

/**
 * MISURA, non verifica: quanti elementi del quadrante non rispettano la geometria di 08-wearable della
 * UI Constitution (passo 1 di "Adopting", 31 ottobre 2026). Scrive `build/misura/geometria-<forma>.txt`,
 * che `scripts/misura-ui.py` riporta nel report; non fallisce per un margine, solo se non ha misurato
 * niente. Il passo a del piano (PR 2) la trasforma in un test che fallisce e corregge cio' che trova.
 *
 * Le regole misurate, su tondo da 192 e 227dp e quadrato da 192 e 227dp, in attivo e in ambient:
 * - ogni testo e segno sta ad almeno 3dp dal bordo interno dell'anello del portiere, quando l'anello
 *   si disegna (in ambient no);
 * - ogni testo e segno sta ad almeno 8dp dalla cornice: 4dp di spostamento anti burn-in piu' 4dp d'aria.
 *   Sul tondo la cornice e' il cerchio intero, sul quadrato il bordo.
 *
 * Si misura l'inchiostro vero dei testi (il rettangolo dei glifi), le viste per strisce, pallini e
 * capsula, i due puntini esterni per il glifo del menu, come in [QuadranteDentroLAnelloTest], che
 * resta il test che fallisce per i 3dp dall'anello in attivo. Gira dalla cartella del modulo (`wear/`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MisuraGeometriaDelPolsoTest {
    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: WearViewModel
    private lateinit var fasce: ViewGroup
    private var densita = 1f

    private val trovati = mutableListOf<String>()
    private var misurati = 0

    private fun apri() {
        val app = RuntimeEnvironment.getApplication()
        val telefono = Mockito.mock(OptimizedWearDataSync::class.java)
        Mockito.`when`(telefono.connectionState).thenReturn(MutableStateFlow(ConnectionState.Disconnected))
        viewModel = WearViewModel(app, telefono, orologio = { SystemClock.uptimeMillis() + 1_000_000L })
        controller = Robolectric.buildActivity(MainActivity::class.java)
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

    private fun rettangolo(vista: View): RectF {
        val r = Rect()
        vista.getDrawingRect(r)
        fasce.offsetDescendantRectToMyCoords(vista, r)
        return RectF(r)
    }

    /** L'inchiostro di un testo a una riga, nelle coordinate del quadrante; null se il testo e' vuoto. */
    private fun inchiostro(vista: TextView): RectF? {
        val testo = vista.text.toString()
        val layout = vista.layout ?: return null
        if (testo.isEmpty()) return null
        val glifi = Rect()
        vista.paint.getTextBounds(testo, 0, layout.getLineEnd(0), glifi)
        val origine = rettangolo(vista)
        val x = origine.left + vista.compoundPaddingLeft + layout.getLineLeft(0)
        val base = origine.top + vista.totalPaddingTop + layout.getLineBaseline(0)
        return RectF(x + glifi.left, base + glifi.top, x + layout.getLineRight(0) - layout.getLineLeft(0), base + glifi.bottom)
    }

    private fun dp(px: Float) = px / densita

    private fun visibile(vista: View): Boolean {
        var v: View? = vista
        while (v != null && v !== fasce) {
            if (v.visibility != View.VISIBLE) return false
            v = v.parent as? View
        }
        return vista.width > 0 && vista.height > 0
    }

    /** Distanza dalla cornice in dp: sul tondo dal cerchio dello schermo, sul quadrato dal bordo. */
    private fun ariaDallaCornice(r: RectF): Float {
        val lato = fasce.width.toFloat()
        val alto = fasce.height.toFloat()
        if (fasce.resources.configuration.isScreenRound) {
            val cx = lato / 2f
            val cy = alto / 2f
            val lontano = angoli(r).maxOf { (x, y) -> hypot(x - cx, y - cy) }
            return dp(minOf(lato, alto) / 2f - lontano)
        }
        return dp(minOf(r.left, r.top, lato - r.right, alto - r.bottom))
    }

    private fun angoli(r: RectF) = listOf(r.left to r.top, r.right to r.top, r.left to r.bottom, r.right to r.bottom)

    /** Distanza dal bordo interno dell'anello del portiere in dp, come in [QuadranteDentroLAnelloTest]. */
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
            val lontano = angoli(r).maxOf { (x, y) -> hypot(x - lato / 2f, y - alto / 2f) }
            return dp(interno - lontano)
        }
        val spessore = risorse.getDimension(R.dimen.keeper_ring_thickness)
        val dentro = risorse.getDimension(R.dimen.keeper_ring_square_inset) + spessore
        val curva = risorse.getDimension(R.dimen.keeper_ring_square_radius) - spessore
        var aria = minOf(r.left - dentro, lato - dentro - r.right, r.top - dentro, alto - dentro - r.bottom)
        val sx = dentro + curva
        val dx = lato - dentro - curva
        val sy = dentro + curva
        val gy = alto - dentro - curva
        if (r.left < sx && r.top < sy) aria = minOf(aria, curva - hypot(r.left - sx, r.top - sy))
        if (r.right > dx && r.top < sy) aria = minOf(aria, curva - hypot(r.right - dx, r.top - sy))
        if (r.left < sx && r.bottom > gy) aria = minOf(aria, curva - hypot(r.left - sx, r.bottom - gy))
        if (r.right > dx && r.bottom > gy) aria = minOf(aria, curva - hypot(r.right - dx, r.bottom - gy))
        return dp(aria)
    }

    /** Ogni testo e segno a vista nel quadrante, col suo rettangolo. */
    private fun elementi(): List<Pair<String, RectF>> {
        val testi =
            listOf(
                "il cronometro o i game" to binding.matchTimer,
                "il portiere" to binding.keeperTimer,
                "le cifre della squadra 1" to binding.team1Score,
                "le cifre della squadra 2" to binding.team2Score,
                "il dettaglio" to binding.faceDetail,
                "la riga di stato" to binding.gestureHint,
            ).filter { visibile(it.second) }
                .mapNotNull { (nome, vista) -> inchiostro(vista)?.let { nome to it } }
        val segni =
            listOf(
                "la striscia della squadra 1" to binding.team1Stripe,
                "la striscia della squadra 2" to binding.team2Stripe,
                "il pallino team1ServeDot" to binding.team1ServeDot,
                "il pallino team1ServeDotSecond" to binding.team1ServeDotSecond,
                "il pallino team2ServeDot" to binding.team2ServeDot,
                "il pallino team2ServeDotSecond" to binding.team2ServeDotSecond,
                "la capsula CHI?" to binding.chiCapsule,
            ).filter { visibile(it.second) }
                .map { (nome, vista) -> nome to rettangolo(vista) }
        val menu =
            if (visibile(binding.menuGlyph)) {
                val r = rettangolo(binding.menuGlyph)
                val raggio = 2 * densita
                listOf(-8f, 8f).map { dx ->
                    val c = PointF(r.centerX() + dx * densita, r.centerY())
                    "un puntino del menu" to RectF(c.x - raggio, c.y - raggio, c.x + raggio, c.y + raggio)
                }
            } else {
                emptyList()
            }
        return testi + segni + menu
    }

    private fun misura(caso: String) {
        val anello = visibile(binding.keeperProgressBar)
        elementi().forEach { (nome, r) ->
            misurati++
            val cornice = ariaDallaCornice(r)
            if (cornice < 8f - 0.01f) trovati += "$caso: $nome a %.1fdp dalla cornice (ne servono 8)".format(cornice)
            if (anello) {
                val dallAnello = ariaDallAnello(r)
                if (dallAnello < 3f - 0.01f) trovati += "$caso: $nome a %.1fdp dall'anello (ne servono 3)".format(dallAnello)
            }
        }
    }

    private fun ambient(on: Boolean) {
        controller.get().applyAmbient(on)
        idle()
    }

    private fun misuraTutto(forma: String) {
        apri()
        viewModel.applyStateV2(stato("88", "88", calcio = true))
        viewModel.syncMatchTimer(5_052_000L, isRunning = true)
        viewModel.setKeeperTimerState(KeeperTimerState.Running(252))
        idle()
        misura("calcio, attivo")
        binding.chiCapsule.visibility = View.VISIBLE
        fasce.requestLayout()
        idle()
        misura("calcio con CHI?, attivo")
        binding.chiCapsule.visibility = View.GONE
        fasce.requestLayout()
        idle()
        ambient(true)
        misura("calcio, ambient")
        ambient(false)

        listOf(1, 2).forEach { lato ->
            viewModel.applyStateV2(stato("AV", "40", calcio = false, servizio = lato, giocatore = 2))
            idle()
            misura("padel, serve la squadra $lato, attivo")
            ambient(true)
            misura("padel, serve la squadra $lato, ambient")
            ambient(false)
        }

        assertTrue("non ho misurato niente su $forma", misurati > 0)
        val file = File("build/misura/geometria-$forma.txt")
        file.parentFile.mkdirs()
        val config = fasce.resources.configuration
        val titolo = "${if (config.isScreenRound) "Tondo" else "Quadrato"} da ${config.screenWidthDp}dp"
        file.writeText(
            buildString {
                appendLine("- $titolo: ${trovati.size} fuori misura su $misurati misure")
                trovati.forEach { appendLine("    - $it") }
            },
        )
    }

    @Test
    @Config(qualifiers = "it-w192dp-h192dp-round-notnight")
    fun `tondo da 192dp`() = misuraTutto("tondo-192")

    @Test
    @Config(qualifiers = "it-w227dp-h227dp-round-notnight")
    fun `tondo da 227dp`() = misuraTutto("tondo-227")

    @Test
    @Config(qualifiers = "it-w192dp-h192dp-notround-notnight")
    fun `quadrato da 192dp`() = misuraTutto("quadrato-192")

    @Test
    @Config(qualifiers = "it-w227dp-h227dp-notround-notnight")
    fun `quadrato da 227dp`() = misuraTutto("quadrato-227")
}
