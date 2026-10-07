package it.vantaggi.scoreboardessential

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import it.vantaggi.scoreboardessential.core.TeamInk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.google.android.material.R as M3

/**
 * Passo 10 della pista Telefono: il tema risolve i ruoli di Material 3 ai token del progetto,
 * e i dialoghi (che prendono sfondo e accento dal tema) li ereditano senza un overlay passato a mano.
 */
@RunWith(AndroidJUnit4::class)
class TemaDelTelefonoTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.Theme_ScoreboardEssential)
    }

    private fun attributo(
        contesto: Context,
        id: Int,
    ) = MaterialColors.getColor(contesto, id, "manca")

    private fun token(id: Int) = context.getColor(id)

    // Gli accenti e i loro inchiostri: sono i ruoli che il tema dichiarava gia', qui si fissano.
    @Test
    fun `primario, secondario e superficie del tema sono i token di Padel Elite`() {
        assertEquals(token(R.color.elite_lime), attributo(context, androidx.appcompat.R.attr.colorPrimary))
        assertEquals(token(R.color.elite_on_lime), attributo(context, M3.attr.colorOnPrimary))
        assertEquals(token(R.color.elite_text_secondary), attributo(context, M3.attr.colorSecondary))
        assertEquals(token(R.color.elite_background), attributo(context, M3.attr.colorOnSecondary))
        assertEquals(token(R.color.elite_surface), attributo(context, M3.attr.colorSurface))
        assertEquals(token(R.color.elite_text_primary), attributo(context, M3.attr.colorOnSurface))
    }

    // DESIGN.md, Contorno: tema e dialoghi. Prima questi ruoli restavano ai valori base di M3.
    @Test
    fun `i contenitori, i bordi e le superfici prendono i token di Padel Elite`() {
        val attesi =
            mapOf(
                "colorPrimaryContainer" to (M3.attr.colorPrimaryContainer to R.color.elite_surface_raised),
                "colorOnPrimaryContainer" to (M3.attr.colorOnPrimaryContainer to R.color.elite_text_primary),
                "colorSurfaceContainerLowest" to (M3.attr.colorSurfaceContainerLowest to R.color.elite_background),
                "colorSurfaceContainerLow" to (M3.attr.colorSurfaceContainerLow to R.color.elite_surface),
                "colorSurfaceContainer" to (M3.attr.colorSurfaceContainer to R.color.elite_surface),
                "colorSurfaceContainerHigh" to (M3.attr.colorSurfaceContainerHigh to R.color.elite_surface_raised),
                "colorSurfaceContainerHighest" to (M3.attr.colorSurfaceContainerHighest to R.color.elite_surface_raised),
                "colorOutline" to (M3.attr.colorOutline to R.color.elite_outline),
                "colorOutlineVariant" to (M3.attr.colorOutlineVariant to R.color.elite_border_strong),
            )

        for ((nome, coppia) in attesi) {
            assertEquals(nome, token(coppia.second), attributo(context, coppia.first))
        }
    }

    // "Niente viola di base da nessuna parte": ogni ruolo di colore di M3, compresi quelli che nessun
    // layout usa oggi ma che il primo componente nuovo userebbe senza avvisare.
    @Test
    fun `nessun ruolo di colore del tema e' viola`() {
        val ruoli =
            mapOf(
                "colorPrimary" to androidx.appcompat.R.attr.colorPrimary,
                "colorOnPrimary" to M3.attr.colorOnPrimary,
                "colorPrimaryInverse" to M3.attr.colorPrimaryInverse,
                "colorPrimaryContainer" to M3.attr.colorPrimaryContainer,
                "colorOnPrimaryContainer" to M3.attr.colorOnPrimaryContainer,
                "colorPrimaryFixed" to M3.attr.colorPrimaryFixed,
                "colorPrimaryFixedDim" to M3.attr.colorPrimaryFixedDim,
                "colorOnPrimaryFixed" to M3.attr.colorOnPrimaryFixed,
                "colorOnPrimaryFixedVariant" to M3.attr.colorOnPrimaryFixedVariant,
                "colorSecondary" to M3.attr.colorSecondary,
                "colorOnSecondary" to M3.attr.colorOnSecondary,
                "colorSecondaryContainer" to M3.attr.colorSecondaryContainer,
                "colorOnSecondaryContainer" to M3.attr.colorOnSecondaryContainer,
                "colorSecondaryFixed" to M3.attr.colorSecondaryFixed,
                "colorSecondaryFixedDim" to M3.attr.colorSecondaryFixedDim,
                "colorOnSecondaryFixed" to M3.attr.colorOnSecondaryFixed,
                "colorOnSecondaryFixedVariant" to M3.attr.colorOnSecondaryFixedVariant,
                "colorTertiary" to M3.attr.colorTertiary,
                "colorOnTertiary" to M3.attr.colorOnTertiary,
                "colorTertiaryContainer" to M3.attr.colorTertiaryContainer,
                "colorOnTertiaryContainer" to M3.attr.colorOnTertiaryContainer,
                "colorError" to androidx.appcompat.R.attr.colorError,
                "colorOnError" to M3.attr.colorOnError,
                "colorErrorContainer" to M3.attr.colorErrorContainer,
                "colorOnErrorContainer" to M3.attr.colorOnErrorContainer,
                "colorTertiaryFixed" to M3.attr.colorTertiaryFixed,
                "colorTertiaryFixedDim" to M3.attr.colorTertiaryFixedDim,
                "colorOnTertiaryFixed" to M3.attr.colorOnTertiaryFixed,
                "colorOnTertiaryFixedVariant" to M3.attr.colorOnTertiaryFixedVariant,
                "colorOnBackground" to M3.attr.colorOnBackground,
                "colorSurface" to M3.attr.colorSurface,
                "colorOnSurface" to M3.attr.colorOnSurface,
                "colorSurfaceVariant" to M3.attr.colorSurfaceVariant,
                "colorOnSurfaceVariant" to M3.attr.colorOnSurfaceVariant,
                "colorSurfaceInverse" to M3.attr.colorSurfaceInverse,
                "colorOnSurfaceInverse" to M3.attr.colorOnSurfaceInverse,
                "colorSurfaceBright" to M3.attr.colorSurfaceBright,
                "colorSurfaceDim" to M3.attr.colorSurfaceDim,
                "colorSurfaceContainer" to M3.attr.colorSurfaceContainer,
                "colorSurfaceContainerLow" to M3.attr.colorSurfaceContainerLow,
                "colorSurfaceContainerHigh" to M3.attr.colorSurfaceContainerHigh,
                "colorSurfaceContainerLowest" to M3.attr.colorSurfaceContainerLowest,
                "colorSurfaceContainerHighest" to M3.attr.colorSurfaceContainerHighest,
                "colorOutline" to M3.attr.colorOutline,
                "colorOutlineVariant" to M3.attr.colorOutlineVariant,
            )

        for ((nome, id) in ruoli) {
            assertFalse("$nome e' viola", eViola(attributo(context, id)))
        }
    }

    // Sfondo e accento dei dialoghi vengono da materialAlertDialogTheme. Senza il riferimento nel
    // tema, solo il dialogo del nome squadra (che lo passava a mano) era fatto bene.
    @Test
    fun `il tema dichiara l'overlay dei dialoghi come materialAlertDialogTheme`() {
        val valore = TypedValue()
        assertTrue(context.theme.resolveAttribute(M3.attr.materialAlertDialogTheme, valore, true))

        assertEquals(R.style.ThemeOverlay_App_MaterialAlertDialog, valore.resourceId)
    }

    // Marcatore, fine partita, annulla, reset del tempo, giocatori: tutti MaterialAlertDialogBuilder(this)
    // senza overlay. Il contesto del builder e' quello che i loro widget vedono.
    @Test
    fun `un dialogo costruito senza overlay ha fondo rialzato e accento lime, non viola`() {
        val contestoDelDialogo = MaterialAlertDialogBuilder(context).context

        assertEquals(token(R.color.elite_surface_raised), attributo(contestoDelDialogo, M3.attr.colorSurfaceContainerHigh))
        assertEquals(token(R.color.elite_lime), attributo(contestoDelDialogo, androidx.appcompat.R.attr.colorPrimary))
        assertEquals(token(R.color.elite_text_primary), attributo(contestoDelDialogo, M3.attr.colorOnSurface))

        val fondo = attributo(contestoDelDialogo, M3.attr.colorSurfaceContainerHigh)
        assertTrue(TeamInk.contrast(attributo(contestoDelDialogo, M3.attr.colorOnSurface), fondo) >= 4.5)
        assertFalse(eViola(attributo(contestoDelDialogo, androidx.appcompat.R.attr.colorPrimary)))
    }

    // Gli avatar delle rose usavano colorPrimaryContainer, il viola #4F378B di M3; con G-6 la riga non ha
    // piu' l'avatar: il nome e' testo primario sul gruppo e non c'e' nessuna card.
    @Test
    fun `la riga di un giocatore della rosa non ha avatar e il nome si legge sul gruppo`() {
        val riga = LayoutInflater.from(context).inflate(R.layout.team_player_item, null)

        assertFalse(riga is MaterialCardView)
        val nome = riga.findViewById<TextView>(R.id.player_name)
        assertTrue(TeamInk.contrast(nome.currentTextColor, token(R.color.elite_surface)) >= 4.5)
    }

    // G-0: i testi del tema (onSurface, onSurfaceVariant) restano leggibili su ogni fondo che il tema
    // espone, e il contorno dei comandi ha 3:1: se qualcuno rimappa un ruolo sul bordo della dashboard
    // (1,4:1) il test lo vede.
    @Test
    fun `i testi e il contorno del tema passano AA e 3 a 1 sui fondi del tema`() {
        val fondi =
            mapOf(
                "background" to attributo(context, android.R.attr.colorBackground),
                "surface" to attributo(context, M3.attr.colorSurface),
                "surfaceContainerLowest" to attributo(context, M3.attr.colorSurfaceContainerLowest),
                "surfaceContainerHigh" to attributo(context, M3.attr.colorSurfaceContainerHigh),
                "surfaceContainerHighest" to attributo(context, M3.attr.colorSurfaceContainerHighest),
            )
        val suSuperficie = attributo(context, M3.attr.colorOnSurface)
        val suVariante = attributo(context, M3.attr.colorOnSurfaceVariant)
        val contorno = attributo(context, M3.attr.colorOutline)

        for ((nome, fondo) in fondi) {
            assertTrue("onSurface su $nome", TeamInk.contrast(suSuperficie, fondo) >= 4.5)
            assertTrue("onSurfaceVariant su $nome", TeamInk.contrast(suVariante, fondo) >= 4.5)
            assertTrue("colorOutline su $nome", TeamInk.contrast(contorno, fondo) >= 3.0)
        }
        // Falsificazione: il bordo della dashboard come contorno non passerebbe.
        assertTrue(TeamInk.contrast(token(R.color.elite_border_strong), attributo(context, M3.attr.colorSurface)) < 3.0)
    }

    // Viola = tonalita' fra 250 e 300 gradi con una saturazione che si nota: il grigio appena
    // tinto (#49454F) non e' il caso cercato, #4F378B e #D0BCFF si'.
    private fun eViola(colore: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(colore, hsv)
        return hsv[0] in 250f..300f && hsv[1] > 0.15f
    }
}
