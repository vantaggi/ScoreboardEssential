package it.vantaggi.scoreboardessential

import android.content.Context
import android.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import it.vantaggi.scoreboardessential.core.TeamInk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Passo G-0 della pista "Coerenza con Padel Elite": i token del contorno hanno i valori della
 * dashboard (tema Navy scuro) e i loro contrasti reggono quello che le regole di Padel Elite gli
 * fanno fare. I rapporti si leggono dalle risorse vere, non da esadecimali copiati nel test: se un
 * valore in `colors.xml` cambia, cambia l'esito.
 */
@RunWith(AndroidJUnit4::class)
class TokenEliteTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    private fun token(id: Int) = context.getColor(id)

    private fun contrasto(
        primo: Int,
        secondo: Int,
    ) = TeamInk.contrast(token(primo), token(secondo))

    private fun assertAlmeno(
        minimo: Double,
        primo: Int,
        secondo: Int,
        nome: String,
    ) {
        val valore = contrasto(primo, secondo)
        assertTrue("$nome vale %.2f, ne servono %.1f".format(valore, minimo), valore >= minimo)
    }

    // I valori veri di css/style.css: se la dashboard cambia, qui lo si vede e si decide.
    @Test
    fun `i token hanno i valori della dashboard di Padel Elite`() {
        val attesi =
            mapOf(
                R.color.elite_background to 0xFF0D0D0F,
                R.color.elite_surface to 0xFF161618,
                R.color.elite_surface_raised to 0xFF1E1E22,
                R.color.elite_surface_hover to 0xFF2A2A2E,
                R.color.elite_border to 0xFF1E1E22,
                R.color.elite_border_strong to 0xFF2A2A2E,
                R.color.elite_text_primary to 0xFFD1D1D8,
                R.color.elite_text_secondary to 0xFF8A8A9A,
                R.color.elite_text_tertiary to 0xFF7F7F93,
                R.color.elite_lime to 0xFFC8F135,
                R.color.elite_on_lime to 0xFF0D0D0F,
                R.color.elite_cyan to 0xFF00E5FF,
                R.color.elite_warning to 0xFFE09A35,
                R.color.elite_error to 0xFFE05252,
            )

        for ((id, valore) in attesi) {
            assertEquals(context.resources.getResourceEntryName(id), valore.toInt(), token(id))
        }
    }

    // Il successo e' il lime (non un verde a parte), il ciano vecchio e' lo stesso valore del nuovo,
    // e il velo e' nero all'85%.
    @Test
    fun `successo e ciano vecchio non sono valori a parte, il velo e' nero all'85 per cento`() {
        assertEquals(token(R.color.elite_lime), token(R.color.elite_success))
        assertEquals(token(R.color.elite_cyan), token(R.color.neon_cyan))

        val velo = token(R.color.elite_overlay)
        assertEquals(0, Color.red(velo) + Color.green(velo) + Color.blue(velo))
        assertEquals(217, Color.alpha(velo)) // 0,85 x 255 = 216,75
    }

    // Testo: primario e secondario >= 4,5 su fondo e superfici di pagina; il terziario solo dove la
    // dashboard lo mette (fondo e superficie).
    @Test
    fun `il testo primario e secondario passa AA su fondo e superfici`() {
        val fondi =
            mapOf(
                "fondo" to R.color.elite_background,
                "superficie" to R.color.elite_surface,
                "rialzata" to R.color.elite_surface_raised,
            )

        for ((nome, fondo) in fondi) {
            assertAlmeno(4.5, R.color.elite_text_primary, fondo, "testo primario su $nome")
            assertAlmeno(4.5, R.color.elite_text_secondary, fondo, "testo secondario su $nome")
        }
        assertAlmeno(4.5, R.color.elite_text_primary, R.color.elite_surface_hover, "testo primario su hover")
        assertAlmeno(4.5, R.color.elite_text_tertiary, R.color.elite_background, "testo terziario su fondo")
        assertAlmeno(4.5, R.color.elite_text_tertiary, R.color.elite_surface, "testo terziario su superficie")
    }

    // Il marchio: il lime si legge su ogni fondo e il testo sopra il lime e' il fondo scuro.
    @Test
    fun `il lime si vede sui fondi e il testo scuro sul lime passa AA`() {
        assertAlmeno(4.5, R.color.elite_on_lime, R.color.elite_lime, "testo scuro su lime")
        for (fondo in listOf(R.color.elite_background, R.color.elite_surface, R.color.elite_surface_raised)) {
            assertAlmeno(4.5, R.color.elite_lime, fondo, "lime come testo su ${context.resources.getResourceEntryName(fondo)}")
        }
    }

    // Stati: avviso ed errore leggibili come testo su fondo e superficie; ciano come grafica.
    @Test
    fun `avviso, errore e ciano reggono come testo o come grafica`() {
        for (fondo in listOf(R.color.elite_background, R.color.elite_surface)) {
            val nome = context.resources.getResourceEntryName(fondo)
            assertAlmeno(4.5, R.color.elite_error, fondo, "errore su $nome")
            assertAlmeno(4.5, R.color.elite_warning, fondo, "avviso su $nome")
            assertAlmeno(3.0, R.color.elite_cyan, fondo, "ciano su $nome")
        }
        assertAlmeno(4.5, R.color.elite_warning, R.color.elite_surface_raised, "avviso su rialzata")
        assertAlmeno(3.0, R.color.elite_cyan, R.color.elite_surface_raised, "ciano su rialzata")
    }

    // Il contorno dei comandi e dei campi (WCAG 1.4.11): 3:1 contro ogni fondo su cui sta.
    @Test
    fun `il contorno dei comandi ha 3 a 1 su fondo e superfici`() {
        for (fondo in listOf(R.color.elite_background, R.color.elite_surface, R.color.elite_surface_raised)) {
            assertAlmeno(3.0, R.color.elite_outline, fondo, "contorno su ${context.resources.getResourceEntryName(fondo)}")
        }
    }

    // FALSIFICAZIONE. Queste asserzioni dicono dove i token della dashboard NON bastano, e perche'
    // l'app ha aggiunto elite_outline e tiene il testo scuro sul lime. Se una cambia, e' la prova che
    // un valore e' stato toccato: si ricalcola e si aggiorna DESIGN.md.
    @Test
    fun `i limiti noti dei token della dashboard restano tali`() {
        // Il bordo forte della dashboard (1,4:1) non e' un contorno di comando: e' una separazione.
        assertTrue(contrasto(R.color.elite_border_strong, R.color.elite_background) < 3.0)
        assertTrue(contrasto(R.color.elite_border_strong, R.color.elite_surface_raised) < 3.0)
        assertTrue(contrasto(R.color.elite_border, R.color.elite_surface) < 3.0)
        // Il bianco sul lime sarebbe illeggibile (1,3:1): per questo onPrimary e' il fondo.
        assertTrue(TeamInk.contrast(TeamInk.BIANCO, token(R.color.elite_lime)) < 3.0)
        // Il testo secondario sul lime non passa: sul pulsante primario non si usa.
        assertTrue(contrasto(R.color.elite_text_secondary, R.color.elite_lime) < 4.5)
        // Terziario ed errore scendono sotto 4,5 sulla superficie rialzata e sull'hover.
        assertTrue(contrasto(R.color.elite_text_tertiary, R.color.elite_surface_raised) < 4.5)
        assertTrue(contrasto(R.color.elite_error, R.color.elite_surface_raised) < 4.5)
        assertTrue(contrasto(R.color.elite_text_secondary, R.color.elite_surface_hover) < 4.5)
    }

    // G-2: ogni ruolo aggiunto dalla UI Constitution risolve al valore dichiarato in DESIGN.md.
    @Test
    fun `i ruoli aggiunti da G-2 hanno i valori della tabella dei ruoli`() {
        val attesi =
            mapOf(
                R.color.elite_text_disabled to 0xFF6B7077,
                R.color.elite_info to 0xFF7FBDEA,
                R.color.elite_success_subtle to 0xFF15291F,
                R.color.elite_warning_subtle to 0xFF2E2410,
                R.color.elite_error_subtle to 0xFF2A1415,
                R.color.elite_info_subtle to 0xFF14262F,
                R.color.elite_state_pressed to 0x1FD1D1D8,
                R.color.elite_chart_3 to 0xFFF0A36B,
                R.color.elite_chart_4 to 0xFFD79BDB,
                R.color.elite_chart_5 to 0xFFA4A9B0,
            )

        for ((id, valore) in attesi) {
            assertEquals(context.resources.getResourceEntryName(id), valore.toInt(), token(id))
        }
    }

    // Spaziature, raggi, bersaglio e durate: i ruoli numerici di DESIGN.md.
    @Test
    fun `spaziature, raggi, bersaglio e durate hanno i valori della tabella dei ruoli`() {
        val r = context.resources
        val densita = r.displayMetrics.density
        fun dp(id: Int) = r.getDimension(id) / densita

        val spazi = listOf(R.dimen.space_4, R.dimen.space_8, R.dimen.space_12, R.dimen.space_16, R.dimen.space_24, R.dimen.space_32, R.dimen.space_48, R.dimen.space_64)
        assertEquals(listOf(4f, 8f, 12f, 16f, 24f, 32f, 48f, 64f), spazi.map { dp(it) })

        assertEquals(8f, dp(R.dimen.radius_control))
        assertEquals(14f, dp(R.dimen.radius_surface))
        assertEquals(14f, dp(R.dimen.radius_overlay))
        assertEquals(6f, dp(R.dimen.radius_badge))
        assertEquals(48f, dp(R.dimen.control_touch))

        assertEquals(100, r.getInteger(R.integer.duration_instant))
        assertEquals(160, r.getInteger(R.integer.duration_fast))
        assertEquals(240, r.getInteger(R.integer.duration_standard))
    }

    // Contrasti dei ruoli nuovi (tabella "Contrasti" di DESIGN.md): il testo primario sui fondi
    // subtle, il colore di stato come etichetta sul proprio subtle, info sulle superfici, i grafici.
    @Test
    fun `i fondi subtle, info e i colori dei grafici passano i contrasti dei ruoli`() {
        val subtle =
            mapOf(
                "successo" to (R.color.elite_success_subtle to R.color.elite_success),
                "avviso" to (R.color.elite_warning_subtle to R.color.elite_warning),
                "errore" to (R.color.elite_error_subtle to R.color.elite_error),
                "info" to (R.color.elite_info_subtle to R.color.elite_info),
            )
        for ((nome, coppia) in subtle) {
            val (fondo, stato) = coppia
            assertAlmeno(4.5, R.color.elite_text_primary, fondo, "testo primario su subtle $nome")
            assertAlmeno(4.5, stato, fondo, "etichetta $nome sul proprio subtle")
        }

        for (fondo in listOf(R.color.elite_background, R.color.elite_surface, R.color.elite_surface_raised)) {
            val nome = context.resources.getResourceEntryName(fondo)
            assertAlmeno(4.5, R.color.elite_info, fondo, "info su $nome")
        }
        for (fondo in listOf(R.color.elite_background, R.color.elite_surface)) {
            for (grafico in listOf(R.color.elite_chart_3, R.color.elite_chart_4, R.color.elite_chart_5)) {
                assertAlmeno(3.0, grafico, fondo, "grafico ${context.resources.getResourceEntryName(grafico)}")
            }
        }
        // Il lime premuto (opacita' 0,85 sul fondo) tiene il testo scuro a 4,5.
        val premuto = ColorUtils.compositeColors(ColorUtils.setAlphaComponent(token(R.color.elite_lime), 217), token(R.color.elite_background))
        assertTrue(TeamInk.contrast(token(R.color.elite_on_lime), premuto) >= 4.5)
    }

    // FALSIFICAZIONE: il disattivo e' sotto 4,5 per scelta (solo etichette), quindi un testo che
    // dovesse restare leggibile non puo' usare questo ruolo; e il fondo subtle dell'errore e' al
    // limite: con il valore "ovvio" della Constitution (#33191A) l'etichetta non passerebbe.
    @Test
    fun `i limiti noti dei ruoli nuovi restano tali`() {
        assertTrue(contrasto(R.color.elite_text_disabled, R.color.elite_surface) < 4.5)
        assertTrue(contrasto(R.color.elite_text_disabled, R.color.elite_surface) >= 3.0)
        assertTrue(TeamInk.contrast(token(R.color.elite_error), 0xFF33191A.toInt()) < 4.5)
    }
}
