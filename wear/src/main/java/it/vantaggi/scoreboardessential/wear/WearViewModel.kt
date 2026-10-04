package it.vantaggi.scoreboardessential.wear

import android.app.Application
import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.DataMap
import it.vantaggi.scoreboardessential.core.ClockMode
import it.vantaggi.scoreboardessential.core.MatchEngine
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.RacketRules
import it.vantaggi.scoreboardessential.core.ScoringEvent
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.shared.HapticFeedbackManager
import it.vantaggi.scoreboardessential.shared.PlayerData
import it.vantaggi.scoreboardessential.shared.communication.ConnectionState
import it.vantaggi.scoreboardessential.shared.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.shared.communication.WearConstants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

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
    /**
     * Quale giocatore della squadra al servizio batte, in coppia: 1 il primo (un pallino), 2 il
     * secondo (due pallini). 0 nel singolare, quando nessuno serve e con un telefono che non
     * manda la chiave: in quel caso, con un servizio in corso, il pallino resta uno.
     */
    val servingSlot: Int = 0,
    /**
     * L'id dell'ultimo arretrato che il telefono ha applicato (L5): se l'ack si perde, e' da qui che
     * il polso capisce di poter togliere le voci dalla coda. 0 da un telefono che non lo manda.
     */
    val lastBatchId: Long = 0L,
    /**
     * L'identita' della partita del telefono (L5): vuota se non ne ha ancora una o se il telefono non
     * la manda. La coda la ricorda quando nasce, e il batch la rimanda.
     */
    val matchUuid: String = "",
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
                servingSlot = dataMap.getInt(WearConstants.KEY_SERVING_SLOT, 0),
                lastBatchId = idDelBatch(dataMap),
                matchUuid = dataMap.getString(WearConstants.KEY_MATCH_UUID, ""),
            )
        }

        /** Long come lo scrive il telefono; un Int (o altro) non deve far lanciare la lettura dello stato. */
        internal fun idDelBatch(dataMap: DataMap): Long =
            when (val valore = dataMap.get<Any>(WearConstants.KEY_LAST_BATCH_ID)) {
                is Long -> valore
                is Int -> valore.toLong()
                else -> 0L
            }
    }
}

sealed class KeeperTimerState {
    object Hidden : KeeperTimerState()

    data class Running(
        val secondsRemaining: Int,
    ) : KeeperTimerState()

