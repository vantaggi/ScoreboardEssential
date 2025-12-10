package it.vantaggi.scoreboardessential.wear

import android.app.Application
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager
import it.vantaggi.scoreboardessential.shared.PlayerData
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class KeeperTimerState {
    object Hidden : KeeperTimerState()

    data class Running(
        val secondsRemaining: Int,
    ) : KeeperTimerState()

    object Finished : KeeperTimerState()
}

class WearViewModel(
    application: Application,
) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "WearViewModel"
    }

    // Team Names
    private val _team1Name = MutableStateFlow("TEAM 1")
    val team1Name = _team1Name.asStateFlow()
    private val _team2Name = MutableStateFlow("TEAM 2")
    val team2Name = _team2Name.asStateFlow()

    // Team Colors
    private val _team1Color = MutableStateFlow<Int?>(null)
    val team1Color = _team1Color.asStateFlow()
    private val _team2Color = MutableStateFlow<Int?>(null)
    val team2Color = _team2Color.asStateFlow()

    private val connectionManager = OptimizedWearDataSync(application)
    val connectionState = connectionManager.connectionState

    // Team Scores
    private val _team1Score = MutableStateFlow(0)
    val team1Score = _team1Score.asStateFlow()

    private val _team2Score = MutableStateFlow(0)
    val team2Score = _team2Score.asStateFlow()

    private val _matchTimer = MutableStateFlow("00:00")
    val matchTimer = _matchTimer.asStateFlow()
    private var matchTimerJob: Job? = null
    private var matchTimeInSeconds = 0L

    // Stato timer partita
    private var isMatchTimerRunning: Boolean = false

    // Keeper Timer
    private val _keeperTimer = MutableStateFlow<KeeperTimerState>(KeeperTimerState.Hidden)
    val keeperTimer = _keeperTimer.asStateFlow()

    private val _keeperProgress = MutableStateFlow(300)
    val keeperProgress = _keeperProgress.asStateFlow()

    private var keeperCountDownTimer: CountDownTimer? = null
    private val keeperTimerDuration = 300000L // 5 minutes

    // Haptics
    private val vibrator = ContextCompat.getSystemService(application, Vibrator::class.java)

    // Player selection events
    private val _showPlayerSelection = MutableStateFlow<Int?>(null)
    val showPlayerSelection = _showPlayerSelection.asStateFlow()

    // Player data
    private val _allPlayers = MutableStateFlow<List<PlayerData>>(emptyList())
    val allPlayers = _allPlayers.asStateFlow()

    private val _team1Players = MutableStateFlow<List<PlayerData>>(emptyList())
    val team1Players = _team1Players.asStateFlow()

    private val _team2Players = MutableStateFlow<List<PlayerData>>(emptyList())
    val team2Players = _team2Players.asStateFlow()

    fun clearPlayerSelectionEvent() {
        _showPlayerSelection.value = null
    }

    fun updateScoresFromMobile(
        team1Score: Int,
        team2Score: Int,
    ) {
        _team1Score.value = team1Score
        _team2Score.value = team2Score
    }

    // --- Score Management ---
    fun setTeamNames(
        team1Name: String,
        team2Name: String,
    ) {
        _team1Name.value = team1Name
        _team2Name.value = team2Name
    }

    fun setTeamColor(team: Int, color: Int) {
        if (team == 1) {
            _team1Color.value = color
        } else {
            _team2Color.value = color
        }
    }

    fun setScores(
        team1Score: Int,
        team2Score: Int,
    ) {
        _team1Score.value = team1Score
        _team2Score.value = team2Score
    }

    fun updateScore(
        team1: Int,
        team2: Int,
    ) {
        _team1Score.value = team1
        _team2Score.value = team2
        viewModelScope.launch {
            val data =
                mapOf(
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_TEAM1_SCORE to team1,
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_TEAM2_SCORE to team2,
                )
            connectionManager.sendData(
                path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_SCORE,
                data = data,
                urgent = true,
            )
        }
    }

    fun incrementTeam1Score() {
        val newScore = _team1Score.value + 1
        updateScore(newScore, _team2Score.value)
        triggerShortVibration()
    }

    fun decrementTeam1Score() {
        if (_team1Score.value > 0) {
            val newScore = _team1Score.value - 1
            updateScore(newScore, _team2Score.value)
            triggerShortVibration()
        }
    }

    fun incrementTeam2Score() {
        val newScore = _team2Score.value + 1
        updateScore(_team1Score.value, newScore)
        triggerShortVibration()
    }

    fun decrementTeam2Score() {
        if (_team2Score.value > 0) {
            val newScore = _team2Score.value - 1
            updateScore(_team1Score.value, newScore)
            triggerShortVibration()
        }
    }

    // --- Match Timer Management ---
    fun setMatchTimer(time: String) {
        matchTimerJob?.cancel() // Stop the internal timer
        _matchTimer.value = time
    }

    fun setMatchTimerMillis(millis: Long) {
        matchTimerJob?.cancel() // Stop internal timer if we get an update from mobile
        val minutes = (millis / 1000) / 60
        val seconds = (millis / 1000) % 60
        _matchTimer.value = String.format("%02d:%02d", minutes, seconds)
    }

    // --- Keeper Timer Management ---
    fun setKeeperTimerState(newState: KeeperTimerState) {
        // Prevent restarting the timer if the state is basically the same
        val currentState = _keeperTimer.value
        if (currentState is KeeperTimerState.Running && newState is KeeperTimerState.Running) {
            val diff = kotlin.math.abs(currentState.secondsRemaining - newState.secondsRemaining)
            if (diff < 2) {
                // Ignore update if difference is less than 2 seconds to avoid jitter
                return
            }
        }

        keeperCountDownTimer?.cancel()
        vibrator?.cancel()
        _keeperTimer.value = newState
        // If the new state is Running, we need to start a countdown
        if (newState is KeeperTimerState.Running) {
            val duration = newState.secondsRemaining * 1000L
            _keeperProgress.value = newState.secondsRemaining
            keeperCountDownTimer =
                object : CountDownTimer(duration, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val seconds = (millisUntilFinished / 1000).toInt()
                        _keeperTimer.value = KeeperTimerState.Running(seconds)
                        _keeperProgress.value = seconds
                    }

                    override fun onFinish() {
                        _keeperTimer.value = KeeperTimerState.Finished
                        _keeperProgress.value = 0
                        triggerStrongContinuousVibration()
                    }
                }.start()
        }
    }

    fun toggleKeeperTimer() {
        if (keeperTimer.value is KeeperTimerState.Running) {
            resetKeeperTimer()
        } else {
            startKeeperTimer()
        }
    }

    // --- Reset ---

    fun resetMatch() {
        updateScore(0, 0)
        matchTimerJob?.cancel()
        matchTimeInSeconds = 0L
        _matchTimer.value = "00:00"
        isMatchTimerRunning = false

        resetKeeperTimer()

        viewModelScope.launch {
            val data =
                mapOf(
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_TIMER_MILLIS to 0L,
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_TIMER_RUNNING to false,
                )
            connectionManager.sendData(
                path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_TIMER_STATE,
                data = data,
            )
        }
    }

    // --- Haptic Feedback ---
    private fun triggerShortVibration() {
        val effect = VibrationEffect.createWaveform(HapticFeedbackManager.PATTERN_CONFIRM, -1)
        vibrator?.vibrate(effect)
    }

    private fun triggerStrongContinuousVibration() {
        val effect = VibrationEffect.createWaveform(HapticFeedbackManager.PATTERN_ALERT, -1)
        vibrator?.vibrate(effect)
    }

    // --- Data Synchronization ---

    fun toggleTimer() {
        isMatchTimerRunning = !isMatchTimerRunning
        if (isMatchTimerRunning) {
            startMatchTimerInternal()
        } else {
            pauseMatchTimerInternal()
        }
        viewModelScope.launch {
            val data =
                mapOf(
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_TIMER_MILLIS to matchTimeInSeconds * 1000,
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_TIMER_RUNNING to isMatchTimerRunning,
                )
            connectionManager.sendData(
                path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_TIMER_STATE,
                data = data,
            )
        }
    }

    private fun startMatchTimerInternal() {
        matchTimerJob?.cancel()
        matchTimerJob =
            viewModelScope.launch {
                while (isMatchTimerRunning) {
                    val minutes = matchTimeInSeconds / 60
                    val seconds = matchTimeInSeconds % 60
                    _matchTimer.value = String.format("%02d:%02d", minutes, seconds)
                    delay(1000)
                    if (isMatchTimerRunning) {
                        matchTimeInSeconds++
                    }
                }
            }
    }

    private fun pauseMatchTimerInternal() {
        matchTimerJob?.cancel()
    }

    fun resetMatchTimer() {
        matchTimeInSeconds = 0L
        isMatchTimerRunning = false
        matchTimerJob?.cancel()
        _matchTimer.value = "00:00"

        viewModelScope.launch {
            val data =
                mapOf(
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_TIMER_MILLIS to 0L,
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_TIMER_RUNNING to false,
                )
            connectionManager.sendData(
                path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_TIMER_STATE,
                data = data,
            )
        }
    }

    private fun startKeeperTimer() {
        keeperCountDownTimer?.cancel()
        val totalSeconds = (keeperTimerDuration / 1000).toInt()
        _keeperTimer.value = KeeperTimerState.Running(totalSeconds)
        _keeperProgress.value = totalSeconds

        keeperCountDownTimer =
            object : CountDownTimer(keeperTimerDuration, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val seconds = (millisUntilFinished / 1000).toInt()
                    _keeperTimer.value = KeeperTimerState.Running(seconds)
                    _keeperProgress.value = seconds
                }

                override fun onFinish() {
                    _keeperTimer.value = KeeperTimerState.Finished
                    _keeperProgress.value = 0
                    triggerStrongContinuousVibration()
                }
            }.start()

        triggerShortVibration()

        // Sync keeper timer start
        viewModelScope.launch {
            val data =
                mapOf(
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_KEEPER_MILLIS to keeperTimerDuration,
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_KEEPER_RUNNING to true,
                )
            connectionManager.sendData(
                path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_KEEPER_TIMER,
                data = data,
            )
        }
    }

    fun resetKeeperTimer() {
        keeperCountDownTimer?.cancel()
        vibrator?.cancel()
        _keeperTimer.value = KeeperTimerState.Hidden
        _keeperProgress.value = (keeperTimerDuration / 1000).toInt()
        triggerShortVibration()

        // Sync keeper timer reset
        viewModelScope.launch {
            val data =
                mapOf(
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_KEEPER_MILLIS to 0L,
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_KEEPER_RUNNING to false,
                )
            connectionManager.sendData(
                path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_KEEPER_TIMER,
                data = data,
            )
        }
    }
}
