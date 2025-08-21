package com.example.scoreboardessential.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Match::class, Player::class, MatchPlayerCrossRef::class, Team::class], version = 6, exportSchema = false)
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
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
