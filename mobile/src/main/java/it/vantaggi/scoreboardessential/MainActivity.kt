package it.vantaggi.scoreboardessential

import android.Manifest
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
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import it.vantaggi.scoreboardessential.core.ClockMode
import it.vantaggi.scoreboardessential.core.ExportProblem
import it.vantaggi.scoreboardessential.core.ExportResult
import it.vantaggi.scoreboardessential.core.MatchExporter
import it.vantaggi.scoreboardessential.core.MatchSummarizer
import it.vantaggi.scoreboardessential.core.ReportLabels
import it.vantaggi.scoreboardessential.core.ScoreDisplay
import it.vantaggi.scoreboardessential.core.SportCapabilities
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.domain.models.Formation
import it.vantaggi.scoreboardessential.ui.MatchSettingsActivity
import it.vantaggi.scoreboardessential.ui.onboarding.OnboardingActivity
import it.vantaggi.scoreboardessential.ui.statistics.StatisticsActivity
import it.vantaggi.scoreboardessential.utils.ExportBlocked
import it.vantaggi.scoreboardessential.utils.MatchExportUtils
import it.vantaggi.scoreboardessential.utils.MatchReportUtils
import it.vantaggi.scoreboardessential.utils.TimeUtils
import it.vantaggi.scoreboardessential.utils.animateScoreButton
import it.vantaggi.scoreboardessential.utils.playNativeGoalAnimation
import it.vantaggi.scoreboardessential.views.FormationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private lateinit var team1ScoreDetailTextView: TextView
    private lateinit var matchPeriodTextView: TextView
    private lateinit var team2ScoreDetailTextView: TextView
    private lateinit var team2ScoreTextView: TextView
    private lateinit var timerTextView: TextView
    private lateinit var team1Card: MaterialCardView
    private lateinit var team2Card: MaterialCardView
    private lateinit var keeperTimerTextView: TextView
    private lateinit var timerStartButton: Button
    private lateinit var undoGoalButton: Button

    // Sezioni che compaiono o spariscono a seconda dello sport
    private lateinit var rostersCard: View
    private lateinit var formationsCard: View

    // L'ultima configurazione osservata. Il countdown del portiere e l'annulla hanno un LiveData
    // proprio che potrebbe riaccenderli dopo il gating, quindi devono poterla riconsultare.
    private var capabilities: SportCapabilities? = null

    // New view references for refactored layout
    private lateinit var team1NameTextView: TextView
    private lateinit var team2NameTextView: TextView
    private lateinit var vsIndicator: View

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
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        // Edge-to-edge: gli insets di sistema diventano padding del contenitore radice,
        // cosi' coprono sia la NestedScrollView sia i FAB ancorati in basso.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById<View>(R.id.main_root)) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            windowInsets
        }

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
        requestNotificationPermission()

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
        team1ScoreDetailTextView = findViewById(R.id.team1_score_detail_textview)
        matchPeriodTextView = findViewById(R.id.match_period_textview)
        team2ScoreDetailTextView = findViewById(R.id.team2_score_detail_textview)
        team2ScoreTextView = findViewById(R.id.team2_score_textview)
        timerTextView = findViewById(R.id.timer_textview)
        team1Card = findViewById(R.id.team1_card)
        team2Card = findViewById(R.id.team2_card)
        keeperTimerTextView = findViewById(R.id.keeper_timer_textview)
        timerStartButton = findViewById(R.id.timer_start_button)
        undoGoalButton = findViewById(R.id.undo_goal_button)
        rostersCard = findViewById(R.id.rosters_card)
        formationsCard = findViewById(R.id.formations_card)

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
        // Il punteggio a schermo viene dalle regole dello sport, non dagli interi: per il calcio
        // e' la stessa cifra di prima, per gli sport a set e' "40"/"AV" con il dettaglio dei set
        // sotto. La schermata non sa che sport si sta giocando.
        viewModel.scoreDisplay.observe(this) { display ->
            team1ScoreTextView.text = display.side1Primary
            team2ScoreTextView.text = display.side2Primary
            bindScoreDetail(team1ScoreDetailTextView, display.side1Secondary)
            bindScoreDetail(team2ScoreDetailTextView, display.side2Secondary)
            bindPeriod(display)
            applyMatchOver(display.matchOver)
        }

        viewModel.team1Name.observe(this) { name ->
            team1NameTextView.text = name.uppercase(Locale.getDefault())
        }

        viewModel.team2Name.observe(this) { name ->
            team2NameTextView.text = name.uppercase(Locale.getDefault())
        }

        viewModel.team1Color.observe(this) { color ->
            team1Card.setCardBackgroundColor(color)
            applyReadableTextColor(
                color,
                team1NameTextView,
                team1ScoreTextView,
                team1ScoreDetailTextView,
            )
            matchLogAdapter.team1Color = color
            matchLogAdapter.notifyDataSetChanged()
        }

        viewModel.team2Color.observe(this) { color ->
            team2Card.setCardBackgroundColor(color)
            applyReadableTextColor(
                color,
                team2NameTextView,
                team2ScoreTextView,
                team2ScoreDetailTextView,
            )
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

        viewModel.watchBatchApplied.observe(this) { quanti ->
            Snackbar
                .make(
                    findViewById(R.id.main_root),
                    resources.getQuantityString(R.plurals.watch_batch_applied, quanti, quanti),
                    Snackbar.LENGTH_LONG,
                ).show()
        }

        viewModel.watchBatchRejected.observe(this) {
            Snackbar
                .make(findViewById(R.id.main_root), R.string.watch_batch_rejected, Snackbar.LENGTH_LONG)
                .show()
        }

        viewModel.sportChangeRejected.observe(this) {
            // @string/sport_change_blocked era dichiarata e mai usata: era un debito registrato
            // nel piano. Ora ha il suo caso -- l'unico punto dell'app in cui un cambio sport puo'
            // essere chiesto da qualcuno che non vede la guardia.
            Snackbar
                .make(findViewById(R.id.main_root), R.string.sport_change_blocked, Snackbar.LENGTH_LONG)
                .show()
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
                // Il tooltip si vede solo tenendo premuto, e chi usa TalkBack non lo incontra:
                // la contentDescription restava quella cablata nel layout, uguale nei due stati.
                statusIcon.contentDescription = getString(R.string.wear_connected_tooltip)
                statusIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.team_electric_green))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    statusIcon.tooltipText = getString(R.string.wear_connected_tooltip)
                }
            } else {
                statusIcon.setImageResource(R.drawable.ic_watch_disconnected)
                statusIcon.contentDescription = getString(R.string.wear_disconnected_tooltip)
                statusIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.sidewalk_gray))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    statusIcon.tooltipText = getString(R.string.wear_disconnected_tooltip)
                }
            }
        }

        viewModel.shareMatchReportData.observe(this) { data ->
            lifecycleScope.launch(Dispatchers.IO) {
                val shareIntent = MatchReportUtils.generateAndGetShareIntent(this@MainActivity, data)
                withContext(Dispatchers.Main) {
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.share_match_results)))
                }
            }
        }

        viewModel.showOnboarding.observe(this) {
            val intent = Intent(this, OnboardingActivity::class.java)
            startActivity(intent)
        }

        viewModel.canUndo.observe(this) {
            refreshUndoButtonVisibility()
        }

        viewModel.sportCapabilities.observe(this) { sportCapabilities ->
            applyCapabilities(sportCapabilities)
        }

        viewModel.serviceBindingStatus.observe(this) { isBound ->
            timerStartButton.isEnabled = isBound
            timerStartButton.alpha = if (isBound) 1.0f else 0.5f

            val resetButton = findViewById<Button>(R.id.reset_timer_button)
            resetButton?.isEnabled = isBound
            resetButton?.alpha = if (isBound) 1.0f else 0.5f
        }
    }

    /**
     * Accende e spegne le sezioni in base a cosa lo sport prevede. Nessun `when` sullo sport:
     * l'unica cosa che questa schermata sa e' quali capacita' le servono.
     */
    private fun applyCapabilities(sportCapabilities: SportCapabilities) {
        capabilities = sportCapabilities
        // Il cronometro si spegne, la CARD no. settings_button e wear_status_icon vivono dentro
        // timer_card: nasconderla intera toglierebbe all'utente l'ingranaggio delle impostazioni,
        // cioe' l'unico modo per tornare a cambiare sport. Si spegne il blocco cronometro, non il
        // contenitore che ospita anche la barra di intestazione.
        // Negli sport a set sottrarre un punto non e' un'operazione definita: il comando annulla
        // l'ultima azione, qualunque lato l'abbia segnata.
        //
        // Quindi i DUE "-" dentro le due card spariscono, invece di limitarsi a cambiare icona.
        // Lasciarli sarebbe peggio: stanno dentro la card di una squadra, e l'unica lettura
        // possibile di un comando li' dentro e' "togli un punto A QUESTA squadra" -- mentre
        // annullano l'ultima azione e basta. Due comandi identici che sembrano di squadre diverse.
        // Al loro posto resta l'unico annullamento che c'e' gia', quello dichiarato.
        val annullaGlobale = sportCapabilities.decrementIsUndo
        findViewById<View>(R.id.team1_subtract_button_card).visibility = if (annullaGlobale) View.GONE else View.VISIBLE
        findViewById<View>(R.id.team2_subtract_button_card).visibility = if (annullaGlobale) View.GONE else View.VISIBLE

        val orologioVisibile = sportCapabilities.clock != ClockMode.NONE
        findViewById<View>(R.id.match_time_label).visibility = if (orologioVisibile) View.VISIBLE else View.GONE
        findViewById<View>(R.id.timer_textview).visibility = if (orologioVisibile) View.VISIBLE else View.GONE
        findViewById<View>(R.id.timer_controls_row).visibility = if (orologioVisibile) View.VISIBLE else View.GONE
        rostersCard.visibility = if (sportCapabilities.hasRoles) View.VISIBLE else View.GONE
        formationsCard.visibility = if (sportCapabilities.hasRoles) View.VISIBLE else View.GONE
        refreshUndoButtonVisibility()
        updateKeeperTimerTextView(viewModel.keeperTimerValue.value ?: 0L)
    }

    private fun refreshUndoButtonVisibility() {
        // Finche' le capacita' non sono arrivate vale il comportamento storico (il calcio).
        //
        // Serve in due casi diversi: dove si attribuisce il marcatore (il calcio, per disfare un
        // gol) e dove il "meno" E' l'annullamento (padel e tennis) -- li' e' l'UNICO modo di
        // correggere, perche' i due "-" per squadra spariscono: annullavano l'ultima azione
        // qualunque lato l'avesse segnata, ma stando dentro la card di una squadra dicevano il
        // contrario.
        val caps = capabilities
        val allowed = caps == null || caps.attributesScorer || caps.decrementIsUndo
        undoGoalButton.visibility = if (allowed && viewModel.canUndo.value == true) View.VISIBLE else View.GONE
    }

    private fun setupImprovedViews() {
        setupScoreButtons()
        setupMatchActions()
        setupPlayerManagementButtons()
        setupNavigationButtons()
    }

    private fun setupScoreButtons() {
        // I due contenitori del nome avevano il ripple e una contentDescription che prometteva
        // "tocca per cambiare il nome", ma nessun listener: il commento qui diceva "no longer
        // clickable" mentre la card continuava ad accendersi sotto il dito. TeamNameDialogFragment
        // esisteva gia', completo, e non lo apriva nessuno. O si toglieva l'apparenza, o si
        // rimetteva la sostanza: a bordo campo rinominare senza passare dalle impostazioni vale
        // piu' di un controllo in meno.
        findViewById<View>(R.id.team1_name_container).setOnClickListener {
            TeamNameDialogFragment
                .newInstance(1, viewModel.team1Name.value.orEmpty())
                .show(supportFragmentManager, TeamNameDialogFragment.TAG)
        }

        findViewById<View>(R.id.team2_name_container).setOnClickListener {
            TeamNameDialogFragment
                .newInstance(2, viewModel.team2Name.value.orEmpty())
                .show(supportFragmentManager, TeamNameDialogFragment.TAG)
        }

        // New buttons with improved feedback
        findViewById<View>(R.id.team1_add_button_card).setOnClickListener {
            it.animateScoreButton()
            viewModel.addScore(1)
            playGoalAnimation(1)
        }

        findViewById<View>(R.id.team1_subtract_button_card).setOnClickListener {
            it.animateScoreButton(isSubtract = true)
            decrementScore(1)
        }

        findViewById<View>(R.id.team2_add_button_card).setOnClickListener {
            it.animateScoreButton()
            viewModel.addScore(2)
            playGoalAnimation(2)
        }

        findViewById<View>(R.id.team2_subtract_button_card).setOnClickListener {
            it.animateScoreButton(isSubtract = true)
            decrementScore(2)
        }
    }

    /**
     * Il "-" e' una correzione oppure un annullamento, a seconda dello sport: dove il punteggio
     * non e' un contatore (game, set) sottrarre un punto non e' un'operazione definita, quindi si
     * disfa l'ultima azione invece di inventarne l'inversa.
     */
    private fun decrementScore(team: Int) {
        if (capabilities?.decrementIsUndo == true) {
            viewModel.undoLastGoal()
        } else {
            viewModel.subtractScore(team)
        }
    }

    private fun setupMatchActions() {
        findViewById<Button>(R.id.reset_scores_button).setOnClickListener {
            showEndMatchConfirmation()
        }

        findViewById<Button>(R.id.share_match_button).setOnClickListener {
            // Due modi di condividere la stessa partita, non due pulsanti. Il riassunto testuale
            // e' quello che si manda al gruppo appena finito di giocare; il PDF resta per chi
            // vuole archiviare. Una scelta in piu' al momento dell'uso, zero controlli in piu'
            // sulla schermata -- che e' la direzione della Fase D.
            // Le voci sono quelle che hanno senso per QUESTO sport. L'export verso Padel Elite
            // era un quarto pulsante in fila: con quattro comandi sulla stessa riga, i due con
            // testo (HISTORY, END MATCH) restavano schiacciati dai due con icona. Entra qui, e la
            // riga torna a tre comandi in ogni sport.
            val voci =
                buildList {
                    add(getString(R.string.share_as_text))
                    add(getString(R.string.share_as_pdf))
                    if (viewModel.activeSport.value == SportRegistry.PADEL) add(getString(R.string.export_match))
                }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.share_choose_title)
                .setItems(voci.toTypedArray()) { _, quale ->
                    when (quale) {
                        0 -> shareMatchSummary()
                        1 -> viewModel.shareMatchResults()
                        else -> exportMatchToPadel()
                    }
                }.show()
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

    /** Il file JSON per Padel Elite. Si raggiunge dalla stessa scelta di Condividi. */
    private fun exportMatchToPadel() {
        when (val esito = viewModel.buildExport()) {
            is ExportResult.Ready -> {
                MatchExportUtils.shareMatchJson(
                    this,
                    MatchExporter.toJson(esito.export),
                    viewModel.exportFileLabel(),
                )
            }

            is ExportResult.Incomplete -> {
                // Il primo problema in ordine e' quello da risolvere per primo: dirne uno solo
                // e' piu' utile che elencarli tutti a chi e' in piedi a bordo campo.
                val nonCollegati =
                    esito.problems
                        .filterIsInstance<ExportProblem.UnlinkedPlayers>()
                        .flatMap { it.names }
                when {
                    esito.problems.any { it is ExportProblem.NoPoints } -> {
                        MatchExportUtils.showBlocked(this, ExportBlocked.NO_MATCH)
                    }

                    nonCollegati.isNotEmpty() -> {
                        MatchExportUtils.showBlocked(
                            this,
                            ExportBlocked.NEEDS_LINK,
                            nonCollegati.joinToString(", "),
                        )
                    }

                    else -> {
                        MatchExportUtils.showBlocked(this, ExportBlocked.NEEDS_FOUR)
                    }
                }
            }
        }
    }

    /**
     * Compone il riassunto e lo manda a una chat.
     *
     * Le etichette arrivano da qui e non da :core, che e' Kotlin puro e non vede strings.xml: e'
     * il compromesso che tiene il calcolo testabile senza emulatore e il testo traducibile.
     */
    private fun shareMatchSummary() {
        val summary = viewModel.summarizeMatch()
        if (summary.totalPoints == 0) {
            MatchExportUtils.showBlocked(this, ExportBlocked.NO_MATCH)
            return
        }
        val labels =
            ReportLabels(
                vince = getString(R.string.report_wins),
                durata = getString(R.string.report_duration),
                punti = getString(R.string.report_points),
                alServizio = getString(R.string.report_on_serve),
                breakVinti = getString(R.string.report_breaks),
                serieMigliore = getString(R.string.report_best_run),
                marcatori = getString(R.string.report_scorers),
                unitaOre = getString(R.string.report_hours_short),
                unitaMinuti = getString(R.string.report_minutes_short),
                squadra1 = viewModel.team1Name.value.orEmpty(),
                squadra2 = viewModel.team2Name.value.orEmpty(),
            )
        MatchExportUtils.shareMatchText(this, MatchSummarizer.format(summary, labels))
    }

    /**
     * A partita finita i due "+" si spengono.
     *
     * Restavano premibili e non facevano niente: il motore ignora un punto dopo la fine, quindi
     * il numero non cambiava e il tocco spariva nel vuoto. Un comando che si puo' premere e non
     * fa nulla non si distingue da un'app bloccata, ed e' lo stesso difetto per cui il primo
     * annullamento dopo un tocco inerte sembrava non funzionare.
     *
     * L'annullamento NON si spegne: e' esattamente cio' che serve se l'ultimo punto era sbagliato,
     * e riportarlo indietro riaccende tutto perche' lo stato torna "non finita".
     */
    private fun applyMatchOver(finita: Boolean) {
        listOf(R.id.team1_add_button_card, R.id.team2_add_button_card).forEach { id ->
            findViewById<View>(id).apply {
                isClickable = !finita
                isFocusable = !finita
                alpha = if (finita) 0.4f else 1f
                contentDescription =
                    if (finita) getString(R.string.cd_match_over) else getString(cdIncrease(id))
            }
        }
    }

    private fun cdIncrease(id: Int): Int =
        if (id == R.id.team1_add_button_card) {
            R.string.cd_increase_team_1
        } else {
            R.string.cd_increase_team_2
        }

    /**
     * Che periodo si gioca e chi serve.
     *
     * L'orologio lo mostrava e il telefono no, pur avendo gli stessi dati: [ScoreDisplay] porta
     * periodLabel e servingSide, e il telefono li spediva al polso per poi buttarne via meta'.
     * In un padel al meglio di tre, "che set stiamo giocando" serve a chi guarda il tabellone
     * almeno quanto il punteggio. Va nello spazio lasciato libero dal cronometro spento.
     */
    private fun bindPeriod(display: ScoreDisplay) {
        val servente =
            when (display.servingSide) {
                1 -> viewModel.team1Name.value
                2 -> viewModel.team2Name.value
                else -> null
            }
        val testo =
            when {
                display.periodLabel == null -> null
                servente.isNullOrBlank() -> display.periodLabel
                else -> getString(R.string.score_period_serving, display.periodLabel, servente)
            }
        matchPeriodTextView.text = testo.orEmpty().uppercase(Locale.getDefault())
        matchPeriodTextView.visibility = if (testo == null) View.GONE else View.VISIBLE
    }

    /**
     * Il testo di una card si adatta al colore che l'utente ha scelto per quella card.
     *
     * Nome, punteggio e dettaglio erano cablati su concrete_gray (#1E1E1E) mentre lo sfondo lo
     * decide il selettore di colore: su una tinta scura il punteggio diventava quasi invisibile.
     * La soglia sta sulla luminanza percepita e non sulla media dei canali, perche' l'occhio pesa
     * il verde molto piu' del blu: un blu acceso che alla media "sembra chiaro" e' scuro davvero.
     */
    private fun applyReadableTextColor(
        cardColor: Int,
        vararg views: TextView,
    ) {
        val luminanza =
            (
                0.2126 * Color.red(cardColor) +
                    0.7152 * Color.green(cardColor) +
                    0.0722 * Color.blue(cardColor)
            ) / 255.0
        val colore = if (luminanza < 0.5) R.color.stencil_white else R.color.concrete_gray
        views.forEach { it.setTextColor(ContextCompat.getColor(this, colore)) }
    }

    private fun bindScoreDetail(
        view: TextView,
        text: String?,
    ) {
        view.text = text.orEmpty()
        view.visibility = if (text.isNullOrEmpty()) View.GONE else View.VISIBLE
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
        val teamPlayerIds = teamPlayers?.map { it.player.playerId }?.toSet() ?: emptySet()
        val availablePlayers =
            allPlayers.filter { playerWithRoles ->
                !teamPlayerIds.contains(playerWithRoles.player.playerId)
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
        timerTextView.text = TimeUtils.formatTime(timeInMillis)
    }

    private fun updateKeeperTimerTextView(timeInMillis: Long) {
        // Finche' le capacita' non sono arrivate vale il comportamento storico (il calcio).
        val visibile = timeInMillis > 0 && capabilities?.hasAuxCountdown != false
        if (visibile) {
            keeperTimerTextView.text = TimeUtils.formatTime(timeInMillis)
        }
        keeperTimerTextView.visibility = if (visibile) View.VISIBLE else View.GONE
        // L'etichetta segue il suo cronometro: da sola non significherebbe niente.
        findViewById<View>(R.id.keeper_timer_label).visibility = if (visibile) View.VISIBLE else View.GONE
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
