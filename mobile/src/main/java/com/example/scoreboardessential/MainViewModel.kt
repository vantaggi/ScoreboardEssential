package com.example.scoreboardessential

import android.app.Application
import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.scoreboardessential.database.AppDatabase
import com.example.scoreboardessential.database.Match
import com.example.scoreboardessential.database.MatchDao
import com.example.scoreboardessential.database.Team
import com.example.scoreboardessential.database.TeamDao
import com.example.scoreboardessential.repository.MatchRepository
import com.example.scoreboardessential.utils.SingleLiveEvent
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch

// ViewModel for the main screen of the app.
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val matchRepository: MatchRepository = (application as ScoreboardEssentialApplication).matchRepository

    // Scores and team names are now Flows from the repository, converted to LiveData
    val team1Score: LiveData<Int> = matchRepository.team1Score.asLiveData()
    val team2Score: LiveData<Int> = matchRepository.team2Score.asLiveData()
    val team1Name: LiveData<String> = matchRepository.team1Name.asLiveData()
    val team2Name: LiveData<String> = matchRepository.team2Name.asLiveData()

    // Event to trigger the "Select Scorer" dialog
    val showSelectScorerDialog = SingleLiveEvent<Int>()

    val timerValue: LiveData<Long> = matchRepository.timerValue.asLiveData()
    val isTimerRunning: LiveData<Boolean> = matchRepository.isTimerRunning.asLiveData()
    val team1Scorers: LiveData<List<String>> = matchRepository.team1Scorers.asLiveData()
    val team2Scorers: LiveData<List<String>> = matchRepository.team2Scorers.asLiveData()

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

    init {
        matchDao = AppDatabase.getDatabase(application).matchDao()
        teamDao = AppDatabase.getDatabase(application).teamDao()

        isTimerRunning.observeForever { running ->
            if (running) {
                if (timer == null) { // To avoid multiple timers
                    startStopTimer()
                }
            } else {
                timer?.cancel()
                timer = null
            }
        }
    }

    // Sets the name of team 1.
    fun setTeam1Name(name: String) {
        viewModelScope.launch {
            matchRepository.setTeam1Name(name)
        }
    }

    // Sets the name of team 2.
    fun setTeam2Name(name: String) {
        viewModelScope.launch {
            matchRepository.setTeam2Name(name)
        }
    }

    // Adds a point to team 1's score.
    fun addTeam1Score() {
        viewModelScope.launch {
            matchRepository.addTeam1Score()
            showSelectScorerDialog.postValue(1)
            sendScoreUpdate()
        }
    }

    // Subtracts a point from team 1's score.
    fun subtractTeam1Score() {
        viewModelScope.launch {
            matchRepository.subtractTeam1Score()
            sendScoreUpdate()
        }
    }

    // Adds a point to team 2's score.
    fun addTeam2Score() {
        viewModelScope.launch {
            matchRepository.addTeam2Score()
            showSelectScorerDialog.postValue(2)
            sendScoreUpdate()
        }
    }

    // Subtracts a point from team 2's score.
    fun subtractTeam2Score() {
        viewModelScope.launch {
            matchRepository.subtractTeam2Score()
            sendScoreUpdate()
        }
    }

    // Sets the timer to the given number of seconds.
    fun setTimer(seconds: Long) {
        viewModelScope.launch {
            matchRepository.setTimer(seconds)
            sendSetTimerUpdate()
        }
    }

    private val timerRunningObserver = Observer<Boolean> { running ->
        if (running) {
            if (timer == null) { // To avoid multiple timers
                timer = object : CountDownTimer(timerValue.value ?: 0, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        viewModelScope.launch {
                            matchRepository.setTimerValue(millisUntilFinished)
                        }
                    }

                    override fun onFinish() {
                        viewModelScope.launch {
                            matchRepository.setTimerRunning(false)
                        }
                    }
                }.start()
            }
        } else {
            timer?.cancel()
            timer = null
        }
    }

    init {
        matchDao = AppDatabase.getDatabase(application).matchDao()
        teamDao = AppDatabase.getDatabase(application).teamDao()

        isTimerRunning.observeForever(timerRunningObserver)
    }

    override fun onCleared() {
        super.onCleared()
        isTimerRunning.removeObserver(timerRunningObserver)
    }

    // Starts or stops the timer.
    fun startStopTimer() {
        val running = isTimerRunning.value == true
        viewModelScope.launch {
            matchRepository.setTimerRunning(!running)
        }
        sendScoreUpdate()
    }

    // Resets the timer.
    fun resetTimer() {
        timer?.cancel()
        viewModelScope.launch {
            matchRepository.setTimerRunning(false)
            matchRepository.setTimerValue(0)
            sendScoreUpdate()
        }
    }

    // Adds a scorer to the list of scorers for the given team.
    fun addScorer(team: Int, scorer: String) {
        viewModelScope.launch {
            matchRepository.addScorer(team, scorer)
        }
    }

    // Resets the scores and the list of scorers.
    fun resetScores() {
        viewModelScope.launch {
            team1?.let { teamDao.update(it.copy(name = team1Name.value!!)) }
            team2?.let { teamDao.update(it.copy(name = team2Name.value!!)) }

            val match = Match(
                team1Id = team1!!.id,
                team2Id = team2!!.id,
                team1Score = team1Score.value!!,
                team2Score = team2Score.value!!,
                timestamp = System.currentTimeMillis()
            )
            matchDao.insert(match)

            matchRepository.resetScores()
            _team1Scorers.value = emptyList()
            _team2Scorers.value = emptyList()
            sendResetUpdate()
        }
    }

    // Sends the current score and timer state to the Wear OS device.
    private fun sendScoreUpdate() {
        val putDataMapReq = PutDataMapRequest.create(DataSyncObject.SCORE_PATH).apply {
            dataMap.putInt(DataSyncObject.TEAM1_SCORE_KEY, team1Score.value ?: 0)
            dataMap.putInt(DataSyncObject.TEAM2_SCORE_KEY, team2Score.value ?: 0)
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
