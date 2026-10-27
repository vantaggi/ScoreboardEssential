package it.vantaggi.scoreboardessential.ui

import android.content.Context
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import it.vantaggi.scoreboardessential.R

/**
 * Gli otto colori di squadra del dialogo del colore (passo 7 della revisione dei menu), nell'ordine
 * dei cerchi di `dialog_team_palette`. Il primo e il secondo sono i predefiniti dei due lati.
 */
object TavolozzaDiSquadra {
    val colori =
        listOf(
            R.color.team_palette_1,
            R.color.team_palette_2,
            R.color.team_palette_3,
            R.color.team_palette_4,
            R.color.team_palette_5,
            R.color.team_palette_6,
            R.color.team_palette_7,
            R.color.team_palette_8,
        )

    val cerchi =
        listOf(
            R.id.team_palette_1,
            R.id.team_palette_2,
            R.id.team_palette_3,
            R.id.team_palette_4,
            R.id.team_palette_5,
            R.id.team_palette_6,
            R.id.team_palette_7,
            R.id.team_palette_8,
        )

    /** Il cerchio del colore [colore], o null se e' un colore della ruota fuori dalla tavolozza. */
    fun indiceDi(
        context: Context,
        colore: Int?,
    ): Int? = colori.indexOfFirst { context.getColor(it) == colore }.takeIf { it >= 0 }

    /**
     * Il cerchio scelto ha l'anello chiaro e la spunta nell'inchiostro di squadra (l'iconTint di
     * dipingiDiSquadra), ed e' selezionato per TalkBack; gli altri il bordo normale del secondario.
     */
    fun segna(
        cerchio: MaterialButton,
        scelto: Boolean,
    ) {
        val context = cerchio.context
        cerchio.isSelected = scelto
        cerchio.strokeColor = ColorStateList.valueOf(context.getColor(if (scelto) R.color.elite_text_primary else R.color.elite_outline))
        cerchio.strokeWidth =
            context.resources.getDimensionPixelSize(if (scelto) R.dimen.focus_ring_width else R.dimen.border_width)
        cerchio.icon = if (scelto) ContextCompat.getDrawable(context, R.drawable.ic_check) else null
    }
}
