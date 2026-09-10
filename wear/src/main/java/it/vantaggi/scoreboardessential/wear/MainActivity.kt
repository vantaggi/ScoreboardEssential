package it.vantaggi.scoreboardessential.wear

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.Wearable
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import it.vantaggi.scoreboardessential.wear.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var binding: ActivityMainBinding
    private val viewModel: WearViewModel by viewModels()

    private var stateRestored = false

    private val broadcastReceiver =
        object : android.content.BroadcastReceiver() {
            override fun onReceive(
                context: android.content.Context,
                intent: android.content.Intent,
            ) {
                when (intent.action) {
                    WearDataLayerService.ACTION_STATE_V2_UPDATE -> {
                        val payload = intent.getByteArrayExtra(WearDataLayerService.EXTRA_V2_PAYLOAD) ?: return
                        viewModel.applyStateV2(WearScoreState.fromDataMap(DataMap.fromByteArray(payload)))
                    }

                    WearDataLayerService.ACTION_SCORE_UPDATE -> {
                        val team1 = intent.getIntExtra(WearDataLayerService.EXTRA_TEAM1_SCORE, 0)
                        val team2 = intent.getIntExtra(WearDataLayerService.EXTRA_TEAM2_SCORE, 0)
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
                        val isRunning = intent.getBooleanExtra(WearDataLayerService.EXTRA_TIMER_RUNNING, false)
                        viewModel.syncMatchTimer(millis, isRunning)
                    }

                    WearDataLayerService.ACTION_KEEPER_TIMER_UPDATE -> {
                        val millis = intent.getLongExtra(WearDataLayerService.EXTRA_KEEPER_MILLIS, 0L)
                        val isRunning = intent.getBooleanExtra(WearDataLayerService.EXTRA_KEEPER_RUNNING, false)
                        if (isRunning) {
                            viewModel.setKeeperTimerState(KeeperTimerState.Running((millis / 1000).toInt()))
                            if (millis > 0) viewModel.updateKeeperTimerDuration(millis)
                        } else {
                            if (millis > 0) {
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

                    WearDataLayerService.ACTION_PLAYERS_UPDATE -> {
                        val raw = intent.getStringExtra(WearDataLayerService.EXTRA_PLAYERS)
                        viewModel.setAllPlayers(
                            it.vantaggi.scoreboardessential.shared.PlayerData
                                .decodeList(raw),
                        )
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        applyGestureLabels(decrementIsUndo = false)
        observeViewModel()

        val filter =
            android.content.IntentFilter().apply {
                addAction(WearDataLayerService.ACTION_STATE_V2_UPDATE)
                addAction(WearDataLayerService.ACTION_SCORE_UPDATE)
                addAction(WearDataLayerService.ACTION_TEAM_NAMES_UPDATE)
                addAction(WearDataLayerService.ACTION_TEAM_COLOR_UPDATE)
                addAction(WearDataLayerService.ACTION_TIMER_UPDATE)
                addAction(WearDataLayerService.ACTION_KEEPER_TIMER_UPDATE)
                addAction(WearDataLayerService.ACTION_MATCH_STATE_UPDATE)
                addAction(WearDataLayerService.ACTION_PLAYERS_UPDATE)
            }
        androidx.localbroadcastmanager.content.LocalBroadcastManager
            .getInstance(this)
            .registerReceiver(broadcastReceiver, filter)
    }

    override fun onResume() {
        super.onResume()
        restoreStateFromDataItems()
    }

    /**
     * Il Data Layer consegna un DataItem solo quando CAMBIA: tutto cio' che e' arrivato mentre
     * questa schermata non esisteva e' stato trasmesso a nessuno, perche' il service lo ha
     * ritrasmesso in broadcast senza che ci fosse un ricevitore. E' il difetto per cui aprendo
     * l'orologio a meta' partita si vedeva 0-0.
     *
     * Sta qui e non nel ViewModel perche' e' un fatto del ciclo di vita della schermata: il
     * ViewModel sopravvive e non viene ricreato al risveglio, quindi un blocco init non basterebbe.
     *
     * Una volta sola per istanza: da quel momento il service consegna gli aggiornamenti vivi, e
     * rigiocare un DataItem vecchio riporterebbe il cronometro indietro all'ultimo valore scritto
     * dal telefono invece di lasciarlo correre.
     */
    private fun restoreStateFromDataItems() {
        if (stateRestored) return
        stateRestored = true
        Wearable
            .getDataClient(this)
            .dataItems
            .addOnSuccessListener { buffer ->
                try {
                    buffer
                        // Prima il v2: da li' in poi il ViewModel scarta da solo il punteggio v1.
                        .sortedBy { if (it.uri.path == WearConstants.PATH_STATE_V2) 0 else 1 }
                        // Il countdown del portiere non porta con se' l'istante di partenza:
                        // rigiocarlo farebbe ripartire da capo un conto alla rovescia gia' finito,
                        // vibrazione compresa.
                        .filter { it.uri.path != WearConstants.PATH_KEEPER_TIMER }
                        .forEach { WearDataLayerService.dispatchDataItem(this, it) }
                } finally {
                    buffer.release()
                }
            }.addOnFailureListener { e ->
                Log.w(TAG, "Rilettura dei DataItem al risveglio fallita", e)
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        androidx.localbroadcastmanager.content.LocalBroadcastManager
            .getInstance(this)
            .unregisterReceiver(broadcastReceiver)
    }

    private fun setupClickListeners() {
        // Un tocco aggiunge, un tocco lungo toglie -- su TUTTO il lato.
        //
        // Prima la meta' alta del lato aggiungeva e la meta' bassa toglieva, senza che niente sullo
        // schermo mostrasse quella divisione: due FrameLayout vuoti, nessuna linea, nessuna
        // etichetta. Un gesto che toglie punti non puo' stare nascosto meta' schermo: chi teneva
        // premuto per sbaglio un po' piu' in basso vedeva sparire un punto e non sapeva perche'.
        // Ora la divisione non esiste piu', e cosa fa il tocco lungo lo dice gestureHint.
        binding.team1Container.setOnClickListener { viewModel.incrementScore(1) }
        binding.team2Container.setOnClickListener { viewModel.incrementScore(2) }

        binding.team1Container.setOnLongClickListener {
            viewModel.decrementScore(1)
            true
        }

        binding.team2Container.setOnLongClickListener {
            viewModel.decrementScore(2)
            true
        }

        // Timer controls
        binding.matchTimer.setOnClickListener {
            // Senza cronometro quella riga mostra il periodo ("Set 2"): non e' piu' un comando.
            if (viewModel.scoreState.value?.hasClock != false) {
                viewModel.toggleTimer()
            }
        }

        binding.keeperTimer.setOnClickListener {
            viewModel.toggleKeeperTimer()
        }

        binding.btnStartNewMatch.setOnClickListener {
            android.app.AlertDialog
                .Builder(this)
                .setTitle("Reset Match")
                .setMessage("Are you sure you want to end this match? This will reset all scores and timers.")
                .setPositiveButton("Reset") { _, _ ->
                    viewModel.resetMatch()
                }.setNegativeButton("Cancel", null)
                .show()
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

    /**
     * L'orologio non calcola: mette a schermo le stringhe che il telefono ha gia' impaginato, e
     * toglie i comandi che questo sport non ha. Con le capacita' del calcio non cambia nulla.
     */
    private fun renderScoreState(state: WearScoreState) {
        binding.team1Score.text = state.side1Primary
        binding.team2Score.text = state.side2Primary
        bindDetail(binding.team1ScoreDetail, state.side1Secondary)
        bindDetail(binding.team2ScoreDetail, state.side2Secondary)
        applyGestureLabels(state.decrementIsUndo)

        // Senza cronometro quel posto in alto e' libero: il periodo non avrebbe dove stare.
        if (!state.hasClock) {
            binding.matchTimer.text = state.periodLabel
        }

        if (!state.hasAuxTimer) {
            binding.keeperTimer.visibility = View.GONE
            binding.keeperProgressBar.visibility = View.INVISIBLE
        }
    }

    /**
     * La riga dei set ha una vista propria, e rimpicciolisce solo se stessa.
     *
     * Prima primario e secondario stavano nella STESSA TextView autoSize, separati da uno span al
     * 40%: per far entrare "6-4 3-6 2-1" su due righe l'autoSize rimpiccioliva tutto, numero
     * grande compreso. Nel padel il punteggio corrente -- l'unica cosa che si guarda mentre si
     * gioca -- finiva piccolo quanto la sua cronologia. Ora il numero tiene la sua altezza e a
     * stringersi e' la riga dei set.
     */
    private fun bindDetail(
        view: android.widget.TextView,
        text: String,
    ) {
        view.text = text
        view.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    /**
     * Dice a parole che cosa fa il tocco lungo, e lo dice in modo diverso nei due casi.
     *
     * decrementIsUndo viveva solo dentro il ViewModel: sullo schermo dell'orologio non c'era una
     * riga, un'icona o una descrizione che distinguesse "togli un punto a questa squadra" da
     * "annulla l'ultima azione". Sono due cose diverse e ora si leggono.
     */
    private fun applyGestureLabels(decrementIsUndo: Boolean) {
        binding.gestureHint.setText(if (decrementIsUndo) R.string.wear_hint_undo else R.string.wear_hint_minus)
        val descrizione = if (decrementIsUndo) R.string.cd_score_side_undo else R.string.cd_score_side_minus
        binding.team1Container.contentDescription = getString(descrizione, 1)
        binding.team2Container.contentDescription = getString(descrizione, 2)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe the pre-formatted v2 state (null until the phone speaks v2)
                launch {
                    // collect su uno StateFlow riemette subito il valore corrente, quindi questo
                    // ridisegna lo stato v2 a ogni ritorno in STARTED: e' il rimedio al risveglio.
                    viewModel.scoreState.collect { state ->
                        state?.let { renderScoreState(it) }
                    }
                }

                // I collector v1 scrivono solo finche' il v2 non e' mai arrivato.
                //
                // Sono StateFlow: riemettono l'ultimo valore ogni volta che si ricomincia a
                // raccogliere, e repeatOnLifecycle(STARTED) ricomincia a OGNI riaccensione dello
                // schermo. Senza questa guardia, in padel il punteggio a schermo tornava a 0-0 ogni
                // volta che si alzava il polso -- perche' nel v2 team1Score resta fermo a zero per
                // costruzione -- fino al punto successivo. Cioe' proprio nel momento per cui
                // l'orologio esiste.
                launch {
                    viewModel.team1Score.collect { score ->
                        if (viewModel.scoreState.value == null) binding.team1Score.text = score.toString()
                    }
                }

                launch {
                    viewModel.team2Score.collect { score ->
                        if (viewModel.scoreState.value == null) binding.team2Score.text = score.toString()
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

                // Stessa ragione: senza cronometro il v2 non manda un tempo, e il valore iniziale
                // "00:00" del flow v1 tornerebbe a schermo a ogni riaccensione.
                launch {
                    viewModel.matchTimer.collect { time ->
                        if (viewModel.scoreState.value == null) binding.matchTimer.text = time
                    }
                }

                // Observe Keeper Timer
                launch {
                    viewModel.keeperTimer.collect { state ->
                        // SEMPRE VISIBILE, finche' lo sport ha davvero un timer ausiliario.
                        val auxAvailable = viewModel.scoreState.value?.hasAuxTimer != false
                        binding.keeperTimer.visibility = if (auxAvailable) View.VISIBLE else View.GONE
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
                        teamNumber?.let { team ->
                            val intent = Intent(this@MainActivity, PlayerSelectionActivity::class.java)
                            intent.putExtra(WearConstants.EXTRA_TEAM_NUMBER, team)
                            intent.putExtra(
                                WearDataLayerService.EXTRA_PLAYERS,
                                it.vantaggi.scoreboardessential.shared.PlayerData
                                    .encodeList(viewModel.allPlayers.value),
                            )
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
