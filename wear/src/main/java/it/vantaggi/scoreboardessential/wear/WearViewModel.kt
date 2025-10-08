package it.vantaggi.scoreboardessential.wear

import android.app.Application
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.PlayerData
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

// Sealed class to represent the state of the keeper timer
sealed class KeeperTimerState {
    object Hidden : KeeperTimerState()
    data class Running(val secondsRemaining: Int) : KeeperTimerState()
    object Finished : KeeperTimerState()
}

class WearViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "WearViewModel"

    // Team Names
    private val _team1Name = MutableStateFlow("TEAM 1")
    val team1Name = _team1Name.asStateFlow()
    private val _team2Name = MutableStateFlow("TEAM 2")
    val team2Name = _team2Name.asStateFlow()


    private val wearDataSync = OptimizedWearDataSync(application)
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

    init {
        startMatchTimer()
        listenForSyncEvents()

        // Perform initial sync after a short delay
        viewModelScope.launch {
            delay(1000)  // Wait for connection
            wearDataSync.syncFullState(
                team1Score = _team1Score.value,
                team2Score = _team2Score.value,
                team1Name = _team1Name.value,
                team2Name = _team2Name.value,
                timerMillis = matchTimeInSeconds * 1000,
                timerRunning = isMatchTimerRunning,
                keeperMillis = 0L,
                keeperRunning = false,
                matchActive = true
            )
        }
    }

    private fun listenForSyncEvents() {
        viewModelScope.launch {
            WearSyncManager.syncEvents.collect { event ->
                Log.d(TAG, "Evento ricevuto dal SyncManager: ${event::class.java.simpleName}")
                when (event) {
                    is WearSyncEvent.ScoreUpdate -> {
                        _team1Score.value = event.team1Score
                        _team2Score.value = event.team2Score
                    }
                    is WearSyncEvent.TeamNamesUpdate -> {
                        _team1Name.value = event.team1Name
                        _team2Name.value = event.team2Name
                    }
                    is WearSyncEvent.KeeperTimerUpdate -> {
                        if (event.isRunning) {
                            setKeeperTimerState(KeeperTimerState.Running((event.duration / 1000).toInt()))
                        } else {
                            setKeeperTimerState(KeeperTimerState.Hidden)
                        }
                    }
                    is WearSyncEvent.MatchReset -> {
                        resetMatch()
                    }
                    is WearSyncEvent.PlayerListUpdate -> {
                        _allPlayers.value = event.players
                        Log.d(TAG, "Updated player list: ${event.players.size} players")
                    }
                    is WearSyncEvent.TeamPlayersUpdate -> {
                        _team1Players.value = event.team1Players
                        _team2Players.value = event.team2Players
                        Log.d(TAG, "Updated team players: T1=${event.team1Players.size}, T2=${event.team2Players.size}")
                    }
                }
            }
        }
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
        sendScoreUpdateProper()  // Use new method
        _showPlayerSelection.value = 1
    }

    fun decrementTeam1Score() {
        if (_team1Score.value > 0) {
            _team1Score.value--
            triggerShortVibration()
            sendScoreUpdateProper()  // Use new method
        }
    }

    fun incrementTeam2Score() {
        _team2Score.value++
        triggerShortVibration()
        sendScoreUpdateProper()  // Use new method
        _showPlayerSelection.value = 2
    }

    fun decrementTeam2Score() {
        if (_team2Score.value > 0) {
            _team2Score.value--
            triggerShortVibration()
            sendScoreUpdateProper()  // Use new method
        }
    }

    private fun sendScoreUpdateProper() {
        wearDataSync.syncScores(
            _team1Score.value,
            _team2Score.value
        )
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
            resetKeeperTimer()
        } else {
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

        // Sync keeper timer start
        wearDataSync.syncKeeperTimer(keeperTimerDuration, true)
    }

    fun resetKeeperTimer() {
        keeperCountDownTimer?.cancel()
        vibrator?.cancel()
        _keeperTimer.value = KeeperTimerState.Hidden
        triggerShortVibration()

        // Sync keeper timer reset
        wearDataSync.syncKeeperTimer(0L, false)
    }

    // --- Reset ---
    fun startNewMatch() {
        _team1Score.value = 0
        _team2Score.value = 0

        matchTimerJob?.cancel()
        matchTimeInSeconds = 0L
        _matchTimer.value = "00:00"
        isMatchTimerRunning = false

        resetKeeperTimer()

        // Send match start message to mobile
        sendMatchControlMessage("START_MATCH")

        // Sync new match state
        wearDataSync.syncMatchState(true)
        wearDataSync.syncFullState(
            team1Score = 0,
            team2Score = 0,
            team1Name = _team1Name.value,
            team2Name = _team2Name.value,
            timerMillis = 0L,
            timerRunning = false,
            keeperMillis = 0L,
            keeperRunning = false,
            matchActive = true
        )
    }

    fun endMatch() {
        // Send match end message to mobile
        sendMatchControlMessage("END_MATCH")

        // Sync match ended
        wearDataSync.syncMatchState(false)
    }

    fun resetMatch() {
        _team1Score.value = 0
        _team2Score.value = 0

        matchTimerJob?.cancel()
        matchTimeInSeconds = 0L
        _matchTimer.value = "00:00"
        isMatchTimerRunning = false

        resetKeeperTimer()

        // Sync full reset
        wearDataSync.syncFullState(
            team1Score = 0,
            team2Score = 0,
            team1Name = _team1Name.value,
            team2Name = _team2Name.value,
            timerMillis = 0L,
            timerRunning = false,
            keeperMillis = 0L,
            keeperRunning = false,
            matchActive = false
        )
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
    private fun sendScoreUpdate() {
        val dataClient = Wearable.getMessageClient(getApplication())
        val messagePath = "/update-score"
        // Encode scores into a byte array. A simple format: "T1Score,T2Score"
        val data = "${_team1Score.value},${_team2Score.value}".toByteArray()

        Wearable.getNodeClient(getApplication()).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                dataClient.sendMessage(node.id, messagePath, data)
                    .addOnSuccessListener { Log.d(TAG, "Score update sent to ${node.displayName}") }
                    .addOnFailureListener { e -> Log.e(TAG, "Failed to send score update", e) }
            }
        }
    }
    fun startStopMatchTimer() {
        isMatchTimerRunning = !isMatchTimerRunning

        if (isMatchTimerRunning) {
            if (matchTimeInSeconds == 0L) {
                // Starting fresh
                matchTimeInSeconds = 0L
            }
            startMatchTimerInternal()
            sendTimerControlMessage("START")
        } else {
            pauseMatchTimerInternal()
            sendTimerControlMessage("PAUSE")
        }

        // Sync timer state
        wearDataSync.syncTimerState(
            matchTimeInSeconds * 1000,
            isMatchTimerRunning
        )
    }

    private fun startMatchTimerInternal() {
        matchTimerJob?.cancel()
        matchTimerJob = viewModelScope.launch {
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

        sendTimerControlMessage("RESET")

        // Sync reset
        wearDataSync.syncTimerState(0L, false)
    }

    private fun sendTimerControlMessage(action: String) {
        // Send via message for immediate response
        val messageClient = Wearable.getMessageClient(getApplication())
        val messagePath = "/timer-control"
        val data = action.toByteArray()

        Wearable.getNodeClient(getApplication()).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, messagePath, data)
            }
        }
    }

    private fun sendMatchControlMessage(action: String) {
        // Send via message for immediate response
        val messageClient = Wearable.getMessageClient(getApplication())
        val messagePath = "/match-control"
        val data = action.toByteArray()

        Wearable.getNodeClient(getApplication()).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, messagePath, data)
                Log.d(TAG, "Sent match control: $action to ${node.displayName}")
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
