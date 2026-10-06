package it.vantaggi.scoreboardessential.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.provider.Settings
import it.vantaggi.scoreboardessential.R

/**
 * Il movimento ridotto del sistema (UI Constitution, motion.md): con "Rimuovi animazioni" o la scala
 * delle animazioni a zero, la pressione perde la scala e tiene la sola opacita'.
 *
 * Android non ha un qualificatore di risorsa per questo, quindi lo decide il tema: ogni activity
 * riceve [R.style.ThemeOverlay_App_MovimentoRidotto] prima di gonfiare i layout, e l'attributo
 * `pressFeedback` risolve allo `StateListAnimator` senza scala (`press_feedback_reduced`).
 */
object MovimentoRidotto {
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

    /** Lo applica a ogni activity dell'app prima di `onCreate`. */
    fun registra(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityPreCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) = applica(activity, activity.theme)

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}
