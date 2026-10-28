package it.vantaggi.scoreboardessential

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.vantaggi.scoreboardessential.core.TeamInk
import it.vantaggi.scoreboardessential.utils.AltoContrasto
import it.vantaggi.scoreboardessential.utils.Aspetto
import it.vantaggi.scoreboardessential.utils.ScelteAspetto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Il tema chiaro (DESIGN.md, "Tema chiaro"): un overlay che cambia i colori dei ruoli e porta con se' l'accento
 * oliva e il campo di gioco bianco. I rapporti si leggono dalle risorse vere attraverso il tema, come per l'alto
 * contrasto; la scelta dell'aspetto si prova su preferenze e configurazione, senza montare l'activity.
 */
@RunWith(AndroidJUnit4::class)
class ChiaroTest {
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

    private fun scuro(): Context = ContextThemeWrapper(app, R.style.Theme_ScoreboardEssential)

    private fun chiaro(): Context =
        ContextThemeWrapper(app, R.style.Theme_ScoreboardEssential).also {
            it.theme.applyStyle(R.style.ThemeOverlay_App_Chiaro, true)
        }

    private fun contrasto(
        contesto: Context,
        sopra: Int,
        sotto: Int,
    ) = TeamInk.contrast(contesto.getColor(sopra), contesto.getColor(sotto))

    private val nomi: (Int) -> String = { app.resources.getResourceEntryName(it) }

    /** Le superfici del tema: tutto cio' che puo' stare sotto un testo o un bordo. */
    private val superfici = listOf(R.color.elite_background, R.color.elite_surface, R.color.elite_surface_raised)

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

    // --- Contrasti del tema chiaro ---

    @Test
    fun `il testo primario e secondario ha almeno 4,5 a 1 su ogni superficie, anche col passaggio del puntatore`() {
        val testi = listOf(R.color.elite_text_primary, R.color.elite_text_secondary)
        assertEquals(emptyList<String>(), sotto(chiaro(), 4.5, testi, superfici + R.color.elite_surface_hover))
    }

    @Test
    fun `l'accento e' leggibile come testo e icona, e il testo sopra l'accento regge`() {
        val c = chiaro()
        assertEquals(emptyList<String>(), sotto(c, 4.5, listOf(R.color.elite_accent), superfici))
        assertTrue("on-accent sull'accento", contrasto(c, R.color.elite_on_lime, R.color.elite_accent) >= 4.5)
    }

    @Test
    fun `il contorno dei comandi, l'anello di fuoco e il testo disattivato reggono su ogni superficie`() {
        val c = chiaro()
        assertEquals(emptyList<String>(), sotto(c, 3.0, listOf(R.color.elite_outline, R.color.elite_focus_ring), superfici))
        // Il disattivato e' sotto 4,5 per scelta della Constitution (solo etichette spente): non scende sotto 2,8.
        assertEquals(emptyList<String>(), sotto(c, 2.8, listOf(R.color.elite_text_disabled), superfici))
    }

    @Test
    fun `i colori di stato si leggono come testo sulle superfici e nell'avviso sul proprio fondo`() {
        val c = chiaro()
        val stati = listOf(R.color.elite_warning, R.color.elite_error, R.color.elite_info)
        assertEquals(emptyList<String>(), sotto(c, 4.5, stati, superfici))
        val avvisi =
            listOf(
                R.color.elite_success_subtle to R.color.elite_accent,
                R.color.elite_warning_subtle to R.color.elite_warning,
                R.color.elite_error_subtle to R.color.elite_error,
                R.color.elite_info_subtle to R.color.elite_info,
            )
        avvisi.forEach { (fondo, etichetta) ->
            assertTrue("${nomi(etichetta)} su ${nomi(fondo)}", contrasto(c, etichetta, fondo) >= 4.5)
            assertTrue("testo primario su ${nomi(fondo)}", contrasto(c, R.color.elite_text_primary, fondo) >= 4.5)
        }
    }

    @Test
    fun `le cifre del gioco si leggono sul campo di gioco, almeno 7 a 1`() {
        assertTrue(contrasto(chiaro(), R.color.elite_game_ink, R.color.elite_game_background) >= 7.0)
        assertTrue(contrasto(scuro(), R.color.elite_game_ink, R.color.elite_game_background) >= 7.0)
    }

