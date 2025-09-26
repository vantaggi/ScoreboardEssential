package it.vantaggi.scoreboardessential

import android.app.Application
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import it.vantaggi.scoreboardessential.database.*
import it.vantaggi.scoreboardessential.utils.ScoreUpdateEventBus
import it.vantaggi.scoreboardessential.repository.PlayerRepository
import it.vantaggi.scoreboardessential.utils.SingleLiveEvent
import it.vantaggi.scoreboardessential.utils.TimerEvent
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import it.vantaggi.scoreboardessential.repository.MatchRepository
import it.vantaggi.scoreboardessential.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.widget.LinearLayout
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.service.MatchTimerService
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager
import it.vantaggi.scoreboardessential.shared.PlayerData
import kotlinx.coroutines.flow.collect
import android.content.SharedPreferences

data class MatchEvent(
    val timestamp: String,
    val event: String,
    val team: Int? = null,
    val player: String? = null,
    val playerRole: String? = null
)

class MainViewModel(
    private val repository: MatchRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    application: Application
) : AndroidViewModel(application) {

    private val playerDao: PlayerDao
    private val matchDao: MatchDao
    private var matchTimerService: MatchTimerService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MatchTimerService.MatchTimerBinder
            matchTimerService = binder.getService()
            isServiceBound = true

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
            matchTimerService = null
            isServiceBound = false
        }
    }
    private val vibrator = ContextCompat.getSystemService(application, Vibrator::class.java)

    private val wearDataSync = OptimizedWearDataSync(application)
    val isWearConnected: LiveData<Boolean> = wearDataSync.isConnected.asLiveData()

    val allMatches: LiveData<List<MatchWithTeams>> = repository.allMatches.asLiveData()

    fun deleteMatch(match: Match) = viewModelScope.launch {
        repository.deleteMatch(match)
    }

    class MainViewModelFactory(
        private val repository: MatchRepository,
        private val userPreferencesRepository: UserPreferencesRepository,
        private val application: Application
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(repository, userPreferencesRepository, application) as T
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
    private val _isMatchTimerRunning = MutableLiveData(false)
    val isMatchTimerRunning: LiveData<Boolean> = _isMatchTimerRunning

    // Keeper Timer
    private val _keeperTimerValue = MutableLiveData(0L)
    val keeperTimerValue: LiveData<Long> = _keeperTimerValue
    private val _isKeeperTimerRunning = MutableLiveData(false)
    val isKeeperTimerRunning: LiveData<Boolean> = _isKeeperTimerRunning
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
    val showOnboarding = SingleLiveEvent<Unit>()
    val showSelectScorerDialog = SingleLiveEvent<Pair<Int, List<PlayerWithRoles>>>()
    val showPlayerSelectionDialog = SingleLiveEvent<Int>()
    val showKeeperTimerExpired = SingleLiveEvent<Unit>()
    val showColorPickerDialog = SingleLiveEvent<Int>()
    val shareMatchEvent = SingleLiveEvent<Intent>()
    val showOnboardingTutorial = SingleLiveEvent<Unit>()

    // Data Client for Wear OS sync
    private val dataClient: DataClient = Wearable.getDataClient(application)

    private val sharedPreferences: SharedPreferences = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

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
        bindService()
        checkIfOnboardingIsNeeded()
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
        Intent(getApplication(), MatchTimerService::class.java).also { intent ->
            getApplication<Application>().startService(intent)
            getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
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
                        startStopMatchTimer()
                        addMatchEvent("Timer started from Wear OS")
                    }
                    is TimerEvent.Pause -> {
                        startStopMatchTimer()
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
                syncTeamPlayersToWear()
            }
        }
    }

    private fun startNewMatch() {
        _team1Score.value = 0
        _team2Score.value = 0
        _matchEvents.value = emptyList()
        if (isServiceBound) {
            matchTimerService?.stopTimer()
            matchTimerService?.resetKeeperTimer()
        }

        addMatchEvent("New match ready - press START to begin")
        sendMatchStateUpdate(true)
        sendScoreUpdate()
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
        val players = _team1Players.value
        if (!players.isNullOrEmpty()) {
            showSelectScorerDialog.postValue(Pair(1, players))
        } else {
            addScorer(1, null) // No player to select, just log the goal
        }
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
        val players = _team2Players.value
        if (!players.isNullOrEmpty()) {
            showSelectScorerDialog.postValue(Pair(2, players))
        } else {
            addScorer(2, null) // No player to select, just log the goal
        }
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

    fun addScorer(team: Int, playerWithRoles: PlayerWithRoles?) {
        viewModelScope.launch {
            val teamName = if (team == 1) _team1Name.value else _team2Name.value
            if (playerWithRoles != null) {
                // A specific player scored
                playerWithRoles.player.goals++
                playerDao.update(playerWithRoles.player)

                val rolesString = playerWithRoles.roles.joinToString(", ") { it.name }
                addMatchEvent("Goal", team = team, player = playerWithRoles.player.playerName, playerRole = rolesString)
                sendScorerToWear(playerWithRoles.player.playerName, playerWithRoles.roles.map { it.name }, team)
            } else {
                // No specific player, just log a goal for the team
                addMatchEvent("Goal", team = team, player = teamName)
            }
        }
    }

    private fun sendScorerToWear(name: String, roles: List<String>, team: Int) {
        wearDataSync.syncScorerSelected(name, roles, team)
    }

    // --- Match Timer Management ---
    fun startStopMatchTimer() {
        if (!isServiceBound) return
        if (_isMatchTimerRunning.value == true) {
            matchTimerService?.pauseTimer()
        } else {
            matchTimerService?.startTimer()
        }
    }

    fun resetMatchTimer() {
        if (!isServiceBound) return
        matchTimerService?.stopTimer()
    }

    // --- Keeper Timer Management ---
    fun setKeeperTimer(seconds: Long) {
        keeperTimerDuration = seconds * 1000
        _keeperTimerValue.value = keeperTimerDuration
        addMatchEvent("Keeper timer set to ${seconds} seconds")
    }

    fun startKeeperTimer() {
        if (!isServiceBound) return
        matchTimerService?.startKeeperTimer(keeperTimerDuration)
        addMatchEvent("Keeper timer started (${keeperTimerDuration / 1000}s)")
    }

    fun pauseKeeperTimer() {
        if (!isServiceBound) return
        matchTimerService?.pauseKeeperTimer()
    }

    fun resetKeeperTimer() {
        if (!isServiceBound) return
        matchTimerService?.resetKeeperTimer()
        addMatchEvent("Keeper timer reset")
    }

    // --- Match Events ---
    private fun addMatchEvent(event: String, team: Int? = null, player: String? = null, playerRole: String? = null) {
        val timeFormat = SimpleDateFormat("mm:ss", Locale.getDefault())
        val timestamp = timeFormat.format(Date(_matchTimerValue.value ?: 0L))

        val matchEvent = MatchEvent(timestamp, event, team, player, playerRole)
        val currentEvents = _matchEvents.value?.toMutableList() ?: mutableListOf()
        currentEvents.add(0, matchEvent) // Add to beginning for reverse chronological order
        _matchEvents.postValue(currentEvents)
    }

    // --- End Match ---
    fun endMatch(): Boolean {
        if (_team1Score.value == 0 && _team2Score.value == 0 && _matchTimerValue.value == 0L) {
            return false // Match not started, do not save
        }

        viewModelScope.launch {
            if (isServiceBound) {
                matchTimerService?.stopTimer()
            }

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
            _team1Players.value?.forEach { player ->
                val playerTextView = android.widget.TextView(context).apply {
                    text = player.player.playerName
                    setTextAppearance(R.style.TextAppearance_App_BodyLarge_Street)
                    setTextColor(ContextCompat.getColor(context, R.color.stencil_white))
                    setPadding(0, 4, 0, 4)
                }
                team1PlayersList.addView(playerTextView)
            }

            _team2Players.value?.forEach { player ->
                val playerTextView = android.widget.TextView(context).apply {
                    text = player.player.playerName
                    setTextAppearance(R.style.TextAppearance_App_BodyLarge_Street)
                    setTextColor(ContextCompat.getColor(context, R.color.stencil_white))
                    setPadding(0, 4, 0, 4)
                }
                team2PlayersList.addView(playerTextView)
            }

            // Populate Scorers
            val scorers = _matchEvents.value
                ?.filter { it.event == "Goal" && it.player != null }
                ?.map { it.player!! }
                ?.groupingBy { it }
                ?.eachCount()
            if (scorers != null && scorers.isNotEmpty()) {
                scorers.forEach { (playerName, goalCount) ->
                    val scorerTextView = android.widget.TextView(context).apply {
                        text = "$playerName ($goalCount)"
                        setTextAppearance(R.style.TextAppearance_App_BodyLarge_Street)
                        setTextColor(ContextCompat.getColor(context, R.color.stencil_white))
                        setPadding(0, 4, 0, 4)
                    }
                    scorersList.addView(scorerTextView)
                }
            } else {
                 val noScorersTextView = android.widget.TextView(context).apply {
                    text = "Nessun marcatore"
                     setTextAppearance(R.style.TextAppearance_App_BodyLarge_Street)
                     setTextColor(ContextCompat.getColor(context, R.color.sidewalk_gray))
                    setPadding(0, 4, 0, 4)
                }
                scorersList.addView(noScorersTextView)
            }


            // PDF Generation
            val pdfDocument = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 size
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val measureWidth = android.view.View.MeasureSpec.makeMeasureSpec(pageInfo.pageWidth, android.view.View.MeasureSpec.EXACTLY)
            val measureHeight = android.view.View.MeasureSpec.makeMeasureSpec(pageInfo.pageHeight, android.view.View.MeasureSpec.UNSPECIFIED)
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

            val pdfUri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.provider", pdfFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
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