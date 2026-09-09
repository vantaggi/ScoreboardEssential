package it.vantaggi.scoreboardessential.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Match::class, Player::class, MatchPlayerCrossRef::class,
        Team::class, Role::class, PlayerRoleCrossRef::class,
    ],
    version = 13,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun matchDao(): MatchDao

    abstract fun playerDao(): PlayerDao

    abstract fun teamDao(): TeamDao

    companion object {
        /** Versione dello schema. Tenuta qui cosi' che i test non la ripetano a mano. */
        const val SCHEMA_VERSION = 13

        @Volatile
        private var instance: AppDatabase? = null

        private val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // 1. Crea tabella roles
                    database.execSQL(
                        """
                    CREATE TABLE IF NOT EXISTS `roles` (
                        `roleId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `category` TEXT NOT NULL
                    )
                """,
                    )

                    // 2. Crea tabella di associazione
                    database.execSQL(
                        """
                    CREATE TABLE IF NOT EXISTS `player_role_cross_ref` (
                        `playerId` INTEGER NOT NULL,
                        `roleId` INTEGER NOT NULL,
                        PRIMARY KEY(`playerId`, `roleId`),
                        FOREIGN KEY(`playerId`) REFERENCES `players`(`playerId`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`roleId`) REFERENCES `roles`(`roleId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """,
                    )

                    // 3. Crea nuova tabella players senza campo roles
                    database.execSQL(
                        """
                    CREATE TABLE `players_new` (
                        `playerId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `playerName` TEXT NOT NULL,
                        `appearances` INTEGER NOT NULL,
                        `goals` INTEGER NOT NULL
                    )
                """,
                    )

                    // 4. Copia dati dalla vecchia tabella
                    database.execSQL(
                        """
                    INSERT INTO `players_new` (`playerId`, `playerName`, `appearances`, `goals`)
                    SELECT `playerId`, `playerName`, `appearances`, `goals` FROM `players`
                """,
                    )

                    // 5. Sostituisci la tabella
                    database.execSQL("DROP TABLE `players`")
                    database.execSQL("ALTER TABLE `players_new` RENAME TO `players`")

                    // 6. Popola i ruoli predefiniti
                    populateRoles(database)
                }
            }

        private val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    database.execSQL("ALTER TABLE matches ADD COLUMN isActive INTEGER NOT NULL DEFAULT 0")
                }
            }

        internal val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override fun migrate(database: SupportSQLiteDatabase) {
// Crea indice per playerId (migliora query quando cerchiamo ruoli per player)
                    database.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_player_role_cross_ref_playerId` " +
                            "ON `player_role_cross_ref` (`playerId`)",
                    )

// L'indice su roleId esiste già dalla migrazione precedente, ma verifichiamo
                    database.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_player_role_cross_ref_roleId` " +
                            "ON `player_role_cross_ref` (`roleId`)",
                    )

