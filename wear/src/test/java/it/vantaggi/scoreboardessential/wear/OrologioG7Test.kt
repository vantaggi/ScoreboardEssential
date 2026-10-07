package it.vantaggi.scoreboardessential.wear

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import it.vantaggi.scoreboardessential.shared.communication.ConnectionState
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.wear.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
import java.io.File
import java.time.Duration

/**
 * G-7, l'orologio sui token della UI Constitution: pallino lime e senza colore in ambient, predefiniti
 * lime e ciano, NumberRoll sulle cifre (verso, niente movimento in ambient, al risveglio e col movimento
 * ridotto, scatola di larghezza fissa), menu senza forme tagliate e senza i colori vecchi.
 *
 * Le prove sulle risorse leggono i file del modulo (il test gira dalla cartella `wear/`); le altre
 * pilotano il quadrante vero con l'orologio iniettato, come AmbientTest.
 */
@RunWith(RobolectricTestRunner::class)
class OrologioG7Test {
    private var ora = 10_000L
    private lateinit var controller: ActivityController<MainActivity>
    private lateinit var viewModel: WearViewModel
    private lateinit var binding: ActivityMainBinding

    @Before
    fun setup() {
        val app = RuntimeEnvironment.getApplication()
        listOf("wear_pending_intents", "wear_last_known_match").forEach { nome ->
            app.getSharedPreferences(nome, Context.MODE_PRIVATE).edit().clear().commit()
        }
        Settings.Global.putFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val telefono = Mockito.mock(OptimizedWearDataSync::class.java)
        Mockito.`when`(telefono.connectionState).thenReturn(MutableStateFlow(ConnectionState.Disconnected))
        viewModel = WearViewModel(app, telefono, orologio = { SystemClock.uptimeMillis() + 1_000_000L })
        controller = Robolectric.buildActivity(MainActivity::class.java)
        controller.get().orologio = { ora }
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
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun attivita() = controller.get()

    private fun passa(millisecondi: Long) {
        ora += millisecondi
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millisecondi))
    }

    private fun stato(
        lato1: String,
        lato2: String = "0",
        servizio: Int = 0,
        slot: Int = 0,
    ) = WearScoreState(
        side1Primary = lato1,
        side1Secondary = "",
        side2Primary = lato2,
        side2Secondary = "",
        periodLabel = "Set 1",
        hasClock = false,
        hasAuxTimer = false,
        attributesScorer = false,
        decrementIsUndo = true,
        sportId = "tennis",
        sportLabel = "",
        sportIds = emptyList(),
        sportLabels = emptyList(),
        matchInProgress = true,
        matchOver = false,
        eventLog = "",
        servingSide = servizio,
        servingSlot = slot,
    )

    private fun applica(stato: WearScoreState) {
        viewModel.applyStateV2(stato)
        idle()
    }

    /** Lo schermo e' acceso e guardato da un po': la guardia del risveglio e' chiusa. */
    private fun schermoGuardato() {
        passa(MainActivity.GUARDIA_RISVEGLIO_MS + 1)
    }

    private fun colore(id: Int) = attivita().getColor(id)

    private fun risorsaDelDisegno(vista: View): Int = shadowOf(vista.background).createdFromResId

    // --- Pallino del servizio ---

    @Test
    fun `il pallino del servizio e' lime fuori dall'ambient, uno solo o due`() {
        applica(stato("0", servizio = 1, slot = 1))
        assertEquals(View.VISIBLE, binding.team1ServeDot.visibility)
        assertEquals(View.GONE, binding.team1ServeDotSecond.visibility)
        assertEquals(R.drawable.bg_serving_dot, risorsaDelDisegno(binding.team1ServeDot))
        assertEquals(0xFFC8F135.toInt(), colore(R.color.elite_lime))

        applica(stato("0", servizio = 2, slot = 2))
        assertEquals(View.VISIBLE, binding.team2ServeDot.visibility)
        assertEquals(View.VISIBLE, binding.team2ServeDotSecond.visibility)
        assertEquals(R.drawable.bg_serving_dot, risorsaDelDisegno(binding.team2ServeDot))
        assertEquals(R.drawable.bg_serving_dot, risorsaDelDisegno(binding.team2ServeDotSecond))
    }

    @Test
    fun `in ambient il pallino non ha colore ne' riempimento e all'uscita torna lime`() {
        applica(stato("0", servizio = 2, slot = 2))

        attivita().applyAmbient(true)
        idle()

        listOf(binding.team2ServeDot, binding.team2ServeDotSecond).forEach {
            assertEquals("resta visibile: dice chi serve", View.VISIBLE, it.visibility)
            assertEquals(R.drawable.bg_serving_dot_ambient, risorsaDelDisegno(it))
        }

        attivita().applyAmbient(false)
        idle()
        listOf(binding.team2ServeDot, binding.team2ServeDotSecond).forEach {
            assertEquals(R.drawable.bg_serving_dot, risorsaDelDisegno(it))
        }
    }

    @Test
    fun `il disegno del pallino in ambient e' solo un contorno bianco e il lime ha il riempimento`() {
        val senzaCommenti = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
        val ambient = File("src/main/res/drawable/bg_serving_dot_ambient.xml").readText().replace(senzaCommenti, "")
        val normale = File("src/main/res/drawable/bg_serving_dot.xml").readText().replace(senzaCommenti, "")

        assertFalse("in ambient nessun riempimento", ambient.contains("<solid"))
        assertTrue(ambient.contains("<stroke"))
        assertTrue(ambient.contains("@color/ink_white"))
        assertFalse("in ambient nessun lime", ambient.contains("lime") || ambient.contains("C8F135", ignoreCase = true))
        assertTrue(normale.contains("<solid android:color=\"@color/elite_lime\""))
    }

    @Test
    fun `falsificazione, se il pallino restasse lime in ambient il controllo lo vede`() {
        // Lo stesso controllo del test sopra, applicato a un disegno sbagliato di proposito.
        val sbagliato = "<shape><solid android:color=\"@color/elite_lime\" /></shape>"
        assertTrue(sbagliato.contains("<solid"))
        assertNotEquals(R.drawable.bg_serving_dot, R.drawable.bg_serving_dot_ambient)
    }

    // --- Colori predefiniti delle squadre ---

    @Test
    fun `le strisce partono lime e ciano come sul telefono, e il colore mandato dal telefono vince`() {
        assertEquals(0xFFC8F135.toInt(), (binding.team1Stripe.background as ColorDrawable).color)
        assertEquals(0xFF00E5FF.toInt(), (binding.team2Stripe.background as ColorDrawable).color)
        assertEquals(colore(R.color.team_side_1), colore(R.color.elite_lime))
        assertEquals(colore(R.color.team_side_2), colore(R.color.elite_cyan))

        viewModel.setTeamColor(1, 0xFFFFD600.toInt())
        idle()
        assertEquals(0xFFFFD600.toInt(), (binding.team1Stripe.background as ColorDrawable).color)
    }

    // --- NumberRoll sulle cifre ---

    /** Una cifra nella sua colonna, come nel quadrante: 0dp fra due margini in un ConstraintLayout. */
    private class Scena(
        val genitore: ConstraintLayout,
        val cifra: TextView,
        val rotolo: NumberRoll,
    )

    private fun montaLaCifra(testo: String): Scena {
        val ctx = attivita()
        val genitore = ConstraintLayout(ctx)
        val cifra =
            TextView(ctx).apply {
                this.text = testo
                textSize = 58f
                gravity = Gravity.CENTER
                typeface = binding.team1Score.typeface
            }
        genitore.addView(
            cifra,
            ConstraintLayout.LayoutParams(0, 0).apply {
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                marginStart = 24
                marginEnd = 4
            },
        )
        genitore.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY))
        genitore.layout(0, 0, 400, 300)
        return Scena(genitore, cifra, NumberRoll(cifra))
    }

    @Test
    fun `quando il punteggio sale la cifra vecchia esce in alto e la nuova entra dal basso`() {
        val s = montaLaCifra("0")

        s.rotolo.mostra("15", NumberRoll.SU)

        assertEquals("15", s.cifra.text.toString())
        assertEquals("il fantasma della cifra vecchia", 2, s.genitore.childCount)
        // Sotto Robolectric il looper porta l'animazione a fine corsa in un colpo: si guarda a meta'
        // spostando l'animatore, come farebbe il tempo.
        s.rotolo.corsa!!.setCurrentPlayTime(80)
        val uscente = s.genitore.getChildAt(1) as TextView
        assertEquals("0", uscente.text.toString())
        assertEquals("il fantasma e' nascosto a TalkBack", View.IMPORTANT_FOR_ACCESSIBILITY_NO, uscente.importantForAccessibility)
        assertTrue("la vecchia sale (y negativo): ${uscente.translationY}", uscente.translationY < 0f)
        assertTrue("la nuova arriva dal basso (y positivo): ${s.cifra.translationY}", s.cifra.translationY > 0f)
        s.genitore.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY))
        s.genitore.layout(0, 0, 400, 300)
        assertTrue(s.cifra.width > 0)
        assertEquals("il fantasma ha la scatola della cifra", s.cifra.width, uscente.width)
        assertEquals(s.cifra.height, uscente.height)

        passa(attivita().resources.getInteger(R.integer.duration_fast).toLong() + 50)
        assertEquals("a fine corsa resta una sola cifra", 1, s.genitore.childCount)
        assertEquals(0f, s.cifra.translationY, 0f)
        assertEquals(1f, s.cifra.alpha, 0f)
    }

    @Test
    fun `quando il punteggio scende il verso e' l'opposto`() {
        val s = montaLaCifra("30")

        s.rotolo.mostra("15", NumberRoll.GIU)
        s.rotolo.corsa!!.setCurrentPlayTime(80)

        val uscente = s.genitore.getChildAt(1)
        assertTrue("la vecchia scende (y positivo)", uscente.translationY > 0f)
        assertTrue("la nuova arriva dall'alto (y negativo)", s.cifra.translationY < 0f)
        passa(300)
    }

    @Test
    fun `falsificazione, invertire il verso scambia su e giu' e il vantaggio sta sopra il 40`() {
        assertEquals(NumberRoll.SU, NumberRoll.direzione("0", "15"))
        assertEquals(NumberRoll.GIU, NumberRoll.direzione("15", "0"))
        assertEquals(-NumberRoll.direzione("15", "30"), NumberRoll.direzione("30", "15"))
        assertEquals(NumberRoll.SU, NumberRoll.direzione("40", "AV"))
        assertEquals(NumberRoll.GIU, NumberRoll.direzione("AV", "40"))
        assertEquals(0, NumberRoll.direzione("15", "15"))
        assertEquals(0, NumberRoll.direzione("", "15"))
        // Con la direzione sbagliata il test di sopra fallirebbe: la cifra nuova partirebbe dall'altra parte.
        val s = montaLaCifra("0")
        s.rotolo.mostra("15", NumberRoll.GIU)
        s.rotolo.corsa!!.setCurrentPlayTime(80)
        assertTrue("salendo col verso sbagliato la nuova arriva dall'alto", s.cifra.translationY < 0f)
        passa(300)
    }

    @Test
    fun `la corsa e' un quarto della scatola, la meta' del telefono, e dura duration_fast`() {
        val risorse = attivita().resources
        assertEquals(160, risorse.getInteger(R.integer.duration_fast))
        assertEquals(0.25f, risorse.getFraction(R.fraction.number_roll_travel, 1, 1), 0f)

        val s = montaLaCifra("0")
        s.rotolo.mostra("15", NumberRoll.SU)
        assertEquals(s.cifra.height * 0.25f, s.cifra.translationY, 0.5f)
        assertEquals(160L, s.rotolo.corsa!!.duration)
        passa(300)
    }

    @Test
    fun `un cambio nuovo sostituisce subito il rotolo in corso, anche a raffica`() {
        val s = montaLaCifra("0")
        s.rotolo.mostra("15", NumberRoll.SU)
        s.rotolo.corsa!!.setCurrentPlayTime(60)
        s.rotolo.mostra("30", NumberRoll.SU)

        assertEquals("30", s.cifra.text.toString())
        assertEquals("un solo fantasma alla volta", 2, s.genitore.childCount)
        assertEquals("15", (s.genitore.getChildAt(1) as TextView).text.toString())
        assertEquals(2, s.rotolo.avviati)
        passa(300)
        assertEquals(1, s.genitore.childCount)
    }

    @Test
    fun `col movimento ridotto del sistema la cifra cambia subito, senza traslazione ne' fantasma`() {
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val s = montaLaCifra("0")

        s.rotolo.mostra("15", NumberRoll.SU)

        assertEquals("15", s.cifra.text.toString())
        assertEquals(1, s.genitore.childCount)
        assertEquals(0f, s.cifra.translationY, 0f)
        assertEquals(1f, s.cifra.alpha, 0f)
        assertEquals(0, s.rotolo.avviati)
    }

    @Test
    fun `la scatola della cifra ha la stessa larghezza qualunque sia il punteggio`() {
        val s = montaLaCifra("0")
        val iniziale = s.cifra.width
        assertTrue(iniziale > 0)

        listOf("15", "30", "40", "AV", "0").forEach {
            s.rotolo.mostra(it, NumberRoll.SU)
            assertEquals("scatola di $it durante il rotolo", iniziale, s.cifra.width)
            passa(300)
            s.genitore.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY))
            s.genitore.layout(0, 0, 400, 300)
            assertEquals("scatola di $it a rotolo finito", iniziale, s.cifra.width)
        }
        // E nel quadrante vero le due colonne hanno la stessa misura con qualunque cifra.
        val prima = binding.team1Score.width
        applica(stato("AV", "40"))
        assertEquals(prima, binding.team1Score.width)
        assertEquals(prima, binding.team2Score.width)
    }

    @Test
    fun `sul quadrante il rotolo parte quando lo schermo e' guardato`() {
        applica(stato("0"))
        schermoGuardato()
        val prima = attivita().rotoliAvviati

        applica(stato("15"))

        assertEquals("15", binding.team1Score.text.toString())
        assertEquals("un rotolo, sulla cifra che e' cambiata", prima + 1, attivita().rotoliAvviati)
    }

    @Test
    fun `in ambient il punteggio cambia ma niente si muove`() {
        applica(stato("0"))
        schermoGuardato()
        attivita().applyAmbient(true)
        idle()
        val prima = attivita().rotoliAvviati

        applica(stato("15"))

        assertEquals("15", binding.team1Score.text.toString())
        assertEquals("in ambient nessun rotolo", prima, attivita().rotoliAvviati)
        assertEquals(0f, binding.team1Score.translationY, 0f)
        assertEquals(1f, binding.team1Score.alpha, 0f)
        assertEquals("nessun fantasma accanto alla cifra", 1, (binding.team1Score.parent as ViewGroup).let { g -> (0 until g.childCount).count { g.getChildAt(it) is TextView } })
    }

    @Test
    fun `col movimento ridotto il quadrante cambia la cifra senza rotolare`() {
        Settings.Global.putFloat(RuntimeEnvironment.getApplication().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        applica(stato("0"))
        schermoGuardato()
        val prima = attivita().rotoliAvviati

        applica(stato("15"))

        assertEquals("15", binding.team1Score.text.toString())
        assertEquals(prima, attivita().rotoliAvviati)
    }

    @Test
    fun `al risveglio il punteggio nuovo compare senza rotolare, dopo la guardia si`() {
        applica(stato("0"))
        schermoGuardato()
        attivita().applyAmbient(true)
        attivita().applyAmbient(false)
        val prima = attivita().rotoliAvviati

        // Dentro i 500ms dall'uscita dall'ambient: e' cio' che e' successo mentre non si guardava.
        applica(stato("15"))
        assertEquals("15", binding.team1Score.text.toString())
        assertEquals("niente animazione al risveglio", prima, attivita().rotoliAvviati)

        passa(MainActivity.GUARDIA_RISVEGLIO_MS)
        applica(stato("30"))
        assertEquals("passata la guardia il rotolo c'e'", prima + 1, attivita().rotoliAvviati)
    }

    @Test
    fun `all'apertura la cifra che arriva subito non rotola`() {
        // Nessuna schermoGuardato(): siamo ancora nei 500ms dall'onStart.
        applica(stato("30", "15"))

        assertEquals("30", binding.team1Score.text.toString())
        assertEquals(0, attivita().rotoliAvviati)
    }

    @Test
    fun `le cifre restano regioni live polite e il lato dice nome e valore`() {
        listOf(binding.team1Score, binding.team2Score).forEach {
            assertEquals(View.ACCESSIBILITY_LIVE_REGION_POLITE, it.accessibilityLiveRegion)
        }
        viewModel.setTeamNames("Rossi", "Blu")
        applica(stato("30", "15"))

        val lato1 = binding.team1Container.contentDescription.toString()
        assertTrue(lato1, lato1.startsWith("Rossi, 30"))
        assertTrue(binding.team2Container.contentDescription.toString().startsWith("Blu, 15"))
    }
}
