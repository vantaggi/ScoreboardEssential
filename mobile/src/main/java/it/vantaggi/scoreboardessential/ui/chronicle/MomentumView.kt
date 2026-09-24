package it.vantaggi.scoreboardessential.ui.chronicle

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withClip
import it.vantaggi.scoreboardessential.R
import kotlin.math.abs

/**
 * L'andamento della partita: per ogni punto, punti vinti dal lato 1 meno punti vinti dal lato 2.
 * Sopra lo zero e' avanti il lato 1, sotto il lato 2; le linee tratteggiate chiudono i set.
 *
 * Disegnato a mano con Canvas: e' una linea spezzata con due riempimenti, e una libreria di
 * grafici porterebbe nell'app un peso e un aspetto che nessun'altra schermata ha.
 */
class MomentumView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : View(context, attrs) {
        private var diffs: List<Int> = emptyList()
        private var setEnds: List<Int> = emptyList()
        private var color1 = 0
        private var color2 = 0

        private val density = resources.displayMetrics.density
        private val linePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2 * density
                strokeJoin = Paint.Join.ROUND
            }
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val zeroPaint =
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = density
                color = ContextCompat.getColor(context, R.color.outline_gray)
            }
        private val setPaint =
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = density
                color = ContextCompat.getColor(context, R.color.sidewalk_gray)
                pathEffect = DashPathEffect(floatArrayOf(4 * density, 4 * density), 0f)
            }
        private val path = Path()

        /**
         * [diffs] e' l'andamento cumulato, un valore per punto. [setEnds] sono gli indici dei punti
         * che hanno chiuso un set (non l'ultimo della partita). I colori devono gia' reggere sul
         * fondo della card: li decide chi chiama, con la regola di TeamInk.
         */
        fun setData(
            diffs: List<Int>,
            setEnds: List<Int>,
            color1: Int,
            color2: Int,
        ) {
            this.diffs = diffs
            this.setEnds = setEnds
            this.color1 = color1
            this.color2 = color2
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (diffs.isEmpty()) return
            val pad = linePaint.strokeWidth
            val left = paddingLeft + pad
            val width = this.width - paddingRight - pad - left
            val top = paddingTop + pad
            val height = this.height - paddingBottom - pad - top
            val mid = top + height / 2
            // Scala simmetrica: lo zero sta sempre a meta', cosi' "sopra" e "sotto" si confrontano.
            val maxAbs = maxOf(1, diffs.maxOf { abs(it) })

            // Il punto 0 e' la partenza a zero; il punto k della lista sta alla x k + 1.
            fun x(i: Float) = left + width * i / diffs.size

            fun y(d: Int) = mid - d * (height / 2) / maxAbs

            // I riempimenti: la stessa spezzata chiusa sullo zero, tagliata sopra e sotto.
            path.reset()
            path.moveTo(x(0f), mid)
            diffs.forEachIndexed { i, d -> path.lineTo(x(i + 1f), y(d)) }
            path.lineTo(x(diffs.size.toFloat()), mid)
            path.close()
            fillPaint.color = ColorUtils.setAlphaComponent(color1, FILL_ALPHA)
            canvas.withClip(left, top, left + width, mid) { drawPath(path, fillPaint) }
            fillPaint.color = ColorUtils.setAlphaComponent(color2, FILL_ALPHA)
            canvas.withClip(left, mid, left + width, top + height) { drawPath(path, fillPaint) }

            canvas.drawLine(left, mid, left + width, mid, zeroPaint)
            // Fra l'ultimo punto del set e il primo del successivo.
            setEnds.forEach { e ->
                val sx = x(e + 1.5f)
                canvas.drawLine(sx, top, sx, top + height, setPaint)
            }

            // La linea, un tratto per punto nel colore di chi e' avanti alla fine del tratto; sullo
            // zero conta da dove si arriva, come nella dashboard.
            var prev = 0
            diffs.forEachIndexed { i, d ->
                val lead = if (d != 0) d else prev
                linePaint.color =
                    when {
                        lead > 0 -> color1
                        lead < 0 -> color2
                        else -> zeroPaint.color
                    }
                canvas.drawLine(x(i.toFloat()), y(prev), x(i + 1f), y(d), linePaint)
                prev = d
            }
        }

        private companion object {
            // Circa il 15% di opacita': il riempimento dice chi e' avanti senza coprire la linea.
            const val FILL_ALPHA = 40
        }
    }
