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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.scoreboardessential.database.Player
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    // Core view references that exist in base layout
    private lateinit var team1ScoreTextView: TextView
    private lateinit var team2ScoreTextView: TextView
    private lateinit var timerTextView: TextView
    private lateinit var timerEditText: EditText
    private lateinit var team1NameEditText: EditText
    private lateinit var team2NameEditText: EditText

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
        observeViewModel()
        setupClickListeners()
        requestNotificationPermission()
    }

    private fun initializeViews() {
        // Core views that exist in base layout
        team1ScoreTextView = findViewById(R.id.team1_score_textview)
        team2ScoreTextView = findViewById(R.id.team2_score_textview)
        timerTextView = findViewById(R.id.timer_textview)
        timerEditText = findViewById(R.id.timer_edittext)
        team1NameEditText = findViewById(R.id.team1_name_edittext)
        team2NameEditText = findViewById(R.id.team2_name_edittext)
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
            team1ScoreTextView.setBackgroundColor(color)
        }

        viewModel.team2Color.observe(this) { color ->
            team2ScoreTextView.setBackgroundColor(color)
        }

        // Match Timer
        viewModel.matchTimerValue.observe(this) { timeInMillis ->
            updateTimerTextView(timeInMillis)
        }

        // UI Events
        viewModel.showSelectScorerDialog.observe(this) { team ->
            if (team != null) {
                showSelectScorerDialog(team)
            }
        }

        viewModel.showKeeperTimerExpired.observe(this) {
            showKeeperTimerExpiredAlert()
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

        // Players management button (if exists)
        findViewById<Button>(R.id.players_management_button)?.setOnClickListener {
            startActivity(Intent(this, PlayersManagementActivity::class.java))
        }

        // Color customization buttons (if they exist)
        findViewById<ImageButton>(R.id.team1_customize_button)?.setOnClickListener {
            showColorPicker(1)
        }

        findViewById<ImageButton>(R.id.team2_customize_button)?.setOnClickListener {
            showColorPicker(2)
        }

        // Players management button (if exists in layout)
        findViewById<Button>(R.id.players_management_button)?.setOnClickListener {
            startActivity(Intent(this, PlayersManagementActivity::class.java))
        }
    }

    private fun showColorPicker(team: Int) {
        ColorPickerDialog.Builder(this)
            .setTitle("Team $team Color")
            .setPreferenceName("Team${team}ColorPicker")
            .setPositiveButton(getString(R.string.confirm),
                ColorEnvelopeListener { envelope, _ ->
                    viewModel.setTeamColor(team, envelope.color)
                })
            .setNegativeButton(getString(R.string.cancel)) { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .attachAlphaSlideBar(true)
            .attachBrightnessSlideBar(true)
            .show()
    }

    private fun showSelectScorerDialog(team: Int) {
        // For now, just add the goal without selecting scorer
        // since we don't have player management in base layout
        val teamName = if (team == 1) viewModel.team1Name.value else viewModel.team2Name.value
        Snackbar.make(
            findViewById(android.R.id.content),
            "Goal scored by $teamName!",
            Snackbar.LENGTH_SHORT
        ).show()
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
        timerTextView.text = String.format("%02d:%02d", minutes, seconds)
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

    // Activity method overrides for onClick in XML
    fun startStopTimer(view: View) {
        viewModel.startStopMatchTimer()
        val button = view as? Button
        button?.text = if (button?.text == "Start") "Pause" else "Start"
    }

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