    /**
     * Messo in pausa dal telefono (notifica o pulsante). Uno stato a se': prima la pausa arrivava
     * come un azzeramento con il residuo, e il residuo diventava la durata dei conti successivi.
     */
    data class Paused(
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
    // Iniettabile per la riga di stato: "da 10 secondi" si prova spostando l'orologio, non
    // aspettando. Il tempo dei test e' quello del loro scheduler, cosi' delay e orologio vanno
    // d'accordo.
    private val orologio: () -> Long = System::currentTimeMillis,
    // Iniettabile per le ricevute del tocco: nei test e' un finto che registra i pattern.
    private val haptics: WearHaptics = VibratoreWearHaptics.di(application),
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

        /**
         * Quanto aspetta il polso lo stato che dice "preso", dopo che il messaggio e' stato
         * consegnato. Oltre, il tocco e' NON CONFERMATO: dice questo e basta, senza rimetterlo in
         * coda. Senza id idempotente (VALIDAZIONE L5) un secondo invio potrebbe contare due volte.
         */
        internal const val SCADENZA_RICEVUTA_MS = 2_500L

        /**
         * Quanto aspetta il polso una risposta all'arretrato. Oltre, il tentativo e' scaduto: lo
         * schermo si ridisegna e il blocco si puo' rimandare (con lo stesso id) alla prossima
         * occasione. Senza, un ack e un NACK persi lasciavano il quadrante fermo finche' il
         * ViewModel non veniva distrutto (L5).
         */
        internal const val TIMEOUT_BATCH_MS = 15_000L

        /**
         * Quanto deve passare fra il tick del tocco e la conferma di lato dello stesso tocco. Il
         * vocabolario conta gli impulsi: un tick subito seguito dall'impulso della conferma
         * sinistra si sente come due colpi, cioe' "destra".
         */
        internal const val DISTANZA_DAL_TICK_MS = 250L

        /** Poco sopra la verifica di 2s: oltre, la richiesta al Data Layer si da' per persa. */
        internal const val TIMEOUT_RICHIESTA_MS = StatoFiducia.DURATA_VERIFICA_MS + 500L

        /** Quanto aspetta CHIUSURA... un v2 a partita non cominciata, prima di dire NON CONFERMATA. */
        internal const val DURATA_ATTESA_CHIUSURA_MS = 10_000L

        /**
         * Tetto di CAMBIO SPORT...: copre l'invio e i 2,5s di attesa dello stato. Di norma la riga si
         * chiude prima, perche' arriva lo stato col nuovo sport o scade la ricevuta.
         */
        internal const val DURATA_CAMBIO_SPORT_MS = 10_000L

        /** Quanto resta offerto CHI? dopo un gol confermato, poi il bersaglio torna al menu. */
        internal const val DURATA_FINESTRA_CHI_MS = 8_000L
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

    /**
     * L'arretrato spedito e in attesa di risposta (id e quante voci), la copia in memoria di quello
     * sul disco ([PendingIntents.batchInVolo]): resta finche' non arriva ack, NACK o lo stato col
     * suo id, ANCHE oltre [TIMEOUT_BATCH_MS]. Scaduto non vuol dire dimenticato: l'id resta lo
     * stesso, e il rinvio dice al telefono "questo l'hai gia' applicato?" invece di contarlo due volte.
     */
    private var batchInVolo: PendingIntents.BatchInVolo? = null

    /** Quando e' partito l'ultimo tentativo; null se e' scaduto, o se viene da una vita precedente del processo. */
    private var batchInVoloDal: Long? = null

    /** La sequenza dell'ultimo tentativo: un ack di un telefono non aggiornato porta solo questa. */
    private var seqUltimoBatch = 0L

    private var timeoutBatchJob: Job? = null

    /** Un arretrato in volo che non e' scaduto: finche' c'e', non se ne spedisce un altro e lo schermo non si ridisegna. */
    private fun batchAttivo(): Boolean = batchInVolo != null && batchInVoloDal != null

    private val _rifiutati = MutableStateFlow(0)

    /**
     * Quanti tocchi il telefono ha rifiutato (NACK definitivo); 0 se non ne ha rifiutati. Stanno da
     * parte, su disco ([PendingIntents.rifiutate]): non si rimandano mai da soli, nemmeno alla
     * partita dopo, e la riga dice "n RIFIUTATI" finche' l'utente non li scarta dal menu
     * ([scartaCoda]). I tocchi nuovi non li raggiungono: aprono una coda nuova, che si calcola, si
     * mostra e si spedisce normalmente.
     */
    val rifiutati = _rifiutati.asStateFlow()

    /**
     * Un tocco consegnato al telefono e non ancora confermato. Consegnato non vuol dire preso: con
     * l'app del telefono chiusa sendMessage risponde true lo stesso e il punto si perde (VALIDAZIONE
     * L5). La conferma e' uno stato v2 che arriva con un registro di lunghezza diversa.
     */
    private class Ricevuta(
        val side: Int,
        val kind: String,
        /** Lunghezza del registro quando il tocco e' partito: quella con cui confrontare gli stati. */
        var registroBase: Int,
        /** Quando e' suonato il tick di questo tocco, se e' suonato: la conferma non deve toccarlo. */
        val tickAlle: Long? = null,
        /** Il tocco era un gol di uno sport che attribuisce: alla conferma si offre CHI?. */
        val chiediMarcatore: Boolean = false,
    ) {
        var scadenza: Job? = null
    }

    /** Le ricevute aperte, la piu' vecchia in testa. Si tocca solo dal thread principale. */
    private val ricevute = ArrayDeque<Ricevuta>()

    /** Il cambio sport chiesto e in attesa dello stato che porta lo sport nuovo. */
    private class RicevutaSport(
        val sportId: String,
    ) {
        var scadenza: Job? = null
    }

    private var ricevutaSport: RicevutaSport? = null

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
     * Dal telefono e' arrivato un punteggio v1 e nessun v2: e' un telefono non aggiornato, il solo
     * caso in cui il tocco va come punteggio assoluto invece che come intenzione.
     */
    private var v1DalTelefono = false

    /**
     * Almeno uno stato v2 e' arrivato DAL VIVO, da quando il ViewModel c'e'. Lo stato letto dal disco
     * (o dai DataItem al risveglio) e' una copia che puo' essere vecchia: la lunghezza del suo
     * registro non e' una base per le ricevute, e il tocco a freddo non ne apre una finche' il
     * telefono non ha parlato davvero (altrimenti un NON CONFERMATO o una conferma sbagliata).
     */
    private var statoVivoVisto = false

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

    // --- Riga di stato: gli input di StatoFiducia ---
    //
    // Il ViewModel non decide cosa dire, decide COSA SA: e' StatoFiducia a scegliere la frase. Il
    // tempo entra qui e solo qui, come istanti; la funzione pura riceve durate.

    /** Fine della verifica del collegamento: zero se nessuna e' in corso. */
    private var verificaFinoA = 0L

    /** Da quando la coda e' non vuota con il telefono raggiungibile; null se non lo e'. */
    private var codaDaCollegatiDal: Long? = null

    private var transitorio: Transitorio? = null
    private var transitorioFinoA = 0L

    /**
     * L'ora dell'ultimo stato v2 arrivato dal vivo. Dopo un riavvio si rilegge dal disco (in
     * [refreshPendingCount]), non in costruzione: costruire il ViewModel non deve toccare il disco.
     */
    private var ultimoStatoVivoAlle: Long? = null

    private var fiduciaJob: Job? = null

    private val _statoFiducia = MutableStateFlow<Frase>(Frase.TieniMeno)

    /** La frase della riga in basso, gia' scelta: la schermata la traduce e la colora. */
    val statoFiducia = _statoFiducia.asStateFlow()

    /**
     * Il collegamento e' stato determinato almeno una volta: una richiesta e' finita, con la
     * risposta o scaduta (a quel punto la riga dice quello che sa, e non c'e' piu' un "non so ancora"
     * da proteggere). Si accende una volta sola e non si spegne piu': e' quello che distingue
     * l'avvio da un rinfresco.
     */
    private var collegamentoNoto = false

    /** La richiesta di collegamento in volo: finche' e' attiva non se ne lancia un'altra. */
    private var refreshJob: Job? = null

    /**
     * Il listener della capability non vede il Bluetooth che cade: lo stato va chiesto di nuovo.
     *
     * Per al massimo 2s la riga non dice "scollegato", ma SOLO finche' il collegamento non e' mai
     * stato determinato: all'avvio ConnectionState vale Disconnected finche' non arriva la
     * risposta, e senza questo la riga lampeggerebbe a ogni accensione. A stato gia' noto la
     * richiesta e' un rinfresco silenzioso: nasconderlo ogni 15s e a ogni onResume farebbe
     * sparire SCOLLEGATO e IN CODA per un attimo, e TalkBack li rileggerebbe ogni volta.
     *
     * Una richiesta alla volta, e mai oltre poco piu' dei 2s: se il Data Layer non risponde, la
     * prossima deve poter partire.
     */
    fun refreshConnection() {
        if (refreshJob?.isActive == true) return
        if (!collegamentoNoto) {
            verificaFinoA = orologio() + StatoFiducia.DURATA_VERIFICA_MS
            ricalcolaFiducia()
        }
        refreshJob =
            viewModelScope.launch {
                withTimeoutOrNull(TIMEOUT_RICHIESTA_MS) { connectionManager.refreshConnection() }
                collegamentoNoto = true
                verificaFinoA = 0L
                ricalcolaFiducia()
            }
    }

    /**
     * Partita in corso: il v2 dice che e' cominciata e non finita; senza v2, un tocco in coda
     * vuol dire che qualcuno sta giocando.
     */
    val partitaInCorso: Boolean
        get() = _scoreState.value?.let { it.matchInProgress && !it.matchOver } ?: (_pendingCount.value > 0)

    /** Il controllo periodico della schermata accesa: a partita finita il collegamento non interessa. */
    fun refreshConnectionSePartitaInCorso() {
        if (partitaInCorso) refreshConnection()
    }

    /** Un messaggio che dura 2-3s sopra la riga di stato, poi la riga torna da sola. */
    fun mostraTransitorio(
        messaggio: Transitorio,
        durataMs: Long = StatoFiducia.DURATA_TRANSITORIO_MS,
    ) {
        transitorio = messaggio
        transitorioFinoA = orologio() + durataMs
        ricalcolaFiducia()
    }

    /** La chiusura dal polso e' in attesa del v2 che dice matchInProgress=false. */
    private var chiusuraInAttesa = false

    /**
     * Il registro del telefono non era vuoto quando e' partito il comando. Senza questo, il primo
     * v2 a partita non cominciata (uno qualunque, anche vecchio) passerebbe per la conferma: la
     * chiusura vale solo se il registro e' passato da non vuoto a vuoto DOPO il comando.
     */
    private var registroPienoAlComando = false
    private var chiusuraJob: Job? = null

    /**
     * La fine partita scelta dal menu. Oltre al comando (sotto) c'e' cio' che il polso DICE mentre
     * aspetta: CHIUSURA... finche' il telefono non
     * risponde con un v2 a partita non cominciata ([applyStateV2]), e dopo
     * [DURATA_ATTESA_CHIUSURA_MS] senza risposta NON CONFERMATA, in ambra. Non dice "non chiusa":
     * il comando e' partito e non si ritira, puo' ancora arrivare. Non si riprova da soli: un
     * nuovo tentativo senza id idempotente e' proprio cio' che VALIDAZIONE L5 sconsiglia.
     *
     * Con un v2 la chiusura e' un'INTENZIONE con sequenza ([WearConstants.INTENT_END_MATCH]): il
     * telefono esegue endMatch sul PROPRIO stato, una volta sola per sequenza. Il polso non scrive
     * piu' lo 0-0 v1 ne' i timer azzerati, che svuotavano il motore del telefono PRIMA di
     * endMatch (nel calcio la partita andava persa o salvata 0-0) e arrivavano in ordine
     * qualunque. Senza un v2 (telefono non aggiornato) resta il v1 di sempre, e il MATCH_STATE
     * parte urgente: un DataItem non urgente puo' arrivare al telefono minuti dopo i 10 secondi, e
     * chiuderebbe la partita a gioco ripreso o quella successiva.
     *
     * Con punti in coda, un arretrato in volo o ricevute aperte non si chiude e ritorna falso: il
     * telefono non li ha (o non ha confermato di averli), e la chiusura (o il reset, nel v1)
     * separerebbe la partita giocata al polso da quella del telefono solo a meta': due partite si
     * fonderebbero. Le ricevute aperte sono punti consegnati e non ancora confermati:
     * MessageClient non garantisce l'ordine, e un end_match che superasse l'ultimo gol lo farebbe
     * perdere. E' la stessa regola del menu ("Prima consegna n punti"), qui perche' il ViewModel
     * non deve dipendere da chi lo chiama.
     */
    fun chiudiPartita(): Boolean {
        if (_pendingCount.value > 0 || batchInVolo != null || ricevute.isNotEmpty()) return false
        registroPienoAlComando = statoDalTelefono?.matchInProgress == true
        chiusuraInAttesa = true
        mostraTransitorio(Transitorio.Chiusura, DURATA_ATTESA_CHIUSURA_MS)
        chiusuraJob?.cancel()
        chiusuraJob =
            viewModelScope.launch {
                delay(DURATA_ATTESA_CHIUSURA_MS)
                chiusuraInAttesa = false
                mostraTransitorio(Transitorio.ChiusuraNonConfermata)
            }
        if (protocolV2Seen) {
            // Il gol offerto da CHI? non esiste piu'.
            chiudiFinestraChi()
            inviaFinePartita()
        } else {
            resetMatch(urgent = true)
        }
        return true
    }

    /**
     * L'intenzione di chiusura: una sequenza nuova (la stessa dei punti, una per nodo) e nessun
     * lato. Non passa da [sendScoreIntent]: la chiusura non ha ricevuta di punto, non va mai in
     * coda (con la coda piena non parte) e non si rimette in coda se non arriva: chi chiude dice
     * subito che non e' partita, invece di far credere per 10 secondi che il telefono ci stia
     * pensando.
     */
    private fun inviaFinePartita() {
        val seq = ++intentSequence
        val quando = System.currentTimeMillis()
        viewModelScope.launch {
            val payload =
                DataMap().apply {
                    putString(WearConstants.KEY_INTENT_KIND, WearConstants.INTENT_END_MATCH)
                    putLong(WearConstants.KEY_SEQ, seq)
                    putLong(WearConstants.KEY_AT_MILLIS, quando)
                }
            if (!connectionManager.sendMessage(WearConstants.MSG_SCORE_INTENT, payload.toByteArray())) {
                chiusuraInAttesa = false
                chiusuraJob?.cancel()
                triggerFailureVibration()
                mostraTransitorio(Transitorio.ChiusuraNonConfermata)
            }
        }
    }

    /**
     * Rifa' la frase e si rimette in attesa del prossimo istante in cui potrebbe cambiare da sola:
     * la fine della verifica, la fine del transitorio, i 10s della coda da collegati. Fra due
     * eventi niente gira: senza una scadenza la riga resterebbe ferma a "INVIO" per sempre.
     */
    private fun ricalcolaFiducia() {
        fiduciaJob?.cancel()
        fiduciaJob = null
        var prossima = pubblicaFiducia() ?: return
        fiduciaJob =
            viewModelScope.launch {
                while (true) {
                    delay((prossima - orologio()).coerceAtLeast(1L))
                    prossima = pubblicaFiducia() ?: break
                }
            }
    }

    /** Pubblica la frase di adesso; ritorna l'istante della prossima scadenza, o null se non ce n'e'. */
    private fun pubblicaFiducia(): Long? {
        val ora = orologio()
        val collegato = connectionState.value is ConnectionState.Connected
        val inCoda = _pendingCount.value
        // Il conto parte quando la coda e' non vuota E il telefono e' raggiungibile, e si azzera
        // appena una delle due smette: "da 10 secondi" vale solo per chi aspetta da collegato.
        if (collegato && inCoda > 0) {
            if (codaDaCollegatiDal == null) codaDaCollegatiDal = ora
        } else {
            codaDaCollegatiDal = null
        }
        val daCollegati = codaDaCollegatiDal?.let { ora - it } ?: 0L
        val verificaInCorso = ora < verificaFinoA
        val transitorioAttivo = transitorio.takeIf { ora < transitorioFinoA }
        val stato = _scoreState.value
        _statoFiducia.value =
            StatoFiducia.calcola(
                InputFiducia(
                    collegato = collegato,
                    verificaInCorso = verificaInCorso,
                    inCoda = inCoda,
                    collegatoConCodaDaMs = daCollegati,
                    matchOver = stato?.matchOver == true,
                    decrementIsUndo = stato?.decrementIsUndo == true,
                    ultimoStatoVivoAlle = ultimoStatoVivoAlle,
                    transitorio = transitorioAttivo,
                    rifiutati = _rifiutati.value,
                ),
            )
        return listOfNotNull(
            verificaFinoA.takeIf { verificaInCorso },
            transitorioFinoA.takeIf { transitorioAttivo != null },
            codaDaCollegatiDal?.plus(StatoFiducia.SOGLIA_NON_CONSEGNATI_MS)?.takeIf { daCollegati < StatoFiducia.SOGLIA_NON_CONSEGNATI_MS },
        ).minOrNull()
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

    // Stato timer partita. Il quadrante lo legge (cronometro bianco se corre, grigio se fermo): la
    // proprieta' resta quella di prima, con un flusso dietro che tiene i due in accordo.
    private val _matchTimerRunning = MutableStateFlow(false)
    val matchTimerRunning = _matchTimerRunning.asStateFlow()
    private var isMatchTimerRunning: Boolean = false
        set(value) {
            field = value
            _matchTimerRunning.value = value
        }

    // Keeper Timer
    private val _keeperTimer = MutableStateFlow<KeeperTimerState>(KeeperTimerState.Hidden)
    val keeperTimer = _keeperTimer.asStateFlow()

    private val _keeperProgress = MutableStateFlow(300)
    val keeperProgress = _keeperProgress.asStateFlow()

    // Il massimo dell'anello: la durata in secondi. Nel layout era fisso a 300, quindi con 600 s
    // l'anello restava pieno per cinque minuti e con 60 s partiva dal 20%.
    private val _keeperDurationSeconds = MutableStateFlow(300)
    val keeperDurationSeconds = _keeperDurationSeconds.asStateFlow()

    private var keeperCountDownTimer: CountDownTimer? = null
    private var keeperTimerDuration = 300000L // 5 minutes default

    fun updateKeeperTimerDuration(millis: Long) {
        keeperTimerDuration = millis
        _keeperDurationSeconds.value = (millis / 1000).toInt()
        // Update progress if not running to reflect new duration immediately
        if (_keeperTimer.value is KeeperTimerState.Hidden || _keeperTimer.value is KeeperTimerState.Finished) {
            _keeperProgress.value = (millis / 1000).toInt()
        }
    }

    /**
     * L'offerta CHI?: il lato che ha segnato e il punteggio dopo quel gol. Il punteggio si fissa
     * ora e non quando si tocca, perche' l'intestazione della lista deve dire QUALE gol si sta
     * attribuendo anche se nel frattempo ne e' arrivato un altro dal telefono.
     */
    data class FinestraChi(
        val lato: Int,
        val risultato: String,
    )

    private val _finestraChi = MutableStateFlow<FinestraChi?>(null)

    /** Non e' null per [DURATA_FINESTRA_CHI_MS] dopo un gol confermato: la schermata offre CHI?. */
    val finestraChi = _finestraChi.asStateFlow()
    private var finestraChiJob: Job? = null

    /**
     * Il registro dello stato con cui e' nata l'offerta. Il telefono attribuisce all'ultimo punto
     * del lato senza marcatore, senza limite di eta': se il registro cambia (un annullamento, un
     * nome dato dal telefono, una partita nuova) il gol offerto non e' piu' quello, e il nome
     * andrebbe a un gol vecchio.
     */
    private var registroDellOfferta: String? = null

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
        // La frase dipende da tre cose che cambiano per conto loro: lo stato, la coda, il
        // collegamento. Ognuna, cambiando, la rifa'.
        viewModelScope.launch { _scoreState.collect { ricalcolaFiducia() } }
        viewModelScope.launch { _pendingCount.collect { ricalcolaFiducia() } }
        viewModelScope.launch { _rifiutati.collect { ricalcolaFiducia() } }
        viewModelScope.launch {
            connectionState.collect {
                ricalcolaFiducia()
                // La scelta del marcatore parte solo verso un telefono che ascolta: da scollegati
                // l'offerta non resta in piedi.
                if (it !is ConnectionState.Connected) chiudiFinestraChi()
            }
        }
        // Il ViewModel nasce insieme all'app: fino alla prima risposta sul collegamento non si
        // sa, e non si dice "scollegato". Anche qui per al massimo 2s.
        verificaFinoA = orologio() + StatoFiducia.DURATA_VERIFICA_MS
        ricalcolaFiducia()
    }

    /** CHI? finisce: scaduta, usata, o non piu' vera (un annullamento, il telefono che cade). */
    fun chiudiFinestraChi() {
        finestraChiJob?.cancel()
        finestraChiJob = null
        registroDellOfferta = null
        _finestraChi.value = null
    }

    /** Replaces the cached roster pushed from the phone. */
    fun setAllPlayers(players: List<PlayerData>) {
        _allPlayers.value = players
        // Senza nomi la lista avrebbe solo SALTA: l'offerta non ha piu' niente da offrire.
        if (players.isEmpty()) chiudiFinestraChi()
    }

    fun updateScoresFromMobile(
        team1Score: Int,
        team2Score: Int,
    ) {
        if (protocolV2Seen) return
        v1DalTelefono = true
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
     *
     * [dalVivo] e' falso per uno stato riletto dai DataItem al risveglio: e' una copia, non un
     * momento in cui il telefono ha parlato, e l'ora di "SCOLLEGATO · 18:42" non deve diventare
     * quella del risveglio.
     */
    fun applyStateV2(
        state: WearScoreState,
        dalVivo: Boolean = true,
    ) {
        protocolV2Seen = true
        // Lo sport e' cambiato: SPORT NON CAMBIATO, a ricevuta scaduta, non e' piu' vero.
        val sportPrima = statoDalTelefono?.sportId
        if (sportPrima != null && sportPrima != state.sportId && transitorio == Transitorio.SportNonCambiato) {
            transitorio = null
        }
        statoDalTelefono = state
        if (dalVivo) {
            statoVivoVisto = true
            ultimoStatoVivoAlle = orologio()
            // Solo un telefono che parla adesso puo' dire "preso": una rilettura dei DataItem al
            // risveglio e' una copia, e chiuderebbe una ricevuta con uno stato vecchio.
            chiudiRicevute(state)
        }
        // Dopo le ricevute: un gol nuovo ha appena aperto la sua offerta, col registro di questo
        // stato, e qui non si tocca. Tutto il resto fa decadere quella che c'era.
        chiudiFinestraChiSeNonVera(state)
        // L'ora sul disco la scrive WearDataLayerService, che c'e' anche ad app chiusa.
        ultimaNota.save(state.sportId, state.eventLog, state.servingSlot)
        // Lo stato porta l'id dell'ultimo arretrato applicato: se e' quello in volo, e' un ack che non
        // ha avuto bisogno di arrivare (L5, ack perso). Dopo chiudiRicevute: lo stato dell'arretrato
        // non e' la conferma di un tocco dal vivo, e finche' batchInVolo c'e' non la chiude.
        batchInVolo?.takeIf { state.lastBatchId > 0L && state.lastBatchId == it.id }?.let { batchApplicato(it) }
        when {
            batchAttivo() -> Unit
            pending.size > 0 && rebuildLocalState() -> Unit
            else -> _scoreState.value = state
        }
        // Il telefono ha chiuso: CHIUSURA... ha finito, e NON CONFERMATA non deve piu' scattare.
        // Si toglie solo CHIUSURA...: un altro messaggio (un tocco non confermato) non e' suo.
        if (chiusuraInAttesa && registroPienoAlComando && !state.matchInProgress) {
            chiusuraInAttesa = false
            chiusuraJob?.cancel()
            if (transitorio is Transitorio.Chiusura) transitorio = null
        }
        // Lo stato puo' restare identico mentre l'ora cambia: il collector non se ne accorgerebbe.
        ricalcolaFiducia()
        // Un telefono che parla adesso e' raggiungibile, e una coda senza un tentativo vivo (un NACK
        // passeggero, un tentativo scaduto, voci rimaste dopo un blocco) non ha altro da aspettare:
        // connectionState e' uno StateFlow e non rimette Connected se lo era gia' (L5, D3).
if (dalVivo && pending.size > 0 && !batchAttivo()) flushPending()
    }

    /**
     * Quanti eventi ha il registro; null se non c'e' o non si legge. Il telefono v2 il registro lo
     * scrive sempre (anche vuoto e' una stringa col suo prefisso, non ""), quindi una stringa vuota
     * e' un registro che manca. Senza non c'e' niente da confrontare: una ricevuta non si
     * chiuderebbe mai e ogni tocco, anche preso, direbbe NON CONFERMATO.
     */
    private fun lunghezzaRegistro(stato: WearScoreState?): Int? =
        stato?.eventLog?.takeIf { it.isNotEmpty() }?.let { MatchLogCodec.decode(it)?.size }

    private fun patternConferma(ricevuta: Ricevuta): LongArray =
        if (ricevuta.kind == WearConstants.INTENT_POINT) {
            WearPatterns.conferma(ricevuta.side)
        } else {
            // Annullamento e correzione: lo stesso schema per i due lati.
            WearPatterns.ANNULLAMENTO
        }

    /**
     * Il telefono ha parlato: un registro di lunghezza diversa da quella del tocco vuol dire che
     * l'ha applicato (un punto allunga il registro, un annullamento lo accorcia).
     *
     * Si chiude la ricevuta piu' vecchia, non tutte: due tocchi ravvicinati hanno bisogno di due
     * stati. Se la distanza e' piu' d'uno (due eventi in un solo DataItem, perche' il Data Layer
     * puo' coalescere) se ne chiudono altrettante, e le rimaste ripartono dal registro nuovo,
     * altrimenti il prossimo stato identico le chiuderebbe a vuoto.
     *
     * Limite noto: un punto segnato dal telefono nello stesso istante chiude per errore la ricevuta
     * del polso. Sara' esatto quando L5 mettera' nello stato la sequenza dell'ultimo intento.
     *
     * Mentre un arretrato e' in volo non si chiude niente: il suo stato allunga il registro di
     * tutte le voci insieme, e passerebbe per la conferma del tocco dal vivo. Un tocco consegnato
     * in quella finestra resta aperto e, se dopo l'ack nessuno stato lo chiude, dice NON
     * CONFERMATO: l'errore dalla parte sicura, fino a L5. Un registro che non c'e' o non si legge
     * non chiude nessuna ricevuta.
     */
    private fun chiudiRicevute(state: WearScoreState) {
        val registro = lunghezzaRegistro(state)
        val piuVecchia = ricevute.firstOrNull()
        if (registro != null && piuVecchia != null && !batchAttivo()) {
            val distanza = abs(registro - piuVecchia.registroBase)
            if (distanza > 0) {
                // Il registro e' CRESCIUTO: un gol ha allungato la lista. Se si e' accorciato e'
                // stato un annullamento (del telefono, di solito) e non c'e' un gol da attribuire.
                val cresciuto = registro > piuVecchia.registroBase
                val chiuse = mutableListOf<Ricevuta>()
                repeat(minOf(distanza, ricevute.size)) {
                    chiuse += ricevute.removeFirst().also { it.scadenza?.cancel() }
                }
                ricevute.forEach { it.registroBase = registro }
                val ultima = chiuse.last()
                // Piu' di una insieme: suona l'ultima, la prima verrebbe tagliata subito.
                suonaDopoIlTick(ultima.tickAlle, patternConferma(ultima))
                // Il gol e' al telefono: adesso, e non prima, si puo' offrire il nome. Con piu' gol
                // chiusi insieme vale l'ultimo.
                if (cresciuto) {
                    chiuse.lastOrNull { it.kind == WearConstants.INTENT_POINT && it.chiediMarcatore }?.let {
                        offriMarcatore(it.side, state)
                    }
                }
            }
        }
        ricevutaSport?.takeIf { it.sportId == state.sportId }?.let {
            it.scadenza?.cancel()
            ricevutaSport = null
            finisciCambioSport()
            haptics.suona(HapticFeedbackManager.PATTERN_CONFIRM)
        }
    }

    /**
     * CHI? vale per il gol con cui e' nata: se lo stato del telefono ha un registro diverso (un
     * ANNULLA, un marcatore dato dal telefono, una partita nuova) o la partita e' finita, si chiude.
     */
    private fun chiudiFinestraChiSeNonVera(state: WearScoreState) {
        if (_finestraChi.value == null) return
        if (state.matchOver || state.eventLog != registroDellOfferta) chiudiFinestraChi()
    }

    /**
     * Offre CHI? per [DURATA_FINESTRA_CHI_MS]: il bersaglio in basso lo dice e un tocco apre la
     * lista. Solo se lo sport attribuisce (il padel no), la rosa c'e' e il telefono e' collegato:
     * da scollegati la scelta si perderebbe in silenzio, e l'attribuzione si fa dopo, dal registro
     * del telefono. Un'offerta nuova sostituisce la vecchia.
     */
    private fun offriMarcatore(
        lato: Int,
        state: WearScoreState,
    ) {
        if (!state.attributesScorer || state.matchOver) return
        if (_allPlayers.value.isEmpty()) return
        if (connectionState.value !is ConnectionState.Connected) return
        finestraChiJob?.cancel()
        registroDellOfferta = state.eventLog
        _finestraChi.value = FinestraChi(lato, "${state.side1Primary}–${state.side2Primary}")
        finestraChiJob =
            viewModelScope.launch {
                delay(DURATA_FINESTRA_CHI_MS)
                chiudiFinestraChi()
            }
    }

    /**
     * Suona un pattern di lato non prima di [DISTANZA_DAL_TICK_MS] dal tick dello stesso tocco
     * ([tickAlle]; null se quel tocco non ne ha avuto uno: suona subito). Il vibratore cancella
     * quello in corso e il vocabolario conta gli impulsi: i due non devono fondersi.
     */
    private fun suonaDopoIlTick(
        tickAlle: Long?,
        pattern: LongArray,
    ) {
        val attesa = if (tickAlle == null) 0L else tickAlle + DISTANZA_DAL_TICK_MS - orologio()
        if (attesa <= 0L) {
            haptics.suona(pattern)
        } else {
            viewModelScope.launch {
                delay(attesa)
                haptics.suona(pattern)
            }
        }
    }

    /**
     * Il messaggio del tocco e' consegnato: da qui si aspetta lo stato, per [SCADENZA_RICEVUTA_MS].
     *
     * La ricevuta e' nella coda gia' dal tocco, con la lunghezza del registro di ALLORA (vedi
     * sendScoreIntent): lo stato che conferma puo' arrivare mentre sendMessage e' ancora in volo, e
     * se la ricevuta nascesse solo alla consegna confronterebbe questo tocco con lo stato di un
     * altro. Se quello stato l'ha gia' chiusa, non c'e' piu' niente da aspettare.
     */
    private fun avviaScadenza(ricevuta: Ricevuta) {
        if (ricevuta !in ricevute) return
        ricevuta.scadenza =
            viewModelScope.launch {
                delay(SCADENZA_RICEVUTA_MS)
                if (ricevute.remove(ricevuta)) nonConfermato()
            }
    }

    /**
     * Nessuno stato in tempo: un colpo lungo e la scritta. Il tocco NON si rimette in coda, e non
     * si rimanda: potrebbe essere gia' stato contato, e senza id idempotente conterebbe due volte.
     * La coda offline resta com'e'.
     *
     * CHIUSURA... non si sostituisce: e' un'attesa piu' grossa, e per un tocco basta la vibrazione.
     */
    private fun nonConfermato(messaggio: Transitorio = Transitorio.NonConfermato) {
        haptics.suona(WearPatterns.NON_CONFERMATO)
        if (transitorio is Transitorio.Chiusura && orologio() < transitorioFinoA) return
        mostraTransitorio(messaggio)
    }

    /** Lo sport chiesto e' arrivato: CAMBIO SPORT... ha detto quel che doveva, e la riga torna sola. */
    private fun finisciCambioSport() {
        if (transitorio == Transitorio.CambioSport) transitorio = null
    }

    /** Come [apriRicevuta], per il cambio sport: la conferma e' lo stato con lo sport chiesto. */
    private fun apriRicevutaSport(sportId: String) {
        ricevutaSport?.scadenza?.cancel()
        if (statoDalTelefono?.sportId == sportId) {
            ricevutaSport = null
            finisciCambioSport()
            ricalcolaFiducia()
            haptics.suona(HapticFeedbackManager.PATTERN_CONFIRM)
            return
        }
        val ricevuta = RicevutaSport(sportId)
        ricevutaSport = ricevuta
        ricevuta.scadenza =
            viewModelScope.launch {
                delay(SCADENZA_RICEVUTA_MS)
                if (ricevutaSport === ricevuta) {
                    ricevutaSport = null
                    nonConfermato(Transitorio.SportNonCambiato)
                }
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

    /**
     * Il tocco breve su un lato. Ritorna se il tocco e' stato accettato: solo allora suona il tick,
     * subito, prima di ogni risposta del telefono (che arriva dopo decimi di secondo): senza, chi
     * segna senza guardare tocca una seconda volta perche' "non ha vibrato". Un tocco scartato
     * (partita finita) non finge di essere stato preso.
     *
     * Il tick lo suona il ViewModel e non la schermata perche' la conferma di lato deve stare
     * almeno [DISTANZA_DAL_TICK_MS] dopo, e il tempo lo sa chi le suona tutte e due.
     */
    fun incrementScore(team: Int): Boolean {
        if (team != 1 && team != 2) return false
        // A partita finita il motore ignora il punto, ma il telefono no: addRemotePoint scrive
        // lo stesso una riga e un'azione che puntano all'evento precedente, e un ANNULLA dopo
        // riapre la partita togliendo un punto vero. Offline il tocco finiva in coda come
        // "1 IN ATTESA". isClickable=false sul lato non basta a fermarlo: la guardia sta qui,
        // dove passano tutti i tocchi, TalkBack compreso.
        //
        // Scartato in silenzio al polso: a dirlo e' la parola PARTITA FINITA, non una vibrazione
        // (DESIGN.md, "Coerenza fra telefono e orologio"). Il colpo lungo vale "NON CONFERMATO", e
        // un tocco che non deve contare non e' un tocco che il telefono non ha confermato.
        if (_scoreState.value?.matchOver == true) return false
        // Il marcatore non si apre piu' da solo: lo offre la finestra CHI?, e solo dopo che il
        // telefono ha rimandato lo stato col gol dentro (vedi chiudiRicevute). Senza telefono la
        // scelta fatta al polso non arrivava a nessuno; il punto invece finisce in coda come
        // sempre, e il marcatore si attribuira' dal registro del telefono. Con un telefono v1 non
        // ci sono ricevute, quindi nemmeno CHI?: il marcatore si attribuisce dal registro del
        // telefono (DESIGN.md: l'offerta viene dopo la ricevuta v2).
        val chiediMarcatore = _scoreState.value?.attributesScorer == true
        val tickAlle = orologio()
        haptics.tick()
        // UN canale solo. Prima del primo v2 il tocco partiva come intenzione E come punteggio
        // assoluto v1, e un telefono v2 lo contava due volte (nel calcio N+2, o il registro
        // appiattito). Il v1 vale SOLO se dal telefono e' arrivato davvero un v1 (un telefono non
        // aggiornato): in ogni altro caso, anche con un orologio nuovo o senza disco, il tocco va
        // come intenzione e, se non consegnato, in coda. Un v1 mandato a un telefono v2 tornato
        // in padel o tennis sarebbe scartato, e il punto perso.
        riconosciV2DalDisco()
        if (protocolV2Seen || !v1DalTelefono) {
            sendScoreIntent(team, WearConstants.INTENT_POINT, chiediMarcatore, tickAlle)
            // Nessuna vibrazione qui: la conferma arriva con lo stato che il telefono rimanda
            // (vedi chiudiRicevute), non alla consegna.
            return true
        }
        modifyScore(team, 1)
        return true
    }

    /**
     * Un orologio riavviato non ha ancora riletto i DataItem, ma il disco ricorda l'ultimo stato
     * v2: vuol dire che il telefono parla v2, e un tocco dato nella finestra dell'avvio a freddo va
     * come intenzione e non come punteggio assoluto dal contatore locale, che riparte da 0-0.
     */
    private fun riconosciV2DalDisco() {
        if (protocolV2Seen) return
        val disco = statoDaDisco() ?: return
        protocolV2Seen = true
        if (statoDalTelefono == null) statoDalTelefono = disco
    }

    fun decrementScore(team: Int) {
        if (team != 1 && team != 2) return
        // Un annullamento o una correzione puo' togliere proprio il gol che CHI? offre.
        chiudiFinestraChi()
        riconosciV2DalDisco()
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
            // Senza questo il ricalcolo tratterebbe un tennis in doppio come un singolare.
            servingSlot = if (ultimaNota.inCoppia) 1 else 0,
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
        // Solo la coda viva: i tocchi che il telefono ha rifiutato sono di un'altra partita e stanno
        // da parte, sommarli a quella del telefono mescolerebbe i due punteggi.
        val base = statoDalTelefono ?: return false
        // Senza sport non si puo' calcolare niente: succede con un telefono che parla una bozza
        // precedente del v2. Si dice di no, e chi ha chiesto mostrera' l'ultimo dato vero invece
        // di uno schermo vuoto -- e' il caso che il test ha scoperto.
        if (base.sportId.isBlank()) return false

        // L'orologio non ha le rose, quindi non sa se un tennis e' in coppia: lo sa dal telefono,
        // che manda il giocatore al servizio (1 o 2) solo in coppia. Il padel lo e' sempre.
        val rules =
            SportRegistry.byId(base.sportId).let {
                if (it is RacketRules && base.servingSlot != 0) it.conIlServizioInCoppia() else it
            }
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
                servingSlot = display.servingPlayerSlot ?: 0,
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
        // L'ora dell'ultimo dato vivo sopravvive al riavvio: e' il momento in cui serve di piu'.
        if (ultimoStatoVivoAlle == null) {
            ultimoStatoVivoAlle = ultimaNota.ricevutoAlle.takeIf { it > 0L }
            ricalcolaFiducia()
        }
        _pendingCount.value = pending.size
        // L'identita' dell'arretrato e i tocchi rifiutati stanno su disco (L5): un ViewModel nuovo li
        // ritrova. Un tentativo di una vita precedente non ha un orario: e' scaduto, e il prossimo
        // flush lo rimanda con lo stesso id.
        if (batchInVolo == null) batchInVolo = pending.batchInVolo()
        _rifiutati.value = pending.rifiutateSize
        // Un orologio riacceso a meta' partita, col telefono in borsa, deve ritrovare il
        // punteggio che aveva: non basta sapere quanti tocchi sono in coda.
        if (pending.size == 0) return
        // Uno stato sul disco e' un telefono v2 gia' visto: da qui un solo canale, il v2.
        riconosciV2DalDisco()
        rebuildLocalState()
    }

    /**
     * Spedisce l'arretrato in UN messaggio e aspetta la conferma prima di cancellarlo.
     *
     * Un messaggio solo perche' MessageClient non garantisce l'ordine: due tocchi invertiti
     * farebbero scartare il piu' vecchio dalla guardia sulla sequenza del telefono, cioe'
     * perdere un punto proprio mentre si recupera una partita intera.
     *
     * La coda si svuota su [onBatchAck] (o sullo stato col suo id), non sulla consegna: il messaggio
     * raggiunge il servizio del telefono anche ad app chiusa, e quel servizio non sa applicare niente.
     *
     * L5. Il blocco ha un id che si assegna al primo invio e si salva con la coda: un RINVIO (ack o
     * NACK persi, tentativo scaduto, nuovo collegamento) porta lo stesso id e le stesse prime
     * `quante` voci, e il telefono lo riconosce se l'ha gia' applicato. Porta anche la base, cioe'
     * il registro del telefono su cui il polso ha calcolato quello che mostra: su un'altra partita
     * il telefono rifiuta con un NACK, e la coda non si applica mai in silenzio a una partita nuova.
     * I tocchi che il telefono ha rifiutato stanno da parte e non ripartono mai da soli: li scarta
     * l'utente. Quelli nuovi sono una coda nuova, e partono.
     */
    fun flushPending(collegatoDiNuovo: Boolean = false) {
        // Il disco e' la verita': ad app chiusa il servizio puo' aver tolto il blocco da solo, e un
        // id vecchio sulle voci segnate dopo le farebbe passare per gia' applicate.
        batchInVolo = pending.batchInVolo()
        // Il passaggio a Connected chiude il tentativo di prima: da collegati, un arretrato che non ha
        // risposto non ha piu' niente da aspettare, e il flush usciva subito (L5). Si rimanda con lo
        // stesso id: se il telefono l'aveva applicato, lo riconosce.
        if (collegatoDiNuovo) batchInVoloDal = null
        if (batchAttivo()) return
        val voci = pending.all()
        if (voci.isEmpty()) return
        val blocco =
            batchInVolo ?: PendingIntents.BatchInVolo(id = ++intentSequence, quante = voci.size).also { pending.segnaBatchInVolo(it) }
        // Un rinvio manda lo STESSO blocco: le voci segnate dopo il primo invio vanno nel prossimo.
        val daSpedire = voci.take(blocco.quante)
        if (daSpedire.isEmpty()) {
            pending.confermaBatch(blocco.id)
            batchInVolo = null
            return
        }
        val seq = ++intentSequence
        batchInVolo = blocco
        batchInVoloDal = orologio()
        seqUltimoBatch = seq
        // La base e' quella su cui la coda e' NATA (salvata con lei), non quella di adesso: se nel
        // frattempo il telefono e' passato a un'altra partita, i tocchi non le appartengono, e il
        // rinvio deve dirlo invece di prendere la base nuova e applicarli in silenzio.
        val base =
            pending.base ?: improntaDelRegistroVisto().also {
                pending.base = it
                pending.partita = statoDalTelefono?.matchUuid.orEmpty()
            }
        val partita = pending.partita.orEmpty()
        armaTimeoutBatch(blocco)
        viewModelScope.launch {
            val payload =
                DataMap().apply {
                    putString(
                        WearConstants.KEY_INTENT_BATCH,
                        daSpedire.joinToString(WearConstants.BATCH_SEPARATOR) {
                            listOf(it.kind, it.side.toString(), it.atMillis.toString())
                                .joinToString(WearConstants.BATCH_FIELD_SEPARATOR)
                        },
                    )
                    putLong(WearConstants.KEY_SEQ, seq)
                    putLong(WearConstants.KEY_BATCH_ID, blocco.id)
                    putString(WearConstants.KEY_BATCH_BASE, base)
                    putString(WearConstants.KEY_MATCH_UUID, partita)
                }
            if (!connectionManager.sendMessage(WearConstants.MSG_INTENT_BATCH, payload.toByteArray())) {
                // Non e' partito: il tentativo e' scaduto subito, si riprova al prossimo collegamento
                // con una sequenza nuova e lo stesso id.
                scadeBatch(blocco)
            }
        }
    }

    /** L'impronta del registro del telefono che il polso ha sotto gli occhi: "0" se non ne ha visto uno. */
    private fun improntaDelRegistroVisto(): String =
        statoDalTelefono?.eventLog?.takeIf { it.isNotEmpty() }?.let { MatchLogCodec.impronta(it) } ?: "0"

    private fun armaTimeoutBatch(blocco: PendingIntents.BatchInVolo) {
        timeoutBatchJob?.cancel()
        timeoutBatchJob =
            viewModelScope.launch {
                delay(TIMEOUT_BATCH_MS)
                scadeBatch(blocco)
            }
    }

    /**
     * Il tentativo e' finito senza risposta: lo schermo torna a ridisegnarsi e il flush puo'
     * ripartire. L'id e le voci restano: e' il rinvio a dire al telefono se le ha gia' contate.
     */
    private fun scadeBatch(blocco: PendingIntents.BatchInVolo) {
        if (batchInVolo?.id != blocco.id || batchInVoloDal == null) return
        batchInVoloDal = null
        timeoutBatchJob?.cancel()
        statoDalTelefono?.let { ridisegna(it) }
        ricalcolaFiducia()
    }

    /** Come [applyStateV2] disegna lo stato quando nessun arretrato e' in volo. */
    private fun ridisegna(state: WearScoreState) {
        if (pending.size > 0 && rebuildLocalState()) return
        _scoreState.value = state
    }

    /**
     * Il telefono ha applicato l'arretrato [blocco], lo dica l'ack o lo stato col suo id: le sue voci
     * escono dalla coda (la copia su disco, idempotente: se ha gia' provveduto il servizio ad app
     * chiusa, non c'e' piu' niente da togliere). I tocchi rifiutati di un'altra partita non c'entrano:
     * restano da parte finche' l'utente non li scarta.
     */
    private fun batchApplicato(blocco: PendingIntents.BatchInVolo) {
        pending.confermaBatch(blocco.id)
        batchInVolo = null
        batchInVoloDal = null
        timeoutBatchJob?.cancel()
        _pendingCount.value = pending.size
        mostraTransitorio(Transitorio.Consegnati(blocco.quante))
    }

    /**
     * Il telefono ha applicato: solo ora le voci consegnate escono dalla coda. [batchId] e' l'id
     * dell'arretrato (0 da un telefono non aggiornato, che porta la sola [seq] del tentativo).
     */
    fun onBatchAck(
        seq: Long,
        batchId: Long = 0L,
    ) {
        val inVolo = batchInVolo ?: return
        // Con l'id si riconosce anche l'ack di un tentativo precedente (stesso blocco, altra seq).
        if (if (batchId > 0L) batchId != inVolo.id else seq != seqUltimoBatch) return
        batchApplicato(inVolo)
        // Coda vuota: l'autorita' torna al telefono, e a schermo va cio' che ha mandato per ultimo.
        if (pending.size == 0 || !rebuildLocalState()) {
            statoDalTelefono?.let { _scoreState.value = it }
        }
        // Le voci segnate mentre il blocco era in volo non hanno altro da aspettare: partono ora (D3).
if (pending.size > 0) flushPending()
    }

    /**
     * Il telefono NON ha applicato l'arretrato ([WearConstants.NACK_REJECTED] o [WearConstants.NACK_RETRY]).
     *
     * Passeggero: nessuno ha applicato (app chiusa sul telefono), il tentativo scade e il blocco si
     * rimanda al prossimo collegamento con lo stesso id. Definitivo: la partita del telefono non e'
     * quella su cui la coda e' nata (la base e' salvata con lei e non cambia). La coda passa fra i
     * tocchi rifiutati, da parte: la riga dice "n RIFIUTATI" finche' l'utente non li scarta dal menu,
     * e non si applicano mai in silenzio alla partita dopo ne' si rimandano da un ViewModel nuovo. I
     * tocchi segnati da qui in poi aprono una coda nuova, con la base del registro che il polso vede
     * adesso.
     */
    fun onBatchNack(
        batchId: Long,
        motivo: String,
    ) {
        val inVolo = batchInVolo ?: return
        if (batchId > 0L && batchId != inVolo.id) return
        scadeBatch(inVolo)
        if (motivo != WearConstants.NACK_REJECTED) return
        pending.rifiutaCoda()
        batchInVolo = null
        batchInVoloDal = null
        timeoutBatchJob?.cancel()
        _pendingCount.value = pending.size
        _rifiutati.value = pending.rifiutateSize
        triggerFailureVibration()
        // La coda rifiutata non si mostra piu' come conto locale: a schermo torna quello del telefono.
        statoDalTelefono?.let { ridisegna(it) }
        ricalcolaFiducia()
    }

    /**
     * L'utente scarta i tocchi rifiutati dal telefono (voce SCARTA del menu): escono loro e basta, la
     * coda nuova resta com'e'. Non fa niente se non ce ne sono: scartare punti che il telefono
     * potrebbe ancora accettare sarebbe la perdita che tutto il resto si sforza di evitare.
     */
    fun scartaCoda() {
        if (pending.rifiutateSize == 0) return
        pending.scartaRifiutate()
        _rifiutati.value = 0
        ricalcolaFiducia()
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
        // Dalla scelta in poi la riga lo dice: CAMBIO SPORT... finche' il quadrante non riceve lo
        // stato col nuovo sport, SPORT NON CAMBIATO se non arriva (o se il messaggio non parte).
        mostraTransitorio(Transitorio.CambioSport, DURATA_CAMBIO_SPORT_MS)
        viewModelScope.launch {
            val payload =
                DataMap().apply {
                    putString(WearConstants.KEY_SPORT_ID, sportId)
                    putLong(WearConstants.KEY_SEQ, seq)
                }
            val consegnato = connectionManager.sendMessage(WearConstants.MSG_SPORT_INTENT, payload.toByteArray())
            // Consegnato non e' cambiato: la conferma e' lo stato con lo sport chiesto.
            if (consegnato) {
                apriRicevutaSport(sportId)
            } else {
                triggerFailureVibration()
                mostraTransitorio(Transitorio.SportNonCambiato)
            }
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
        chiediMarcatore: Boolean = false,
        tickAlle: Long? = null,
    ) {
        val seq = ++intentSequence
        // L'orario si prende ORA, non quando il messaggio partira': un tocco messo in coda e
        // consegnato due ore dopo deve restare il tocco delle 18.
        val quando = System.currentTimeMillis()
        // Anche la ricevuta si apre ORA, prima di sendMessage, col registro di adesso: lo stato che
        // conferma puo' arrivare mentre sendMessage e' in volo, e allora deve trovarla (e trovare
        // prima quelle dei tocchi precedenti, nell'ordine). Senza un v2 mai visto nessuno stato
        // arrivera', e senza un registro leggibile non c'e' niente da confrontare: niente ricevuta.
        val registroAlTocco = if (protocolV2Seen && statoVivoVisto) lunghezzaRegistro(statoDalTelefono) else null
        val ricevuta =
            registroAlTocco?.let { Ricevuta(side, kind, it, tickAlle, chiediMarcatore).also(ricevute::addLast) }
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
                // Consegnato non e' preso: niente vibrazione adesso. La conferma suona quando il
                // telefono rimanda lo stato, e se non lo rimanda lo dice NON CONFERMATO.
                ricevuta?.let { avviaScadenza(it) }
                return@launch
            }
            // Non consegnato: la ricevuta si toglie senza suonare, vale la coda qui sotto.
            ricevuta?.let { ricevute.remove(it) }
            // Non arrivato: si REGISTRA invece di sparire. Il gesto e' cieco -- sullo schermo non
            // cambia niente -- quindi il polso deve comunque distinguere "preso dal telefono" da
            // "tenuto da parte", e il conteggio in attesa lo dice a schermo.
            // La base della coda e' il registro che il polso vede ORA, se la coda nasce con questo tocco.
            val accodato =
                pending.add(PendingIntent(kind, side, quando), improntaDelRegistroVisto(), statoDalTelefono?.matchUuid.orEmpty())
            _pendingCount.value = pending.size
            // Il gesto smette di essere cieco: il punteggio a schermo si aggiorna subito, calcolato
            // qui, e sara' identico a quello che il telefono calcolera' ricevendo la coda.
            if (accodato) rebuildLocalState()
            // Tenuto da parte non e' un fallimento -- il punto e' salvo, arrivera' al telefono da
            // solo -- ma non e' nemmeno la conferma: il tabellone del telefono non si sta muovendo.
            // Per questo suona la conferma del lato con in fondo un colpo lungo.
            suonaDopoIlTick(tickAlle, if (accodato) WearPatterns.inCoda(side) else WearPatterns.NON_CONFERMATO)
        }
    }

    /**
     * L'errore e il tocco inerte: un colpo solo di 400ms. Il vecchio doppio colpo di 60/120/60
     * coinciderebbe con la conferma del lato destro.
     */
    private fun triggerFailureVibration() {
        haptics.suona(WearPatterns.NON_CONFERMATO)
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
            // La scelta del marcatore la apre sendScoreIntent, e solo se il telefono ha ricevuto.
        }
    }

    // --- Match Timer Management ---
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

    /**
     * Il messaggio del portiere arrivato dal telefono.
     *
     * [durationMillis] e' la durata configurata (chiave nuova), [millis] il valore di sempre: la
     * durata alla partenza, il residuo in pausa e alla ripresa. Con la durata nota il residuo non
     * la sostituisce mai, e la pausa diventa [KeeperTimerState.Paused] invece di un azzeramento.
     * Senza (telefono non aggiornato) vale la regola di prima, perche' li' non si possono distinguere.
     */
    fun applyKeeperFromPhone(
        millis: Long,
        running: Boolean,
        durationMillis: Long,
    ) {
        val durataNota = durationMillis > 0
        if (durataNota) updateKeeperTimerDuration(durationMillis)
        when {
            running -> {
                setKeeperTimerState(KeeperTimerState.Running((millis / 1000).toInt()))
                if (!durataNota && millis > 0) updateKeeperTimerDuration(millis)
            }

            // Fermo a meta' conto: e' una pausa. Fermo alla durata piena equivale a non partito.
            durataNota && millis in 1 until durationMillis -> {
                setKeeperTimerState(KeeperTimerState.Paused((millis / 1000).toInt()))
            }

            else -> {
                if (!durataNota && millis > 0) updateKeeperTimerDuration(millis)
                resetKeeperTimer(fromRemote = true)
            }
        }
    }

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
        haptics.annulla()
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
        } else if (newState is KeeperTimerState.Paused) {
            _keeperProgress.value = newState.secondsRemaining
        }
    }

