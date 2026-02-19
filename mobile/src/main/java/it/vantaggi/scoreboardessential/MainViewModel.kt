package it.vantaggi.scoreboardessential

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchDao
import it.vantaggi.scoreboardessential.database.MatchPlayerCrossRef
import it.vantaggi.scoreboardessential.database.MatchWithTeams
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerDao
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.repository.MatchRepository
import it.vantaggi.scoreboardessential.repository.MatchSettingsRepository
import it.vantaggi.scoreboardessential.repository.PlayerRepository
import it.vantaggi.scoreboardessential.repository.UserPreferencesRepository
import it.vantaggi.scoreboardessential.service.MatchTimerService
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager
import it.vantaggi.scoreboardessential.domain.models.MatchEvent
import it.vantaggi.scoreboardessential.domain.models.MatchReportData
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import it.vantaggi.scoreboardessential.utils.SingleLiveEvent
import it.vantaggi.scoreboardessential.ui.MatchHistoryUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The primary ViewModel for the application's main scoring screen.
 * It manages the state of the match, including scores, timer, team names/colors, players, and match events.
 * It also handles the connection to the Wear OS device and the synchronization of data.
 *
 * This ViewModel acts as the central coordinator for:
 * - Match state management (Score, Timer, Teams)
 * - Wear OS communication (Sending/Receiving updates)
 * - Persistence (Saving match history, Settings)
 * - Service binding (MatchTimerService)
 */
