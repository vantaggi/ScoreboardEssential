package com.example.scoreboardessential.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.CountDownTimer
import android.os.IBinder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.scoreboardessential.MainActivity
import com.example.scoreboardessential.R

import com.example.scoreboardessential.WearDataSync

class MatchTimerService : Service() {

    private val binder = MatchTimerBinder()
    private var timer: CountDownTimer? = null
    private lateinit var wearDataSync: WearDataSync

    private val _timerValue = MutableStateFlow(0L)
    val timerValue: StateFlow<Long> = _timerValue

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private var matchStartTime = 0L

    companion object {
        const val ACTION_START_PAUSE = "com.example.scoreboardessential.service.START_PAUSE"
        const val ACTION_STOP = "com.example.scoreboardessential.service.STOP"
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "MatchTimerChannel"
    }

    inner class MatchTimerBinder : Binder() {
        fun getService(): MatchTimerService = this@MatchTimerService
    }

    override fun onCreate() {
        super.onCreate()
        wearDataSync = WearDataSync(applicationContext)
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PAUSE -> {
                if (_isRunning.value) {
                    pauseTimer()
                } else {
                    startTimer()
                }
            }
            ACTION_STOP -> {
                stopTimer()
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        createNotificationChannel()
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Match Timer Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val pauseIntent = Intent(this, MatchTimerService::class.java).apply { action = ACTION_START_PAUSE }
        val pausePendingIntent = PendingIntent.getService(this, 0, pauseIntent, PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = Intent(this, MatchTimerService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        val hours = _timerValue.value / 3600000
        val minutes = (_timerValue.value % 3600000) / 60000
        val seconds = (_timerValue.value % 60000) / 1000
        val timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds)

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Match in Progress")
            .setContentText(timeString)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .addAction(if (_isRunning.value) R.drawable.ic_pause else R.drawable.ic_play, "Pause", pausePendingIntent)
            .addAction(R.drawable.ic_stop, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun sendTimerUpdate() {
        wearDataSync.syncTimerState(
            _timerValue.value,
            _isRunning.value
        )
    }

    fun startTimer() {
        if (_isRunning.value) return

        _isRunning.value = true
        if (_timerValue.value == 0L) {
            matchStartTime = System.currentTimeMillis()
        }
        startForegroundService()
        sendTimerUpdate()

        timer = object : CountDownTimer(Long.MAX_VALUE, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val elapsedTime = System.currentTimeMillis() - matchStartTime
                _timerValue.value = elapsedTime
                updateNotification()
            }

            override fun onFinish() {
                _isRunning.value = false
            }
        }.start()
    }

    fun pauseTimer() {
        timer?.cancel()
        _isRunning.value = false
        updateNotification()
        sendTimerUpdate()
    }

    fun stopTimer() {
        timer?.cancel()
        _isRunning.value = false
        _timerValue.value = 0L
        stopForeground(true)
        sendTimerUpdate()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}
