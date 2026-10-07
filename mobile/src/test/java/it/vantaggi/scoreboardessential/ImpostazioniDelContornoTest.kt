package it.vantaggi.scoreboardessential

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.shape.MaterialShapeDrawable
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.core.TeamInk
import it.vantaggi.scoreboardessential.utils.anteprimaDiSquadra
import it.vantaggi.scoreboardessential.utils.etichettaDiSquadra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Il contorno, schermate: la riga delle regole e l'anteprima del selettore del colore nelle
 * impostazioni, e il contorno delle etichette di squadra dal colore scuro.
 */
@RunWith(AndroidJUnit4::class)
class ImpostazioniDelContornoTest {
    private val base: Context get() = ApplicationProvider.getApplicationContext()
    private val tema: Context get() = ContextThemeWrapper(base, R.style.Theme_ScoreboardEssential)

    private val bluNotte = 0xFF1A237E.toInt()
    private val giallo = 0xFFFFD600.toInt()

    // Il selettore dello sport non diceva quali regole arbitra il motore: il primo 40–40 era una sorpresa.
    @Test
    @Config(qualifiers = "it")
    fun `in italiano la riga delle regole dice cosa arbitra il motore`() {
        assertEquals("Set unico · punto secco sul 40–40 · tie-break a 7", sportRulesLine(base, SportRegistry.PADEL))
        assertEquals("Al meglio di 3 set · vantaggi · tie-break a 7", sportRulesLine(base, SportRegistry.TENNIS))
        assertEquals("Cronometro · cambio portiere", sportRulesLine(base, SportRegistry.FOOTBALL))
    }

    @Test
    @Config(qualifiers = "en")
    fun `in inglese la riga delle regole e' inglese`() {
        assertEquals("Single set · golden point at 40–40 · tie-break to 7", sportRulesLine(base, SportRegistry.PADEL))
        assertEquals("Best of 3 sets · advantage · tie-break to 7", sportRulesLine(base, SportRegistry.TENNIS))
        assertEquals("Stopwatch · keeper change", sportRulesLine(base, SportRegistry.FOOTBALL))
    }

    // La riga e' composta dal registro: non puo' dire una regola diversa da quella che il motore applica.
    @Test
    @Config(qualifiers = "en")
    fun `la riga segue la configurazione del registro e non un testo per sport`() {
        val padel = SportRegistry.byId(SportRegistry.PADEL).config
        val tennis = SportRegistry.byId(SportRegistry.TENNIS).config
        assertTrue(sportRulesLine(base, SportRegistry.PADEL).contains("tie-break to ${padel.tieBreakTo}"))
        assertTrue(sportRulesLine(base, SportRegistry.TENNIS).contains("Best of ${tennis.sets} sets"))
    }

    // Anteprima 96x64 nel colore che si muove: il testo sopra e' quello che ci andra' davvero.
    @Test
    @Config(qualifiers = "it")
    fun `l'anteprima scrive 12 e il nome con l'inchiostro del colore`() {
        val anteprima = TextView(tema)

        anteprima.anteprimaDiSquadra(giallo, "Rossi")
        assertEquals("12\nRossi", anteprima.text.toString())
        assertEquals(TeamInk.NERO, anteprima.currentTextColor)
        assertEquals(giallo, (anteprima.background as MaterialShapeDrawable).fillColor?.defaultColor)

        anteprima.anteprimaDiSquadra(bluNotte, "Blu")
        assertEquals(TeamInk.BIANCO, anteprima.currentTextColor)
        assertEquals(bluNotte, (anteprima.background as MaterialShapeDrawable).fillColor?.defaultColor)
        assertTrue(TeamInk.contrast(anteprima.currentTextColor, bluNotte) >= 4.5)
    }

    // Un blu notte sulla scheda rialzata fa 1,2:1: senza bordo l'etichetta e' una macchia nel grigio.
    @Test
    fun `l'etichetta scura prende il contorno e quella chiara no`() {
        val scura = TextView(tema).apply { etichettaDiSquadra(bluNotte) }
        val chiara = TextView(tema).apply { etichettaDiSquadra(giallo) }

        val bordoScura = (scura.background as MaterialShapeDrawable).strokeWidth
        val bordoChiara = (chiara.background as MaterialShapeDrawable).strokeWidth
        assertTrue("blu notte senza contorno", bordoScura > 0f)
        assertEquals("il giallo non ne ha bisogno", 0f, bordoChiara, 0f)
        assertEquals(base.getColor(R.color.elite_text_secondary), (scura.background as MaterialShapeDrawable).strokeColor?.defaultColor)
    }
}
