package it.vantaggi.scoreboardessential.ui.chronicle

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import it.vantaggi.scoreboardessential.R
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import it.vantaggi.scoreboardessential.shared.R as SharedR

/**
 * L'andamento della partita, e risponde a una domanda sola: chi e' stato avanti nei punti vinti.
 * Per ogni punto, punti vinti dal lato 1 meno punti vinti dal lato 2, cumulati. Il lato 1 sta
 * SOPRA lo zero e il lato 2 SOTTO, e il colore non e' l'unico segno: ogni lato ha il suo nome,
 * scritto nella fascia sopra (lato 1) o sotto (lato 2) il grafico con un tratto del suo colore e,
 * se e' mai stato avanti, il suo vantaggio massimo. Le linee tratteggiate chiudono i set.
 *
 * Nella forma piu' semplice: una linea, l'asse dello zero (`border-strong`, perche' dice sopra e
 * sotto) e le due estremita' sulla griglia (`chart-grid`). Niente riempimenti: dicevano quello che
 * dice la posizione. Disegnato a mano con Canvas: e' una linea spezzata, e una libreria di grafici
 * porterebbe nell'app un peso e un aspetto che nessun'altra schermata ha.
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
        private var name1 = ""
        private var name2 = ""

        private val density = resources.displayMetrics.density
        private val inter: Typeface = ResourcesCompat.getFont(context, SharedR.font.inter) ?: Typeface.DEFAULT
        private val linePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2 * density
                strokeJoin = Paint.Join.ROUND
            }
        private val axisPaint =
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = density
                color = ContextCompat.getColor(context, R.color.elite_outline)
            }
        private val gridPaint =
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = density
                color = ContextCompat.getColor(context, R.color.elite_border_strong)
            }
        private val setPaint =
            Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = density
                color = ContextCompat.getColor(context, R.color.elite_outline)
                pathEffect = DashPathEffect(floatArrayOf(4 * density, 4 * density), 0f)
            }

        // Nome e vantaggio: Inter 500 a 12sp (caption), cifre tabulari, nel testo primario.
        private val labelPaint =
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.create(inter, WEIGHT_MEDIUM, false)
                textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, LABEL_SP, resources.displayMetrics)
                color = ContextCompat.getColor(context, R.color.elite_text_primary)
                fontFeatureSettings = "tnum"
            }
        private val swatchPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2 * density
                strokeCap = Paint.Cap.ROUND
            }

        /** I colori dei due lati cosi' come si disegnano, per chi vuole verificarli. */
        val sideColors: Pair<Int, Int> get() = color1 to color2

        /** Le due etichette dirette come si scrivono sul grafico, lato 1 e poi lato 2. */
        val labels: List<String>
            get() = if (diffs.isEmpty()) emptyList() else listOf(labelText(name1, maxLead(1)), labelText(name2, maxLead(2)))

        /** Il vantaggio massimo di un lato in punti vinti, zero se non e' mai stato avanti. */
        private fun maxLead(side: Int): Int = if (side == 1) maxOf(0, diffs.max()) else maxOf(0, -diffs.min())

        /** "Nome +12", o solo il nome se quel lato non e' mai stato avanti: un +0 non direbbe niente. */
        private fun labelText(
            name: String,
            lead: Int,
        ): String = if (lead > 0) "$name +$lead" else name

        /**
         * [diffs] e' l'andamento cumulato, un valore per punto. [setEnds] sono gli indici dei punti
         * che hanno chiuso un set (non l'ultimo della partita). I colori devono gia' reggere sul
         * fondo del gruppo: li decide chi chiama, con la regola di TeamInk. I nomi sono quelli dei
         * due lati, lato 1 per primo.
         */
        fun setData(
            diffs: List<Int>,
            setEnds: List<Int>,
            color1: Int,
            color2: Int,
            name1: String,
            name2: String,
        ) {
            this.diffs = diffs
            this.setEnds = setEnds
            this.color1 = color1
            this.color2 = color2
            this.name1 = name1
            this.name2 = name2
            invalidate()
        }

        /** L'altezza di una fascia dei nomi: una riga di testo con un po' d'aria, e cresce col carattere. */
        private fun bandHeight(): Float {
            val m = labelPaint.fontMetrics
            return ceil(m.descent - m.ascent) + 2 * BAND_PAD_DP * density
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            val wanted = (PLOT_HEIGHT_DP * density + 2 * bandHeight()).roundToInt() + paddingTop + paddingBottom
            setMeasuredDimension(getDefaultSize(suggestedMinimumWidth, widthMeasureSpec), resolveSize(wanted, heightMeasureSpec))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (diffs.isEmpty()) return
            val band = bandHeight()
            val pad = linePaint.strokeWidth
            val left = paddingLeft + pad
            val width = this.width - paddingRight - pad - left
            val top = paddingTop + band
            val bottom = this.height - paddingBottom - band
            val mid = (top + bottom) / 2
            val half = (bottom - top) / 2 - pad
            // Scala simmetrica: lo zero sta sempre a meta', cosi' "sopra" e "sotto" si confrontano.
            val maxAbs = maxOf(1, diffs.maxOf { abs(it) })

            // Il punto 0 e' la partenza a zero; il punto k della lista sta alla x k + 1.
            fun x(i: Float) = left + width * i / diffs.size

            fun y(d: Int) = mid - d * half / maxAbs

            // Le due estremita' sulla griglia, poi l'asse dello zero e le fini dei set.
            canvas.drawLine(left, top, left + width, top, gridPaint)
            canvas.drawLine(left, bottom, left + width, bottom, gridPaint)
            canvas.drawLine(left, mid, left + width, mid, axisPaint)
            // Fra l'ultimo punto del set e il primo del successivo.
            setEnds.forEach { e ->
                val sx = x(e + 1.5f)
                canvas.drawLine(sx, top, sx, bottom, setPaint)
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
                        else -> axisPaint.color
                    }
                canvas.drawLine(x(i.toFloat()), y(prev), x(i + 1f), y(d), linePaint)
                prev = d
            }

            // Etichette dirette: il nome con il suo tratto, sopra per il lato 1 e sotto per il lato 2.
            val (text1, text2) = labels
            label(canvas, left, width, paddingTop + band / 2, color1, text1)
            label(canvas, left, width, this.height - paddingBottom - band / 2, color2, text2)
        }

        private fun label(
            canvas: Canvas,
            left: Float,
            width: Float,
            centerY: Float,
            ink: Int,
            text: String,
        ) {
            swatchPaint.color = ink
            val swatch = SWATCH_DP * density
            canvas.drawLine(left, centerY, left + swatch, centerY, swatchPaint)
            val start = left + swatch + LABEL_GAP_DP * density
            val shown = TextUtils.ellipsize(text, labelPaint, maxOf(0f, left + width - start), TextUtils.TruncateAt.END).toString()
            val m = labelPaint.fontMetrics
            canvas.drawText(shown, start, centerY - (m.ascent + m.descent) / 2, labelPaint)
        }

        private companion object {
            const val PLOT_HEIGHT_DP = 160
            const val LABEL_SP = 12f
            const val BAND_PAD_DP = 4
            const val SWATCH_DP = 16
            const val LABEL_GAP_DP = 8
            const val WEIGHT_MEDIUM = 500
        }
    }
