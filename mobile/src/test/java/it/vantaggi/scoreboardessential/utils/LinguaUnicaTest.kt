package it.vantaggi.scoreboardessential.utils

import android.content.Context
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.room.Room
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.ui.MatchSettingsActivity
import it.vantaggi.scoreboardessential.ui.statistics.StatisticsActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * La lingua scelta nell'app deve valere per tutta l'app, e deve arrivare anche da Play.
 *
 * Prima la applicavano solo MainActivity e le impostazioni, sovrascrivendo attachBaseContext:
 * Statistiche, Storico, Giocatori e l'onboarding seguivano il sistema. E con gli split per
 * lingua attivi, un telefono in inglese non riceveva nemmeno values-it.
 */
@RunWith(RobolectricTestRunner::class)
class LinguaUnicaTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        // Le impostazioni leggono AppDatabase.getDatabase: si inietta un database in memoria,
        // come in AddEditPlayerActivityTest.
        database =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        setDatabaseInstance(database)
    }

    @After
    fun tearDown() {
        // AppCompat tiene la lingua scelta in un campo statico: non deve passare al test dopo.
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        database.close()
        setDatabaseInstance(null)
    }

    private fun setDatabaseInstance(instance: AppDatabase?) {
        val field = AppDatabase::class.java.getDeclaredField("instance")
        field.isAccessible = true
        field.set(null, instance)
    }

    private fun scegliItalianoNelleImpostazioni() {
        val impostazioni =
            Robolectric
                .buildActivity(MatchSettingsActivity::class.java)
                .setup()
                .get()
        val selettore = impostazioni.findViewById<AutoCompleteTextView>(R.id.languageAutoComplete)
        // La seconda voce del menu e' "Italiano".
        selettore.onItemClickListener.onItemClick(null, selettore, 1, 1L)
    }

    @Test
    fun `scegliere Italiano nelle impostazioni passa dal meccanismo unico`() {
        scegliItalianoNelleImpostazioni()

        assertEquals("it", AppCompatDelegate.getApplicationLocales().toLanguageTags())
    }

    /**
     * Sotto API 33 e' AppCompat ad applicare la lingua a ogni Activity che si crea: e' il caso in
     * cui Robolectric esegue davvero l'applicazione, invece di delegarla al sistema. Sotto
     * Robolectric AppCompat la applica in onCreate e non in attachBaseContext, per questo
     * l'Activity va creata e non solo costruita.
     */
    @Test
    @Config(sdk = [32])
    fun `la lingua scelta nelle impostazioni vale anche per le altre schermate`() {
        scegliItalianoNelleImpostazioni()

        val statistiche = Robolectric.buildActivity(StatisticsActivity::class.java).setup().get()

        assertEquals(
            "Statistiche deve mostrare la lingua scelta, non quella del sistema",
            "Lingua",
            statistiche.getString(R.string.language),
        )
    }

    @Test
    fun `la scelta fatta con la versione precedente non si perde all'aggiornamento`() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("match_settings_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("app_language", "it").commit()
        val host =
            Robolectric
                .buildActivity(MatchSettingsActivity::class.java)
                .setup()
                .get()

        LocaleHelper.migrateLegacyChoice(host)

        assertEquals("it", AppCompatDelegate.getApplicationLocales().toLanguageTags())
        assertFalse("la vecchia chiave va letta una volta sola", prefs.contains("app_language"))
    }

    @Test
    fun `gli split per lingua sono spenti nel bundle`() {
        val build = File("build.gradle")
        assertTrue("build.gradle non trovato in ${build.absolutePath}", build.exists())

        val splitSpento = Regex("""bundle\s*\{\s*language\s*\{[^}]*enableSplit\s*=\s*false""")

        assertTrue(
            "con gli split Play installa solo la lingua del telefono e la scelta nell'app non ha effetto",
            splitSpento.containsMatchIn(build.readText()),
        )
    }
}
