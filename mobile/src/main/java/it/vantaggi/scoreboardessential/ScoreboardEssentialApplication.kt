package it.vantaggi.scoreboardessential

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.padelelite.DirettaAvvisoNotifica
import it.vantaggi.scoreboardessential.padelelite.DirettaChiusuraWorker
import it.vantaggi.scoreboardessential.padelelite.DirettaStore
import it.vantaggi.scoreboardessential.padelelite.PadelEliteConfig
import it.vantaggi.scoreboardessential.padelelite.PadelEliteServices
import it.vantaggi.scoreboardessential.repository.ColorRepository
import it.vantaggi.scoreboardessential.repository.MatchRepository
import it.vantaggi.scoreboardessential.repository.MatchSettingsRepository
import it.vantaggi.scoreboardessential.repository.PlayerRepository
import it.vantaggi.scoreboardessential.repository.UserPreferencesRepository
import it.vantaggi.scoreboardessential.utils.AltoContrasto
import it.vantaggi.scoreboardessential.utils.Aspetto
import it.vantaggi.scoreboardessential.utils.MovimentoRidotto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

open class ScoreboardEssentialApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val colorRepository by lazy { ColorRepository(this) }
    val playerRepository by lazy { PlayerRepository(database.playerDao(), database.padelEliteLinkDao()) }
    val matchRepository by lazy { MatchRepository(database.matchDao(), this, colorRepository, database.padelEliteLinkDao()) }
    val userPreferencesRepository by lazy { UserPreferencesRepository(this) }
    val matchSettingsRepository by lazy { MatchSettingsRepository(this, colorRepository) }

    /**
     * Dove gira il lavoro dell'avvio del processo ([sistemaLaDirettaDiPrima]). Vive quanto il processo; i test lo
     * sostituiscono con uno che chiudono a fine test (debito 20 di D-3), perche' nessuna coroutine sopravviva al test.
     */
    internal open val scopeDiAvvio: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * L'invio a Padel Elite. Spento (nessun comando, nessuna rete) senza la configurazione di local.properties. Aperto: i
     * test strumentati lo puntano a un indirizzo muto (debito 12 di D-3).
     */
    open val padelElite: PadelEliteServices by lazy { PadelEliteServices(this, PadelEliteConfig.fromBuildConfig(), matchRepository) }

    override fun onCreate() {
        // Il contesto dell'applicazione non riceve il tema del manifest (solo le activity): i token elite_* seguono il
        // tema (G-10), e senza questa riga un getColor() da servizio, ricevitore o test uscirebbe magenta.
        setTheme(R.style.Theme_ScoreboardEssential)
        super.onCreate()
        MovimentoRidotto.registra(this)
        AltoContrasto.registra(this)
        Aspetto.registra(this)
        createNotificationChannel()
        sistemaLaDirettaDiPrima()
    }

    companion object {
        const val CHANNEL_ID = "scoreboard_channel"
        const val CHANNEL_ID_ALARM = "scoreboard_alarm_channel"
        const val CHANNEL_ID_TIMER = "scoreboard_timer_channel"

        /** La notifica fissa della diretta (D-3): importanza bassa, niente suono ne' vibrazione. */
        const val CHANNEL_ID_DIRETTA = "scoreboard_diretta_channel"
    }

    /**
     * Cio' che la diretta del processo di prima ha lasciato, a ogni avvio del processo e **senza aprire la schermata** (D-3,
     * debiti 13 e 15): un messaggio dell'orologio fa risorgere il processo senza `MainViewModel`, e le chiusure rimaste segnate
     * restavano li' finche' l'app non si apriva. Si leggono solo le preferenze (nessun Keystore, nessuna rete, niente
     * `padelElite`); i lavori si scrivono su un altro thread. L'avviso del tetto delle 6 ore e' di una diretta che il processo
     * morto non trasmette piu': si toglie.
     */
    private fun sistemaLaDirettaDiPrima() {
        DirettaChiusuraWorker.accodaLeRimaste(
            this,
            DirettaStore(getSharedPreferences(DirettaStore.FILE, MODE_PRIVATE)),
            scopeDiAvvio,
        )
        DirettaAvvisoNotifica(this).togli()
    }

    private fun createNotificationChannel() {
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

        // Timer Channel (Low Importance - Silent for updates)
        val timerName = getString(R.string.match_timer)
        val timerDescription = "Active match timer updates"
        val timerChannel =
            NotificationChannel(CHANNEL_ID_TIMER, timerName, NotificationManager.IMPORTANCE_LOW).apply {
                description = timerDescription
                setShowBadge(false)
            }
        notificationManager.createNotificationChannel(timerChannel)

        // Diretta Channel (Low Importance - Silent): la notifica fissa del servizio in primo piano della diretta
        val direttaChannel =
            NotificationChannel(CHANNEL_ID_DIRETTA, getString(R.string.diretta_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.diretta_channel_description)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        notificationManager.createNotificationChannel(direttaChannel)

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
