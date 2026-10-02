package it.vantaggi.scoreboardessential.wear

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.ambient.AmbientLifecycleObserver
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.Wearable
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.core.TeamInk
import it.vantaggi.scoreboardessential.shared.communication.ConnectionState
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import it.vantaggi.scoreboardessential.wear.databinding.ActivityMainBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"

        /** Ogni quanto si richiede il collegamento a partita in corso e schermo acceso. */
        internal const val INTERVALLO_VERIFICA_MS = 15_000L

        /**
         * Dopo il risveglio (onResume e uscita dall'ambient) i tocchi sui lati non contano: il primo
         * tocco serve quasi sempre a svegliare lo schermo, non a segnare.
         */
        internal const val GUARDIA_RISVEGLIO_MS = 500L

        /** Quanto si sposta il quadrante in ambient se lo schermo chiede la protezione anti burn-in. */
        private const val SPOSTAMENTO_BURN_IN_DP = 4
    }

    /**
     * L'orologio della guardia al risveglio, iniettabile come quello del ViewModel: il test sposta
     * il tempo invece di aspettarlo. Di default e' il tempo di sistema che non torna indietro.
     */
    internal var orologio: () -> Long = SystemClock::elapsedRealtime

    /** Fino a quando i tocchi sui lati sono ignorati in silenzio; 0 se nessuna guardia e' aperta. */
    private var guardiaFinoA = 0L

    /** Il quadrante e' in ambient: polso abbassato, solo bianco e grigio su nero. */
    private var ambient = false
    private var ambientBurnIn = false
    private var ambientBitBassi = false
    private var passoBurnIn = 0

    /** Le cifre in ambient sono light; fuori dall'ambient tornano le condensed bold del tema. */
    private val carattereLeggero: Typeface by lazy { Typeface.create("sans-serif-condensed-light", Typeface.NORMAL) }
    private val carattereGrasso: Typeface by lazy { Typeface.create("sans-serif-condensed", Typeface.BOLD) }

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

    /**
     * Il menu partita torna qui con la scelta: lo sport apre la sua lista, la fine chiude la
     * partita. Con un risultato annullato (indietro o chiusura automatica) non si fa niente.
     */
    private val menu =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { esito ->
            if (esito.resultCode != android.app.Activity.RESULT_OK) return@registerForActivityResult
            when (esito.data?.getStringExtra(MenuActivity.EXTRA_AZIONE)) {
                MenuActivity.AZIONE_SPORT -> apriSceltaSport()
                MenuActivity.AZIONE_FINE -> chiudiSeAncoraPossibile()
            }
        }

    // Serve solo alla descrizione dei lati per TalkBack: cosa dice la riga in basso lo decide
    // StatoFiducia nel ViewModel, non questa schermata.
    private var annullamentoGlobale = false

    /** Chi serve, come l'ultimo stato l'ha detto: serve alla descrizione dei lati (0 = nessuno). */
    private var serveLato = 0
    private var serveGiocatore = 0
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
                        viewModel.applyStateV2(
                            WearScoreState.fromDataMap(DataMap.fromByteArray(payload)),
                            dalVivo = intent.getBooleanExtra(WearDataLayerService.EXTRA_V2_DAL_VIVO, true),
                        )
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
                        val durata = intent.getLongExtra(WearDataLayerService.EXTRA_KEEPER_DURATION, 0L)
                        viewModel.applyKeeperFromPhone(millis, isRunning, durata)
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
        describeSides()
        viewModel.refreshPendingCount()
        observeViewModel()

        osservaAmbient()

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

    /**
     * Abbassando il polso il quadrante resta in ambient invece di cedere il posto a quello di
     * sistema: il punteggio c'e' quando si rialza il braccio. Lo schermo sempre acceso no (DESIGN.md,
     * Decisioni prese, Orologio 2): con l'always-on spento nel sistema l'ambient non costa niente.
     *
     * L'observer parla con la libreria condivisa com.google.android.wearable, che l'orologio ha e
     * che il manifest richiede (uses-library required): sull'orologio la creazione non fallisce.
     * Sulla JVM dei test quella classe non esiste e l'observer lancia NoClassDefFoundError subito:
     * li' l'ambient resta spento e i test lo pilotano da applyAmbient, che e' la stessa funzione
     * che i callback chiamano.
     */
    private fun osservaAmbient() {
        try {
            lifecycle.addObserver(
                AmbientLifecycleObserver(
                    this,
                    object : AmbientLifecycleObserver.AmbientLifecycleCallback {
                        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) =
                            applyAmbient(true, ambientDetails.burnInProtectionRequired, ambientDetails.deviceHasLowBitAmbient)

                        override fun onUpdateAmbient() = aggiornaAmbient()

                        override fun onExitAmbient() = applyAmbient(false)
                    },
                ),
            )
        } catch (e: NoClassDefFoundError) {
            Log.w(TAG, "Libreria wearable assente: niente ambient", e)
        }
    }

    override fun onResume() {
        super.onResume()
        apriGuardia()
        restoreStateFromDataItems()
        // Al risveglio la riga di stato deve dire com'e' il collegamento ADESSO, non com'era
        // all'avvio.
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
                        // Rilettura, non un momento in cui il telefono ha parlato: l'ora dell'ultimo
                        // dato vivo non si tocca.
                        .forEach { WearDataLayerService.dispatchDataItem(this, it, dalVivo = false) }
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
        // Il tick lo suona il ViewModel (WearHaptics), non performHapticFeedback.
        //
        // Nei 500ms dopo il risveglio i due lati non rispondono, ne' al tocco ne' al tocco lungo:
        // un punto segnato per svegliare lo schermo sarebbe un punto falso (vedi guardiaAttiva).
        binding.team1Container.setOnClickListener { if (!guardiaAttiva()) viewModel.incrementScore(1) }
        binding.team2Container.setOnClickListener { if (!guardiaAttiva()) viewModel.incrementScore(2) }

        binding.team1Container.setOnLongClickListener {
            if (!guardiaAttiva()) viewModel.decrementScore(1)
            true
        }

        binding.team2Container.setOnLongClickListener {
            if (!guardiaAttiva()) viewModel.decrementScore(2)
            true
        }

        // Timer controls: i bersagli sono viste a parte, separate dai testi della fascia A.
        binding.touchTimer.setOnClickListener {
            // Senza cronometro quella fascia mostra i game del set: non e' un comando.
            if (viewModel.scoreState.value?.hasClock != false) {
                viewModel.toggleTimer()
            }
        }
        // Il tempo nella descrizione si legge quando TalkBack la chiede, non a ogni tick: un
        // cronometro che cambia la descrizione ogni secondo verrebbe riletto di continuo.
        binding.touchTimer.accessibilityDelegate =
            object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfo,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    descrizioneContesto()?.let { info.contentDescription = it }
                }
            }

        binding.touchKeeper.setOnClickListener {
            viewModel.toggleKeeperTimer()
        }

        // Lo stesso bersaglio vale due cose: CHI? per 8s dopo un gol confermato, altrimenti il menu.
        binding.btnMenu.setOnClickListener {
            if (viewModel.finestraChi.value != null) apriMarcatore() else apriMenu()
        }
    }

    /**
     * Mentre CHI? e' offerto il glifo del menu lascia il posto alla capsula e il bersaglio cambia
     * descrizione, cosi' TalkBack dice che cosa fa il tocco. Quando l'offerta finisce torna il menu.
     */
    private fun renderFinestraChi(finestra: WearViewModel.FinestraChi?) {
        val offerta = finestra != null
        binding.chiCapsule.visibility = if (offerta) View.VISIBLE else View.GONE
        binding.menuGlyph.visibility = if (offerta) View.INVISIBLE else View.VISIBLE
        binding.btnMenu.contentDescription = getString(if (offerta) R.string.wear_who_scored else R.string.cd_menu)
        sovrapponiAmbient()
    }

    /**
     * Apre la lista del marcatore e consuma l'offerta: tornati dalla lista, il bersaglio e' di nuovo
     * il menu. Il colore e' quello della squadra che ha segnato, il punteggio quello del gol offerto.
     */
    private fun apriMarcatore() {
        val finestra = viewModel.finestraChi.value ?: return
        val colore =
            (if (finestra.lato == 1) viewModel.team1Color.value else viewModel.team2Color.value)
                ?: ContextCompat.getColor(this, if (finestra.lato == 1) R.color.team_spray_yellow else R.color.team_electric_green)
        viewModel.chiudiFinestraChi()
        startActivity(
            PlayerSelectionActivity.intent(this, finestra.lato, viewModel.allPlayers.value, colore, finestra.risultato),
        )
    }

    /**
     * Sport e fine partita stanno nel menu: qui si raccolgono i fatti su cui MenuVoci decide quali
     * voci accendere, e il menu restituisce solo che cosa si e' scelto.
     */
    private fun apriMenu() {
        menu.launch(MenuActivity.intent(this, inputMenu()))
    }

    /**
     * Il menu ha fotografato i fatti all'apertura, ma fra i due tocchi della conferma possono
     * passare secondi: arrivare una coda, un v2 di calcio, una chiusura gia' fatta. I blocchi si
     * ricalcolano sui fatti di adesso, e se FINE PARTITA non e' piu' accesa si riapre il menu, che
     * dice a parole perche' (il sottotitolo della voce spenta).
     */
    private fun chiudiSeAncoraPossibile() {
        val fine = MenuVoci.calcola(inputMenu()).first { it.id == IdVoce.FINE_PARTITA }
        if (fine.attiva) viewModel.chiudiPartita() else apriMenu()
    }

    /** I fatti su cui [MenuVoci] decide, letti adesso. */
    private fun inputMenu(): InputMenu {
        val stato = viewModel.scoreState.value
        // Senza v2 il punteggio e' quello che il polso ha contato da solo.
        val risultato =
            if (stato != null) {
                "${stato.side1Primary}–${stato.side2Primary}"
            } else {
                "${viewModel.team1Score.value}–${viewModel.team2Score.value}"
            }
        return InputMenu(
            inCoda = viewModel.pendingCount.value,
            collegato = viewModel.connectionState.value is ConnectionState.Connected,
            // Senza v2, un tocco in coda o un punto a schermo sono gli unici segni che si sta giocando.
            partitaIniziata =
                stato?.matchInProgress
                    ?: (viewModel.pendingCount.value > 0 || viewModel.team1Score.value + viewModel.team2Score.value > 0),
            calcioConV2 = stato?.sportId == SportRegistry.FOOTBALL,
            haElencoSport = stato != null && stato.sportIds.size > 1,
            sport = stato?.sportLabel.orEmpty(),
            risultato = risultato,
        )
    }

    private fun apriSceltaSport() {
        val stato = viewModel.scoreState.value ?: return
        // Il menu l'ha gia' deciso, ma fra il tocco e qui puo' essere arrivato un nuovo stato: il
        // telefono rifiuterebbe comunque, e far partire una richiesta persa non cambia niente.
        if (stato.matchInProgress) return
        sceltaSport.launch(
            Intent(this, SportSelectionActivity::class.java).apply {
                putExtra(SportSelectionActivity.EXTRA_IDS, ArrayList(stato.sportIds))
                putExtra(SportSelectionActivity.EXTRA_LABELS, ArrayList(stato.sportLabels))
                putExtra(SportSelectionActivity.EXTRA_CURRENT, stato.sportLabel)
            },
        )
    }

    /**
     * L'orologio non calcola: mette a schermo le stringhe che il telefono ha gia' impaginato, e
     * toglie i comandi che questo sport non ha. Con le capacita' del calcio non cambia nulla.
     */
    private fun renderScoreState(state: WearScoreState) {
        binding.team1Score.text = state.side1Primary
        binding.team2Score.text = state.side2Primary
        binding.faceDetail.text = FaceText.split(state).second
        applyGestureLabels(state.decrementIsUndo)
        applyMatchOver(state)
        applyServing(state)

        renderContesto()

        applyAuxTimerRole(state)
        sovrapponiAmbient()
    }

    /**
     * Il pallino bianco sul lato esterno della colonna di chi serve; con 0 (calcio, finita)
     * nessuno. Un pallino se batte il primo giocatore o si gioca in singolare, due se batte il
     * secondo: il secondo sta accanto al primo, verso le cifre, e il primo non si muove.
     */
    private fun applyServing(state: WearScoreState) {
        binding.team1ServeDot.visibility = if (state.servingSide == 1) View.VISIBLE else View.GONE
        binding.team2ServeDot.visibility = if (state.servingSide == 2) View.VISIBLE else View.GONE
        val secondo = state.servingSlot == 2
        binding.team1ServeDotSecond.visibility = if (state.servingSide == 1 && secondo) View.VISIBLE else View.GONE
        binding.team2ServeDotSecond.visibility = if (state.servingSide == 2 && secondo) View.VISIBLE else View.GONE
        serveLato = state.servingSide
        serveGiocatore = state.servingSlot
        describeSides()
    }

    /** Il colore della squadra e' solo grafica: portato a 3:1 sul nero, mai colore di un testo. */
    private fun coloraStriscia(
        striscia: View,
        colore: Int,
    ) = striscia.setBackgroundColor(TeamInk.graphicOnBlack(colore) or TeamInk.NERO)

    /**
     * Dice a parole che cosa fa il tocco lungo, e lo dice in modo diverso nei due casi.
     *
     * decrementIsUndo viveva solo dentro il ViewModel: sullo schermo dell'orologio non c'era una
     * riga, un'icona o una descrizione che distinguesse "togli un punto a questa squadra" da
     * "annulla l'ultima azione". Sono due cose diverse e ora si leggono: la descrizione dei lati
     * qui, e il suggerimento in riga di stato quando non c'e' altro da dire.
     */
    private fun applyGestureLabels(decrementIsUndo: Boolean) {
        annullamentoGlobale = decrementIsUndo
        describeSides()
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
            getString(descrizione, teamName(viewModel.team1Name.value, 1), binding.team1Score.text.toString()) +
            chiServe(1, teamName(viewModel.team1Name.value, 1))
        binding.team2Container.contentDescription =
            getString(descrizione, teamName(viewModel.team2Name.value, 2), binding.team2Score.text.toString()) +
            chiServe(2, teamName(viewModel.team2Name.value, 2))
    }

    /**
     * La frase di chi serve, in coda alla descrizione del lato: i pallini non hanno una
     * descrizione propria, perche' il lato e' un bersaglio solo e TalkBack legge la sua. Vuota
     * per la squadra che non serve.
     */
    private fun chiServe(
        lato: Int,
        nome: String,
    ): String {
        if (serveLato != lato) return ""
        val frase =
            when (serveGiocatore) {
                1 -> R.string.cd_serving_first_player
                2 -> R.string.cd_serving_second_player
                else -> R.string.cd_serving
            }
        return " " + getString(frase, nome)
    }

    /** Il nome scelto sul telefono, lo stesso che legge TalkBack la'; "Squadra 1" finche' non arriva. */
    private fun teamName(
        nome: String,
        lato: Int,
    ): String = nome.ifBlank { getString(R.string.cd_team_fallback, lato) }

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
        mostraPortiere(state.hasAuxTimer)
        if (!state.hasAuxTimer) {
            binding.keeperProgressBar.visibility = View.INVISIBLE
        }
    }

    /** Il testo e il bersaglio del portiere vanno e vengono insieme. */
    private fun mostraPortiere(visibile: Boolean) {
        val visibilita = if (visibile) View.VISIBLE else View.GONE
        binding.keeperTimer.visibility = visibilita
        binding.touchKeeper.visibility = visibilita
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
     * schermo. Lo sconfitto passa invece a #9E9E9E (7.84:1 su nero), come sul telefono: si legge
     * chi ha perso senza spegnere la cifra. Il vincitore, e chi non ha un vincitore (un pari),
     * restano bianchi. Che i lati siano spenti lo dice anche la riga in basso.
     */
    private fun applyMatchOver(state: WearScoreState) {
        listOf(binding.team1Container, binding.team2Container).forEach { lato ->
            lato.isClickable = !state.matchOver
        }
        val vincitore = FaceText.vincitore(state)
        val bianco = ContextCompat.getColor(this, R.color.ink_white)
        val grigio = ContextCompat.getColor(this, R.color.sidewalk_gray)
        binding.team1Score.setTextColor(if (vincitore == 2) grigio else bianco)
        binding.team2Score.setTextColor(if (vincitore == 1) grigio else bianco)
    }

    /** I due lati ignorano i tocchi finche' la guardia al risveglio e' aperta. */
    private fun guardiaAttiva(): Boolean = orologio() < guardiaFinoA

    private fun apriGuardia() {
        guardiaFinoA = orologio() + GUARDIA_RISVEGLIO_MS
    }

    /**
     * Polso abbassato (on) o rialzato (off).
     *
     * In ambient il quadrante e' solo bianco e grigio su nero, con le cifre light: via le strisce, il
     * portiere con il suo anello, il dettaglio, il glifo del menu e CHI?; la riga in basso parla
     * solo se c'e' qualcosa che non va o la partita e' finita, in grigio chiaro. Nel calcio il
     * tempo e' in minuti, "34'". Le cifre restano dove sono e della stessa misura: l'unica cosa che
     * si muove e' lo spostamento anti burn-in, se lo schermo lo chiede ([burnIn]).
     *
     * All'uscita si ridisegna tutto dallo stato di adesso e si apre la guardia: il tocco che sveglia
     * lo schermo non deve segnare un punto.
     */
    internal fun applyAmbient(
        on: Boolean,
        burnIn: Boolean = false,
        bitBassi: Boolean = false,
    ) {
        val eraAmbient = ambient
        ambient = on
        ambientBurnIn = on && burnIn
        ambientBitBassi = on && bitBassi
        if (on) {
            passoBurnIn = 0
            spostaBurnIn()
            renderContesto()
            sovrapponiAmbient()
        } else if (eraAmbient) {
            ripristinaDaAmbient()
            apriGuardia()
        }
    }

    /** Il sistema aggiorna il quadrante ogni minuto: il tempo e lo spostamento anti burn-in. */
    internal fun aggiornaAmbient() {
        if (!ambient) return
        renderContesto()
        spostaBurnIn()
    }

    /**
     * Con la protezione anti burn-in la radice si sposta di 4dp a ogni aggiornamento, su un
     * giro di quattro angoli: gli stessi pixel non restano accesi uguali per ore.
     */
    private fun spostaBurnIn() {
        if (!ambientBurnIn) {
            binding.root.translationX = 0f
            binding.root.translationY = 0f
            return
        }
        val passo = SPOSTAMENTO_BURN_IN_DP * resources.displayMetrics.density
        val (segnoX, segnoY) =
            when (passoBurnIn++ % 4) {
                0 -> 1 to 1
                1 -> -1 to 1
                2 -> -1 to -1
                else -> 1 to -1
            }
        binding.root.translationX = segnoX * passo
        binding.root.translationY = segnoY * passo
    }

    /**
     * Cio' che l'ambient toglie o cambia SOPRA al disegno normale. Dopo ogni render che potrebbe
     * rimettere in vista un elemento colorato si richiama: non fa niente fuori dall'ambient.
     */
    private fun sovrapponiAmbient() {
        if (!ambient) return
        listOf(binding.team1Score, binding.team2Score, binding.matchTimer).forEach { vista ->
            if (vista.typeface !== carattereLeggero) vista.typeface = carattereLeggero
            vista.paint.isAntiAlias = !ambientBitBassi
        }
        binding.gestureHint.paint.isAntiAlias = !ambientBitBassi
        listOf(
            binding.team1Stripe,
            binding.team2Stripe,
            binding.keeperProgressBar,
            binding.keeperTimer,
            binding.faceDetail,
            binding.menuGlyph,
        ).forEach { it.visibility = View.INVISIBLE }
        binding.chiCapsule.visibility = View.GONE
        // Il suggerimento (TIENI: -1) non e' un'anomalia: a polso abbassato si tace.
        val frase = viewModel.statoFiducia.value
        if (frase == Frase.TieniMeno || frase == Frase.TieniAnnulla) {
            binding.gestureHint.text = ""
        }
        binding.gestureHint.setTextColor(ContextCompat.getColor(this, R.color.ambient_gray))
    }

    /** Fuori dall'ambient: caratteri, strisce e dettaglio tornano, il resto lo ridisegna lo stato. */
    private fun ripristinaDaAmbient() {
        binding.root.translationX = 0f
        binding.root.translationY = 0f
        listOf(binding.team1Score, binding.team2Score, binding.matchTimer).forEach { vista ->
            vista.typeface = carattereGrasso
            vista.paint.isAntiAlias = true
        }
        binding.gestureHint.paint.isAntiAlias = true
        listOf(binding.team1Stripe, binding.team2Stripe, binding.faceDetail).forEach { it.visibility = View.VISIBLE }
        val stato = viewModel.scoreState.value
        if (stato != null) {
            renderScoreState(stato)
        } else {
            binding.team1Score.text = viewModel.team1Score.value.toString()
            binding.team2Score.text = viewModel.team2Score.value.toString()
            describeSides()
            renderContesto()
        }
        renderPortiere(viewModel.keeperTimer.value)
        renderFinestraChi(viewModel.finestraChi.value)
        renderStatus(viewModel.statoFiducia.value)
    }

    /**
     * La riga in basso ha una cosa sola da dire, e quale sia l'ha gia' deciso StatoFiducia.
     *
     * Sul polso la norma e' che vada tutto bene, quindi niente pallino che dica "ok": la riga
     * parla quando qualcosa non va, a parole, cosi' vale anche per chi non distingue l'ambra dal
     * rosso e al sole. Col telefono scollegato, a differenza di prima, dice anche QUANTI punti
     * aspettano; col telefono raggiungibile dice se la coda non parte.
     */
    private fun renderStatus(frase: Frase) {
        binding.gestureHint.text = frase.testo(this)
        binding.gestureHint.setTextColor(ContextCompat.getColor(this, frase.tono.colore()))
        sovrapponiAmbient()
    }

    /**
     * La fascia A mostra due cose diverse e va detto quale.
     *
     * Col cronometro (calcio) e' "12:34": bianco se corre, grigio se e' fermo, e si tocca per
     * avviarlo o fermarlo. Senza (padel, tennis) sono i game del set in corso, "4 – 3", bianchi, e
     * non e' un comando: il bersaglio non risponde. I set chiusi e il periodo stanno in fascia D.
     *
     * Senza v2 si comporta come il calcio, perche' e' quello che il polso sa fare da solo.
     * Si ridisegna a ogni cambio dello stato, del tempo e del correre: il tempo si scrive SUBITO e
     * non al prossimo tick, perche' passando da padel a calcio col cronometro fermo non arriva
     * nessun tick, e restavano scritti i game del set.
     */
    private fun renderContesto() {
        val stato = viewModel.scoreState.value
        if (stato != null && !stato.hasClock) {
            posizionaContesto(centrato = true)
            binding.matchTimer.text = FaceText.split(stato).first
            binding.matchTimer.setTextColor(ContextCompat.getColor(this, R.color.ink_white))
            binding.touchTimer.isClickable = false
            return
        }
        posizionaContesto(centrato = false)
        // In ambient solo i minuti ("34'"): i secondi non si aggiornano a polso abbassato. Il testo si
        // riscrive solo se cambia, perche' il cronometro del ViewModel batte ogni secondo.
        val tempo = viewModel.matchTimer.value.let { if (ambient) FaceText.minuti(it) else it }
        if (binding.matchTimer.text.toString() != tempo) binding.matchTimer.text = tempo
        val corre = viewModel.matchTimerRunning.value
        binding.matchTimer.setTextColor(ContextCompat.getColor(this, if (corre) R.color.ink_white else R.color.sidewalk_gray))
        binding.touchTimer.isClickable = true
    }

    /**
     * Dove sta il testo della fascia A.
     *
     * Col cronometro (calcio) e' allineato a destra e finisce 5dp prima del centro, dove comincia
     * il K: le due posizioni non cambiano col testo. Senza (racchetta) il K non c'e' e i game stanno
     * al centro dello schermo: restando fermi su x=91 col K nascosto sarebbero fuori centro.
     */
    private fun posizionaContesto(centrato: Boolean) {
        val vista = binding.matchTimer
        val gravita = if (centrato) Gravity.CENTER else Gravity.END or Gravity.CENTER_VERTICAL
        if (vista.gravity == gravita) return
        vista.gravity = gravita
        val parametri = vista.layoutParams as ConstraintLayout.LayoutParams
        if (centrato) {
            parametri.endToStart = ConstraintLayout.LayoutParams.UNSET
            parametri.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            parametri.marginEnd = 0
        } else {
            parametri.endToEnd = ConstraintLayout.LayoutParams.UNSET
            parametri.endToStart = R.id.guide_center
            parametri.marginEnd = resources.getDimensionPixelSize(R.dimen.face_context_gap)
        }
        vista.layoutParams = parametri
    }

    /** Cosa legge TalkBack sul bersaglio della fascia A, calcolato adesso; niente se non c'e' niente. */
    private fun descrizioneContesto(): String? {
        val stato = viewModel.scoreState.value
        if (stato != null && !stato.hasClock) {
            val giochi = FaceText.split(stato).first
            return if (giochi.isEmpty()) null else getString(R.string.cd_current_game, giochi)
        }
        val tempo = binding.matchTimer.text.toString()
        val corre = viewModel.matchTimerRunning.value
        return getString(if (corre) R.string.cd_match_clock_running else R.string.cd_match_clock_stopped, tempo)
    }

    /**
     * Il portiere (solo calcio): testo e anello. Sta qui e non nel collector perche' all'uscita
     * dall'ambient va ridisegnato con lo stato di adesso.
     */
    private fun renderPortiere(state: KeeperTimerState) {
        // SEMPRE VISIBILE, finche' lo sport ha davvero un timer ausiliario.
        val auxAvailable = viewModel.scoreState.value?.hasAuxTimer != false
        mostraPortiere(auxAvailable)
        when (state) {
            is KeeperTimerState.Hidden -> {
                binding.keeperTimer.text = "K"
                binding.keeperTimer.setTextColor(ContextCompat.getColor(this, R.color.sidewalk_gray))
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
                binding.keeperTimer.setTextColor(ContextCompat.getColor(this, R.color.graffiti_pink))
                binding.keeperProgressBar.visibility =
                    if (auxAvailable) View.VISIBLE else View.INVISIBLE
            }

            is KeeperTimerState.Paused -> {
                // Il residuo resta leggibile, in grigio: fermo, non scaduto.
                binding.keeperTimer.text =
                    getString(
                        R.string.wear_keeper_running,
                        state.secondsRemaining / 60,
                        state.secondsRemaining % 60,
                    )
                binding.keeperTimer.setTextColor(ContextCompat.getColor(this, R.color.sidewalk_gray))
                binding.keeperProgressBar.visibility =
                    if (auxAvailable) View.VISIBLE else View.INVISIBLE
            }

            is KeeperTimerState.Finished -> {
                // Scaduto: "K 0:00" in rosso, come "K 4:12" mentre corre.
                binding.keeperTimer.text = getString(R.string.wear_keeper_running, 0, 0)
                binding.keeperTimer.setTextColor(ContextCompat.getColor(this, R.color.error_red))
                binding.keeperProgressBar.visibility =
                    if (auxAvailable) View.VISIBLE else View.INVISIBLE
            }
        }
        sovrapponiAmbient()
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

                launch { viewModel.statoFiducia.collect { renderStatus(it) } }

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
                        color?.let { coloraStriscia(binding.team1Stripe, it) }
                    }
                }

                launch {
                    viewModel.team2Color.collect { color ->
                        color?.let { coloraStriscia(binding.team2Stripe, it) }
                    }
                }

                // Stessa ragione: senza cronometro il v2 non manda un tempo, e il valore iniziale
                // "00:00" del flow v1 tornerebbe a schermo al posto del periodo ("Set 2").
                //
                // Ma solo senza cronometro. Con la sola condizione "v2 mai arrivato", nel calcio
                // il quadrante restava fermo sul primo valore per tutta la partita: il v2 non porta
                // il tempo, e il tempo arriva proprio da qui. Col cronometro il flow E' il dato.
                launch { viewModel.matchTimer.collect { renderContesto() } }

                // Bianco se corre, grigio se e' fermo: il colore dice lo stato senza un segno in piu'.
                launch { viewModel.matchTimerRunning.collect { renderContesto() } }

                // Observe Keeper Timer
                // Il portiere si ridisegna anche all'uscita dall'ambient: vedi renderPortiere.
                launch { viewModel.keeperTimer.collect { renderPortiere(it) } }

                // Observe Keeper Progress
                launch {
                    viewModel.keeperProgress.collect { progress ->
                        binding.keeperProgressBar.progress = progress
                    }
                }

                // Il massimo dell'anello e' la durata. Il progresso si riapplica dopo, perche'
                // ProgressBar lo taglia al massimo vecchio se arriva prima del nuovo.
                launch {
                    viewModel.keeperDurationSeconds.collect { secondi ->
                        binding.keeperProgressBar.max = secondi
                        binding.keeperProgressBar.progress = viewModel.keeperProgress.value
                    }
                }

                // CHI? prende il posto del glifo per 8s dopo un gol confermato, e basta: la lista
                // non si apre piu' da sola. Collect riemette il valore a ogni ritorno in STARTED.
                launch { viewModel.finestraChi.collect { renderFinestraChi(it) } }
                // Il telefono e' tornato: cio' che si e' segnato senza di lui parte adesso, da solo.
                // Cosa dire del collegamento non e' affare di questo collector: lo dice la riga.
                launch {
                    viewModel.connectionState.collect { state ->
                        if (state is it.vantaggi.scoreboardessential.shared.communication.ConnectionState.Connected) {
                            viewModel.flushPending()
                        }
                    }
                }
            }
        }

        // Il listener della capability non vede il Bluetooth che cade: con la partita in corso il
        // collegamento si richiede da soli ogni 15 secondi. Legato a RESUMED e non a STARTED: a
        // schermo spento, o col quadrante di sistema in primo piano, l'activity puo' restare STARTED
        // ma nessuno guarda la riga, e le richieste sarebbero solo batteria. Fuori da RESUMED il
        // ciclo si ferma, e riparte dal primo onResume (che gia' chiede una volta).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (true) {
                    delay(INTERVALLO_VERIFICA_MS)
                    viewModel.refreshConnectionSePartitaInCorso()
                }
            }
        }
    }
}

