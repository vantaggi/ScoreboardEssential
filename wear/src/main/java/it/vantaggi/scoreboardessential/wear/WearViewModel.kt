package it.vantaggi.scoreboardessential.wear

import android.app.Application
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.DataMap
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager
import it.vantaggi.scoreboardessential.shared.PlayerData
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Il punteggio gia' impaginato dal telefono, piu' le capacita' dello sport.
 *
 * L'orologio non esegue mai regole: rende stringhe. Per questo qui non c'e' nessun numero da
 * sommare, e aggiungere uno sport non richiede una riga di codice su questo lato.
 */
data class WearScoreState(
    val side1Primary: String,
    val side1Secondary: String,
    val side2Primary: String,
    val side2Secondary: String,
    val periodLabel: String,
    val hasClock: Boolean,
    val hasAuxTimer: Boolean,
    val attributesScorer: Boolean,
    val decrementIsUndo: Boolean,
) {
    companion object {
        private const val TAG = "WearScoreState"

        /**
         * Nessuna versione viene mai rifiutata: un telefono piu' recente puo' aggiungere chiavi, e
         * ogni lettura ha un valore di default, quindi non lancia. Un orologio vecchio deve
         * mostrare cio' che capisce, non morire.
         *
         * I default delle capacita' sono quelli del calcio: se il telefono le omettesse, la
         * schermata resterebbe quella di oggi invece di perdere comandi.
         */
        fun fromDataMap(dataMap: DataMap): WearScoreState {
            val version = dataMap.getInt(WearConstants.KEY_PROTO_VERSION, WearConstants.PROTO_VERSION)
            if (version > WearConstants.PROTO_VERSION) {
                Log.i(TAG, "Protocollo v$version piu' recente di v${WearConstants.PROTO_VERSION}: leggo cio' che conosco")
            }
            return WearScoreState(
                side1Primary = dataMap.getString(WearConstants.KEY_SIDE1_PRIMARY, ""),
                side1Secondary = dataMap.getString(WearConstants.KEY_SIDE1_SECONDARY, ""),
                side2Primary = dataMap.getString(WearConstants.KEY_SIDE2_PRIMARY, ""),
                side2Secondary = dataMap.getString(WearConstants.KEY_SIDE2_SECONDARY, ""),
                periodLabel = dataMap.getString(WearConstants.KEY_PERIOD_LABEL, ""),
                hasClock = dataMap.getBoolean(WearConstants.KEY_CAP_HAS_CLOCK, true),
                hasAuxTimer = dataMap.getBoolean(WearConstants.KEY_CAP_HAS_AUX_TIMER, true),
                attributesScorer = dataMap.getBoolean(WearConstants.KEY_CAP_ATTRIBUTES_SCORER, true),
                decrementIsUndo = dataMap.getBoolean(WearConstants.KEY_CAP_DECREMENT_IS_UNDO, false),
            )
        }
    }
}

sealed class KeeperTimerState {
    object Hidden : KeeperTimerState()

    data class Running(
        val secondsRemaining: Int,
    ) : KeeperTimerState()

    object Finished : KeeperTimerState()
}

