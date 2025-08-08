package com.example.scoreboardessential

import android.app.Application
import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.scoreboardessential.database.AppDatabase
import com.example.scoreboardessential.database.Match
import com.example.scoreboardessential.database.MatchDao
import com.example.scoreboardessential.database.MatchPlayerCrossRef
import com.example.scoreboardessential.database.Player
import com.example.scoreboardessential.database.PlayerDao
import com.example.scoreboardessential.utils.ScoreUpdateEventBus
import com.example.scoreboardessential.utils.SingleLiveEvent
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val playerDao: PlayerDao
    private val matchDao: MatchDao

    private val _team1Score = MutableLiveData(0)
    val team1Score: LiveData<Int> = _team1Score

    private val _team2Score = MutableLiveData(0)
    val team2Score: LiveData<Int> = _team2Score

    private val _team1Name = MutableLiveData("Team 1")
    val team1Name: LiveData<String> = _team1Name

    private val _team2Name = MutableLiveData("Team 2")
    val team2Name: LiveData<String> = _team2Name

    private val _team1Color = MutableLiveData(0xFF000000.toInt())
    val team1Color: LiveData<Int> = _team1Color

    private val _team2Color = MutableLiveData(0xFF000000.toInt())
    val team2Color: LiveData<Int> = _team2Color

    private val _timerValue = MutableLiveData(0L)
    val timerValue: LiveData<Long> = _timerValue

    private val _team1Players = MutableLiveData<List<Player>>(emptyList())
    val team1Players: LiveData<List<Player>> = _team1Players

    private val _team2Players = MutableLiveData<List<Player>>(emptyList())
    val team2Players: LiveData<List<Player>> = _team2Players

    val showSelectScorerDialog = SingleLiveEvent<Int>()

    private var timer: CountDownTimer? = null
    private var isTimerRunning = false
    private var initialTimerValue = 0L
    private val dataClient: DataClient = Wearable.getDataClient(application)

    init {
        val db = AppDatabase.getDatabase(application)
        matchDao = db.matchDao()
        playerDao = db.playerDao()
        listenForScoreUpdates()
    }

    private fun listenForScoreUpdates() {
        viewModelScope.launch {
            ScoreUpdateEventBus.events.collect { event ->
                _team1Score.postValue(event.team1Score)
                _team2Score.postValue(event.team2Score)
            }
        }
    }

    fun addPlayerToTeam(playerId: Int, teamId: Int) {
        viewModelScope.launch {
            val player = playerDao.getAllPlayers().first().find { it.playerId == playerId }
            if (player != null) {
                if (teamId == 1) {
                    val currentPlayers = _team1Players.value?.toMutableList() ?: mutableListOf()
                    currentPlayers.add(player)
                    _team1Players.postValue(currentPlayers)
                } else {
                    val currentPlayers = _team2Players.value?.toMutableList() ?: mutableListOf()
                    currentPlayers.add(player)
                    _team2Players.postValue(currentPlayers)
                }
            }
        }
    }

    fun addTeam1Score() {
        _team1Score.value = (_team1Score.value ?: 0) + 1
        showSelectScorerDialog.postValue(1)
        sendScoreUpdate()
    }

    fun subtractTeam1Score() {
        _team1Score.value = (_team1Score.value ?: 0) - 1
        sendScoreUpdate()
    }

    fun addTeam2Score() {
        _team2Score.value = (_team2Score.value ?: 0) + 1
        showSelectScorerDialog.postValue(2)
        sendScoreUpdate()
    }

    fun subtractTeam2Score() {
        _team2Score.value = (_team2Score.value ?: 0) - 1
        sendScoreUpdate()
    }

    fun addScorer(team: Int, scorer: String) {
        // Implementazione semplificata - dovresti adattarla secondo le tue necessità
        viewModelScope.launch {
            val players = if (team == 1) _team1Players.value else _team2Players.value
            val player = players?.find { it.playerName == scorer }
            if (player != null) {
                player.goals++
                playerDao.update(player)
            }
        }
    }

    fun setTeam1Name(name: String) {
        _team1Name.value = name
    }

    fun setTeam2Name(name: String) {
        _team2Name.value = name
    }

    fun setTeamColor(team: Int, color: Int) {
        if (team == 1) {
            _team1Color.value = color
        } else {
            _team2Color.value = color
        }
    }

    fun setTimer(seconds: Long) {
        initialTimerValue = seconds * 1000
        _timerValue.value = initialTimerValue
    }

    fun startStopTimer() {
        if (isTimerRunning) {
            timer?.cancel()
            isTimerRunning = false
        } else {
            startTimer()
            isTimerRunning = true
        }
    }

    fun resetTimer() {
        timer?.cancel()
        isTimerRunning = false
        _timerValue.value = initialTimerValue
    }

    private fun startTimer() {
        val currentTime = _timerValue.value ?: 0L
        timer = object : CountDownTimer(currentTime, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _timerValue.postValue(millisUntilFinished)
            }

            override fun onFinish() {
                _timerValue.postValue(0L)
                isTimerRunning = false
            }
        }.start()
    }

    fun resetScores() {
        viewModelScope.launch {
            val matchId = matchDao.insert(
                Match(
                    team1Score = _team1Score.value ?: 0,
                    team2Score = _team2Score.value ?: 0,
                    timestamp = System.currentTimeMillis()
                )
            )

            val allPlayers = (_team1Players.value ?: emptyList()) + (_team2Players.value ?: emptyList())
            for (player in allPlayers) {
                player.appearances++
                playerDao.update(player)
                matchDao.insertMatchPlayerCrossRef(MatchPlayerCrossRef(matchId.toInt(), player.playerId))
            }

            _team1Score.postValue(0)
            _team2Score.postValue(0)
            _team1Players.postValue(emptyList())
            _team2Players.postValue(emptyList())
            sendResetUpdate()
        }
    }

    private fun sendScoreUpdate() {
        val putDataMapReq = PutDataMapRequest.create("/score_update").apply {
            dataMap.putInt("team1_score", _team1Score.value ?: 0)
            dataMap.putInt("team2_score", _team2Score.value ?: 0)
        }
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        dataClient.putDataItem(putDataReq)
    }

    private fun sendResetUpdate() {
        val putDataMapReq = PutDataMapRequest.create("/score_update").apply {
            dataMap.putBoolean("reset", true)
        }
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        dataClient.putDataItem(putDataReq)
    }

    override fun onCleared() {
        super.onCleared()
        timer?.cancel()
    }
}
