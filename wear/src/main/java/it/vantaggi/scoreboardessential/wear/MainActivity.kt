package it.vantaggi.scoreboardessential.wear

import android.os.Bundle
import android.view.View
import android.content.Intent
import androidx.wear.remote.interactions.RemoteActivityHelper
import android.os.ResultReceiver
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import it.vantaggi.scoreboardessential.wear.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: WearViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        observeViewModel()
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

        binding.keeperTimer.setOnClickListener {
            viewModel.handleKeeperTimer()
        }

        binding.btnStartNewMatch.setOnClickListener {
            viewModel.startNewMatch()
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
                        binding.keeperTimer.visibility = View.VISIBLE // SEMPRE VISIBILE
                        when (state) {
                            is KeeperTimerState.Hidden -> {
                                binding.keeperTimer.text = "KEEPER"
                                binding.keeperTimer.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.sidewalk_gray))
                            }
                            is KeeperTimerState.Running -> {
                                binding.keeperTimer.text = "K: ${state.secondsRemaining}"
                                binding.keeperTimer.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.team_spray_yellow))
                            }
                            is KeeperTimerState.Finished -> {
                                binding.keeperTimer.text = "K: 00"
                                binding.keeperTimer.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.error_red))
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