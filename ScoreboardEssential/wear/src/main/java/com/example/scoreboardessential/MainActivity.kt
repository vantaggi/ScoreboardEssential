package com.example.scoreboardessential

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private lateinit var timerTextView: TextView
    private lateinit var team1ScoreTextView: TextView
    private lateinit var team2ScoreTextView: TextView

    private var timer: CountDownTimer? = null
    private var isTimerRunning = false
    private var remainingTimeInMillis = 0L

    private var team1Score = 0
    private var team2Score = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // initialize views
        timerTextView = findViewById(R.id.timer_text_view)
        team1ScoreTextView = findViewById(R.id.team_1_score_text_view)
        team2ScoreTextView = findViewById(R.id.team_2_score_text_view)

        // set initial scores
        team1ScoreTextView.text = team1Score.toString()
        team2ScoreTextView.text = team2Score.toString()
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
    }

    private fun updateTimerTextView() {
        val minutes = (remainingTimeInMillis / 1000) / 60
        val seconds = (remainingTimeInMillis / 1000) % 60
        timerTextView.text = String.format("%02d:%02d", minutes, seconds)
    }
}