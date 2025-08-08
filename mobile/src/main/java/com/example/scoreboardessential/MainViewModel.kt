package com.example.scoreboardessential

import android.app.Application
import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.scoreboardessential.database.AppDatabase
import com.example.scoreboardessential.database.Match
import com.example.scoreboardessential.database.MatchDao
import com.example.scoreboardessential.database.Player
import com.example.scoreboardessential.database.PlayerDao
import com.example.scoreboardessential.database.Team
import com.example.scoreboardessential.database.TeamDao
import com.example.scoreboardessential.repository.MatchRepository
import com.example.scoreboardessential.utils.SingleLiveEvent
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// ViewModel for the main screen of the app.
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val matchRepository: MatchRepository = (application as ScoreboardEssentialApplication).matchRepository
    private val playerDao: PlayerDao
    private val teamDao: TeamDao
    private val matchDao: MatchDao

    // Scores and team names are now Flows from the repository, converted to LiveData
    val team1Score: LiveData<Int> = matchRepository.team1Score.asLiveData()
    val team2Score: LiveData<Int> = matchRepository.team2Score.asLiveData()
    val team1Name: LiveData<String> = matchRepository.team1Name.asLiveData()
    val team2Name: LiveData<String> = matchRepository.team2Name.asLiveData()
    val team1Color: LiveData<Int> = matchRepository.team1Color.asLiveData()
    val team2Color: LiveData<Int> = matchRepository.team2Color.asLiveData()

    // Event to trigger the "Select Scorer" dialog
    val showSelectScorerDialog = SingleLiveEvent<Int>()

    val timerValue: LiveData<Long> = matchRepository.timerValue.asLiveData()
    val isTimerRunning: LiveData<Boolean> = matchRepository.isTimerRunning.asLiveData()

    private val _team1Scorers = MutableLiveData<List<String>>(emptyList())
    val team1Scorers: LiveData<List<String>> = _team1Scorers

    private val _team2Scorers = MutableLiveData<List<String>>(emptyList())
    val team2Scorers: LiveData<List<String>> = _team2Scorers

    val team1Players: LiveData<List<String>>
    val team2Players: LiveData<List<String>>

    // The countdown timer.
    private var timer: CountDownTimer? = null
    private val dataClient: DataClient = Wearable.getDataClient(application)

    private var team1: Team? = null
    private var team2: Team? = null

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
        val db = AppDatabase.getDatabase(application)
        matchDao = db.matchDao()
        teamDao = db.teamDao()
        playerDao = db.playerDao()

        viewModelScope.launch {
            if (matchRepository.team1Id.value == 0L) {
                val team1 = Team(name = "Team 1", color = 0, logoUri = null)
                val team2 = Team(name = "Team 2", color = 0, logoUri = null)
                val id1 = teamDao.insertWithId(team1)
                val id2 = teamDao.insertWithId(team2)
                matchRepository.setTeamIds(id1, id2)

                // Add default players
                playerDao.insert(Player(teamId = id1.toInt(), name = "Player 1"))
                playerDao.insert(Player(teamId = id1.toInt(), name = "Player 2"))
                playerDao.insert(Player(teamId = id2.toInt(), name = "Player 3"))
                playerDao.insert(Player(teamId = id2.toInt(), name = "Player 4"))
            }
        }

        team1Players = matchRepository.team1Id.flatMapLatest { teamId ->
            playerDao.getPlayersForTeam(teamId.toInt())
        }.map { players -> players.map { it.name } }.asLiveData()

        team2Players = matchRepository.team2Id.flatMapLatest { teamId ->
            playerDao.getPlayersForTeam(teamId.toInt())
        }.map { players -> players.map { it.name } }.asLiveData()


        isTimerRunning.observeForever(timerRunningObserver)
    }

    override fun onCleared() {
        super.onCleared()
        isTimerRunning.removeObserver(timerRunningObserver)
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

    fun setTeamColor(team: Int, color: Int) {
        viewModelScope.launch {
            matchRepository.setTeamColor(team, color)
        }
    }

    fun addPlayer(teamId: Int, name: String) {
        viewModelScope.launch {
            playerDao.insert(Player(teamId = teamId, name = name))
        }
    }

    fun deletePlayer(player: Player) {
        viewModelScope.launch {
            playerDao.delete(player)
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
            val id1 = matchRepository.team1Id.value
            val id2 = matchRepository.team2Id.value
            val name1 = team1Name.value ?: "Team 1"
            val name2 = team2Name.value ?: "Team 2"
            val color1 = team1Color.value ?: 0
            val color2 = team2Color.value ?: 0

            // This assumes logo URIs are handled elsewhere and might be null
            teamDao.update(Team(id = id1.toInt(), name = name1, color = color1, logoUri = null))
            teamDao.update(Team(id = id2.toInt(), name = name2, color = color2, logoUri = null))

            val match = Match(
                team1Id = id1.toInt(),
                team2Id = id2.toInt(),
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
            dataMap.putLong(DataSyncObject.TIMER_KEY, timerValue.value ?: 0)
            dataMap.putBoolean(DataSyncObject.TIMER_STATE_KEY, isTimerRunning.value ?: false)
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
            dataMap.putLong(DataSyncObject.SET_TIMER_KEY, timerValue.value ?: 0)
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
