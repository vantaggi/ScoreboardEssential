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
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.text.Editable
import android.text.TextWatcher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.utils.playEnhancedScoreAnimation
import it.vantaggi.scoreboardessential.utils.playNativeGoalAnimation
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.sliders.BrightnessSlideBar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.MainViewModelFactory(
            (application as ScoreboardEssentialApplication).matchRepository,
            application
        )
    }

    // Core view references
    private lateinit var team1ScoreTextView: TextView
    private lateinit var team2ScoreTextView: TextView
    private lateinit var timerTextView: TextView
    private lateinit var team1Card: MaterialCardView
    private lateinit var team2Card: MaterialCardView
    private lateinit var keeperTimerTextView: TextView
    private lateinit var timerEditText: EditText
    private lateinit var timerStartButton: Button

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

    private var vibrator: Vibrator? = null

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
        setupImprovedViews() // Call new setup method
        setupGestureControls() // Call gesture setup
        requestNotificationPermission()
    }

    private fun initializeViews() {
        // Core views
        team1ScoreTextView = findViewById(R.id.team1_score_textview)
        team2ScoreTextView = findViewById(R.id.team2_score_textview)
        timerTextView = findViewById(R.id.timer_textview)
        team1Card = findViewById(R.id.team1_card)
        team2Card = findViewById(R.id.team2_card)
        keeperTimerTextView = findViewById(R.id.keeper_timer_textview)
        timerEditText = findViewById(R.id.timer_edittext)
        timerStartButton = findViewById(R.id.timer_start_button)

        // New Views
        team1NameTextView = findViewById(R.id.team1_name_textview)
        team2NameTextView = findViewById(R.id.team2_name_textview)
        vsIndicator = findViewById(R.id.vs_indicator)

        // Roster RecyclerViews
        team1RosterRecyclerView = findViewById(R.id.team1_roster_recyclerview)
        team2RosterRecyclerView = findViewById(R.id.team2_roster_recyclerview)
        matchLogRecyclerView = findViewById(R.id.match_log_recyclerview)
    }

    private fun setupRecyclerViews() {
        team1RosterAdapter = TeamRosterAdapter { playerWithRoles ->
            showRemovePlayerDialog(playerWithRoles, 1)
        }
        team1RosterRecyclerView.apply {
            adapter = team1RosterAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }

        team2RosterAdapter = TeamRosterAdapter { playerWithRoles ->
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
        }

        viewModel.team2Players.observe(this) { players ->
            team2RosterAdapter.submitList(players)
        }

        viewModel.matchEvents.observe(this) { events ->
            matchLogAdapter.submitList(events)
        }

        viewModel.showSelectScorerDialog.observe(this) { team ->
            showSelectScorerDialog(team)
        }

        viewModel.showColorPickerDialog.observe(this) { team ->
            showColorPickerDialog(team)
        }

        viewModel.isMatchTimerRunning.observe(this) { isRunning ->
            timerStartButton.text = if (isRunning) "PAUSE" else "START"
        }

        viewModel.isWearConnected.observe(this) { isConnected ->
            val statusIcon = findViewById<ImageView>(R.id.wear_status_icon)
            if (isConnected) {
                statusIcon.setImageResource(R.drawable.ic_watch_connected)
                statusIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.team_electric_green))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    statusIcon.tooltipText = "Wear OS Connected"
                }
            } else {
                statusIcon.setImageResource(R.drawable.ic_watch_disconnected)
                statusIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sidewalk_gray))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    statusIcon.tooltipText = "Wear OS Disconnected"
                }
            }
        }

        viewModel.shareMatchEvent.observe(this) { intent ->
            startActivity(Intent.createChooser(intent, "Share Match Results"))
        }
    }

    private fun setupImprovedViews() {
        // Setup new TextView clickable for names
        findViewById<View>(R.id.team1_name_container).setOnClickListener {
            showTeamNameDialog(1)
        }

        findViewById<View>(R.id.team2_name_container).setOnClickListener {
            showTeamNameDialog(2)
        }

        // New buttons with improved feedback
        findViewById<View>(R.id.team1_add_button_card).setOnClickListener {
            animateScoreButton(it)
            viewModel.addTeam1Score()
            playGoalAnimation(1)
        }

        findViewById<View>(R.id.team1_subtract_button_card).setOnClickListener {
            animateScoreButton(it, isSubtract = true)
            viewModel.subtractTeam1Score()
        }

        findViewById<View>(R.id.team2_add_button_card).setOnClickListener {
            animateScoreButton(it)
            viewModel.addTeam2Score()
            playGoalAnimation(2)
        }

        findViewById<View>(R.id.team2_subtract_button_card).setOnClickListener {
            animateScoreButton(it, isSubtract = true)
            viewModel.subtractTeam2Score()
        }

        // Keep other listeners from original setupClickListeners
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

        findViewById<Button>(R.id.reset_scores_button).setOnClickListener {
            showEndMatchConfirmation()
        }

        findViewById<Button>(R.id.match_history_button).setOnClickListener {
            startActivity(Intent(this, MatchHistoryActivity::class.java))
        }

        findViewById<Button>(R.id.add_team1_player_button).setOnClickListener {
            showAddPlayerToTeamDialog(1)
        }

        findViewById<Button>(R.id.add_team2_player_button).setOnClickListener {
            showAddPlayerToTeamDialog(2)
        }

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.players_fab).setOnClickListener {
            startActivity(Intent(this, PlayersManagementActivity::class.java))
        }

        findViewById<View>(R.id.team1_card).setOnLongClickListener {
            viewModel.requestTeamColorChange(1)
            true
        }
        findViewById<View>(R.id.team2_card).setOnLongClickListener {
            viewModel.requestTeamColorChange(2)
            true
        }

        findViewById<Button>(R.id.share_match_button).setOnClickListener {
            viewModel.shareMatchResults()
        }
    }

    private fun setupGestureControls() {
        // Swipe up to increase, swipe down to decrease for Team 1
        team1GestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                e1?.let {
                    val diffY = e2.y - it.y
                    if (Math.abs(diffY) > 100) {
                        if (diffY < 0) {
                            viewModel.addTeam1Score()
                            playGoalAnimation(1)
                        } else {
                            viewModel.subtractTeam1Score()
                        }
                        return true
                    }
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                viewModel.addTeam1Score()
                playGoalAnimation(1)
                return true
            }
        })

        findViewById<View>(R.id.team1_card).setOnTouchListener { _, event ->
            team1GestureDetector.onTouchEvent(event)
        }

        // Swipe up to increase, swipe down to decrease for Team 2
        team2GestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                e1?.let {
                    val diffY = e2.y - it.y
                    if (Math.abs(diffY) > 100) {
                        if (diffY < 0) {
                            viewModel.addTeam2Score()
                            playGoalAnimation(2)
                        } else {
                            viewModel.subtractTeam2Score()
                        }
                        return true
                    }
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                viewModel.addTeam2Score()
                playGoalAnimation(2)
                return true
            }
        })

        findViewById<View>(R.id.team2_card).setOnTouchListener { _, event ->
            team2GestureDetector.onTouchEvent(event)
        }
    }

    internal fun showTeamNameDialog(teamNumber: Int) {
    // 1. Inflate the dialog view
        val dialogView = layoutInflater.inflate(R.layout.dialog_team_name, null)

    // 2. Find views AFTER inflating the layout
        val editText = dialogView.findViewById<TextInputEditText>(R.id.team_name_input)
    val previewText = dialogView.findViewById<TextView>(R.id.preview_text)
    val suggestionsChipGroup = dialogView.findViewById<ChipGroup>(R.id.suggestions_chips)

        val currentName = if (teamNumber == 1) viewModel.team1Name.value else viewModel.team2Name.value
        editText.setText(currentName)
    previewText.text = currentName // Set initial preview text

    // Add a listener to update the preview in real-time
    editText.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            previewText.text = s.toString().uppercase()
        }
        override fun afterTextChanged(s: Editable?) {}
    })

    // Add listeners for suggestion chips
    for (i in 0 until suggestionsChipGroup.childCount) {
        val chip = suggestionsChipGroup.getChildAt(i) as? Chip
        chip?.setOnClickListener {
            editText.setText(chip.text)
        }
    }

    // 3. Build and show the dialog
        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_App_MaterialAlertDialog)
            .setTitle("Edit Team $teamNumber")
        .setView(dialogView) // Use the prepared view
            .setPositiveButton("SAVE") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    if (teamNumber == 1) {
                        viewModel.setTeam1Name(newName)
                        animateTextChange(team1NameTextView, newName)
                    } else {
                        viewModel.setTeam2Name(newName)
                        animateTextChange(team2NameTextView, newName)
                    }
                }
            }
            .setNeutralButton("CHANGE COLOR") { _, _ ->
                viewModel.requestTeamColorChange(teamNumber)
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun animateScoreButton(view: View, isSubtract: Boolean = false) {
        val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.9f, 1.1f, 1f)
        val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.9f, 1.1f, 1f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 200
            interpolator = OvershootInterpolator()
            start()
        }

        if (view is MaterialCardView) {
            val originalColor = view.cardBackgroundColor
            val targetColor = if (isSubtract)
                ColorStateList.valueOf(Color.parseColor("#FF1744"))
            else
                ColorStateList.valueOf(Color.parseColor("#76FF03"))

            ValueAnimator.ofArgb(originalColor.defaultColor, targetColor.defaultColor).apply {
                duration = 300
                addUpdateListener { animator ->
                    view.setCardBackgroundColor(animator.animatedValue as Int)
                }
                addListener(object : android.animation.Animator.AnimatorListener {
                    override fun onAnimationStart(animation: android.animation.Animator) {}
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        view.setCardBackgroundColor(originalColor)
                    }
                    override fun onAnimationCancel(animation: android.animation.Animator) {}
                    override fun onAnimationRepeat(animation: android.animation.Animator) {}
                })
                start()
            }
        }
    }


    private fun animateTextChange(textView: TextView, newText: String) {
        textView.animate()
            .alpha(0f)
            .translationY(-20f)
            .setDuration(150)
            .withEndAction {
                textView.text = newText.uppercase()
                textView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(150)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            }
            .start()
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
        vsIndicator.animate()
            .rotationBy(360f)
            .scaleX(1.3f)
            .scaleY(1.3f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                vsIndicator.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(200)
                    .start()
            }
            .start()
    }

    private fun playGoalVibrationPattern() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 100, 50, 100, 50, 200)
            val amplitudes = intArrayOf(0, 128, 0, 255, 0, 128)
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
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
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun startStopTimer(view: View) {
        viewModel.startStopMatchTimer()
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