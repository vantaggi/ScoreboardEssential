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

class MainActivity : AppCompatActivity(), DataClient.OnDataChangedListener {

    private var team1Score = 0
    private var team2Score = 0

    private lateinit var team1ScoreTextView: TextView
    private lateinit var team2ScoreTextView: TextView
    private lateinit var dataClient: DataClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        team1ScoreTextView = findViewById(R.id.team1_score_textview)
        team2ScoreTextView = findViewById(R.id.team2_score_textview)

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
                    val receivedTeam1Score = dataMap.getInt(DataSync.TEAM1_SCORE_KEY)
                    val receivedTeam2Score = dataMap.getInt(DataSync.TEAM2_SCORE_KEY)

                    // Update local state
                    team1Score = receivedTeam1Score
                    team2Score = receivedTeam2Score

                    updateUi(team1Score, team2Score)
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