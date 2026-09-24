package it.vantaggi.scoreboardessential.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.os.LocaleListCompat

/**
 * La lingua dell'app passa da UNA sola via: AppCompatDelegate.setApplicationLocales.
 *
 * Prima ogni Activity doveva ricordarsi di sovrascrivere attachBaseContext, e lo facevano solo
 * MainActivity e le impostazioni: Statistiche, Storico, Giocatori e l'onboarding seguivano il
 * sistema, e su un telefono italiano la prima installazione mostrava l'onboarding in italiano e
 * il tabellone in inglese (il vecchio default forzava "en"). Da API 33 la scelta la tiene il
 * sistema e vale per tutto il processo, servizi compresi; sotto API 33 la tiene AppCompat
 * (autoStoreLocales nel manifest) e la applica a ogni AppCompatActivity.
 *
 * Nessuna scelta salvata vuol dire "segui il sistema", non piu' "inglese".
 */
object LocaleHelper {
    // Dove la vecchia versione salvava la scelta. Letta una volta sola, per non perderla.
    private const val LEGACY_PREFS = "match_settings_prefs"
    private const val LEGACY_KEY = "app_language"

    /** Va chiamata dal thread principale: sotto API 33 ricrea subito le Activity aperte. */
    fun apply(language: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language))
    }

    /** La lingua che [context] sta davvero mostrando, scelta o ereditata dal sistema. */
    fun currentLanguage(context: Context): String =
        context.resources.configuration.locales[0]
            .language

    /**
     * Porta nel meccanismo unico la lingua scelta con la versione precedente, poi la dimentica.
     *
     * Senza questo passaggio chi aveva scelto l'italiano su un telefono in inglese, al primo avvio
     * dopo l'aggiornamento, se lo ritroverebbe in inglese. Va chiamata da un'Activity gia' creata:
     * da API 33 AppCompat trova il servizio di sistema solo attraverso un'Activity viva.
     */
    fun migrateLegacyChoice(context: Context) {
        val prefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val legacy = prefs.getString(LEGACY_KEY, null) ?: return
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            apply(legacy)
        }
        prefs.edit { remove(LEGACY_KEY) }
    }
}
