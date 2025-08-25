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
import com.example.scoreboardessential.repository.PlayerRepository
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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import com.example.scoreboardessential.repository.MatchRepository

data class MatchEvent(
    val timestamp: String,
    val event: String,
    val team: Int? = null,
    val player: String? = null,
    val playerRole: String? = null
)

class MainViewModel(private val repository: MatchRepository, application: Application) : AndroidViewModel(application) {

    private val playerDao: PlayerDao
    private val matchDao: MatchDao
    private val vibrator = ContextCompat.getSystemService(application, Vibrator::class.java)

    private val wearDataSync = WearDataSync(application)
    
    val allMatches: LiveData<List<MatchWithTeams>> = repository.allMatches.asLiveData()
    
    fun deleteMatch(match: Match) = viewModelScope.launch {
        repository.deleteMatch(match)
    }

    class MainViewModelFactory(private val repository: MatchRepository, private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(repository, application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }


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
    private val _team1Players = MutableLiveData<List<PlayerWithRoles>>(emptyList())
    val team1Players: LiveData<List<PlayerWithRoles>> = _team1Players

    private val _team2Players = MutableLiveData<List<PlayerWithRoles>>(emptyList())
    val team2Players: LiveData<List<PlayerWithRoles>> = _team2Players

    // All Players (for selection)
    private val _allPlayers = MutableLiveData<List<PlayerWithRoles>>(emptyList())
    val allPlayers: LiveData<List<PlayerWithRoles>> = _allPlayers

    // Match Events Log
    private val _matchEvents = MutableLiveData<List<MatchEvent>>(emptyList())
    val matchEvents: LiveData<List<MatchEvent>> = _matchEvents

    // UI Events
    val showSelectScorerDialog = SingleLiveEvent<Int>()
    val showPlayerSelectionDialog = SingleLiveEvent<Int>()
    val showKeeperTimerExpired = SingleLiveEvent<Unit>()
    val showColorPickerDialog = SingleLiveEvent<Int>()

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
        loadActiveMatch()
    }

    private fun loadActiveMatch() {
        viewModelScope.launch {
            repository.getActiveMatch().first().let { matchWithTeams ->
                if (matchWithTeams != null) {
                    restoreMatchState(matchWithTeams)
                } else {
                    prepareNewMatch()
                }
                // Sync with Wear OS after loading state
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
    }

    private fun restoreMatchState(matchWithTeams: MatchWithTeams) {
        viewModelScope.launch {
            val match = matchWithTeams.match
            currentMatchId = match.matchId
            _team1Score.value = match.team1Score
            _team2Score.value = match.team2Score
            _team1Name.value = matchWithTeams.team1?.name ?: "TEAM 1"
            _team2Name.value = matchWithTeams.team2?.name ?: "TEAM 2"

            _team1Players.value = playerDao.getPlayersForTeamInMatch(match.matchId, match.team1Id).first()
            _team2Players.value = playerDao.getPlayersForTeamInMatch(match.matchId, match.team2Id).first()

            addMatchEvent("Partita in corso ripristinata")
        }
    }

    private fun prepareNewMatch() {
        _team1Score.value = 0
        _team2Score.value = 0
        _team1Name.value = "TEAM 1"
        _team2Name.value = "TEAM 2"
        _matchEvents.value = emptyList()
        _matchTimerValue.value = 0L
        matchTimer?.cancel()
        isMatchTimerRunning = false
        currentMatchId = null

        addMatchEvent("Pronto per una nuova partita")
        sendMatchStateUpdate(false)
        sendScoreUpdate()
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
                // Sync players to wear whenever the player list updates
                syncTeamPlayersToWear()
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
        sendTeamColorUpdate(team, color)
    }

    fun requestTeamColorChange(team: Int) {
        val teamId = if (team == 1) 1 else 2
        showColorPickerDialog.postValue(teamId)
    }

    // --- Player Management ---
    fun addPlayerToTeam(playerWithRoles: PlayerWithRoles, teamId: Int) {
        viewModelScope.launch {
            if (teamId == 1) {
                val currentPlayers = _team1Players.value?.toMutableList() ?: mutableListOf()
                if (!currentPlayers.any { it.player.playerId == playerWithRoles.player.playerId }) {
                    currentPlayers.add(playerWithRoles)
                    _team1Players.postValue(currentPlayers)
                    addMatchEvent("${playerWithRoles.player.playerName} added to ${_team1Name.value}", team = 1)
                    syncTeamPlayersToWear()
                }
            } else {
                val currentPlayers = _team2Players.value?.toMutableList() ?: mutableListOf()
                if (!currentPlayers.any { it.player.playerId == playerWithRoles.player.playerId }) {
                    currentPlayers.add(playerWithRoles)
                    _team2Players.postValue(currentPlayers)
                    addMatchEvent("${playerWithRoles.player.playerName} added to ${_team2Name.value}", team = 2)
                    syncTeamPlayersToWear()
                }
            }
        }
    }

    fun removePlayerFromTeam(playerWithRoles: PlayerWithRoles, teamId: Int) {
        if (teamId == 1) {
            val currentPlayers = _team1Players.value?.toMutableList() ?: mutableListOf()
            currentPlayers.removeAll { it.player.playerId == playerWithRoles.player.playerId }
            _team1Players.postValue(currentPlayers)
        } else {
            val currentPlayers = _team2Players.value?.toMutableList() ?: mutableListOf()
            currentPlayers.removeAll { it.player.playerId == playerWithRoles.player.playerId }
            _team2Players.postValue(currentPlayers)
        }
        syncTeamPlayersToWear()
    }

    fun createNewPlayer(name: String, roleIds: List<Int>) {
        viewModelScope.launch {
            val player = Player(
                playerName = name,
                appearances = 0,
                goals = 0
            )
            val playerRepository = PlayerRepository(playerDao)
            playerRepository.insertPlayerWithRoles(player, roleIds)
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
            val playerWithRoles = players?.find { it.player.playerName == playerName }

            if (playerWithRoles != null) {
                playerWithRoles.player.goals++
                playerDao.update(playerWithRoles.player)

                val teamName = if (team == 1) _team1Name.value else _team2Name.value
                val rolesString = playerWithRoles.roles.joinToString(", ")
                addMatchEvent("GOAL! $playerName (${teamName})", team = team, player = playerName, playerRole = rolesString)

                // Invia info al Wear con ruolo
                sendScorerToWear(playerName, playerWithRoles.roles.map { it.name }, team)
            }
        }
    }

    private fun sendScorerToWear(name: String, roles: List<String>, team: Int) {
        wearDataSync.syncScorerSelected(name, roles, team)
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
    private fun addMatchEvent(event: String, team: Int? = null, player: String? = null, playerRole: String? = null) {
        val timeFormat = SimpleDateFormat("mm:ss", Locale.getDefault())
        val timestamp = timeFormat.format(Date(System.currentTimeMillis() - matchStartTime))

        val matchEvent = MatchEvent(timestamp, event, team, player, playerRole)
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
                    team1Id = 1, // Default team 1 ID
                    team2Id = 2, // Default team 2 ID
                    team1Score = _team1Score.value ?: 0,
                    team2Score = _team2Score.value ?: 0,
                    timestamp = System.currentTimeMillis()
                )
            )

            val allMatchPlayers = (_team1Players.value ?: emptyList()) + (_team2Players.value ?: emptyList())
            for (playerWithRoles in allMatchPlayers) {
                playerWithRoles.player.appearances++
                playerDao.update(playerWithRoles.player)
                matchDao.insertMatchPlayerCrossRef(MatchPlayerCrossRef(matchId.toInt(), playerWithRoles.player.playerId))
            }

            addMatchEvent("Match ended - Final Score: ${_team1Score.value} - ${_team2Score.value}")

            sendMatchStateUpdate(false)  // Sync match ended
            startNewMatch()
            sendResetUpdate()  // Sync full reset
        }
    }

