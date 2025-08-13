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

        listenForScoreUpdates()
        listenForTimerEvents()
        loadAllPlayers()
        startNewMatch()
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
        // Reset everything for a new match
        _team1Score.value = 0
        _team2Score.value = 0
        _matchEvents.value = emptyList()
        matchStartTime = System.currentTimeMillis()
        _matchTimerValue.value = 0L
        // Don't auto-start timer - let user control it
        addMatchEvent("New match ready - press start to begin timer")
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
        val putDataMapReq = PutDataMapRequest.create("/scorer_info").apply {
            dataMap.putString("scorer_name", name)
            dataMap.putString("scorer_role", role)
            dataMap.putInt("team", team)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        dataClient.putDataItem(putDataReq)
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
        matchTimer?.cancel()
        isMatchTimerRunning = true

        matchTimer = object : CountDownTimer(Long.MAX_VALUE, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val elapsedTime = System.currentTimeMillis() - matchStartTime
                _matchTimerValue.postValue(elapsedTime)
            }

            override fun onFinish() {
                // Won't finish as we use MAX_VALUE
            }
        }.start()
    }

    private fun pauseMatchTimer() {
        matchTimer?.cancel()
        isMatchTimerRunning = false
        addMatchEvent("Match paused")
    }

    fun resetMatchTimer() {
        matchTimer?.cancel()
        isMatchTimerRunning = false
        matchStartTime = System.currentTimeMillis()
        _matchTimerValue.value = 0L
        addMatchEvent("Match timer reset")
    }

    // --- Keeper Timer Management ---
    fun setKeeperTimer(seconds: Long) {
        keeperTimerDuration = seconds * 1000
        _keeperTimerValue.value = keeperTimerDuration
    }

    fun startKeeperTimer() {
        keeperTimer?.cancel()
        isKeeperTimerRunning = true

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
        _keeperTimerValue.value = keeperTimerDuration
        vibrator?.cancel()
        sendKeeperTimerUpdate(false)
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
            // Save match to database
            val matchId = matchDao.insert(
                Match(
                    team1Score = _team1Score.value ?: 0,
                    team2Score = _team2Score.value ?: 0,
                    timestamp = System.currentTimeMillis()
                )
            )

            // Update player statistics
            val allMatchPlayers = (_team1Players.value ?: emptyList()) + (_team2Players.value ?: emptyList())
            for (player in allMatchPlayers) {
                player.appearances++
                playerDao.update(player)
                matchDao.insertMatchPlayerCrossRef(MatchPlayerCrossRef(matchId.toInt(), player.playerId))
            }

            // Notify about match end
            addMatchEvent("Match ended - Final Score: ${_team1Score.value} - ${_team2Score.value}")

            // Reset for new match
            startNewMatch()
            sendResetUpdate()
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
        val putDataMapReq = PutDataMapRequest.create("/score_update").apply {
            dataMap.putInt("team1_score", _team1Score.value ?: 0)
            dataMap.putInt("team2_score", _team2Score.value ?: 0)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        dataClient.putDataItem(putDataReq)
    }

    private fun sendTeamNamesUpdate() {
        val putDataMapReq = PutDataMapRequest.create("/team_names").apply {
            // Use the Elvis operator (?:) to provide a default value if .value is null
            dataMap.putString("team1_name", _team1Name.value ?: "TEAM 1")
            dataMap.putString("team2_name", _team2Name.value ?: "TEAM 2")
        }
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        dataClient.putDataItem(putDataReq)
    }

    private fun sendKeeperTimerUpdate(isRunning: Boolean) {
        val putDataMapReq = PutDataMapRequest.create("/keeper_timer").apply {
            dataMap.putBoolean("is_running", isRunning)
            dataMap.putLong("duration", keeperTimerDuration)
        }
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        dataClient.putDataItem(putDataReq)
    }

    private fun sendResetUpdate() {
        val putDataMapReq = PutDataMapRequest.create("/reset_match").apply {
            dataMap.putBoolean("reset", true)
            dataMap.putLong("timestamp", System.currentTimeMillis())
        }
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        dataClient.putDataItem(putDataReq)
    }

    override fun onCleared() {
        super.onCleared()
        matchTimer?.cancel()
        keeperTimer?.cancel()
        vibrator?.cancel()
    }
}