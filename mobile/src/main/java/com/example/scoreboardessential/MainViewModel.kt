package com.example.scoreboardessential

import android.app.Application
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.scoreboardessential.database.*
import com.example.scoreboardessential.utils.ScoreUpdateEventBus
import com.example.scoreboardessential.utils.SingleLiveEvent
import com.example.scoreboardessential.utils.TimerEvent
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

data class MatchEvent(
    val timestamp: String,
    val event: String,
    val team: Int? = null,
    val player: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val playerDao: PlayerDao
    private val matchDao: MatchDao
    private val vibrator = ContextCompat.getSystemService(application, Vibrator::class.java)

    private val wearDataSync = WearDataSync(application)

    // Scores
    private val _team1Score = MutableLiveData(0)
    val team1Score: LiveData<Int> = _team1Score

    private val _team2Score = MutableLiveData(0)
    val team2Score: LiveData<Int> = _team2Score

    // Team Names
    private val _team1Name = MutableLiveData("TEAM 1")
    val team1Name: LiveData<String> = _team1Name

    private val _team2Name = MutableLiveData("TEAM 2")
    val team2Name: LiveData<String> = _team2Name

    // Team Colors
    private val _team1Color = MutableLiveData(0xFFFFA726.toInt())
    val team1Color: LiveData<Int> = _team1Color

    private val _team2Color = MutableLiveData(0xFFAEEA00.toInt())
    val team2Color: LiveData<Int> = _team2Color

    // Match Timer
    private val _matchTimerValue = MutableLiveData(0L)
    val matchTimerValue: LiveData<Long> = _matchTimerValue
    private var matchTimer: CountDownTimer? = null
    private var isMatchTimerRunning = false
    private var matchStartTime = 0L

    // Keeper Timer
    private val _keeperTimerValue = MutableLiveData(0L)
    val keeperTimerValue: LiveData<Long> = _keeperTimerValue
    private var keeperTimer: CountDownTimer? = null
    private var isKeeperTimerRunning = false
    private var keeperTimerDuration = 300000L // 5 minutes default

    // Team Players
    private val _team1Players = MutableLiveData<List<Player>>(emptyList())
    val team1Players: LiveData<List<Player>> = _team1Players

    private val _team2Players = MutableLiveData<List<Player>>(emptyList())
    val team2Players: LiveData<List<Player>> = _team2Players

    // All Players (for selection)
    private val _allPlayers = MutableLiveData<List<Player>>(emptyList())
    val allPlayers: LiveData<List<Player>> = _allPlayers

    // Match Events Log
    private val _matchEvents = MutableLiveData<List<MatchEvent>>(emptyList())
    val matchEvents: LiveData<List<MatchEvent>> = _matchEvents

    // UI Events
    val showSelectScorerDialog = SingleLiveEvent<Int>()
    val showPlayerSelectionDialog = SingleLiveEvent<Int>()
    val showKeeperTimerExpired = SingleLiveEvent<Unit>()

    // Data Client for Wear OS sync
    private val dataClient: DataClient = Wearable.getDataClient(application)

    // Current Match ID
    private var currentMatchId: Long? = null

    init {
        val db = AppDatabase.getDatabase(application)
        matchDao = db.matchDao()
        playerDao = db.playerDao()
        _keeperTimerValue.value = 0L  // Start with 0 to hide it

        listenForScoreUpdates()
        listenForTimerEvents()
        loadAllPlayers()
        startNewMatch()
        viewModelScope.launch {
            delay(500)
            wearDataSync.syncFullState(
                team1Score = _team1Score.value ?: 0,
                team2Score = _team2Score.value ?: 0,
                team1Name = _team1Name.value ?: "TEAM 1",
                team2Name = _team2Name.value ?: "TEAM 2",
                timerMillis = _matchTimerValue.value ?: 0L,
                timerRunning = isMatchTimerRunning,
                keeperMillis = _keeperTimerValue.value ?: 0L,
                keeperRunning = isKeeperTimerRunning,
                matchActive = currentMatchId != null
            )
        }
    }

    private fun listenForScoreUpdates() {
        viewModelScope.launch {
            ScoreUpdateEventBus.events.collect { event ->
                _team1Score.postValue(event.team1Score)
                _team2Score.postValue(event.team2Score)
                addMatchEvent("Score updated from Wear OS")
            }
        }
    }

    private fun listenForTimerEvents() {
        viewModelScope.launch {
            ScoreUpdateEventBus.timerEvents.collect { event ->
                when (event) {
                    is TimerEvent.Start -> {
                        startMatchTimer()
                        addMatchEvent("Timer started from Wear OS")
                    }
                    is TimerEvent.Pause -> {
                        pauseMatchTimer()
                        addMatchEvent("Timer paused from Wear OS")
                    }
                    is TimerEvent.Reset -> {
                        resetMatchTimer()
                        addMatchEvent("Timer reset from Wear OS")
                    }
                    is TimerEvent.StartNewMatch -> {
                        startNewMatch()
                        addMatchEvent("New match started from Wear OS")
                    }
                    is TimerEvent.EndMatch -> {
                        endMatch()
                        addMatchEvent("Match ended from Wear OS")
                    }
                }
            }
        }
    }

    private fun loadAllPlayers() {
        viewModelScope.launch {
            playerDao.getAllPlayers().collect { players ->
                _allPlayers.postValue(players)
            }
        }
    }

    private fun startNewMatch() {
        _team1Score.value = 0
        _team2Score.value = 0
        _matchEvents.value = emptyList()
        matchStartTime = System.currentTimeMillis()
        _matchTimerValue.value = 0L

        matchTimer?.cancel()
        isMatchTimerRunning = false

        addMatchEvent("New match ready - press START to begin")
        sendMatchStateUpdate(true)  // Sync match started
        sendScoreUpdate()  // Sync initial scores
    }

    // --- Team Management ---
    fun setTeam1Name(name: String) {
        _team1Name.value = name
        sendTeamNamesUpdate()
    }

    fun setTeam2Name(name: String) {
        _team2Name.value = name
        sendTeamNamesUpdate()
    }

    fun setTeamColor(team: Int, color: Int) {
        if (team == 1) {
            _team1Color.value = color
        } else {
            _team2Color.value = color
        }
    }

    // --- Player Management ---
    fun addPlayerToTeam(player: Player, teamId: Int) {
        viewModelScope.launch {
            if (teamId == 1) {
                val currentPlayers = _team1Players.value?.toMutableList() ?: mutableListOf()
                if (!currentPlayers.contains(player)) {
                    currentPlayers.add(player)
                    _team1Players.postValue(currentPlayers)
                    addMatchEvent("${player.playerName} added to ${_team1Name.value}", team = 1)
                }
            } else {
                val currentPlayers = _team2Players.value?.toMutableList() ?: mutableListOf()
                if (!currentPlayers.contains(player)) {
                    currentPlayers.add(player)
                    _team2Players.postValue(currentPlayers)
                    addMatchEvent("${player.playerName} added to ${_team2Name.value}", team = 2)
                }
            }
        }
    }

    fun removePlayerFromTeam(player: Player, teamId: Int) {
        if (teamId == 1) {
            val currentPlayers = _team1Players.value?.toMutableList() ?: mutableListOf()
            currentPlayers.remove(player)
            _team1Players.postValue(currentPlayers)
        } else {
            val currentPlayers = _team2Players.value?.toMutableList() ?: mutableListOf()
            currentPlayers.remove(player)
            _team2Players.postValue(currentPlayers)
        }
    }

    fun createNewPlayer(name: String, roles: String) {
        viewModelScope.launch {
            val player = Player(
                playerName = name,
                roles = roles,
                appearances = 0,
                goals = 0
            )
            playerDao.insert(player)
            addMatchEvent("New player created: $name")
        }
    }

    // --- Score Management ---
    fun addTeam1Score() {
        _team1Score.value = (_team1Score.value ?: 0) + 1
        triggerHapticFeedback()
        showSelectScorerDialog.postValue(1)
        sendScoreUpdate()
    }

    fun subtractTeam1Score() {
        val currentScore = _team1Score.value ?: 0
        if (currentScore > 0) {
            _team1Score.value = currentScore - 1
            triggerHapticFeedback()
            addMatchEvent("Score correction for ${_team1Name.value}", team = 1)
            sendScoreUpdate()
        }
    }

    fun addTeam2Score() {
        _team2Score.value = (_team2Score.value ?: 0) + 1
        triggerHapticFeedback()
        showSelectScorerDialog.postValue(2)
        sendScoreUpdate()
    }

    fun subtractTeam2Score() {
        val currentScore = _team2Score.value ?: 0
        if (currentScore > 0) {
            _team2Score.value = currentScore - 1
            triggerHapticFeedback()
            addMatchEvent("Score correction for ${_team2Name.value}", team = 2)
            sendScoreUpdate()
        }
    }

    fun addScorer(team: Int, playerName: String) {
        viewModelScope.launch {
            val players = if (team == 1) _team1Players.value else _team2Players.value
            val player = players?.find { it.playerName == playerName }

            if (player != null) {
                player.goals++
                playerDao.update(player)

                val teamName = if (team == 1) _team1Name.value else _team2Name.value
                val roleText = if (player.roles.isNotEmpty()) " (${player.roles})" else ""
                addMatchEvent("GOAL! $playerName$roleText (${teamName})", team = team, player = playerName)

                // Invia info al Wear con ruolo
                sendScorerToWear(playerName, player.roles, team)
            }
        }
    }
    private fun sendScorerToWear(name: String, role: String, team: Int) {
        wearDataSync.syncScorerSelected(name, role, team)
    }

    // --- Match Timer Management ---
    fun startStopMatchTimer() {
        if (isMatchTimerRunning) {
            pauseMatchTimer()
        } else {
            startMatchTimer()
        }
    }

    private fun startMatchTimer() {
        if (isMatchTimerRunning) return

        matchTimer?.cancel()
        isMatchTimerRunning = true

        if (_matchTimerValue.value == 0L) {
            matchStartTime = System.currentTimeMillis()
        }

        matchTimer = object : CountDownTimer(Long.MAX_VALUE, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (isMatchTimerRunning) {
                    val elapsedTime = System.currentTimeMillis() - matchStartTime
                    _matchTimerValue.postValue(elapsedTime)
                }
            }

            override fun onFinish() {}
        }.start()

        addMatchEvent("Match timer started")
        sendTimerUpdate()  // Sync with Wear
    }

    private fun pauseMatchTimer() {
        matchTimer?.cancel()
        isMatchTimerRunning = false
        addMatchEvent("Match paused")
        sendTimerUpdate()  // Sync with Wear
    }

    fun resetMatchTimer() {
        matchTimer?.cancel()
        isMatchTimerRunning = false
        matchStartTime = System.currentTimeMillis()
        _matchTimerValue.value = 0L
        addMatchEvent("Match timer reset")
        sendTimerUpdate()  // Sync with Wear
    }

    // --- Keeper Timer Management ---
    fun setKeeperTimer(seconds: Long) {
        keeperTimerDuration = seconds * 1000
        _keeperTimerValue.value = keeperTimerDuration  // This will make it visible
        addMatchEvent("Keeper timer set to ${seconds} seconds")
    }

    fun startKeeperTimer() {
        keeperTimer?.cancel()
        isKeeperTimerRunning = true

        // Ensure the timer value is set so it becomes visible
        _keeperTimerValue.value = keeperTimerDuration

        keeperTimer = object : CountDownTimer(keeperTimerDuration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _keeperTimerValue.postValue(millisUntilFinished)
            }

            override fun onFinish() {
                _keeperTimerValue.postValue(0L)
                isKeeperTimerRunning = false
                triggerKeeperTimerExpired()
                showKeeperTimerExpired.postValue(Unit)
                addMatchEvent("Keeper timer expired!")
            }
        }.start()

        addMatchEvent("Keeper timer started (${keeperTimerDuration / 1000}s)")
        sendKeeperTimerUpdate(true)
    }

    fun resetKeeperTimer() {
        keeperTimer?.cancel()
        isKeeperTimerRunning = false
        _keeperTimerValue.value = 0L  // Set to 0 to hide
        vibrator?.cancel()
        sendKeeperTimerUpdate(false)
        addMatchEvent("Keeper timer reset")
    }

    // --- Match Events ---
    private fun addMatchEvent(event: String, team: Int? = null, player: String? = null) {
        val timeFormat = SimpleDateFormat("mm:ss", Locale.getDefault())
        val timestamp = timeFormat.format(Date(System.currentTimeMillis() - matchStartTime))

        val matchEvent = MatchEvent(timestamp, event, team, player)
        val currentEvents = _matchEvents.value?.toMutableList() ?: mutableListOf()
        currentEvents.add(0, matchEvent) // Add to beginning for reverse chronological order
        _matchEvents.postValue(currentEvents)
    }

    // --- End Match ---
    fun endMatch() {
        viewModelScope.launch {
            matchTimer?.cancel()
            isMatchTimerRunning = false

            val matchId = matchDao.insert(
                Match(
                    team1Score = _team1Score.value ?: 0,
                    team2Score = _team2Score.value ?: 0,
                    timestamp = System.currentTimeMillis()
                )
            )

            val allMatchPlayers = (_team1Players.value ?: emptyList()) + (_team2Players.value ?: emptyList())
            for (player in allMatchPlayers) {
                player.appearances++
                playerDao.update(player)
                matchDao.insertMatchPlayerCrossRef(MatchPlayerCrossRef(matchId.toInt(), player.playerId))
            }

            addMatchEvent("Match ended - Final Score: ${_team1Score.value} - ${_team2Score.value}")

            sendMatchStateUpdate(false)  // Sync match ended
            startNewMatch()
            sendResetUpdate()  // Sync full reset
        }
    }

    // --- Haptic Feedback ---
    private fun triggerHapticFeedback() {
        val effect = VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
        vibrator?.vibrate(effect)
    }

    private fun triggerKeeperTimerExpired() {
        // Strong pattern vibration for keeper timer
        val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
        val effect = VibrationEffect.createWaveform(pattern, -1)
        vibrator?.vibrate(effect)
    }

    // --- Data Synchronization with Wear OS ---
    private fun sendScoreUpdate() {
        wearDataSync.syncScores(
            _team1Score.value ?: 0,
            _team2Score.value ?: 0
        )
    }

    private fun sendTeamNamesUpdate() {
        wearDataSync.syncTeamNames(
            _team1Name.value ?: "TEAM 1",
            _team2Name.value ?: "TEAM 2"
        )
    }

    private fun sendKeeperTimerUpdate(isRunning: Boolean) {
        wearDataSync.syncKeeperTimer(
            _keeperTimerValue.value ?: 0L,
            isRunning
        )
    }

    private fun sendTimerUpdate() {
        wearDataSync.syncTimerState(
            _matchTimerValue.value ?: 0L,
            isMatchTimerRunning
        )
    }
    private fun sendMatchStateUpdate(isActive: Boolean) {
        wearDataSync.syncMatchState(isActive)
    }
    private fun sendResetUpdate() {
        // Send full state reset
        wearDataSync.syncFullState(
            team1Score = 0,
            team2Score = 0,
            team1Name = _team1Name.value ?: "TEAM 1",
            team2Name = _team2Name.value ?: "TEAM 2",
            timerMillis = 0L,
            timerRunning = false,
            keeperMillis = 0L,
            keeperRunning = false,
            matchActive = false
        )
    }

    override fun onCleared() {
        super.onCleared()
        matchTimer?.cancel()
        keeperTimer?.cancel()
        vibrator?.cancel()
    }
}