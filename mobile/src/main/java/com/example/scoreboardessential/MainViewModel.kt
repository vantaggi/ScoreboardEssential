package com.example.scoreboardessential

import android.app.Application
import android.content.Context
import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.scoreboardessential.database.AppDatabase
import com.example.scoreboardessential.database.Match
import com.example.scoreboardessential.database.MatchDao
import com.example.scoreboardessential.database.Team
import com.example.scoreboardessential.database.TeamDao
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch

// ViewModel for the main screen of the app.
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // The score for team 1.
    private val _team1Score = MutableLiveData(0)
    val team1Score: LiveData<Int> = _team1Score

    // The score for team 2.
    private val _team2Score = MutableLiveData(0)
    val team2Score: LiveData<Int> = _team2Score

    // The name of team 1.
    private val _team1Name = MutableLiveData("Team 1")
    val team1Name: LiveData<String> = _team1Name

    // The name of team 2.
    private val _team2Name = MutableLiveData("Team 2")
    val team2Name: LiveData<String> = _team2Name

    // The current value of the timer in milliseconds.
    private val _timerValue = MutableLiveData(0L)
    val timerValue: LiveData<Long> = _timerValue

    // Whether the timer is currently running.
    private val _isTimerRunning = MutableLiveData(false)
    val isTimerRunning: LiveData<Boolean> = _isTimerRunning

    // The list of scorers for team 1.
    private val _team1Scorers = MutableLiveData<List<String>>(emptyList())
    val team1Scorers: LiveData<List<String>> = _team1Scorers

    // The list of scorers for team 2.
    private val _team2Scorers = MutableLiveData<List<String>>(emptyList())
    val team2Scorers: LiveData<List<String>> = _team2Scorers

    // The list of players for team 1.
    private val _team1Players = MutableLiveData<List<String>>(listOf("Player 1", "Player 2", "Player 3"))
    val team1Players: LiveData<List<String>> = _team1Players

    // The list of players for team 2.
    private val _team2Players = MutableLiveData<List<String>>(listOf("Player 4", "Player 5", "Player 6"))
    val team2Players: LiveData<List<String>> = _team2Players

    // The countdown timer.
    private var timer: CountDownTimer? = null
    private val dataClient: DataClient = Wearable.getDataClient(application)
    private val matchDao: MatchDao
    private val teamDao: TeamDao

    private var team1: Team? = null
    private var team2: Team? = null

    private val sharedPreferences = application.getSharedPreferences("match_state", Context.MODE_PRIVATE)

    init {
        matchDao = AppDatabase.getDatabase(application).matchDao()
        teamDao = AppDatabase.getDatabase(application).teamDao()
        restoreState()
    }

    // Sets the name of team 1.
    fun setTeam1Name(name: String) {
        _team1Name.value = name
    }

    // Sets the name of team 2.
    fun setTeam2Name(name: String) {
        _team2Name.value = name
    }

    // Adds a point to team 1's score.
    fun addTeam1Score() {
        _team1Score.value = (_team1Score.value ?: 0) + 1
        sendScoreUpdate()
    }

    // Subtracts a point from team 1's score.
    fun subtractTeam1Score() {
        if ((_team1Score.value ?: 0) > 0) {
            _team1Score.value = (_team1Score.value ?: 0) - 1
            sendScoreUpdate()
        }
    }

    // Adds a point to team 2's score.
    fun addTeam2Score() {
        _team2Score.value = (_team2Score.value ?: 0) + 1
        sendScoreUpdate()
    }

    // Subtracts a point from team 2's score.
    fun subtractTeam2Score() {
        if ((_team2Score.value ?: 0) > 0) {
            _team2Score.value = (_team2Score.value ?: 0) - 1
            sendScoreUpdate()
        }
    }

    // Sets the timer to the given number of seconds.
    fun setTimer(seconds: Long) {
        _timerValue.value = seconds * 1000
        sendSetTimerUpdate()
    }

    // Starts or stops the timer.
    fun startStopTimer() {
        if (_isTimerRunning.value == true) {
            timer?.cancel()
            _isTimerRunning.value = false
        } else {
            _isTimerRunning.value = true
            timer = object : CountDownTimer(_timerValue.value ?: 0, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    _timerValue.value = millisUntilFinished
                }

                override fun onFinish() {
                    _isTimerRunning.value = false
                }
            }.start()
        }
        sendScoreUpdate()
    }

    // Resets the timer.
    fun resetTimer() {
        timer?.cancel()
        _isTimerRunning.value = false
        _timerValue.value = 0
        sendScoreUpdate()
    }

    // Adds a scorer to the list of scorers for the given team.
    fun addScorer(team: Int, scorer: String) {
        if (team == 1) {
            _team1Scorers.value = _team1Scorers.value?.plus(scorer)
        } else {
            _team2Scorers.value = _team2Scorers.value?.plus(scorer)
        }
    }

    // Resets the scores and the list of scorers.
    fun resetScores() {
        viewModelScope.launch {
            team1?.let { teamDao.update(it.copy(name = _team1Name.value!!)) }
            team2?.let { teamDao.update(it.copy(name = _team2Name.value!!)) }

            val match = Match(
                team1Id = team1!!.id,
                team2Id = team2!!.id,
                team1Score = _team1Score.value!!,
                team2Score = _team2Score.value!!,
                timestamp = System.currentTimeMillis()
            )
            matchDao.insert(match)
        }

        _team1Score.value = 0
        _team2Score.value = 0
        _team1Scorers.value = emptyList()
        _team2Scorers.value = emptyList()
        sendResetUpdate()
    }

    // Saves the current state of the match to SharedPreferences.
    fun saveState() {
        with(sharedPreferences.edit()) {
            putInt("team1_score", _team1Score.value ?: 0)
            putInt("team2_score", _team2Score.value ?: 0)
            putString("team1_name", _team1Name.value ?: "Team 1")
            putString("team2_name", _team2Name.value ?: "Team 2")
            putLong("timer_value", _timerValue.value ?: 0)
            putBoolean("is_timer_running", _isTimerRunning.value ?: false)
            putStringSet("team1_scorers", _team1Scorers.value?.toSet())
            putStringSet("team2_scorers", _team2Scorers.value?.toSet())
            apply()
        }
    }

    // Restores the state of the match from SharedPreferences.
    private fun restoreState() {
        _team1Score.value = sharedPreferences.getInt("team1_score", 0)
        _team2Score.value = sharedPreferences.getInt("team2_score", 0)
        _team1Name.value = sharedPreferences.getString("team1_name", "Team 1")
        _team2Name.value = sharedPreferences.getString("team2_name", "Team 2")
        _timerValue.value = sharedPreferences.getLong("timer_value", 0)
        _isTimerRunning.value = sharedPreferences.getBoolean("is_timer_running", false)
        _team1Scorers.value = sharedPreferences.getStringSet("team1_scorers", emptySet())?.toList()
        _team2Scorers.value = sharedPreferences.getStringSet("team2_scorers", emptySet())?.toList()

        if (_isTimerRunning.value == true) {
            startStopTimer()
        }
    }

    // Sends the current score and timer state to the Wear OS device.
    private fun sendScoreUpdate() {
        val putDataMapReq = PutDataMapRequest.create(DataSyncObject.SCORE_PATH).apply {
            dataMap.putInt(DataSyncObject.TEAM1_SCORE_KEY, _team1Score.value ?: 0)
            dataMap.putInt(DataSyncObject.TEAM2_SCORE_KEY, _team2Score.value ?: 0)
            dataMap.putLong(DataSyncObject.TIMER_KEY, _timerValue.value ?: 0)
            dataMap.putBoolean(DataSyncObject.TIMER_STATE_KEY, _isTimerRunning.value ?: false)
        }
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        dataClient.putDataItem(putDataReq).addOnSuccessListener {
            Log.d("DataSync", "Data sent successfully: $it")
        }.addOnFailureListener {
            Log.e("DataSync", "Data sending failed", it)
        }
    }

    // Sends the current timer value to the Wear OS device.
    private fun sendSetTimerUpdate() {
        val putDataMapReq = PutDataMapRequest.create(DataSyncObject.SCORE_PATH).apply {
            dataMap.putLong(DataSyncObject.SET_TIMER_KEY, _timerValue.value ?: 0)
        }
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        dataClient.putDataItem(putDataReq)
    }

    // Sends a reset message to the Wear OS device.
    private fun sendResetUpdate() {
        val putDataMapReq = PutDataMapRequest.create(DataSyncObject.SCORE_PATH).apply {
            dataMap.putBoolean(DataSyncObject.RESET_KEY, true)
        }
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        dataClient.putDataItem(putDataReq)
    }
}
