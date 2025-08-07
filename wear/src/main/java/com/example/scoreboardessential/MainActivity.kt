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
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable

// The main activity for the Wear OS app.
class MainActivity : ComponentActivity(), DataClient.OnDataChangedListener {

    // The text view that displays the timer.
    private lateinit var timerTextView: TextView
    // The text view that displays the score for team 1.
    private lateinit var team1ScoreTextView: TextView
    // The text view that displays the score for team 2.
    private lateinit var team2ScoreTextView: TextView

    // The countdown timer.
    private var timer: CountDownTimer? = null
    // Whether the timer is running.
    private var isTimerRunning = false
    // The remaining time on the timer in milliseconds.
    private var remainingTimeInMillis = 0L

    // The score for team 1.
    private var team1Score = 0
    // The score for team 2.
    private var team2Score = 0

    // The data client for communicating with the mobile app.
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

    // Sends a message to the mobile app to update the score.
    private fun sendScoreUpdateMessage(team: String) {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) {
                Wearable.getMessageClient(this).sendMessage(
                    node.id,
                    "/score_update",
                    team.toByteArray()
                ).apply {
                    addOnSuccessListener {
                        Log.d("DataSync", "Message sent successfully to ${node.displayName}")
                    }
                    addOnFailureListener {
                        Log.e("DataSync", "Message sending failed to ${node.displayName}", it)
                    }
                }
            }
        }
    }

    // Starts or stops the timer.
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

    // Resets the timer.
    fun resetTimer(view: View) {
        timer?.cancel()
        isTimerRunning = false
        remainingTimeInMillis = 0
        updateTimerTextView()
    }

    // Adds a point to the score of the given team.
    fun addScore(view: View) {
        when (view.id) {
            R.id.team_1_score_text_view -> {
                sendScoreUpdateMessage("team_1")
            }

            R.id.team_2_score_text_view -> {
                sendScoreUpdateMessage("team_2")
            }
        }
    }

    // Resets the scores.
    fun resetScores(view: View) {
        team1Score = 0
        team2Score = 0
        updateUi(team1Score, team2Score)
    }

    // Updates the timer text view with the current time.
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

    // Called when data is changed on the mobile app.
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

    // Updates the UI with the current scores.
    private fun updateUi(team1Score: Int, team2Score: Int) {
        runOnUiThread {
            team1ScoreTextView.text = team1Score.toString()
            team2ScoreTextView.text = team2Score.toString()
        }
    }
}