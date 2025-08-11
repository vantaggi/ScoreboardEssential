package com.example.scoreboardessential

import android.app.Application
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Sealed class to represent the state of the keeper timer
sealed class KeeperTimerState {
    object Hidden : KeeperTimerState()
    data class Running(val secondsRemaining: Int) : KeeperTimerState()
    object Finished : KeeperTimerState()
}

class WearViewModel(application: Application) : AndroidViewModel(application) {

    // Team Names
    private val _team1Name = MutableStateFlow("TEAM 1")
    val team1Name = _team1Name.asStateFlow()
    private val _team2Name = MutableStateFlow("TEAM 2")
    val team2Name = _team2Name.asStateFlow()

    // Team Scores
    private val _team1Score = MutableStateFlow(0)
    val team1Score = _team1Score.asStateFlow()

    private val _team2Score = MutableStateFlow(0)
    val team2Score = _team2Score.asStateFlow()

    // Match Timer
    private val _matchTimer = MutableStateFlow("00:00")
    val matchTimer = _matchTimer.asStateFlow()
    private var matchTimerJob: Job? = null
    private var matchTimeInSeconds = 0L

    // Keeper Timer
    private val _keeperTimer = MutableStateFlow<KeeperTimerState>(KeeperTimerState.Hidden)
    val keeperTimer = _keeperTimer.asStateFlow()
    private var keeperCountDownTimer: CountDownTimer? = null
    private val keeperTimerDuration = 300000L // 5 minutes

    // Haptics
    private val vibrator = ContextCompat.getSystemService(application, Vibrator::class.java)

    init {
        startMatchTimer()
    }

    // --- Score Management ---
    fun setTeamNames(team1Name: String, team2Name: String) {
        _team1Name.value = team1Name
        _team2Name.value = team2Name
    }

    fun setScores(team1Score: Int, team2Score: Int) {
        _team1Score.value = team1Score
        _team2Score.value = team2Score
    }

    fun incrementTeam1Score() {
        _team1Score.value++
        triggerShortVibration()
        sendScoreUpdate()
    }

    fun decrementTeam1Score() {
        if (_team1Score.value > 0) {
            _team1Score.value--
            triggerShortVibration()
            sendScoreUpdate()
        }
    }

    fun incrementTeam2Score() {
        _team2Score.value++
        triggerShortVibration()
        sendScoreUpdate()
    }

    fun decrementTeam2Score() {
        if (_team2Score.value > 0) {
            _team2Score.value--
            triggerShortVibration()
            sendScoreUpdate()
        }
    }

    // --- Match Timer Management ---
    fun setMatchTimer(time: String) {
        matchTimerJob?.cancel() // Stop the internal timer
        _matchTimer.value = time
    }

    private fun startMatchTimer() {
        matchTimerJob?.cancel()
        matchTimerJob = viewModelScope.launch {
            while (true) {
                val minutes = matchTimeInSeconds / 60
                val seconds = matchTimeInSeconds % 60
                _matchTimer.value = String.format("%02d:%02d", minutes, seconds)
                delay(1000)
                matchTimeInSeconds++
            }
        }
    }

    // --- Keeper Timer Management ---
    fun setKeeperTimerState(newState: KeeperTimerState) {
        keeperCountDownTimer?.cancel()
        vibrator?.cancel()
        _keeperTimer.value = newState
        // If the new state is Running, we need to start a countdown
        if (newState is KeeperTimerState.Running) {
            val duration = newState.secondsRemaining * 1000L
            keeperCountDownTimer = object : CountDownTimer(duration, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    _keeperTimer.value = KeeperTimerState.Running((millisUntilFinished / 1000).toInt())
                }

                override fun onFinish() {
                    _keeperTimer.value = KeeperTimerState.Finished
                    triggerStrongContinuousVibration()
                }
            }.start()
        }
    }

    fun handleKeeperTimer() {
        if (keeperTimer.value is KeeperTimerState.Running) {
            // If running, a long press should reset it.
            resetKeeperTimer()
        } else {
            // If hidden or finished, start it.
            startKeeperTimer()
        }
    }

    private fun startKeeperTimer() {
        keeperCountDownTimer?.cancel()
        _keeperTimer.value = KeeperTimerState.Running((keeperTimerDuration / 1000).toInt())
        keeperCountDownTimer = object : CountDownTimer(keeperTimerDuration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _keeperTimer.value = KeeperTimerState.Running((millisUntilFinished / 1000).toInt())
            }

            override fun onFinish() {
                _keeperTimer.value = KeeperTimerState.Finished
                triggerStrongContinuousVibration()
            }
        }.start()
        triggerShortVibration()
    }

    fun resetKeeperTimer() {
        keeperCountDownTimer?.cancel()
        vibrator?.cancel() // Stop the continuous vibration if it's active
        _keeperTimer.value = KeeperTimerState.Hidden // Or decide if it should restart immediately
        triggerShortVibration()
    }

    // --- Reset ---
    fun resetMatch() {
        _team1Score.value = 0
        _team2Score.value = 0

        matchTimerJob?.cancel()
        matchTimeInSeconds = 0L
        _matchTimer.value = "00:00"
        startMatchTimer() // Restart the timer from 0

        resetKeeperTimer()
    }

    // --- Haptic Feedback ---
    private fun triggerShortVibration() {
        val effect = VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE)
        vibrator?.vibrate(effect)
    }

    private fun triggerStrongContinuousVibration() {
        val pattern = longArrayOf(0, 500, 500) // Vibrate for 500ms, pause for 500ms
        val effect = VibrationEffect.createWaveform(pattern, 0) // Repeat from the start
        vibrator?.vibrate(effect)
    }

    // --- Data Synchronization ---
    private fun sendScoreUpdate() {
        val dataClient = Wearable.getMessageClient(getApplication())
        val messagePath = "/update-score"
        // Encode scores into a byte array. A simple format: "T1Score,T2Score"
        val data = "${_team1Score.value},${_team2Score.value}".toByteArray()

        Wearable.getNodeClient(getApplication()).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                dataClient.sendMessage(node.id, messagePath, data)
                    .addOnSuccessListener { Log.d("WearViewModel", "Score update sent to ${node.displayName}") }
                    .addOnFailureListener { e -> Log.e("WearViewModel", "Failed to send score update", e) }
            }
        }
    }
    fun startStopMatchTimer() {
        isMatchTimerRunning = !isMatchTimerRunning
        sendTimerControlMessage(if (isMatchTimerRunning) "START" else "PAUSE")
    }

    fun resetMatchTimer() {
        matchTimeInSeconds = 0L
        isMatchTimerRunning = false
        sendTimerControlMessage("RESET")
    }

    private fun sendTimerControlMessage(action: String) {
        val dataClient = Wearable.getMessageClient(getApplication())
        val messagePath = "/timer-control"
        val data = action.toByteArray()

        Wearable.getNodeClient(getApplication()).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                dataClient.sendMessage(node.id, messagePath, data)
            }
        }
    }
    override fun onCleared() {
        super.onCleared()
        matchTimerJob?.cancel()
        keeperCountDownTimer?.cancel()
        vibrator?.cancel()
    }
}
