package it.vantaggi.scoreboardessential.ui

import android.content.Context
import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import it.vantaggi.scoreboardessential.R

/**
 * Gli otto colori di squadra del dialogo del colore (user-1..user-8 della UI Constitution, PR 6),
 * nell'ordine dei cerchi di `dialog_team_palette`. Il primo e il secondo sono i predefiniti dei due lati.
 */
object TavolozzaDiSquadra {
    val colori =
        listOf(
            R.color.user_1,
            R.color.user_2,
            R.color.user_3,
            R.color.user_4,
            R.color.user_5,
            R.color.user_6,
            R.color.user_7,
            R.color.user_8,
        )

    val cerchi =
        listOf(
            R.id.user_1,
            R.id.user_2,
            R.id.user_3,
            R.id.user_4,
            R.id.user_5,
            R.id.user_6,
            R.id.user_7,
            R.id.user_8,
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
        cerchio.strokeColor = ColorStateList.valueOf(context.getColor(if (scelto) R.color.text_primary else R.color.border_strong))
        cerchio.strokeWidth =
            context.resources.getDimensionPixelSize(if (scelto) R.dimen.focus_ring_width else R.dimen.border_width)
        cerchio.icon = if (scelto) ContextCompat.getDrawable(context, R.drawable.ic_check) else null
    }
}
