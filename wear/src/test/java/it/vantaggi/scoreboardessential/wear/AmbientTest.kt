package it.vantaggi.scoreboardessential.wear

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import it.vantaggi.scoreboardessential.shared.communication.ConnectionState
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.wear.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

/** Il vibratore finto dell'ambient: del tocco interessa solo se il ViewModel l'ha preso. */
private class VibratoreAmbient : WearHaptics {
    var tick = 0

    override fun suona(pattern: LongArray) = Unit

    override fun annulla() = Unit

    override fun tick() {
        tick++
    }
}

/**
 * Ambient, guardia al risveglio e uscita dal quadrante (passo 9 della pista Orologio).
 *
 * Il tempo e' quello iniettato nella schermata ([MainActivity.orologio]): "500ms dopo il risveglio"
 * si prova spostando un numero, non aspettando. L'observer vero di androidx.wear non gira sotto
 * Robolectric (il sistema non lo chiama), quindi si pilota la stessa funzione che i suoi callback
 * chiamano: [MainActivity.applyAmbient].
 */
@RunWith(RobolectricTestRunner::class)
class AmbientTest {
    private var ora = 10_000L
    private val vibratore = VibratoreAmbient()
    private lateinit var telefono: OptimizedWearDataSync
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
        telefono = Mockito.mock(OptimizedWearDataSync::class.java)
        Mockito.`when`(telefono.connectionState).thenReturn(MutableStateFlow(ConnectionState.Disconnected))
        viewModel =
            WearViewModel(
                app,
                telefono,
                orologio = { SystemClock.uptimeMillis() + 1_000_000L },
                haptics = vibratore,
            )
        controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.get().orologio = { ora }
        val fabbrica =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = viewModel as T
            }
        ViewModelProvider(controller.get(), fabbrica)[WearViewModel::class.java]
        controller.create().start()
        idle()
        val contenuto = controller.get().findViewById<ViewGroup>(android.R.id.content)
        binding = ActivityMainBinding.bind(contenuto.getChildAt(0))
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun attivita() = controller.get()

    private fun stato(
        hasClock: Boolean,
        giochi: String = "",
        servizio: Int = 0,
    ) = WearScoreState(
        side1Primary = if (hasClock) "3" else "40",
        side1Secondary = giochi,
        side2Primary = if (hasClock) "2" else "15",
        side2Secondary = "",
        periodLabel = if (hasClock) "" else "Set 2",
        hasClock = hasClock,
        hasAuxTimer = hasClock,
        attributesScorer = false,
        decrementIsUndo = !hasClock,
        sportId = if (hasClock) "football" else "tennis",
        sportLabel = "",
        sportIds = emptyList(),
        sportLabels = emptyList(),
        matchInProgress = true,
        matchOver = false,
        eventLog = "",
        servingSide = servizio,
    )

    private fun applica(stato: WearScoreState) {
        viewModel.applyStateV2(stato)
        idle()
    }

    /** Una partita di calcio con tutto cio' che e' colorato acceso: strisce, K scaduto, riga rossa. */
    private fun calcioColorato() {
        viewModel.setTeamColor(1, Color.RED)
        viewModel.setTeamColor(2, Color.BLUE)
        applica(stato(hasClock = true))
        viewModel.syncMatchTimer(34 * 60_000L + 12_000L, isRunning = false)
        viewModel.setKeeperTimerState(KeeperTimerState.Finished)
        viewModel.mostraTransitorio(Transitorio.NonConfermato)
        idle()
    }

    private fun colore(id: Int) = ContextCompat.getColor(attivita(), id)

    /** Tutti i testi a vista, a qualunque profondita': l'ambient non ne deve lasciare uno colorato. */
    private fun testiVisibili(vista: View): List<TextView> =
        when {
            vista.visibility != View.VISIBLE -> emptyList()
            vista is ViewGroup -> (0 until vista.childCount).flatMap { testiVisibili(vista.getChildAt(it)) }
            vista is TextView && vista.text.isNotBlank() -> listOf(vista)
            else -> emptyList()
        }

    private fun eGrigio(colore: Int) = Color.red(colore) == Color.green(colore) && Color.green(colore) == Color.blue(colore)

    @Test
    fun `in ambient le cifre sono light e il tempo del calcio e' in minuti`() {
        calcioColorato()
        assertEquals("34:12", binding.matchTimer.text.toString())
        val prima = binding.team1Score.typeface

        attivita().applyAmbient(true)
        idle()

        assertNotSame("in ambient cambia il carattere", prima, binding.team1Score.typeface)
        assertSame("le due cifre hanno lo stesso carattere", binding.team1Score.typeface, binding.team2Score.typeface)
        assertSame("e il cronometro lo stesso delle cifre", binding.team1Score.typeface, binding.matchTimer.typeface)
        assertEquals("34'", binding.matchTimer.text.toString())
    }

    @Test
    fun `in ambient non resta niente di colorato`() {
        calcioColorato()
        // Fuori dall'ambient i colori ci sono: senza questo il test non direbbe niente.
        assertEquals(View.VISIBLE, binding.team1Stripe.visibility)
        assertEquals(colore(R.color.error_red), binding.gestureHint.currentTextColor)
        assertEquals(colore(R.color.error_red), binding.keeperTimer.currentTextColor)

        attivita().applyAmbient(true)
        idle()

        listOf(
            binding.team1Stripe,
            binding.team2Stripe,
            binding.keeperProgressBar,
            binding.keeperTimer,
            binding.faceDetail,
            binding.menuGlyph,
        ).forEach { assertEquals("${it.id} in ambient", View.INVISIBLE, it.visibility) }
        assertNotEquals(View.VISIBLE, binding.chiCapsule.visibility)
        // L'anomalia si dice ancora, ma in grigio chiaro e non in rosso.
        assertEquals(attivita().getString(R.string.wear_status_not_confirmed), binding.gestureHint.text.toString())
        assertEquals(colore(R.color.ambient_gray), binding.gestureHint.currentTextColor)
        testiVisibili(binding.root).forEach {
            assertTrue("\"${it.text}\" ha un colore (#${Integer.toHexString(it.currentTextColor)})", eGrigio(it.currentTextColor))
        }
    }

    @Test
    fun `uscendo dall'ambient tornano colori, tempo con i secondi e cifre bold`() {
        calcioColorato()
        attivita().applyAmbient(true)
        idle()
        val light = binding.team1Score.typeface

        attivita().applyAmbient(false)
        idle()

        assertEquals("34:12", binding.matchTimer.text.toString())
        assertNotSame("fuori dall'ambient non restano le cifre light", light, binding.team1Score.typeface)
        assertNotSame(light, binding.matchTimer.typeface)
        assertEquals(View.VISIBLE, binding.team1Stripe.visibility)
        assertEquals(View.VISIBLE, binding.team2Stripe.visibility)
        assertEquals(View.VISIBLE, binding.keeperTimer.visibility)
        assertEquals(View.VISIBLE, binding.keeperProgressBar.visibility)
        assertEquals(View.VISIBLE, binding.menuGlyph.visibility)
        assertEquals(colore(R.color.error_red), binding.gestureHint.currentTextColor)
        assertEquals(colore(R.color.error_red), binding.keeperTimer.currentTextColor)
    }

    @Test
    fun `in ambient il suggerimento tace e la partita finita parla`() {
        applica(stato(hasClock = true))
        assertEquals(Frase.TieniMeno, viewModel.statoFiducia.value)
        assertEquals(attivita().getString(R.string.wear_hint_minus), binding.gestureHint.text.toString())

        attivita().applyAmbient(true)
        idle()
        assertEquals("", binding.gestureHint.text.toString())

        applica(stato(hasClock = true).copy(matchOver = true))
        assertEquals(Frase.PartitaFinita, viewModel.statoFiducia.value)
        assertEquals(attivita().getString(R.string.wear_match_over), binding.gestureHint.text.toString())
        assertEquals(colore(R.color.ambient_gray), binding.gestureHint.currentTextColor)
    }

    @Test
    fun `in ambient i transitori positivi tacciono e le anomalie parlano`() {
        applica(stato(hasClock = true))
        attivita().applyAmbient(true)
        idle()

        listOf(Transitorio.Consegnati(3), Transitorio.CambioSport, Transitorio.Chiusura).forEach {
            viewModel.mostraTransitorio(it)
            idle()
            assertEquals("${it::class.simpleName} in ambient", "", binding.gestureHint.text.toString())
        }

        viewModel.mostraTransitorio(Transitorio.SportNonCambiato)
        idle()
        assertEquals(attivita().getString(R.string.wear_status_sport_unchanged), binding.gestureHint.text.toString())
        assertEquals(colore(R.color.ambient_gray), binding.gestureHint.currentTextColor)
    }

    @Test
    fun `in ambient il portiere non cambia visibilita' ai tick`() {
        applica(stato(hasClock = true))
        attivita().applyAmbient(true)
        idle()
        // Una visibilita' che il render non produce mai: se dopo il tick e' cambiata, ha toccato il K.
        binding.keeperTimer.visibility = View.GONE

        viewModel.setKeeperTimerState(KeeperTimerState.Running(119))
        idle()

        assertEquals(View.GONE, binding.keeperTimer.visibility)

        // All'uscita il portiere si ridisegna con lo stato di adesso.
        attivita().applyAmbient(false)
        idle()
        assertEquals(View.VISIBLE, binding.keeperTimer.visibility)
    }

    @Test
    fun `in ambient il ciclo dei 15s non chiede il collegamento`() {
        controller.resume()
        idle()
        applica(stato(hasClock = true))

        attivita().applyAmbient(true)
        idle()
        val inAmbient = richiesteDiCollegamento()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(MainActivity.INTERVALLO_VERIFICA_MS * 3))
        assertEquals("in ambient niente richieste", inAmbient, richiesteDiCollegamento())

        attivita().applyAmbient(false)
        idle()
        val allUscita = richiesteDiCollegamento()
        assertTrue("l'uscita dall'ambient la richiede", allUscita > inAmbient)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(MainActivity.INTERVALLO_VERIFICA_MS))
        assertTrue("fuori dall'ambient il ciclo chiede", richiesteDiCollegamento() > allUscita)
    }

    private fun richiesteDiCollegamento() = Mockito.mockingDetails(telefono).invocations.count { it.method.name == "refreshConnection" }

    @Test
    fun `nella racchetta in ambient la fascia A e il pallino del servizio restano`() {
        applica(stato(hasClock = false, giochi = "6-4 · 4-3", servizio = 1))
        assertEquals("4 – 3", binding.matchTimer.text.toString())

        attivita().applyAmbient(true)
        idle()

        assertEquals("4 – 3", binding.matchTimer.text.toString())
        assertEquals("il pallino bianco del servizio e' grafica senza colore", View.VISIBLE, binding.team1ServeDot.visibility)
        assertEquals("40", binding.team1Score.text.toString())
    }

    @Test
    fun `lo spostamento anti burn-in cambia a ogni aggiornamento e torna a zero all'uscita`() {
        val quattroDp = 4 * attivita().resources.displayMetrics.density

        attivita().applyAmbient(true, burnIn = true)
        val primo = binding.root.translationX to binding.root.translationY
        assertEquals(quattroDp, Math.abs(primo.first), 0.01f)
        assertEquals(quattroDp, Math.abs(primo.second), 0.01f)

        attivita().aggiornaAmbient()
        assertNotEquals("a ogni aggiornamento lo schermo si sposta", primo, binding.root.translationX to binding.root.translationY)

        attivita().applyAmbient(false)
        assertEquals(0f, binding.root.translationX, 0f)
        assertEquals(0f, binding.root.translationY, 0f)
    }

    @Test
    fun `senza la richiesta del sistema lo schermo non si sposta`() {
        attivita().applyAmbient(true, burnIn = false)
        attivita().aggiornaAmbient()

        assertEquals(0f, binding.root.translationX, 0f)
        assertEquals(0f, binding.root.translationY, 0f)
    }

    // --- La guardia al risveglio ---

    @Test
    fun `il tocco entro 500ms dall'uscita dall'ambient non segna e dopo si`() {
        attivita().applyAmbient(true)
        attivita().applyAmbient(false)

        ora += MainActivity.GUARDIA_RISVEGLIO_MS - 1
        binding.team1Container.performClick()
        assertEquals("il primo tocco ha svegliato lo schermo, non ha segnato", 0, vibratore.tick)

        ora += 1
        binding.team1Container.performClick()
        assertEquals(1, vibratore.tick)
    }

    @Test
    fun `anche il tocco lungo e il lato destro stanno fermi durante la guardia`() {
        viewModel.setScores(2, 2)
        attivita().applyAmbient(true)
        attivita().applyAmbient(false)

        binding.team2Container.performClick()
        binding.team1Container.performLongClick()
        assertEquals("il tocco e' scartato", 0, vibratore.tick)
        assertEquals("il tocco lungo non toglie un punto", 2, viewModel.team1Score.value)

        ora += MainActivity.GUARDIA_RISVEGLIO_MS
        binding.team1Container.performLongClick()
        assertEquals(1, viewModel.team1Score.value)
    }

    @Test
    fun `senza risveglio i tocchi contano subito`() {
        binding.team2Container.performClick()

        assertEquals(1, vibratore.tick)
    }

    @Test
    fun `onResume apre la guardia di 500ms`() {
        controller.resume()
        idle()

        ora += MainActivity.GUARDIA_RISVEGLIO_MS - 1
        binding.team1Container.performClick()
        assertEquals(0, vibratore.tick)

        ora += 1
        binding.team1Container.performClick()
        assertEquals(1, vibratore.tick)
    }

    // --- Uscita dal quadrante ---

    @Test
    fun `il tema del quadrante non si chiude con lo swipe e lo stesso vale per la schermata`() {
        val tema = attivita().theme
        val attributo = intArrayOf(android.R.attr.windowSwipeToDismiss)
        val letto = tema.obtainStyledAttributes(attributo)
        try {
            assertFalse("MainActivity non si chiude con lo swipe", letto.getBoolean(0, true))
        } finally {
            letto.recycle()
        }
        val tema2 = attivita().resources.newTheme().apply { applyStyle(R.style.Theme_ScoreboardEssential_Face, true) }
        tema2.obtainStyledAttributes(attributo).let {
            assertFalse(it.getBoolean(0, true))
            it.recycle()
        }
    }

    @Test
    fun `solo il quadrante perde lo swipe, le altre schermate tengono l'indietro`() {
        val pacchetto = attivita().packageManager
        val nome = attivita().packageName

        fun swipe(tema: Int): Boolean {
            val risorse = attivita().resources.newTheme().apply { applyStyle(tema, true) }
            val letto = risorse.obtainStyledAttributes(intArrayOf(android.R.attr.windowSwipeToDismiss))
            return letto.getBoolean(0, true).also { letto.recycle() }
        }

        val principale = pacchetto.getActivityInfo(android.content.ComponentName(nome, MainActivity::class.java.name), 0)
        assertEquals(R.style.Theme_ScoreboardEssential_Face, principale.theme)
        listOf(MenuActivity::class.java, SportSelectionActivity::class.java, PlayerSelectionActivity::class.java).forEach { classe ->
            val info = pacchetto.getActivityInfo(android.content.ComponentName(nome, classe.name), PackageManager.GET_META_DATA)
            val tema = if (info.theme != 0) info.theme else info.applicationInfo.theme
            assertTrue("${classe.simpleName} deve tenere lo swipe", swipe(tema))
        }
    }

    @Test
    fun `il tempo del calcio in minuti e' una funzione pura`() {
        assertEquals("34'", FaceText.minuti("34:12"))
        assertEquals("0'", FaceText.minuti("00:00"))
        assertEquals("95'", FaceText.minuti("95:03"))
        // Un tempo che non si legge resta com'e': meglio intero che inventato.
        assertEquals("4 – 3", FaceText.minuti("4 – 3"))
        assertEquals("", FaceText.minuti(""))
    }
}
