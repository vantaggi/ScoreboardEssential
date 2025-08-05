package com.example.scoreboardessential

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.TextView
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

class MainActivity : AppCompatActivity(), DataClient.OnDataChangedListener {

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
        val putDataMapReq = PutDataMapRequest.create(DataSync.SCORE_PATH).apply {
            dataMap.putInt(DataSync.TEAM1_SCORE_KEY, team1Score)
            dataMap.putInt(DataSync.TEAM2_SCORE_KEY, team2Score)
            dataMap.putLong(DataSync.TIMER_KEY, remainingTimeInMillis)
            dataMap.putBoolean(DataSync.TIMER_STATE_KEY, isTimerRunning)
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
    }

    fun resetTimer(view: View) {
        timer?.cancel()
        isTimerRunning = false
        remainingTimeInMillis = 0
        updateTimerTextView()
    }

    fun addScore(view: View) {
        when (view.id) {
            R.id.team_1_add_button -> {
                team1Score++
                team1ScoreTextView.text = team1Score.toString()
            }
            R.id.team_2_add_button -> {
                team2Score++
                team2ScoreTextView.text = team2Score.toString()
            }
        }
        sendScoreUpdate()
    }

    fun subtractScore(view: View) {
        when (view.id) {
            R.id.team_1_subtract_button -> {
                if (team1Score > 0) {
                    team1Score--
                    team1ScoreTextView.text = team1Score.toString()
                }
            }
            R.id.team_2_subtract_button -> {
                if (team2Score > 0) {
                    team2Score--
                    team2ScoreTextView.text = team2Score.toString()
                }
            }
        }
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
                if (dataItem.uri.path?.compareTo(DataSync.SCORE_PATH) == 0) {
                    val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                    team1Score = dataMap.getInt(DataSync.TEAM1_SCORE_KEY)
                    team2Score = dataMap.getInt(DataSync.TEAM2_SCORE_KEY)
                    remainingTimeInMillis = dataMap.getLong(DataSync.TIMER_KEY)
                    isTimerRunning = dataMap.getBoolean(DataSync.TIMER_STATE_KEY)

                    updateUi(team1Score, team2Score)
                    updateTimerTextView()
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