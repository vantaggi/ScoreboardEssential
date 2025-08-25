package com.example.scoreboardessential

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.scoreboardessential.database.Player
import com.example.scoreboardessential.database.PlayerWithRoles
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.sliders.BrightnessSlideBar
import com.google.android.material.card.MaterialCardView
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.MainViewModelFactory(
            (application as ScoreboardEssentialApplication).matchRepository,
            application
        )
    }

    // Core view references that exist in base layout
    private lateinit var team1ScoreTextView: TextView
    private lateinit var team2ScoreTextView: TextView
    private lateinit var timerTextView: TextView
    private lateinit var team1Card: MaterialCardView
    private lateinit var team2Card: MaterialCardView
    private lateinit var keeperTimerTextView: TextView
    private lateinit var timerEditText: EditText
    private lateinit var team1NameEditText: EditText
    private lateinit var team2NameEditText: EditText

    // Team roster recycler views
    private lateinit var team1RosterRecyclerView: RecyclerView
    private lateinit var team2RosterRecyclerView: RecyclerView
    private lateinit var matchLogRecyclerView: RecyclerView

    // Adapters
    private lateinit var team1RosterAdapter: TeamRosterAdapter
    private lateinit var team2RosterAdapter: TeamRosterAdapter
    private lateinit var matchLogAdapter: MatchLogAdapter

    private var vibrator: Vibrator? = null

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Snackbar.make(
                findViewById(android.R.id.content),
                "Notifications permission is required for timer alerts",
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
        setupClickListeners()
        requestNotificationPermission()
    }

    private fun initializeViews() {
        // Core views that exist in base layout
        team1ScoreTextView = findViewById(R.id.team1_score_textview)
        team2ScoreTextView = findViewById(R.id.team2_score_textview)
        timerTextView = findViewById(R.id.timer_textview)
        team1Card = findViewById(R.id.team1_card)
        team2Card = findViewById(R.id.team2_card)
        keeperTimerTextView = findViewById(R.id.keeper_timer_textview)
        timerEditText = findViewById(R.id.timer_edittext)
        team1NameEditText = findViewById(R.id.team1_name_edittext)
        team2NameEditText = findViewById(R.id.team2_name_edittext)

        // Roster RecyclerViews
        team1RosterRecyclerView = findViewById(R.id.team1_roster_recyclerview)
        team2RosterRecyclerView = findViewById(R.id.team2_roster_recyclerview)
        matchLogRecyclerView = findViewById(R.id.match_log_recyclerview)
    }

    private fun setupRecyclerViews() {
        // Team 1 Roster
        team1RosterAdapter = TeamRosterAdapter { playerWithRoles ->
            showRemovePlayerDialog(playerWithRoles, 1)
        }
        team1RosterRecyclerView.apply {
            adapter = team1RosterAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        // Team 2 Roster
        team2RosterAdapter = TeamRosterAdapter { playerWithRoles ->
            showRemovePlayerDialog(playerWithRoles, 2)
        }
        team2RosterRecyclerView.apply {
            adapter = team2RosterAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        // Match Log
        matchLogAdapter = MatchLogAdapter()
        matchLogRecyclerView.apply {
            adapter = matchLogAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }

    private fun observeViewModel() {
        // Scores
        viewModel.team1Score.observe(this) { score ->
            team1ScoreTextView.text = score.toString()
        }

        viewModel.team2Score.observe(this) { score ->
            team2ScoreTextView.text = score.toString()
        }

        // Team Names
        viewModel.team1Name.observe(this) { name ->
            if (team1NameEditText.text.toString() != name) {
                team1NameEditText.setText(name)
            }
        }

        viewModel.team2Name.observe(this) { name ->
            if (team2NameEditText.text.toString() != name) {
                team2NameEditText.setText(name)
            }
        }

        // Team Colors - apply to score text views
        viewModel.team1Color.observe(this) { color ->
            team1Card.setCardBackgroundColor(color)
        }

        viewModel.team2Color.observe(this) { color ->
            team2Card.setCardBackgroundColor(color)
        }

        // Match Timer
        viewModel.matchTimerValue.observe(this) { timeInMillis ->
            updateTimerTextView(timeInMillis)
        }

        // Keeper Timer
        viewModel.keeperTimerValue.observe(this) { timeInMillis ->
            updateKeeperTimerTextView(timeInMillis)
        }

        // Team Players
        viewModel.team1Players.observe(this) { players ->
            team1RosterAdapter.submitList(players)
        }

        viewModel.team2Players.observe(this) { players ->
            team2RosterAdapter.submitList(players)
        }

        // Match Events
        viewModel.matchEvents.observe(this) { events ->
            matchLogAdapter.submitList(events)
        }

        // UI Events
        viewModel.showSelectScorerDialog.observe(this) { team ->
            showSelectScorerDialog(team)
        }

        viewModel.showColorPickerDialog.observe(this) { team ->
            showColorPickerDialog(team)
        }
    }

    private fun setupClickListeners() {
        // Score buttons
        findViewById<Button>(R.id.team1_add_button).setOnClickListener {
            viewModel.addTeam1Score()
            triggerHapticFeedback()
        }

        findViewById<Button>(R.id.team1_subtract_button).setOnClickListener {
            viewModel.subtractTeam1Score()
            triggerHapticFeedback()
        }

        findViewById<Button>(R.id.team2_add_button).setOnClickListener {
            viewModel.addTeam2Score()
            triggerHapticFeedback()
        }

        findViewById<Button>(R.id.team2_subtract_button).setOnClickListener {
            viewModel.subtractTeam2Score()
            triggerHapticFeedback()
        }

        // Timer button
        findViewById<Button>(R.id.set_timer_button).setOnClickListener {
            val seconds = timerEditText.text.toString().toLongOrNull()
            if (seconds != null && seconds > 0) {
                viewModel.setKeeperTimer(seconds)
                viewModel.startKeeperTimer()
                Snackbar.make(it, "Keeper timer started: ${seconds}s", Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(it, "Please enter a valid timer duration", Snackbar.LENGTH_SHORT).show()
            }
        }

        // Team name changes
        team1NameEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.setTeam1Name(team1NameEditText.text.toString())
            }
        }

        team2NameEditText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.setTeam2Name(team2NameEditText.text.toString())
            }
        }

        // Reset scores button
        findViewById<Button>(R.id.reset_scores_button).setOnClickListener {
            showEndMatchConfirmation()
        }

        // Match history button
        findViewById<Button>(R.id.match_history_button).setOnClickListener {
            startActivity(Intent(this, MatchHistoryActivity::class.java))
        }

        // Add player buttons
        findViewById<Button>(R.id.add_team1_player_button).setOnClickListener {
            showAddPlayerToTeamDialog(1)
        }

        findViewById<Button>(R.id.add_team2_player_button).setOnClickListener {
            showAddPlayerToTeamDialog(2)
        }

        // FAB for Players Management
        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.players_fab).setOnClickListener {
            startActivity(Intent(this, PlayersManagementActivity::class.java))
        }

        // Team card long click listeners for changing color
        findViewById<View>(R.id.team1_card).setOnLongClickListener {
            viewModel.requestTeamColorChange(1)
            true
        }
        findViewById<View>(R.id.team2_card).setOnLongClickListener {
            viewModel.requestTeamColorChange(2)
            true
        }
    }

    private fun showColorPickerDialog(team: Int) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_color_picker, null)
        val colorPickerView = dialogView.findViewById<ColorPickerView>(R.id.colorPickerView)
        val brightnessSlideBar = dialogView.findViewById<BrightnessSlideBar>(R.id.brightnessSlide)

        colorPickerView.attachBrightnessSlider(brightnessSlideBar)

        MaterialAlertDialogBuilder(this)
            .setTitle("Choose Team $team Color")
            .setView(dialogView)
            .setPositiveButton("Select") { _, _ ->
                viewModel.setTeamColor(team, colorPickerView.color)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddPlayerToTeamDialog(team: Int) {
        val allPlayers = viewModel.allPlayers.value ?: emptyList()
        val teamPlayers = if (team == 1) viewModel.team1Players.value else viewModel.team2Players.value
        val availablePlayers = allPlayers.filter { playerWithRoles ->
            teamPlayers?.none { it.player.playerId == playerWithRoles.player.playerId } ?: true
        }

        if (availablePlayers.isEmpty()) {
            Snackbar.make(
                findViewById(android.R.id.content),
                "No available players. Create new players in Player Management.",
                Snackbar.LENGTH_LONG
            ).setAction("MANAGE") {
                startActivity(Intent(this, PlayersManagementActivity::class.java))
            }.show()
            return
        }

        val playerNames = availablePlayers.map { it.player.playerName }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Add Player to Team $team")
            .setItems(playerNames) { _, which ->
                val selectedPlayer = availablePlayers[which]
                viewModel.addPlayerToTeam(selectedPlayer, team)
                val teamName = if (team == 1) viewModel.team1Name.value else viewModel.team2Name.value
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "${selectedPlayer.player.playerName} added to $teamName",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRemovePlayerDialog(playerWithRoles: PlayerWithRoles, team: Int) {
        val teamName = if (team == 1) viewModel.team1Name.value else viewModel.team2Name.value
        MaterialAlertDialogBuilder(this)
            .setTitle("Remove Player?")
            .setMessage("Remove ${playerWithRoles.player.playerName} from $teamName?")
            .setPositiveButton("Remove") { _, _ ->
                viewModel.removePlayerFromTeam(playerWithRoles, team)
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "${playerWithRoles.player.playerName} removed from $teamName",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSelectScorerDialog(team: Int) {
        val players = if (team == 1) viewModel.team1Players.value else viewModel.team2Players.value

        if (players.isNullOrEmpty()) {
            val teamName = if (team == 1) viewModel.team1Name.value else viewModel.team2Name.value
            Snackbar.make(
                findViewById(android.R.id.content),
                "Goal scored by $teamName!",
                Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        val playerNames = players.map { it.player.playerName }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle("Who scored?")
            .setItems(playerNames) { _, which ->
                val scorer = players[which]
                viewModel.addScorer(team, scorer.player.playerName)
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "⚽ Goal by ${scorer.player.playerName}!",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEndMatchConfirmation() {
        val team1Score = viewModel.team1Score.value ?: 0
        val team2Score = viewModel.team2Score.value ?: 0
        val team1Name = viewModel.team1Name.value ?: "Team 1"
        val team2Name = viewModel.team2Name.value ?: "Team 2"

        MaterialAlertDialogBuilder(this)
            .setTitle("End Match?")
            .setMessage("$team1Name: $team1Score\n$team2Name: $team2Score\n\nSave this match and start a new one?")
            .setPositiveButton("End Match") { _, _ ->
                viewModel.setTeam1Name(team1NameEditText.text.toString())
                viewModel.setTeam2Name(team2NameEditText.text.toString())
                viewModel.endMatch()
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "Match saved!",
                    Snackbar.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("Continue", null)
            .show()
    }

    private fun showKeeperTimerExpiredAlert() {
        triggerStrongVibration()
        MaterialAlertDialogBuilder(this)
            .setTitle("⏰ KEEPER CHANGE!")
            .setMessage("Time to change the goalkeeper!")
            .setPositiveButton("OK") { _, _ ->
                viewModel.resetKeeperTimer()
            }
            .setCancelable(false)
            .show()
    }

    // Helper Methods
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

    private fun triggerHapticFeedback() {
        vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun triggerStrongVibration() {
        val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Activity method overrides for onClick in XML
    fun startStopTimer(view: View) {
        viewModel.startStopMatchTimer()
        val button = view as Button
        button.text = if (button.text == "Start") "Pause" else "Start"
    }

    @Suppress("UNUSED_PARAMETER")
    fun resetTimer(view: View) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset Timer?")
            .setMessage("This will reset the match timer to 00:00")
            .setPositiveButton("Reset") { _, _ ->
                viewModel.resetMatchTimer()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}