// Crea indice composito per ottimizzare i join
                    database.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_player_role_cross_ref_playerId_roleId` " +
                            "ON `player_role_cross_ref` (`playerId`, `roleId`)",
                    )
                }
            }

        internal val MIGRATION_9_10 =
            object : Migration(9, 10) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // Crea indice esplicito per matchId in MatchPlayerCrossRef
                    database.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_MatchPlayerCrossRef_matchId` " +
                            "ON `MatchPlayerCrossRef` (`matchId`)",
                    )
                }
            }

        internal val MIGRATION_10_11 =
            object : Migration(10, 11) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // Track which team a player was on in a match, enabling win-rate stats.
                    database.execSQL(
                        "ALTER TABLE `MatchPlayerCrossRef` ADD COLUMN `teamNumber` INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }

        internal val MIGRATION_11_12 =
            object : Migration(11, 12) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // Puramente ADDITIVA: due ALTER TABLE e un indice. Nessuna riga riscritta,
                    // nessuna tabella ricostruita, nessuna UPDATE -- quindi nemmeno il rischio che
                    // PRAGMA foreign_key_check faccia abortire la migrazione a meta'.
                    //
                    // Il backfill E' la clausola DEFAULT: ogni partita gia' salvata diventa
                    // 'football' con cronologia vuota, che significa "solo punteggio finale".
                    // E' l'opposto esatto di MIGRATION_6_7, che creava players_new, copiava
                    // quattro colonne e poi faceva DROP TABLE players perdendo per strada le
                    // assegnazioni di ruolo precedenti alla v7.
                    database.execSQL(
                        "ALTER TABLE `matches` ADD COLUMN `sportId` TEXT NOT NULL DEFAULT 'football'",
                    )
                    database.execSQL(
                        "ALTER TABLE `matches` ADD COLUMN `eventLog` TEXT NOT NULL DEFAULT ''",
                    )
                    // Il nome deve combaciare CARATTERE PER CARATTERE con quello che Room genera
                    // da @Index(value = ["sportId", "timestamp"]) su `matches`, altrimenti la
                    // validazione fallisce all'avvio con "Migration didn't properly handle" -- a
                    // ogni avvio, per sempre, senza fix remoto possibile.
                    database.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_matches_sportId_timestamp` " +
                            "ON `matches` (`sportId`, `timestamp`)",
                    )
                }
            }

        internal val MIGRATION_12_13 =
            object : Migration(12, 13) {
                override fun migrate(database: SupportSQLiteDatabase) {
                    // Additiva e nullable: nessun default, quindi lo schema atteso da Room
                    // combacia senza @ColumnInfo. Null significa "non ancora collegato", che e'
                    // lo stato giusto per ogni giocatore esistente.
                    database.execSQL("ALTER TABLE `players` ADD COLUMN `padelPlayerId` INTEGER")
                }
            }

        fun getDatabase(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                val instance =
                    Room
                        .databaseBuilder(
                            context.applicationContext,
                            AppDatabase::class.java,
                            "match_database",
                        ).addCallback(
                            object : Callback() {
                                override fun onOpen(db: SupportSQLiteDatabase) {
                                    super.onOpen(db)
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val cursor = db.query("SELECT COUNT(*) FROM roles")
                                        val count = if (cursor.moveToFirst()) cursor.getInt(0) else 0
                                        cursor.close()
                                        if (count == 0) {
                                            populateRoles(db)
                                        }
                                    }
                                }
                            },
                        ).addMigrations(
                            MIGRATION_6_7,
                            MIGRATION_7_8,
                            MIGRATION_8_9,
                            MIGRATION_9_10,
                            MIGRATION_10_11,
                            MIGRATION_11_12,
                            MIGRATION_12_13,
                        )
                        // Non esiste alcun percorso di migrazione dalle versioni 1-5: un
                        // dispositivo fermo li' crasherebbe a ogni avvio, per sempre. La
                        // ricostruzione e' limitata a QUELLE versioni e non generalizzata:
                        // un fallbackToDestructiveMigration() globale trasformerebbe
                        // "migrazione sbagliata = crash rumoroso in test" in "migrazione
                        // sbagliata = cronologia utente cancellata in silenzio".
                        .fallbackToDestructiveMigrationFrom(1, 2, 3, 4, 5)
                        .fallbackToDestructiveMigrationOnDowngrade()
                        .build()
                this.instance = instance
                instance
            }

        fun populateRoles(database: SupportSQLiteDatabase) {
            val roles =
                listOf(
                    // PORTA
                    "Portiere" to "PORTA",
                    // DIFESA
                    "Difensore Centrale" to "DIFESA",
                    "Terzino Sinistro" to "DIFESA",
                    "Terzino Destro" to "DIFESA",
                    "Libero" to "DIFESA",
                    // CENTROCAMPO
                    "Mediano" to "CENTROCAMPO",
                    "Centrocampista Centrale" to "CENTROCAMPO",
                    "Trequartista" to "CENTROCAMPO",
                    "Esterno Sinistro" to "CENTROCAMPO",
                    "Esterno Destro" to "CENTROCAMPO",
                    // ATTACCO
                    "Ala Sinistra" to "ATTACCO",
                    "Ala Destra" to "ATTACCO",
                    "Seconda Punta" to "ATTACCO",
                    "Centravanti" to "ATTACCO",
                )

            roles.forEach { (name, category) ->
                database.execSQL(
                    "INSERT INTO `roles` (name, category) VALUES (?, ?)",
                    arrayOf(name, category),
                )
            }
        }
    }
}
