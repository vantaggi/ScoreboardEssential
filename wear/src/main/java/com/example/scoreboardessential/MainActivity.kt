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

    }

    private fun sendMessage(path: String, data: ByteArray = byteArrayOf()) {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                Wearable.getMessageClient(this).sendMessage(node.id, path, data)
                    .addOnSuccessListener {
                        Log.d("DataSync", "Message sent to ${node.displayName}")
                    }
                    .addOnFailureListener {
                        Log.e("DataSync", "Failed to send message to ${node.displayName}", it)
                    }
            }
        }
    }

    // Starts or stops the timer.
    fun startStopTimer(view: View) {
        sendMessage("/start_stop_timer")
    }

    // Resets the timer.
    fun resetTimer(view: View) {
        sendMessage("/reset_timer")
    }

    // Adds a point to the score of the given team.
    fun addScore(view: View) {
        when (view.id) {
            R.id.team_1_score_text_view -> {
                sendMessage("/score_update", "team_1".toByteArray())
            }

            R.id.team_2_score_text_view -> {
                sendMessage("/score_update", "team_2".toByteArray())
            }
        }
    }

    // Resets the scores.
    fun resetScores(view: View) {
        sendMessage("/reset_scores")
    }

    // Updates the timer text view with the current time.
    private fun updateTimerTextView(remainingTimeInMillis: Long) {
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

                    val team1Score = dataMap.getInt(DataSyncObject.TEAM1_SCORE_KEY, 0)
                    val team2Score = dataMap.getInt(DataSyncObject.TEAM2_SCORE_KEY, 0)
                    updateUi(team1Score, team2Score)

                    if (dataMap.containsKey(DataSyncObject.TIMER_KEY)) {
                        val remainingTimeInMillis = dataMap.getLong(DataSyncObject.TIMER_KEY)
                        updateTimerTextView(remainingTimeInMillis)
                    }
                    if (dataMap.containsKey(DataSyncObject.RESET_KEY) && dataMap.getBoolean(DataSyncObject.RESET_KEY)) {
                        updateUi(0, 0)
                        updateTimerTextView(0)
                    }
                    if (dataMap.containsKey(DataSyncObject.SET_TIMER_KEY)) {
                        val remainingTimeInMillis = dataMap.getLong(DataSyncObject.SET_TIMER_KEY)
                        updateTimerTextView(remainingTimeInMillis)
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