package com.example.scoreboardessential

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {

    private lateinit var timerTextView: TextView
    private lateinit var team1ScoreTextView: TextView
    private lateinit var team2ScoreTextView: TextView

    private var timer: CountDownTimer? = null
    private var isTimerRunning = false
    private var remainingTimeInMillis = 0L

    private var team1Score = 0
    private var team2Score = 0

    private lateinit var dataClient: DataClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dataClient = Wearable.getDataClient(this)

        // initialize views
        timerTextView = findViewById(R.id.timer_text_view)
        team1ScoreTextView = findViewById(R.id.team_1_score_text_view)
        team2ScoreTextView = findViewById(R.id.team_2_score_text_view)

        // set initial scores
        team1ScoreTextView.text = team1Score.toString()
        team2ScoreTextView.text = team2Score.toString()
    }

    private fun sendScoreUpdate() {
        val putDataMapReq = PutDataMapRequest.create(DataSyncObject.SCORE_PATH).apply {
            dataMap.putInt(DataSyncObject.TEAM1_SCORE_KEY, team1Score)
            dataMap.putInt(DataSyncObject.TEAM2_SCORE_KEY, team2Score)
            dataMap.putLong(DataSyncObject.TIMER_KEY, remainingTimeInMillis)
            dataMap.putBoolean(DataSyncObject.TIMER_STATE_KEY, isTimerRunning)
        }
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        dataClient.putDataItem(putDataReq).addOnSuccessListener {
            Log.d("DataSync", "Data sent successfully: $it")
        }.addOnFailureListener {
            Log.e("DataSync", "Data sending failed", it)
        }
    }

    fun startStopTimer(view: View) {
        if (isTimerRunning) {
            // stop the timer
            timer?.cancel()
            isTimerRunning = false
        } else {
            // start the timer
            timer = object : CountDownTimer(remainingTimeInMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    remainingTimeInMillis = millisUntilFinished
                    updateTimerTextView()
                }

                override fun onFinish() {
                    isTimerRunning = false
                }
            }.start()

            isTimerRunning = true
        }
        sendScoreUpdate()
    }

    fun resetTimer(view: View) {
        timer?.cancel()
        isTimerRunning = false
        remainingTimeInMillis = 0
        updateTimerTextView()
        sendScoreUpdate()
    }

    fun addScore(view: View) {
        when (view.id) {
            R.id.team_1_score_text_view -> {
                team1Score++
                team1ScoreTextView.text = team1Score.toString()
            }

            R.id.team_2_score_text_view -> {
                team2Score++
                team2ScoreTextView.text = team2Score.toString()
            }
        }
        sendScoreUpdate()
    }

    fun resetScores(view: View) {
        team1Score = 0
        team2Score = 0
        updateUi(team1Score, team2Score)
        sendScoreUpdate()
    }

    private fun updateTimerTextView() {
        val minutes = (remainingTimeInMillis / 1000) / 60
        val seconds = (remainingTimeInMillis / 1000) % 60
        timerTextView.text = String.format("%02d:%02d", minutes, seconds)
    }

    override fun onResume() {
        super.onResume()
        dataClient.addListener(this)
    }

    override fun onPause() {
        super.onPause()
        dataClient.removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d("DataSync", "onDataChanged: $dataEvents")
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                if (dataItem.uri.path?.compareTo(DataSyncObject.SCORE_PATH) == 0) {
                    val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                    if (dataMap.containsKey(DataSyncObject.TEAM1_SCORE_KEY)) {
                        team1Score = dataMap.getInt(DataSyncObject.TEAM1_SCORE_KEY)
                        team2Score = dataMap.getInt(DataSyncObject.TEAM2_SCORE_KEY)
                        updateUi(team1Score, team2Score)
                    }
                    if (dataMap.containsKey(DataSyncObject.TIMER_KEY)) {
                        remainingTimeInMillis = dataMap.getLong(DataSyncObject.TIMER_KEY)
                        isTimerRunning = dataMap.getBoolean(DataSyncObject.TIMER_STATE_KEY)
                        updateTimerTextView()
                    }
                    if (dataMap.containsKey(DataSyncObject.RESET_KEY)) {
                        team1Score = 0
                        team2Score = 0
                        updateUi(team1Score, team2Score)
                    }
                    if (dataMap.containsKey(DataSyncObject.SET_TIMER_KEY)) {
                        remainingTimeInMillis = dataMap.getLong(DataSyncObject.SET_TIMER_KEY)
                        updateTimerTextView()
                    }
                }
            }
        }
    }

    private fun updateUi(team1Score: Int, team2Score: Int) {
        runOnUiThread {
            team1ScoreTextView.text = team1Score.toString()
            team2ScoreTextView.text = team2Score.toString()
        }
    }
}