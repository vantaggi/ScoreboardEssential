package it.vantaggi.scoreboardessential

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.repository.ColorRepository
import it.vantaggi.scoreboardessential.repository.MatchRepository
import it.vantaggi.scoreboardessential.repository.MatchSettingsRepository
import it.vantaggi.scoreboardessential.repository.PlayerRepository
import it.vantaggi.scoreboardessential.repository.UserPreferencesRepository

class ScoreboardEssentialApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val colorRepository by lazy { ColorRepository(this) }
    val playerRepository by lazy { PlayerRepository(database.playerDao()) }
    val matchRepository by lazy { MatchRepository(database.matchDao(), this, colorRepository) }
    val userPreferencesRepository by lazy { UserPreferencesRepository(this) }
    val matchSettingsRepository by lazy { MatchSettingsRepository(this) }

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(it.vantaggi.scoreboardessential.utils.LocaleHelper.onAttach(base))
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    companion object {
        const val CHANNEL_ID = "scoreboard_channel"
        const val CHANNEL_ID_ALARM = "scoreboard_alarm_channel"
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            // Service Channel (Low Importance - Silent)
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel =
                NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                    setShowBadge(false)
                }
            notificationManager.createNotificationChannel(channel)

            // Alarm Channel (High Importance - Sound & Pop-up)
            val alarmName = "Timer Alarms" // You might want to extract this to strings.xml later if strict localization is needed
            val alarmDescription = "Notifications for expired timers"
            val alarmImportance = NotificationManager.IMPORTANCE_HIGH
            val alarmChannel =
                NotificationChannel(CHANNEL_ID_ALARM, alarmName, alarmImportance).apply {
                    description = alarmDescription
                    enableVibration(true)
                    enableLights(true)
                }
            notificationManager.createNotificationChannel(alarmChannel)
        }
    }
}
