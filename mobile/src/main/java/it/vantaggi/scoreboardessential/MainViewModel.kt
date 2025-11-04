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
import android.widget.LinearLayout
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
import it.vantaggi.scoreboardessential.domain.usecases.ManagePlayersUseCase
import it.vantaggi.scoreboardessential.domain.usecases.ManageTimerUseCase
import it.vantaggi.scoreboardessential.domain.usecases.UpdateScoreUseCase
import it.vantaggi.scoreboardessential.repository.MatchRepository
import it.vantaggi.scoreboardessential.repository.MatchSettingsRepository
import it.vantaggi.scoreboardessential.repository.PlayerRepository
import it.vantaggi.scoreboardessential.repository.UserPreferencesRepository
import it.vantaggi.scoreboardessential.service.MatchTimerService
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.utils.ScoreUpdateEventBus
import it.vantaggi.scoreboardessential.utils.SingleLiveEvent
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    // Use Cases
    private val wearDataSync = OptimizedWearDataSync(application)
    private val updateScoreUseCase = UpdateScoreUseCase(wearDataSync)
    private val manageTimerUseCase = ManageTimerUseCase(null, wearDataSync)
    private val managePlayersUseCase = ManagePlayersUseCase(playerDao, wearDataSync)

    // Expose states from Use Cases
    val team1Score =
        updateScoreUseCase.scoreState
            .map { it.team1Score }
            .asLiveData()

    val team2Score =
        updateScoreUseCase.scoreState
            .map { it.team2Score }
            .asLiveData()

    val matchTimerValue =
        manageTimerUseCase.timerState
            .map { it.timeMillis }
            .asLiveData()

    val isMatchTimerRunning =
        manageTimerUseCase.timerState
            .map { it.isRunning }
            .asLiveData()

    val team1Players =
        managePlayersUseCase.teamRoster
            .map { it.team1Players }
            .asLiveData()

    val team2Players =
        managePlayersUseCase.teamRoster
            .map { it.team2Players }
            .asLiveData()

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                android.util.Log.d("MainViewModel", "Service connected!")
                val binder = service as MatchTimerService.MatchTimerBinder
                matchTimerService = binder.getService()
                manageTimerUseCase.setTimerService(matchTimerService) // Update UseCase with service
                isServiceBound = true

                viewModelScope.launch {
                    binder.getService().matchTimerValue.collect {
                        manageTimerUseCase.updateTimerValue(it)
                    }
                }
                viewModelScope.launch {
                    binder.getService().isMatchTimerRunning.collect { running ->
                        manageTimerUseCase.setTimerRunning(running)
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
                manageTimerUseCase.setTimerService(null) // Clear service from UseCase
                isServiceBound = false
            }
        }
    private val vibrator = ContextCompat.getSystemService(application, Vibrator::class.java)

    val isWearConnected: LiveData<Boolean> = wearDataSync.isConnected.asLiveData()

    val allMatches: LiveData<List<MatchWithTeams>> = repository.allMatches.asLiveData()

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
        loadMatchSettings()
        listenForScoreUpdates()
        listenForTimerStateEvents()
        loadAllPlayers()
        startNewMatch()
        bindService()
        checkIfOnboardingIsNeeded()
    }

    private fun loadMatchSettings() {
        viewModelScope.launch {
            _team1Name.postValue(matchSettingsRepository.getTeam1Name())
            _team2Name.postValue(matchSettingsRepository.getTeam2Name())
            _team1Color.postValue(matchSettingsRepository.getTeam1Color())
            _team2Color.postValue(matchSettingsRepository.getTeam2Color())
            setKeeperTimer(matchSettingsRepository.getKeeperTimerDuration())
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
            getApplication<Application>().startService(intent)
            getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun listenForScoreUpdates() {
        viewModelScope.launch {
            ScoreUpdateEventBus.events.collect { event ->
                updateScoreUseCase.setScores(event.team1Score, event.team2Score)
                addMatchEvent("Score updated from Wear OS")
            }
        }
    }

    private fun listenForTimerStateEvents() {
        viewModelScope.launch {
            ScoreUpdateEventBus.timerStateEvents.collect { event ->
                manageTimerUseCase.updateTimerValue(event.millis)
                manageTimerUseCase.setTimerRunning(event.isRunning)
                addMatchEvent("Timer state updated from Wear OS")
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
        updateScoreUseCase.resetScores()
        _matchEvents.value = emptyList()
        manageTimerUseCase.resetTimer()
        if (isServiceBound) {
            matchTimerService?.resetKeeperTimer()
        }

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
        managePlayersUseCase.addPlayerToTeam(playerWithRoles, teamId)
        val teamName = if (teamId == 1) _team1Name.value else _team2Name.value
        addMatchEvent("${playerWithRoles.player.playerName} added to $teamName", team = teamId)
    }

    fun removePlayerFromTeam(
        playerWithRoles: PlayerWithRoles,
        teamId: Int,
    ) {
        managePlayersUseCase.removePlayerFromTeam(playerWithRoles, teamId)
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
    fun addTeam1Score() {
        if (updateScoreUseCase.incrementScore(1)) {
            triggerHapticFeedback()
            val players = team1Players.value
            if (!players.isNullOrEmpty()) {
                showSelectScorerDialog.postValue(Pair(1, players))
            } else {
                addScorer(1, null) // No player to select, just log the goal
            }
        }
    }

    fun subtractTeam1Score() {
        if (updateScoreUseCase.decrementScore(1)) {
            triggerHapticFeedback()
            addMatchEvent("Score correction for ${_team1Name.value}", team = 1)
        }
    }

    fun addTeam2Score() {
        if (updateScoreUseCase.incrementScore(2)) {
            triggerHapticFeedback()
            val players = team2Players.value
            if (!players.isNullOrEmpty()) {
                showSelectScorerDialog.postValue(Pair(2, players))
            } else {
                addScorer(2, null) // No player to select, just log the goal
            }
        }
    }

    fun subtractTeam2Score() {
        if (updateScoreUseCase.decrementScore(2)) {
            triggerHapticFeedback()
            addMatchEvent("Score correction for ${_team2Name.value}", team = 2)
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
                sendScorerToWear(playerWithRoles.player.playerName, playerWithRoles.roles.map { it.name }, team)
            } else {
                // No specific player, just log a goal for the team
                addMatchEvent("Goal", team = team, player = teamName)
            }
        }
    }

    private fun sendScorerToWear(
        name: String,
        roles: List<String>,
        team: Int,
    ) {
        wearDataSync.syncScorerSelected(name, roles, team)
    }

    // --- Match Timer Management ---
    fun startStopMatchTimer() {
        android.util.Log.d("MainViewModel", "startStopMatchTimer called, service bound: $isServiceBound")
        if (!isServiceBound) {
            android.util.Log.e("MainViewModel", "Timer service NOT bound! Cannot start timer.")
            return
        }
        manageTimerUseCase.startOrPauseTimer()
    }

    fun resetMatchTimer() {
        manageTimerUseCase.resetTimer()
    }

    // --- Keeper Timer Management ---
    fun setKeeperTimer(seconds: Long) {
        keeperTimerDuration = seconds * 1000
        _keeperTimerValue.value = keeperTimerDuration
        addMatchEvent("Keeper timer set to $seconds seconds")
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
            for (playerWithRoles in allMatchPlayers) {
                playerWithRoles.player.appearances++
                playerDao.update(playerWithRoles.player)
                matchDao.insertMatchPlayerCrossRef(MatchPlayerCrossRef(matchId.toInt(), playerWithRoles.player.playerId))
            }

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
        wearDataSync.syncTeamNames(
            _team1Name.value ?: "TEAM 1",
            _team2Name.value ?: "TEAM 2",
        )
    }

    private fun sendKeeperTimerUpdate(isRunning: Boolean) {
        wearDataSync.syncKeeperTimer(
            _keeperTimerValue.value ?: 0L,
            isRunning,
        )
    }

    private fun sendMatchStateUpdate(isActive: Boolean) {
        wearDataSync.syncMatchState(isActive)
    }

    private fun sendTeamColorUpdate(
        team: Int,
        color: Int,
    ) {
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
            matchActive = true,
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
