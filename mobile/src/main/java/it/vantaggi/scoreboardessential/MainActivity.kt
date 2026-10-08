package it.vantaggi.scoreboardessential

import android.Manifest
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnNextLayout
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import it.vantaggi.scoreboardessential.core.ClockMode
import it.vantaggi.scoreboardessential.core.ExportResult
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
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager
import it.vantaggi.scoreboardessential.ui.InsetDividerDecoration
import it.vantaggi.scoreboardessential.ui.MatchSettingsActivity
import it.vantaggi.scoreboardessential.ui.onboarding.OnboardingActivity
import it.vantaggi.scoreboardessential.ui.statistics.StatisticsActivity
import it.vantaggi.scoreboardessential.utils.ExportBlocked
import it.vantaggi.scoreboardessential.utils.MatchExportUtils
import it.vantaggi.scoreboardessential.utils.MatchReportUtils
import it.vantaggi.scoreboardessential.utils.NumberRoll
import it.vantaggi.scoreboardessential.utils.TimeUtils
import it.vantaggi.scoreboardessential.utils.etichettaConBarretta
import it.vantaggi.scoreboardessential.views.FormationView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Quanto scurisce la colonna di gioco a foglio PARTITA aperto: nero al 60%.
private const val SCRIM_ALPHA = 0.6f

// Un comando spento (ANNULLA senza niente da annullare) resta al suo posto, ma a 0,38.
private const val ALPHA_SPENTO = 0.38f

// Quanto resta un messaggio temporaneo nella striscia prima che torni il testo base.
private const val DURATA_MESSAGGIO_STRISCIA_MS = 3000L

// Il token piu' largo che il punteggio di uno sport puo' mostrare: "88" nel calcio, "AV" (vantaggio)
// nella racchetta. Con questo si misura il numero una volta sola, invece di ridimensionarlo a ogni punto.
private const val TOKEN_PIU_LARGO_CALCIO = "88"
private const val TOKEN_PIU_LARGO_RACCHETTA = "AV"

// La dimensione a cui si misura il token; il risultato si scala linearmente.
private const val PROVA_DI_MISURA = 100f

// Nel dettaglio dei set il separatore fra set chiusi e game correnti ("6-4 · 3-2"): con lui la didascalia e' SET · GAME.
private const val SEPARATORE_SET_GAME = " · "

// Il tempo piu' lungo che il pulsante del cronometro mostra: oltre i 99 minuti le cifre diventano sei.
private const val TEMPO_PIU_LUNGO = "100:00"

/**
 * La larghezza minima del pulsante del tempo: quella che ha con il tempo piu' lungo, padding e
 * icona compresi. Cosi' passando i 99 minuti il pulsante non si allarga e non sposta il portiere
 * e il resto della barra. Si misura con il pennello del pulsante, che ha gia' la dimensione e i
 * caratteri di sistema di adesso.
 */
internal fun larghezzaMinimaDelTempo(pulsante: MaterialButton): Int {
    val testo = Math.ceil(pulsante.paint.measureText(TEMPO_PIU_LUNGO).toDouble()).toInt()
    return testo + pulsante.paddingLeft + pulsante.paddingRight + pulsante.iconSize + pulsante.iconPadding
}

/**
 * La didascalia del dettaglio sotto i numeri, o null se non c'e' niente da dire. A partita finita
 * non ci sono game correnti e il dettaglio contiene solo i set chiusi: e' SET, anche se senza il
 * separatore fra set e game.
 */
@StringRes
internal fun didascaliaDelDettaglio(
    testo: String?,
    partitaFinita: Boolean,
): Int? =
    when {
        testo.isNullOrEmpty() -> null
        partitaFinita -> R.string.caption_set
        testo.contains(SEPARATORE_SET_GAME) -> R.string.caption_set_game
        else -> R.string.caption_game
    }

// Il secondo argomento di VibrationEffect.createWaveform: -1 e' "suona una volta e basta".
private const val NON_RIPETERE = -1

// Lo stroke della zona + scura sul nero, in dp.
private const val STROKE_ZONA_DP = 2

