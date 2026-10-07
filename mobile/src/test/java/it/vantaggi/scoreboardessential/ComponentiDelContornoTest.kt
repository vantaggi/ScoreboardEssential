package it.vantaggi.scoreboardessential

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.shape.CutCornerTreatment
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.RoundedCornerTreatment
import com.google.android.material.textfield.TextInputLayout
import it.vantaggi.scoreboardessential.utils.MovimentoRidotto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import com.google.android.material.R as M3

/**
 * Passo G-2: i componenti del contorno sono quelli della UI Constitution. Bottoni con raggio 8 e
 * altezza minima 48dp (mai a pillola), gruppi e card senza ombra, campo con fuoco lime, chip e
 * badge solo bordo, selezione col lime e un secondo segno, dialoghi sui raggi nuovi, e la stessa
 * pressione ovunque (scala 0,97 e opacita' 0,85) che col movimento ridotto perde la scala.
 *
 * Si legge dalle viste vere, gonfiate con il tema del telefono, e ogni misura che puo' coincidere per
 * caso ha la sua falsificazione.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "it-w411dp-h923dp-xxhdpi")
class ComponentiDelContornoTest {
    private val app: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var contesto: Context
    private var densita = 1f

    @Before
    fun setUp() {
        contesto = ContextThemeWrapper(app, R.style.Theme_ScoreboardEssential)
        densita = contesto.resources.displayMetrics.density
    }

    private fun token(id: Int) = contesto.getColor(id)

    private fun dp(id: Int) = contesto.resources.getDimension(id)

    private fun misura(vista: View) {
        vista.measure(
            View.MeasureSpec.makeMeasureSpec((300 * densita).toInt(), View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
    }

    private fun raggioDelBottone(bottone: MaterialButton): Float {
        misura(bottone)
        bottone.layout(0, 0, bottone.measuredWidth, bottone.measuredHeight)
        return bottone.shapeAppearanceModel.topLeftCornerSize.getCornerSize(
            RectF(0f, 0f, bottone.width.toFloat(), bottone.height.toFloat()),
        )
    }

    private fun tutti(
        radice: View,
        tipo: Class<out View>,
    ): List<View> {
        val trovate = mutableListOf<View>()

        fun visita(v: View) {
            if (tipo.isInstance(v)) trovate += v
            if (v is ViewGroup) for (i in 0 until v.childCount) visita(v.getChildAt(i))
        }
        visita(radice)
        return trovate
    }

    // --- Bottoni ---

    @Test
    fun `il bottone primario ha raggio 8, almeno 48dp, fondo lime e testo on-accent`() {
        val bottone = MaterialButton(contesto).apply { text = "Salva risultato" }

        assertEquals(dp(R.dimen.radius_control), raggioDelBottone(bottone), 0.5f)
        assertEquals(8f * densita, raggioDelBottone(bottone), 0.5f)
        assertTrue("altezza ${bottone.measuredHeight}", bottone.measuredHeight >= 48 * densita)
        assertEquals(token(R.color.elite_lime), bottone.backgroundTintList!!.defaultColor)
        assertEquals(token(R.color.elite_on_lime), bottone.currentTextColor)
        assertEquals(0f, bottone.elevation, 0f)
        // Nessun maiuscolo forzato: il bottone dice l'azione.
        assertFalse(bottone.isAllCaps)
    }

    // FALSIFICAZIONE: il bottone di fabbrica di Material 3 e' a pillola. Se la misura del
    // raggio non distinguesse le due forme, il test sopra passerebbe per caso.
    @Test
    fun `il bottone di fabbrica di M3 e' a pillola, quindi la misura del raggio distingue le forme`() {
        val fabbrica = ContextThemeWrapper(app, M3.style.Theme_Material3_Dark_NoActionBar)
        val bottone = MaterialButton(fabbrica).apply { text = "Salva risultato" }

        val raggio = raggioDelBottone(bottone)
        assertTrue("pillola: $raggio", raggio > 12f * densita)
    }

    @Test
    fun `il bottone secondario e' una superficie rialzata con bordo e testo secondario`() {
        val bottone = MaterialButton(contesto, null, M3.attr.materialButtonOutlinedStyle).apply { text = "Impostazioni" }

        assertEquals(8f * densita, raggioDelBottone(bottone), 0.5f)
        assertTrue(bottone.measuredHeight >= 48 * densita)
        assertEquals(token(R.color.elite_surface_raised), bottone.backgroundTintList!!.defaultColor)
        assertEquals(token(R.color.elite_outline), bottone.strokeColor.defaultColor)
        assertEquals(dp(R.dimen.border_width).toInt(), bottone.strokeWidth)
        assertEquals(token(R.color.elite_text_secondary), bottone.currentTextColor)
    }

    private fun colorePredefinito(
        stile: Int,
        attributo: Int,
    ): Int {
        val a = contesto.obtainStyledAttributes(stile, intArrayOf(attributo))
        try {
            return a.getColorStateList(0)!!.defaultColor
        } finally {
            a.recycle()
        }
    }

    // Il distruttivo e' solo testo in colore errore; il testuale e' solo testo primario; nessuno dei due ha un fondo.
    @Test
    fun `il distruttivo e il testuale sono solo testo`() {
        assertEquals(token(R.color.elite_error), colorePredefinito(R.style.Widget_App_Button_Destructive, android.R.attr.textColor))
        assertEquals(token(R.color.elite_text_primary), colorePredefinito(R.style.Widget_App_Button_Text, android.R.attr.textColor))
    }

    @Test
    fun `un bottone disattivo perde il lime e prende text-disabled`() {
        val bottone =
            MaterialButton(contesto).apply {
                text = "Salva"
                isEnabled = false
            }
        val fondo = bottone.backgroundTintList!!.getColorForState(intArrayOf(-android.R.attr.state_enabled), 0)

        assertEquals(token(R.color.elite_surface_raised), fondo)
        assertEquals(token(R.color.elite_text_disabled), bottone.currentTextColor)
    }

    // Un solo primario per regione: nelle schermate del contorno al massimo un bottone lime visibile.
    @Test
    fun `ogni layout del contorno ha al massimo un bottone primario visibile`() {
        val layout =
            listOf(
                R.layout.activity_match_settings,
                R.layout.content_scoreboard_details,
                R.layout.activity_onboarding,
                R.layout.dialog_create_player,
                R.layout.match_item,
            )
        var primariTrovati = 0
        for (id in layout) {
            val radice = LayoutInflater.from(contesto).inflate(id, null)
            val primari =
                tutti(radice, MaterialButton::class.java)
                    .map { it as MaterialButton }
                    .filter { it.visibility == View.VISIBLE && it.backgroundTintList?.defaultColor == token(R.color.elite_lime) }
            assertTrue("${contesto.resources.getResourceEntryName(id)} ha ${primari.size} primari", primari.size <= 1)
            primariTrovati += primari.size
        }
        // Non e' un test vuoto: il foglio ha TERMINA e l'onboarding ha AVANTI.
        assertTrue(primariTrovati >= 2)
    }

    // --- Gruppi e card ---

    @Test
    fun `i gruppi tonali del contorno non hanno elevazione ne' ombra`() {
        for (layout in listOf(R.layout.activity_match_settings, R.layout.content_scoreboard_details, R.layout.activity_statistics, R.layout.activity_players_management)) {
            val radice = LayoutInflater.from(contesto).inflate(layout, null)
            val gruppi = tutti(radice, MaterialCardView::class.java).map { it as MaterialCardView }
            assertTrue("nessun gruppo in ${contesto.resources.getResourceEntryName(layout)}", gruppi.isNotEmpty())
            for (g in gruppi) {
                assertEquals(0f, g.cardElevation, 0f)
                assertEquals(0f, g.elevation, 0f)
                assertEquals(dp(R.dimen.border_width).toInt(), g.strokeWidth)
                assertEquals(token(R.color.elite_border), g.strokeColorStateList!!.defaultColor)
                assertEquals(token(R.color.elite_surface), g.cardBackgroundColor.defaultColor)
                assertEquals(14f * densita, g.radius, 0.5f)
            }
        }
    }

    @Test
    fun `la card ha raggio 14, bordo, nessuna ombra e prende il lime quando e' selezionata`() {
        val card = MaterialCardView(contesto).apply { isCheckable = true }

        assertEquals(0f, card.cardElevation, 0f)
        assertEquals(0f, card.elevation, 0f)
        assertEquals(14f * densita, card.radius, 0.5f)
        assertEquals(dp(R.dimen.border_width).toInt(), card.strokeWidth)
        assertEquals(token(R.color.elite_surface_raised), card.cardBackgroundColor.defaultColor)
        val bordo = card.strokeColorStateList!!
        assertEquals(token(R.color.elite_border_strong), bordo.defaultColor)
        assertEquals(token(R.color.elite_lime), bordo.getColorForState(intArrayOf(android.R.attr.state_checked), 0))
        assertNotNull("la card prende la pressione condivisa", card.stateListAnimator)
    }

    // FALSIFICAZIONE: la card di fabbrica M3 e' sollevata. Se leggesse 0 anche lei il test non direbbe nulla.
    @Test
    fun `la card di fabbrica di M3 ha un'ombra, quindi la misura distingue`() {
        val fabbrica = ContextThemeWrapper(app, M3.style.Theme_Material3_Dark_NoActionBar)
        val card = MaterialCardView(fabbrica, null, M3.attr.materialCardViewElevatedStyle)

        assertTrue("elevazione ${card.cardElevation}", card.cardElevation > 0f)
    }

    // --- Campo di testo ---

    @Test
    fun `il campo ha fondo, bordo, fuoco lime di 2dp e l'errore sotto il campo`() {
        val campo = TextInputLayout(contesto).apply { hint = "Nome" }
        val a = contesto.obtainStyledAttributes(R.style.Widget_App_TextInputLayout, intArrayOf(M3.attr.boxStrokeColor))
        val s = a.getColorStateList(0)!!
        a.recycle()

        assertEquals(token(R.color.elite_surface), campo.boxBackgroundColor)
        assertEquals(token(R.color.elite_outline), s.defaultColor)
        assertEquals(
            token(R.color.elite_lime),
            s.getColorForState(intArrayOf(android.R.attr.state_focused, android.R.attr.state_enabled), 0),
        )
        assertEquals(dp(R.dimen.border_width).toInt(), campo.boxStrokeWidth)
        assertEquals(dp(R.dimen.focus_ring_width).toInt(), campo.boxStrokeWidthFocused)
        assertEquals(token(R.color.elite_error), campo.boxStrokeErrorColor!!.defaultColor)

        campo.error = "Il nome e' obbligatorio"
        val messaggio = campo.findViewById<TextView>(M3.id.textinput_error)
        assertEquals("Il nome e' obbligatorio", messaggio.text.toString())
        assertEquals(token(R.color.elite_error), messaggio.currentTextColor)
    }

    // --- Chip e badge ---

    @Test
    fun `il chip e' solo bordo e testo, e selezionato prende il lime con la spunta`() {
        val chip = LayoutInflater.from(contesto).inflate(R.layout.item_chip_filter, null) as Chip
        chip.text = "ATTACCO"

        assertEquals(0, chip.chipBackgroundColor!!.defaultColor ushr 24)
        assertEquals(token(R.color.elite_outline), chip.chipStrokeColor!!.defaultColor)
        assertEquals(dp(R.dimen.border_width), chip.chipStrokeWidth, 0.5f)
        val selezionato = intArrayOf(android.R.attr.state_checked, android.R.attr.state_enabled)
        assertEquals(token(R.color.elite_lime), chip.chipStrokeColor!!.getColorForState(selezionato, 0))
        assertEquals(token(R.color.elite_lime), chip.textColors.getColorForState(selezionato, 0))
        assertEquals(token(R.color.elite_text_primary), chip.textColors.defaultColor)

        chip.isCheckable = true
        chip.isChecked = true
        assertTrue("il secondo segno e' la spunta", chip.isCheckedIconVisible)
        assertNotNull(chip.checkedIcon)
    }

    @Test
    fun `i badge e i chip usano il raggio 6, non le forme tagliate`() {
        val chip = LayoutInflater.from(contesto).inflate(R.layout.item_chip_filter, null) as Chip
        val angolo = chip.shapeAppearanceModel.topLeftCornerSize.getCornerSize(RectF(0f, 0f, 200f * densita, 32f * densita))

        assertEquals(6f * densita, angolo, 0.5f)
        assertTrue(chip.shapeAppearanceModel.topLeftCorner is RoundedCornerTreatment)
        assertFalse(chip.shapeAppearanceModel.topLeftCorner is CutCornerTreatment)
    }

    // --- Interruttori e caselle ---

    @Test
    fun `casella e interruttore si selezionano col lime e con un secondo segno`() {
        val casella = MaterialCheckBox(contesto)
        val acceso = intArrayOf(android.R.attr.state_checked, android.R.attr.state_enabled)
        val spento = intArrayOf(android.R.attr.state_enabled)
        val tinta: ColorStateList = casella.buttonTintList!!

        assertEquals(token(R.color.elite_lime), tinta.getColorForState(acceso, 0))
        assertEquals(token(R.color.elite_outline), tinta.getColorForState(spento, 0))
        assertTrue(casella.minimumHeight >= 48 * densita)

        val interruttore = MaterialSwitch(contesto)
        assertNotNull("l'interruttore ha il pomello con la spunta", interruttore.thumbIconDrawable)
    }

    // --- Dialoghi ---

    @Test
    fun `i dialoghi hanno raggio overlay, fondo rialzato e scrim all'85 per cento`() {
        val overlay = ContextThemeWrapper(contesto, R.style.ThemeOverlay_App_Dialog)
        val a = overlay.obtainStyledAttributes(intArrayOf(android.R.attr.backgroundDimAmount, android.R.attr.windowBackground))
        assertEquals(0.85f, a.getFloat(0, 0f), 0.001f)
        val sfondo = a.getDrawable(1) as GradientDrawable
        a.recycle()
        assertEquals(14f * densita, sfondo.cornerRadius, 0.5f)
        assertEquals(token(R.color.elite_surface_raised), sfondo.color!!.defaultColor)

        // Il dialogo del builder M3 prende la forma dall'overlay del tema.
        val dialogo = MaterialAlertDialogBuilder(contesto).setTitle("Titolo").setMessage("Messaggio").create()
        dialogo.show()
        val inset = dialogo.window!!.decorView.background as InsetDrawable
        val forma = inset.drawable as MaterialShapeDrawable
        assertEquals(14f * densita, forma.topLeftCornerResolvedSize, 0.5f)
        dialogo.dismiss()
    }

    // --- Pressione ---

    private fun premi(vista: View) {
        vista.isPressed = true
        vista.stateListAnimator?.jumpToCurrentState()
    }

    private fun rilascia(vista: View) {
        vista.isPressed = false
        vista.stateListAnimator?.jumpToCurrentState()
    }

    @Test
    fun `premuto, un controllo va a scala 0,97 e opacita' 0,85 e al rilascio torna com'era`() {
        val vista = listOf(MaterialButton(contesto), MaterialCardView(contesto).apply { isClickable = true }, MaterialCheckBox(contesto))
        for (v in vista) {
            assertNotNull(v.javaClass.simpleName, v.stateListAnimator)
            premi(v)
            assertEquals(v.javaClass.simpleName, 0.97f, v.scaleX, 0.001f)
            assertEquals(0.97f, v.scaleY, 0.001f)
            assertEquals(0.85f, v.alpha, 0.001f)
            rilascia(v)
            assertEquals(1f, v.scaleX, 0.001f)
            assertEquals(1f, v.alpha, 0.001f)
        }
    }

    // FALSIFICAZIONE: senza lo StateListAnimator la pressione non cambia niente, quindi e' lui a farlo.
    @Test
    fun `senza lo StateListAnimator la pressione non fa niente`() {
        val bottone = MaterialButton(contesto)
        bottone.stateListAnimator = null
        premi(bottone)

        assertEquals(1f, bottone.scaleX, 0.001f)
        assertEquals(1f, bottone.alpha, 0.001f)
    }

    private fun conAnimazioneRidotta(prova: () -> Unit) {
        val resolver = app.contentResolver
        val prima = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            prova()
        } finally {
            Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, prima)
        }
    }

    @Test
    fun `con l'animazione ridotta del sistema la pressione non scala e tiene l'opacita'`() {
        conAnimazioneRidotta {
            assertTrue(MovimentoRidotto.attivo(app))
            val ridotto = ContextThemeWrapper(app, R.style.Theme_ScoreboardEssential)
            MovimentoRidotto.applica(ridotto, ridotto.theme)
            val bottone = MaterialButton(ridotto)
            assertNotNull(bottone.stateListAnimator)

            premi(bottone)
            assertEquals(1f, bottone.scaleX, 0.001f)
            assertEquals(1f, bottone.scaleY, 0.001f)
            assertEquals(0.85f, bottone.alpha, 0.001f)
            rilascia(bottone)
            assertEquals(1f, bottone.alpha, 0.001f)
        }
    }

    // FALSIFICAZIONE: e' l'overlay a togliere la scala. Con la scala delle animazioni a zero ma senza
    // l'overlay (un'activity che non l'ha ricevuto) la scala c'e' ancora.
    @Test
    fun `senza l'overlay del movimento ridotto la scala resta anche con le animazioni a zero`() {
        conAnimazioneRidotta {
            val bottone = MaterialButton(contesto)
            premi(bottone)

            assertEquals(0.97f, bottone.scaleX, 0.001f)
        }
    }

    // Con la scala normale l'overlay non si applica.
    @Test
    fun `con le animazioni normali l'overlay non si applica`() {
        assertFalse(MovimentoRidotto.attivo(app))
        val normale = ContextThemeWrapper(app, R.style.Theme_ScoreboardEssential)
        MovimentoRidotto.applica(normale, normale.theme)
        val bottone = MaterialButton(normale)

        premi(bottone)
        assertEquals(0.97f, bottone.scaleX, 0.001f)
    }

    // L'app lo registra per ogni activity: prima di onCreate il tema ha gia' l'overlay.
    @Test
    fun `ogni activity riceve l'overlay del movimento ridotto prima di gonfiare i layout`() {
        conAnimazioneRidotta {
            val controller = Robolectric.buildActivity(Activity::class.java)
            val attivita = controller.get()
            attivita.setTheme(R.style.Theme_ScoreboardEssential)
            MovimentoRidotto.registra(app as Application)
            controller.create()

            val bottone = MaterialButton(attivita)
            premi(bottone)
            assertEquals(1f, bottone.scaleX, 0.001f)
            assertEquals(0.85f, bottone.alpha, 0.001f)
        }
    }

    // --- Niente forme tagliate ne' rosa negli stili ---

    @Test
    fun `nessuno stile del contorno usa piu' le forme tagliate o il rosa`() {
        for (nome in listOf("themes.xml", "styles.xml")) {
            val testo = File("src/main/res/values/$nome").readText()
            assertFalse("$nome ha una forma tagliata", Regex("""cornerFamily">\s*cut""").containsMatchIn(testo))
            assertFalse("$nome cita il rosa", testo.contains("graffiti_pink"))
        }
    }

    // FALSIFICAZIONE del controllo di sopra: lo stesso criterio trova una forma tagliata se c'e'.
    @Test
    fun `il controllo delle forme tagliate le trova se ci sono`() {
        val vecchio = """<style name="X"><item name="cornerFamily">cut</item></style>"""
        assertTrue(Regex("""cornerFamily">\s*cut""").containsMatchIn(vecchio))
    }

    // L'attributo che risolve la pressione e' dichiarato dal tema, e punta all'animator con la scala.
    @Test
    fun `il tema dichiara la pressione condivisa`() {
        val valore = android.util.TypedValue()
        assertTrue(contesto.theme.resolveAttribute(R.attr.pressFeedback, valore, true))
        assertEquals(R.animator.press_feedback, valore.resourceId)
    }
}
