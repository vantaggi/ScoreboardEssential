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
import it.vantaggi.scoreboardessential.domain.model.SportType
import it.vantaggi.scoreboardessential.domain.scoring.ScoreBoardState
import it.vantaggi.scoreboardessential.domain.scoring.ScoringStrategy
import it.vantaggi.scoreboardessential.domain.scoring.SoccerScoringStrategy
import it.vantaggi.scoreboardessential.domain.scoring.PadelScoringStrategy
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.repository.MatchRepository
import it.vantaggi.scoreboardessential.repository.MatchSettingsRepository
import it.vantaggi.scoreboardessential.repository.PlayerRepository
import it.vantaggi.scoreboardessential.repository.UserPreferencesRepository
import it.vantaggi.scoreboardessential.service.MatchTimerService
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager
import it.vantaggi.scoreboardessential.utils.SingleLiveEvent
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Stack

data class MatchEvent(
    val timestamp: String,
    val event: String,
    val team: Int? = null,
    val player: String? = null,
    val playerRole: String? = null,
)

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
    private val _serviceBindingStatus = MutableLiveData(false)
    val serviceBindingStatus: LiveData<Boolean> = _serviceBindingStatus

    // Undo Stacks
    private data class GoalAction(
    val teamId: Int,
    val playerId: Int?,
    val timestamp: Long,
    val previousState: ScoreBoardState? = null
)
    private val actionStack = java.util.Stack<GoalAction>()
    private val _canUndo = MutableLiveData(false)
    val canUndo: LiveData<Boolean> = _canUndo

    val connectionManager = OptimizedWearDataSync(application)
    private val _isWearConnected = MutableLiveData(false)
    val isWearConnected: LiveData<Boolean> = _isWearConnected

    // LiveData for scores (Changed to String for generic support)
    private val _team1Score = MutableLiveData("0")
    val team1Score: LiveData<String> = _team1Score
    private val _team2Score = MutableLiveData("0")
    val team2Score: LiveData<String> = _team2Score
    
    // Detailed Score Board State
    private val _scoreBoardState = MutableLiveData<ScoreBoardState>()
    val scoreBoardState: LiveData<ScoreBoardState> = _scoreBoardState

    // Current Strategy
    private var scoringStrategy: ScoringStrategy = SoccerScoringStrategy()
    private val _currentSport = MutableLiveData(SportType.SOCCER)
    val currentSport: LiveData<SportType> = _currentSport

    // LiveData for timer
    private val _matchTimerValue = MutableLiveData(0L)
    val matchTimerValue: LiveData<Long> = _matchTimerValue
    private val _isMatchTimerRunning = MutableLiveData(false)
    val isMatchTimerRunning: LiveData<Boolean> = _isMatchTimerRunning

    // LiveData for players
    private val _team1Players = MutableLiveData<List<PlayerWithRoles>>(emptyList())
    val team1Players: LiveData<List<PlayerWithRoles>> = _team1Players
    private val _team2Players = MutableLiveData<List<PlayerWithRoles>>(emptyList())
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

    val allMatches: LiveData<List<MatchWithTeams>> = repository.allMatches.asLiveData()

    private val broadcastReceiver =
        object : android.content.BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    SimplifiedDataLayerListenerService.ACTION_SCORE_UPDATE -> {
                        val team1 = intent.getIntExtra(SimplifiedDataLayerListenerService.EXTRA_TEAM1_SCORE, 0)
                        val team2 = intent.getIntExtra(SimplifiedDataLayerListenerService.EXTRA_TEAM2_SCORE, 0)
                        Log.d("VM", "📥 Score from Wear: T1=$team1, T2=$team2")
                        _team1Score.value = team1.toString()
                        _team2Score.value = team2.toString()
                    }
                    SimplifiedDataLayerListenerService.ACTION_TIMER_UPDATE -> {
                        val millis = intent.getLongExtra(SimplifiedDataLayerListenerService.EXTRA_TIMER_MILLIS, 0L)
                        val running = intent.getBooleanExtra(SimplifiedDataLayerListenerService.EXTRA_TIMER_RUNNING, false)
                        Log.d("VM", "📥 Timer from Wear: $millis ms, running=$running")
                        
                        if (millis == 0L && !running) {
                            resetMatchTimer(fromRemote = true)
                        } else {
                             // For running updates, we can just update the service value directly without toggling if it matches
                             if (isServiceBound) {
                                matchTimerService?.updateMatchTimer(millis, fromRemote = true)
                                // Only toggle if running state is different
                                if (running != (_isMatchTimerRunning.value ?: false)) {
                                     // We need a way to set state without sending back...
                                     // Actually updateMatchTimer updates the value.
                                     // We need to set running state too.
                                     if (running) matchTimerService?.startTimer() else matchTimerService?.pauseTimer()
                                }
                             }
                        }
                    }
                    SimplifiedDataLayerListenerService.ACTION_KEEPER_TIMER_UPDATE -> {
                        val millis = intent.getLongExtra(SimplifiedDataLayerListenerService.EXTRA_KEEPER_MILLIS, 0L)
                        val running = intent.getBooleanExtra(SimplifiedDataLayerListenerService.EXTRA_KEEPER_RUNNING, false)
                        if (!running && millis == 0L) {
                            resetKeeperTimer(fromRemote = true)
                        } else if (running != (_isKeeperTimerRunning.value ?: false)) {
                            // Sync running state if needed, usually just start/stop
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
                        val isActive = intent.getBooleanExtra(SimplifiedDataLayerListenerService.EXTRA_MATCH_ACTIVE, true)
                        if (!isActive) {
                            endMatch()
                        }
                    }
                    SimplifiedDataLayerListenerService.ACTION_WEAR_COMMAND -> {
                        val cmd = intent.getStringExtra(SimplifiedDataLayerListenerService.EXTRA_COMMAND)
                        when (cmd) {
                            WearConstants.PATH_CMD_ADD_T1 -> addTeam1Score()
                            WearConstants.PATH_CMD_ADD_T2 -> addTeam2Score()
                            WearConstants.PATH_CMD_SUB_T1 -> subtractTeam1Score()
                            WearConstants.PATH_CMD_SUB_T2 -> subtractTeam2Score()
                            WearConstants.PATH_CMD_UNDO -> undoLastGoal()
                        }
                    }
                }
            }
        }

    fun registerBroadcasts(context: Context) {
        val filter =
            android.content.IntentFilter().apply {
                addAction(SimplifiedDataLayerListenerService.ACTION_SCORE_UPDATE)
                addAction(SimplifiedDataLayerListenerService.ACTION_TIMER_UPDATE)
                addAction(SimplifiedDataLayerListenerService.ACTION_TEAM_NAMES_UPDATE)
                addAction(SimplifiedDataLayerListenerService.ACTION_KEEPER_TIMER_UPDATE)
                addAction(SimplifiedDataLayerListenerService.ACTION_MATCH_STATE_UPDATE)
                addAction(SimplifiedDataLayerListenerService.ACTION_WEAR_COMMAND)
            }
        androidx.localbroadcastmanager.content.LocalBroadcastManager
            .getInstance(context)
            .registerReceiver(broadcastReceiver, filter)
    }

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
    val shareMatchEvent = SingleLiveEvent<Intent>()
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
        loadAllPlayers()
        // Initialize default strategy
        switchSport(SportType.SOCCER) 
        
        bindService()
        checkIfOnboardingIsNeeded()

        viewModelScope.launch {
            connectionManager.connectionState.collect { state ->
                when (state) {
                    is it.vantaggi.scoreboardessential.shared.communication.ConnectionState.Connected -> {
                        Log.d("App", "✓ Connected to ${state.nodeCount} device(s)")
                        _isWearConnected.value = true
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

    fun switchSport(sportType: SportType) {
        _currentSport.value = sportType
        scoringStrategy = when (sportType) {
            SportType.SOCCER -> SoccerScoringStrategy()
            SportType.PADEL -> PadelScoringStrategy()
        }
        startNewMatch()
    }

    private fun startNewMatch() {
        val initialState = scoringStrategy.getInitialState()
        updateScoreState(initialState)
        
        _matchEvents.value = emptyList()
        matchTimerService?.resetTimer()
        if (isServiceBound) {
            matchTimerService?.resetKeeperTimer()
        }

        actionStack.clear()
        _canUndo.postValue(false)

        addMatchEvent("New match ready (${_currentSport.value}) - press START")
        sendMatchStateUpdate(true)
    }
    
    private fun updateScoreState(state: ScoreBoardState) {
        _scoreBoardState.value = state
        
        // Map main display score
        _team1Score.value = state.team1Score
        _team2Score.value = state.team2Score
        
        // Sync to wear (simplified for now, sends Strings converted to Int if possible or specific protocol)
        // For Padel, we might need to send separate events or change the protocol.
        // Attempt to parse or send generic
       
        // Temporary: If sport is Soccer, parse Int. If Padel, logic needed in Wear.
        // We will send string data to wear in updated method
        updateWearScore(state)
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
    // Generic update score method replaced by Strategy usage
    private fun updateWearScore(state: ScoreBoardState) {
         viewModelScope.launch {
            val data = mutableMapOf<String, Any>(
                    WearConstants.KEY_TEAM1_SCORE to state.team1Score,
                    WearConstants.KEY_TEAM2_SCORE to state.team2Score,
                    WearConstants.KEY_SPORT_TYPE to state.sportType.name
            )
            
            // Add Sets if Padel
            if (state.sportType == SportType.PADEL) {
                data[WearConstants.KEY_TEAM1_SETS] = state.team1Sets.toIntArray()
                data[WearConstants.KEY_TEAM2_SETS] = state.team2Sets.toIntArray()
                state.servingTeam?.let { data[WearConstants.KEY_SERVING_TEAM] = it }
                state.servingSide?.let { data[WearConstants.KEY_SERVING_SIDE] = it }
                data[WearConstants.KEY_IS_GOLDEN_POINT] = state.isGoldenPoint
            }
            
            // Legacy Int support
             data["team1_score_int"] = state.team1Score.toIntOrNull() ?: 0
             data["team2_score_int"] = state.team2Score.toIntOrNull() ?: 0
             
            connectionManager.sendData(
                path = WearConstants.PATH_SCORE,
                data = data,
                urgent = true,
            )
        }
    }

    fun addTeam1Score() {
        val currentState = _scoreBoardState.value ?: scoringStrategy.getInitialState()
        val newState = scoringStrategy.addPoint(currentState, 1)
        updateScoreState(newState)
        
        triggerHapticFeedback()
        
        // Scorer dialog only for Soccer
        if (scoringStrategy.getSportType() == SportType.SOCCER) {
            val players = team1Players.value
            if (!players.isNullOrEmpty()) {
                showSelectScorerDialog.postValue(Pair(1, players))
            } else {
                addScorer(1, null) 
            }
        } else {
            // Padel Point
            addMatchEvent("Point for ${_team1Name.value}", team = 1)
            // Undo Stack
            actionStack.push(GoalAction(1, null, System.currentTimeMillis(), _scoreBoardState.value)) // Reusing GoalAction generic
            _canUndo.postValue(true)
        }
    }

    fun subtractTeam1Score() {
        // Undo logic is generally handled by 'undoLastGoal', but direct minus button?
        // Logic depends on strategy.
        val currentState = _scoreBoardState.value ?: return
        val newState = scoringStrategy.removePoint(currentState, 1)
        updateScoreState(newState)
        
        triggerHapticFeedback()
        addMatchEvent("Score correction for ${_team1Name.value}", team = 1)
    }

    fun addTeam2Score() {
        val currentState = _scoreBoardState.value ?: scoringStrategy.getInitialState()
        val newState = scoringStrategy.addPoint(currentState, 2)
        updateScoreState(newState)
        
        triggerHapticFeedback()
        
        if (scoringStrategy.getSportType() == SportType.SOCCER) {
             val players = team2Players.value
            if (!players.isNullOrEmpty()) {
                showSelectScorerDialog.postValue(Pair(2, players))
            } else {
                addScorer(2, null)
            }
        } else {
            addMatchEvent("Point for ${_team2Name.value}", team = 2)
            actionStack.push(GoalAction(2, null, System.currentTimeMillis(), _scoreBoardState.value))
            _canUndo.postValue(true)
        }
    }

    fun subtractTeam2Score() {
        val currentState = _scoreBoardState.value ?: return
        val newState = scoringStrategy.removePoint(currentState, 2)
        updateScoreState(newState)
        
        triggerHapticFeedback()
        addMatchEvent("Score correction for ${_team2Name.value}", team = 2)
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
                actionStack.push(GoalAction(team, playerWithRoles.player.playerId, System.currentTimeMillis(), _scoreBoardState.value))
            } else {
                // No specific player, just log a goal for the team
                addMatchEvent("Goal", team = team, player = teamName)
                
                // Track for Undo (null playerId)
                actionStack.push(GoalAction(team, null, System.currentTimeMillis(), _scoreBoardState.value))
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
                if (lastAction.previousState != null) {
                    updateScoreState(lastAction.previousState)
                } else if (scoringStrategy.getSportType() == SportType.SOCCER) {
                     // Fallback for actions without state (should generally not happen with new logic)
                     // Try to manually decrement info
                     val current = _scoreBoardState.value
                     if (current != null) {
                         // Very basic fallback: just reload purely from current - 1 logic if possible?
                         // But for Soccer we can just assume Int scores.
                         if (lastAction.teamId == 1) {
                             val newScore = (current.team1Score.toIntOrNull() ?: 1) - 1
                             _team1Score.value = newScore.toString()
                         } else {
                             val newScore = (current.team2Score.toIntOrNull() ?: 1) - 1
                             _team2Score.value = newScore.toString()
                         }
                     }
                }

                // 2. Revert Player Stats (Database)
                lastAction.playerId?.let { playerId ->
                     _allPlayers.value?.find { it.player.playerId == playerId }?.let { p ->
                         if (p.player.goals > 0) {
                             p.player.goals--
                             playerDao.update(p.player)
                         }
                     }
                }

                // 3. Revert Event Log
                val currentEvents = _matchEvents.value?.toMutableList() ?: return@launch
                val index = currentEvents.indexOfFirst { 
                    (it.event.contains("Goal") || it.event.contains("Point")) && 
                    it.team == lastAction.teamId &&
                    (lastAction.playerId == null || it.player != null)
                }
                if (index != -1) {
                    currentEvents.removeAt(index)
                    _matchEvents.postValue(currentEvents)
                }
                
                addMatchEvent("Undo: Last action removed", team = lastAction.teamId)
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
    // --- End Match ---
    fun endMatch(): Boolean {
        // Check if match started based on timer or score
        // We use string comparison for scores "0"
        if (team1Score.value == "0" && team2Score.value == "0" && matchTimerValue.value == 0L) {
            return false // Match not started, do not save
        }
        
        val currentState = _scoreBoardState.value
        val sport = currentState?.sportType ?: SportType.SOCCER

        viewModelScope.launch {
            if (isServiceBound) {
                matchTimerService?.stopTimer()
            }
            
            // Calculate Integer scores for compatibility
            var s1 = 0
            var s2 = 0
            
            if (sport == SportType.SOCCER) {
                s1 = team1Score.value?.toIntOrNull() ?: 0
                s2 = team2Score.value?.toIntOrNull() ?: 0
            } else {
                // For Padel, we count Sets won
                // We'll trust the sets list or helper
                // Simple logic: check finished sets
                val t1Sets = currentState?.team1Sets ?: emptyList()
                val t2Sets = currentState?.team2Sets ?: emptyList()
                // ... logic to count sets ...
                // Simplified: Just use scoreBoardState logic if available or recompute
                // We know 2 sets won = match win.
                // We can check if isMatchFinished and winnerTeamId
                if (currentState?.isMatchFinished == true) {
                    if (currentState.winnerTeamId == 1) { s1 = 2; s2 = countSetsWonSimple(t2Sets, t1Sets) } 
                    else { s2 = 2; s1 = countSetsWonSimple(t1Sets, t2Sets) }
                } else {
                   // Match abandoned? Count sets won so far
                   s1 = countSetsWonSimple(t1Sets, t2Sets)
                   s2 = countSetsWonSimple(t2Sets, t1Sets)
                }
            }
            
            val detailsJson = try {
                 org.json.JSONObject().apply {
                    put("t1", currentState?.team1Score)
                    put("t2", currentState?.team2Score)
                    put("sets1", org.json.JSONArray(currentState?.team1Sets ?: emptyList<Int>()))
                    put("sets2", org.json.JSONArray(currentState?.team2Sets ?: emptyList<Int>()))
                    put("sport", currentState?.sportType?.name)
                 }.toString()
            } catch (e: Exception) { null }

            val matchId =
                matchDao.insert(
                    Match(
                        team1Id = 1, // Default team 1 ID
                        team2Id = 2, // Default team 2 ID
                        team1Score = s1,
                        team2Score = s2,
                        timestamp = System.currentTimeMillis(),
                        sport = sport.name,
                        scoreDetails = detailsJson
                    ),
                )

            val allMatchPlayers = (team1Players.value ?: emptyList()) + (team2Players.value ?: emptyList())
            for (playerWithRoles in allMatchPlayers) {
                playerWithRoles.player.appearances++
                playerDao.update(playerWithRoles.player)
                matchDao.insertMatchPlayerCrossRef(MatchPlayerCrossRef(matchId.toInt(), playerWithRoles.player.playerId))
            }

            addMatchEvent("Match ended - Final: ${team1Score.value} - ${team2Score.value}")

            sendMatchStateUpdate(false)
            startNewMatch()
            sendResetUpdate()
        }
        return true
    }
    
    private fun countSetsWonSimple(mySets: List<Int>, theirSets: List<Int>): Int {
        var won = 0
        for (i in mySets.indices) {
            if (i >= theirSets.size) break
            val my = mySets[i]
            val their = theirSets[i]
            if ((my == 6 && their <= 4) || (my == 7)) won++
        }
        return won
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
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_TEAM1_NAME to (_team1Name.value ?: "TEAM 1"),
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_TEAM2_NAME to (_team2Name.value ?: "TEAM 2"),
                )
            connectionManager.sendData(
                path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_TEAM_NAMES,
                data = data,
            )
        }
    }

    private fun sendKeeperTimerUpdate(isRunning: Boolean, millis: Long? = null) {
        viewModelScope.launch {
            val data =
                mapOf(
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_KEEPER_MILLIS to (millis ?: (_keeperTimerValue.value ?: 0L)),
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_KEEPER_RUNNING to isRunning,
                )
            connectionManager.sendData(
                path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_KEEPER_TIMER,
                data = data,
            )
        }
    }

    private fun sendMatchStateUpdate(isActive: Boolean) {
        viewModelScope.launch {
            val data =
                mapOf(
                    "match_active" to isActive,
                )
            connectionManager.sendData(
                path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_MATCH_STATE,
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
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_TEAM1_COLOR
                } else {
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_TEAM2_COLOR
                }
            val data = mapOf("color" to color)
            connectionManager.sendData(path = path, data = data)
        }
    }

    private fun sendResetUpdate() {
        val initialState = scoringStrategy.getInitialState()
        updateScoreState(initialState)
        sendTeamNamesUpdate()
        
        viewModelScope.launch {
            // Signal match reset to wear (active state = false)
            val stateData = mapOf(WearConstants.KEY_MATCH_ACTIVE to true) // Keep it active but reset
            connectionManager.sendData(
                path = WearConstants.PATH_MATCH_STATE,
                data = stateData,
            )

            // Reset timers on wear
            val timerData = mapOf(
                WearConstants.KEY_TIMER_MILLIS to 0L,
                WearConstants.KEY_TIMER_RUNNING to false,
            )
            connectionManager.sendData(
                path = WearConstants.PATH_TIMER_STATE,
                data = timerData,
            )
        }
    }

    fun shareMatchResults() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as android.view.LayoutInflater
            val view = inflater.inflate(R.layout.pdf_match_report, null)

            // Get Views
            val team1NameTextView = view.findViewById<android.widget.TextView>(R.id.pdf_team1_name)
            val team1ScoreTextView = view.findViewById<android.widget.TextView>(R.id.pdf_team1_score)
            val team2NameTextView = view.findViewById<android.widget.TextView>(R.id.pdf_team2_name)
            val team2ScoreTextView = view.findViewById<android.widget.TextView>(R.id.pdf_team2_score)
            val team1PlayersList = view.findViewById<android.widget.LinearLayout>(R.id.pdf_team1_players_list)
            val team2PlayersList = view.findViewById<android.widget.LinearLayout>(R.id.pdf_team2_players_list)
            val scorersList = view.findViewById<android.widget.LinearLayout>(R.id.pdf_scorers_list)

            // Set Header Info
            team1NameTextView.text = team1Name.value
            team1ScoreTextView.text = team1Score.value?.toString() ?: "0"
            team2NameTextView.text = team2Name.value
            team2ScoreTextView.text = team2Score.value?.toString() ?: "0"

            // Apply Dynamic Colors
            _team1Color.value?.let {
                team1NameTextView.setTextColor(it)
                team1ScoreTextView.setTextColor(it)
            }
            _team2Color.value?.let {
                team2NameTextView.setTextColor(it)
                team2ScoreTextView.setTextColor(it)
            }

            // Populate Formations
            team1Players.value?.forEach { player ->
                val playerTextView =
                    android.widget.TextView(context).apply {
                        text = player.player.playerName
                        setTextAppearance(R.style.TextAppearance_App_BodyLarge_Street)
                        setTextColor(ContextCompat.getColor(context, R.color.stencil_white))
                        setPadding(0, 4, 0, 4)
                    }
                team1PlayersList.addView(playerTextView)
            }

            team2Players.value?.forEach { player ->
                val playerTextView =
                    android.widget.TextView(context).apply {
                        text = player.player.playerName
                        setTextAppearance(R.style.TextAppearance_App_BodyLarge_Street)
                        setTextColor(ContextCompat.getColor(context, R.color.stencil_white))
                        setPadding(0, 4, 0, 4)
                    }
                team2PlayersList.addView(playerTextView)
            }

            // Populate Scorers
            val scorers =
                _matchEvents.value
                    ?.filter { it.event == "Goal" && it.player != null }
                    ?.map { it.player!! }
                    ?.groupingBy { it }
                    ?.eachCount()
            if (scorers != null && scorers.isNotEmpty()) {
                scorers.forEach { (playerName, goalCount) ->
                    val scorerTextView =
                        android.widget.TextView(context).apply {
                            text = "$playerName ($goalCount)"
                            setTextAppearance(R.style.TextAppearance_App_BodyLarge_Street)
                            setTextColor(ContextCompat.getColor(context, R.color.stencil_white))
                            setPadding(0, 4, 0, 4)
                        }
                    scorersList.addView(scorerTextView)
                }
            } else {
                val noScorersTextView =
                    android.widget.TextView(context).apply {
                        text = "Nessun marcatore"
                        setTextAppearance(R.style.TextAppearance_App_BodyLarge_Street)
                        setTextColor(ContextCompat.getColor(context, R.color.sidewalk_gray))
                        setPadding(0, 4, 0, 4)
                    }
                scorersList.addView(noScorersTextView)
            }

            // PDF Generation
            val pdfDocument = android.graphics.pdf.PdfDocument()
            val pageInfo =
                android.graphics.pdf.PdfDocument.PageInfo
                    .Builder(595, 842, 1)
                    .create() // A4 size
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val measureWidth =
                android.view.View.MeasureSpec
                    .makeMeasureSpec(pageInfo.pageWidth, android.view.View.MeasureSpec.EXACTLY)
            val measureHeight =
                android.view.View.MeasureSpec.makeMeasureSpec(
                    pageInfo.pageHeight,
                    android.view.View.MeasureSpec.UNSPECIFIED,
                )
            view.measure(measureWidth, measureHeight)
            view.layout(0, 0, pageInfo.pageWidth, view.measuredHeight)

            view.draw(canvas)
            pdfDocument.finishPage(page)

            val pdfFile = java.io.File(context.cacheDir, "match_report.pdf")
            try {
                pdfDocument.writeTo(java.io.FileOutputStream(pdfFile))
            } catch (e: java.io.IOException) {
                e.printStackTrace()
            }
            pdfDocument.close()

            val pdfUri =
                androidx.core.content.FileProvider
                    .getUriForFile(context, "${context.packageName}.provider", pdfFile)

            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, pdfUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(Intent.EXTRA_SUBJECT, "Match Report: ${team1Name.value} vs ${team2Name.value}")
                    val text = "Ecco il report del match tra ${team1Name.value} e ${team2Name.value}."
                    putExtra(Intent.EXTRA_TEXT, text)
                }

            shareMatchEvent.postValue(shareIntent)
        }
    }

    override fun onCleared() {
        vibrator?.cancel()
        try {
            if (isServiceBound) {
                getApplication<Application>().unbindService(serviceConnection)
                isServiceBound = false
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error unbinding service", e)
        }
        super.onCleared()
    }
}
