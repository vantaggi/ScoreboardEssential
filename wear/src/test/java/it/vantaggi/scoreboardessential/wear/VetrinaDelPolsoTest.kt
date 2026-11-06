package it.vantaggi.scoreboardessential.wear

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Looper
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.shared.communication.ConnectionState
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assume.assumeTrue
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
import java.io.FileOutputStream

/**
 * Le immagini del README (vetrina), non un controllo: il quadrante vero, con i dati finti di
 * una partita di padel, salvato in `wear/build/vetrina/`. Gira solo con `VETRINA=1`
 * (`scripts/vetrina.sh`), cosi' la CI non scrive immagini a ogni build.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VetrinaDelPolsoTest {
    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var viewModel: WearViewModel

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun apri() {
        assumeTrue("solo con VETRINA=1", System.getenv("VETRINA") == "1")
        val app = RuntimeEnvironment.getApplication()
        val telefono = Mockito.mock(OptimizedWearDataSync::class.java)
        Mockito.`when`(telefono.connectionState).thenReturn(MutableStateFlow(ConnectionState.Connected(1)))
        viewModel = WearViewModel(app, telefono, orologio = { SystemClock.uptimeMillis() + 1_000_000L })
        controller = Robolectric.buildActivity(MainActivity::class.java)
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
        idle()
        // Nomi finti: nessun giocatore vero nelle immagini pubbliche.
        viewModel.applicaCoppie(CoppieDalTelefono(rosa1 = listOf("Rossi", "Bianchi"), rosa2 = listOf("Verdi", "Neri")))
        idle()
    }

    /** Il quadrante intero; sul tondo cio' che sta fuori dal cerchio resta trasparente. */
    private fun cattura(nome: String) {
        idle()
        val decor = controller.get().window.decorView
        decor.requestLayout()
        idle()
        val w = decor.width
        val h = decor.height
        val faccia = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(faccia).apply {
            drawColor(Color.BLACK)
            decor.draw(this)
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            if (decor.resources.configuration.isScreenRound) {
                clipPath(Path().apply { addCircle(w / 2f, h / 2f, w / 2f, Path.Direction.CW) })
            }
            drawBitmap(faccia, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG))
        }
        val file = File("build/vetrina/$nome.png")
        file.parentFile!!.mkdirs()
        FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /**
     * Una partita finta punto per punto: un fotogramma per punto, per la GIF del teaser, e la
     * copertina a 3-2 e 30-15.
     */
    private fun partita(forma: String) {
        apri()
        val p1 = ScoringEvent.Point(1)
        val p2 = ScoringEvent.Point(2)
        // Tre game a due, poi il game in corso.
        val base = game(1) + game(2) + game(1) + game(2) + game(1)
        viewModel.applyStateV2(statoPadel(base + listOf(p2, p1, p1)))
        idle()
        cattura("polso-$forma-copertina")
        val giro = listOf(p1, p2, p1, p1, p2, p2, p1, p1)
        var eventi = base
        viewModel.applyStateV2(statoPadel(eventi))
        cattura("polso-$forma-teaser-00")
        giro.forEachIndexed { i, punto ->
            eventi = eventi + punto
            viewModel.applyStateV2(statoPadel(eventi))
            cattura("polso-$forma-teaser-%02d".format(i + 1))
        }
    }

    @Test
    @Config(qualifiers = "en-w227dp-h227dp-round-notnight-xhdpi")
    fun tondo() = partita("tondo")

    @Test
    @Config(qualifiers = "en-w192dp-h192dp-notround-notnight-xhdpi")
    fun quadrato() = partita("quadrato")
}
