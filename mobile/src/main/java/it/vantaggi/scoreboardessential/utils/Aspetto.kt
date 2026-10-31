package it.vantaggi.scoreboardessential.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import it.vantaggi.scoreboardessential.R
import java.util.WeakHashMap

/**
 * Come `enableEdgeToEdge()`, ma le icone delle barre di sistema seguono l'aspetto dell'app e non la notte del
 * sistema: il valore di base le sceglie dalla modalita' del telefono, e con l'app chiara su un telefono scuro
 * l'orologio e la batteria sarebbero bianchi sul bianco.
 */
fun ComponentActivity.enableEdgeToEdgeConAspetto() {
    val stato = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { !Aspetto.chiaro(this) }
    val navigazione =
        SystemBarStyle.auto(
            ContextCompat.getColor(this, R.color.scrim_navigazione_chiaro),
            ContextCompat.getColor(this, R.color.scrim_navigazione_scuro),
        ) { !Aspetto.chiaro(this) }
    enableEdgeToEdge(statusBarStyle = stato, navigationBarStyle = navigazione)
}

/** Le tre scelte dell'aspetto; [chiave] e' il valore salvato nelle preferenze. */
enum class ScelteAspetto(
    val chiave: String,
) {
    SCURO("dark"),
    CHIARO("light"),
    COME_IL_TELEFONO("system"),
}

/**
 * L'aspetto dell'app (DESIGN.md, "Tema chiaro"): scuro, chiaro o come il telefono. Il tema e' scuro di base e il
 * chiaro e' un tema AGGIUNTO ([R.style.ThemeOverlay_App_Chiaro]) che cambia solo i colori: lo applica
 * [registra] a ogni activity prima che gonfi i layout, come l'alto contrasto e il movimento ridotto.
 *
 * Chi decide: la scelta dell'utente; di base e' [ScelteAspetto.SCURO], com'e' sempre stata l'app (chi aggiorna non
 * si trova un'altra app). "Come il telefono" segue la modalita' notte del sistema. L'alto contrasto e' solo scuro e
 * vince: con lui acceso il chiaro non si applica (la riga delle impostazioni lo dice).
 *
 * Al cambio, di scelta o di notte/giorno del sistema, ogni activity aperta si ricrea al ritorno in primo piano.
 */
object Aspetto {
    private const val NOME_PREFERENZE = "user_preferences"
    private const val CHIAVE = "appearance"

    private fun preferenze(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(NOME_PREFERENZE, Context.MODE_PRIVATE)

    /** La scelta salvata; senza scelta, scuro. */
    fun scelta(context: Context): ScelteAspetto =
        ScelteAspetto.entries.firstOrNull { it.chiave == preferenze(context).getString(CHIAVE, null) } ?: ScelteAspetto.SCURO

    fun scegli(
        context: Context,
        scelta: ScelteAspetto,
    ) {
        preferenze(context).edit { putString(CHIAVE, scelta.chiave) }
    }

    /** Il nome della scelta, per la riga delle impostazioni e per il foglio. */
    @StringRes
    fun nomeDi(scelta: ScelteAspetto): Int =
        when (scelta) {
            ScelteAspetto.SCURO -> R.string.appearance_dark
            ScelteAspetto.CHIARO -> R.string.appearance_light
            ScelteAspetto.COME_IL_TELEFONO -> R.string.appearance_system
        }

    /** `true` se il tema chiaro e' in vigore per [context]: scelto (o dal sistema) e senza alto contrasto. */
    fun chiaro(context: Context): Boolean {
        if (AltoContrasto.attivo(context)) return false
        return when (scelta(context)) {
            ScelteAspetto.SCURO -> {
                false
            }

            ScelteAspetto.CHIARO -> {
                true
            }

            ScelteAspetto.COME_IL_TELEFONO -> {
                (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    /** Sovrappone al tema l'overlay chiaro, se serve. */
    fun applica(
        context: Context,
        theme: Resources.Theme,
    ) {
        if (chiaro(context)) theme.applyStyle(R.style.ThemeOverlay_App_Chiaro, true)
    }

    /**
     * Lo applica a ogni activity dell'app prima di `onCreate` e ricrea quelle il cui aspetto e' cambiato (scelta o
     * notte/giorno del sistema) quando tornano in primo piano.
     */
    fun registra(application: Application) {
        val applicato = WeakHashMap<Activity, Boolean>()
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityPreCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) {
                    applicato[activity] = chiaro(activity)
                    applica(activity, activity.theme)
                }

                override fun onActivityResumed(activity: Activity) {
                    val avevaApplicato = applicato[activity] ?: return
                    if (avevaApplicato != chiaro(activity)) activity.recreate()
                }

                override fun onActivityCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) = Unit

                override fun onActivityStarted(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) = Unit

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    outState: Bundle,
                ) = Unit

                override fun onActivityDestroyed(activity: Activity) {
                    applicato.remove(activity)
                }
            },
        )
    }
}