    fun toggleKeeperTimer() {
        when (val stato = keeperTimer.value) {
            is KeeperTimerState.Running -> resetKeeperTimer()

            // Dalla pausa si riprende dal residuo, come fa il service del telefono.
            is KeeperTimerState.Paused -> startKeeperTimer(stato.secondsRemaining * 1000L)

            else -> startKeeperTimer()
        }
    }

    // --- Reset ---

    fun resetMatch(
        fromRemote: Boolean = false,
        urgent: Boolean = false,
    ) {
        // Una partita finita o azzerata non ha piu' il gol che CHI? offriva.
        chiudiFinestraChi()
        // Con un v2 il polso non scrive il v1: lo 0-0 svuoterebbe il motore del telefono prima di
        // endMatch (L4), e la chiusura e' un'intenzione ([chiudiPartita]). Il v1 resta solo per
        // un telefono che non parla v2.
        val senzaInvii = fromRemote || protocolV2Seen
        // Update local state without sending data if fromRemote is true
        if (senzaInvii) {
            _team1Score.value = 0
            _team2Score.value = 0
        } else {
            updateScore(0, 0)
        }

        resetMatchTimer(senzaInvii)
        resetKeeperTimer(senzaInvii)

        if (!senzaInvii) {
            // Also signal match reset to mobile
            viewModelScope.launch {
                val data = mapOf(it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_MATCH_ACTIVE to false)
                connectionManager.sendData(
                    path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_MATCH_STATE,
                    data = data,
                    urgent = urgent,
                )
            }
        }
    }

