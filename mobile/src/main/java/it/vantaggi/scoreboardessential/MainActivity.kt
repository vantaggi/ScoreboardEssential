package it.vantaggi.scoreboardessential

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.domain.models.Formation
import it.vantaggi.scoreboardessential.ui.MatchSettingsActivity
import it.vantaggi.scoreboardessential.ui.onboarding.OnboardingActivity
import it.vantaggi.scoreboardessential.ui.statistics.StatisticsActivity
import it.vantaggi.scoreboardessential.utils.animateScoreButton
import it.vantaggi.scoreboardessential.utils.playNativeGoalAnimation
import it.vantaggi.scoreboardessential.views.FormationView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity :
    AppCompatActivity(),
    SelectScorerDialogFragment.ScorerDialogListener {
    private val viewModel: MainViewModel by viewModels {
        val application = application as ScoreboardEssentialApplication
        MainViewModel.MainViewModelFactory(
            application.matchRepository,
            application.userPreferencesRepository,
            application.matchSettingsRepository,
            application,
        )
    }

    // Core view references
    private lateinit var team1ScoreTextView: TextView
    private lateinit var team2ScoreTextView: TextView
    private lateinit var timerTextView: TextView
    private lateinit var team1Card: MaterialCardView
    private lateinit var team2Card: MaterialCardView
    private lateinit var keeperTimerTextView: TextView
    private lateinit var timerStartButton: Button
    private lateinit var undoGoalButton: Button

    // New view references for refactored layout
    private lateinit var team1NameTextView: TextView
    private lateinit var team2NameTextView: TextView
    private lateinit var vsIndicator: View

    // Gesture detectors
    private lateinit var team1GestureDetector: GestureDetector
    private lateinit var team2GestureDetector: GestureDetector

    // Team roster recycler views
    private lateinit var team1RosterRecyclerView: RecyclerView
    private lateinit var team2RosterRecyclerView: RecyclerView
    private lateinit var matchLogRecyclerView: RecyclerView

    // Adapters
    private lateinit var team1RosterAdapter: TeamRosterAdapter
    private lateinit var team2RosterAdapter: TeamRosterAdapter
    private lateinit var matchLogAdapter: MatchLogAdapter

    private lateinit var team1FormationView: FormationView
    private lateinit var team2FormationView: FormationView
    private lateinit var team1FormationLabel: TextView
    private lateinit var team2FormationLabel: TextView

    private var vibrator: Vibrator? = null

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted: Boolean ->
            if (!isGranted) {
                Snackbar
                    .make(
                        findViewById(android.R.id.content),
                        getString(R.string.notification_permission_required),
                        Snackbar.LENGTH_LONG,
                    ).show()
            }
        }

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(
            it.vantaggi.scoreboardessential.utils.LocaleHelper
                .onAttach(newBase),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        vibrator =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }

        initializeViews()
        setupRecyclerViews()
        observeViewModel()
        setupImprovedViews() // Call new setup method
        setupGestureControls() // Call gesture setup
        requestNotificationPermission()
        viewModel.registerBroadcasts(this)

        lifecycleScope.launch {
            delay(2000) // Aspetta che il servizio si registri

            val testResult = viewModel.connectionManager.testConnection()
            if (testResult) {
                Log.d("ConnectionTest", "✅ CONNECTION TEST PASSED")
                Toast.makeText(this@MainActivity, getString(R.string.wear_connected), Toast.LENGTH_SHORT).show()
            } else {
                Log.e("ConnectionTest", "❌ CONNECTION TEST FAILED")
                Toast.makeText(this@MainActivity, getString(R.string.wear_not_connected), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun initializeViews() {
        // Core views
        team1ScoreTextView = findViewById(R.id.team1_score_textview)
        team2ScoreTextView = findViewById(R.id.team2_score_textview)
        timerTextView = findViewById(R.id.timer_textview)
        team1Card = findViewById(R.id.team1_card)
        team2Card = findViewById(R.id.team2_card)
        keeperTimerTextView = findViewById(R.id.keeper_timer_textview)
        timerStartButton = findViewById(R.id.timer_start_button)
        undoGoalButton = findViewById(R.id.undo_goal_button)

        // New Views
        team1NameTextView = findViewById(R.id.team1_name_textview)
        team2NameTextView = findViewById(R.id.team2_name_textview)
        vsIndicator = findViewById(R.id.vs_indicator)

        // Roster RecyclerViews
        team1RosterRecyclerView = findViewById(R.id.team1_roster_recyclerview)
        team2RosterRecyclerView = findViewById(R.id.team2_roster_recyclerview)
        matchLogRecyclerView = findViewById(R.id.match_log_recyclerview)

        team1FormationView = findViewById(R.id.team1_formation_view)
        team2FormationView = findViewById(R.id.team2_formation_view)
        team1FormationLabel = findViewById(R.id.team1_formation_label)
        team2FormationLabel = findViewById(R.id.team2_formation_label)
    }

    private fun setupRecyclerViews() {
        team1RosterAdapter =
            TeamRosterAdapter { playerWithRoles ->
                showRemovePlayerDialog(playerWithRoles, 1)
            }
        team1RosterRecyclerView.apply {
            adapter = team1RosterAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        team2RosterAdapter =
            TeamRosterAdapter { playerWithRoles ->
                showRemovePlayerDialog(playerWithRoles, 2)
            }
        team2RosterRecyclerView.apply {
            adapter = team2RosterAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        matchLogAdapter = MatchLogAdapter()
        matchLogRecyclerView.apply {
            adapter = matchLogAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }

    private fun observeViewModel() {
        viewModel.team1Score.observe(this) { score ->
            team1ScoreTextView.text = score.toString()
        }

        viewModel.team2Score.observe(this) { score ->
            team2ScoreTextView.text = score.toString()
        }

        viewModel.team1Name.observe(this) { name ->
            team1NameTextView.text = name.uppercase(Locale.getDefault())
        }

        viewModel.team2Name.observe(this) { name ->
            team2NameTextView.text = name.uppercase(Locale.getDefault())
        }

        viewModel.team1Color.observe(this) { color ->
            team1Card.setCardBackgroundColor(color)
            matchLogAdapter.team1Color = color
            matchLogAdapter.notifyDataSetChanged()
        }

        viewModel.team2Color.observe(this) { color ->
            team2Card.setCardBackgroundColor(color)
            matchLogAdapter.team2Color = color
            matchLogAdapter.notifyDataSetChanged()
        }

        viewModel.matchTimerValue.observe(this) { timeInMillis ->
            updateTimerTextView(timeInMillis)
        }

        viewModel.keeperTimerValue.observe(this) { timeInMillis ->
            updateKeeperTimerTextView(timeInMillis)
        }

        viewModel.team1Players.observe(this) { players ->
            team1RosterAdapter.submitList(players)
            updateFormation(1, players)
        }

        viewModel.team2Players.observe(this) { players ->
            team2RosterAdapter.submitList(players)
            updateFormation(2, players)
        }

        viewModel.matchEvents.observe(this) { events ->
            matchLogAdapter.submitList(events)
        }

        viewModel.showSelectScorerDialog.observe(this) { (teamId, players) ->
            SelectScorerDialogFragment
                .newInstance(players, teamId)
                .show(supportFragmentManager, SelectScorerDialogFragment.TAG)
        }

        viewModel.isMatchTimerRunning.observe(this) { isRunning ->
            timerStartButton.text = if (isRunning) getString(R.string.pause_caps) else getString(R.string.start)
        }

        viewModel.isWearConnected.observe(this) { isConnected ->
            val statusIcon = findViewById<ImageView>(R.id.wear_status_icon)
            if (isConnected) {
                statusIcon.setImageResource(R.drawable.ic_watch_connected)
                statusIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.team_electric_green))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    statusIcon.tooltipText = getString(R.string.wear_connected_tooltip)
                }
            } else {
                statusIcon.setImageResource(R.drawable.ic_watch_disconnected)
                statusIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sidewalk_gray))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    statusIcon.tooltipText = getString(R.string.wear_disconnected_tooltip)
                }
            }
        }

        viewModel.shareMatchEvent.observe(this) { intent ->
            startActivity(Intent.createChooser(intent, getString(R.string.share_match_results)))
        }

        viewModel.showOnboarding.observe(this) {
            val intent = Intent(this, OnboardingActivity::class.java)
            startActivity(intent)
        }

        viewModel.canUndo.observe(this) { canUndo ->
            undoGoalButton.visibility = if (canUndo) View.VISIBLE else View.GONE
        }

        viewModel.serviceBindingStatus.observe(this) { isBound ->
            timerStartButton.isEnabled = isBound
            timerStartButton.alpha = if (isBound) 1.0f else 0.5f

            val resetButton = findViewById<Button>(R.id.reset_timer_button)
            resetButton?.isEnabled = isBound
            resetButton?.alpha = if (isBound) 1.0f else 0.5f
        }
    }

    private fun setupImprovedViews() {
        setupScoreButtons()
        setupMatchActions()
        setupPlayerManagementButtons()
        setupNavigationButtons()
    }

    private fun setupScoreButtons() {
        // Team name containers are no longer clickable
        // New buttons with improved feedback
        findViewById<View>(R.id.team1_add_button_card).setOnClickListener {
            it.animateScoreButton()
            viewModel.addScore(1)
            playGoalAnimation(1)
        }

        findViewById<View>(R.id.team1_subtract_button_card).setOnClickListener {
            it.animateScoreButton(isSubtract = true)
            viewModel.subtractScore(1)
        }

        findViewById<View>(R.id.team2_add_button_card).setOnClickListener {
            it.animateScoreButton()
            viewModel.addScore(2)
            playGoalAnimation(2)
        }

        findViewById<View>(R.id.team2_subtract_button_card).setOnClickListener {
            it.animateScoreButton(isSubtract = true)
            viewModel.subtractScore(2)
        }
    }

    private fun setupMatchActions() {
        findViewById<Button>(R.id.reset_scores_button).setOnClickListener {
            showEndMatchConfirmation()
        }

        findViewById<Button>(R.id.share_match_button).setOnClickListener {
            viewModel.shareMatchResults()
        }

        undoGoalButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.undo_goal_title))
                .setMessage(getString(R.string.undo_goal_message))
                .setPositiveButton(getString(R.string.undo)) { _, _ ->
                    viewModel.undoLastGoal()
                }.setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    private fun setupPlayerManagementButtons() {
        findViewById<Button>(R.id.add_team1_player_button).setOnClickListener {
            showAddPlayerToTeamDialog(1)
        }

        findViewById<Button>(R.id.add_team2_player_button).setOnClickListener {
            showAddPlayerToTeamDialog(2)
        }

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.players_fab).setOnClickListener {
            startActivity(Intent(this, PlayersManagementActivity::class.java))
        }
    }

    private fun setupNavigationButtons() {
        findViewById<Button>(R.id.match_history_button).setOnClickListener {
            startActivity(Intent(this, MatchHistoryActivity::class.java))
        }

        findViewById<View>(R.id.settings_button).setOnClickListener {
            startActivity(Intent(this, MatchSettingsActivity::class.java))
        }

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.stats_fab).setOnClickListener {
            startActivity(Intent(this, StatisticsActivity::class.java))
        }
    }

    private fun setupGestureControls() {
        // Swipe up to increase, swipe down to decrease for Team 1
        team1GestureDetector =
            GestureDetector(
                this,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onFling(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        velocityX: Float,
                        velocityY: Float,
                    ): Boolean {
                        e1?.let {
                            val diffY = e2.y - it.y
                            if (Math.abs(diffY) > 100) {
                                if (diffY < 0) {
                                    viewModel.addScore(1)
                                    playGoalAnimation(1)
                                } else {
                                    viewModel.subtractScore(1)
                                }
                                return true
                            }
                        }
                        return false
                    }

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        viewModel.addScore(1)
                        playGoalAnimation(1)
                        return true
                    }
                },
            )

        findViewById<View>(R.id.team1_card).setOnTouchListener { _, event ->
            team1GestureDetector.onTouchEvent(event)
        }

        // Swipe up to increase, swipe down to decrease for Team 2
        team2GestureDetector =
            GestureDetector(
                this,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onFling(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        velocityX: Float,
                        velocityY: Float,
                    ): Boolean {
                        e1?.let {
                            val diffY = e2.y - it.y
                            if (Math.abs(diffY) > 100) {
                                if (diffY < 0) {
                                    viewModel.addScore(2)
                                    playGoalAnimation(2)
                                } else {
                                    viewModel.subtractScore(2)
                                }
                                return true
                            }
                        }
                        return false
                    }

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        viewModel.addScore(2)
                        playGoalAnimation(2)
                        return true
                    }
                },
            )

        findViewById<View>(R.id.team2_card).setOnTouchListener { _, event ->
            team2GestureDetector.onTouchEvent(event)
        }
    }


    private fun animateTextChange(
        textView: TextView,
        newText: String,
    ) {
        textView
            .animate()
            .alpha(0f)
            .translationY(-20f)
            .setDuration(150)
            .withEndAction {
                textView.text = newText.uppercase()
                textView
                    .animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(150)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            }.start()
    }

    private fun playGoalAnimation(team: Int) {
        val scoreTextView = if (team == 1) team1ScoreTextView else team2ScoreTextView

        // Chiama la nostra nuova animazione nativa!
        scoreTextView.playNativeGoalAnimation()

        // Mantiene le altre animazioni e la vibrazione
        animateVsIndicator()
        playGoalVibrationPattern()
    }

    private fun animateVsIndicator() {
        vsIndicator
            .animate()
            .rotationBy(360f)
            .scaleX(1.3f)
            .scaleY(1.3f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                vsIndicator
                    .animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .start()
            }.start()
    }

    private fun playGoalVibrationPattern() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 100, 50, 100, 50, 200)
            val amplitudes = intArrayOf(0, 128, 0, 255, 0, 128)
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
    }

    private fun showAddPlayerToTeamDialog(team: Int) {
        val allPlayers = viewModel.allPlayers.value ?: emptyList()
        val teamPlayers = if (team == 1) viewModel.team1Players.value else viewModel.team2Players.value
        val availablePlayers =
            allPlayers.filter { playerWithRoles ->
                teamPlayers?.none { it.player.playerId == playerWithRoles.player.playerId } ?: true
            }

        if (availablePlayers.isEmpty()) {
            Snackbar
                .make(
                    findViewById(android.R.id.content),
                    getString(R.string.no_available_players),
                    Snackbar.LENGTH_LONG,
                ).setAction(getString(R.string.manage)) {
                    startActivity(Intent(this, PlayersManagementActivity::class.java))
                }.show()
            return
        }

        val playerNames = availablePlayers.map { it.player.playerName }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.add_player_title, team))
            .setItems(playerNames) { _, which ->
                val selectedPlayer = availablePlayers[which]
                viewModel.addPlayerToTeam(selectedPlayer, team)
                val teamName = if (team == 1) viewModel.team1Name.value else viewModel.team2Name.value
                Snackbar
                    .make(
                        findViewById(android.R.id.content),
                        getString(R.string.player_added_message, selectedPlayer.player.playerName, teamName),
                        Snackbar.LENGTH_SHORT,
                    ).show()
            }.setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showRemovePlayerDialog(
        playerWithRoles: PlayerWithRoles,
        team: Int,
    ) {
        val teamName = if (team == 1) viewModel.team1Name.value else viewModel.team2Name.value
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.remove_player_title))
            .setMessage(getString(R.string.remove_player_message, playerWithRoles.player.playerName, teamName))
            .setPositiveButton(getString(R.string.remove)) { _, _ ->
                viewModel.removePlayerFromTeam(playerWithRoles, team)
                Snackbar
                    .make(
                        findViewById(android.R.id.content),
                        getString(R.string.player_removed_message, playerWithRoles.player.playerName, teamName),
                        Snackbar.LENGTH_SHORT,
                    ).show()
            }.setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onScorerSelected(
        playerWithRoles: PlayerWithRoles,
        teamId: Int,
    ) {
        viewModel.addScorer(teamId, playerWithRoles)
        Snackbar
            .make(
                findViewById(android.R.id.content),
                getString(R.string.goal_by_message, playerWithRoles.player.playerName),
                Snackbar.LENGTH_SHORT,
            ).show()
    }

    private fun showEndMatchConfirmation() {
        val team1Score = viewModel.team1Score.value ?: 0
        val team2Score = viewModel.team2Score.value ?: 0
        val team1Name = viewModel.team1Name.value ?: "Team 1"
        val team2Name = viewModel.team2Name.value ?: "Team 2"

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.end_match_title))
            .setMessage(getString(R.string.end_match_message, team1Name, team1Score, team2Name, team2Score))
            .setPositiveButton(getString(R.string.btn_end_match)) { _, _ ->
                if (viewModel.endMatch()) {
                    Snackbar
                        .make(
                            findViewById(android.R.id.content),
                            getString(R.string.match_saved),
                            Snackbar.LENGTH_LONG,
                        ).show()
                } else {
                    Snackbar
                        .make(
                            findViewById(android.R.id.content),
                            getString(R.string.match_not_started_error),
                            Snackbar.LENGTH_LONG,
                        ).show()
                }
            }.setNegativeButton(getString(R.string.continue_action), null)
            .show()
    }

    private fun showKeeperTimerExpiredAlert() {
        triggerStrongVibration()
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.keeper_change_title))
            .setMessage(getString(R.string.keeper_change_message))
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                viewModel.resetKeeperTimer()
            }.setCancelable(false)
            .show()
    }

    private fun updateTimerTextView(timeInMillis: Long) {
        val minutes = (timeInMillis / 1000) / 60
        val seconds = (timeInMillis / 1000) % 60
        timerTextView.text = String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun updateKeeperTimerTextView(timeInMillis: Long) {
        if (timeInMillis > 0) {
            val minutes = (timeInMillis / 1000) / 60
            val seconds = (timeInMillis / 1000) % 60
            keeperTimerTextView.text = String.format(Locale.getDefault(), "⏰ %02d:%02d", minutes, seconds)
            keeperTimerTextView.visibility = View.VISIBLE
        } else {
            keeperTimerTextView.visibility = View.GONE
        }
    }

    private fun triggerStrongVibration() {
        val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun startStopTimer(view: View) {
        android.util.Log.d("MainActivity", "START/STOP timer button clicked")
        viewModel.startStopMatchTimer()

        // Verifica lo stato del timer dopo 1 secondo
        view.postDelayed({
            val isRunning = viewModel.isMatchTimerRunning.value ?: false
            android.util.Log.d("MainActivity", "Timer running state: $isRunning")
        }, 1000)
    }

    @Suppress("UNUSED_PARAMETER")
    fun resetTimer(view: View) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.reset_timer_title))
            .setMessage(getString(R.string.reset_timer_message))
            .setPositiveButton(getString(R.string.reset)) { _, _ ->
                viewModel.resetMatchTimer()
            }.setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun updateFormation(
        teamNumber: Int,
        players: List<PlayerWithRoles>,
    ) {
        val formation = Formation.fromPlayers(players)

        when (teamNumber) {
            1 -> {
                team1FormationView.setFormation(formation)
                val teamName = viewModel.team1Name.value ?: "Team 1"
                if (formation.isValid()) {
                    team1FormationLabel.text = "$teamName (${formation.getFormationString()})"
                } else {
                    team1FormationLabel.text = "$teamName (No formation)"
                }
            }
            2 -> {
                team2FormationView.setFormation(formation)
                val teamName = viewModel.team2Name.value ?: "Team 2"
                if (formation.isValid()) {
                    team2FormationLabel.text = "$teamName (${formation.getFormationString()})"
                } else {
                    team2FormationLabel.text = "$teamName (No formation)"
                }
            }
        }
    }
}
