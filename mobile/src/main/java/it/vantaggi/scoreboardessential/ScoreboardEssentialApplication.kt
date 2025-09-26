package it.vantaggi.scoreboardessential

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.repository.ColorRepository
import it.vantaggi.scoreboardessential.repository.MatchRepository
import it.vantaggi.scoreboardessential.repository.PlayerRepository
import it.vantaggi.scoreboardessential.repository.UserPreferencesRepository

class ScoreboardEssentialApplication : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }
    val colorRepository by lazy { ColorRepository(this) }
    val playerRepository by lazy { PlayerRepository(database.playerDao()) }
    val matchRepository by lazy { MatchRepository(database.matchDao(), this, colorRepository) }
    val userPreferencesRepository by lazy { UserPreferencesRepository(this) }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "scoreboard_channel"
    }
}
