package it.vantaggi.scoreboardessential.wear

import android.content.Context
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView

/**
 * Un titolo in cui una parola non si spezza mai: se la parola piu' lunga non entra nella larghezza
 * che il genitore gli da, il carattere scende di 1sp alla volta fino a farcela, e non oltre 14sp.
 * Le righe vanno a capo solo sugli spazi (il comportamento normale del TextView).
 *
 * Perche' una vista e non un calcolo nel bind della voce: nel bind la card non ha ancora una
 * larghezza, e la larghezza vera dipende dal BoxInsetLayout del tondo (sul 192dp le card sono
 * larghe circa 104dp, e "PARTITA" a 20sp bold in 76dp di testo non c'entra: "FINE PA" / "RTITA").
 * In onMeasure la larghezza c'e' sempre, e un testo cambiato (la card del menu si arma e dice
 * "CHIUDERE 3-2?") rimisura da capo, ripartendo dalla dimensione dichiarata nel layout.
 *
 * La dimensione dichiarata (20sp) resta quella di tutti i casi in cui la parola entra: il quadrato
 * da 180dp e il tondo da 227dp non cambiano.
 *
 * AppCompatTextView e non MaterialTextView: quella applica la lineHeight di HeadlineSmall (32sp),
 * che il TextView di prima ignorava, e allargava le righe del titolo di un terzo.
 */
class TitoloIntero
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : AppCompatTextView(context, attrs) {
        // Il carattere dichiarato nel layout: da qui si riparte a ogni misura.
        private val dimensioneDichiarata = textSize
        private val dimensioneMinima = minOf(dimensioneDichiarata, sp(MINIMO_SP))
        private val passo = sp(1f)

        override fun onMeasure(
            widthMeasureSpec: Int,
            heightMeasureSpec: Int,
        ) {
            if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                val utile = MeasureSpec.getSize(widthMeasureSpec) - compoundPaddingLeft - compoundPaddingRight
                val dimensione = dimensioneChePiace(utile)
                if (kotlin.math.abs(textSize - dimensione) > 0.01f) setTextSize(TypedValue.COMPLEX_UNIT_PX, dimensione)
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }

        /** La dimensione piu' grande, fra la dichiarata e il minimo, a cui la parola piu' lunga entra in [utile]. */
        private fun dimensioneChePiace(utile: Int): Float {
            val visibile = transformationMethod?.getTransformation(text, this) ?: text
            val parole = visibile.split(SPAZI).filter { it.isNotEmpty() }
            if (parole.isEmpty()) return dimensioneDichiarata
            val penna = TextPaint(paint)
            var dimensione = dimensioneDichiarata
            penna.textSize = dimensione
            while (dimensione > dimensioneMinima && parole.maxOf { penna.measureText(it) } > utile) {
                dimensione = maxOf(dimensioneMinima, dimensione - passo)
                penna.textSize = dimensione
            }
            return dimensione
        }

        private fun sp(valore: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, valore, resources.displayMetrics)

        private companion object {
            const val MINIMO_SP = 14f
            val SPAZI = Regex("\\s+")
        }
    }
