package it.vantaggi.scoreboardessential.wear

import android.app.Application
import android.os.CountDownTimer
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.DataMap
import it.vantaggi.scoreboardessential.core.ClockMode
import it.vantaggi.scoreboardessential.core.MatchEngine
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager
import it.vantaggi.scoreboardessential.shared.PlayerData
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Il punteggio gia' impaginato dal telefono, piu' le capacita' dello sport.
 *
 * L'orologio non esegue mai regole: rende stringhe. Per questo qui non c'e' nessun numero da
 * sommare, e aggiungere uno sport non richiede una riga di codice su questo lato.
 */
data class WearScoreState(
    val side1Primary: String,
    val side1Secondary: String,
    val side2Primary: String,
    val side2Secondary: String,
    val periodLabel: String,
    val hasClock: Boolean,
    val hasAuxTimer: Boolean,
    val attributesScorer: Boolean,
    val decrementIsUndo: Boolean,
    /**
     * Lo sport corrente e quelli scegliibili, gia' tradotti dal telefono.
     *
     * L'orologio non conosce :core e non deve conoscerlo: se l'elenco vivesse anche qui,
     * aggiungere uno sport vorrebbe dire aggiornare due APK invece di uno. Su un telefono che
     * parla una bozza precedente del v2 questi elenchi arrivano vuoti, e il comando dello sport
     * semplicemente non compare: nessun ramo speciale, nessun errore.
     */
    val sportId: String,
    val sportLabel: String,
    val sportIds: List<String>,
    val sportLabels: List<String>,
    val matchInProgress: Boolean,
    /** La partita e' finita: nessun tocco puo' piu' cambiare il risultato. */
    val matchOver: Boolean,
    /** Il registro degli eventi del telefono: il punto di partenza quando si resta soli. */
    val eventLog: String,
    /**
     * Chi serve: 1 o 2, 0 se nessuno (calcio, partita finita). Il telefono lo spediva gia' e qui
     * andava perso. Col default i costruttori esistenti non cambiano; non e' ancora disegnato.
     */
    val servingSide: Int = 0,
) {
    companion object {
        private const val TAG = "WearScoreState"

        /** Una stringa vuota da' una lista vuota, non una lista con dentro il vuoto. */
        private fun elenco(grezzo: String): List<String> =
            if (grezzo.isEmpty()) emptyList() else grezzo.split(WearConstants.SPORT_SEPARATOR)

        /**
         * Nessuna versione viene mai rifiutata: un telefono piu' recente puo' aggiungere chiavi, e
         * ogni lettura ha un valore di default, quindi non lancia. Un orologio vecchio deve
         * mostrare cio' che capisce, non morire.
         *
         * I default delle capacita' sono quelli del calcio: se il telefono le omettesse, la
         * schermata resterebbe quella di oggi invece di perdere comandi.
         */
        fun fromDataMap(dataMap: DataMap): WearScoreState {
            val version = dataMap.getInt(WearConstants.KEY_PROTO_VERSION, WearConstants.PROTO_VERSION)
            if (version > WearConstants.PROTO_VERSION) {
                Log.i(TAG, "Protocollo v$version piu' recente di v${WearConstants.PROTO_VERSION}: leggo cio' che conosco")
            }
            return WearScoreState(
                side1Primary = dataMap.getString(WearConstants.KEY_SIDE1_PRIMARY, ""),
                side1Secondary = dataMap.getString(WearConstants.KEY_SIDE1_SECONDARY, ""),
                side2Primary = dataMap.getString(WearConstants.KEY_SIDE2_PRIMARY, ""),
                side2Secondary = dataMap.getString(WearConstants.KEY_SIDE2_SECONDARY, ""),
                periodLabel = dataMap.getString(WearConstants.KEY_PERIOD_LABEL, ""),
                hasClock = dataMap.getBoolean(WearConstants.KEY_CAP_HAS_CLOCK, true),
                hasAuxTimer = dataMap.getBoolean(WearConstants.KEY_CAP_HAS_AUX_TIMER, true),
                attributesScorer = dataMap.getBoolean(WearConstants.KEY_CAP_ATTRIBUTES_SCORER, true),
                decrementIsUndo = dataMap.getBoolean(WearConstants.KEY_CAP_DECREMENT_IS_UNDO, false),
                sportId = dataMap.getString(WearConstants.KEY_SPORT_ID, ""),
                sportLabel = dataMap.getString(WearConstants.KEY_SPORT_LABEL, ""),
                sportIds = elenco(dataMap.getString(WearConstants.KEY_SPORT_IDS, "")),
                sportLabels = elenco(dataMap.getString(WearConstants.KEY_SPORT_LABELS, "")),
                matchInProgress = dataMap.getBoolean(WearConstants.KEY_MATCH_IN_PROGRESS, false),
                matchOver = dataMap.getBoolean(WearConstants.KEY_MATCH_OVER, false),
                eventLog = dataMap.getString(WearConstants.KEY_EVENT_LOG, ""),
                servingSide = dataMap.getInt(WearConstants.KEY_SERVING_SIDE, 0),
            )
        }
    }
}