class WearViewModel(
    application: Application,
    // Iniettabile come i client dentro OptimizedWearDataSync stesso: sotto test
    // il costruttore di default avvia i client GMS reali, il cui GoogleApiHandler
    // muore sul looper di Robolectric.
    private val connectionManager: OptimizedWearDataSync = OptimizedWearDataSync(application),
) : AndroidViewModel(application) {
    /**
     * Costruttore richiesto da [androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory],
     * che lo cerca PER RIFLESSIONE con esattamente la firma (Application).
     *
     * Non basta il valore di default sul parametro secondario: i default di Kotlin non
     * generano un costruttore separato, quindi la factory non lo troverebbe e MainActivity
     * crasherebbe all'avvio con NoSuchMethodException. E' un contratto invisibile nel
     * codice sorgente, per questo e' esplicito qui e coperto da WearViewModelConstructorTest.
     */
    constructor(application: Application) : this(application, OptimizedWearDataSync(application))

    companion object {
        private const val TAG = "WearViewModel"
    }

    // Stato v2: il telefono e' autoritativo, qui c'e' solo il testo da mettere a schermo.
    private val _scoreState = MutableStateFlow<WearScoreState?>(null)
    val scoreState = _scoreState.asStateFlow()

    /**
     * Visto un v2, non si torna indietro: lo stesso telefono continua a scrivere anche il v1 per
     * gli orologi non aggiornati, e i due formati si sovrascriverebbero a vicenda.
     */
    private var protocolV2Seen = false

    /**
     * Seminato con l'orologio di sistema, non da zero.
     *
     * Il telefono ricorda l'ultima sequenza vista per nodo e scarta cio' che non la supera.
     * Ripartendo da 1 dopo un riavvio dell'app, i primi tocchi verrebbero scartati come "gia'
     * visti", tanti quanti se ne erano fatti prima. Dal tempo corrente la monotonia attraversa i
     * riavvii.
     */
    private var intentSequence = System.currentTimeMillis()

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
    private var keeperTimerDuration = 300000L // 5 minutes default

    fun updateKeeperTimerDuration(millis: Long) {
        keeperTimerDuration = millis
        // Update progress if not running to reflect new duration immediately
        if (_keeperTimer.value is KeeperTimerState.Hidden || _keeperTimer.value is KeeperTimerState.Finished) {
            _keeperProgress.value = (millis / 1000).toInt()
        }
    }

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

    init {
        viewModelScope.launch {
            connectionManager.sendMessage(it.vantaggi.scoreboardessential.shared.communication.WearConstants.MSG_REQUEST_SYNC)
        }
    }

    fun clearPlayerSelectionEvent() {
        _showPlayerSelection.value = null
    }

    /** Replaces the cached roster pushed from the phone. */
    fun setAllPlayers(players: List<PlayerData>) {
        _allPlayers.value = players
    }

    fun updateScoresFromMobile(
        team1Score: Int,
        team2Score: Int,
    ) {
        if (protocolV2Seen) return
        _team1Score.value = team1Score
        _team2Score.value = team2Score
    }

    /** Il punteggio arriva gia' impaginato: da qui in poi il v1 sullo stesso telefono e' rumore. */
    fun applyStateV2(state: WearScoreState) {
        protocolV2Seen = true
        _scoreState.value = state
    }

    // --- Score Management ---
    fun setTeamNames(
        team1Name: String,
        team2Name: String,
    ) {
        _team1Name.value = team1Name
        _team2Name.value = team2Name
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

    fun incrementScore(team: Int) {
        if (team != 1 && team != 2) return
        sendScoreIntent(team, WearConstants.INTENT_POINT)
        if (protocolV2Seen) {
            // Il punteggio lo decide il telefono: qui resta solo la risposta al tocco.
            triggerShortVibration()
            if (_scoreState.value?.attributesScorer == true && _allPlayers.value.isNotEmpty()) {
                _showPlayerSelection.value = team
            }
            return
        }
        modifyScore(team, 1)
    }

    fun decrementScore(team: Int) {
        if (team != 1 && team != 2) return
        if (protocolV2Seen) {
            val tipo =
                if (_scoreState.value?.decrementIsUndo == true) {
                    WearConstants.INTENT_UNDO
                } else {
                    WearConstants.INTENT_CORRECTION
                }
            sendScoreIntent(team, tipo)
            triggerShortVibration()
            return
        }
        modifyScore(team, -1)
    }

    /**
     * Il tocco e' un'INTENZIONE, non uno stato. Va su MessageClient, che non coalesce: due punti a
     * un secondo di distanza restano due messaggi, mentre sullo stesso path DataClient il secondo
     * put sostituiva il primo e un punto spariva.
     *
     * Il lato resta SEMPRE 1 o 2; il significato del gesto viaggia in un campo suo. La prima
     * stesura caricava il lato di tre semantiche (negativi per la correzione, zero per
     * l'annullamento) e il telefono li scartava con la validazione del numero di squadra: sul
     * calcio, con entrambi i lati aggiornati, il tocco di sottrazione sarebbe diventato inerte.
     */
    private fun sendScoreIntent(
        side: Int,
        kind: String,
    ) {
        val seq = ++intentSequence
        viewModelScope.launch {
            val payload =
                DataMap().apply {
                    putInt(WearConstants.KEY_SIDE, side)
                    putString(WearConstants.KEY_INTENT_KIND, kind)
                    putLong(WearConstants.KEY_SEQ, seq)
                }
            connectionManager.sendMessage(WearConstants.MSG_SCORE_INTENT, payload.toByteArray())
        }
    }

    private fun modifyScore(
        team: Int,
        delta: Int,
    ) {
        if (team != 1 && team != 2) return
        val currentScore = if (team == 1) _team1Score.value else _team2Score.value
        val newScore = (currentScore + delta).coerceAtLeast(0)

        if (newScore != currentScore) {
            val s1 = if (team == 1) newScore else _team1Score.value
            val s2 = if (team == 1) _team2Score.value else newScore
            updateScore(s1, s2)
            triggerShortVibration()

            // On a goal (increment), prompt to attribute a scorer if a roster is available.
            if (delta > 0 && _allPlayers.value.isNotEmpty()) {
                _showPlayerSelection.value = team
            }
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

    fun syncMatchTimer(
        millis: Long,
        isRunning: Boolean,
    ) {
        matchTimeInSeconds = millis / 1000
        isMatchTimerRunning = isRunning

        // Update display immediately
        val minutes = matchTimeInSeconds / 60
        val seconds = matchTimeInSeconds % 60
        _matchTimer.value = String.format("%02d:%02d", minutes, seconds)

        if (isRunning) {
            startMatchTimerInternal()
        } else {
            matchTimerJob?.cancel()
        }
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

    fun resetMatch(fromRemote: Boolean = false) {
        // Update local state without sending data if fromRemote is true
        if (fromRemote) {
            _team1Score.value = 0
            _team2Score.value = 0
        } else {
            updateScore(0, 0)
        }

        resetMatchTimer(fromRemote)
        resetKeeperTimer(fromRemote)

        if (!fromRemote) {
            // Also signal match reset to mobile
            viewModelScope.launch {
                val data = mapOf(it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_MATCH_ACTIVE to false)
                connectionManager.sendData(
                    path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_MATCH_STATE,
                    data = data,
                )
            }
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
                val startTime = System.currentTimeMillis() - (matchTimeInSeconds * 1000)
                while (isMatchTimerRunning) {
                    val now = System.currentTimeMillis()
                    val diff = now - startTime
                    matchTimeInSeconds = diff / 1000

                    val minutes = matchTimeInSeconds / 60
                    val seconds = matchTimeInSeconds % 60
                    _matchTimer.value = String.format("%02d:%02d", minutes, seconds)

                    // drift correction
                    val nextSecond = (matchTimeInSeconds + 1) * 1000
                    val delayMillis = nextSecond - diff
                    delay(if (delayMillis > 0) delayMillis else 100L)
                }
            }
    }

    private fun pauseMatchTimerInternal() {
        matchTimerJob?.cancel()
    }

    fun resetMatchTimer(fromRemote: Boolean = false) {
        matchTimeInSeconds = 0L
        isMatchTimerRunning = false
        matchTimerJob?.cancel()
        _matchTimer.value = "00:00"

        if (!fromRemote) {
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

    fun resetKeeperTimer(fromRemote: Boolean = false) {
        keeperCountDownTimer?.cancel()
        vibrator?.cancel()
        _keeperTimer.value = KeeperTimerState.Hidden
        _keeperProgress.value = (keeperTimerDuration / 1000).toInt()

        if (!fromRemote) {
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

    override fun onCleared() {
        matchTimerJob?.cancel()
        keeperCountDownTimer?.cancel()
        vibrator?.cancel()
        connectionManager.cleanup()
        super.onCleared()
    }
}
