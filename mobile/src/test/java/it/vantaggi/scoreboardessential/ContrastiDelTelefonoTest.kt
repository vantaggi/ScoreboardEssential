package it.vantaggi.scoreboardessential

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.MaterialShapeDrawable
import it.vantaggi.scoreboardessential.core.TeamInk
import it.vantaggi.scoreboardessential.utils.dipingiDiSquadra
import it.vantaggi.scoreboardessential.utils.etichettaDiSquadra
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * I contrasti di L11 che si possono provare senza un emulatore: il tema, i pulsanti colore delle
 * impostazioni e le etichette di squadra di rose e formazioni.
 */
@RunWith(AndroidJUnit4::class)
class ContrastiDelTelefonoTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_ScoreboardEssential)
    }

    private fun attributo(id: Int) = MaterialColors.getColor(context, id, "manca")

    // Col bianco sporco il rosa faceva 3,17:1 (testo dei pulsanti pieni) e il ciano 1,17:1
    // (icona del FAB giocatori).
    @Test
    fun `sopra rosa e ciano del tema il testo passa AA`() {
        // colorPrimary non sta piu' in R.attr di Material 1.13: si prende da appcompat.
        val primario = attributo(androidx.appcompat.R.attr.colorPrimary)
        val suPrimario = attributo(com.google.android.material.R.attr.colorOnPrimary)
        val secondario = attributo(com.google.android.material.R.attr.colorSecondary)
        val suSecondario = attributo(com.google.android.material.R.attr.colorOnSecondary)

        assertTrue("sul rosa %.2f".format(TeamInk.contrast(suPrimario, primario)), TeamInk.contrast(suPrimario, primario) >= 4.5)
        assertTrue(
            "sul ciano %.2f".format(TeamInk.contrast(suSecondario, secondario)),
            TeamInk.contrast(suSecondario, secondario) >= 4.5,
        )
    }

    // TEAM 1 COLOR e TEAM 2 COLOR: sul giallo e sul verde predefiniti la scritta era a 1,07 e 1,01.
    @Test
    fun `il pulsante colore scrive nero sul giallo e bianco sul blu notte`() {
        val giallo = context.getColor(R.color.team_spray_yellow)
        val verde = context.getColor(R.color.team_electric_green)
        val bluNotte = 0xFF1A237E.toInt()

        for (colore in listOf(giallo, verde, bluNotte)) {
            val pulsante = MaterialButton(context).apply { dipingiDiSquadra(colore) }
            val inchiostro = pulsante.currentTextColor
            assertEquals(TeamInk.on(colore), inchiostro)
            assertEquals(inchiostro, pulsante.iconTint?.defaultColor)
            assertTrue(TeamInk.contrast(inchiostro, colore) >= 4.5)
        }
        assertEquals(TeamInk.NERO, MaterialButton(context).apply { dipingiDiSquadra(giallo) }.currentTextColor)
        assertEquals(TeamInk.BIANCO, MaterialButton(context).apply { dipingiDiSquadra(bluNotte) }.currentTextColor)
    }

    // Rose e formazioni: il colore della squadra era il colore del TESTO su #1E1E1E (blu notte 1,26).
    @Test
    fun `l'etichetta di squadra e' un tag pieno con testo leggibile`() {
        val bluNotte = 0xFF1A237E.toInt()
        val etichetta = TextView(context).apply { etichettaDiSquadra(bluNotte) }

        assertEquals(bluNotte, (etichetta.background as MaterialShapeDrawable).fillColor?.defaultColor)
        assertEquals(TeamInk.BIANCO, etichetta.currentTextColor)
        assertTrue(TeamInk.contrast(etichetta.currentTextColor, bluNotte) >= 4.5)
    }
}
