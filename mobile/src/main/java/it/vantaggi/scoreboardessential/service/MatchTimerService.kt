package it.vantaggi.scoreboardessential.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import it.vantaggi.scoreboardessential.MainActivity
import it.vantaggi.scoreboardessential.R
import it.vantaggi.scoreboardessential.ScoreboardEssentialApplication
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager
import it.vantaggi.scoreboardessential.shared.communication.WearConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MatchTimerService : Service() {
    private val binder = MatchTimerBinder()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var connectionManager: WearConnectionManager
    private lateinit var vibrator: Vibrator

    // Match Timer
    private var matchTimerJob: Job? = null
    private val _matchTimerValue = MutableStateFlow(0L)
    val matchTimerValue = _matchTimerValue.asStateFlow()
    private val _isMatchTimerRunning = MutableStateFlow(false)
    val isMatchTimerRunning = _isMatchTimerRunning.asStateFlow()
    private var matchStartTime = 0L
    private var elapsedTimeOnPause = 0L

    // Keeper Timer
    private var keeperTimerJob: Job? = null
    private val _keeperTimerValue = MutableStateFlow(0L)
    val keeperTimerValue = _keeperTimerValue.asStateFlow()
    private val _isKeeperTimerRunning = MutableStateFlow(false)
    val isKeeperTimerRunning = _isKeeperTimerRunning.asStateFlow()
    private var keeperTimerEndTime = 0L
    private var keeperRemainingOnPause = 0L

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val KEEPER_TIMER_EXPIRED_NOTIFICATION_ID = 2
        const val ACTION_PAUSE = "it.vantaggi.scoreboardessential.service.PAUSE"
        const val ACTION_STOP = "it.vantaggi.scoreboardessential.service.STOP"

        private const val PREFS_NAME = "MatchTimerPrefs"
        private const val KEY_MATCH_START_TIME = "match_start_time"
        private const val KEY_MATCH_ELAPSED_PAUSE = "match_elapsed_pause"
        private const val KEY_MATCH_RUNNING = "match_running"
        private const val KEY_KEEPER_END_TIME = "keeper_end_time"
        private const val KEY_KEEPER_REMAINING_PAUSE = "keeper_remaining_pause"
        private const val KEY_KEEPER_RUNNING = "keeper_running"
    }

    override fun onCreate() {
        super.onCreate()
        connectionManager = WearConnectionManager(applicationContext)
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        restoreState()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                pauseTimer()
                pauseKeeperTimer()
            }
            ACTION_STOP -> {
                stopTimer()
                resetKeeperTimer()
            }
        }
        return START_STICKY
    }

    inner class MatchTimerBinder : Binder() {
        fun getService(): MatchTimerService = this@MatchTimerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    // --- Match Timer Control ---
    fun startTimer() {
        if (_isMatchTimerRunning.value) return
        _isMatchTimerRunning.value = true
        matchStartTime = System.currentTimeMillis() - elapsedTimeOnPause
        matchTimerJob =
            scope.launch {
                while (isActive) {
                    val elapsed = System.currentTimeMillis() - matchStartTime
                    _matchTimerValue.value = elapsed
                    updateNotification(elapsed)
                    val data = mapOf(
                        it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_TIMER_MILLIS to elapsed,
                        it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_TIMER_RUNNING to true
                    )
                    connectionManager.sendData(
                        path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_TIMER_STATE,
                        data = data
                    )
                    delay(1000)
                }
            }
        startForegroundWithPermissionCheck()
        saveState()
    }

    fun pauseTimer() {
        if (!_isMatchTimerRunning.value) return
        _isMatchTimerRunning.value = false
        matchTimerJob?.cancel()
        elapsedTimeOnPause = _matchTimerValue.value
        scope.launch {
            val data = mapOf(
                it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_TIMER_MILLIS to _matchTimerValue.value,
                it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_TIMER_RUNNING to false
            )
            connectionManager.sendData(
                path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_TIMER_STATE,
                data = data
            )
        }
        updateNotification(_matchTimerValue.value)
        checkStopForeground()
        saveState()
    }

    fun stopTimer() {
        _isMatchTimerRunning.value = false
        matchTimerJob?.cancel()
        _matchTimerValue.value = 0L
        elapsedTimeOnPause = 0L
        scope.launch {
            val data = mapOf(
                it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_TIMER_MILLIS to 0L,
                it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_TIMER_RUNNING to false
            )
            connectionManager.sendData(
                path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_TIMER_STATE,
                data = data
            )
        }
        updateNotification(0)
        checkStopForeground()
        saveState()
    }

    // --- Keeper Timer Control ---
    fun startKeeperTimer(durationMillis: Long) {
        if (_isKeeperTimerRunning.value) return
        val duration = if (keeperRemainingOnPause > 0) keeperRemainingOnPause else durationMillis
        _isKeeperTimerRunning.value = true
        keeperTimerEndTime = System.currentTimeMillis() + duration
        keeperTimerJob =
            scope.launch {
                while (isActive) {
                    val remaining = keeperTimerEndTime - System.currentTimeMillis()
                    if (remaining > 0) {
                        _keeperTimerValue.value = remaining
                    } else {
                        _keeperTimerValue.value = 0L
                        _isKeeperTimerRunning.value = false
                        keeperRemainingOnPause = 0L
                        scope.launch {
                            val data = mapOf(
                                it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_KEEPER_MILLIS to 0L,
                                it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_KEEPER_RUNNING to false
                            )
                            connectionManager.sendData(
                                path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_KEEPER_TIMER,
                                data = data
                            )
                        }
                        showKeeperTimerExpiredNotification()
                        triggerKeeperTimerExpiredVibration()
                        this.cancel()
                    }
                    delay(1000)
                }
            }
        scope.launch {
            val data = mapOf(
                it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_KEEPER_MILLIS to duration,
                it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_KEEPER_RUNNING to true
            )
            connectionManager.sendData(
                path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_KEEPER_TIMER,
                data = data
            )
        }
        startForegroundWithPermissionCheck()
        saveState()
    }

    fun pauseKeeperTimer() {
        if (!_isKeeperTimerRunning.value) return
        _isKeeperTimerRunning.value = false
        keeperTimerJob?.cancel()
        keeperRemainingOnPause = _keeperTimerValue.value
        scope.launch {
            val data = mapOf(
                it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_KEEPER_MILLIS to _keeperTimerValue.value,
                it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_KEEPER_RUNNING to false
            )
            connectionManager.sendData(
                path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_KEEPER_TIMER,
                data = data
            )
        }
        checkStopForeground()
        saveState()
    }

    fun resetKeeperTimer() {
        _isKeeperTimerRunning.value = false
        keeperTimerJob?.cancel()
        _keeperTimerValue.value = 0L
        keeperTimerEndTime = 0L
        keeperRemainingOnPause = 0L
        scope.launch {
            val data = mapOf(
                it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_KEEPER_MILLIS to 0L,
                it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_KEEPER_RUNNING to false
            )
            connectionManager.sendData(
                path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_KEEPER_TIMER,
                data = data
            )
        }
        checkStopForeground()
        saveState()
    }

    private fun triggerKeeperTimerExpiredVibration() {
        val effect = VibrationEffect.createWaveform(HapticFeedbackManager.PATTERN_ALERT, -1)
        vibrator.vibrate(effect)
    }

    private fun checkStopForeground() {
        if (!_isMatchTimerRunning.value && !_isKeeperTimerRunning.value) {
            stopForeground(true)
        }
    }

    private fun startForegroundWithPermissionCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        startForeground(NOTIFICATION_ID, createNotification(_matchTimerValue.value))
    }

    // --- Foreground Service & Notifications ---
    private fun createNotification(timeInMillis: Long): Notification {
        val formattedTime = formatTime(timeInMillis)
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val pauseIntent = Intent(this, MatchTimerService::class.java).apply { action = ACTION_PAUSE }
        val pausePendingIntent = PendingIntent.getService(this, 0, pauseIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, MatchTimerService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat
            .Builder(this, ScoreboardEssentialApplication.CHANNEL_ID)
            .setContentTitle(getString(R.string.match_timer))
            .setContentText(formattedTime)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, getString(R.string.pause), pausePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop), stopPendingIntent)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification(timeInMillis: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notification = createNotification(timeInMillis)
        with(NotificationManagerCompat.from(this)) {
            notify(NOTIFICATION_ID, notification)
        }
    }

    private fun showKeeperTimerExpiredNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification =
            NotificationCompat
                .Builder(this, ScoreboardEssentialApplication.CHANNEL_ID)
                .setContentTitle(getString(R.string.keeper_timer_expired_title))
                .setContentText(getString(R.string.keeper_timer_expired_text))
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

        with(NotificationManagerCompat.from(this)) {
            notify(KEEPER_TIMER_EXPIRED_NOTIFICATION_ID, notification)
        }
    }

    // --- State Persistence ---
    private fun saveState() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putBoolean(KEY_MATCH_RUNNING, _isMatchTimerRunning.value)
            if (_isMatchTimerRunning.value) {
                putLong(KEY_MATCH_START_TIME, matchStartTime)
            } else {
                putLong(KEY_MATCH_ELAPSED_PAUSE, elapsedTimeOnPause)
            }

            putBoolean(KEY_KEEPER_RUNNING, _isKeeperTimerRunning.value)
            if (_isKeeperTimerRunning.value) {
                putLong(KEY_KEEPER_END_TIME, keeperTimerEndTime)
            } else {
                putLong(KEY_KEEPER_REMAINING_PAUSE, keeperRemainingOnPause)
            }
            apply()
        }
    }

    private fun restoreState() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isMatchRunning = prefs.getBoolean(KEY_MATCH_RUNNING, false)
        if (isMatchRunning) {
            matchStartTime = prefs.getLong(KEY_MATCH_START_TIME, System.currentTimeMillis())
            val elapsed = System.currentTimeMillis() - matchStartTime
            _matchTimerValue.value = elapsed
            elapsedTimeOnPause = elapsed
            startTimer()
        } else {
            elapsedTimeOnPause = prefs.getLong(KEY_MATCH_ELAPSED_PAUSE, 0L)
            _matchTimerValue.value = elapsedTimeOnPause
        }

        val isKeeperRunning = prefs.getBoolean(KEY_KEEPER_RUNNING, false)
        if (isKeeperRunning) {
            keeperTimerEndTime = prefs.getLong(KEY_KEEPER_END_TIME, 0L)
            val remaining = keeperTimerEndTime - System.currentTimeMillis()
            if (remaining > 0) {
                keeperRemainingOnPause = remaining
                startKeeperTimer(remaining)
            }
        } else {
            keeperRemainingOnPause = prefs.getLong(KEY_KEEPER_REMAINING_PAUSE, 0L)
            _keeperTimerValue.value = keeperRemainingOnPause
        }
    }

    private fun formatTime(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        saveState()
        matchTimerJob?.cancel()
        keeperTimerJob?.cancel()
        scope.cancel()
    }
}
