package it.vantaggi.scoreboardessential

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import it.vantaggi.scoreboardessential.core.TeamInk
import it.vantaggi.scoreboardessential.ui.MatchSettingsActivity
import it.vantaggi.scoreboardessential.utils.AltoContrasto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.io.File
import com.google.android.material.R as M3

/**
 * Passo G-10: il tema ad alto contrasto del telefono. Un overlay del tema che cambia SOLO i colori, a 7:1
 * sul testo e a 3:1 su bordi e icone, che si attiva con l'interruttore delle impostazioni o, se l'utente
 * non ha scelto, seguendo il contrasto di sistema. I rapporti si leggono dalle risorse vere attraverso il
 * tema (file colore, attributo, overlay), non da esadecimali copiati nel test.
 */
@RunWith(AndroidJUnit4::class)
class AltoContrastoTest {
    private val app: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        pulisci()
    }

    @After
    fun tearDown() {
        pulisci()
    }

    /** Nessuna scelta salvata e nessun contrasto di sistema: lo stato di un'installazione nuova. */
    private fun pulisci() {
        app.applicationContext
            .getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        AltoContrasto.contrastoDiSistema = { 0f }
    }

    private fun standard(): Context = ContextThemeWrapper(app, R.style.Theme_ScoreboardEssential)

    private fun altoContrasto(): Context =
        ContextThemeWrapper(app, R.style.Theme_ScoreboardEssential).also {
            it.theme.applyStyle(R.style.ThemeOverlay_App_AltoContrasto, true)
        }

    private fun colore(
        contesto: Context,
        id: Int,
    ) = contesto.getColor(id)

    private fun contrasto(
        contesto: Context,
        sopra: Int,
        sotto: Int,
    ) = TeamInk.contrast(colore(contesto, sopra), colore(contesto, sotto))

    private val nomi: (Int) -> String = { app.resources.getResourceEntryName(it) }

    /** Le superfici del tema: tutto cio' che puo' stare sotto un testo o un bordo. */
    private val superfici = listOf(R.color.elite_background, R.color.elite_surface, R.color.elite_surface_raised)

    /** Ogni coppia (sopra, sotto) sotto la soglia, come testo leggibile. Vuota se il tema regge. */
    private fun sotto(
        contesto: Context,
        soglia: Double,
        sopra: List<Int>,
        sotto: List<Int>,
    ): List<String> =
        sopra.flatMap { s ->
            sotto.mapNotNull { b ->
                val valore = contrasto(contesto, s, b)
                if (valore < soglia) "${nomi(s)} su ${nomi(b)} vale %.2f".format(valore) else null
            }
        }

    // --- Contrasti del tema ad alto contrasto ---

    @Test
    fun `il testo primario e secondario ha almeno 7 a 1 su ogni superficie, anche col passaggio del puntatore`() {
        val ac = altoContrasto()
        val testi = listOf(R.color.elite_text_primary, R.color.elite_text_secondary)
        val fondi = superfici + R.color.elite_surface_hover
        assertEquals(emptyList<String>(), sotto(ac, 7.0, testi, fondi))
    }

    @Test
    fun `il testo disattivato resta leggibile, 4,5 a 1 su ogni superficie`() {
        assertEquals(emptyList<String>(), sotto(altoContrasto(), 4.5, listOf(R.color.elite_text_disabled), superfici))
    }

    @Test
    fun `bordi, contorno dei comandi e fuoco hanno almeno 3 a 1 su ogni superficie`() {
        val ac = altoContrasto()
        val bordi = listOf(R.color.elite_border, R.color.elite_border_strong, R.color.elite_outline)
        assertEquals(emptyList<String>(), sotto(ac, 3.0, bordi, superfici))
        // L'anello di fuoco vale anche sul passaggio del puntatore.
        assertEquals(emptyList<String>(), sotto(ac, 3.0, listOf(R.color.elite_focus_ring), superfici + R.color.elite_surface_hover))
        // Il gruppo si vede per il bordo (sfondo e superficie coincidono): il bordo del gruppo e' il segno, 4,5 e oltre.
        assertEquals(colore(ac, R.color.elite_background), colore(ac, R.color.elite_surface))
        assertEquals(emptyList<String>(), sotto(ac, 4.5, listOf(R.color.elite_border), superfici))
    }

    @Test
    fun `lime, avviso, errore e informazione reggono 7 a 1 come testo e come icona su ogni superficie`() {
        val ac = altoContrasto()
        val colori = listOf(R.color.elite_lime, R.color.elite_warning, R.color.elite_error, R.color.elite_info)
        assertEquals(emptyList<String>(), sotto(ac, 7.0, colori, superfici))
        // Il testo sopra il lime e' scuro e ha 7:1 sul lime, anche premuto (opacita' 0,85 sul fondo).
        assertTrue(contrasto(ac, R.color.elite_on_lime, R.color.elite_lime) >= 7.0)
    }

    @Test
    fun `i fondi degli avvisi hanno il testo a 7 a 1 e l'etichetta del colore di stato pure`() {
        val ac = altoContrasto()
        val coppie =
            listOf(
                R.color.elite_success to R.color.elite_success_subtle,
                R.color.elite_warning to R.color.elite_warning_subtle,
                R.color.elite_error to R.color.elite_error_subtle,
                R.color.elite_info to R.color.elite_info_subtle,
            )
        for ((stato, fondo) in coppie) {
            assertTrue("testo su ${nomi(fondo)}", contrasto(ac, R.color.elite_text_primary, fondo) >= 7.0)
            assertTrue("${nomi(stato)} su ${nomi(fondo)}", contrasto(ac, stato, fondo) >= 7.0)
        }
    }

    @Test
    fun `lime e ciano dei grafici e dei lati reggono 3 a 1 e il lime pure come testo su ogni superficie`() {
        val ac = altoContrasto()
        val grafici =
            listOf(
                R.color.team_side_1,
                R.color.team_side_2,
                R.color.elite_chart_3,
                R.color.elite_chart_4,
                R.color.elite_chart_5,
                R.color.elite_cyan,
            )
        assertEquals(emptyList<String>(), sotto(ac, 3.0, grafici, superfici + R.color.elite_surface_hover))
        assertTrue(contrasto(ac, R.color.elite_lime, R.color.ink_black) >= 7.0)
    }

    // Falsificazione: abbassare di un passo il testo secondario (al valore standard, 6,18:1 sul nero) fa fallire il controllo.
    @Test
    fun `il controllo dei 7 a 1 trova il testo secondario abbassato di un passo`() {
        val standard = standard()
        val difetti = sotto(standard, 7.0, listOf(R.color.elite_text_secondary), superfici)
        assertTrue("il testo secondario standard non e' sotto 7:1: $difetti", difetti.isNotEmpty())
        // E lo stesso controllo, sul tema ad alto contrasto, e' vuoto.
        assertEquals(emptyList<String>(), sotto(altoContrasto(), 7.0, listOf(R.color.elite_text_secondary), superfici))
        // Un bordo abbassato al valore standard del bordo forte (1,36:1) fallisce il 3:1.
        assertTrue(sotto(standard, 3.0, listOf(R.color.elite_border_strong), superfici).isNotEmpty())
    }

    // --- Il tema standard non cambia ---

    @Test
    fun `senza l'overlay i token hanno i valori standard di sempre`() {
        val s = standard()
        assertEquals(0xFF0D0D0F.toInt(), colore(s, R.color.elite_background))
        assertEquals(0xFFD1D1D8.toInt(), colore(s, R.color.elite_text_primary))
        assertEquals(0xFF8A8A9A.toInt(), colore(s, R.color.elite_text_secondary))
        assertEquals(0xFF6E6E7E.toInt(), colore(s, R.color.elite_outline))
        assertEquals(0xFFC8F135.toInt(), colore(s, R.color.elite_focus_ring))
        // E il contesto dell'applicazione, senza activity, risolve gli stessi valori (niente magenta).
        assertEquals(0xFFD1D1D8.toInt(), app.getColor(R.color.elite_text_primary))
    }

    @Test
    fun `l'alto contrasto ha i valori calcolati nel passo`() {
        val ac = altoContrasto()
        val attesi =
            mapOf(
                R.color.elite_background to 0xFF000000,
                R.color.elite_surface to 0xFF000000,
                R.color.elite_surface_raised to 0xFF0D0D0F,
                R.color.elite_text_primary to 0xFFFFFFFF,
                R.color.elite_text_secondary to 0xFFC9C9D2,
                R.color.elite_text_disabled to 0xFF8A8A9A,
                R.color.elite_border_strong to 0xFF6E6E7E,
                R.color.elite_border to 0xFF8A8A9A,
                R.color.elite_outline to 0xFFB4B4C0,
                R.color.elite_lime to 0xFFC8F135,
                R.color.elite_focus_ring to 0xFFFFFFFF,
                R.color.elite_on_lime to 0xFF000000,
                R.color.elite_warning to 0xFFF0B35A,
                R.color.elite_error to 0xFFFF8A8A,
                R.color.elite_info to 0xFF9ED0F5,
                R.color.elite_success_subtle to 0xFF0A1A10,
                R.color.elite_warning_subtle to 0xFF1F1606,
                R.color.elite_error_subtle to 0xFF2B0D0D,
                R.color.elite_info_subtle to 0xFF0A1822,
            )
        for ((id, valore) in attesi) assertEquals(nomi(id), valore.toInt(), colore(ac, id))
    }

    // --- L'overlay cambia solo i colori ---

    private fun corpoDellOverlay(): String {
        val themes = File("src/main/res/values/themes.xml").readText()
        return Regex("""<style name="ThemeOverlay.App.AltoContrasto".*?</style>""", RegexOption.DOT_MATCHES_ALL).find(themes)!!.value
    }

    /** Le voci dell'overlay che non sono un colore di ruolo (attributo elite*, valore *_alto_contrasto). */
    private fun vociNonColore(corpo: String): List<String> =
        Regex("""<item name="([^"]+)">([^<]*)</item>""")
            .findAll(corpo)
            .mapNotNull {
                val (nome, valore) = it.destructured
                if (Regex("""elite[A-Z]\w*""").matches(nome) && Regex("""@color/elite_\w+_alto_contrasto""").matches(valore)) null else nome
            }.toList()

    @Test
    fun `l'overlay ha solo colori di ruolo, nessuna forma, peso, raggio, durata o stile`() {
        val corpo = corpoDellOverlay()
        assertTrue("l'overlay e' vuoto", Regex("<item ").findAll(corpo).count() >= 19)
        assertEquals(emptyList<String>(), vociNonColore(corpo))
        // Ogni valore dell'overlay esiste nel file dei valori ad alto contrasto.
        val valori = File("src/main/res/values/colors_alto_contrasto.xml").readText()
        for (nome in Regex("""@color/(\w+_alto_contrasto)""").findAll(corpo).map { it.groupValues[1] }) {
            assertTrue("$nome non e' definito", valori.contains("name=\"$nome\""))
        }
    }

    // Falsificazione: un overlay che toccasse una forma, un peso o la pressione verrebbe fermato.
    @Test
    fun `il controllo dell'overlay trova una forma, un peso e una pressione`() {
        val sbagliato =
            """<style name="x" parent=""><item name="eliteTextPrimary">@color/elite_text_primary_alto_contrasto</item>""" +
                """<item name="cornerSize">4dp</item><item name="android:textFontWeight">700</item>""" +
                """<item name="pressFeedback">@animator/press_feedback_reduced</item></style>"""
        assertEquals(listOf("cornerSize", "android:textFontWeight", "pressFeedback"), vociNonColore(sbagliato))
    }

    @Test
    fun `col tema ad alto contrasto forme, stili, tipografia e pressione risolvono come nello standard`() {
        val s = standard()
        val ac = altoContrasto()
        val attributi =
            listOf(
                M3.attr.shapeAppearanceSmallComponent,
                M3.attr.shapeAppearanceMediumComponent,
                M3.attr.shapeAppearanceLargeComponent,
                M3.attr.materialButtonStyle,
                M3.attr.materialButtonOutlinedStyle,
                M3.attr.materialCardViewStyle,
                M3.attr.materialSwitchStyle,
                M3.attr.textInputStyle,
                M3.attr.textAppearanceDisplaySmall,
                M3.attr.textAppearanceHeadlineLarge,
                M3.attr.textAppearanceTitleLarge,
                M3.attr.textAppearanceBodyLarge,
                M3.attr.textAppearanceLabelLarge,
                M3.attr.textAppearanceBodySmall,
                R.attr.pressFeedback,
            )
        for (attributo in attributi) {
            val nello = android.util.TypedValue().also { s.theme.resolveAttribute(attributo, it, false) }
            val inAlto = android.util.TypedValue().also { ac.theme.resolveAttribute(attributo, it, false) }
            val nome = app.resources.getResourceEntryName(attributo)
            assertEquals("$nome: tipo", nello.type, inAlto.type)
            assertEquals("$nome: risorsa", nello.resourceId, inAlto.resourceId)
            assertEquals("$nome: dato", nello.data, inAlto.data)
        }
        // I raggi e le durate sono dimensioni e interi: l'overlay non li nomina, quindi sono quelli di sempre.
        assertEquals(s.resources.getDimension(R.dimen.radius_control), ac.resources.getDimension(R.dimen.radius_control), 0f)
        assertEquals(s.resources.getInteger(R.integer.duration_fast), ac.resources.getInteger(R.integer.duration_fast))
    }

    // --- I layout e i componenti seguono l'overlay ---

    private fun inflare(
        contesto: Context,
        layout: Int,
    ): View = LayoutInflater.from(contesto).inflate(layout, FrameLayout(contesto), false)

    /** Il colore pieno di uno sfondo, dipinto come tinta unita o come file colore. */
    private fun sfondoDi(vista: View): Int =
        when (val sfondo = vista.background) {
            is android.graphics.drawable.ColorDrawable -> sfondo.color
            is android.graphics.drawable.ColorStateListDrawable -> sfondo.colorStateList.defaultColor
            else -> error("sfondo non pieno: $sfondo")
        }

    private fun tutti(vista: View): List<View> =
        listOf(vista) + ((vista as? ViewGroup)?.let { g -> (0 until g.childCount).flatMap { tutti(g.getChildAt(it)) } } ?: emptyList())

    @Test
    fun `le impostazioni gonfiate col tema ad alto contrasto hanno fondo nero, testi bianchi e bordo del gruppo visibile`() {
        val ac = altoContrasto()
        val radice = inflare(ac, R.layout.activity_match_settings)
        assertEquals(0xFF000000.toInt(), sfondoDi(radice))
        val titolo = radice.findViewById<TextView>(R.id.sportRulesText)
        assertEquals(colore(ac, R.color.elite_text_secondary), titolo.currentTextColor)
        assertEquals(0xFFC9C9D2.toInt(), titolo.currentTextColor)
        // Nello standard le stesse viste hanno i valori di prima.
        val s = standard()
        val radiceStandard = inflare(s, R.layout.activity_match_settings)
        assertEquals(0xFF0D0D0F.toInt(), sfondoDi(radiceStandard))
        assertEquals(0xFF8A8A9A.toInt(), radiceStandard.findViewById<TextView>(R.id.sportRulesText).currentTextColor)
    }

    @Test
    fun `i bottoni secondari e primari prendono contorno e testo dall'overlay`() {
        val ac = altoContrasto()
        val secondario = MaterialButton(ac, null, M3.attr.materialButtonOutlinedStyle)
        assertEquals(colore(ac, R.color.elite_text_secondary), secondario.textColors.defaultColor)
        assertEquals(0xFFC9C9D2.toInt(), secondario.textColors.defaultColor)
        assertEquals(0xFFB4B4C0.toInt(), secondario.strokeColor.defaultColor)
        val primario = MaterialButton(ac)
        assertEquals(0xFFC8F135.toInt(), primario.backgroundTintList!!.defaultColor)
        assertEquals(0xFF000000.toInt(), primario.textColors.defaultColor)
    }

    @Test
    fun `i dialoghi costruiti dall'activity ad alto contrasto prendono fondo e testo dall'overlay`() {
        val ac = altoContrasto()
        val dialogo = MaterialAlertDialogBuilder(ac).setTitle("x").create()
        assertEquals(0xFF0D0D0F.toInt(), MaterialColors.getColor(dialogo.context, M3.attr.colorSurface, "manca"))
        assertEquals(0xFFFFFFFF.toInt(), MaterialColors.getColor(dialogo.context, M3.attr.colorOnSurface, "manca"))
    }

    // La schermata di gioco e' gia' bianco su nero: l'alto contrasto non puo' peggiorarla.
    @Test
    fun `il tabellone col tema ad alto contrasto ha testi a 7 a 1 sul nero e mai meno che nello standard`() {
        val s = standard()
        val ac = altoContrasto()
        val nero = 0xFF000000.toInt()
        val inStandard = tutti(inflare(s, R.layout.content_scoreboard_live)).filterIsInstance<TextView>()
        val inAlto = tutti(inflare(ac, R.layout.content_scoreboard_live)).filterIsInstance<TextView>()
        assertEquals(inStandard.size, inAlto.size)
        assertTrue("il tabellone non ha testi", inAlto.isNotEmpty())
        inStandard.zip(inAlto).forEach { (prima, dopo) ->
            val prima1 = TeamInk.contrast(prima.currentTextColor, nero)
            val dopo1 = TeamInk.contrast(dopo.currentTextColor, nero)
            val nome = if (dopo.id != View.NO_ID) app.resources.getResourceEntryName(dopo.id) else dopo.javaClass.simpleName
            assertTrue("$nome peggiora: %.2f contro %.2f".format(dopo1, prima1), dopo1 >= prima1 - 0.001)
            assertTrue("$nome ha %.2f sul nero".format(dopo1), dopo1 >= 7.0)
        }
        // Le cifre e il pallino restano bianco e lime su nero, uguali nei due temi.
        assertEquals(colore(s, R.color.ink_white), colore(ac, R.color.ink_white))
        assertEquals(colore(s, R.color.elite_lime), colore(ac, R.color.elite_lime))
    }

    // --- Scelta, sistema e applicazione alle activity ---

    private fun impostazioni() = Robolectric.buildActivity(MatchSettingsActivity::class.java).setup().get()

    private fun testoPrimarioDi(activity: android.app.Activity) = activity.getColor(R.color.elite_text_primary)

    @Test
    fun `senza scelta e senza contrasto di sistema l'activity e' standard e l'interruttore e' spento`() {
        val activity = impostazioni()
        assertEquals(0xFFD1D1D8.toInt(), testoPrimarioDi(activity))
        assertFalse(activity.findViewById<MaterialSwitch>(R.id.highContrastSwitch).isChecked)
        assertNull(AltoContrasto.sceltaDellUtente(app))
    }

    @Test
    fun `l'interruttore salva la scelta e le activity nuove prendono l'overlay`() {
        val activity = impostazioni()
        activity.findViewById<MaterialSwitch>(R.id.highContrastSwitch).isChecked = true

        assertEquals(true, AltoContrasto.sceltaDellUtente(app))
        val nuova = impostazioni()
        assertEquals(0xFFFFFFFF.toInt(), testoPrimarioDi(nuova))
        assertTrue(nuova.findViewById<MaterialSwitch>(R.id.highContrastSwitch).isChecked)

        // Lo si spegne: la scelta "spento" e' salvata e vale.
        nuova.findViewById<MaterialSwitch>(R.id.highContrastSwitch).isChecked = false
        assertEquals(false, AltoContrasto.sceltaDellUtente(app))
        assertEquals(0xFFD1D1D8.toInt(), testoPrimarioDi(impostazioni()))
    }

    @Test
    fun `senza scelta l'app segue il contrasto di sistema dalla soglia in su`() {
        AltoContrasto.contrastoDiSistema = { 1f }
        assertEquals(0xFFFFFFFF.toInt(), testoPrimarioDi(impostazioni()))
        AltoContrasto.contrastoDiSistema = { AltoContrasto.SOGLIA_DI_SISTEMA }
        assertEquals(0xFFFFFFFF.toInt(), testoPrimarioDi(impostazioni()))
        AltoContrasto.contrastoDiSistema = { AltoContrasto.SOGLIA_DI_SISTEMA - 0.01f }
        assertEquals(0xFFD1D1D8.toInt(), testoPrimarioDi(impostazioni()))
        // L'interruttore mostra lo stato in vigore, anche se viene dal sistema.
        AltoContrasto.contrastoDiSistema = { 1f }
        assertTrue(impostazioni().findViewById<MaterialSwitch>(R.id.highContrastSwitch).isChecked)
    }

    @Test
    fun `la scelta dell'utente vince sul contrasto di sistema, nei due sensi`() {
        AltoContrasto.contrastoDiSistema = { 1f }
        AltoContrasto.scegli(app, false)
        assertEquals(0xFFD1D1D8.toInt(), testoPrimarioDi(impostazioni()))
        assertFalse(impostazioni().findViewById<MaterialSwitch>(R.id.highContrastSwitch).isChecked)

        AltoContrasto.contrastoDiSistema = { 0f }
        AltoContrasto.scegli(app, true)
        assertEquals(0xFFFFFFFF.toInt(), testoPrimarioDi(impostazioni()))
    }

    @Test
    fun `ogni activity del telefono riceve l'overlay, non solo le impostazioni`() {
        AltoContrasto.scegli(app, true)
        val storico = Robolectric.buildActivity(it.vantaggi.scoreboardessential.MatchHistoryActivity::class.java).setup().get()
        assertEquals(0xFFFFFFFF.toInt(), storico.getColor(R.color.elite_text_primary))
        val giocatori = Robolectric.buildActivity(it.vantaggi.scoreboardessential.PlayersManagementActivity::class.java).setup().get()
        assertEquals(0xFFFFFFFF.toInt(), giocatori.getColor(R.color.elite_text_primary))
    }

    // Falsificazione: se l'applicazione non registrasse l'overlay, l'activity resterebbe standard.
    @Test
    fun `senza l'overlay applicato lo stesso contesto resta standard`() {
        AltoContrasto.scegli(app, true)
        assertNotEquals(colore(standard(), R.color.elite_text_primary), colore(altoContrasto(), R.color.elite_text_primary))
        assertTrue(AltoContrasto.attivo(app))
    }

    @Test
    fun `il contrasto di sistema vero e' zero sotto Android 14 e non fa errori`() {
        // Il predefinito legge UiModeManager; sul simulatore (34) senza regolazioni vale 0, e non lancia.
        AltoContrasto.contrastoDiSistema = AltoContrasto::contrastoLettoDalSistema
        assertEquals(0f, AltoContrasto.contrastoDiSistema(app), 0f)
        assertFalse(AltoContrasto.attivo(app))
    }

    @Test
    @Config(sdk = [30])
    fun `sotto Android 14 il contrasto di sistema non c'e' e vale solo l'interruttore`() {
        AltoContrasto.contrastoDiSistema = { 0f }
        assertFalse(AltoContrasto.attivo(app))
        AltoContrasto.scegli(app, true)
        assertTrue(AltoContrasto.attivo(app))
    }
}
