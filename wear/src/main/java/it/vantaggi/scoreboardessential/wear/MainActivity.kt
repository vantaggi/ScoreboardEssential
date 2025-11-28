package it.vantaggi.scoreboardessential.wear

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.remote.interactions.RemoteActivityHelper
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
        registerBroadcastReceiver()
    }

    private fun registerBroadcastReceiver() {
        val receiver =
            object : android.content.BroadcastReceiver() {
                override fun onReceive(
                    context: android.content.Context,
                    intent: android.content.Intent,
                ) {
                    when (intent.action) {
                        WearDataLayerService.ACTION_SCORE_UPDATE -> {
                            val team1 =
                                intent.getIntExtra(
                                    WearDataLayerService.EXTRA_TEAM1_SCORE,
                                    0,
                                )
                            val team2 =
                                intent.getIntExtra(
                                    WearDataLayerService.EXTRA_TEAM2_SCORE,
                                    0,
                                )
                            viewModel.updateScoresFromMobile(team1, team2)
                        }
                    }
                }
            }

        val filter = android.content.IntentFilter(WearDataLayerService.ACTION_SCORE_UPDATE)
        androidx.localbroadcastmanager.content.LocalBroadcastManager
            .getInstance(this)
            .registerReceiver(receiver, filter)
    }

    private fun setupClickListeners() {
        // Long press per incrementare/decrementare
        binding.team1Container.setOnLongClickListener {
            viewModel.incrementTeam1Score()
            true
        }

        binding.team2Container.setOnLongClickListener {
            viewModel.incrementTeam2Score()
            true
        }

        // Timer controls
        binding.matchTimer.setOnClickListener {
            viewModel.toggleTimer()
        }

        binding.keeperTimer.setOnClickListener {
            viewModel.toggleKeeperTimer()
        }

        binding.btnStartNewMatch.setOnClickListener {
            viewModel.resetMatch()
        }
    }

    private fun showTeamNameInput(team: Int) {
        val intent =
            Intent(Intent.ACTION_MAIN).apply {
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