sealed class KeeperTimerState {
    object Hidden : KeeperTimerState()

    data class Running(
        val secondsRemaining: Int,
    ) : KeeperTimerState()

    object Finished : KeeperTimerState()
}

class WearViewModel(
    application: Application,
    // Iniettabile come i client dentro OptimizedWearDataSync stesso: sotto test
    // il costruttore di default avvia i client GMS reali, il cui GoogleApiHandler
    // muore sul looper di Robolectric.
    private val connectionManager: OptimizedWearDataSync = OptimizedWearDataSync(application),
) : AndroidViewModel(application) {
    /**
     * Costruttore richiesto da [androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory],
     * che lo cerca PER RIFLESSIONE con esattamente la firma (Application).
     *
     * Non basta il valore di default sul parametro secondario: i default di Kotlin non
     * generano un costruttore separato, quindi la factory non lo troverebbe e MainActivity
     * crasherebbe all'avvio con NoSuchMethodException. E' un contratto invisibile nel
     * codice sorgente, per questo e' esplicito qui e coperto da WearViewModelConstructorTest.
     */
    constructor(application: Application) : this(application, OptimizedWearDataSync(application))

    companion object {
        private const val TAG = "WearViewModel"
    }

    /**
     * I tocchi dati mentre il telefono non c'era.
     *
     * Prima venivano buttati via con una vibrazione di errore: chi voleva segnare una partita dal
     * solo polso e mandarla dopo non poteva, perche' non c'era un "dopo".
     */
    private val pending by lazy { PendingIntents(getApplication()) }

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount = _pendingCount.asStateFlow()

    /** Sequenza dell'arretrato spedito e in attesa di conferma, e quante voci comprendeva. */
    private var batchInVolo: Pair<Long, Int>? = null

    /** L'ultima partita raccontata dal telefono: da qui riparte il conto quando si resta soli. */
    private val ultimaNota by lazy { LastKnownMatch(getApplication()) }

    /** L'ultimo stato ricevuto, tenuto a parte perche' quello a schermo puo' essere locale. */
    private var statoDalTelefono: WearScoreState? = null

    // Stato v2: il telefono e' autoritativo, qui c'e' solo il testo da mettere a schermo.
    private val _scoreState = MutableStateFlow<WearScoreState?>(null)
    val scoreState = _scoreState.asStateFlow()

    /**
     * Visto un v2, non si torna indietro: lo stesso telefono continua a scrivere anche il v1 per
     * gli orologi non aggiornati, e i due formati si sovrascriverebbero a vicenda.
     */
    private var protocolV2Seen = false

    /**
     * Seminato con l'orologio di sistema, non da zero.
     *
     * Il telefono ricorda l'ultima sequenza vista per nodo e scarta cio' che non la supera.
     * Ripartendo da 1 dopo un riavvio dell'app, i primi tocchi verrebbero scartati come "gia'
     * visti", tanti quanti se ne erano fatti prima. Dal tempo corrente la monotonia attraversa i
     * riavvii.
     */
    private var intentSequence = System.currentTimeMillis()

    // Vuoti finche' il telefono non li manda: e' cosi' che la schermata sa di dover ripiegare su
    // "Squadra 1" nella lingua dell'orologio, invece di leggere a TalkBack un "TEAM 1" inglese.
    private val _team1Name = MutableStateFlow("")
    val team1Name = _team1Name.asStateFlow()
    private val _team2Name = MutableStateFlow("")
    val team2Name = _team2Name.asStateFlow()

    // Team Colors
    private val _team1Color = MutableStateFlow<Int?>(null)
    val team1Color = _team1Color.asStateFlow()
    private val _team2Color = MutableStateFlow<Int?>(null)
    val team2Color = _team2Color.asStateFlow()

    val connectionState = connectionManager.connectionState

    /** Il listener della capability non vede il Bluetooth che cade: lo stato va chiesto di nuovo. */
    fun refreshConnection() {
        viewModelScope.launch { connectionManager.refreshConnection() }
    }

    // Team Scores
    private val _team1Score = MutableStateFlow(0)
    val team1Score = _team1Score.asStateFlow()

    private val _team2Score = MutableStateFlow(0)
    val team2Score = _team2Score.asStateFlow()

    private val _matchTimer = MutableStateFlow("00:00")
    val matchTimer = _matchTimer.asStateFlow()
    private var matchTimerJob: Job? = null
    private var matchTimeInSeconds = 0L

    // Stato timer partita
    private var isMatchTimerRunning: Boolean = false

    // Keeper Timer
    private val _keeperTimer = MutableStateFlow<KeeperTimerState>(KeeperTimerState.Hidden)
    val keeperTimer = _keeperTimer.asStateFlow()

    private val _keeperProgress = MutableStateFlow(300)
    val keeperProgress = _keeperProgress.asStateFlow()

    private var keeperCountDownTimer: CountDownTimer? = null
    private var keeperTimerDuration = 300000L // 5 minutes default

    fun updateKeeperTimerDuration(millis: Long) {
        keeperTimerDuration = millis
        // Update progress if not running to reflect new duration immediately
        if (_keeperTimer.value is KeeperTimerState.Hidden || _keeperTimer.value is KeeperTimerState.Finished) {
            _keeperProgress.value = (millis / 1000).toInt()
        }
    }

    // Haptics
    private val vibrator = ContextCompat.getSystemService(application, Vibrator::class.java)

    // Player selection events
    private val _showPlayerSelection = MutableStateFlow<Int?>(null)
    val showPlayerSelection = _showPlayerSelection.asStateFlow()

    // Player data
    private val _allPlayers = MutableStateFlow<List<PlayerData>>(emptyList())
    val allPlayers = _allPlayers.asStateFlow()

    private val _team1Players = MutableStateFlow<List<PlayerData>>(emptyList())
    val team1Players = _team1Players.asStateFlow()

    private val _team2Players = MutableStateFlow<List<PlayerData>>(emptyList())
    val team2Players = _team2Players.asStateFlow()

    init {
        viewModelScope.launch {
            connectionManager.sendMessage(it.vantaggi.scoreboardessential.shared.communication.WearConstants.MSG_REQUEST_SYNC)
        }
    }

    fun clearPlayerSelectionEvent() {
        _showPlayerSelection.value = null
    }

    /** Replaces the cached roster pushed from the phone. */
    fun setAllPlayers(players: List<PlayerData>) {
        _allPlayers.value = players
    }

    fun updateScoresFromMobile(
        team1Score: Int,
        team2Score: Int,
    ) {
        if (protocolV2Seen) return
        _team1Score.value = team1Score
        _team2Score.value = team2Score
    }

    /**
     * Lo stato che il telefono manda. Vince sempre, tranne finche' il polso ha eventi suoi.
     *
     * La regola in una riga: **il polso mostra il proprio calcolo finche' ha tocchi non
     * confermati, altrimenti mostra quello che dice il telefono.** Senza, al ritorno del telefono
     * il punteggio tornerebbe visibilmente indietro -- il telefono manda cio' che sa, e cio' che
     * sa non comprende ancora l'arretrato -- per poi risalire un secondo dopo.
     *
     * Mentre un arretrato e' in viaggio non si ridisegna affatto: lo stato applicato e l'ack
     * partono dal telefono quasi insieme, e ricalcolare in quella finestra significherebbe
     * sommare l'arretrato a un registro che lo contiene gia'.
     */
    fun applyStateV2(state: WearScoreState) {
        protocolV2Seen = true
        statoDalTelefono = state
        ultimaNota.save(state.sportId, state.eventLog)
        when {
            batchInVolo != null -> Unit
            pending.size > 0 && rebuildLocalState() -> Unit
            else -> _scoreState.value = state
        }
    }

    // --- Score Management ---
    fun setTeamNames(
        team1Name: String,
        team2Name: String,
    ) {
        _team1Name.value = team1Name
        _team2Name.value = team2Name
    }

    fun setTeamColor(
        team: Int,
        color: Int,
    ) {
        if (team == 1) {
            _team1Color.value = color
        } else {
            _team2Color.value = color
        }
    }

    fun setScores(
        team1Score: Int,
        team2Score: Int,
    ) {
        _team1Score.value = team1Score
        _team2Score.value = team2Score
    }

    fun updateScore(
        team1: Int,
        team2: Int,
    ) {
        _team1Score.value = team1
        _team2Score.value = team2
        viewModelScope.launch {
            val data =
                mapOf(
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_TEAM1_SCORE to team1,
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_TEAM2_SCORE to team2,
                )
            connectionManager.sendData(
                path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_SCORE,
                data = data,
                urgent = true,
            )
        }
    }

    fun incrementScore(team: Int) {
        if (team != 1 && team != 2) return
        // A partita finita il motore ignora il punto, ma il telefono no: addRemotePoint scrive
        // lo stesso una riga e un'azione che puntano all'evento precedente, e un ANNULLA dopo
        // riapre la partita togliendo un punto vero. Offline il tocco finiva in coda come
        // "1 IN ATTESA". isClickable=false sul lato non basta a fermarlo: la guardia sta qui,
        // dove passano tutti i tocchi, TalkBack compreso.
        if (_scoreState.value?.matchOver == true) return
        sendScoreIntent(team, WearConstants.INTENT_POINT)
        if (protocolV2Seen) {
            // Nessuna vibrazione qui: la conferma la da' sendScoreIntent, e SOLO se il messaggio
            // e' davvero arrivato al telefono.
            if (_scoreState.value?.attributesScorer == true && _allPlayers.value.isNotEmpty()) {
                _showPlayerSelection.value = team
            }
            return
        }
        modifyScore(team, 1)
    }

    fun decrementScore(team: Int) {
        if (team != 1 && team != 2) return
        if (protocolV2Seen) {
            val tipo =
                if (_scoreState.value?.decrementIsUndo == true) {
                    WearConstants.INTENT_UNDO
                } else {
                    WearConstants.INTENT_CORRECTION
                }
            sendScoreIntent(team, tipo)
            return
        }
        modifyScore(team, -1)
    }

    /**
     * Ricostruisce un punto di partenza dal disco, per un orologio riavviato senza telefono.
     *
     * Le CAPACITA' non vengono salvate: sono una funzione dello sport, e ricavarle dal registro
     * degli sport e' piu' giusto che conservarne una copia che potrebbe invecchiare. L'elenco
     * degli sport invece resta vuoto, quindi a freddo e senza telefono il comando SPORT non
     * compare: sceglierlo richiede comunque qualcuno che accetti la richiesta.
     */
    private fun statoDaDisco(): WearScoreState? {
        val sportId = ultimaNota.sportId
        if (sportId.isBlank()) return null
        val capacita = SportRegistry.byId(sportId).capabilities
        return WearScoreState(
            side1Primary = "",
            side1Secondary = "",
            side2Primary = "",
            side2Secondary = "",
            periodLabel = "",
            hasClock = capacita.clock != ClockMode.NONE,
            hasAuxTimer = capacita.hasAuxCountdown,
            attributesScorer = capacita.attributesScorer,
            decrementIsUndo = capacita.decrementIsUndo,
            sportId = sportId,
            sportLabel = "",
            sportIds = emptyList(),
            sportLabels = emptyList(),
            matchInProgress = true,
            // A freddo non si sa: lo dira' il ricalcolo, che parte subito dopo.
            matchOver = false,
            eventLog = ultimaNota.eventLog,
        )
    }

    /**
     * Il punteggio calcolato al polso, valido finche' ci sono tocchi che il telefono non ha
     * ancora confermato.
     *
     * Non e' una seconda autorita': e' la STESSA operazione che fara' il telefono, fatta con lo
     * stesso codice di :core sugli stessi eventi -- il registro ricevuto per ultimo piu' la coda
     * locale. Per costruzione i due risultati non possono divergere, perche' non c'e' un secondo
     * algoritmo da tenere allineato: c'e' una sola funzione, chiamata due volte.
     *
     * Gli eventi locali si applicano SENZA tempo. Il tempo appartiene alla cronaca e non alla
     * regola: il punteggio non dipende da quando e' stato dato il tocco, e gli orari veri li
     * porta la coda quando parte davvero. Convertirli qui vorrebbe dire mantenere un secondo
     * orologio di partita al polso per un valore che qui nessuno legge.
     */
    private fun rebuildLocalState(): Boolean {
        val base = statoDalTelefono ?: return false
        // Senza sport non si puo' calcolare niente: succede con un telefono che parla una bozza
        // precedente del v2. Si dice di no, e chi ha chiesto mostrera' l'ultimo dato vero invece
        // di uno schermo vuoto -- e' il caso che il test ha scoperto.
        if (base.sportId.isBlank()) return false

        val rules = SportRegistry.byId(base.sportId)
        val engine = MatchEngine(rules)
        MatchLogCodec.decode(base.eventLog)?.let { engine.restoreLog(it) }
        pending.all().forEach { intento ->
            when (intento.kind) {
                WearConstants.INTENT_UNDO -> engine.undo()
                WearConstants.INTENT_CORRECTION -> engine.apply(ScoringEvent.Correction(side = intento.side))
                else -> engine.apply(ScoringEvent.Point(side = intento.side))
            }
        }
        val display = rules.display(engine.state)
        _scoreState.value =
            base.copy(
                side1Primary = display.side1Primary,
                side1Secondary = display.side1Secondary.orEmpty(),
                side2Primary = display.side2Primary,
                side2Secondary = display.side2Secondary.orEmpty(),
                periodLabel = display.periodLabel.orEmpty(),
                matchInProgress = engine.log.isNotEmpty(),
                matchOver = display.matchOver,
                // Il servizio cambia con i game segnati in coda: quello del telefono e' vecchio.
                servingSide = display.servingSide ?: 0,
            )
        return true
    }

    /**
     * Rilegge quante voci ci sono in coda.
     *
     * La coda e' `by lazy` e il conteggio parte da zero perche' costruire il ViewModel non deve
     * toccare il disco: sotto test l'Application e' finta e `getSharedPreferences` ritorna null.
     * Chiamarla all'avvio della schermata e' anche l'unico momento in cui serve davvero -- un
     * orologio riacceso con una partita in coda deve dirlo subito.
     */
    fun refreshPendingCount() {
        _pendingCount.value = pending.size
        // Un orologio riacceso a meta' partita, col telefono in borsa, deve ritrovare il
        // punteggio che aveva: non basta sapere quanti tocchi sono in coda.
        if (pending.size == 0) return
        if (statoDalTelefono == null) statoDalTelefono = statoDaDisco()
        rebuildLocalState()
    }

    /**
     * Spedisce l'arretrato in UN messaggio e aspetta la conferma prima di cancellarlo.
     *
     * Un messaggio solo perche' MessageClient non garantisce l'ordine: due tocchi invertiti
     * farebbero scartare il piu' vecchio dalla guardia sulla sequenza del telefono, cioe'
     * perdere un punto proprio mentre si recupera una partita intera.
     *
     * La coda si svuota su [onBatchAck], non sulla consegna: il messaggio raggiunge il servizio
     * del telefono anche ad app chiusa, e quel servizio non sa applicare niente.
     */
    fun flushPending() {
        val voci = pending.all()
        if (voci.isEmpty() || batchInVolo != null) return
        val seq = ++intentSequence
        batchInVolo = seq to voci.size
        viewModelScope.launch {
            val payload =
                DataMap().apply {
                    putString(
                        WearConstants.KEY_INTENT_BATCH,
                        voci.joinToString(WearConstants.BATCH_SEPARATOR) {
                            listOf(it.kind, it.side.toString(), it.atMillis.toString())
                                .joinToString(WearConstants.BATCH_FIELD_SEPARATOR)
                        },
                    )
                    putLong(WearConstants.KEY_SEQ, seq)
                }
            if (!connectionManager.sendMessage(WearConstants.MSG_INTENT_BATCH, payload.toByteArray())) {
                // Non e' partito: si riprova al prossimo collegamento, con una sequenza nuova.
                batchInVolo = null
            }
        }
    }

    /** Il telefono ha applicato: solo ora le voci consegnate escono dalla coda. */
    fun onBatchAck(seq: Long) {
        val inVolo = batchInVolo ?: return
        if (seq != inVolo.first) return
        pending.removeFirst(inVolo.second)
        batchInVolo = null
        _pendingCount.value = pending.size
        // Coda vuota: l'autorita' torna al telefono, e a schermo va cio' che ha mandato per ultimo.
        if (pending.size == 0 || !rebuildLocalState()) {
            statoDalTelefono?.let { _scoreState.value = it }
        }
    }

    /**
     * Chiede al telefono di cambiare sport. La decisione non e' di questo lato.
     *
     * Stesso contatore delle intenzioni di punteggio, perche' la sequenza e' UNA per nodo: se ne
     * usasse uno suo, i due flussi si scavalcherebbero e il telefono scarterebbe come "gia' visto"
     * un messaggio nuovo. La risposta e' lo stato v2 che torna: se lo sport cambia, la schermata
     * si ridisegna da sola; se il telefono rifiuta, non cambia niente e a dirlo e' il telefono.
     */
    fun requestSport(sportId: String) {
        val seq = ++intentSequence
        viewModelScope.launch {
            val payload =
                DataMap().apply {
                    putString(WearConstants.KEY_SPORT_ID, sportId)
                    putLong(WearConstants.KEY_SEQ, seq)
                }
            val consegnato = connectionManager.sendMessage(WearConstants.MSG_SPORT_INTENT, payload.toByteArray())
            if (consegnato) triggerShortVibration() else triggerFailureVibration()
        }
    }

    /**
     * Il tocco e' un'INTENZIONE, non uno stato. Va su MessageClient, che non coalesce: due punti a
     * un secondo di distanza restano due messaggi, mentre sullo stesso path DataClient il secondo
     * put sostituiva il primo e un punto spariva.
     *
     * Il lato resta SEMPRE 1 o 2; il significato del gesto viaggia in un campo suo. La prima
     * stesura caricava il lato di tre semantiche (negativi per la correzione, zero per
     * l'annullamento) e il telefono li scartava con la validazione del numero di squadra: sul
     * calcio, con entrambi i lati aggiornati, il tocco di sottrazione sarebbe diventato inerte.
     */
    private fun sendScoreIntent(
        side: Int,
        kind: String,
    ) {
        val seq = ++intentSequence
        // L'orario si prende ORA, non quando il messaggio partira': un tocco messo in coda e
        // consegnato due ore dopo deve restare il tocco delle 18.
        val quando = System.currentTimeMillis()
        viewModelScope.launch {
            val payload =
                DataMap().apply {
                    putInt(WearConstants.KEY_SIDE, side)
                    putString(WearConstants.KEY_INTENT_KIND, kind)
                    putLong(WearConstants.KEY_SEQ, seq)
                    putLong(WearConstants.KEY_AT_MILLIS, quando)
                }
            val consegnato = connectionManager.sendMessage(WearConstants.MSG_SCORE_INTENT, payload.toByteArray())
            if (consegnato) {
                triggerShortVibration()
                return@launch
            }
            // Non arrivato: si REGISTRA invece di sparire. Il gesto e' cieco -- sullo schermo non
            // cambia niente -- quindi il polso deve comunque distinguere "preso dal telefono" da
            // "tenuto da parte", e il conteggio in attesa lo dice a schermo.
            val accodato = pending.add(PendingIntent(kind, side, quando))
            _pendingCount.value = pending.size
            // Il gesto smette di essere cieco: il punteggio a schermo si aggiorna subito, calcolato
            // qui, e sara' identico a quello che il telefono calcolera' ricevendo la coda.
            if (accodato) rebuildLocalState()
            if (accodato) triggerBufferedVibration() else triggerFailureVibration()
        }
    }

    /**
     * Tocco tenuto da parte: un colpo lungo, diverso sia dalla conferma sia dall'errore.
     *
     * Non e' un fallimento e non deve suonare come tale -- il punto e' salvo, arrivera' al
     * telefono da solo -- ma non e' nemmeno la conferma normale, perche' il tabellone del
     * telefono in quel momento non si sta muovendo.
     */
    private fun triggerBufferedVibration() {
        vibrator?.vibrate(VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /**
     * Pattern diverso da [triggerShortVibration]: due colpi separati, che al polso non si
     * confondono con la conferma anche senza guardare.
     */
    private fun triggerFailureVibration() {
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 120, 60), -1))
    }

    private fun modifyScore(
        team: Int,
        delta: Int,
    ) {
        if (team != 1 && team != 2) return
        val currentScore = if (team == 1) _team1Score.value else _team2Score.value
        val newScore = (currentScore + delta).coerceAtLeast(0)

        if (newScore != currentScore) {
            val s1 = if (team == 1) newScore else _team1Score.value
            val s2 = if (team == 1) _team2Score.value else newScore
            updateScore(s1, s2)
            triggerShortVibration()

            // On a goal (increment), prompt to attribute a scorer if a roster is available.
            if (delta > 0 && _allPlayers.value.isNotEmpty()) {
                _showPlayerSelection.value = team
            }
        }
    }

    // --- Match Timer Management ---
    fun setMatchTimer(time: String) {
        matchTimerJob?.cancel() // Stop the internal timer
        _matchTimer.value = time
    }

    fun setMatchTimerMillis(millis: Long) {
        matchTimerJob?.cancel() // Stop internal timer if we get an update from mobile
        val minutes = (millis / 1000) / 60
        val seconds = (millis / 1000) % 60
        _matchTimer.value = String.format("%02d:%02d", minutes, seconds)
    }

    fun syncMatchTimer(
        millis: Long,
        isRunning: Boolean,
    ) {
        matchTimeInSeconds = millis / 1000
        isMatchTimerRunning = isRunning

        // Update display immediately
        val minutes = matchTimeInSeconds / 60
        val seconds = matchTimeInSeconds % 60
        _matchTimer.value = String.format("%02d:%02d", minutes, seconds)

        if (isRunning) {
            startMatchTimerInternal()
        } else {
            matchTimerJob?.cancel()
        }
    }

    // --- Keeper Timer Management ---
    fun setKeeperTimerState(newState: KeeperTimerState) {
        // Prevent restarting the timer if the state is basically the same
        val currentState = _keeperTimer.value
        if (currentState is KeeperTimerState.Running && newState is KeeperTimerState.Running) {
            val diff = kotlin.math.abs(currentState.secondsRemaining - newState.secondsRemaining)
            if (diff < 2) {
                // Ignore update if difference is less than 2 seconds to avoid jitter
                return
            }
        }

        keeperCountDownTimer?.cancel()
        vibrator?.cancel()
        _keeperTimer.value = newState
        // If the new state is Running, we need to start a countdown
        if (newState is KeeperTimerState.Running) {
            val duration = newState.secondsRemaining * 1000L
            _keeperProgress.value = newState.secondsRemaining
            keeperCountDownTimer =
                object : CountDownTimer(duration, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        val seconds = (millisUntilFinished / 1000).toInt()
                        _keeperTimer.value = KeeperTimerState.Running(seconds)
                        _keeperProgress.value = seconds
                    }

                    override fun onFinish() {
                        _keeperTimer.value = KeeperTimerState.Finished
                        _keeperProgress.value = 0
                        triggerStrongContinuousVibration()
                    }
                }.start()
        }
    }

    fun toggleKeeperTimer() {
        if (keeperTimer.value is KeeperTimerState.Running) {
            resetKeeperTimer()
        } else {
            startKeeperTimer()
        }
    }

    // --- Reset ---

    fun resetMatch(fromRemote: Boolean = false) {
        // Update local state without sending data if fromRemote is true
        if (fromRemote) {
            _team1Score.value = 0
            _team2Score.value = 0
        } else {
            updateScore(0, 0)
        }

        resetMatchTimer(fromRemote)
        resetKeeperTimer(fromRemote)

        if (!fromRemote) {
            // Also signal match reset to mobile
            viewModelScope.launch {
                val data = mapOf(it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_MATCH_ACTIVE to false)
                connectionManager.sendData(
                    path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_MATCH_STATE,
                    data = data,
                )
            }
        }
    }

    // --- Haptic Feedback ---
    private fun triggerShortVibration() {
        val effect = VibrationEffect.createWaveform(HapticFeedbackManager.PATTERN_CONFIRM, -1)
        vibrator?.vibrate(effect)
    }

    private fun triggerStrongContinuousVibration() {
        val effect = VibrationEffect.createWaveform(HapticFeedbackManager.PATTERN_ALERT, -1)
        vibrator?.vibrate(effect)
    }

    // --- Data Synchronization ---

    fun toggleTimer() {
        isMatchTimerRunning = !isMatchTimerRunning
        if (isMatchTimerRunning) {
            startMatchTimerInternal()
        } else {
            pauseMatchTimerInternal()
        }
        viewModelScope.launch {
            val data =
                mapOf(
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_TIMER_MILLIS to matchTimeInSeconds * 1000,
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_TIMER_RUNNING to isMatchTimerRunning,
                )
            connectionManager.sendData(
                path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_TIMER_STATE,
                data = data,
            )
        }
    }

    private fun startMatchTimerInternal() {
        matchTimerJob?.cancel()
        matchTimerJob =
            viewModelScope.launch {
                val startTime = System.currentTimeMillis() - (matchTimeInSeconds * 1000)
                while (isMatchTimerRunning) {
                    val now = System.currentTimeMillis()
                    val diff = now - startTime
                    matchTimeInSeconds = diff / 1000

                    val minutes = matchTimeInSeconds / 60
                    val seconds = matchTimeInSeconds % 60
                    _matchTimer.value = String.format("%02d:%02d", minutes, seconds)

                    // drift correction
                    val nextSecond = (matchTimeInSeconds + 1) * 1000
                    val delayMillis = nextSecond - diff
                    delay(if (delayMillis > 0) delayMillis else 100L)
                }
            }
    }

    private fun pauseMatchTimerInternal() {
        matchTimerJob?.cancel()
    }

    fun resetMatchTimer(fromRemote: Boolean = false) {
        matchTimeInSeconds = 0L
        isMatchTimerRunning = false
        matchTimerJob?.cancel()
        _matchTimer.value = "00:00"

        if (!fromRemote) {
            viewModelScope.launch {
                val data =
                    mapOf(
                        it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_TIMER_MILLIS to 0L,
                        it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_TIMER_RUNNING to false,
                    )
                connectionManager.sendData(
                    path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_TIMER_STATE,
                    data = data,
                )
            }
        }
    }

    private fun startKeeperTimer() {
        keeperCountDownTimer?.cancel()
        val totalSeconds = (keeperTimerDuration / 1000).toInt()
        _keeperTimer.value = KeeperTimerState.Running(totalSeconds)
        _keeperProgress.value = totalSeconds

        keeperCountDownTimer =
            object : CountDownTimer(keeperTimerDuration, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val seconds = (millisUntilFinished / 1000).toInt()
                    _keeperTimer.value = KeeperTimerState.Running(seconds)
                    _keeperProgress.value = seconds
                }

                override fun onFinish() {
                    _keeperTimer.value = KeeperTimerState.Finished
                    _keeperProgress.value = 0
                    triggerStrongContinuousVibration()
                }
            }.start()

        triggerShortVibration()

        // Sync keeper timer start
        viewModelScope.launch {
            val data =
                mapOf(
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_KEEPER_MILLIS to keeperTimerDuration,
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_KEEPER_RUNNING to true,
                )
            connectionManager.sendData(
                path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_KEEPER_TIMER,
                data = data,
            )
        }
    }

    fun resetKeeperTimer(fromRemote: Boolean = false) {
        keeperCountDownTimer?.cancel()
        vibrator?.cancel()
        _keeperTimer.value = KeeperTimerState.Hidden
        _keeperProgress.value = (keeperTimerDuration / 1000).toInt()

        if (!fromRemote) {
            triggerShortVibration()

            // Sync keeper timer reset
            viewModelScope.launch {
                val data =
                    mapOf(
                        it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_KEEPER_MILLIS to 0L,
                        it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_KEEPER_RUNNING to false,
                    )
                connectionManager.sendData(
                    path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_KEEPER_TIMER,
                    data = data,
                )
            }
        }
    }

    override fun onCleared() {
        matchTimerJob?.cancel()
        keeperCountDownTimer?.cancel()
        vibrator?.cancel()
        connectionManager.cleanup()
        super.onCleared()
    }
}
