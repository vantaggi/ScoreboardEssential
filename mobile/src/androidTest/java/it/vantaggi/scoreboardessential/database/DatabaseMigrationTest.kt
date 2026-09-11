package it.vantaggi.scoreboardessential.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Validazione delle migrazioni contro gli schemi esportati in `mobile/schemas`.
 *
 * **Perche' e' un test strumentato e non un test unitario.** Ci e' stato per un po', ma
 * `MigrationTestHelper` di Room 2.8 sotto Robolectric apre il database per percorso assoluto
 * mentre il driver e' configurato col nome nudo, e fallisce con `IllegalArgumentException`. La
 * classe restava li' con un `@Ignore` e una spiegazione. Un test che non gira non e' un test:
 * era uno di quelli che rendevano verde una CI senza verificare niente. Qui gira davvero, su un
 * dispositivo o un emulatore.
 *
 * **Cosa verifica, oltre allo schema.** Una migrazione puo' produrre uno schema formalmente
 * giusto e dati sbagliati. Questi test inseriscono righe alla versione di partenza e le
 * rileggono dopo, perche' il difetto che e' costato di piu' in questo progetto era esattamente
 * di quel tipo: i valori di default di Kotlin NON sono default SQL, e lo schema atteso da Room
 * non riportava le clausole `DEFAULT` che la migrazione invece creava. Il risultato sarebbe
 * stato un crash all'avvio, a ogni avvio, su ogni telefono gia' installato.
 *
 * **Limite dichiarato:** l'export degli schemi e' stato acceso quando il database era gia' alla
 * versione 11, quindi la piu' vecchia validabile e' quella. Le migrazioni 6->11 non sono
 * verificabili a posteriori senza inventarsi gli schemi precedenti, e uno schema inventato
 * verificherebbe una supposizione invece che la verita'.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    /** Una partita e un giocatore come li scriveva la versione 11, prima del multi-sport. */
    private fun righeDellaVersione11(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL(
            "INSERT INTO matches (matchId, team1Id, team2Id, team1Score, team2Score, timestamp, isActive) " +
                "VALUES (1, 10, 20, 3, 1, 1700000000000, 0)",
        )
        db.execSQL(
            "INSERT INTO players (playerId, playerName, appearances, goals) VALUES (7, 'Anna', 5, 2)",
        )
    }

    @Test
    @Throws(IOException::class)
    fun migrazione11a12_aggiunge_sport_e_registro_con_i_default_giusti() {
        helper.createDatabase(TEST_DB, 11).apply {
            righeDellaVersione11(this)
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 12, true, AppDatabase.MIGRATION_11_12)

        // Il backfill E' la clausola DEFAULT: una partita salvata prima del multi-sport diventa
        // calcio con cronologia vuota, cioe' "solo punteggio finale". Se il DEFAULT mancasse,
        // qui si leggerebbe null su una colonna NOT NULL -- o la migrazione sarebbe gia' fallita.
        db.query("SELECT sportId, eventLog, team1Score FROM matches WHERE matchId = 1").use { c ->
            assertTrue("la partita della v11 deve sopravvivere alla migrazione", c.moveToFirst())
            assertEquals("football", c.getString(0))
            assertEquals("", c.getString(1))
            assertEquals(3, c.getInt(2))
        }
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun migrazione12a13_collega_i_giocatori_lasciandoli_scollegati() {
        helper.createDatabase(TEST_DB, 11).apply {
            righeDellaVersione11(this)
            close()
        }
        helper.runMigrationsAndValidate(TEST_DB, 12, true, AppDatabase.MIGRATION_11_12).close()

        val db = helper.runMigrationsAndValidate(TEST_DB, 13, true, AppDatabase.MIGRATION_12_13)

        // La colonna e' nullable SENZA default: null significa "non ancora collegato a Padel
        // Elite", che e' lo stato giusto per ogni giocatore che esisteva prima.
        db.query("SELECT playerName, padelPlayerId FROM players WHERE playerId = 7").use { c ->
            assertTrue("il giocatore della v11 deve sopravvivere", c.moveToFirst())
            assertEquals("Anna", c.getString(0))
            assertTrue("chi esisteva prima non e' collegato a nessuno", c.isNull(1))
        }
        db.close()
    }

    @Test
    @Throws(IOException::class)
    fun laCatena11a13_arriva_in_fondo_in_un_colpo_solo() {
        // E' il percorso che fa davvero un telefono rimasto indietro di due versioni: Room
        // incatena le migrazioni da solo, e l'ordine in cui le applica non e' garantito da
        // nessun test che le provi una per una.
        helper.createDatabase(TEST_DB, 11).apply {
            righeDellaVersione11(this)
            close()
        }

        val db =
            helper.runMigrationsAndValidate(
                TEST_DB,
                AppDatabase.SCHEMA_VERSION,
                true,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
            )

        db.query("SELECT sportId FROM matches WHERE matchId = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("football", c.getString(0))
        }
        // L'indice deve chiamarsi ESATTAMENTE come quello che Room genera da @Index: un nome
        // diverso fa fallire la validazione all'avvio, a ogni avvio, senza fix remoto possibile.
        val indice = "SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_matches_sportId_timestamp'"
        db.query(indice).use { c ->
            assertTrue("l'indice del multi-sport deve esistere col nome che Room si aspetta", c.moveToFirst())
        }
        db.close()
    }

    companion object {
        private const val TEST_DB = "migration-test"
    }
}
