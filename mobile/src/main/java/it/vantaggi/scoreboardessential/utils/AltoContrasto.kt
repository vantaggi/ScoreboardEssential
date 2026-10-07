package it.vantaggi.scoreboardessential.utils

import android.app.Activity
import android.app.Application
import android.app.UiModeManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import androidx.core.content.edit
import it.vantaggi.scoreboardessential.R
import java.util.WeakHashMap

/**
 * Il tema ad alto contrasto (UI Constitution, G-10): un tema AGGIUNTO, scuro, che cambia solo i colori
 * ([R.style.ThemeOverlay_App_AltoContrasto]: testo a 7:1, bordi e icone a 3:1) e non tocca forme, pesi
 * e movimento.
 *
 * Chi decide: la scelta dell'utente, se c'e' (l'interruttore delle impostazioni salva `true` o `false`);
 * altrimenti il contrasto del sistema, da Android 14 (`UiModeManager.getContrast()`): sotto, solo
 * l'interruttore, perche' minSdk e' 30. Il sistema ha tre livelli (0 standard, 0,5 medio, 1 alto): il
 * tema ha un livello solo, quindi segue il sistema dal medio in su ([SOGLIA_DI_SISTEMA]).
 *
 * Come il movimento ridotto, lo decide il tema: ogni activity riceve l'overlay prima di gonfiare i
 * layout. Al cambio (interruttore o contrasto di sistema) l'activity si ricrea.
 */
object AltoContrasto {
    /** Dal contrasto di sistema "medio" (0,5) in su l'app passa all'alto contrasto, se l'utente non ha scelto. */
    const val SOGLIA_DI_SISTEMA = 0.5f

    private const val NOME_PREFERENZE = "user_preferences"
    private const val CHIAVE = "high_contrast"

    /** Il contrasto di sistema, 0 senza supporto. I test lo sostituiscono per simulare `getContrast()`. */
    @Volatile
    var contrastoDiSistema: (Context) -> Float = ::leggiContrastoDiSistema

    private fun leggiContrastoDiSistema(context: Context): Float {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return 0f
        val gestore = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        return gestore?.contrast ?: 0f
    }

    private fun preferenze(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(NOME_PREFERENZE, Context.MODE_PRIVATE)

    /** La scelta dell'utente: `null` se non ha mai toccato l'interruttore (allora vale il sistema). */
    fun sceltaDellUtente(context: Context): Boolean? =
        preferenze(context).let { if (it.contains(CHIAVE)) it.getBoolean(CHIAVE, false) else null }

    /** Salva la scelta dell'interruttore, sia `true` sia `false`: spegnerlo vince anche sul sistema. */
    fun scegli(
        context: Context,
        attivo: Boolean,
    ) {
        preferenze(context).edit { putBoolean(CHIAVE, attivo) }
    }

    /** `true` se l'alto contrasto e' in vigore: la scelta dell'utente, altrimenti il sistema. */
    fun attivo(context: Context): Boolean = sceltaDellUtente(context) ?: (contrastoDiSistema(context) >= SOGLIA_DI_SISTEMA)

    /** Sovrappone al tema l'overlay dell'alto contrasto, se serve. */
    fun applica(
        context: Context,
        theme: Resources.Theme,
    ) {
        if (attivo(context)) theme.applyStyle(R.style.ThemeOverlay_App_AltoContrasto, true)
    }

    /**
     * Lo applica a ogni activity dell'app prima di `onCreate` e, da Android 14, ricrea quelle aperte
     * quando cambia il contrasto di sistema e l'esito cambia (con una scelta dell'utente non cambia).
     */
    fun registra(application: Application) {
        val applicato = WeakHashMap<Activity, Boolean>()
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityPreCreated(
                    activity: Activity,
                    savedInstanceState: Bundle?,
                ) {
                    applicato[activity] = attivo(activity)
                    applica(activity, activity.theme)
                }

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

                override fun onActivityDestroyed(activity: Activity) {
                    applicato.remove(activity)
                }
            },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val gestore = application.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
            gestore?.addContrastChangeListener(application.mainExecutor) {
                // Solo le activity il cui aspetto cambia davvero: con una scelta dell'utente nessuna.
                applicato.entries
                    .filter { (activity, avevaApplicato) -> avevaApplicato != attivo(activity) }
                    .forEach { (activity, _) -> activity.recreate() }
            }
        }
    }
}