    // --- L'overlay cambia i ruoli, e solo quando c'e' ---

    @Test
    fun `senza l'overlay il tema e' quello scuro di sempre, lime e nero puro`() {
        val s = scuro()
        assertEquals(s.getColor(R.color.elite_lime), s.getColor(R.color.elite_accent))
        assertEquals(0xFF000000.toInt(), s.getColor(R.color.elite_game_background))
        assertEquals(0xFFFFFFFF.toInt(), s.getColor(R.color.elite_game_ink))
    }

    @Test
    fun `il tema chiaro cambia ogni ruolo del tema scuro`() {
        val s = scuro()
        val c = chiaro()
        val ruoli =
            listOf(
                R.color.elite_background,
                R.color.elite_surface,
                R.color.elite_surface_raised,
                R.color.elite_surface_hover,
                R.color.elite_border,
                R.color.elite_border_strong,
                R.color.elite_outline,
                R.color.elite_text_primary,
                R.color.elite_text_secondary,
                R.color.elite_text_disabled,
                R.color.elite_on_lime,
                R.color.elite_warning,
                R.color.elite_error,
                R.color.elite_info,
                R.color.elite_success_subtle,
                R.color.elite_warning_subtle,
                R.color.elite_error_subtle,
                R.color.elite_info_subtle,
                R.color.elite_state_pressed,
                R.color.elite_focus_ring,
                R.color.elite_accent,
                R.color.elite_game_background,
                R.color.elite_game_ink,
            )
        val uguali = ruoli.filter { s.getColor(it) == c.getColor(it) }.map(nomi)
        assertEquals("ruoli rimasti scuri nel tema chiaro", emptyList<String>(), uguali)
    }

    @Test
    fun `fondo, gruppo e testo del tema chiaro sono quelli della carta del report`() {
        val c = chiaro()
        assertEquals(c.getColor(R.color.print_background), c.getColor(R.color.elite_background))
        assertEquals(c.getColor(R.color.print_surface), c.getColor(R.color.elite_surface))
        assertEquals(c.getColor(R.color.print_text_primary), c.getColor(R.color.elite_text_primary))
        assertEquals(c.getColor(R.color.print_text_secondary), c.getColor(R.color.elite_text_secondary))
    }

    // --- La scelta dell'aspetto ---

    @Test
    fun `di base l'app e' scura, come e' sempre stata`() {
        assertEquals(ScelteAspetto.SCURO, Aspetto.scelta(app))
        assertFalse(Aspetto.chiaro(app))
    }

    @Test
    fun `scegliere il chiaro lo accende e la scelta resta salvata`() {
        Aspetto.scegli(app, ScelteAspetto.CHIARO)
        assertEquals(ScelteAspetto.CHIARO, Aspetto.scelta(app))
        assertTrue(Aspetto.chiaro(app))
        Aspetto.scegli(app, ScelteAspetto.SCURO)
        assertFalse(Aspetto.chiaro(app))
    }

    @Test
    fun `come il telefono segue la modalita' notte del sistema`() {
        Aspetto.scegli(app, ScelteAspetto.COME_IL_TELEFONO)
        assertTrue("giorno: chiaro", Aspetto.chiaro(conNotte(false)))
        assertFalse("notte: scuro", Aspetto.chiaro(conNotte(true)))
    }

    @Test
    fun `con l'alto contrasto acceso il chiaro resta sospeso, l'alto contrasto e' solo scuro`() {
        Aspetto.scegli(app, ScelteAspetto.CHIARO)
        AltoContrasto.scegli(app, true)
        assertFalse(Aspetto.chiaro(app))
        AltoContrasto.scegli(app, false)
        assertTrue(Aspetto.chiaro(app))
    }

    @Test
    fun `un valore salvato che non si conosce vale scuro`() {
        app.applicationContext
            .getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
            .edit()
            .putString("appearance", "sepia")
            .commit()
        assertEquals(ScelteAspetto.SCURO, Aspetto.scelta(app))
    }

    /** Un contesto con la modalita' notte del sistema [notte] o giorno, come la vedrebbe un'activity. */
    private fun conNotte(notte: Boolean): Context {
        val configurazione = Configuration(app.resources.configuration)
        configurazione.uiMode =
            (configurazione.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (notte) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        return app.createConfigurationContext(configurazione)
    }
}
