package com.example.scoreboardessential

import android.os.Bundle
import android.view.View
import android.content.Intent
import androidx.wear.remote.interactions.RemoteActivityHelper
import android.os.ResultReceiver
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.scoreboardessential.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: WearViewModel by viewModels()
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGestureDetector()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                
                // Check if horizontal swipe is more significant than vertical
                if (abs(diffX) > abs(diffY)) {
                    // Swipe left to show match management
                    if (diffX < -100 && abs(velocityX) > 100) {
                        startActivity(Intent(this@MainActivity, MatchManagementActivity::class.java))
                        return true
                    }
                }
                return false
            }
        })
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    private fun setupClickListeners() {
        binding.team1IncrementArea.setOnLongClickListener {
            viewModel.incrementTeam1Score()
            true // Consume the event
        }

        binding.team1DecrementArea.setOnLongClickListener {
            viewModel.decrementTeam1Score()
            true
        }

        binding.team2IncrementArea.setOnLongClickListener {
            viewModel.incrementTeam2Score()
            true
        }

        binding.team2DecrementArea.setOnLongClickListener {
            viewModel.decrementTeam2Score()
            true
        }

        binding.keeperTimer.setOnLongClickListener {
            viewModel.handleKeeperTimer()
            true
        }

        // Add timer controls via long press on match timer
        binding.matchTimer.setOnLongClickListener {
            viewModel.startStopMatchTimer()
            true
        }

        binding.matchTimer.setOnClickListener {
            viewModel.resetMatchTimer()
        }
    }
    private fun showTeamNameInput(team: Int) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            action = "com.google.android.wearable.action.INPUT_TEXT"
            putExtra("com.google.android.wearable.extra.PROMPT", "Team $team name:")
        }
        RemoteActivityHelper(this).startRemoteActivity(intent)
    }
    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe Team 1 Score
                launch {
                    viewModel.team1Score.collect { score ->
                        binding.team1Score.text = score.toString()
                    }
                }

                // Observe Team 1 Name
                launch {
                    viewModel.team1Name.collect { name ->
                        binding.team1Name.text = name
                    }
                }

                // Observe Team 2 Name
                launch {
                    viewModel.team2Name.collect { name ->
                        binding.team2Name.text = name
                    }
                }

                // Observe Team 1 Score
                launch {
                    viewModel.team1Score.collect { score ->
                        binding.team1Score.text = score.toString()
                    }
                }

                // Observe Team 2 Score
                launch {
                    viewModel.team2Score.collect { score ->
                        binding.team2Score.text = score.toString()
                    }
                }

                // Observe Match Timer
                launch {
                    viewModel.matchTimer.collect { time ->
                        binding.matchTimer.text = time
                    }
                }

                // Observe Keeper Timer
                launch {
                    viewModel.keeperTimer.collect { state ->
                        when (state) {
                            is KeeperTimerState.Hidden -> {
                                binding.keeperTimer.visibility = View.GONE
                            }
                            is KeeperTimerState.Running -> {
                                binding.keeperTimer.visibility = View.VISIBLE
                                binding.keeperTimer.text = state.secondsRemaining.toString()
                            }
                            is KeeperTimerState.Finished -> {
                                binding.keeperTimer.visibility = View.VISIBLE
                                binding.keeperTimer.text = "00"
                            }
                        }
                    }
                }

                // Observe Player Selection Events
                launch {
                    viewModel.showPlayerSelection.collect { teamNumber ->
                        teamNumber?.let {
                            val intent = Intent(this@MainActivity, PlayerSelectionActivity::class.java)
                            intent.putExtra("team_number", it)
                            startActivity(intent)
                            viewModel.clearPlayerSelectionEvent()
                        }
                    }
                }

            }
        }
    }
}