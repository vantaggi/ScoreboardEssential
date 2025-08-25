package com.example.scoreboardessential.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.scoreboardessential.database.Match
import com.example.scoreboardessential.database.Player
import com.example.scoreboardessential.database.MatchPlayerCrossRef
import com.example.scoreboardessential.database.Team
import com.example.scoreboardessential.database.Role
import com.example.scoreboardessential.database.PlayerRoleCrossRef

@Database(
    entities = [Match::class, Player::class, MatchPlayerCrossRef::class,
                Team::class, Role::class, PlayerRoleCrossRef::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun matchDao(): MatchDao
    abstract fun playerDao(): PlayerDao
    abstract fun teamDao(): TeamDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Crea tabella roles
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `roles` (
                        `roleId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `category` TEXT NOT NULL
                    )
                """)

                // 2. Crea tabella di associazione
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `player_role_cross_ref` (
                        `playerId` INTEGER NOT NULL,
                        `roleId` INTEGER NOT NULL,
                        PRIMARY KEY(`playerId`, `roleId`),
                        FOREIGN KEY(`playerId`) REFERENCES `players`(`playerId`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`roleId`) REFERENCES `roles`(`roleId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)

                // 3. Crea nuova tabella players senza campo roles
                database.execSQL("""
                    CREATE TABLE `players_new` (
                        `playerId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `playerName` TEXT NOT NULL,
                        `appearances` INTEGER NOT NULL,
                        `goals` INTEGER NOT NULL
                    )
                """)

                // 4. Copia dati dalla vecchia tabella
                database.execSQL("""
                    INSERT INTO `players_new` (`playerId`, `playerName`, `appearances`, `goals`)
                    SELECT `playerId`, `playerName`, `appearances`, `goals` FROM `players`
                """)

                // 5. Sostituisci la tabella
                database.execSQL("DROP TABLE `players`")
                database.execSQL("ALTER TABLE `players_new` RENAME TO `players`")

                // 6. Popola i ruoli predefiniti
                populateRoles(database)
            }

            private fun populateRoles(database: SupportSQLiteDatabase) {
                val roles = listOf(
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
                    "Centravanti" to "ATTACCO"
                )

                roles.forEach { (name, category) ->
                    database.execSQL("INSERT INTO `roles` (name, category) VALUES ('$name', '$category')")
                }
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE matches ADD COLUMN isActive INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "match_database"
                )
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