    // --- Haptic Feedback ---
    private fun triggerShortVibration() {
        haptics.suona(HapticFeedbackManager.PATTERN_CONFIRM)
    }

    private fun triggerStrongContinuousVibration() {
        haptics.suona(HapticFeedbackManager.PATTERN_ALERT)
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
                // Un comando, non uno stato di sfondo: START non deve arrivare minuti dopo (L4).
                urgent = true,
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
                    urgent = true,
                )
            }
        }
    }

    private fun startKeeperTimer(fromMillis: Long = keeperTimerDuration) {
        keeperCountDownTimer?.cancel()
        val totalSeconds = (fromMillis / 1000).toInt()
        _keeperTimer.value = KeeperTimerState.Running(totalSeconds)
        _keeperProgress.value = totalSeconds

        keeperCountDownTimer =
            object : CountDownTimer(fromMillis, 1000) {
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
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_KEEPER_MILLIS to fromMillis,
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_KEEPER_RUNNING to true,
                    // Il telefono prende la durata da qui: da una ripresa [fromMillis] e' il residuo.
                    it.vantaggi.scoreboardessential.shared.communication.WearConstants.KEY_KEEPER_DURATION to keeperTimerDuration,
                )
            connectionManager.sendData(
                path = it.vantaggi.scoreboardessential.shared.communication.WearConstants.PATH_KEEPER_TIMER,
                data = data,
                urgent = true,
            )
        }
    }

    fun resetKeeperTimer(fromRemote: Boolean = false) {
        keeperCountDownTimer?.cancel()
        haptics.annulla()
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
                    urgent = true,
                )
            }
        }
    }

    override fun onCleared() {
        matchTimerJob?.cancel()
        keeperCountDownTimer?.cancel()
        haptics.annulla()
        connectionManager.cleanup()
        super.onCleared()
    }
}
