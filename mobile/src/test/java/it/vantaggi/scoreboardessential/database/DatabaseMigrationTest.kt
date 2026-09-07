package it.vantaggi.scoreboardessential.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Validazione delle migrazioni contro gli schemi esportati in `mobile/schemas`.
 *
 * Limite dichiarato: l'export e' stato acceso quando il database era gia' alla
 * versione 11, quindi esiste solo `11.json`. Le migrazioni storiche 6->11 non
 * sono validabili a posteriori senza inventarsi gli schemi delle versioni
 * precedenti, e uno schema inventato verificherebbe una supposizione invece che
 * la verita'. Da qui in avanti ogni nuova versione esporta il proprio schema e
 * la sua migrazione e' verificabile: il test 11->12 sara' l'aggiunta di poche
 * righe qui sotto.
 */
@RunWith(AndroidJUnit4::class)
@org.junit.Ignore(
    "MigrationTestHelper di Room 2.8 non funziona sotto Robolectric: apre il database " +
        "per percorso assoluto mentre il driver e' configurato col nome nudo, e fallisce con " +
        "IllegalArgumentException. E' pensato per i test strumentati. Lo schema E' esportato e " +
        "raggiungibile fra gli asset (verificato in mergeDebugAssets): quello che manca e' solo " +
        "il runner. Riaccendere spostando questa classe in androidTest, oppure quando Room " +
        "supportera' il driver su JVM.",
)
class DatabaseMigrationTest {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    /**
     * Prova che il cablaggio funzioni: lo schema della versione corrente e'
     * esportato, raggiungibile fra gli asset e materializzabile da
     * [MigrationTestHelper]. E' il prerequisito che mancava -- da qui il test della
     * migrazione 11->12 sara' l'aggiunta di poche righe:
     *
     *   helper.createDatabase(TEST_DB, 11).apply { /* righe di prova */ ; close() }
     *   helper.runMigrationsAndValidate(TEST_DB, 12, true, MIGRATION_11_12).close()
     *
     * Se qualcuno cambia una entity senza rigenerare lo schema, questo test resta
     * verde ma il gate di CI (git diff su mobile/schemas) diventa rosso.
     */
    @Test
    @Throws(IOException::class)
    fun exportedSchema_isPresentAndUsable() {
        helper.createDatabase(TEST_DB, AppDatabase.SCHEMA_VERSION).close()
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
