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
                        WearDataLayerService.ACTION_TEAM_NAMES_UPDATE -> {
                            val team1Name = intent.getStringExtra(WearDataLayerService.EXTRA_TEAM1_NAME) ?: "Team 1"
                            val team2Name = intent.getStringExtra(WearDataLayerService.EXTRA_TEAM2_NAME) ?: "Team 2"
                            viewModel.setTeamNames(team1Name, team2Name)
                        }
                        WearDataLayerService.ACTION_TEAM_COLOR_UPDATE -> {
                            val teamId = intent.getIntExtra(WearDataLayerService.EXTRA_TEAM_ID, 0)
                            val color = intent.getIntExtra(WearDataLayerService.EXTRA_COLOR, 0)
                            if (teamId > 0) {
                                viewModel.setTeamColor(teamId, color)
                            }
                        }
                        WearDataLayerService.ACTION_TIMER_UPDATE -> {
                            val millis = intent.getLongExtra(WearDataLayerService.EXTRA_TIMER_MILLIS, 0L)
                            viewModel.setMatchTimerMillis(millis)
                        }
                        WearDataLayerService.ACTION_KEEPER_TIMER_UPDATE -> {
                            val millis = intent.getLongExtra(WearDataLayerService.EXTRA_KEEPER_MILLIS, 0L)
                            val isRunning = intent.getBooleanExtra(WearDataLayerService.EXTRA_KEEPER_RUNNING, false)
                            if (isRunning) {
                                viewModel.setKeeperTimerState(KeeperTimerState.Running((millis / 1000).toInt()))
                                // Also update duration if it runs with specific duration?
                                // Yes, if we start remotely, we should respect that duration for next runs too.
                                if (millis > 0) viewModel.updateKeeperTimerDuration(millis)
                            } else {
                                if (millis > 0) {
                                    // This is a settings update (or pause?)
                                    viewModel.updateKeeperTimerDuration(millis)
                                }
                                viewModel.resetKeeperTimer(fromRemote = true)
                            }
                        }
                        WearDataLayerService.ACTION_MATCH_STATE_UPDATE -> {
                            val isActive = intent.getBooleanExtra(WearDataLayerService.EXTRA_MATCH_ACTIVE, true)
                            if (!isActive) {
                                viewModel.resetMatch(fromRemote = true)
                            }
                        }
                    }
                }
            }

        val filter =
            android.content.IntentFilter().apply {
                addAction(WearDataLayerService.ACTION_SCORE_UPDATE)
                addAction(WearDataLayerService.ACTION_TEAM_NAMES_UPDATE)
                addAction(WearDataLayerService.ACTION_TEAM_COLOR_UPDATE)
                addAction(WearDataLayerService.ACTION_TIMER_UPDATE)
                addAction(WearDataLayerService.ACTION_KEEPER_TIMER_UPDATE)
                addAction(WearDataLayerService.ACTION_MATCH_STATE_UPDATE)
            }
        androidx.localbroadcastmanager.content.LocalBroadcastManager
            .getInstance(this)
            .registerReceiver(receiver, filter)
    }

    private var lastTouchY = 0f

    private fun setupClickListeners() {
        val touchListener =
            View.OnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    lastTouchY = event.y
                }
                false
            }

        binding.team1Container.setOnTouchListener(touchListener)
        binding.team2Container.setOnTouchListener(touchListener)

        // Click per incrementare (comportamento standard)
        binding.team1Container.setOnClickListener {
            viewModel.incrementTeam1Score()
        }

        binding.team2Container.setOnClickListener {
            viewModel.incrementTeam2Score()
        }

        // Long press spaziale: Alto -> Incrementa, Basso -> Decrementa
        binding.team1Container.setOnLongClickListener { v ->
            if (lastTouchY < v.height / 2) {
                viewModel.incrementTeam1Score()
            } else {
                viewModel.decrementTeam1Score()
            }
            true
        }

        binding.team2Container.setOnLongClickListener { v ->
            if (lastTouchY < v.height / 2) {
                viewModel.incrementTeam2Score()
            } else {
                viewModel.decrementTeam2Score()
            }
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

                // Observe Team Colors
                launch {
                    viewModel.team1Color.collect { color ->
                        color?.let { binding.team1Score.setTextColor(it) }
                    }
                }

                launch {
                    viewModel.team2Color.collect { color ->
                        color?.let { binding.team2Score.setTextColor(it) }
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
                                binding.keeperTimer.text = "K"
                                binding.keeperTimer.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.sidewalk_gray))
                                binding.keeperProgressBar.visibility = View.INVISIBLE
                            }
                            is KeeperTimerState.Running -> {
                                binding.keeperTimer.text = "K"
                                binding.keeperTimer.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.graffiti_pink))
                                binding.keeperProgressBar.visibility = View.VISIBLE
                            }
                            is KeeperTimerState.Finished -> {
                                binding.keeperTimer.text = "K"
                                binding.keeperTimer.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.error_red))
                                binding.keeperProgressBar.visibility = View.VISIBLE
                            }
                        }
                    }
                }

                // Observe Keeper Progress
                launch {
                    viewModel.keeperProgress.collect { progress ->
                        binding.keeperProgressBar.progress = progress
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
                // Observe Connection State
                launch {
                    viewModel.connectionState.collect { state ->
                        when (state) {
                            is it.vantaggi.scoreboardessential.shared.communication.ConnectionState.Connected -> {
                                binding.connectionStatusIndicator.backgroundTintList =
                                    android.content.res.ColorStateList.valueOf(
                                        ContextCompat.getColor(
                                            this@MainActivity,
                                            R.color.team_electric_green,
                                        ),
                                    )
                            }
                            else -> {
                                binding.connectionStatusIndicator.backgroundTintList =
                                    android.content.res.ColorStateList.valueOf(
                                        ContextCompat.getColor(
                                            this@MainActivity,
                                            R.color.error_red,
                                        ),
                                    )
                            }
                        }
                    }
                }
            }
        }
    }
}