    // --- Haptic Feedback ---
    private fun triggerHapticFeedback() {
        val effect = VibrationEffect.createWaveform(HapticFeedbackManager.PATTERN_TICK, -1)
        vibrator?.vibrate(effect)
    }

    private fun triggerKeeperTimerExpired() {
        // Strong pattern vibration for keeper timer
        val effect = VibrationEffect.createWaveform(HapticFeedbackManager.PATTERN_ALERT, -1)
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

    private fun sendTeamColorUpdate(team: Int, color: Int) {
        val teamName = if (team == 1) _team1Name.value else _team2Name.value
        wearDataSync.syncTeamColor(team, color)
    }

    private fun sendResetUpdate() {
        wearDataSync.syncFullState(
            team1Score = 0,
            team2Score = 0,
            team1Name = "TEAM 1",
            team2Name = "TEAM 2",
            timerMillis = 0L,
            timerRunning = false,
            keeperMillis = 0L,
            keeperRunning = false,
            matchActive = true
        )
    }

    private fun syncTeamPlayersToWear() {
        val team1PlayerData = _team1Players.value?.map { playerWithRoles ->
            PlayerData(
                id = playerWithRoles.player.playerId,
                name = playerWithRoles.player.playerName,
                roles = playerWithRoles.roles.map { it.name },
                goals = playerWithRoles.player.goals,
                appearances = playerWithRoles.player.appearances
            )
        } ?: emptyList()

        val team2PlayerData = _team2Players.value?.map { playerWithRoles ->
            PlayerData(
                id = playerWithRoles.player.playerId,
                name = playerWithRoles.player.playerName,
                roles = playerWithRoles.roles.map { it.name },
                goals = playerWithRoles.player.goals,
                appearances = playerWithRoles.player.appearances
            )
        } ?: emptyList()

        wearDataSync.syncTeamPlayers(team1PlayerData, team2PlayerData)

        // Also sync all available players for player selection
        val allPlayerData = _allPlayers.value?.map { playerWithRoles ->
            PlayerData(
                id = playerWithRoles.player.playerId,
                name = playerWithRoles.player.playerName,
                roles = playerWithRoles.roles.map { it.name },
                goals = playerWithRoles.player.goals,
                appearances = playerWithRoles.player.appearances
            )
        } ?: emptyList()

        wearDataSync.syncPlayerList(allPlayerData)
    }

    // --- Match Events ---

    override fun onCleared() {
        matchTimer?.cancel()
        keeperTimer?.cancel()
        vibrator?.cancel()
        super.onCleared()
    }
}