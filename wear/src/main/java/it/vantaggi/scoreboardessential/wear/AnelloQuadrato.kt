package it.vantaggi.scoreboardessential.wear

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat

/**
 * L'anello del portiere sul quadrante quadrato (bozza W3b di Cioti): un rettangolo arrotondato lungo
 * la cornice invece del cerchio, che sul quadrato tagliava le fasce di testo e lasciava vuoti gli
 * angoli. Fa quello che fa il cerchio (`circular_progress_bar`): parte dal centro in alto e corre in
 * senso orario per la frazione del livello (0-10000, quello che la ProgressBar gli da'), nello stesso
 * colore e con lo stesso spessore.
 *
 * Misure in dimens: `keeper_ring_square_inset` dal bordo dello schermo al bordo esterno,
 * `keeper_ring_thickness`, `keeper_ring_square_radius` per gli angoli (sul bordo esterno). Il bordo
 * interno sta quindi a inset + spessore dallo schermo: i testi del quadrato stanno 3dp piu' dentro
 * (QuadranteDentroLAnelloTest).
 */
internal class AnelloQuadrato(
    context: Context,
) : Drawable() {
    private val rientro = context.resources.getDimension(R.dimen.keeper_ring_square_inset)
    private val spessore = context.resources.getDimension(R.dimen.keeper_ring_thickness)
    private val raggio = context.resources.getDimension(R.dimen.keeper_ring_square_radius)

    private val penna =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = spessore
            strokeCap = Paint.Cap.BUTT
            color = ContextCompat.getColor(context, R.color.elite_text_secondary)
        }

    private val giro = Path()
    private val tratto = Path()
    private val misura = PathMeasure()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        // La linea passa a meta' spessore: cosi' il bordo esterno sta a `rientro` dallo schermo.
        val meta = rientro + spessore / 2f
        val r = RectF(bounds.left + meta, bounds.top + meta, bounds.right - meta, bounds.bottom - meta)
        val curva = (raggio - spessore / 2f).coerceAtLeast(0f)
        giro.reset()
        // Dal centro in alto, in senso orario, come il cerchio ruotato di 270 gradi.
        giro.moveTo(r.centerX(), r.top)
        giro.lineTo(r.right - curva, r.top)
        giro.arcTo(RectF(r.right - 2 * curva, r.top, r.right, r.top + 2 * curva), 270f, 90f)
        giro.lineTo(r.right, r.bottom - curva)
        giro.arcTo(RectF(r.right - 2 * curva, r.bottom - 2 * curva, r.right, r.bottom), 0f, 90f)
        giro.lineTo(r.left + curva, r.bottom)
        giro.arcTo(RectF(r.left, r.bottom - 2 * curva, r.left + 2 * curva, r.bottom), 90f, 90f)
        giro.lineTo(r.left, r.top + curva)
        giro.arcTo(RectF(r.left, r.top, r.left + 2 * curva, r.top + 2 * curva), 180f, 90f)
        giro.lineTo(r.centerX(), r.top)
        misura.setPath(giro, false)
    }

    override fun onLevelChange(level: Int): Boolean {
        invalidateSelf()
        return true
    }

    /** La frazione disegnata, da 0 a 1: serve ai test. */
    internal val frazione: Float get() = level / MASSIMO

    override fun draw(canvas: Canvas) {
        val lunghezza = misura.length * frazione
        if (lunghezza <= 0f) return
        tratto.reset()
        misura.getSegment(0f, lunghezza, tratto, true)
        canvas.drawPath(tratto, penna)
    }

    override fun setAlpha(alpha: Int) {
        penna.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        penna.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val MASSIMO = 10_000f
    }
}