/**
 * La frase di stato nella lingua dell'orologio.
 *
 * Fuori dalla schermata perche' il test delle lunghezze la chiama sulle risorse vere, in italiano e
 * in inglese: e' il solo modo di sapere che una frase entra nei 18 caratteri prima che la tagli il
 * quadrante tondo.
 */
internal fun Frase.testo(context: Context): String =
    when (this) {
        is Frase.Rifiutati -> context.getString(R.string.wear_status_rejected, n)
        is Frase.NonConsegnati -> context.getString(R.string.wear_status_not_delivered, n)
        is Frase.Invio -> context.getString(R.string.wear_status_sending, n)
        is Frase.InCoda -> context.getString(R.string.wear_status_queued, n)
        is Frase.Scollegato -> testoScollegato(context, alle)
        Frase.PartitaFinita -> context.getString(R.string.wear_match_over)
        Frase.TieniMeno -> context.getString(R.string.wear_hint_minus)
        Frase.TieniAnnulla -> context.getString(R.string.wear_hint_undo)
        Transitorio.NonConfermato -> context.getString(R.string.wear_status_not_confirmed)
        is Transitorio.Consegnati -> context.getString(R.string.wear_status_delivered, n)
        Transitorio.Chiusura -> context.getString(R.string.wear_status_closing)
        Transitorio.ChiusuraNonConfermata -> context.getString(R.string.wear_status_close_unconfirmed)
        Transitorio.CambioSport -> context.getString(R.string.wear_status_changing_sport)
        Transitorio.SportNonCambiato -> context.getString(R.string.wear_status_sport_unchanged)
    }

/** L'ora e' fissa a 24 ore, "18:42": la stessa larghezza in ogni lingua, dentro i 18 caratteri. */
private fun testoScollegato(
    context: Context,
    alle: Long?,
): String {
    if (alle == null) return context.getString(R.string.wear_status_offline)
    val ora = SimpleDateFormat("HH:mm", Locale.ROOT).format(Date(alle))
    return context.getString(R.string.wear_status_offline_at, ora)
}

/** Il colore del ruolo: ambra e rosso distano 2.14:1, li distingue la parola. */
internal fun Tono.colore(): Int =
    when (this) {
        Tono.ROSSO -> R.color.error_red
        Tono.AMBRA -> R.color.signal_amber
        Tono.CHIARO -> R.color.stencil_white
        Tono.GRIGIO -> R.color.sidewalk_gray
    }
