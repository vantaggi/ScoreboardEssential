package com.example.scoreboardessential

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import android.os.CountDownTimer
import android.view.View

class MainActivity : AppCompatActivity(), DataClient.OnDataChangedListener {

    private var team1Score = 0
    private var team2Score = 0
    private var timer: CountDownTimer? = null
    private var isTimerRunning = false
    private var remainingTimeInMillis = 0L

    private lateinit var team1ScoreTextView: TextView
    private lateinit var team2ScoreTextView: TextView
    private lateinit var timerTextView: TextView
    private lateinit var dataClient: DataClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        team1ScoreTextView = findViewById(R.id.team1_score_textview)
        team2ScoreTextView = findViewById(R.id.team2_score_textview)
        timerTextView = findViewById(R.id.timer_textview)

        dataClient = Wearable.getDataClient(this)

        findViewById<Button>(R.id.team1_add_button).setOnClickListener {
            team1Score++
            updateUi(team1Score, team2Score)
            sendScoreUpdate()
        }
        findViewById<Button>(R.id.team1_subtract_button).setOnClickListener {
            if (team1Score > 0) {
                team1Score--
                updateUi(team1Score, team2Score)
                sendScoreUpdate()
            }
        }
        findViewById<Button>(R.id.team2_add_button).setOnClickListener {
            team2Score++
            updateUi(team1Score, team2Score)
            sendScoreUpdate()
        }
        findViewById<Button>(R.id.team2_subtract_button).setOnClickListener {
            if (team2Score > 0) {
                team2Score--
                updateUi(team1Score, team2Score)
                sendScoreUpdate()
            }
        }
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

    private fun updateTimerTextView() {
        val minutes = (remainingTimeInMillis / 1000) / 60
        val seconds = (remainingTimeInMillis / 1000) % 60
        timerTextView.text = String.format("%02d:%02d", minutes, seconds)
    }
}
