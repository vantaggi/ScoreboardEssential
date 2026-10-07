package it.vantaggi.scoreboardessential.wear

import android.content.Context
import android.content.res.Resources
import android.provider.Settings

/**
 * Il movimento ridotto del sistema (UI Constitution, motion.md): con "Rimuovi animazioni" o la scala
 * delle animazioni a zero la pressione perde la scala e tiene la sola opacita', e la cifra del
 * punteggio non rotola ma cambia subito. Stessa regola del telefono.
 *
 * Android non ha un qualificatore di risorsa per questo, quindi lo decide il tema: ogni schermata
 * chiama [applica] prima di gonfiare i layout, e l'attributo `pressFeedback` risolve allo
 * `StateListAnimator` senza scala (`press_feedback_reduced`).
 */
internal object MovimentoRidotto {
    /** `true` se la scala delle animazioni del sistema e' zero. */
    fun attivo(context: Context): Boolean =
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    /** Sovrappone al tema l'overlay del movimento ridotto, se serve. */
    fun applica(
        context: Context,
        theme: Resources.Theme,
    ) {
        if (attivo(context)) theme.applyStyle(R.style.ThemeOverlay_App_MovimentoRidotto, true)
    }
}
