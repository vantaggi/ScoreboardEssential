package it.vantaggi.scoreboardessential.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.RotateDrawable
import android.util.AttributeSet
import android.view.animation.LinearInterpolator
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.button.MaterialButton
import it.vantaggi.scoreboardessential.R

/**
 * Il bottone che lavora (UI Constitution, ProgressButton): resta lo stesso bottone, della stessa
 * larghezza, e prende l'icona `progress_activity` che gira una volta ogni 0,8s. Mentre lavora non
 * accetta altri tocchi e dice che cosa fa con [loadingLabel]. Con il movimento ridotto del sistema
 * (ANIMATOR_DURATION_SCALE a 0) l'animatore non gira: resta l'icona ferma e la parola.
 *
 * La larghezza si fissa sul valore che il bottone ha al momento di partire: l'etichetta di
 * "lavoro in corso" non lo allarga ne' lo stringe.
 */
class ProgressButton
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle,
    ) : MaterialButton(context, attrs, defStyleAttr) {
        private var testoNormale: CharSequence? = null
        private var iconaNormale: Drawable? = null
        private var rotazione: ObjectAnimator? = null

        /** Il testo mentre lavora; null lascia l'etichetta di prima. */
        var loadingLabel: CharSequence? = null

        var isLoading: Boolean = false
            private set

        fun setLoading(loading: Boolean) {
            if (loading == isLoading) return
            isLoading = loading
            if (loading) {
                testoNormale = text
                iconaNormale = icon
                // Tiene la larghezza: dopo il cambio di testo la vista non puo' stare sotto questo valore.
                if (width > 0) minWidth = width
                loadingLabel?.let { text = it }
                val ruota = RotateDrawable()
                ruota.drawable = AppCompatResources.getDrawable(context, R.drawable.ic_progress_activity)
                ruota.fromDegrees = 0f
                ruota.toDegrees = 360f
                icon = ruota
                iconGravity = ICON_GRAVITY_TEXT_START
                rotazione =
                    ObjectAnimator.ofInt(ruota, "level", 0, LIVELLO_PIENO).apply {
                        duration = GIRO_MS
                        repeatCount = ObjectAnimator.INFINITE
                        interpolator = LinearInterpolator()
                        start()
                    }
                isClickable = false
            } else {
                rotazione?.cancel()
                rotazione = null
                text = testoNormale
                icon = iconaNormale
                isClickable = true
            }
        }

        override fun onDetachedFromWindow() {
            rotazione?.cancel()
            super.onDetachedFromWindow()
        }

        private companion object {
            const val GIRO_MS = 800L
            const val LIVELLO_PIENO = 10_000
        }
    }
