package it.vantaggi.scoreboardessential.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MatchTimerService : Service() {

    private val binder = MatchTimerBinder()
    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _timerValue = MutableStateFlow(0L)
    val timerValue = _timerValue.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private var matchStartTime = 0L

    companion object {
        private const val PREFS_NAME = "MatchTimerPrefs"
        private const val KEY_START_TIME = "start_time"
        private const val KEY_IS_RUNNING = "is_running"
        private const val KEY_ELAPSED_TIME = "elapsed_time"
    }

    override fun onCreate() {
        super.onCreate()
        restoreTimerState()
    }

    inner class MatchTimerBinder : Binder() {
        fun getService(): MatchTimerService = this@MatchTimerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun startTimer() {
        if (_isRunning.value) return

        _isRunning.value = true
        matchStartTime = System.currentTimeMillis() - _timerValue.value

        timerJob = scope.launch {
            while (isActive) {
                _timerValue.value = System.currentTimeMillis() - matchStartTime
                delay(100) // Update every 100ms for smoother UI
            }
        }
        saveTimerState()
    }

    fun pauseTimer() {
        _isRunning.value = false
        timerJob?.cancel()
        saveTimerState()
    }

    fun stopTimer() {
        _isRunning.value = false
        timerJob?.cancel()
        _timerValue.value = 0L
        saveTimerState()
    }

    private fun saveTimerState() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putLong(KEY_START_TIME, matchStartTime)
            putBoolean(KEY_IS_RUNNING, _isRunning.value)
            putLong(KEY_ELAPSED_TIME, _timerValue.value)
            apply()
        }
    }

    private fun restoreTimerState() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).apply {
            matchStartTime = getLong(KEY_START_TIME, 0L)
            _isRunning.value = getBoolean(KEY_IS_RUNNING, false)
            _timerValue.value = getLong(KEY_ELAPSED_TIME, 0L)

            if (_isRunning.value) {
                startTimer() // Riprendi il timer se era in esecuzione
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        scope.cancel()
    }
}
