package com.example.scoreboardessential.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Match::class, Player::class, MatchPlayerCrossRef::class, Team::class, Role::class, PlayerRoleCrossRef::class], version = 7, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun matchDao(): MatchDao
    abstract fun playerDao(): PlayerDao
    abstract fun teamDao(): TeamDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "match_database"
                )
                .addMigrations(MIGRATION_6_7)
                .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create the new 'roles' table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `roles` (
                        `roleId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `category` TEXT NOT NULL
                    )
                """)

                // Create the new 'player_role_cross_ref' table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `player_role_cross_ref` (
                        `playerId` INTEGER NOT NULL,
                        `roleId` INTEGER NOT NULL,
                        PRIMARY KEY(`playerId`, `roleId`),
                        FOREIGN KEY(`playerId`) REFERENCES `players`(`playerId`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`roleId`) REFERENCES `roles`(`roleId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """)

                // Create a temporary table to hold the new player data
                database.execSQL("""
                    CREATE TABLE `players_new` (
                        `playerId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `playerName` TEXT NOT NULL,
                        `appearances` INTEGER NOT NULL,
                        `goals` INTEGER NOT NULL
                    )
                """)

                // Copy data from the old 'players' table to the new one, excluding the 'roles' column
                database.execSQL("""
                    INSERT INTO `players_new` (`playerId`, `playerName`, `appearances`, `goals`)
                    SELECT `playerId`, `playerName`, `appearances`, `goals` FROM `players`
                """)

                // Drop the old 'players' table
                database.execSQL("DROP TABLE `players`")

                // Rename the new table to 'players'
                database.execSQL("ALTER TABLE `players_new` RENAME TO `players`")

                // Populate the 'roles' table with predefined values
                populateRoles(database)
            }

            private fun populateRoles(database: SupportSQLiteDatabase) {
                val roles = listOf(
                    // PORTA
                    Role(name = "Portiere", category = "PORTA"),
                    // DIFESA
                    Role(name = "Difensore Centrale", category = "DIFESA"),
                    Role(name = "Terzino Sinistro", category = "DIFESA"),
                    Role(name = "Terzino Destro", category = "DIFESA"),
                    Role(name = "Libero", category = "DIFESA"),
                    // CENTROCAMPO
                    Role(name = "Mediano", category = "CENTROCAMPO"),
                    Role(name = "Centrocampista Centrale", category = "CENTROCAMPO"),
                    Role(name = "Trequartista", category = "CENTROCAMPO"),
                    Role(name = "Esterno Sinistro", category = "CENTROCAMPO"),
                    Role(name = "Esterno Destro", category = "CENTROCAMPO"),
                    // ATTACCO
                    Role(name = "Ala Sinistra", category = "ATTACCO"),
                    Role(name = "Ala Destra", category = "ATTACCO"),
                    Role(name = "Seconda Punta", category = "ATTACCO"),
                    Role(name = "Centravanti", category = "ATTACCO")
                )
                roles.forEach { role ->
                    database.execSQL("INSERT INTO `roles` (name, category) VALUES ('${role.name}', '${role.category}')")
                }
            }
        }
    }
}
