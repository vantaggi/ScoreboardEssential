package com.example.scoreboardessential

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.scoreboardessential.database.Player
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    // View references
    private lateinit var team1ScoreTextView: TextView
    private lateinit var team2ScoreTextView: TextView
    private lateinit var matchTimerTextView: TextView
    private lateinit var keeperTimerTextView: TextView
    private lateinit var keeperTimerEditText: EditText
    private lateinit var team1NameEditText: EditText
    private lateinit var team2NameEditText: EditText

    // RecyclerViews
    private lateinit var team1RosterRecyclerView: RecyclerView
    private lateinit var team2RosterRecyclerView: RecyclerView
    private lateinit var matchLogRecyclerView: RecyclerView

    // Adapters
    private lateinit var team1RosterAdapter: TeamRosterAdapter
    private lateinit var team2RosterAdapter: TeamRosterAdapter
    private lateinit var matchLogAdapter: MatchLogAdapter

    // Buttons
    private lateinit var startTimerButton: MaterialButton
    private lateinit var playersFab: FloatingActionButton

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

        vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

        initializeViews()
        setupAdapters()
        observeViewModel()
        setupClickListeners()
        requestNotificationPermission()
    }

    private fun initializeViews() {
        // Score views
        team1ScoreTextView = findViewById(R.id.team1_score_textview)
        team2ScoreTextView = findViewById(R.id.team2_score_textview)

        // Timer views
        matchTimerTextView = findViewById(R.id.timer_textview)
        // Keeper timer potrebbe non esistere in tutti i layout, quindi gestiamo con safe call
        keeperTimerTextView = findViewById(R.id.keeper_timer_textview) ?: TextView(this).apply {
            visibility = View.GONE
        }
        keeperTimerEditText = findViewById(R.id.timer_edittext)

        // Team name inputs
        team1NameEditText = findViewById(R.id.team1_name_edittext)
        team2NameEditText = findViewById(R.id.team2_name_edittext)

        // RecyclerViews - potrebbero non esistere nel layout base
        team1RosterRecyclerView = findViewById(R.id.team1_roster_recyclerview) ?: RecyclerView(this)
        team2RosterRecyclerView = findViewById(R.id.team2_roster_recyclerview) ?: RecyclerView(this)
        matchLogRecyclerView = findViewById(R.id.match_log_recyclerview) ?: RecyclerView(this)

        // Buttons
        startTimerButton = findViewById(R.id.timer_start_button)
        playersFab = findViewById(R.id.players_fab) ?: FloatingActionButton(this).apply {
            visibility = View.GONE
        }
    }

    private fun setupAdapters() {
        // Team 1 Roster Adapter
        team1RosterAdapter = TeamRosterAdapter { player ->
            showRemovePlayerDialog(player, 1)
        }
        team1RosterRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = team1RosterAdapter
        }

        // Team 2 Roster Adapter
        team2RosterAdapter = TeamRosterAdapter { player ->
            showRemovePlayerDialog(player, 2)
        }
        team2RosterRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = team2RosterAdapter
        }

        // Match Log Adapter
        matchLogAdapter = MatchLogAdapter()
        matchLogRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply {
                reverseLayout = false
                stackFromEnd = false
            }
            adapter = matchLogAdapter
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

        // Team Colors
        viewModel.team1Color.observe(this) { color ->
            findViewById<View>(R.id.team1_card)?.setBackgroundColor(color)
        }

        viewModel.team2Color.observe(this) { color ->
            findViewById<View>(R.id.team2_card)?.setBackgroundColor(color)
        }

        // Match Timer
        viewModel.matchTimerValue.observe(this) { timeInMillis ->
            updateTimerTextView(matchTimerTextView, timeInMillis)
        }

        // Keeper Timer
        viewModel.keeperTimerValue.observe(this) { timeInMillis ->
            updateTimerTextView(keeperTimerTextView, timeInMillis)
            if (timeInMillis == 0L && timeInMillis != viewModel.keeperTimerValue.value) {
                showKeeperTimerExpiredAlert()
            }
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
            if (events.isNotEmpty()) {
                matchLogRecyclerView.smoothScrollToPosition(0)
            }
        }

        // UI Events
        viewModel.showSelectScorerDialog.observe(this) { team ->
            if (team != null) {
                showSelectScorerDialog(team)
            }
        }

        viewModel.showPlayerSelectionDialog.observe(this) { team ->
            if (team != null) {
                showPlayerSelectionDialog(team)
            }
        }

        viewModel.showKeeperTimerExpired.observe(this) {
            showKeeperTimerExpiredAlert()
        }
    }

    private fun setupClickListeners() {
        // Score buttons
        findViewById<MaterialButton>(R.id.team1_add_button).setOnClickListener {
            viewModel.addTeam1Score()
            triggerHapticFeedback()
        }

        findViewById<MaterialButton>(R.id.team1_subtract_button).setOnLongClickListener {
            viewModel.subtractTeam1Score()
            triggerHapticFeedback()
            true
        }

        findViewById<MaterialButton>(R.id.team2_add_button).setOnClickListener {
            viewModel.addTeam2Score()
            triggerHapticFeedback()
        }

        findViewById<MaterialButton>(R.id.team2_subtract_button).setOnLongClickListener {
            viewModel.subtractTeam2Score()
            triggerHapticFeedback()
            true
        }

        // Timer buttons
        startTimerButton.setOnClickListener {
            viewModel.startStopMatchTimer()
            updateTimerButtonState()
        }

        findViewById<MaterialButton>(R.id.timer_reset_button).setOnClickListener {
            showResetTimerConfirmation()
        }

        // Keeper Timer
        findViewById<MaterialButton>(R.id.set_timer_button).setOnClickListener {
            val seconds = keeperTimerEditText.text.toString().toLongOrNull()
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

        // Add players buttons
        findViewById<MaterialButton>(R.id.add_team1_player_button)?.setOnClickListener {
            showPlayerSelectionDialog(1)
        }

        findViewById<MaterialButton>(R.id.add_team2_player_button)?.setOnClickListener {
            showPlayerSelectionDialog(2)
        }

        // Match actions
        findViewById<MaterialButton>(R.id.match_history_button).setOnClickListener {
            startActivity(Intent(this, MatchHistoryActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.reset_scores_button).setOnClickListener {
            showEndMatchConfirmation()
        }

        // Players FAB
        playersFab.setOnClickListener {
            showCreatePlayerDialog()
        }
    }

    // Dialog Methods
    private fun showSelectScorerDialog(team: Int) {
        val players = if (team == 1) {
            viewModel.team1Players.value
        } else {
            viewModel.team2Players.value
        }

        if (players.isNullOrEmpty()) {
            Snackbar.make(
                findViewById(android.R.id.content),
                "No players in team. Add players first!",
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        val playerNames = players.map { it.playerName }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("Who scored?")
            .setItems(playerNames) { _, which ->
                viewModel.addScorer(team, playerNames[which])
                triggerHapticFeedback()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPlayerSelectionDialog(team: Int) {
        val allPlayers = viewModel.allPlayers.value
        if (allPlayers.isNullOrEmpty()) {
            showCreatePlayerDialog()
            return
        }

        val currentTeamPlayers = if (team == 1) {
            viewModel.team1Players.value ?: emptyList()
        } else {
            viewModel.team2Players.value ?: emptyList()
        }

        val availablePlayers = allPlayers.filter { player ->
            !currentTeamPlayers.contains(player)
        }

        if (availablePlayers.isEmpty()) {
            Snackbar.make(
                findViewById(android.R.id.content),
                "All players are already assigned. Create new players!",
                Snackbar.LENGTH_LONG
            ).show()
            showCreatePlayerDialog()
            return
        }

        val playerNames = availablePlayers.map { "${it.playerName} (${it.roles})" }.toTypedArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("Add player to Team $team")
            .setItems(playerNames) { _, which ->
                viewModel.addPlayerToTeam(availablePlayers[which], team)
            }
            .setPositiveButton("Create New Player") { _, _ ->
                showCreatePlayerDialog()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCreatePlayerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_player, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.player_name_input)
        val rolesInput = dialogView.findViewById<TextInputEditText>(R.id.player_roles_input)

        MaterialAlertDialogBuilder(this)
            .setTitle("Create New Player")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = nameInput.text.toString().trim()
                val roles = rolesInput.text.toString().trim()

                if (name.isNotEmpty()) {
                    viewModel.createNewPlayer(name, roles)
                    Snackbar.make(
                        findViewById(android.R.id.content),
                        "Player $name created!",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRemovePlayerDialog(player: Player, team: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Remove ${player.playerName}?")
            .setMessage("Remove this player from Team $team?")
            .setPositiveButton("Remove") { _, _ ->
                viewModel.removePlayerFromTeam(player, team)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEndMatchConfirmation() {
        val team1Score = viewModel.team1Score.value ?: 0
        val team2Score = viewModel.team2Score.value ?: 0

        MaterialAlertDialogBuilder(this)
            .setTitle("End Match?")
            .setMessage("Final Score: $team1Score - $team2Score\n\nThis will save the match and reset for a new game.")
            .setPositiveButton("End Match") { _, _ ->
                viewModel.endMatch()
                Snackbar.make(
                    findViewById(android.R.id.content),
                    "Match saved! Starting new match...",
                    Snackbar.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("Continue Playing", null)
            .show()
    }

    private fun showResetTimerConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset Timer?")
            .setMessage("This will reset the match timer to 00:00")
            .setPositiveButton("Reset") { _, _ ->
                viewModel.resetMatchTimer()
            }
            .setNegativeButton("Cancel", null)
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
    private fun updateTimerTextView(textView: TextView, timeInMillis: Long) {
        val minutes = (timeInMillis / 1000) / 60
        val seconds = (timeInMillis / 1000) % 60
        textView.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateTimerButtonState() {
        // This would update the button text based on timer state
        // You can implement this based on your needs
    }

    private fun triggerHapticFeedback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(50)
        }
    }

    private fun triggerStrongVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
            vibrator?.vibrate(pattern, -1)
        }
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

    // Activity method overrides for timer handling
    fun startStopTimer(view: View) {
        viewModel.startStopMatchTimer()
    }

    fun resetTimer(view: View) {
        showResetTimerConfirmation()
    }
}