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
import it.vantaggi.scoreboardessential.core.ClockMode
import it.vantaggi.scoreboardessential.core.ExportResult
import it.vantaggi.scoreboardessential.core.MatchEngine
import it.vantaggi.scoreboardessential.core.MatchExporter
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.MatchPlayer
import it.vantaggi.scoreboardessential.core.ScoreDisplay
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportCapabilities
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.core.SportRules
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchDao
import it.vantaggi.scoreboardessential.database.MatchPlayerCrossRef
import it.vantaggi.scoreboardessential.database.MatchWithTeams
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerDao
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.domain.models.MatchEvent
import it.vantaggi.scoreboardessential.domain.models.MatchEventType
import it.vantaggi.scoreboardessential.domain.models.MatchReportData
import it.vantaggi.scoreboardessential.repository.MatchRepository
import it.vantaggi.scoreboardessential.repository.MatchSettingsRepository
import it.vantaggi.scoreboardessential.repository.PlayerRepository
import it.vantaggi.scoreboardessential.repository.UserPreferencesRepository
import it.vantaggi.scoreboardessential.service.MatchTimerService
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager
import it.vantaggi.scoreboardessential.shared.PlayerData
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import it.vantaggi.scoreboardessential.shared.utils.WearDataValidator
import it.vantaggi.scoreboardessential.ui.MatchHistoryUiState
import it.vantaggi.scoreboardessential.utils.SingleLiveEvent
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

    private val actionStack = ArrayDeque<GoalAction>()
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
    val matchHistory: LiveData<List<MatchHistoryUiState>> =
        repository.allMatches
            .map { matches ->
                matches.map { match ->
                    val formatted =
                        if (match.players.isNotEmpty()) {
                            "Players: ${match.players.joinToString(", ") { it.playerName }}"
                        } else {
                            ""
                        }
                    MatchHistoryUiState(match, formatted)
                }
            }.flowOn(Dispatchers.Default)
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
                        // Il v1 spedisce due interi ASSOLUTI: sono un punteggio solo per il
                        // calcio. Un orologio non aggiornato che li mandasse durante una partita
                        // di padel sovrascriverebbe uno stato strutturato (game, set, servizio)
                        // con la sua idea piatta di esso. Qui si degrada -- l'aggiornamento non
                        // arriva -- invece di corrompere.
                        if (sportRules.id != SportRegistry.FOOTBALL) return

                        val team1 = intent.getIntExtra(WearConstants.KEY_TEAM1_SCORE, 0)
                        val team2 = intent.getIntExtra(WearConstants.KEY_TEAM2_SCORE, 0)
                        Log.d("VM", "📥 Score update received from Wear")
                        // Anche il motore va riallineato, non solo le LiveData: altrimenti il
                        // primo tocco locale ripartirebbe dal punteggio che il motore aveva
                        // prima dell'aggiornamento remoto, facendo saltare il tabellone
                        // all'indietro.
                        seedEngineFromAbsolute(team1, team2)
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
                                    // fromRemote = true come per updateMatchTimer appena sopra:
                                    // senza, il telefono ri-pubblica all'orologio lo stato che
                                    // l'orologio gli ha appena mandato.
                                    if (running) {
                                        matchTimerService?.startTimer(fromRemote = true)
                                    } else {
                                        matchTimerService?.pauseTimer(fromRemote = true)
                                    }
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

                    SimplifiedDataLayerListenerService.ACTION_REQUEST_SYNC -> {
                        // L'orologio la manda a ogni avvio a freddo. Finora l'azione era
                        // registrata nell'IntentFilter ma non aveva alcun ramo qui, quindi
                        // veniva scartata in silenzio: l'orologio restava a 0-0 finche' il
                        // collector di ConnectionState non scattava per conto suo.
                        Log.d("VM", "Richiesta di risincronizzazione ricevuta dall'orologio")
                        syncAllDataToWear()
                    }

                    SimplifiedDataLayerListenerService.ACTION_SCORE_INTENT -> {
                        val side = intent.getIntExtra(WearConstants.KEY_SIDE, 0)
                        if (side != 1 && side != 2) return
                        // Il lato dice DOVE, il tipo dice COSA. Tenerli separati e' cio' che evita
                        // di caricare un campo di piu' significati.
                        when (intent.getStringExtra(WearConstants.KEY_INTENT_KIND)) {
                            WearConstants.INTENT_UNDO -> undoLastGoal()
                            WearConstants.INTENT_CORRECTION -> subtractScore(side)
                            else -> addRemotePoint(side)
                        }
                    }

                    SimplifiedDataLayerListenerService.ACTION_SCORER_SELECTED -> {
                        val playerName = intent.getStringExtra(WearConstants.KEY_PLAYER_NAME) ?: return
                        val team = intent.getIntExtra(WearConstants.EXTRA_TEAM_NUMBER, 1)
                        val playerId = intent.getIntExtra(WearConstants.KEY_PLAYER_ID, -1).takeIf { it >= 0 }
                        attributeRemoteScorer(team, playerName, playerId)
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

    /**
     * Le regole in vigore. Oggi sempre il calcio: il selettore dello sport arriva dopo, e
     * introdurlo qui senza un'interfaccia che lo mostri sarebbe codice senza consumatori.
     */
    private var sportRules: SportRules = SportRegistry.byId(SportRegistry.FOOTBALL)

    /**
     * Sorgente di verita' del punteggio.
     *
     * [_team1Score] e [_team2Score] restano `LiveData<Int>` con la stessa identica superficie
     * pubblica di prima -- l'interfaccia, i binding e i test esistenti non si accorgono di nulla
     * -- ma ora sono una PROIEZIONE di `engine.state.headline()` invece di essere loro stessi lo
     * stato. E' l'unico cambiamento di questo passo: nessuna rinomina, nessuna firma toccata.
     */
    private var engine = MatchEngine(sportRules)

    private val _activeSport = MutableLiveData(SportRegistry.FOOTBALL)

    /** Identificativo dello sport in corso. L'interfaccia lo usa solo per la selezione. */
    val activeSport: LiveData<String> = _activeSport

    private val _sportCapabilities = MutableLiveData(sportRules.capabilities)

    /**
     * Cosa lo sport corrente prevede.
     *
     * E' l'unica cosa che l'interfaccia deve sapere: non c'e' alcun `when (sport)` nei layout ne'
     * nelle Activity. Accendere uno sport nuovo non tocca una riga di codice di presentazione.
     */
    val sportCapabilities: LiveData<SportCapabilities> = _sportCapabilities

    /**
     * Cambia sport, se e' lecito farlo.
     *
     * Ritorna false quando una partita e' gia' in corso: convertire un punteggio vivo fra due
     * regolamenti diversi non ha una risposta giusta (quanti game vale un 3-1 di calcio?), quindi
     * la si evita invece di inventarne una. Una guardia al posto di un problema.
     */
    fun selectSport(sportId: String): Boolean {
        if (engine.log.isNotEmpty()) return false
        if (sportId == _activeSport.value) return true
        applySport(sportId)
        viewModelScope.launch { matchSettingsRepository.setActiveSport(sportId) }
        return true
    }

    /**
     * L'ordine di servizio si DERIVA dai roster invece di chiederlo.
     *
     * Nel padel la rotazione e' A1, B1, A2, B2: alternando i due roster si ottiene esattamente
     * quella, quindi l'unica cosa che l'utente deve davvero decidere -- chi serve per primo --
     * coincide con chi mette per primo nel roster. Una schermata in meno da compilare a bordo
     * campo, che e' il posto peggiore per compilare schermate.
     *
     * Si applica solo a partita ferma: cambiare la rotazione a meta' partita falserebbe
     * l'attribuzione dei punti gia' giocati.
     */
    private fun refreshServeOrder() {
        if (engine.log.isNotEmpty()) return
        val uno = _team1Players.value.orEmpty()
        val due = _team2Players.value.orEmpty()
        if (uno.size < 2 || due.size < 2) return
        val ordine =
            listOf(
                uno[0].player.playerId,
                due[0].player.playerId,
                uno[1].player.playerId,
                due[1].player.playerId,
            )
        if (ordine == sportRules.config.serveOrder) return
        sportRules = SportRegistry.forMatch(sportRules.id, ordine)
        engine = MatchEngine(sportRules)
        _scoreDisplay.value = sportRules.display(engine.state)
    }

    private fun applySport(sportId: String) {
        sportRules = SportRegistry.byId(sportId)
        engine = MatchEngine(sportRules)
        _activeSport.value = sportRules.id
        _sportCapabilities.value = sportRules.capabilities
        currentMatchId = null
        _team1Score.value = 0
        _team2Score.value = 0
        _scoreDisplay.value = sportRules.display(engine.state)
        // Il cambio di sport non passa da updateScore (che qui non va chiamata: spedirebbe un
        // azzeramento v1 che oggi non parte), ma cambia le CAPACITA'. Senza questo invio
        // l'orologio continuerebbe a mostrare i comandi del calcio fino al primo punto.
        sendStateV2()
    }

    /**
     * Sorgente di verita' del registro eventi.
     *
     * Prima ogni scrittura faceva leggi-modifica-postValue su [_matchEvents]. postValue e'
     * asincrono, quindi due eventi ravvicinati leggevano lo stesso valore e il secondo
     * sovrascriveva il primo. L'annullamento era il caso peggiore: rimuoveva il gol dalla lista
     * e subito dopo registrava "Undo", che ripubblicava la lista PRIMA della rimozione -- quindi
     * il gol restava. La lista vive qui, a LiveData va sempre una copia.
     */
    private val matchEventLog = mutableListOf<MatchEvent>()

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

                // Lo sport si applica solo a partita ferma: un cambio arrivato dalle impostazioni
                // mentre si sta giocando verrebbe ignorato qui e ripreso alla partita successiva.
                if (settings.activeSport != _activeSport.value && engine.log.isEmpty()) {
                    applySport(settings.activeSport)
                }
            }
        }

        loadAllPlayers()
        // bindService PRIMA di startNewMatch: startNewMatch azzera i timer attraverso il service,
        // e con l'ordine invertito quelle chiamate cadevano nel vuoto perche' matchTimerService
        // era ancora null. Effetto osservabile: alla ricostruzione del ViewModel il punteggio
        // tornava a 0-0 mentre il cronometro, che il service persiste per conto suo, proseguiva.
        bindService()
        startNewMatch()
        restoreActiveMatchIfAny()
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
                addAction(SimplifiedDataLayerListenerService.ACTION_KEEPER_TIMER_UPDATE)
                addAction(SimplifiedDataLayerListenerService.ACTION_MATCH_STATE_UPDATE)
                addAction(SimplifiedDataLayerListenerService.ACTION_REQUEST_SYNC)
                addAction(SimplifiedDataLayerListenerService.ACTION_SCORE_INTENT)
                addAction(SimplifiedDataLayerListenerService.ACTION_SCORER_SELECTED)
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
                sendPlayersUpdate(players)
            }
        }
    }

    /**
     * Pushes the full player roster to the Wear device so the watch can attribute
     * goals to a scorer. Serialized via [PlayerData.encodeList].
     */
    private fun sendPlayersUpdate(players: List<PlayerWithRoles>) {
        viewModelScope.launch {
            val encoded =
                PlayerData.encodeList(
                    players.map { pwr ->
                        PlayerData(
                            id = pwr.player.playerId,
                            name = pwr.player.playerName,
                            roles = pwr.roles.map { it.name },
                            goals = pwr.player.goals,
                            appearances = pwr.player.appearances,
                        )
                    },
                )
            connectionManager.sendData(
                path = WearConstants.PATH_PLAYERS,
                data = mapOf(WearConstants.KEY_PLAYERS to encoded),
            )
        }
    }

    private fun startNewMatch() {
        engine.reset()
        currentMatchId = null
        updateScore(0, 0)
        synchronized(matchEventLog) {
            matchEventLog.clear()
            _matchEvents.postValue(emptyList())
        }
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
        refreshServeOrder()
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
        refreshServeOrder()
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
        if (!WearDataValidator.isValidScore(team1) || !WearDataValidator.isValidScore(team2)) {
            Log.w("MainViewModel", "Refusing to set out-of-range score: $team1-$team2")
            return
        }
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

        // Il v2 esce dallo stesso imbuto del v1, cosi' i due non possono mai divergere: ogni
        // chiamante che aggiorna il punteggio (publishEngineState, syncAllDataToWear,
        // startNewMatch, restoreActiveMatchIfAny, sendResetUpdate) passa di qui.
        sendStateV2()
    }

    /**
     * Il punteggio GIA' IMPAGINATO sul path v2. Il telefono e' autoritativo: l'orologio rende
     * stringhe e non esegue mai regole.
     *
     * **Convenzione delle stringhe assenti** (da leggere insieme al lato orologio): le chiavi ci
     * sono SEMPRE, e il valore VUOTO significa "niente da mostrare". Cosi' chi legge ha una regola
     * sola -- `getString(chiave, "")` e poi `isEmpty()` -- invece di dover distinguere una chiave
     * mancante da una chiave vuota. Per la stessa ragione [WearConstants.KEY_SERVING_SIDE] vale 0
     * quando nessuno e' al servizio: i lati sono 1 e 2, quindi 0 non e' ambiguo.
     *
     * `KEY_TIMESTAMP` lo aggiunge gia' `sendData` a ogni invio, ed e' portante: senza, il Data
     * Layer non riconsegna un DataItem identico al precedente e un punteggio che torna al valore
     * di prima non arriverebbe.
     */
    private fun sendStateV2() {
        // Impaginato al momento dallo stato del motore invece che da _scoreDisplay: quella
        // LiveData viene scritta anche con postValue, quindi il suo `value` puo' essere indietro
        // di un giro rispetto al motore.
        val display = sportRules.display(engine.state)
        val capacita = sportRules.capabilities
        viewModelScope.launch {
            val data =
                mapOf(
                    WearConstants.KEY_PROTO_VERSION to WearConstants.PROTO_VERSION,
                    WearConstants.KEY_SPORT_ID to sportRules.id,
                    WearConstants.KEY_SIDE1_PRIMARY to display.side1Primary,
                    WearConstants.KEY_SIDE1_SECONDARY to (display.side1Secondary ?: ""),
                    WearConstants.KEY_SIDE2_PRIMARY to display.side2Primary,
                    WearConstants.KEY_SIDE2_SECONDARY to (display.side2Secondary ?: ""),
                    WearConstants.KEY_PERIOD_LABEL to (display.periodLabel ?: ""),
                    WearConstants.KEY_SERVING_SIDE to (display.servingSide ?: 0),
                    WearConstants.KEY_CAP_HAS_CLOCK to (capacita.clock != ClockMode.NONE),
                    WearConstants.KEY_CAP_HAS_AUX_TIMER to capacita.hasAuxCountdown,
                    WearConstants.KEY_CAP_ATTRIBUTES_SCORER to capacita.attributesScorer,
                    WearConstants.KEY_CAP_DECREMENT_IS_UNDO to capacita.decrementIsUndo,
                )
            connectionManager.sendData(
                path = WearConstants.PATH_STATE_V2,
                data = data,
                urgent = true,
            )
        }
    }

    private val _scoreDisplay = MutableLiveData(sportRules.display(sportRules.initial()))

    /**
     * Il punteggio gia' impaginato dalle regole dello sport.
     *
     * L'interfaccia mostra STRINGHE, non interi. Per il calcio `side1Primary` e' "3" e il
     * dettaglio e' null, quindi il risultato a schermo e' identico a prima; per il padel il
     * primario e' "40" o "AV" e il dettaglio "6-4 - 3-2". Cosi' la schermata non deve sapere che
     * sport si sta giocando, ed e' anche la stessa forma che l'orologio ricevera' gia' pronta.
     */
    val scoreDisplay: LiveData<ScoreDisplay> = _scoreDisplay

    /** Proietta lo stato del motore sulle LiveData e lo propaga all'orologio. */
    private fun publishEngineState() {
        val (uno, due) = engine.state.headline()
        _scoreDisplay.value = sportRules.display(engine.state)
        updateScore(uno, due)
        persistLiveMatch()
    }

    /**
     * Scrive la partita in corso, creando la riga alla prima azione utile.
     *
     * La riga NON nasce alla costruzione del ViewModel: altrimenti ogni apertura dell'app
     * lascerebbe in cronologia una partita vuota mai giocata. Nasce al primo punto, che e' il
     * momento in cui esiste davvero qualcosa da non perdere.
     *
     * Una scrittura per punto su Dispatchers.IO. E' il prezzo per cui, oggi, il cronometro
     * sopravvive alla morte del processo (il service lo persiste per conto suo) mentre punteggio
     * e registro no: si torna con il cronometro a 12:34 e il tabellone a 0-0.
     */
    private fun persistLiveMatch() {
        val (uno, due) = engine.state.headline()
        val log = MatchLogCodec.encode(engine.log)
        viewModelScope.launch {
            val id = currentMatchId
            if (id == null) {
                if (uno == 0 && due == 0 && engine.log.isEmpty()) return@launch
                currentMatchId =
                    matchDao.insert(
                        Match(
                            team1Id = 1,
                            team2Id = 2,
                            team1Score = uno,
                            team2Score = due,
                            timestamp = System.currentTimeMillis(),
                            isActive = true,
                            sportId = sportRules.id,
                            eventLog = log,
                        ),
                    )
            } else {
                matchDao.updateLiveMatch(id.toInt(), uno, due, log)
            }
        }
    }

    /**
     * Ripristina una partita rimasta aperta.
     *
     * Volutamente ASINCRONO e successivo a [startNewMatch]: la costruzione del ViewModel continua
     * a lasciare il tabellone a 0-0 esattamente come prima, e il ripristino arriva dopo, se c'e'
     * qualcosa da ripristinare. Cosi' il comportamento predefinito non cambia e nessun test
     * esistente cambia di significato.
     */
    private fun restoreActiveMatchIfAny() {
        viewModelScope.launch {
            val attiva = matchDao.getActiveMatchOnce() ?: return@launch
            currentMatchId = attiva.matchId.toLong()
            val eventi = MatchLogCodec.decode(attiva.eventLog)
            if (eventi != null) {
                engine.restoreLog(eventi)
                val (uno, due) = engine.state.headline()
                _scoreDisplay.postValue(sportRules.display(engine.state))
                updateScore(uno, due)
            } else {
                // Cronologia illeggibile (formato piu' recente, riga corrotta): si recupera
                // comunque il punteggio di testata invece di perdere la partita. Degradare una
                // riga di cronologia e' accettabile; perdere il punteggio no.
                Log.w("MainViewModel", "eventLog illeggibile per la partita ${attiva.matchId}: recupero il solo punteggio")
                seedEngineFromAbsolute(attiva.team1Score, attiva.team2Score)
                updateScore(attiva.team1Score, attiva.team2Score)
            }
            addMatchEvent("Partita ripresa")
        }
    }

    /**
     * Riallinea il motore a un punteggio ASSOLUTO arrivato dall'orologio.
     *
     * Finche' il protocollo non e' versionato, l'orologio manda la coppia di interi e non gli
     * eventi che l'hanno prodotta. Per uno sport a contatore semplice la ricostruzione e' esatta:
     * un punteggio N-M *e'* N punti a un lato e M all'altro. Senza questo, motore e schermo
     * divergerebbero e il primo tocco locale farebbe saltare il punteggio all'indietro.
     */
    private fun seedEngineFromAbsolute(
        team1: Int,
        team2: Int,
    ) {
        val eventi =
            List(team1.coerceAtLeast(0)) { ScoringEvent.Point(side = 1) } +
                List(team2.coerceAtLeast(0)) { ScoringEvent.Point(side = 2) }
        engine.restore(eventi)
        _scoreDisplay.postValue(sportRules.display(engine.state))
    }

    fun addScore(teamId: Int) {
        engine.apply(ScoringEvent.Point(side = teamId))
        publishEngineState()

        triggerHapticFeedback()

        val players = if (teamId == 1) team1Players.value else team2Players.value
        if (!players.isNullOrEmpty()) {
            showSelectScorerDialog.postValue(Pair(teamId, players))
        } else {
            addScorer(teamId, null) // No player to select, just log the goal
        }
    }

    /**
     * Applica un punto arrivato come INTENZIONE dall'orologio.
     *
     * Non riusa [addScore] di proposito. [addScore] apre il dialogo del marcatore, che e' una
     * domanda rivolta a chi ha il TELEFONO in mano; l'intenzione invece la genera chi guarda
     * l'orologio, spesso con il telefono in tasca o su una panchina. Quel dialogo resterebbe
     * aperto a bloccare la schermata e finirebbe per attribuire il punto a una scelta fatta minuti
     * dopo, o alla persona sbagliata. Il punto viene quindi registrato senza marcatore, come fa
     * gia' [addScore] quando la rosa e' vuota: l'attribuzione ha il suo canale, MSG_SCORER_SELECTED,
     * che l'orologio manda quando l'utente sceglie li'.
     *
     * Nemmeno la vibrazione viene riprodotta: e' la conferma tattile di un tocco locale, e chi ha
     * toccato sta guardando l'orologio.
     */
    private fun addRemotePoint(side: Int) {
        engine.apply(ScoringEvent.Point(side = side))
        publishEngineState()

        // L'orologio manda l'intenzione E POI, se lo sport attribuisce il marcatore e c'e' un
        // roster, la scelta del giocatore. Registrare il gol qui e di nuovo all'arrivo
        // dell'attribuzione produrrebbe DUE voci nel registro e DUE annullamenti in coda per un
        // solo punto. Lo si registra qui solo quando l'attribuzione non arrivera' mai -- che e' la
        // stessa condizione del percorso locale in addScore.
        val roster = if (side == 1) team1Players.value else team2Players.value
        val attribuiraDopo = sportRules.capabilities.attributesScorer && !roster.isNullOrEmpty()
        if (!attribuiraDopo) {
            addScorer(side, null)
        }
    }

    fun subtractScore(teamId: Int) {
        val prima = engine.state.headline()
        engine.apply(ScoringEvent.Correction(side = teamId))
        val dopo = engine.state.headline()

        // Come prima: gli effetti collaterali scattano solo se il punteggio e' davvero cambiato.
        // A zero la correzione e' un'operazione nulla, e resta tale.
        if (dopo != prima) {
            publishEngineState()
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
                // Incremento atomico lato database invece di mutare l'istanza in memoria e
                // riscrivere l'intera riga: _allPlayers e i roster tengono grafi di oggetti
                // DIVERSI per lo stesso giocatore, quindi un @Update di riga intera partendo da
                // una copia stantia riportava indietro i gol segnati nel frattempo.
                playerDao.incrementGoals(playerWithRoles.player.playerId)

                val rolesString = playerWithRoles.roles.joinToString(", ") { it.name }
                addMatchEvent(
                    "Goal",
                    team = team,
                    player = playerWithRoles.player.playerName,
                    playerRole = rolesString,
                    type = MatchEventType.SCORE,
                )

                // Track for Undo
                actionStack.addLast(GoalAction(team, playerWithRoles.player.playerId, System.currentTimeMillis()))
            } else {
                // No specific player, just log a goal for the team
                addMatchEvent("Goal", team = team, player = teamName, type = MatchEventType.SCORE)

                // Track for Undo (null playerId)
                actionStack.addLast(GoalAction(team, null, System.currentTimeMillis()))
            }
            _canUndo.postValue(true)
        }
    }

    /**
     * Attributes a goal to a player chosen on the Wear device.
     * The score itself is synchronized separately via [ACTION_SCORE_UPDATE]; this only
     * records the scorer (player goal count + match event). Matches the player by name
     * against the known roster; falls back to a name-only event if not found.
     */
    fun attributeRemoteScorer(
        team: Int,
        playerName: String,
        playerId: Int? = null,
    ) {
        val roster = _allPlayers.value
        // L'id vince sul nome: due omonimi erano indistinguibili e il nome e' modificabile.
        // La ricerca per nome resta come ripiego, perche' un orologio non aggiornato manda
        // ancora soltanto quello.
        val match =
            playerId?.let { id -> roster?.find { it.player.playerId == id } }
                ?: roster?.find { it.player.playerName == playerName }
        if (match != null) {
            addScorer(team, match)
        } else {
            addMatchEvent("Goal", team = team, player = playerName, type = MatchEventType.SCORE)
            actionStack.addLast(GoalAction(team, null, System.currentTimeMillis()))
            _canUndo.postValue(true)
        }
    }

    fun undoLastGoal() {
        val lastAction = actionStack.removeLastOrNull()
        if (lastAction != null) {
            _canUndo.postValue(actionStack.isNotEmpty())

            viewModelScope.launch {
                // 1. Revert Score -- rifacendo il fold, non sottraendo a mano. Per il calcio
                // il risultato e' identico; per uno sport a set sara' l'unico modo corretto di
                // riattraversare all'indietro un confine di game.
                if (engine.canUndo()) {
                    engine.undo()
                    publishEngineState()
                }

                // 2. Revert Player Stats if needed
                // Decremento atomico: non dipende piu' dal fatto che _allPlayers contenga gia' il
                // giocatore ne' che la sua copia sia aggiornata. Prima, annullare subito dopo aver
                // segnato saltava il decremento perche' il Flow non aveva ancora riemesso.
                lastAction.playerId?.let { playerId -> playerDao.decrementGoals(playerId) }

                // 3. Remove from Match Events
                synchronized(matchEventLog) {
                    val index =
                        matchEventLog.indexOfFirst {
                            it.type == MatchEventType.SCORE && it.team == lastAction.teamId
                        }
                    if (index != -1) {
                        matchEventLog.removeAt(index)
                        _matchEvents.postValue(matchEventLog.toList())
                    }
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
        type: MatchEventType = MatchEventType.INFO,
    ) {
        val timeFormat = SimpleDateFormat("mm:ss", Locale.getDefault())
        val timestamp = timeFormat.format(Date(matchTimerValue.value ?: 0L))

        synchronized(matchEventLog) {
            // in testa: ordine cronologico inverso
            matchEventLog.add(0, MatchEvent(timestamp, event, team, player, playerRole, type))
            _matchEvents.postValue(matchEventLog.toList())
        }
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

            val uno = team1Score.value ?: 0
            val due = team2Score.value ?: 0
            val log = MatchLogCodec.encode(engine.log)
            val adesso = System.currentTimeMillis()

            // Se una riga viva esiste gia' la si CHIUDE, invece di inserirne una seconda: la
            // partita e' la stessa, e duplicarla falserebbe presenze e statistiche.
            val vivaId = currentMatchId
            val matchId =
                if (vivaId != null) {
                    matchDao.finalizeMatch(vivaId.toInt(), uno, due, log, adesso)
                    vivaId
                } else {
                    matchDao.insert(
                        Match(
                            team1Id = 1, // Default team 1 ID
                            team2Id = 2, // Default team 2 ID
                            team1Score = uno,
                            team2Score = due,
                            timestamp = adesso,
                            sportId = sportRules.id,
                            eventLog = log,
                        ),
                    )
                }

            val team1Roster = team1Players.value ?: emptyList()
            val team2Roster = team2Players.value ?: emptyList()
            val allMatchPlayers = team1Roster + team2Roster

            val playersToUpdate =
                allMatchPlayers.map {
                    it.player.apply { appearances++ }
                }
            playerDao.updatePlayers(playersToUpdate)

            val matchPlayerCrossRefs =
                team1Roster.map { MatchPlayerCrossRef(matchId.toInt(), it.player.playerId, teamNumber = 1) } +
                    team2Roster.map { MatchPlayerCrossRef(matchId.toInt(), it.player.playerId, teamNumber = 2) }
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
            val data = mapOf(WearConstants.KEY_TEAM_COLOR to color)
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
            // 1. Scores -- updateScore spedisce sia il v1 sia il v2: non aggiungere un secondo
            // invio qui, sarebbe un duplicato.
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

            // 7. Player roster (so the watch can attribute scorers)
            _allPlayers.value?.let { sendPlayersUpdate(it) }
        }
    }

    /**
     * Prepara l'export della partita corrente verso Padel Elite.
     *
     * La validazione la fa :core e ritorna un risultato TIPIZZATO invece di lanciare: cosi'
     * l'interfaccia puo' dire all'utente che cosa manca -- quali giocatori non sono collegati,
     * per nome -- invece di limitarsi a rifiutare.
     */
    fun buildExport(): ExportResult {
        val uno = _team1Players.value.orEmpty()
        val due = _team2Players.value.orEmpty()
        val roster =
            uno.map { MatchPlayer(it.player.playerId, it.player.playerName, 1) } +
                due.map { MatchPlayer(it.player.playerId, it.player.playerName, 2) }
        val padelIds =
            (uno + due).mapNotNull { p -> p.player.padelPlayerId?.let { p.player.playerId to it } }.toMap()
        return MatchExporter.build(engine, roster, padelIds)
    }

    /** Etichetta della partita per il nome del file. Il timestamp lo aggiunge chi scrive. */
    fun exportFileLabel(): String {
        val squadre = "${_team1Name.value.orEmpty()}-vs-${_team2Name.value.orEmpty()}"
        return "${sportRules.id}-$squadre"
    }

    fun shareMatchResults() {
        val data =
            MatchReportData(
                team1Name = _team1Name.value ?: "TEAM 1",
                team1Score = _team1Score.value ?: 0,
                team1Color = _team1Color.value,
                team1Players = _team1Players.value ?: emptyList(),
                team2Name = _team2Name.value ?: "TEAM 2",
                team2Score = _team2Score.value ?: 0,
                team2Color = _team2Color.value,
                team2Players = _team2Players.value ?: emptyList(),
                matchEvents = _matchEvents.value ?: emptyList(),
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
            connectionManager.cleanup()
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error during ViewModel cleanup", e)
        }
        super.onCleared()
    }
}
