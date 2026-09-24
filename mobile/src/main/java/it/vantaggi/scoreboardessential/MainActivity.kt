package it.vantaggi.scoreboardessential

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
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
import it.vantaggi.scoreboardessential.core.TeamInk
import it.vantaggi.scoreboardessential.database.PlayerWithRoles
import it.vantaggi.scoreboardessential.domain.models.Formation
import it.vantaggi.scoreboardessential.domain.models.MatchEvent
import it.vantaggi.scoreboardessential.domain.models.MatchEventType
import it.vantaggi.scoreboardessential.ui.MatchSettingsActivity
import it.vantaggi.scoreboardessential.ui.onboarding.OnboardingActivity
import it.vantaggi.scoreboardessential.ui.statistics.StatisticsActivity
import it.vantaggi.scoreboardessential.utils.ExportBlocked
import it.vantaggi.scoreboardessential.utils.FabOverlap
import it.vantaggi.scoreboardessential.utils.MatchExportUtils
import it.vantaggi.scoreboardessential.utils.MatchReportUtils
import it.vantaggi.scoreboardessential.utils.TimeUtils
import it.vantaggi.scoreboardessential.utils.animateScoreButton
import it.vantaggi.scoreboardessential.utils.etichettaDiSquadra
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La lingua non si applica piu' qui ma per tutta l'app (vedi LocaleHelper): resta solo
        // da recuperare, una volta, la scelta fatta con la versione precedente.
        it.vantaggi.scoreboardessential.utils.LocaleHelper
            .migrateLegacyChoice(this)
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

            // Prima si riallinea lo stato, cosi' l'icona in barra dice il vero. Niente toast: a ogni
            // rotazione ricompariva "Wear OS Not Connected", e lo stato lo mostra gia' l'icona
            // tramite isWearConnected.
            viewModel.connectionManager.refreshConnection()
            val testResult = viewModel.connectionManager.testConnection()
            if (testResult) {
                Log.d("ConnectionTest", "✅ CONNECTION TEST PASSED")
            } else {
                Log.e("ConnectionTest", "❌ CONNECTION TEST FAILED")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Il Bluetooth puo' essere caduto mentre l'app era in secondo piano, e nessun listener lo
        // segnala: senza questo l'icona dell'orologio restava "collegato".
        lifecycleScope.launch { viewModel.connectionManager.refreshConnection() }
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

        matchLogAdapter =
            MatchLogAdapter { evento ->
                // La rosa del lato che ha segnato: attribuire a un giocatore dell'altra squadra
                // non e' un caso da gestire, e' un caso da non offrire.
                val rosa = if (evento.team == 1) viewModel.team1Players.value else viewModel.team2Players.value
                val indice = evento.engineIndex
                if (rosa.isNullOrEmpty() || indice == null) {
                    Snackbar
                        .make(findViewById(R.id.main_root), R.string.no_players_to_attribute, Snackbar.LENGTH_LONG)
                        .show()
                } else {
                    mostraDialogo(
                        SelectScorerDialogFragment.newInstance(rosa, evento.team ?: 1, indice),
                        SelectScorerDialogFragment.TAG,
                    )
                }
            }
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
            aggiornaSchermoAcceso()
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
            // Nel riquadro delle rose la squadra aveva il colore del TEMA (rosa o ciano)
            // mentre la sua card sopra aveva quello scelto dall'utente: le stesse due
            // squadre con due coppie di colori diverse sulla stessa schermata. Rose e
            // formazioni portano il colore come riempimento del tag, mai come testo.
            findViewById<TextView>(R.id.team1_roster_label).etichettaDiSquadra(color)
            team1FormationLabel.etichettaDiSquadra(color)
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
            findViewById<TextView>(R.id.team2_roster_label).etichettaDiSquadra(color)
            team2FormationLabel.etichettaDiSquadra(color)
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
            aggiornaSchermoAcceso()
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
        // Negli sport senza marcatore non esistono gol: pulsante, dialogo e registro parlano di
        // punti, e le righe del registro smettono di offrire un marcatore da scegliere.
        undoGoalButton.setText(
            if (sportCapabilities.attributesScorer) R.string.label_undo_last_goal else R.string.label_undo_last_point,
        )
        matchLogAdapter.attribuisceMarcatore = sportCapabilities.attributesScorer
        matchLogAdapter.notifyDataSetChanged()
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
        keepFabsOffMatchActions()
    }

    /**
     * I due FAB non devono mai stare sopra HISTORY, END MATCH o SHARE.
     *
     * Si confrontano i rettangoli veri a schermo, non un margine: quanto spazio serva dipende
     * dall'altezza dello schermo e da cosa c'e' nell'intestazione fissa (l'annulla che compare
     * spinge giu' la riga). Si ricalcola a ogni scorrimento e a ogni cambio di layout, sull'intera
     * finestra: cosi' vale uguale in verticale e in orizzontale, dove scorre un contenitore diverso.
     * Quando la riga non e' nella fascia dei FAB, i FAB tornano: restano raggiungibili ovunque
     * tranne proprio li', e a fine scorrimento il margine in fondo la porta sopra di loro.
     */
    private fun keepFabsOffMatchActions() {
        val fabs =
            listOf(R.id.stats_fab, R.id.players_fab)
                .map { findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(it) }
        val azioni =
            listOf(R.id.match_history_button, R.id.reset_scores_button, R.id.share_match_button)
                .map { findViewById<View>(it) }

        // La posizione di un FAB nascosto resta quella dell'ultimo layout, quindi il confronto
        // continua a valere anche mentre e' nascosto. show()/hide() ripetuti non fanno nulla.
        val aggiorna = {
            val nascondi = FabOverlap.fabsMustHide(azioni.map { it.boxInWindow() }, fabs.map { it.boxInWindow() })
            fabs.forEach { if (nascondi) it.hide() else it.show() }
        }
        val osservatore = window.decorView.viewTreeObserver
        osservatore.addOnScrollChangedListener { aggiorna() }
        osservatore.addOnGlobalLayoutListener { aggiorna() }
    }

    private fun View.boxInWindow(): FabOverlap.Box {
        val xy = IntArray(2)
        getLocationInWindow(xy)
        return FabOverlap.Box(xy[0], xy[1], xy[0] + width, xy[1] + height)
    }

    /**
     * Mostra un dialogo solo se l'activity puo' ancora ospitarlo.
     *
     * `DialogFragment.show` usa `commit()`, che dopo `onSaveInstanceState` lancia
     * `IllegalStateException` e porta giu' l'app. Non e' teoria: e' il modo in cui questo test
     * strumentato e' diventato rosso la prima volta che ha girato su un dispositivo vero.
     *
     * Un tocco che arriva mentre l'activity sta salvando lo stato non viene eseguito, e va bene
     * cosi': in quel momento l'activity sta andando via e non c'e' nessuna schermata su cui
     * mostrare un dialogo. Meglio un tocco perso di un'app chiusa.
     */
    private fun mostraDialogo(
        fragment: androidx.fragment.app.DialogFragment,
        tag: String,
    ) {
        if (supportFragmentManager.isStateSaved) return
        fragment.show(supportFragmentManager, tag)
    }

    private fun setupScoreButtons() {
        // I due contenitori del nome avevano il ripple e una contentDescription che prometteva
        // "tocca per cambiare il nome", ma nessun listener: il commento qui diceva "no longer
        // clickable" mentre la card continuava ad accendersi sotto il dito. TeamNameDialogFragment
        // esisteva gia', completo, e non lo apriva nessuno. O si toglieva l'apparenza, o si
        // rimetteva la sostanza: a bordo campo rinominare senza passare dalle impostazioni vale
        // piu' di un controllo in meno.
        findViewById<View>(R.id.team1_name_container).setOnClickListener {
            mostraDialogo(
                TeamNameDialogFragment.newInstance(1, viewModel.team1Name.value.orEmpty()),
                TeamNameDialogFragment.TAG,
            )
        }

        findViewById<View>(R.id.team2_name_container).setOnClickListener {
            mostraDialogo(
                TeamNameDialogFragment.newInstance(2, viewModel.team2Name.value.orEmpty()),
                TeamNameDialogFragment.TAG,
            )
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
            // Finche' le capacita' non sono arrivate vale il calcio, come per la visibilita'.
            val gol = capabilities?.attributesScorer != false
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(if (gol) R.string.undo_goal_title else R.string.undo_point_title))
                .setMessage(getString(if (gol) R.string.undo_goal_message else R.string.undo_point_message))
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
     * L'inchiostro lo decide [TeamInk], la sola regola del colore per telefono e orologio. Quella
     * che c'era qui (media NON linearizzata, soglia 0,5, #E0E0E0 o #1E1E1E) scendeva a 2,07:1 su
     * #FF4BFF.
     */
    private fun applyReadableTextColor(
        cardColor: Int,
        vararg views: TextView,
    ) {
        val inchiostro = TeamInk.on(cardColor)
        views.forEach { it.setTextColor(inchiostro) }
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

    /**
     * Un colpo breve di sistema, non piu' la sequenza da 500ms che partiva a ogni + in tutti gli
     * sport: nel padel, con un punto ogni pochi secondi, durava quasi quanto lo scambio di tocchi.
     */
    private fun playGoalVibrationPattern() {
        vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }

    /**
     * Lo schermo resta acceso finche' c'e' una partita viva, e torna a seguire il timeout di
     * sistema appena finisce o ne comincia una nuova. Senza, fra un punto e l'altro lo schermo si
     * spegneva e a bordo campo andava sbloccato a ogni punto.
     */
    private fun aggiornaSchermoAcceso() {
        val acceso =
            schermoDaTenereAcceso(
                viewModel.matchEvents.value,
                viewModel.scoreDisplay.value?.matchOver == true,
            )
        if (acceso) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        engineIndex: Int,
    ) {
        viewModel.attributeScorer(engineIndex, playerWithRoles)
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
            }.setNeutralButton(getString(R.string.btn_discard_match)) { _, _ ->
                // "Termina" salva, "scarta" butta via: due intenzioni diverse, due comandi
                // diversi. Prima esisteva solo la prima, quindi una partita cominciata per
                // sbaglio poteva solo finire nello storico.
                val messaggio =
                    if (viewModel.discardMatch()) R.string.match_discarded else R.string.match_not_started_error
                Snackbar
                    .make(findViewById(android.R.id.content), getString(messaggio), Snackbar.LENGTH_LONG)
                    .show()
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

    // Da risorsa: "No formation" era cablato in inglese anche con l'app in italiano.
    private fun testoFormazione(
        nomeSquadra: String,
        formazione: Formation,
    ): String =
        if (formazione.isValid()) {
            getString(R.string.formation_label, nomeSquadra, formazione.getFormationString())
        } else {
            getString(R.string.formation_none, nomeSquadra)
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
                team1FormationLabel.text = testoFormazione(teamName, formation)
            }

            2 -> {
                team2FormationView.setFormation(formation)
                val teamName = viewModel.team2Name.value ?: "Team 2"
                team2FormationLabel.text = testoFormazione(teamName, formation)
            }
        }
    }
}

/**
 * Se lo schermo va tenuto acceso: almeno un punto segnato e partita non finita.
 *
 * Un punto e non un avvio: una partita aperta e mai giocata non deve tenere acceso il telefono in
 * tasca. Finita la partita, o cominciata la nuova che svuota il registro, lo schermo torna al
 * timeout di sistema. Sta fuori dall'Activity perche' sotto Robolectric MainActivity non si
 * monta: cosi' la decisione si prova da sola.
 */
internal fun schermoDaTenereAcceso(
    eventi: List<MatchEvent>?,
    partitaFinita: Boolean,
): Boolean = !partitaFinita && eventi.orEmpty().any { it.type == MatchEventType.SCORE }
