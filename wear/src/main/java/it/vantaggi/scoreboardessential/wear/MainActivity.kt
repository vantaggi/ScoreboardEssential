package it.vantaggi.scoreboardessential.wear

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
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

    /**
     * La scelta dello sport torna qui, e da qui parte la richiesta al telefono: il numero di
     * sequenza e' uno solo per nodo e vive nel ViewModel di questa schermata.
     */
    private val sceltaSport =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { esito ->
            val sportId = esito.data?.getStringExtra(SportSelectionActivity.EXTRA_CHOSEN)
            if (esito.resultCode == android.app.Activity.RESULT_OK && !sportId.isNullOrBlank()) {
                viewModel.requestSport(sportId)
            }
        }

    // Cosa dice la riga in basso dipende da DUE cose: lo sport e il collegamento.
    private var annullamentoGlobale = false
    private var telefonoRaggiungibile = true
    private var daConsegnare = 0
    private var partitaFinita = false
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

                    WearDataLayerService.ACTION_BATCH_ACK -> {
                        viewModel.onBatchAck(intent.getLongExtra(WearConstants.KEY_SEQ, 0L))
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
        viewModel.refreshPendingCount()
        observeViewModel()

        val filter =
            android.content.IntentFilter().apply {
                addAction(WearDataLayerService.ACTION_STATE_V2_UPDATE)
                addAction(WearDataLayerService.ACTION_BATCH_ACK)
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
        // Al risveglio il pallino deve dire com'e' il collegamento ADESSO, non com'era all'avvio.
        viewModel.refreshConnection()
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

        binding.btnSport.setOnClickListener {
            val stato = viewModel.scoreState.value ?: return@setOnClickListener
            if (stato.matchInProgress) {
                // Il telefono rifiuterebbe comunque: dirlo QUI evita di far partire una richiesta
                // che si sa gia' come finisce, e di far aspettare una risposta che non cambia niente.
                android.widget.Toast
                    .makeText(this, R.string.wear_sport_locked, android.widget.Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            sceltaSport.launch(
                Intent(this, SportSelectionActivity::class.java).apply {
                    putExtra(SportSelectionActivity.EXTRA_IDS, ArrayList(stato.sportIds))
                    putExtra(SportSelectionActivity.EXTRA_LABELS, ArrayList(stato.sportLabels))
                    putExtra(SportSelectionActivity.EXTRA_CURRENT, stato.sportLabel)
                },
            )
        }

        binding.btnStartNewMatch.setOnClickListener {
            android.app.AlertDialog
                .Builder(this)
                .setTitle(R.string.wear_reset_title)
                .setMessage(R.string.wear_reset_message)
                .setPositiveButton(R.string.wear_reset_confirm) { _, _ ->
                    viewModel.resetMatch()
                }.setNegativeButton(R.string.wear_reset_cancel, null)
                .show()
        }
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
        // Senza elenco non c'e' niente da scegliere: succede con un telefono che parla una bozza
        // precedente del v2. Il comando non compare invece di aprire una lista vuota.
        binding.btnSport.visibility = if (state.sportIds.size > 1) View.VISIBLE else View.GONE
        applyMatchOver(state.matchOver)

        applyClockRole(state)

        applyAuxTimerRole(state)
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
        annullamentoGlobale = decrementIsUndo
        describeSides()
        refreshHint()
    }

    /**
     * Il lato e' un bersaglio unico, quindi TalkBack legge la SUA descrizione al posto delle cifre
     * che contiene. Quando era fissa, sul 30-15 il focus diceva "Squadra 1. Tocca per segnare..."
     * senza nessun numero: chi non guarda riceveva tutto tranne il punteggio.
     *
     * Le cifre si leggono dalla vista, non dallo stato: cosi' vale uguale per il v2 e per il v1, e
     * la descrizione non puo' dire un numero diverso da quello a schermo. Va richiamata a ogni
     * cambio di punteggio, di nome e di gesto.
     */
    private fun describeSides() {
        val descrizione = if (annullamentoGlobale) R.string.cd_score_side_undo else R.string.cd_score_side_minus
        binding.team1Container.contentDescription =
            getString(descrizione, teamName(viewModel.team1Name.value, 1), binding.team1Score.text.toString())
        binding.team2Container.contentDescription =
            getString(descrizione, teamName(viewModel.team2Name.value, 2), binding.team2Score.text.toString())
    }

    /** Il nome scelto sul telefono, lo stesso che legge TalkBack la'; "Squadra 1" finche' non arriva. */
    private fun teamName(
        nome: String,
        lato: Int,
    ): String = nome.ifBlank { getString(R.string.cd_team_fallback, lato) }

    /**
     * Quando il telefono non risponde, il tocco non fa NIENTE.
     *
     * Prima l'unica differenza fra collegato e non collegato era il colore di un punto da 8dp:
     * chi non distingue il rosso dal verde, o semplicemente non guarda in cima, continuava a
     * segnare su un tabellone fermo. Ora il punto e' piu' grande, ha una descrizione parlata, e
     * soprattutto la riga in basso smette di spiegare un gesto che in quel momento non funziona.
     */
    private fun applyConnectionState(connesso: Boolean) {
        telefonoRaggiungibile = connesso
        // Il telefono e' tornato: cio' che si e' segnato senza di lui parte adesso, da solo.
        if (connesso) viewModel.flushPending()
        val colore = if (connesso) R.color.team_electric_green else R.color.error_red
        binding.connectionStatusIndicator.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, colore))
        binding.connectionStatusIndicator.contentDescription =
            getString(if (connesso) R.string.cd_connection_ok else R.string.cd_connection_lost)
        refreshHint()
    }

    /**
     * Il comando del portiere c'e' o non c'e', e deve poter TORNARE.
     *
     * Prima questo era un `if (!state.hasAuxTimer)` senza ramo contrario: passando a padel il "K"
     * spariva, e tornando al calcio non ricompariva piu' -- restava nascosto fino al primo
     * cambiamento del timer del portiere o alla prima riaccensione dello schermo, perche' l'unico
     * altro posto che ne decide la visibilita' e' il collector di keeperTimer. Ogni capacita' che
     * nasconde qualcosa deve avere il ramo che lo rimostra: e' la stessa regola per cui il
     * cronometro, i dettagli dei set e l'etichetta del gesto sono tutti scritti con un ternario.
     *
     * L'anello invece si spegne e basta: riaccenderlo NON spetta a questa funzione, perche'
     * dipende da se il conto alla rovescia stia girando, e quello lo sa solo il suo collector.
     */
    private fun applyAuxTimerRole(state: WearScoreState) {
        binding.keeperTimer.visibility = if (state.hasAuxTimer) View.VISIBLE else View.GONE
        if (!state.hasAuxTimer) {
            binding.keeperProgressBar.visibility = View.INVISIBLE
        }
    }

    /**
     * A partita finita i due lati smettono di essere bersagli.
     *
     * Sull'orologio il "+" e' tutto il lato: restava premibile e non faceva niente, perche' il
     * motore ignora un punto dopo la fine. Qui e' anche piu' facile che sul telefono continuare a
     * toccare senza guardare, quindi il tocco lungo -- che annulla -- resta acceso: e' l'unica
     * cosa sensata da fare a quel punto, e riportando indietro l'ultimo punto riaccende tutto.
     * Il tocco breve lo ferma davvero la guardia in WearViewModel.incrementScore.
     *
     * Niente piu' opacita': ad alpha 0.4 il giallo scendeva a 3.02:1 sul fondo, e il risultato
     * finale, cioe' la cosa che tutti chiedono a fine partita, era la meno leggibile dello
     * schermo. Che i lati siano spenti lo dice la riga in basso.
     */
    private fun applyMatchOver(finita: Boolean) {
        partitaFinita = finita
        listOf(binding.team1Container, binding.team2Container).forEach { lato ->
            lato.isClickable = !finita
        }
        refreshHint()
    }

    /** La riga in basso ha una cosa sola da dire, e quale sia lo decide qui. */
    private fun refreshHint() {
        if (!telefonoRaggiungibile) {
            // Quanti punti sono stati segnati e stanno aspettando il telefono. Senza questo
            // numero, "NIENTE TELEFONO" si legge come "non sto registrando niente", che e'
            // esattamente il contrario di quello che sta succedendo.
            binding.gestureHint.text =
                if (daConsegnare > 0) {
                    getString(R.string.wear_hint_pending, daConsegnare)
                } else {
                    getString(R.string.wear_hint_disconnected)
                }
            binding.gestureHint.setTextColor(ContextCompat.getColor(this, R.color.error_red))
            return
        }
        if (partitaFinita) {
            // Spegnere i due lati senza dire perche' li farebbe sembrare rotti.
            binding.gestureHint.setText(R.string.wear_match_over)
            binding.gestureHint.setTextColor(ContextCompat.getColor(this, R.color.stencil_white))
            return
        }
        binding.gestureHint.setText(if (annullamentoGlobale) R.string.wear_hint_undo else R.string.wear_hint_minus)
        binding.gestureHint.setTextColor(ContextCompat.getColor(this, R.color.sidewalk_gray))
    }

    /**
     * La stessa riga mostra due cose diverse e va detto quale.
     *
     * Con il cronometro e' "12:34" e si tocca per avviare; senza, e' "Set 2" e il tocco era gia'
     * disattivato -- ma restavano lo stesso stile, lo stesso peso e lo stesso riscontro al tocco,
     * quindi continuava a sembrare un comando. Ora il periodo e' piu' quieto del cronometro, e
     * non e' nemmeno piu' cliccabile.
     */
    private fun applyClockRole(state: WearScoreState) {
        if (state.hasClock) {
            // Il tempo si scrive SUBITO, non al prossimo tick: passando da padel a calcio col
            // cronometro fermo non arriva nessun tick, e restava scritto "Set 1".
            binding.matchTimer.text = viewModel.matchTimer.value
            binding.matchTimer.setTextColor(ContextCompat.getColor(this, R.color.stencil_white))
            binding.matchTimer.setTypeface(binding.matchTimer.typeface, android.graphics.Typeface.BOLD)
            binding.matchTimer.isClickable = true
            return
        }
        binding.matchTimer.text = state.periodLabel
        binding.matchTimer.setTextColor(ContextCompat.getColor(this, R.color.sidewalk_gray))
        binding.matchTimer.setTypeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL))
        binding.matchTimer.isClickable = false
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

                launch {
                    viewModel.pendingCount.collect { quanti ->
                        daConsegnare = quanti
                        refreshHint()
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
                        if (viewModel.scoreState.value == null) {
                            binding.team1Score.text = score.toString()
                            describeSides()
                        }
                    }
                }

                launch {
                    viewModel.team2Score.collect { score ->
                        if (viewModel.scoreState.value == null) {
                            binding.team2Score.text = score.toString()
                            describeSides()
                        }
                    }
                }

                // I nomi arrivano quando vogliono, e sono la prima parola che TalkBack legge.
                launch { viewModel.team1Name.collect { describeSides() } }
                launch { viewModel.team2Name.collect { describeSides() } }

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
                // "00:00" del flow v1 tornerebbe a schermo al posto del periodo ("Set 2").
                //
                // Ma solo senza cronometro. Con la sola condizione "v2 mai arrivato", nel calcio
                // il quadrante restava fermo sul primo valore per tutta la partita: il v2 non porta
                // il tempo, e il tempo arriva proprio da qui. Col cronometro il flow E' il dato.
                launch {
                    viewModel.matchTimer.collect { time ->
                        val stato = viewModel.scoreState.value
                        if (stato == null || stato.hasClock) binding.matchTimer.text = time
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
                                // Il tempo in cifre: dall'anello si poteva solo stimarlo, e
                                // l'anello ha un massimo fisso che con durate diverse mente (L8).
                                binding.keeperTimer.text =
                                    getString(
                                        R.string.wear_keeper_running,
                                        state.secondsRemaining / 60,
                                        state.secondsRemaining % 60,
                                    )
                                binding.keeperTimer.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.graffiti_pink))
                                binding.keeperProgressBar.visibility =
                                    if (auxAvailable) View.VISIBLE else View.INVISIBLE
                            }

                            is KeeperTimerState.Finished -> {
                                binding.keeperTimer.text = "K"
                                binding.keeperTimer.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.error_red))
                                binding.keeperProgressBar.visibility =
                                    if (auxAvailable) View.VISIBLE else View.INVISIBLE
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
                        applyConnectionState(
                            state is it.vantaggi.scoreboardessential.shared.communication.ConnectionState.Connected,
                        )
                    }
                }
            }
        }
    }
}
