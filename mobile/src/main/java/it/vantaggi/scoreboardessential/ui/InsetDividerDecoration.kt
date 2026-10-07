package it.vantaggi.scoreboardessential.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import androidx.recyclerview.widget.RecyclerView
import it.vantaggi.scoreboardessential.R

/**
 * La linea sottile fra le righe di un gruppo (UI Constitution): `border-subtle` di 1dp, rientrata fino
 * al bordo del testo ([insetStart]) e dopo l'ultima riga niente. Si disegna sopra la riga, nel suo
 * ultimo pixel, quindi non cambia le misure.
 */
class InsetDividerDecoration(
    context: Context,
    private val insetStart: Int = context.resources.getDimensionPixelSize(R.dimen.space_16),
) : RecyclerView.ItemDecoration() {
    private val spessore = context.resources.getDimension(R.dimen.border_width)
    private val pennello =
        Paint().apply {
            color = context.getColor(R.color.elite_border_strong)
            style = Paint.Style.FILL
        }

    override fun onDrawOver(
        canvas: Canvas,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val ultimo = (parent.adapter?.itemCount ?: 0) - 1
        for (i in 0 until parent.childCount) {
            val riga = parent.getChildAt(i)
            if (parent.getChildAdapterPosition(riga) >= ultimo) continue
            val y = riga.bottom + riga.translationY
            canvas.drawRect(riga.left + insetStart + riga.translationX, y - spessore, riga.right.toFloat(), y, pennello)
        }
    }
}