// Sotto questo contrasto contro il nero il colore di squadra e' grafica troppo debole: la zona + prende lo stroke.
private const val CONTRASTO_MINIMO_GRAFICA = 3.0

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
    private lateinit var scoreDetailValue: TextView
    private lateinit var scoreDetailCaption: TextView
    private lateinit var matchPeriodTextView: TextView
    private lateinit var team1Zone: MaterialCardView
    private lateinit var team2Zone: MaterialCardView
    private lateinit var keeperTimerTextView: TextView
    private lateinit var keeperSlot: MaterialCardView
    private lateinit var keeperTimerLabel: TextView

    // La dimensione del conto del portiere com'e' nel layout: CAMBIO la riduce, il conto la ripristina.
    private var dimensioneDelConto = 0f
    private lateinit var timerStartButton: MaterialButton
    private lateinit var undoGoalButton: Button
    private lateinit var lastActionStrip: View
    private lateinit var lastActionText: TextView

    // Vero mentre la striscia mostra un messaggio di 3 secondi: il testo base aspetta la fine.
    private var messaggioInStriscia = false
    private val fineMessaggioInStriscia =
        Runnable {
            messaggioInStriscia = false
            aggiornaStriscia()
        }

    // Sezioni che compaiono o spariscono a seconda dello sport
    private lateinit var rostersCard: View
    private lateinit var formationsCard: View

    // Il foglio PARTITA: parte nascosto, si apre con il pulsante della barra.
    private lateinit var matchSheet: BottomSheetBehavior<View>
    private lateinit var chiudiConIndietro: OnBackPressedCallback

    // L'ultima configurazione osservata. Il countdown del portiere e l'annulla hanno un LiveData
    // proprio che potrebbe riaccenderli dopo il gating, quindi devono poterla riconsultare.
    private var capabilities: SportCapabilities? = null

    // New view references for refactored layout
    private lateinit var team1NameTextView: TextView
    private lateinit var team2NameTextView: TextView

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

    // L'istante dell'ultimo tocco su ANNULLA accettato, per ignorare il rimbalzo del dito.
    private var ultimoToccoAnnulla: Long? = null

    // Il punteggio che rotola (G-6): uno per lato. Il verso lo da' chi ha provocato il cambio (un punto
    // sale, una correzione o un annullamento scende) e vale solo dentro quella chiamata; senza, il verso
    // si ricava confrontando i due punteggi (un punto dell'orologio).
    private lateinit var rotolo1: NumberRoll
    private lateinit var rotolo2: NumberRoll
    private var versoDelCambio: Int? = null
    private var latoDelCambio: Int? = null

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted: Boolean ->
            if (!isGranted) {
                snackbarSopraLaStriscia(getString(R.string.notification_permission_required), Snackbar.LENGTH_LONG).show()
            }
        }

    // La Serata torna con OK quando l'utente preme "Inizia la partita": le coppie composte diventano le rose.
    private val serataLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { risultato ->
            if (risultato.resultCode != RESULT_OK) return@registerForActivityResult
            if (viewModel.avviaPartitaDellaSerata()) {
                matchSheet.state = BottomSheetBehavior.STATE_HIDDEN
                val numero = viewModel.serataInCorso()?.numeroDellaProssima ?: 1
                snackbarSopraLaStriscia(getString(R.string.serata_started, numero), Snackbar.LENGTH_LONG).show()
            } else {
                snackbarSopraLaStriscia(getString(R.string.serata_cannot_start), Snackbar.LENGTH_LONG).show()
            }
            aggiornaLaSerata()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La lingua non si applica piu' qui ma per tutta l'app (vedi LocaleHelper): resta solo
        // da recuperare, una volta, la scelta fatta con la versione precedente.
        it.vantaggi.scoreboardessential.utils.LocaleHelper
            .migrateLegacyChoice(this)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        // Edge-to-edge: gli insets di sistema diventano padding della sola colonna di gioco. Sul
        // contenitore radice NO: BottomSheetBehavior posiziona il foglio su parent.getHeight() e lo
        // misura togliendo il padding del genitore, quindi il fondo del foglio finiva sotto la barra
        // di navigazione. Il foglio gestisce i suoi con padding*SystemWindowInsets.
        val colonna = findViewById<View>(R.id.scoreboard_live)
        val paddingIniziale = intArrayOf(colonna.paddingLeft, colonna.paddingTop, colonna.paddingRight, colonna.paddingBottom)
        ViewCompat.setOnApplyWindowInsetsListener(colonna) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(
                paddingIniziale[0] + bars.left,
                paddingIniziale[1] + bars.top,
                paddingIniziale[2] + bars.right,
                paddingIniziale[3] + bars.bottom,
            )
            // Non consumati: il foglio, fratello della colonna, deve riceverli a sua volta.
            windowInsets
        }

        // Il foglio sale fin sotto la barra di stato, ma il behavior non ne sposta il contenuto:
        // l'inset in alto diventa padding del dettaglio, cosi' titolo e CHIUDI restano leggibili.
        val dettagli = findViewById<View>(R.id.scoreboard_details)
        val paddingTopDettagli = dettagli.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(dettagli) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(view.paddingLeft, paddingTopDettagli + bars.top, view.paddingRight, view.paddingBottom)
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

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        // Ricreata durante l'animazione, BottomSheetBehavior ripristina STATE_COLLAPSED anche con
        // skipCollapsed: resterebbe una striscia di foglio sopra le zone da toccare. Il foglio non
        // ha una posizione intermedia, quindi si riporta a nascosto.
        if (matchSheet.state == BottomSheetBehavior.STATE_COLLAPSED) {
            matchSheet.state = BottomSheetBehavior.STATE_HIDDEN
        }
        // Lo stato ripristinato del foglio non passa dai callback del behavior: senza questo,
        // dopo una ricreazione a foglio aperto indietro uscirebbe dall'app invece di chiuderlo, e
        // la colonna resterebbe raggiungibile (o nascosta) da TalkBack come prima della ricreazione.
        allineaAlFoglio(matchSheet.state)
    }

    override fun onResume() {
        super.onResume()
        // Il Bluetooth puo' essere caduto mentre l'app era in secondo piano, e nessun listener lo
        // segnala: senza questo l'icona dell'orologio restava "collegato".
        lifecycleScope.launch { viewModel.connectionManager.refreshConnection() }
        if (::matchSheet.isInitialized) aggiornaLaSerata()
    }

    override fun onDestroy() {
        // Il ritorno al testo base dopo 3 secondi e' accodato sulla vista: non deve sopravvivere
        // all'Activity e tenerla in vita fino alla sua scadenza.
        if (::lastActionText.isInitialized) lastActionText.removeCallbacks(fineMessaggioInStriscia)
        super.onDestroy()
    }

    private fun initializeViews() {
        // Core views
        team1ScoreTextView = findViewById(R.id.team1_score_textview)
        team2ScoreTextView = findViewById(R.id.team2_score_textview)
        rotolo1 = NumberRoll(team1ScoreTextView)
        rotolo2 = NumberRoll(team2ScoreTextView)
        scoreDetailValue = findViewById(R.id.score_detail_value)
        scoreDetailCaption = findViewById(R.id.score_detail_caption)
        matchPeriodTextView = findViewById(R.id.match_period_textview)
        // Il testo della vittoria accorcia il nome in base alla larghezza: quando la barra prende la
        // sua misura (la prima volta arriva dopo il primo bind) il testo si riscrive.
        matchPeriodTextView.addOnLayoutChangeListener { _, sinistra, _, destra, _, vecchiaSinistra, _, vecchiaDestra, _ ->
            if (destra - sinistra != vecchiaDestra - vecchiaSinistra) viewModel.scoreDisplay.value?.let(::bindPeriod)
        }
        team1Zone = findViewById(R.id.team1_add_button_card)
        team2Zone = findViewById(R.id.team2_add_button_card)
        keeperTimerTextView = findViewById(R.id.keeper_timer_textview)
        keeperSlot = findViewById(R.id.keeper_slot)
        keeperTimerLabel = findViewById(R.id.keeper_timer_label)
        dimensioneDelConto = keeperTimerTextView.textSize
        keeperTimerTextView.minWidth = kotlin.math.ceil(larghezzaDelContoPiuLargo()).toInt()
        timerStartButton = findViewById(R.id.timer_start_button)
        timerStartButton.minWidth = larghezzaMinimaDelTempo(timerStartButton)
        undoGoalButton = findViewById(R.id.undo_goal_button)
        lastActionStrip = findViewById(R.id.last_action_strip)
        lastActionText = findViewById(R.id.last_action_text)
        rostersCard = findViewById(R.id.rosters_card)
        formationsCard = findViewById(R.id.formations_card)

        // New Views
        team1NameTextView = findViewById(R.id.team1_name_textview)
        team2NameTextView = findViewById(R.id.team2_name_textview)

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
            addItemDecoration(InsetDividerDecoration(this@MainActivity, 0))
        }

        team2RosterAdapter =
            TeamRosterAdapter { playerWithRoles ->
                showRemovePlayerDialog(playerWithRoles, 2)
            }
        team2RosterRecyclerView.apply {
            adapter = team2RosterAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            addItemDecoration(InsetDividerDecoration(this@MainActivity, 0))
        }

        matchLogAdapter = MatchLogAdapter { evento -> apriAttribuzione(evento) }
        matchLogRecyclerView.apply {
            adapter = matchLogAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
            addItemDecoration(InsetDividerDecoration(this@MainActivity, 0))
        }
    }

    private fun observeViewModel() {
        // Il punteggio a schermo viene dalle regole dello sport, non dagli interi: per il calcio
        // e' la stessa cifra di prima, per gli sport a set e' "40"/"AV" con il dettaglio dei set
        // sotto. La schermata non sa che sport si sta giocando.
        viewModel.scoreDisplay.observe(this) { display ->
            rollaIlPunteggio(1, rotolo1, team1ScoreTextView, display.side1Primary)
            rollaIlPunteggio(2, rotolo2, team2ScoreTextView, display.side2Primary)
            bindScoreDetail(display.side1Secondary, display.matchOver)
            bindPeriod(display)
            applyMatchOver(display)
            aggiornaDescrizioni()
            aggiornaSchermoAcceso()
            aggiornaStriscia()
        }

        viewModel.team1Name.observe(this) { name ->
            mostraNomeSquadra(findViewById(R.id.team1_name_container), team1NameTextView, name)
            // Il servente e il vincitore nella barra portano il nome: rinominare li riscrive.
            viewModel.scoreDisplay.value?.let(::bindPeriod)
            aggiornaDescrizioni()
            aggiornaStriscia()
            aggiornaLeCoppie()
        }

        viewModel.team2Name.observe(this) { name ->
            mostraNomeSquadra(findViewById(R.id.team2_name_container), team2NameTextView, name)
            viewModel.scoreDisplay.value?.let(::bindPeriod)
            aggiornaDescrizioni()
            aggiornaStriscia()
            aggiornaLeCoppie()
        }

        viewModel.team1Color.observe(this) { color ->
            aggiornaZona(1, color)
            // Nel riquadro delle rose la squadra aveva il colore del TEMA (rosa o ciano)
            // mentre la sua card sopra aveva quello scelto dall'utente: le stesse due
            // squadre con due coppie di colori diverse sulla stessa schermata. Rose e
            // formazioni portano il colore come riempimento del tag, mai come testo.
            findViewById<TextView>(R.id.team1_roster_label).etichettaConBarretta(color)
            findViewById<TextView>(R.id.team1_pair_label).etichettaConBarretta(color)
            team1FormationLabel.etichettaConBarretta(color)
            matchLogAdapter.team1Color = color
            matchLogAdapter.notifyDataSetChanged()
        }

        viewModel.team2Color.observe(this) { color ->
            aggiornaZona(2, color)
            findViewById<TextView>(R.id.team2_roster_label).etichettaConBarretta(color)
            findViewById<TextView>(R.id.team2_pair_label).etichettaConBarretta(color)
            team2FormationLabel.etichettaConBarretta(color)
            matchLogAdapter.team2Color = color
            matchLogAdapter.notifyDataSetChanged()
        }

        viewModel.matchTimerValue.observe(this) { timeInMillis ->
            updateTimerTextView(timeInMillis)
        }

        viewModel.keeperTimerValue.observe(this) { timeInMillis ->
            updateKeeperTimerTextView(timeInMillis)
        }

        // Il colore del conto dipende anche dal fatto che corra: si riscrive al cambio di stato.
        viewModel.isKeeperTimerRunning.observe(this) {
            updateKeeperTimerTextView(viewModel.keeperTimerValue.value ?: 0L)
        }

        // SCADUTO viene dall'evento di scadenza e dura fino al tocco o all'azzeramento.
        viewModel.isKeeperTimerExpired.observe(this) {
            updateKeeperTimerTextView(viewModel.keeperTimerValue.value ?: 0L)
        }

        viewModel.team1Players.observe(this) { players ->
            team1RosterAdapter.submitList(players)
            updateFormation(1, players)
            aggiornaLeCoppie()
        }

        viewModel.team2Players.observe(this) { players ->
            team2RosterAdapter.submitList(players)
            updateFormation(2, players)
            aggiornaLeCoppie()
        }

        // La notizia dell'orologio e' uno stato: il badge e la card restano finche' non c'e' una
        // partita nuova, anche dopo una ricreazione. Il riepilogo dei punti entrati sta nella
        // striscia per 3 secondi, una volta sola (takeWatchSummary): una Snackbar copriva le zone +.
        viewModel.watchNotice.observe(this) { notizia ->
            aggiornaIconaOrologio()
            findViewById<View>(R.id.watch_notice_card).visibility =
                if (notizia is WatchNotice.Rejected) View.VISIBLE else View.GONE
            viewModel.takeWatchSummary()?.let { punti ->
                mostraMessaggioInStriscia(resources.getQuantityString(R.plurals.strip_msg_from_watch, punti, punti))
            }
        }

        viewModel.sportChangeRejected.observe(this) {
            // @string/sport_change_blocked era dichiarata e mai usata: era un debito registrato
            // nel piano. Ora ha il suo caso -- l'unico punto dell'app in cui un cambio sport puo'
            // essere chiesto da qualcuno che non vede la guardia.
            snackbarSopraLaStriscia(getString(R.string.sport_change_blocked), Snackbar.LENGTH_LONG).show()
        }

        // Il registro a schermo ha una riga per game nel padel e nel tennis: la striscia e lo schermo
        // acceso leggono invece le righe dei punti, che stanno in matchEvents.
        viewModel.registroDelFoglio.observe(this) { righe ->
            matchLogAdapter.submitList(righe)
        }

        viewModel.matchEvents.observe(this) {
            aggiornaSchermoAcceso()
            aggiornaStriscia()
        }

        // Lo stato si legge dal glifo e dal colore del tempo, non da una parola che cambia larghezza.
        viewModel.isMatchTimerRunning.observe(this) { isRunning ->
            timerStartButton.setIconResource(if (isRunning) R.drawable.ic_pause else R.drawable.ic_play_arrow)
            timerStartButton.setTextColor(ContextCompat.getColor(this, if (isRunning) R.color.ink_white else R.color.elite_text_secondary))
            ViewCompat.setStateDescription(
                timerStartButton,
                getString(if (isRunning) R.string.timer_state_running else R.string.timer_state_stopped),
            )
        }

        viewModel.isWearConnected.observe(this) { aggiornaIconaOrologio() }

        viewModel.shareMatchReportData.observe(this) { data ->
            // Letto qui, sul thread principale, prima di passare al thread di I/O.
            val attributesScorer = viewModel.sportCapabilities.value?.attributesScorer == true
            lifecycleScope.launch(Dispatchers.IO) {
                val shareIntent = MatchReportUtils.generateAndGetShareIntent(this@MainActivity, data, attributesScorer)
                withContext(Dispatchers.Main) {
                    if (shareIntent == null) {
                        Toast.makeText(this@MainActivity, R.string.report_pdf_failed, Toast.LENGTH_LONG).show()
                    } else {
                        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_match_results)))
                    }
                }
            }
        }

        viewModel.showOnboarding.observe(this) {
            val intent = Intent(this, OnboardingActivity::class.java)
            startActivity(intent)
        }

        viewModel.canUndo.observe(this) {
            refreshUndoButtonState()
            // Il registro non vuoto e' anche il lucchetto dello scambio delle coppie.
            aggiornaLeCoppie()
        }

        viewModel.sportCapabilities.observe(this) { sportCapabilities ->
            applyCapabilities(sportCapabilities)
        }

        viewModel.serviceBindingStatus.observe(this) { isBound ->
            timerStartButton.isEnabled = isBound
            timerStartButton.alpha = if (isBound) 1.0f else 0.5f

            findViewById<Button>(R.id.sheet_reset_timer_button).apply {
                isEnabled = isBound
                alpha = if (isBound) 1.0f else 0.5f
            }
        }
    }

    /**
     * Accende e spegne le sezioni in base a cosa lo sport prevede. Nessun `when` sullo sport:
     * l'unica cosa che questa schermata sa e' quali capacita' le servono.
     *
     * E' l'UNICO punto in cui una vista della colonna di gioco cambia visibilita': succede al
     * cambio sport, che a partita iniziata la guardia blocca. Durante la partita niente cambia
     * misura (vedi content_scoreboard_live).
     */
    private fun applyCapabilities(sportCapabilities: SportCapabilities) {
        capabilities = sportCapabilities
        // Negli sport a set sottrarre un punto non e' un'operazione definita: il comando annulla
        // l'ultima azione, qualunque lato l'abbia segnata. Quindi i due -1 ai bordi della riga dei
        // nomi spariscono: lasciarli sarebbe peggio, perche' un comando accanto al nome di una
        // squadra si legge "togli un punto A QUESTA squadra" mentre annullerebbe e basta. Al loro
        // posto resta l'unico annullamento che c'e' gia', quello della striscia. Per lo stesso
        // motivo negli sport a set compare il dettaglio (game, set) sotto i numeri.
        val sportASet = sportCapabilities.decrementIsUndo
        findViewById<View>(R.id.team1_subtract_button_card).visibility = if (sportASet) View.GONE else View.VISIBLE
        findViewById<View>(R.id.team2_subtract_button_card).visibility = if (sportASet) View.GONE else View.VISIBLE
        findViewById<View>(R.id.score_detail_container).visibility = if (sportASet) View.VISIBLE else View.GONE

        // Il tempo e il portiere sono slot della barra: senza cronometro spariscono e il testo del
        // periodo (che fa da molla) prende il loro posto. L'ingranaggio e il reset non sono piu' in
        // barra: stanno nel foglio, dove il reset segue lo stesso cronometro.
        val orologioVisibile = sportCapabilities.clock != ClockMode.NONE
        timerStartButton.visibility = if (orologioVisibile) View.VISIBLE else View.GONE
        findViewById<View>(R.id.keeper_slot).visibility = if (sportCapabilities.hasAuxCountdown) View.VISIBLE else View.GONE
        findViewById<View>(R.id.sheet_reset_timer_button).visibility = if (orologioVisibile) View.VISIBLE else View.GONE
        // Le rose restano in ogni sport: nel padel e nel tennis sono l'unico posto dove si
        // assegnano i giocatori ai lati, e senza di loro l'export verso Padel Elite (4 giocatori,
        // 2 per lato) e l'ordine di servizio per nome erano impossibili. Legarle alle formazioni le
        // spegneva insieme a loro, che invece sono davvero solo del calcio. Le coppie seguono i
        // giocatori per lato: ci sono nel padel e nel tennis in doppio, non nel calcio.
        rostersCard.visibility = View.VISIBLE
        formationsCard.visibility = if (sportCapabilities.hasFormations) View.VISIBLE else View.GONE
        aggiornaLeCoppie()
        aggiornaLaSerata()
        refreshUndoButtonState()
        // Negli sport senza marcatore non esistono gol: il dialogo e il registro parlano di punti, e
        // le righe del registro smettono di offrire un marcatore da scegliere.
        matchLogAdapter.attribuisceMarcatore = sportCapabilities.attributesScorer
        matchLogAdapter.notifyDataSetChanged()
        aggiornaDescrizioni()
        aggiornaStriscia()
        dimensionaINumeri(if (sportASet) TOKEN_PIU_LARGO_RACCHETTA else TOKEN_PIU_LARGO_CALCIO)
    }

    /**
     * Il gruppo Serata del foglio: c'e' solo negli sport a coppie (padel, tennis). Dice se la serata c'e' e
     * quanti sono; il comando la apre (la crea al primo tocco sui presenti).
     */
    private fun aggiornaLaSerata() {
        findViewById<View>(R.id.serata_card).visibility = if (viewModel.sportDellaSerata()) View.VISIBLE else View.GONE
        val serata = viewModel.serataInCorso()
        val riassunto = findViewById<TextView>(R.id.serata_summary)
        val comando = findViewById<MaterialButton>(R.id.serata_open_button)
        if (serata == null || serata.presenti.isEmpty()) {
            riassunto.text = getString(R.string.serata_sheet_none)
            comando.setText(R.string.serata_sheet_new)
        } else {
            riassunto.text =
                resources.getQuantityString(R.plurals.serata_sheet_summary, serata.presenti.size, serata.presenti.size, serata.giocate.size)
            comando.setText(R.string.serata_sheet_open)
        }
    }

    /** Apre la Serata; con una partita in corso e' di sola lettura. */
    private fun apriLaSerata() {
        val inCorso = viewModel.canUndo.value == true
        serataLauncher.launch(Intent(this, SerataActivity::class.java).putExtra(SerataActivity.EXTRA_PARTITA_IN_CORSO, inCorso))
    }

    /**
     * La card COPPIE segue le capacita' (giocatori per lato), le rose e il registro: lo scambio
     * si spegne dal primo punto, come l'ordine di servizio che non cambia a partita iniziata.
     */
    private fun aggiornaLeCoppie() {
        val rosa1 = viewModel.team1Players.value.orEmpty()
        val rosa2 = viewModel.team2Players.value.orEmpty()
        mostraLeCoppie(
            findViewById(R.id.scoreboard_details),
            capabilities?.playersPerSide,
            listOf(rosa1.map { it.player.playerName }, rosa2.map { it.player.playerName }),
            listOf(viewModel.team1Name.value ?: "Team 1", viewModel.team2Name.value ?: "Team 2"),
            viewModel.canUndo.value == true,
        )
    }

    /**
     * ANNULLA non sparisce mai: senza niente da annullare si spegne (isEnabled false, alpha 0,38).
     * Una partita arrivata dall'orologio mostra 5-3 con ANNULLA spento, invece di un pulsante che
     * compare e sposta tutto quello che sta intorno.
     */
    private fun refreshUndoButtonState() {
        // Finche' le capacita' non sono arrivate vale il comportamento storico (il calcio).
        //
        // Serve in due casi diversi: dove si attribuisce il marcatore (il calcio, per disfare un
        // gol) e dove il "meno" E' l'annullamento (padel e tennis) -- li' e' l'UNICO modo di
        // correggere, perche' i due -1 per squadra spariscono.
        val caps = capabilities
        val allowed = caps == null || caps.attributesScorer || caps.decrementIsUndo
        val acceso = allowed && viewModel.canUndo.value == true
        undoGoalButton.isEnabled = acceso
        undoGoalButton.alpha = if (acceso) 1f else ALPHA_SPENTO
    }

    /**
     * Il testo base della striscia e cosa fa toccandola. Non cambia mai l'altezza della striscia.
     * Mentre un messaggio di 3 secondi e' in mostra non si tocca: alla sua fine si rilegge tutto.
     */
    private fun aggiornaStriscia() {
        if (messaggioInStriscia) return
        val stato =
            statoDellaStriscia(
                this,
                viewModel.matchEvents.value,
                capabilities,
                viewModel.scoreDisplay.value,
                viewModel.team1Name.value ?: "Team 1",
                viewModel.team2Name.value ?: "Team 2",
            )
        ViewCompat.setAccessibilityLiveRegion(lastActionText, ViewCompat.ACCESSIBILITY_LIVE_REGION_NONE)
        lastActionText.text = stato.testo
        impostaAzioneDellaStriscia(stato.daAttribuire, stato.terminaPartita)
    }

    /**
     * Il tocco sulla striscia e' la scorciatoia del marcatore: apre lo stesso dialogo della riga del
     * registro. A partita finita e' invece la scorciatoia di TERMINA e apre il dialogo di fine
     * partita. Senza nessuna delle due la striscia non e' un bersaglio. In quest'ordine:
     * setOnClickListener rende la vista cliccabile anche quando riceve null (vedi MatchLogAdapter).
     */
    private fun impostaAzioneDellaStriscia(
        daAttribuire: MatchEvent?,
        terminaPartita: Boolean = false,
    ) {
        val azione =
            when {
                terminaPartita -> View.OnClickListener { showEndMatchConfirmation() }
                daAttribuire != null -> View.OnClickListener { apriAttribuzione(daAttribuire) }
                else -> null
            }
        lastActionStrip.setOnClickListener(azione)
        lastActionStrip.isClickable = azione != null
    }

    /**
     * Un messaggio di 3 secondi al posto del testo base, poi si torna al testo base. Sostituisce le
     * Snackbar che coprivano le zone +. La regione live fa leggere il messaggio da TalkBack.
     */
    private fun mostraMessaggioInStriscia(testo: String) {
        lastActionText.removeCallbacks(fineMessaggioInStriscia)
        messaggioInStriscia = true
        ViewCompat.setAccessibilityLiveRegion(lastActionText, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
        lastActionText.text = testo
        impostaAzioneDellaStriscia(null)
        lastActionText.postDelayed(fineMessaggioInStriscia, DURATA_MESSAGGIO_STRISCIA_MS)
    }

    /** Un nuovo punto toglie subito il messaggio: il testo base deve dire l'ultima azione vera. */
    private fun chiudiMessaggioInStriscia() {
        lastActionText.removeCallbacks(fineMessaggioInStriscia)
        messaggioInStriscia = false
        aggiornaStriscia()
    }

    /**
     * Apre la scelta del marcatore per il gol di questo evento. La usano la riga del registro e la
     * striscia, cosi' fanno la stessa cosa.
     *
     * La rosa e' quella del lato che ha segnato: attribuire a un giocatore dell'altra squadra non e'
     * un caso da gestire, e' un caso da non offrire.
     */
    private fun apriAttribuzione(evento: MatchEvent) {
        val rosa = if (evento.team == 1) viewModel.team1Players.value else viewModel.team2Players.value
        val indice = evento.engineIndex
        if (rosa.isNullOrEmpty() || indice == null) {
            snackbarSopraLaStriscia(getString(R.string.no_players_to_attribute), Snackbar.LENGTH_LONG).show()
        } else {
            mostraDialogo(
                SelectScorerDialogFragment.newInstance(rosa, evento.team ?: 1, indice),
                SelectScorerDialogFragment.TAG,
            )
        }
    }

    /**
     * Ogni Snackbar dell'Activity passa di qui: ancorata sopra la striscia, cosi' non copre le zone +
     * che stanno in fondo alla colonna.
     *
     * A foglio PARTITA aperto la striscia sta sotto lo scrim: una Snackbar ancorata a lei
     * galleggerebbe sulle righe del foglio, quindi senza ancora sta in fondo allo schermo.
     */
    private fun snackbarSopraLaStriscia(
        testo: CharSequence,
        durata: Int,
    ): Snackbar {
        val strisciaInVista =
            !::matchSheet.isInitialized ||
                matchSheet.state == BottomSheetBehavior.STATE_HIDDEN ||
                matchSheet.state == BottomSheetBehavior.STATE_COLLAPSED
        return Snackbar
            .make(findViewById(R.id.main_root), testo, durata)
            .setAnchorView(if (strisciaInVista) lastActionStrip else null)
    }

    /**
     * La dimensione del numero, una volta per sport, uguale per tutta la partita.
     *
     * Si misura il token piu' largo dello sport nella meta' di colonna e si prende il minimo fra il
     * tetto (150) e cio' che entra, per larghezza e per altezza, con un pavimento di 72. Niente
     * autoSize di sistema: cambierebbe dimensione fra "1" e "15", e il numero e' la cosa che si
     * legge a due metri. Tutto in pixel: con il carattere di sistema al 200% un limite in sp
     * raddoppierebbe e il numero uscirebbe dal suo spazio.
     */
    private fun dimensionaINumeri(token: String) {
        // Il chiamante ha appena cambiato la visibilita' di qualcosa sopra o sotto la riga (il
        // dettaglio dei set): doOnLayout, con la vista gia' misurata, girerebbe subito sulla misura
        // vecchia. requestLayout obbliga un passaggio nuovo e doOnNextLayout aspetta quello.
        val riga = findViewById<View>(R.id.score_row)
        riga.requestLayout()
        riga.doOnNextLayout {
            val pixel = dimensioneDelNumero(team1ScoreTextView.paint, token, riga.width / 2f, riga.height.toFloat())
            team1ScoreTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, pixel)
            team2ScoreTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, pixel)
            // La scatola della cifra ha la larghezza del token piu' largo: fra "1" e "15" niente si sposta.
            NumberRoll.fissaLaScatola(team1ScoreTextView, token)
            NumberRoll.fissaLaScatola(team2ScoreTextView, token)
        }
    }

    private fun dimensioneDelNumero(
        pennello: Paint,
        token: String,
        larghezzaMezzaColonna: Float,
        altezzaRiga: Float,
    ): Float {
        val massimo = resources.getDimension(R.dimen.score_text_max)
        val minimo = resources.getDimension(R.dimen.score_text_min)
        val prova = Paint(pennello).apply { textSize = PROVA_DI_MISURA }
        val larghezza = prova.measureText(token)
        val metriche = prova.fontMetrics
        val altezza = metriche.descent - metriche.ascent
        // 16dp di aria fra il numero e il bordo della meta' di colonna.
        val disponibile = larghezzaMezzaColonna - 16 * resources.displayMetrics.density
        val perLarghezza = PROVA_DI_MISURA * disponibile / larghezza
        val perAltezza = PROVA_DI_MISURA * altezzaRiga / altezza
        return minOf(massimo, perLarghezza, perAltezza).coerceAtLeast(minimo)
    }

    private fun setupImprovedViews() {
        setupScoreButtons()
        setupMatchActions()
        setupPlayerManagementButtons()
        setupNavigationButtons()
        setupMatchSheet()
    }

    /**
     * Il foglio PARTITA: rose, registro, formazioni e azioni, sopra la colonna di gioco.
     *
     * Parte nascosto e dal nascosto passa direttamente ad aperto (skipCollapsed): una via di mezzo
     * lascerebbe una striscia di foglio sopra le zone da toccare. Indietro lo richiude prima di
     * uscire: il callback e' acceso solo finche' il foglio non e' nascosto, cosi' a foglio chiuso
     * indietro torna a fare cio' che ha sempre fatto.
     */
    private fun setupMatchSheet() {
        matchSheet = BottomSheetBehavior.from(findViewById(R.id.match_sheet))
        // Dopo una ricreazione il behavior ripristina da solo lo stato salvato, piu' tardi
        // (onRestoreInstanceState): qui si fissa solo il punto di partenza.
        matchSheet.state = BottomSheetBehavior.STATE_HIDDEN

        chiudiConIndietro =
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    matchSheet.state = BottomSheetBehavior.STATE_HIDDEN
                }
            }
        onBackPressedDispatcher.addCallback(this, chiudiConIndietro)
        matchSheet.addBottomSheetCallback(
            object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(
                    bottomSheet: View,
                    newState: Int,
                ) {
                    allineaAlFoglio(newState)
                }

                override fun onSlide(
                    bottomSheet: View,
                    slideOffset: Float,
                ) {
                    // Il behavior non da' un offset unico da nascosto ad aperto (va da -1 a 0 fino
                    // al punto di riposo, poi da 0 a 1): si legge la parte di foglio in vista.
                    val inVista = (findViewById<View>(R.id.main_root).height - bottomSheet.top).coerceAtLeast(0)
                    val frazione = if (bottomSheet.height > 0) (inVista.toFloat() / bottomSheet.height).coerceIn(0f, 1f) else 0f
                    findViewById<View>(R.id.match_sheet_scrim).alpha = SCRIM_ALPHA * frazione
                }
            },
        )
        findViewById<View>(R.id.match_sheet_scrim).setOnClickListener {
            matchSheet.state = BottomSheetBehavior.STATE_HIDDEN
        }

        findViewById<View>(R.id.match_sheet_button).setOnClickListener {
            matchSheet.state = BottomSheetBehavior.STATE_EXPANDED
        }
        // L'icona dell'orologio apre lo stesso foglio: li' sta la card che spiega il badge. Il foglio
        // e' una NestedScrollView e tiene lo scrollY anche da nascosto: si riporta in cima SEMPRE,
        // non solo con una notizia, perche' chi tocca l'icona cerca lo stato dell'orologio e questo
        // sta in testa al foglio; senza, la card poteva restare fuori vista.
        findViewById<View>(R.id.wear_status_icon).setOnClickListener {
            findViewById<NestedScrollView>(R.id.match_sheet).scrollTo(0, 0)
            matchSheet.state = BottomSheetBehavior.STATE_EXPANDED
        }
        findViewById<View>(R.id.match_sheet_close_button).setOnClickListener {
            matchSheet.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    /**
     * Allinea indietro, scrim e accessibilita' allo stato del foglio.
     *
     * A foglio non nascosto la colonna di gioco sta sotto lo scrim: nessun tocco la raggiunge, e
     * TalkBack non deve piu' arrivarci (un doppio tocco segnava un punto sotto il foglio).
     */
    private fun allineaAlFoglio(stato: Int) {
        val nascosto = stato == BottomSheetBehavior.STATE_HIDDEN
        chiudiConIndietro.isEnabled = !nascosto
        val scrim = findViewById<View>(R.id.match_sheet_scrim)
        scrim.visibility = if (nascosto) View.GONE else View.VISIBLE
        if (stato == BottomSheetBehavior.STATE_EXPANDED) scrim.alpha = SCRIM_ALPHA
        if (nascosto) scrim.alpha = 0f
        findViewById<View>(R.id.scoreboard_live).importantForAccessibility =
            if (nascosto) {
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            } else {
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
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
        // Lo slot del portiere: da fermo o scaduto avvia dalla durata piena, in corso riparte da
        // capo. Un tocco breve come quello di un punto; lo stato lo dicono il colore e il conto.
        keeperSlot.setOnClickListener {
            vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            viewModel.toccaIlPortiere()
        }

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

        team1Zone.setOnClickListener { toccaLaZona(1) }

        // Il -1 e' una correzione rara e voluta: ha la pressione condivisa e niente altro.
        findViewById<View>(R.id.team1_subtract_button_card).setOnClickListener {
            decrementScore(1)
        }

        team2Zone.setOnClickListener { toccaLaZona(2) }

        findViewById<View>(R.id.team2_subtract_button_card).setOnClickListener {
            decrementScore(2)
        }
    }

    /**
     * Il tocco su una zona +. A partita finita la zona e' spenta ma risponde: niente punto, un colpo
     * lungo e «PARTITA FINITA» per 3 secondi nella striscia. Altrimenti e' un punto, e il riscontro
     * (game chiuso, partita finita) si decide dai due display attorno alla chiamata: il ViewModel
     * aggiorna lo stato in modo sincrono, e un punto dell'orologio non passa da qui.
     */
    private fun toccaLaZona(team: Int) {
        if (viewModel.scoreDisplay.value?.matchOver == true) {
            vibrator?.vibrate(VibrationEffect.createWaveform(HapticFeedbackManager.PATTERN_INERT_TAP, NON_RIPETERE))
            mostraMessaggioInStriscia(getString(R.string.strip_msg_match_over))
            return
        }
        // Il riscontro del tocco (scala 0,97 e opacita' 0,85) e' lo StateListAnimator condiviso della zona.
        chiudiMessaggioInStriscia()
        val prima = viewModel.scoreDisplay.value
        coneIlVerso(NumberRoll.SU, team) { viewModel.addScore(team) }
        val riscontro =
            riscontroDelPunto(
                prima,
                viewModel.scoreDisplay.value,
                capabilities?.decrementIsUndo == true,
                modalitaAGame(viewModel.activeSport.value ?: SportRegistry.FOOTBALL),
            )
        playGoalVibrationPattern(riscontro)
    }

    /**
     * Esegue [azione] dicendo al punteggio che rotola in che verso cambia: [verso] vale per il [lato]
     * (o per tutti e due se null) e solo finche' l'azione non ritorna, cosi' un cambio che arriva da
     * altrove (l'orologio) non lo eredita.
     */
    private fun <T> coneIlVerso(
        verso: Int,
        lato: Int?,
        azione: () -> T,
    ): T {
        versoDelCambio = verso
        latoDelCambio = lato
        try {
            return azione()
        } finally {
            versoDelCambio = null
            latoDelCambio = null
        }
    }

    /**
     * Scrive il punteggio di un lato facendolo rotolare nel verso del cambio: quello dichiarato da chi
     * l'ha provocato, altrimenti quello che dicono i due valori.
     */
    private fun rollaIlPunteggio(
        lato: Int,
        rotolo: NumberRoll,
        cifra: TextView,
        nuovo: String,
    ) {
        val verso =
            versoDelCambio?.takeIf { latoDelCambio == null || latoDelCambio == lato }
                ?: NumberRoll.direzione(cifra.text.toString(), nuovo)
        rotolo.mostra(nuovo, verso)
    }

    /**
     * Il "-" e' una correzione oppure un annullamento, a seconda dello sport: dove il punteggio
     * non e' un contatore (game, set) sottrarre un punto non e' un'operazione definita, quindi si
     * disfa l'ultima azione invece di inventarne l'inversa.
     */
    private fun decrementScore(team: Int) {
        if (capabilities?.decrementIsUndo == true) {
            coneIlVerso(NumberRoll.GIU, null) { viewModel.undoLastGoal() }
        } else {
            val prima = viewModel.scoreDisplay.value
            coneIlVerso(NumberRoll.GIU, team) { viewModel.subtractScore(team) }
            // A zero la correzione non fa niente, e la striscia non deve dire che e' successo.
            if (viewModel.scoreDisplay.value != prima) {
                val nome = if (team == 1) viewModel.team1Name.value else viewModel.team2Name.value
                mostraMessaggioInStriscia(getString(R.string.strip_msg_correction, nome ?: ""))
            }
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
            // Nel padel e nel tennis un punto si rimette con un altro tocco: ANNULLA e' un tocco
            // solo. Il calcio tiene il dialogo, perche' un gol puo' portare un marcatore.
            if (annullaChiedeConferma(capabilities)) {
                MaterialAlertDialogBuilder(this)
                    .setTitle(getString(R.string.undo_goal_title))
                    .setMessage(getString(R.string.undo_goal_message))
                    .setPositiveButton(getString(R.string.undo)) { _, _ -> annullaEMostra() }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            } else {
                annullaEMostra()
            }
        }
    }

    /**
     * Annulla l'ultima azione e ne da' il segno: il doppio tick dell'annullamento (lo stesso
     * schema condiviso con l'orologio, HapticFeedbackManager.PATTERN_UNDO) e «ANNULLATO: PUNTO
     * ROSSI» per 3 secondi nella striscia. Tutti e due solo se il motore ha tolto davvero
     * qualcosa: il tocco puo' cadere durante il ripristino (e si scarta), o non trovare che eventi
     * inerti. Un tocco entro mezzo secondo dal precedente si ignora ([toccoAnnullaRipetuto]).
     */
    private fun annullaEMostra() {
        val adesso = SystemClock.elapsedRealtime()
        if (toccoAnnullaRipetuto(adesso, ultimoToccoAnnulla)) return
        ultimoToccoAnnulla = adesso
        // Rimandarlo non serve: durante il ripristino a schermo non c'e' ancora niente da annullare,
        // e a ripristino finito toglierebbe un punto che chi tocca non ha mai visto, senza segnale.
        val tolto = coneIlVerso(NumberRoll.GIU, null) { viewModel.annullaUltimaAzione(rimandabile = false) } ?: return
        vibrator?.vibrate(VibrationEffect.createWaveform(HapticFeedbackManager.PATTERN_UNDO, NON_RIPETERE))
        mostraMessaggioInStriscia(
            testoDellAnnullamento(
                this,
                tolto,
                capabilities,
                viewModel.team1Name.value ?: "Team 1",
                viewModel.team2Name.value ?: "Team 2",
            ),
        )
    }

    private fun setupPlayerManagementButtons() {
        findViewById<Button>(R.id.add_team1_player_button).setOnClickListener {
            showAddPlayerToTeamDialog(1)
        }

        findViewById<Button>(R.id.add_team2_player_button).setOnClickListener {
            showAddPlayerToTeamDialog(2)
        }

        findViewById<Button>(R.id.team1_swap_button).setOnClickListener { viewModel.swapPlayers(1) }
        findViewById<Button>(R.id.team2_swap_button).setOnClickListener { viewModel.swapPlayers(2) }

        findViewById<Button>(R.id.serata_open_button).setOnClickListener { apriLaSerata() }

        findViewById<Button>(R.id.players_button).setOnClickListener {
            startActivity(Intent(this, PlayersManagementActivity::class.java))
        }
    }

    private fun setupNavigationButtons() {
        findViewById<Button>(R.id.match_history_button).setOnClickListener {
            startActivity(Intent(this, MatchHistoryActivity::class.java))
        }

        // Le impostazioni stanno solo nel foglio, accanto agli altri comandi che prima erano FAB.
        findViewById<Button>(R.id.sheet_settings_button).setOnClickListener {
            startActivity(Intent(this, MatchSettingsActivity::class.java))
        }

        findViewById<Button>(R.id.statistics_button).setOnClickListener {
            startActivity(Intent(this, StatisticsActivity::class.java))
        }

        findViewById<Button>(R.id.sheet_reset_timer_button).setOnClickListener { resetTimer(it) }
    }

    private fun animateTextChange(
        textView: TextView,
        newText: String,
    ) {
        // Dissolvenza breve (G-2): fuori e dentro in duration_fast in tutto, senza traslazione ne' rimbalzo.
        val meta = resources.getInteger(R.integer.duration_fast) / 2L
        textView
            .animate()
            .alpha(0f)
            .setDuration(meta)
            .withEndAction {
                textView.text = newText
                textView
                    .animate()
                    .alpha(1f)
                    .setDuration(meta)
                    .start()
            }.start()
    }

    /** Il file JSON della partita. Si raggiunge dalla stessa scelta di Condividi. */
    private fun exportMatchToPadel() {
        MatchExportUtils.shareExport(this, viewModel.buildExport(), viewModel.exportFileLabel())
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
     * A partita finita i due "+" si spengono, ma rispondono, e lo sconfitto si fa grigio.
     *
     * Prima restavano premibili e non facevano niente: il motore ignora un punto dopo la fine,
     * quindi il numero non cambiava e il tocco spariva nel vuoto. Poi si erano resi non cliccabili,
     * e il tocco restava muto lo stesso. Un comando che non risponde non si distingue da un'app
     * bloccata: la zona spenta (grigia, con la barra del colore in fondo) resta toccabile solo per
     * dichiararsi, con un colpo lungo e «PARTITA FINITA» nella striscia (vedi [toccaLaZona]).
     *
     * L'annullamento NON si spegne: e' esattamente cio' che serve se l'ultimo punto era sbagliato,
     * e riportarlo indietro riaccende tutto perche' lo stato torna "non finita".
     */
    private fun applyMatchOver(display: ScoreDisplay) {
        aggiornaZone()
        val bianco = ContextCompat.getColor(this, R.color.ink_white)
        val grigio = ContextCompat.getColor(this, R.color.elite_text_secondary)
        team1ScoreTextView.setTextColor(coloreDelNumero(1, display, bianco, grigio))
        team2ScoreTextView.setTextColor(coloreDelNumero(2, display, bianco, grigio))
    }

    private fun aggiornaZone() {
        viewModel.team1Color.value?.let { aggiornaZona(1, it) }
        viewModel.team2Color.value?.let { aggiornaZona(2, it) }
    }

    /**
     * L'icona dell'orologio: #E0E0E0 se collegato, #9E9E9E con il glifo barrato se no. Il badge rosso
     * resta finche' la notizia e' Rejected, cioe' fino alla partita nuova. Il badge e' il foreground
     * dell'icona, cosi' non prende il colore del tint e non occupa spazio nella barra. Toccarla apre
     * il foglio, dove sta la card con la spiegazione.
     */
    private fun aggiornaIconaOrologio() {
        val statusIcon = findViewById<ImageView>(R.id.wear_status_icon)
        val collegato = viewModel.isWearConnected.value == true
        val rifiutato = viewModel.watchNotice.value is WatchNotice.Rejected
        statusIcon.setImageResource(if (collegato) R.drawable.ic_watch else R.drawable.ic_watch_off)
        statusIcon.imageTintList =
            ColorStateList.valueOf(
                ContextCompat.getColor(this, if (collegato) R.color.elite_text_primary else R.color.elite_text_secondary),
            )
        statusIcon.foreground = if (rifiutato) ContextCompat.getDrawable(this, R.drawable.bg_watch_notice_badge) else null
        // Il tooltip si vede solo tenendo premuto, e chi usa TalkBack non lo incontra:
        // la contentDescription restava quella cablata nel layout, uguale nei due stati. Con un
        // arretrato rifiutato la descrizione dice anche quello, perche' il badge e' solo grafica.
        val stato = getString(if (collegato) R.string.wear_connected_tooltip else R.string.wear_disconnected_tooltip)
        statusIcon.contentDescription =
            if (rifiutato) getString(R.string.cd_wear_status_with_notice, stato, getString(R.string.watch_batch_rejected)) else stato
        statusIcon.tooltipText = stato
    }

    /** Una zona con il colore di squadra, accesa o spenta secondo che la partita sia finita. */
    private fun aggiornaZona(
        squadra: Int,
        colore: Int,
    ) {
        val prima = squadra == 1
        applicaStatoDellaZona(
            if (prima) team1Zone else team2Zone,
            findViewById(if (prima) R.id.team1_plus_icon else R.id.team2_plus_icon),
            findViewById(if (prima) R.id.team1_color_bar else R.id.team2_color_bar),
            findViewById(if (prima) R.id.team1_zone_bar else R.id.team2_zone_bar),
            colore,
            viewModel.scoreDisplay.value?.matchOver == true,
        )
    }

    /**
     * Le descrizioni per TalkBack, con il nome della squadra e il punteggio di adesso.
     *
     * La zona dice cosa fa il tocco e a che punto siamo ("Punto a ROSSI. 30 a 15"), il numero dice
     * di chi e' ("ROSSI, 30"), il -1 dice a chi toglie. Prima erano stringhe fisse: "aumenta
     * squadra 1" non dice niente a chi non vede il tabellone. A partita finita la zona lo dichiara.
     * Il contenitore del nome ha la sua descrizione, scritta da [mostraNomeSquadra].
     */
    private fun aggiornaDescrizioni() {
        val display = viewModel.scoreDisplay.value ?: return
        val gol = capabilities?.attributesScorer != false
        val lati =
            listOf(
                Triple(viewModel.team1Name.value.orEmpty(), display.side1Primary, display.side2Primary),
                Triple(viewModel.team2Name.value.orEmpty(), display.side2Primary, display.side1Primary),
            )
        val zone = listOf(team1Zone, team2Zone)
        val numeri = listOf(team1ScoreTextView, team2ScoreTextView)
        val meno = listOf(R.id.team1_subtract_button_card, R.id.team2_subtract_button_card)
        lati.forEachIndexed { i, (nome, proprio, altrui) ->
            zone[i].contentDescription =
                if (display.matchOver) {
                    getString(R.string.cd_match_over)
                } else {
                    getString(if (gol) R.string.cd_add_goal else R.string.cd_add_point, nome, proprio, altrui)
                }
            numeri[i].contentDescription = getString(R.string.cd_score_of_team, nome, proprio)
            findViewById<View>(meno[i]).contentDescription = getString(R.string.cd_correct_team, nome)
        }
    }

    /**
     * Che periodo si gioca e chi serve.
     *
     * L'orologio lo mostrava e il telefono no, pur avendo gli stessi dati: [ScoreDisplay] porta
     * periodLabel e servingSide, e il telefono li spediva al polso per poi buttarne via meta'.
     * In un padel al meglio di tre, "che set stiamo giocando" serve a chi guarda il tabellone
     * almeno quanto il punteggio. Va nello spazio lasciato libero dal cronometro spento. Il testo
     * cambia, la vista no: resta sempre in barra (vuota, nel calcio) e il pallino del servizio
     * accanto al nome e' INVISIBLE e non GONE, cosi' niente cambia misura a partita in corso.
     */
    private fun bindPeriod(display: ScoreDisplay) {
        val disponibile = matchPeriodTextView.width - matchPeriodTextView.compoundPaddingLeft - matchPeriodTextView.compoundPaddingRight
        matchPeriodTextView.text =
            testoDellaBarra(
                this,
                viewModel.activeSport.value ?: SportRegistry.FOOTBALL,
                display,
                viewModel.team1Name.value.orEmpty(),
                viewModel.team2Name.value.orEmpty(),
                // Non ancora misurata: niente da accorciare, ci pensa il listener del layout.
            ) { candidato -> disponibile <= 0 || matchPeriodTextView.paint.measureText(candidato) <= disponibile }
        mostraIlServizio(
            findViewById<View>(android.R.id.content),
            display,
            viewModel.team1Name.value.orEmpty(),
            viewModel.team2Name.value.orEmpty(),
        )
    }

    /**
     * Il dettaglio sotto i numeri, negli sport a set: game o set e game, letto dalla parte della
     * squadra di sinistra come tutta la schermata. Un solo valore al posto dei due, uno per card,
     * ognuno dal proprio punto di vista (3-2 a sinistra, 2-3 a destra): chi guarda da lontano non
     * deve capire da che lato e' scritto. La didascalia dice cosa sono i numeri: a partita finita
     * non ci sono game correnti e il dettaglio contiene solo i set chiusi, quindi e' SET.
     */
    private fun bindScoreDetail(
        testo: String?,
        partitaFinita: Boolean,
    ) {
        scoreDetailValue.text = testo.orEmpty()
        scoreDetailCaption.text = didascaliaDelDettaglio(testo, partitaFinita)?.let { getString(it) }.orEmpty()
    }

    /**
     * Un colpo breve di sistema, non piu' la sequenza da 500ms che partiva a ogni + in tutti gli
     * sport: nel padel, con un punto ogni pochi secondi, durava quasi quanto lo scambio di tocchi.
     * Un game chiuso o la fine della partita lo sostituiscono con un colpo piu' deciso (doppio o
     * pesante): una vibrazione nuova interrompe quella in corso, quindi ne parte una sola.
     */
    private fun playGoalVibrationPattern(riscontro: RiscontroDelPunto) {
        val effetto =
            when (riscontro) {
                RiscontroDelPunto.NESSUNO -> VibrationEffect.EFFECT_CLICK
                RiscontroDelPunto.GAME_CHIUSO -> VibrationEffect.EFFECT_DOUBLE_CLICK
                RiscontroDelPunto.PARTITA_FINITA -> VibrationEffect.EFFECT_HEAVY_CLICK
            }
        vibrator?.vibrate(VibrationEffect.createPredefined(effetto))
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
            snackbarSopraLaStriscia(getString(R.string.no_available_players), Snackbar.LENGTH_LONG)
                .setAction(getString(R.string.manage)) {
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
                snackbarSopraLaStriscia(
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
                snackbarSopraLaStriscia(
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
        // Il messaggio solo se l'attribuzione e' avvenuta: il punto puo' essere sparito nel frattempo.
        if (viewModel.attributeScorer(engineIndex, playerWithRoles)) {
            mostraMessaggioInStriscia(
                getString(R.string.strip_msg_goal_by, playerWithRoles.player.playerName),
            )
        }
    }

    /**
     * Il dialogo di TERMINA. Il messaggio viene dal display (vedi [testoDelDialogoDiFine]): a
     * partita chiusa e' PARTITA FINITA con chi ha vinto e il positivo dice SALVA. SCARTA e' in
     * #FF6E6E, solo qui: e' il comando che butta via la partita.
     */
    private fun showEndMatchConfirmation() {
        val team1Name = viewModel.team1Name.value ?: "Team 1"
        val team2Name = viewModel.team2Name.value ?: "Team 2"
        val display = viewModel.scoreDisplay.value ?: return
        val sport = viewModel.activeSport.value ?: SportRegistry.FOOTBALL
        val testo = testoDelDialogoDiFine(this, sport, display, team1Name, team2Name, modalitaAGame(sport))

        // "Invia anche a Padel Elite": solo con la funzione configurata, nel padel e con un file
        // esportabile. L'identificativo si prende ORA: la chiusura azzera la partita in corso.
        val padelElite = (application as ScoreboardEssentialApplication).padelElite
        val uuidDaInviare = viewModel.currentMatchUuid
        val inviaBox =
            if (padelElite.isEnabled && sport == SportRegistry.PADEL && uuidDaInviare != null &&
                viewModel.buildExport() is ExportResult.Ready
            ) {
                MaterialCheckBox(this).apply {
                    setText(R.string.padel_elite_send_in_dialog)
                    minHeight = resources.getDimensionPixelSize(R.dimen.control_touch)
                }
            } else {
                null
            }

        // "Prossima partita della serata": con una serata in corso e acceso di default; dopo il salvataggio
        // apre la Serata, che ha gia' proposto le coppie successive.
        val serataBox =
            if (viewModel.sportDellaSerata() && viewModel.serataInCorso()?.inGioco != null) {
                MaterialCheckBox(this).apply {
                    setText(R.string.serata_next_in_dialog)
                    isChecked = true
                    minHeight = resources.getDimensionPixelSize(R.dimen.control_touch)
                }
            } else {
                null
            }

        val dialogo =
            MaterialAlertDialogBuilder(this)
                .setTitle(testo.titolo)
                .setMessage(testo.messaggio)
                .apply {
                    val caselle = listOfNotNull(inviaBox, serataBox)
                    if (caselle.isNotEmpty()) {
                        val margine = resources.getDimensionPixelSize(R.dimen.space_24)
                        setView(
                            android.widget.LinearLayout(this@MainActivity).apply {
                                orientation = android.widget.LinearLayout.VERTICAL
                                setPadding(margine, 0, margine, 0)
                                caselle.forEach { addView(it) }
                            },
                        )
                    }
                }.setPositiveButton(getString(if (testo.salva) R.string.btn_save_match else R.string.btn_end_match)) { _, _ ->
                    if (viewModel.endMatch()) {
                        snackbarSopraLaStriscia(getString(R.string.match_saved), Snackbar.LENGTH_LONG).show()
                        if (inviaBox?.isChecked == true && uuidDaInviare != null) padelElite.sendOrOpenLogin(this, uuidDaInviare)
                        if (serataBox?.isChecked == true) apriLaSerata()
                    } else {
                        snackbarSopraLaStriscia(getString(R.string.match_not_started_error), Snackbar.LENGTH_LONG).show()
                    }
                }.setNeutralButton(getString(R.string.btn_discard_match)) { _, _ ->
                    // "Termina" salva, "scarta" butta via: due intenzioni diverse, due comandi
                    // diversi. Prima esisteva solo la prima, quindi una partita cominciata per
                    // sbaglio poteva solo finire nello storico.
                    val messaggio =
                        if (viewModel.discardMatch()) R.string.match_discarded else R.string.match_not_started_error
                    snackbarSopraLaStriscia(getString(messaggio), Snackbar.LENGTH_LONG).show()
                }.setNegativeButton(getString(R.string.continue_action), null)
                .show()
        // Un solo primario nel dialogo: SALVA e' lime, SCARTA e CONTINUA sono testo primario (la parola dice il resto).
        dialogo.getButton(DialogInterface.BUTTON_NEUTRAL).setTextColor(ContextCompat.getColor(this, R.color.elite_text_primary))
        dialogo.getButton(DialogInterface.BUTTON_NEGATIVE).setTextColor(ContextCompat.getColor(this, R.color.elite_text_primary))
    }

    // Il tempo sta nel testo del pulsante della barra: e' il pulsante a dire se corre, col glifo.
    private fun updateTimerTextView(timeInMillis: Long) {
        timerStartButton.text = TimeUtils.formatTime(timeInMillis)
    }

    /**
     * Il valore dello slot del portiere. Lo slot e' sempre in barra nel calcio (lo decide
     * applyCapabilities): prima la riga spariva a 0 e sotto tutto saltava proprio alla scadenza.
     * Fermo o a 00:00 e' grigio (testo secondario), in corso e' testo primario: il lime marca solo chi serve
     * (G-9: la stessa scelta del polso, dove il conto che corre e' testo primario).
     */
    private fun updateKeeperTimerTextView(timeInMillis: Long) {
        val conto = TimeUtils.formatTime(timeInMillis)
        val inCorso = timeInMillis > 0 && viewModel.isKeeperTimerRunning.value == true
        val stato = statoDelPortiere(inCorso, viewModel.isKeeperTimerExpired.value == true)
        val scaduto = stato == StatoPortiere.SCADUTO
        // SCADUTO: slot pieno elite_error con «CAMBIO» in elite_background (5,08:1). L'errore e' un riempimento e
        // lo sfondo l'inchiostro, come nelle zone +; il conto in corso e' testo primario.
        val nero = ContextCompat.getColor(this, R.color.elite_background)
        val rosso = ContextCompat.getColor(this, R.color.elite_error)
        keeperSlot.setCardBackgroundColor(if (scaduto) rosso else Color.TRANSPARENT)
        keeperSlot.strokeColor = if (scaduto) rosso else ContextCompat.getColor(this, R.color.elite_outline)
        keeperTimerLabel.setTextColor(if (scaduto) nero else ContextCompat.getColor(this, R.color.elite_text_secondary))
        keeperTimerTextView.text = if (scaduto) getString(R.string.label_keeper_change) else conto
        keeperTimerTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, if (scaduto) dimensioneDelCambio() else dimensioneDelConto)
        keeperTimerTextView.setTextColor(
            when {
                scaduto -> nero
                inCorso -> ContextCompat.getColor(this, R.color.elite_text_primary)
                else -> ContextCompat.getColor(this, R.color.elite_text_secondary)
            },
        )
        keeperSlot.contentDescription = descrizioneDelPortiere(this, stato, conto)
    }

    /**
     * La dimensione di «CAMBIO»: quella del conto, ridotta quanto basta perche' la parola non sia
     * piu' larga di «00:00». Lo slot e' largo quanto il suo testo piu' largo, e per tutta la partita
     * non deve cambiare misura: se la parola fosse piu' larga del conto, alla scadenza la barra si
     * stringerebbe e lo spazio lasciato libero sposterebbe i comandi accanto (laZonaPiuNonSiSposta).
     */
    private fun dimensioneDelCambio(): Float {
        val larghezzaDelConto = larghezzaDelContoPiuLargo()
        val larghezzaDellaParola = misuraDelConto().measureText(getString(R.string.label_keeper_change))
        if (larghezzaDellaParola <= larghezzaDelConto) return dimensioneDelConto
        return dimensioneDelConto * larghezzaDelConto / larghezzaDellaParola
    }

    private fun misuraDelConto() = Paint(keeperTimerTextView.paint).apply { textSize = dimensioneDelConto }

    /**
     * Il conto piu' largo, «88:88» fra le coppie di cifre uguali: le cifre del carattere non sono
     * tutte larghe uguale (due pixel di differenza fra «02:00» e «00:00» sono bastati a far muovere
     * lo slot). Il valore ha questa come larghezza minima, cosi' lo slot misura sempre lo stesso,
     * qualunque sia il conto o CAMBIO.
     */
    private fun larghezzaDelContoPiuLargo(): Float {
        val misura = misuraDelConto()
        return ('0'..'9').maxOf { cifra -> misura.measureText("$cifra$cifra:$cifra$cifra") }
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

/**
 * Il colore della squadra sulla zona + e sulla barretta sotto il nome, e MAI dietro un testo.
 *
 * La zona prende il colore vero e il glifo l'inchiostro [TeamInk] (nero o bianco puro, almeno
 * 4,58:1 con qualsiasi colore). Se il colore sul nero sta sotto 3:1 (un blu notte come #1A237E fa
 * 1,59) la zona si distinguerebbe male dallo sfondo e prende lo stroke da 2dp; la barretta
 * sottile invece non ha spazio per un contorno e usa [TeamInk.graphicOnBlack]. Sta fuori
 * dall'Activity perche' sotto Robolectric MainActivity non si monta.
 */
internal fun applicaColoreDiSquadra(
    zona: MaterialCardView,
    glifo: ImageView,
    barretta: View,
    colore: Int,
) {
    val inchiostro = TeamInk.on(colore)
    zona.setCardBackgroundColor(colore)
    // Nessuna increspatura: il riscontro del tocco e' la pressione condivisa (scala 0,97 e opacita' 0,85).
    zona.rippleColor = ColorStateList.valueOf(Color.TRANSPARENT)
    zona.strokeColor = ContextCompat.getColor(zona.context, R.color.elite_text_primary)
    zona.strokeWidth =
        if (TeamInk.contrast(colore, TeamInk.NERO) < CONTRASTO_MINIMO_GRAFICA) {
            (STROKE_ZONA_DP * zona.resources.displayMetrics.density).toInt()
        } else {
            0
        }
    glifo.imageTintList = ColorStateList.valueOf(inchiostro)
    barretta.setBackgroundColor(TeamInk.graphicOnBlack(colore))
}

/**
 * La zona + in gioco o, a partita finita, spenta.
 *
 * In gioco e' [applicaColoreDiSquadra] e basta: colore vero, glifo in inchiostro, nessuna barra.
 * Spenta (DESIGN.md, "Gioco: partita finita") la zona diventa #2C2C2C, il glifo sparisce e in fondo
 * compare una barra di 4dp nel colore della squadra, cosi' si vede ancora di chi e'. Resta
 * toccabile: risponde con un messaggio, non con un punto (vedi toccaLaZona). Nessuna misura cambia:
 * la barra e' sempre nel layout, con alfa 0 durante il gioco. Sta fuori dall'Activity perche' sotto
 * Robolectric MainActivity non si monta.
 */
internal fun applicaStatoDellaZona(
    zona: MaterialCardView,
    glifo: ImageView,
    barretta: View,
    barraDellaZona: View,
    colore: Int,
    finita: Boolean,
) {
    applicaColoreDiSquadra(zona, glifo, barretta, colore)
    glifo.alpha = if (finita) 0f else 1f
    barraDellaZona.alpha = if (finita) 1f else 0f
    if (!finita) return
    // Spenta: superficie rialzata con la sua linea (la zona resta una forma sul nero), senza ripple.
    val rialzata = ContextCompat.getColor(zona.context, R.color.elite_surface_raised)
    zona.setCardBackgroundColor(rialzata)
    zona.strokeColor = ContextCompat.getColor(zona.context, R.color.elite_border_strong)
    zona.strokeWidth = zona.resources.getDimensionPixelSize(R.dimen.border_width)
    zona.rippleColor = ColorStateList.valueOf(Color.TRANSPARENT)
    // La barra sta sulla zona grigia, non sul nero: il 3:1 si misura contro la zona spenta.
    barraDellaZona.setBackgroundColor(TeamInk.graphicOn(colore, rialzata))
}

/**
 * Scrive il nome sulla card e lo mette anche nella descrizione del contenitore cliccabile.
 *
 * Il contenitore aveva la descrizione fissa "Modifica nome Squadra 1": TalkBack legge quella e non
 * il testo dentro, cosi' il nome della squadra non si sentiva mai. Sta fuori dall'Activity perche'
 * sotto Robolectric MainActivity non si monta.
 */
internal fun mostraNomeSquadra(
    contenitore: View,
    testo: TextView,
    nome: String,
) {
    testo.text = nome
    contenitore.contentDescription = contenitore.context.getString(R.string.cd_edit_team_name, nome)
}

/**
 * I pallini del servizio: uno se batte il primo giocatore della squadra, due se batte il secondo.
 *
 * Niente nomi e niente numeri a schermo: la barra dice gia' chi serve, qui si dice quale dei due.
 * Il singolare ha un solo giocatore per lato, quindi un pallino. Lo slot ha sempre la larghezza
 * di due pallini e quelli spenti sono INVISIBLE e non GONE, cosi' nulla si sposta. Sta fuori
 * dall'Activity perche' sotto Robolectric MainActivity non si monta.
 *
 * TalkBack non vede i pallini uno a uno: lo slot ha una descrizione sola, e solo mentre la
 * squadra serve, che dice chi batte.
 */
internal fun mostraIlServizio(
    radice: View,
    display: ScoreDisplay,
    nome1: String,
    nome2: String,
) {
    val lati =
        listOf(
            listOf(R.id.team1_name_container, R.id.team1_serve_slot, R.id.team1_serve_dot, R.id.team1_serve_dot_second),
            listOf(R.id.team2_name_container, R.id.team2_serve_slot, R.id.team2_serve_dot, R.id.team2_serve_dot_second),
        )
    lati.forEachIndexed { indice, ids ->
        val nome = if (indice == 0) nome1 else nome2
        val serve = display.servingSide == indice + 1
        val slot = radice.findViewById<View>(ids[1])
        radice.findViewById<View>(ids[2]).visibility = if (serve) View.VISIBLE else View.INVISIBLE
        radice.findViewById<View>(ids[3]).visibility =
            if (serve && display.servingPlayerSlot == 2) View.VISIBLE else View.INVISIBLE
        val frase =
            when {
                !serve -> null
                display.servingPlayerSlot == 1 -> R.string.cd_serving_first_player
                display.servingPlayerSlot == 2 -> R.string.cd_serving_second_player
                else -> R.string.cd_serving
            }?.let { slot.context.getString(it, nome) }
        slot.contentDescription = frase
        slot.importantForAccessibility =
            if (frase == null) View.IMPORTANT_FOR_ACCESSIBILITY_NO else View.IMPORTANT_FOR_ACCESSIBILITY_YES
        // Il contenitore del nome e' un bersaglio solo: TalkBack legge la SUA descrizione e non
        // quella dei figli, quindi chi serve va detto anche li'.
        val modifica = slot.context.getString(R.string.cd_edit_team_name, nome)
        radice.findViewById<View>(ids[0]).contentDescription = if (frase == null) modifica else "$modifica. $frase"
    }
}