class MainViewModel(
    private val repository: MatchRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val matchSettingsRepository: MatchSettingsRepository,
    application: Application,
) : AndroidViewModel(application) {
    private val playerDao: PlayerDao = AppDatabase.getDatabase(application).playerDao()
    private val matchDao: MatchDao = AppDatabase.getDatabase(application).matchDao()
    private var matchTimerService: MatchTimerService? = null
    private var isServiceBound = false

    /**
     * LiveData indicating if the [MatchTimerService] is currently bound and accessible.
     */
    private val _serviceBindingStatus = MutableLiveData(false)
    val serviceBindingStatus: LiveData<Boolean> = _serviceBindingStatus

    // Undo Stacks
    /**
     * Represents a single goal action that can be undone.
     * @property teamId The ID of the team that scored.
     * @property playerId The optional ID of the player who scored.
     * @property timestamp When the goal occurred.
     */
    private data class GoalAction(
        val teamId: Int,
        val playerId: Int?,
        val timestamp: Long,
    )

    private val actionStack = java.util.Stack<GoalAction>()
    private val _canUndo = MutableLiveData(false)

    /** LiveData indicating if there are actions available to undo. */
    val canUndo: LiveData<Boolean> = _canUndo

    /** Component handling efficient data synchronization with Wear OS nodes. */
    val connectionManager = OptimizedWearDataSync(application)
    private val _isWearConnected = MutableLiveData(false)

    /** LiveData indicating if a Wear OS device is currently connected. */
    val isWearConnected: LiveData<Boolean> = _isWearConnected

    // LiveData for scores
    private val _team1Score = MutableLiveData(0)
    /** Current score for Team 1. */
    val team1Score: LiveData<Int> = _team1Score

    private val _team2Score = MutableLiveData(0)
    /** Current score for Team 2. */
    val team2Score: LiveData<Int> = _team2Score

    // LiveData for timer
    private val _matchTimerValue = MutableLiveData(0L)
    /** Current elapsed time of the match in milliseconds. */
    val matchTimerValue: LiveData<Long> = _matchTimerValue

    private val _isMatchTimerRunning = MutableLiveData(false)
    /** Status of the match timer (running or paused). */
    val isMatchTimerRunning: LiveData<Boolean> = _isMatchTimerRunning

    // LiveData for players
    private val _team1Players = MutableLiveData<List<PlayerWithRoles>>(emptyList())
    /** List of players currently assigned to Team 1 roster. */
    val team1Players: LiveData<List<PlayerWithRoles>> = _team1Players

    private val _team2Players = MutableLiveData<List<PlayerWithRoles>>(emptyList())
    /** List of players currently assigned to Team 2 roster. */
    val team2Players: LiveData<List<PlayerWithRoles>> = _team2Players

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                android.util.Log.d("MainViewModel", "Service connected!")
                val binder = service as MatchTimerService.MatchTimerBinder
                matchTimerService = binder.getService()
                isServiceBound = true
                _serviceBindingStatus.postValue(true)

                // Collect flows from service to update UI
                viewModelScope.launch {
                    binder.getService().matchTimerValue.collect {
                        _matchTimerValue.postValue(it)
                    }
                }
                viewModelScope.launch {
                    binder.getService().isMatchTimerRunning.collect { running ->
                        _isMatchTimerRunning.postValue(running)
                    }
                }
                viewModelScope.launch {
                    binder.getService().keeperTimerValue.collect {
                        _keeperTimerValue.postValue(it)
                    }
                }
                viewModelScope.launch {
                    var previousKeeperRunningState = _isKeeperTimerRunning.value ?: false
                    binder.getService().isKeeperTimerRunning.collect { isRunning ->
                        if (!isRunning && previousKeeperRunningState) {
                            showKeeperTimerExpired.postValue(Unit)
                            addMatchEvent("Keeper timer expired!")
                        }
                        _isKeeperTimerRunning.postValue(isRunning)
                        previousKeeperRunningState = isRunning
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                android.util.Log.d("MainViewModel", "Service disconnected!")
                matchTimerService = null
                isServiceBound = false
                _serviceBindingStatus.postValue(false)
            }
        }
    private val vibrator = ContextCompat.getSystemService(application, Vibrator::class.java)

    /** LiveData of all saved matches from history. */
    val allMatches: LiveData<List<MatchWithTeams>> = repository.allMatches.asLiveData()

    /** LiveData of all saved matches formatted for UI. */
    val matchHistory: LiveData<List<MatchHistoryUiState>> = repository.allMatches
        .map { matches ->
            matches.map { match ->
                val formatted = if (match.players.isNotEmpty()) {
                    "Players: ${match.players.joinToString(", ") { it.playerName }}"
                } else ""
                MatchHistoryUiState(match, formatted)
            }
        }
        .flowOn(Dispatchers.Default)
        .asLiveData()

    /*
     * Receives local broadcasts from the Wear OS listener service.
     * This handles updates coming from the watch when the app is in the background or foreground.
     */
    private val broadcastReceiver =
        object : android.content.BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    SimplifiedDataLayerListenerService.ACTION_SCORE_UPDATE -> {
                        val team1 = intent.getIntExtra(WearConstants.KEY_TEAM1_SCORE, 0)
                        val team2 = intent.getIntExtra(WearConstants.KEY_TEAM2_SCORE, 0)
                        Log.d("VM", "📥 Score update received from Wear")
                        _team1Score.value = team1
                        _team2Score.value = team2
                    }
                    SimplifiedDataLayerListenerService.ACTION_TIMER_UPDATE -> {
                        val millis = intent.getLongExtra(WearConstants.KEY_TIMER_MILLIS, 0L)
                        val running = intent.getBooleanExtra(WearConstants.KEY_TIMER_RUNNING, false)
                        Log.d("VM", "📥 Timer update received from Wear")

                        if (millis == 0L && !running) {
                            resetMatchTimer(fromRemote = true)
                        } else {
                            if (isServiceBound) {
                                matchTimerService?.updateMatchTimer(millis, fromRemote = true)
                                if (running != (_isMatchTimerRunning.value ?: false)) {
                                    if (running) matchTimerService?.startTimer() else matchTimerService?.pauseTimer()
                                }
                            }
                        }
                    }
                    SimplifiedDataLayerListenerService.ACTION_KEEPER_TIMER_UPDATE -> {
                        val millis = intent.getLongExtra(WearConstants.KEY_KEEPER_MILLIS, 0L)
                        val running = intent.getBooleanExtra(WearConstants.KEY_KEEPER_RUNNING, false)
                        if (!running && millis == 0L) {
                            resetKeeperTimer(fromRemote = true)
                        } else if (running != (_isKeeperTimerRunning.value ?: false)) {
                            if (running) {
                                if (millis > 0) {
                                    keeperTimerDuration = millis
                                    _keeperTimerValue.postValue(millis)
                                }
                                startKeeperTimer(fromRemote = true)
                            } else {
                                pauseKeeperTimer(fromRemote = true)
                            }
                        }
                    }
                    SimplifiedDataLayerListenerService.ACTION_MATCH_STATE_UPDATE -> {
                        val isActive = intent.getBooleanExtra(WearConstants.KEY_MATCH_ACTIVE, true)
                        if (!isActive) {
                            endMatch()
                        }
                    }
                }
            }
        }

    /**
     * Deletes a match from the history permanently.
     */
    fun deleteMatch(match: Match) =
        viewModelScope.launch {
            repository.deleteMatch(match)
        }

    class MainViewModelFactory(
        private val repository: MatchRepository,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val matchSettingsRepository: MatchSettingsRepository,
        private val application: Application,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(repository, userPreferencesRepository, matchSettingsRepository, application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }

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

    // Keeper Timer
    private val _keeperTimerValue = MutableLiveData(0L)
    val keeperTimerValue: LiveData<Long> = _keeperTimerValue
    private val _isKeeperTimerRunning = MutableLiveData(false)
    val isKeeperTimerRunning: LiveData<Boolean> = _isKeeperTimerRunning
    private var keeperTimerDuration = 300000L // 5 minutes default

    // All Players (for selection)
    private val _allPlayers = MutableLiveData<List<PlayerWithRoles>>(emptyList())
    val allPlayers: LiveData<List<PlayerWithRoles>> = _allPlayers

    // Match Events Log
    private val _matchEvents = MutableLiveData<List<MatchEvent>>(emptyList())
    val matchEvents: LiveData<List<MatchEvent>> = _matchEvents

    // UI Events
    val showOnboarding = SingleLiveEvent<Unit>()
    val showSelectScorerDialog = SingleLiveEvent<Pair<Int, List<PlayerWithRoles>>>()
    val showPlayerSelectionDialog = SingleLiveEvent<Int>()
    val showKeeperTimerExpired = SingleLiveEvent<Unit>()
    val shareMatchReportData = SingleLiveEvent<MatchReportData>()
    val showOnboardingTutorial = SingleLiveEvent<Unit>()

    // Data Client for Wear OS sync
    private val dataClient: DataClient = Wearable.getDataClient(application)

    private val sharedPreferences: SharedPreferences = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    // Current Match ID
    private var currentMatchId: Long? = null

    init {
        viewModelScope.launch {
            matchSettingsRepository.getSettingsFlow().collect { settings ->
                if (_team1Name.value != settings.team1Name) setTeam1Name(settings.team1Name)
                if (_team2Name.value != settings.team2Name) setTeam2Name(settings.team2Name)
                if (_team1Color.value != settings.team1Color) setTeamColor(1, settings.team1Color)
                if (_team2Color.value != settings.team2Color) setTeamColor(2, settings.team2Color)

                val currentDurationSeconds = keeperTimerDuration / 1000
                if (currentDurationSeconds != settings.keeperTimerDuration) {
                    setKeeperTimer(settings.keeperTimerDuration)
                }
            }
        }

        loadAllPlayers()
        startNewMatch()
        bindService()
        checkIfOnboardingIsNeeded()

        viewModelScope.launch {
            connectionManager.connectionState.collect { state ->
                when (state) {
                    is it.vantaggi.scoreboardessential.shared.communication.ConnectionState.Connected -> {
                        Log.d("App", "✓ Connected to ${state.nodeCount} device(s)")
                        _isWearConnected.value = true
                        syncAllDataToWear()
                    }
                    is it.vantaggi.scoreboardessential.shared.communication.ConnectionState.Disconnected -> {
                        Log.d("App", "✗ Not connected")
                        _isWearConnected.value = false
                    }
                    is it.vantaggi.scoreboardessential.shared.communication.ConnectionState.Error -> {
                        Log.e("App", "✗ Error: ${state.message}")
                        _isWearConnected.value = false
                    }
                }
            }
        }

        val filter =
            android.content.IntentFilter().apply {
                addAction(SimplifiedDataLayerListenerService.ACTION_SCORE_UPDATE)
                addAction(SimplifiedDataLayerListenerService.ACTION_TIMER_UPDATE)
                addAction(SimplifiedDataLayerListenerService.ACTION_TEAM_NAMES_UPDATE)
                addAction(SimplifiedDataLayerListenerService.ACTION_KEEPER_TIMER_UPDATE)
                addAction(SimplifiedDataLayerListenerService.ACTION_MATCH_STATE_UPDATE)
                addAction(SimplifiedDataLayerListenerService.ACTION_REQUEST_SYNC)
            }
        androidx.localbroadcastmanager.content.LocalBroadcastManager
            .getInstance(application)
            .registerReceiver(broadcastReceiver, filter)
    }

    private fun checkIfOnboardingIsNeeded() {
        val onboardingCompleted = sharedPreferences.getBoolean("onboarding_completed", false)
        if (!onboardingCompleted) {
            showOnboarding.postValue(Unit)
        }
    }

    fun onOnboardingFinished() {
        sharedPreferences.edit().putBoolean("onboarding_completed", true).apply()
    }

    private fun bindService() {
        android.util.Log.d("MainViewModel", "Attempting to bind MatchTimerService")
        Intent(getApplication(), MatchTimerService::class.java).also { intent ->
            getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
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
        updateScore(0, 0)
        _matchEvents.value = emptyList()
        matchTimerService?.resetTimer()
        if (isServiceBound) {
            matchTimerService?.resetKeeperTimer()
        }

        actionStack.clear()
        _canUndo.postValue(false)

        addMatchEvent("New match ready - press START to begin")
        sendMatchStateUpdate(true)
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

    fun setTeamColor(
        team: Int,
        color: Int,
    ) {
        if (team == 1) {
            _team1Color.value = color
        } else {
            _team2Color.value = color
        }
        sendTeamColorUpdate(team, color)
    }

    // --- Player Management ---
    fun addPlayerToTeam(
        playerWithRoles: PlayerWithRoles,
        teamId: Int,
    ) {
        if (teamId == 1) {
            _team1Players.value = _team1Players.value?.plus(playerWithRoles)
        } else {
            _team2Players.value = _team2Players.value?.plus(playerWithRoles)
        }
        val teamName = if (teamId == 1) _team1Name.value else _team2Name.value
        addMatchEvent("${playerWithRoles.player.playerName} added to $teamName", team = teamId)
    }

    fun removePlayerFromTeam(
        playerWithRoles: PlayerWithRoles,
        teamId: Int,
    ) {
        if (teamId == 1) {
            _team1Players.value = _team1Players.value?.minus(playerWithRoles)
        } else {
            _team2Players.value = _team2Players.value?.minus(playerWithRoles)
        }
    }

    fun createNewPlayer(
        name: String,
        roleIds: List<Int>,
    ) {
        viewModelScope.launch {
            val player =
                Player(
                    playerName = name,
                    appearances = 0,
                    goals = 0,
                )
            val playerRepository = PlayerRepository(playerDao)
            playerRepository.insertPlayerWithRoles(player, roleIds)
            addMatchEvent("New player created: $name")
        }
    }

    // --- Score Management ---
    fun updateScore(
        team1: Int,
        team2: Int,
    ) {
        _team1Score.value = team1
        _team2Score.value = team2

        viewModelScope.launch {
            val data =
                mapOf(
                    WearConstants.KEY_TEAM1_SCORE to team1,
                    WearConstants.KEY_TEAM2_SCORE to team2,
                )
            connectionManager.sendData(
                path = WearConstants.PATH_SCORE,
                data = data,
                urgent = true,
            )
        }
    }

    fun addScore(teamId: Int) {
        val currentTeam1Score = _team1Score.value ?: 0
        val currentTeam2Score = _team2Score.value ?: 0

        if (teamId == 1) {
            updateScore(currentTeam1Score + 1, currentTeam2Score)
        } else {
            updateScore(currentTeam1Score, currentTeam2Score + 1)
        }

        triggerHapticFeedback()

        val players = if (teamId == 1) team1Players.value else team2Players.value
        if (!players.isNullOrEmpty()) {
            showSelectScorerDialog.postValue(Pair(teamId, players))
        } else {
            addScorer(teamId, null) // No player to select, just log the goal
        }
    }

    fun subtractScore(teamId: Int) {
        val currentTeam1Score = _team1Score.value ?: 0
        val currentTeam2Score = _team2Score.value ?: 0
        var newTeam1Score = currentTeam1Score
        var newTeam2Score = currentTeam2Score

        if (teamId == 1) {
            newTeam1Score = (currentTeam1Score - 1).coerceAtLeast(0)
        } else {
            newTeam2Score = (currentTeam2Score - 1).coerceAtLeast(0)
        }

        if (newTeam1Score != currentTeam1Score || newTeam2Score != currentTeam2Score) {
            updateScore(newTeam1Score, newTeam2Score)
            triggerHapticFeedback()
            val teamName = if (teamId == 1) _team1Name.value else _team2Name.value
            addMatchEvent("Score correction for $teamName", team = teamId)
        }
    }

    fun addScorer(
        team: Int,
        playerWithRoles: PlayerWithRoles?,
    ) {
        viewModelScope.launch {
            val teamName = if (team == 1) _team1Name.value else _team2Name.value
            if (playerWithRoles != null) {
                // A specific player scored
                playerWithRoles.player.goals++
                playerDao.update(playerWithRoles.player)

                val rolesString = playerWithRoles.roles.joinToString(", ") { it.name }
                addMatchEvent("Goal", team = team, player = playerWithRoles.player.playerName, playerRole = rolesString)

                // Track for Undo
                actionStack.push(GoalAction(team, playerWithRoles.player.playerId, System.currentTimeMillis()))
            } else {
                // No specific player, just log a goal for the team
                addMatchEvent("Goal", team = team, player = teamName)

                // Track for Undo (null playerId)
                actionStack.push(GoalAction(team, null, System.currentTimeMillis()))
            }
            _canUndo.postValue(true)
        }
    }

    fun undoLastGoal() {
        if (actionStack.isNotEmpty()) {
            val lastAction = actionStack.pop()
            _canUndo.postValue(actionStack.isNotEmpty())

            viewModelScope.launch {
                // 1. Revert Score
                if (lastAction.teamId == 1) {
                    val current = _team1Score.value ?: 0
                    if (current > 0) updateScore(current - 1, _team2Score.value ?: 0)
                } else {
                    val current = _team2Score.value ?: 0
                    if (current > 0) updateScore(_team1Score.value ?: 0, current - 1)
                }

                // 2. Revert Player Stats if needed
                lastAction.playerId?.let { playerId ->
                    val playerFlow = playerDao.getPlayerWithRoles(playerId)
                    // We need to collect once to get the player
                    // Since we are in coroutine, we can't easily wait for Flow value without collection
                    // Ideally DAO should have suspend fun getPlayer(id)
                    // For now, we assume we might need to fetch from our local list if possible or just log it
                    // Optimization: add suspend getPlayer to DAO for QoL

                    _allPlayers.value?.find { it.player.playerId == playerId }?.let { p ->
                        if (p.player.goals > 0) {
                            p.player.goals--
                            playerDao.update(p.player)
                        }
                    }
                }

                // 3. Remove from Match Events
                // We remove the first "Goal" event for this team/player
                val currentEvents = _matchEvents.value?.toMutableList() ?: return@launch
                val index =
                    currentEvents.indexOfFirst {
                        it.event == "Goal" &&
                            it.team == lastAction.teamId &&
                            (lastAction.playerId == null || it.player != null) // Simplistic matching
                    }
                if (index != -1) {
                    currentEvents.removeAt(index)
                    _matchEvents.postValue(currentEvents)
                }

                addMatchEvent("Undo: Goal removed", team = lastAction.teamId)
            }
        }
    }

    // --- Match Timer Management ---
    fun startStopMatchTimer() {
        android.util.Log.d("MainViewModel", "startStopMatchTimer called, service bound: $isServiceBound")
        if (!isServiceBound) {
            android.util.Log.e("MainViewModel", "Timer service NOT bound! Cannot start timer.")
            return
        }
        matchTimerService?.let {
            if (it.isMatchTimerRunning.value) {
                it.pauseTimer()
            } else {
                it.startTimer()
            }
        }
    }

    fun resetMatchTimer(fromRemote: Boolean = false) {
        if (fromRemote && isServiceBound) {
            matchTimerService?.resetTimer(fromRemote = true)
        } else {
            matchTimerService?.resetTimer()
        }
    }

    // --- Keeper Timer Management ---
    fun setKeeperTimer(seconds: Long) {
        keeperTimerDuration = seconds * 1000
        _keeperTimerValue.value = keeperTimerDuration
        addMatchEvent("Keeper timer set to $seconds seconds")
        // Sync default duration to Wear
        sendKeeperTimerUpdate(false, keeperTimerDuration)
    }

    fun startKeeperTimer(fromRemote: Boolean = false) {
        if (!isServiceBound) return
        matchTimerService?.startKeeperTimer(keeperTimerDuration, fromRemote)
        addMatchEvent("Keeper timer started (${keeperTimerDuration / 1000}s)")
    }

    fun pauseKeeperTimer(fromRemote: Boolean = false) {
        if (!isServiceBound) return
        matchTimerService?.pauseKeeperTimer(fromRemote)
    }

    fun resetKeeperTimer(fromRemote: Boolean = false) {
        if (!isServiceBound) return
        matchTimerService?.resetKeeperTimer(fromRemote)
        addMatchEvent("Keeper timer reset")
    }

    // --- Match Events ---
    private fun addMatchEvent(
        event: String,
        team: Int? = null,
        player: String? = null,
        playerRole: String? = null,
    ) {
        val timeFormat = SimpleDateFormat("mm:ss", Locale.getDefault())
        val timestamp = timeFormat.format(Date(matchTimerValue.value ?: 0L))

        val matchEvent = MatchEvent(timestamp, event, team, player, playerRole)
        val currentEvents = _matchEvents.value?.toMutableList() ?: mutableListOf()
        currentEvents.add(0, matchEvent) // Add to beginning for reverse chronological order
        _matchEvents.postValue(currentEvents)
    }

    // --- End Match ---
    fun endMatch(): Boolean {
        if (team1Score.value == 0 && team2Score.value == 0 && matchTimerValue.value == 0L) {
            return false // Match not started, do not save
        }

        viewModelScope.launch {
            if (isServiceBound) {
                matchTimerService?.stopTimer()
            }

            val matchId =
                matchDao.insert(
                    Match(
                        team1Id = 1, // Default team 1 ID
                        team2Id = 2, // Default team 2 ID
                        team1Score = team1Score.value ?: 0,
                        team2Score = team2Score.value ?: 0,
                        timestamp = System.currentTimeMillis(),
                    ),
                )

            val allMatchPlayers = (team1Players.value ?: emptyList()) + (team2Players.value ?: emptyList())

            val playersToUpdate =
                allMatchPlayers.map {
                    it.player.apply { appearances++ }
                }
            playerDao.updatePlayers(playersToUpdate)

            val matchPlayerCrossRefs =
                allMatchPlayers.map {
                    MatchPlayerCrossRef(matchId.toInt(), it.player.playerId)
                }
            matchDao.insertMatchPlayerCrossRefs(matchPlayerCrossRefs)

            addMatchEvent("Match ended - Final Score: ${team1Score.value} - ${team2Score.value}")

            sendMatchStateUpdate(false)
            startNewMatch()
            sendResetUpdate()
        }
        return true
    }

    // --- Haptic Feedback ---
    private fun triggerHapticFeedback() {
        val effect = VibrationEffect.createWaveform(HapticFeedbackManager.PATTERN_TICK, -1)
        vibrator?.vibrate(effect)
    }

// --- Data Synchronization with Wear OS ---
    private fun sendTeamNamesUpdate() {
        viewModelScope.launch {
            val data =
                mapOf(
                    WearConstants.KEY_TEAM1_NAME to (_team1Name.value ?: "TEAM 1"),
                    WearConstants.KEY_TEAM2_NAME to (_team2Name.value ?: "TEAM 2"),
                )
            connectionManager.sendData(
                path = WearConstants.PATH_TEAM_NAMES,
                data = data,
            )
        }
    }

    private fun sendKeeperTimerUpdate(
        isRunning: Boolean,
        millis: Long? = null,
    ) {
        viewModelScope.launch {
            val data =
                mapOf(
                    WearConstants.KEY_KEEPER_MILLIS to
                        (millis ?: (_keeperTimerValue.value ?: 0L)),
                    WearConstants.KEY_KEEPER_RUNNING to isRunning,
                )
            connectionManager.sendData(
                path = WearConstants.PATH_KEEPER_TIMER,
                data = data,
            )
        }
    }

    private fun sendMatchStateUpdate(isActive: Boolean) {
        viewModelScope.launch {
            val data =
                mapOf(
                    WearConstants.KEY_MATCH_ACTIVE to isActive,
                )
            connectionManager.sendData(
                path = WearConstants.PATH_MATCH_STATE,
                data = data,
            )
        }
    }

    private fun sendTeamColorUpdate(
        team: Int,
        color: Int,
    ) {
        viewModelScope.launch {
            val path =
                if (team ==
                    1
                ) {
                    WearConstants.PATH_TEAM1_COLOR
                } else {
                    WearConstants.PATH_TEAM2_COLOR
                }
            val data = mapOf("color" to color)
            connectionManager.sendData(path = path, data = data)
        }
    }

    private fun sendResetUpdate() {
        updateScore(0, 0)
        sendTeamNamesUpdate()
        viewModelScope.launch {
            val data =
                mapOf(
                    WearConstants.KEY_TIMER_MILLIS to 0L,
                    WearConstants.KEY_TIMER_RUNNING to false,
                )
            connectionManager.sendData(
                path = WearConstants.PATH_TIMER_STATE,
                data = data,
            )
        }
    }

    private fun syncAllDataToWear() {
        viewModelScope.launch {
            Log.d("MainViewModel", "Syncing all data to Wear OS")
            // 1. Scores
            updateScore(_team1Score.value ?: 0, _team2Score.value ?: 0)

            // 2. Names
            sendTeamNamesUpdate()

            // 3. Colors
            _team1Color.value?.let { sendTeamColorUpdate(1, it) }
            _team2Color.value?.let { sendTeamColorUpdate(2, it) }

            // 4. Match State
            sendMatchStateUpdate(true)

            // 5. Timer State (Manual construction to ensure current VM state is sent)
            val timerData =
                mapOf(
                    WearConstants.KEY_TIMER_MILLIS to (_matchTimerValue.value ?: 0L),
                    WearConstants.KEY_TIMER_RUNNING to (_isMatchTimerRunning.value ?: false),
                )
            connectionManager.sendData(
                path = WearConstants.PATH_TIMER_STATE,
                data = timerData,
            )

            // 6. Keeper Timer State
            sendKeeperTimerUpdate(_isKeeperTimerRunning.value ?: false, _keeperTimerValue.value)
        }
    }

    fun shareMatchResults() {
        val data = MatchReportData(
            team1Name = _team1Name.value ?: "TEAM 1",
            team1Score = _team1Score.value ?: 0,
            team1Color = _team1Color.value,
            team1Players = _team1Players.value ?: emptyList(),
            team2Name = _team2Name.value ?: "TEAM 2",
            team2Score = _team2Score.value ?: 0,
            team2Color = _team2Color.value,
            team2Players = _team2Players.value ?: emptyList(),
            matchEvents = _matchEvents.value ?: emptyList()
        )
        shareMatchReportData.postValue(data)
    }

    override fun onCleared() {
        vibrator?.cancel()
        try {
            if (isServiceBound) {
                getApplication<Application>().unbindService(serviceConnection)
                isServiceBound = false
            }
            androidx.localbroadcastmanager.content.LocalBroadcastManager
                .getInstance(getApplication())
                .unregisterReceiver(broadcastReceiver)
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error unbinding service", e)
        }
        super.onCleared()
    }
}
