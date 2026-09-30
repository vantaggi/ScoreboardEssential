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
    fun `primario, secondario e superficie del tema sono i token del progetto`() {
        assertEquals(token(R.color.graffiti_pink), attributo(context, androidx.appcompat.R.attr.colorPrimary))
        assertEquals(token(R.color.asphalt_black), attributo(context, M3.attr.colorOnPrimary))
        assertEquals(token(R.color.neon_cyan), attributo(context, M3.attr.colorSecondary))
        assertEquals(token(R.color.asphalt_black), attributo(context, M3.attr.colorOnSecondary))
        assertEquals(token(R.color.concrete_gray), attributo(context, M3.attr.colorSurface))
        assertEquals(token(R.color.stencil_white), attributo(context, M3.attr.colorOnSurface))
    }

    // DESIGN.md, Contorno: tema e dialoghi. Prima questi ruoli restavano ai valori base di M3.
    @Test
    fun `i contenitori, i bordi e le superfici prendono i grigi dello street`() {
        val attesi =
            mapOf(
                "colorPrimaryContainer" to (M3.attr.colorPrimaryContainer to R.color.graffiti_dark_gray),
                "colorOnPrimaryContainer" to (M3.attr.colorOnPrimaryContainer to R.color.stencil_white),
                "colorSurfaceContainerLowest" to (M3.attr.colorSurfaceContainerLowest to R.color.asphalt_dark),
                "colorSurfaceContainerLow" to (M3.attr.colorSurfaceContainerLow to R.color.concrete_gray),
                "colorSurfaceContainer" to (M3.attr.colorSurfaceContainer to R.color.concrete_gray),
                "colorSurfaceContainerHigh" to (M3.attr.colorSurfaceContainerHigh to R.color.graffiti_dark_gray),
                "colorSurfaceContainerHighest" to (M3.attr.colorSurfaceContainerHighest to R.color.graffiti_dark_gray),
                "colorOutline" to (M3.attr.colorOutline to R.color.outline_gray),
                "colorOutlineVariant" to (M3.attr.colorOutlineVariant to R.color.graffiti_dark_gray),
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
    fun `un dialogo costruito senza overlay ha fondo grigio e accento ciano, non viola`() {
        val contestoDelDialogo = MaterialAlertDialogBuilder(context).context

        assertEquals(token(R.color.graffiti_dark_gray), attributo(contestoDelDialogo, M3.attr.colorSurfaceContainerHigh))
        assertEquals(token(R.color.neon_cyan), attributo(contestoDelDialogo, androidx.appcompat.R.attr.colorPrimary))
        assertEquals(token(R.color.stencil_white), attributo(contestoDelDialogo, M3.attr.colorOnSurface))

        val fondo = attributo(contestoDelDialogo, M3.attr.colorSurfaceContainerHigh)
        assertTrue(TeamInk.contrast(attributo(contestoDelDialogo, M3.attr.colorOnSurface), fondo) >= 4.5)
        assertFalse(eViola(attributo(contestoDelDialogo, androidx.appcompat.R.attr.colorPrimary)))
    }

    // Gli avatar delle rose usavano colorPrimaryContainer, il viola #4F378B di M3.
    @Test
    fun `l'avatar di un giocatore della rosa e' grigio con iniziali leggibili`() {
        val riga = LayoutInflater.from(context).inflate(R.layout.team_player_item, null)
        val avatar = riga.findViewById<MaterialCardView>(R.id.player_avatar_container)
        val iniziali = riga.findViewById<TextView>(R.id.player_initials)

        val fondo = avatar.cardBackgroundColor.defaultColor
        assertEquals(token(R.color.graffiti_dark_gray), fondo)
        assertFalse(eViola(fondo))
        assertTrue(TeamInk.contrast(iniziali.currentTextColor, fondo) >= 4.5)
    }

    // Viola = tonalita' fra 250 e 300 gradi con una saturazione che si nota: il grigio appena
    // tinto (#49454F) non e' il caso cercato, #4F378B e #D0BCFF si'.
    private fun eViola(colore: Int): Boolean {
        val hsv = FloatArray(3)
        Color.colorToHSV(colore, hsv)
        return hsv[0] in 250f..300f && hsv[1] > 0.15f
    }
}
