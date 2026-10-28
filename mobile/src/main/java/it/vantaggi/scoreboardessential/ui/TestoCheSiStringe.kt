package it.vantaggi.scoreboardessential.ui

import android.content.Context
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import com.google.android.material.textview.MaterialTextView
import it.vantaggi.scoreboardessential.R

/**
 * Un testo del gioco che si stringe prima di perdere lettere: i nomi lunghi delle squadre
 * («Polisportiva Sangiorgese») a 360dp finivano in «Polisporti…», e nella striscia del calcio
 * «Gol Polisportiva Sangiorge…».
 *
 * Alla misura prova la misura del testo (quella del layout, in dp) e scende di 1dp alla volta fino a
 * `minimo`, finche' ogni riga entra intera nella larghezza. Le righe sono quelle che il testo chiede
 * (`maxLines`, deciso da chi lo usa). Con `aCapoSeNonBasta` (i nomi) se su una riga non entra a
 * nessuna misura prova due righe, spezzate solo fra parole, partendo da `massimoADueRighe`: due righe
 * piu' grandi non starebbero nella riga dei nomi con la barretta. Vale anche per la striscia del
 * padel, dove i puntini si mangiavano il punteggio («Point Polisportiva Sangiorgese · 0-…»). Se non
 * entra neanche cosi' resta il minimo con i puntini in fondo, come prima. Nessun movimento: la misura
 * cambia solo col testo.
 *
 * L'interlinea e' quella naturale del carattere alla misura scelta. Quella dello stile (24sp del
 * TitleMedium) era in sp: al 200% cresceva di una dozzina di dp, e le due righe del nome non stavano
 * piu' nei 48dp con la barretta (la «g» di «Sangiorgese» tagliata in fondo).
 */
class TestoCheSiStringe
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = android.R.attr.textViewStyle,
    ) : MaterialTextView(context, attrs, defStyleAttr) {
        private val grande: Float = textSize
        private val minimo: Float
        private val aCapoSeNonBasta: Boolean
        private val massimoADueRighe: Float

        /** Le righe che chiede chi usa il testo; quelle in piu' per andare a capo le decide [adatta]. */
        private var righeChieste: Int

        init {
            val valori = context.obtainStyledAttributes(attrs, R.styleable.TestoCheSiStringe)
            minimo = valori.getDimension(R.styleable.TestoCheSiStringe_minimo, grande)
            aCapoSeNonBasta = valori.getBoolean(R.styleable.TestoCheSiStringe_aCapoSeNonBasta, false)
            massimoADueRighe = valori.getDimension(R.styleable.TestoCheSiStringe_massimoADueRighe, grande)
            valori.recycle()
            righeChieste = maxLines
            // Mai a meta' parola: con la sillabazione «Polispor-tiva» sembrerebbe un errore.
            hyphenationFrequency = Layout.HYPHENATION_FREQUENCY_NONE
            interlineaNaturale()
        }

        override fun setMaxLines(maxLines: Int) {
            righeChieste = maxLines
            super.setMaxLines(maxLines)
        }

        private fun interlineaNaturale() {
            if (lineSpacingExtra != 0f || lineSpacingMultiplier != 1f) setLineSpacing(0f, 1f)
        }

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                val spazio = MeasureSpec.getSize(widthMeasureSpec) - compoundPaddingLeft - compoundPaddingRight
                if (spazio > 0) adatta(spazio)
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }

        private fun adatta(spazio: Int) {
            val testo = text?.toString().orEmpty()
            if (testo.isEmpty()) return
            val righe = righeChieste
            val passo = resources.displayMetrics.density
            var scelta: Pair<Float, Int>? = null
            var px = grande
            while (scelta == null && px >= minimo - 0.5f) {
                if (entra(testo, px, righe, spazio)) scelta = px to righe
                px -= passo
            }
            if (scelta == null && aCapoSeNonBasta && righe == 1) {
                px = minOf(grande, massimoADueRighe)
                while (scelta == null && px >= minimo - 0.5f) {
                    if (entra(testo, px, 2, spazio)) scelta = px to 2
                    px -= passo
                }
            }
            val (misura, quante) = scelta ?: (minimo to if (aCapoSeNonBasta) maxOf(righe, 2) else righe)
            if (textSize != misura) super.setTextSize(TypedValue.COMPLEX_UNIT_PX, misura)
            // Da Android 14 un'interlinea data in sp torna a ogni cambio di misura.
            interlineaNaturale()
            if (maxLines != quante) super.setMaxLines(quante)
        }

        /** Il testo sta in [righe] righe larghe [spazio], intero, senza puntini e senza parole spezzate. */
        private fun entra(
            testo: String,
            px: Float,
            righe: Int,
            spazio: Int,
        ): Boolean {
            val penna = TextPaint(paint).apply { textSize = px }
            val impaginato =
                StaticLayout.Builder
                    .obtain(testo, 0, testo.length, penna, spazio)
                    .setMaxLines(righe)
                    .setEllipsize(TextUtils.TruncateAt.END)
                    .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
                    .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
                    .setIncludePad(includeFontPadding)
                    .build()
            val ultima = impaginato.lineCount - 1
            if (impaginato.lineCount > righe || impaginato.getLineEnd(ultima) != testo.length) return false
            if ((0..ultima).any { impaginato.getEllipsisCount(it) > 0 }) return false
            // Ogni a capo cade fra due parole (uno spazio o un a capo del testo), mai dentro una parola.
            return (0 until ultima).all { testo[impaginato.getLineEnd(it) - 1].isWhitespace() }
        }
    }
