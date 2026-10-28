package it.vantaggi.scoreboardessential

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.vantaggi.scoreboardessential.core.PresenteDellaDashboard
import it.vantaggi.scoreboardessential.core.RotazioneSerata
import it.vantaggi.scoreboardessential.core.Serata
import it.vantaggi.scoreboardessential.core.Traduzione
import it.vantaggi.scoreboardessential.database.MatchDao
import it.vantaggi.scoreboardessential.database.PadelEliteLink
import it.vantaggi.scoreboardessential.database.PadelEliteLinkDao
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerDao
import it.vantaggi.scoreboardessential.padelelite.FonteSerataDashboard
import it.vantaggi.scoreboardessential.padelelite.IndizioOmonimo
import it.vantaggi.scoreboardessential.padelelite.RemotePlayer
import it.vantaggi.scoreboardessential.padelelite.RosaGruppo
import it.vantaggi.scoreboardessential.padelelite.SerataDashboard
import it.vantaggi.scoreboardessential.padelelite.indizioDellOmonimo
import it.vantaggi.scoreboardessential.repository.SerataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Cosa e' toccato nel compositore: un posto della bozza (0..3) o uno della panchina. */
sealed interface SelezioneSerata {
    data class Posto(
        val posto: Int,
    ) : SelezioneSerata

    data class Panchina(
        val id: Int,
    ) : SelezioneSerata
}

/** Un giocatore della rosa locale, come lo vede la schermata Serata. */
data class GiocatoreDellaSerata(
    val id: Int,
    val nome: String,
)

/** Quale proposta della dashboard e' stata usata: i presenti, o il girone di indice [girone]. */
sealed interface PropostaDashboard {
    data object Presenti : PropostaDashboard

    data class Girone(
        val girone: Int,
    ) : PropostaDashboard
}

/**
 * Una proposta della dashboard usata: quale, con quali giocatori locali, e la serata di prima per
 * l'annullamento. [ids] dice anche se la proposta e' ancora quella in uso (la serata ha quei presenti).
 */
data class UsoDellaDashboard(
    val proposta: PropostaDashboard,
    val ids: List<Int>,
    val prima: Serata?,
)

/**
 * "E' gia' nell'app?": il presente della dashboard [idDashboard] ha lo stesso nome di [candidati], giocatori dell'app
 * non collegati. Nella dashboard spesso c'e' solo il cognome, quindi decide l'utente, guardando le partite giocate.
 */
data class DomandaOmonimo(
    val idDashboard: Int,
    val nome: String,
    val candidati: List<IndizioOmonimo>,
)

/** Cosa dire dopo aver usato una proposta della dashboard: quanti presenti, di cui quanti entrati come ospiti. */
data class RapportoDashboard(
    val presenti: Int,
    val ospiti: Int,
)

/**
 * Tutto cio' che la schermata Serata mostra: la rosa, la serata (se c'e'), cosa e' toccato.
 *
 * [inCorso] e' la sola lettura: mentre si gioca le coppie sono quelle della partita e la serata
 * non si tocca (si compone la prossima a fine partita). [dashboard] e' la serata della dashboard
 * ([SerataDashboard.Spenta] senza accesso o senza gruppo: allora la schermata e' quella di sempre).
 */
data class StatoDellaSerata(
    val rosa: List<GiocatoreDellaSerata> = emptyList(),
    val serata: Serata? = null,
    val selezione: SelezioneSerata? = null,
    val inCorso: Boolean = false,
    val dashboard: SerataDashboard = SerataDashboard.Spenta,
    val usata: UsoDellaDashboard? = null,
    /** Un presente della dashboard con omonimi nell'app: la schermata chiede se e' uno di loro. */
    val domanda: DomandaOmonimo? = null,
) {
    /** Il nome di [id], o niente se non e' piu' in rosa. */
    fun nome(id: Int): String = rosa.firstOrNull { it.id == id }?.nome.orEmpty()

    /** Quanti presenti mancano per cominciare (0 quando bastano). */
    val presentiMancanti: Int get() = (RotazioneSerata.MINIMO_PRESENTI - (serata?.presenti?.size ?: 0)).coerceAtLeast(0)

    /** Si puo' cominciare: una bozza composta e niente in corso. */
    val puoIniziare: Boolean get() = !inCorso && serata?.bozza != null

    /** Vero se [proposta] e' quella usata e la serata ha ancora proprio quei presenti. */
    fun inUso(proposta: PropostaDashboard): Boolean {
        val uso = usata ?: return false
        return uso.proposta == proposta && serata?.presenti?.toSet() == uso.ids.toSet()
    }
}

/**
 * Il compositore della serata: "chi gioca la prossima".
 *
 * Tiene la [Serata] (che e' un valore, e ha la sua logica in :core) e la ricorda a ogni comando nello
 * [SerataStore], cosi' riavviare l'app non la perde. Non conosce il motore della partita: "Inizia la
 * partita" lo fa [MainViewModel.avviaPartitaDellaSerata], quando la schermata torna.
 *
 * La selezione (un posto o uno della panchina) vive qui e non nella serata: e' un gesto a meta', non
 * un dato da ricordare.
 */
class SerataViewModel(
    private val playerDao: PlayerDao,
    private val store: SerataStore,
    partitaInCorso: Boolean,
    /** La serata della dashboard; null con la funzione spenta: nessuna riga e nessuna richiesta. */
    private val dashboard: FonteSerataDashboard? = null,
    /** I collegamenti R-1: un presente della dashboard che nasce nell'app nasce collegato. Null: nasce senza. */
    private val collegamenti: PadelEliteLinkDao? = null,
    /** Per gli indizi degli omonimi (partite giocate, l'ultima). */
    private val partite: MatchDao? = null,
) : ViewModel() {
    private val _stato =
        MutableStateFlow(
            StatoDellaSerata(
                serata = store.load(),
                inCorso = partitaInCorso,
            ),
        )
    val stato: StateFlow<StatoDellaSerata> = _stato

    private val _rapporti = Channel<RapportoDashboard>(Channel.CONFLATED)

    /** Un rapporto per ogni proposta usata: la schermata lo dice con "Annulla". */
    val rapporti: Flow<RapportoDashboard> = _rapporti.receiveAsFlow()

    private var lettura: Job? = null

    /** Una proposta toccata che aspetta le risposte sugli omonimi; le risposte: id della dashboard -> giocatore dell'app, o null per "nuovo". */
    private class UsoInAttesa(
        val proposta: PropostaDashboard,
        val traduzione: Traduzione,
        val gruppo: String,
        val risposte: MutableMap<Int, Int?> = LinkedHashMap(),
    )

    private var inAttesa: UsoInAttesa? = null

    init {
        viewModelScope.launch {
            playerDao.getAllPlayers().collect { giocatori ->
                val rosa = giocatori.map { GiocatoreDellaSerata(it.player.playerId, it.player.playerName) }
                _stato.update { it.copy(rosa = rosa) }
                esceChiNonEPiuInRosa(rosa)
            }
        }
    }

    /** Un giocatore eliminato dalla rosa esce dalla serata: non si puo' mandare in campo chi non c'e' piu'. */
    private fun esceChiNonEPiuInRosa(rosa: List<GiocatoreDellaSerata>) {
        val serata = store.load() ?: return
        val ids = rosa.map { it.id }.toSet()
        val usciti = serata.presenti.filter { it !in ids }
        if (usciti.isEmpty() || _stato.value.inCorso) return
        cambia { usciti.fold(it) { s, id -> s.togli(id) } }
    }

    /**
     * Rilegge la serata dalla memoria: chi torna sulla schermata (onStart) vede la partita chiusa o
     * avviata nel frattempo da MainViewModel. La selezione, un gesto a meta', si butta.
     */
    fun ricarica() {
        _stato.update { it.copy(serata = store.load(), selezione = null) }
        leggiLaDashboard(mostraLaLettura = _stato.value.dashboard !is SerataDashboard.Pronta)
    }

    /**
     * La serata e' cambiata nella memoria mentre la schermata e' aperta: l'orologio ha ruotato, scambiato o
     * cominciato la partita (lo scrive MainViewModel). Prima la schermata restava sulle coppie vecchie fino
     * alla riapertura. Se la memoria dice gia' quello che si vede (la scrittura e' di questa schermata) non
     * cambia niente; altrimenti la selezione si butta, come in [ricarica]. Una partita consegnata nel
     * frattempo rende la schermata di sola lettura; quella chiusa la riapre.
     */
    fun allineaAllaMemoria() {
        val inMemoria = store.load()
        val stato = _stato.value
        if (inMemoria == stato.serata) return
        val inCorso =
            when {
                inMemoria?.inGioco != null -> true
                stato.serata?.inGioco != null -> false
                else -> stato.inCorso
            }
        _stato.value = stato.copy(serata = inMemoria, selezione = null, inCorso = inCorso)
    }

    /** "Riprova" dopo un errore: rilegge mostrando che sta leggendo. */
    fun riprovaLaDashboard() = leggiLaDashboard(mostraLaLettura = true)

    /**
     * Rilegge la serata della dashboard. Senza accesso o senza gruppo la riga non c'e' (e non compare per un
     * attimo); una serata gia' letta resta in vista finche' arriva quella nuova (tornando sulla schermata non
     * lampeggia); una lettura in corso cede il posto alla nuova.
     */
    private fun leggiLaDashboard(mostraLaLettura: Boolean) {
        val fonte = dashboard ?: return
        lettura?.cancel()
        lettura =
            viewModelScope.launch {
                if (!fonte.attiva()) {
                    _stato.update { it.copy(dashboard = SerataDashboard.Spenta) }
                    return@launch
                }
                if (mostraLaLettura) _stato.update { it.copy(dashboard = SerataDashboard.InLettura) }
                val letta = fonte.leggi()
                _stato.update { it.copy(dashboard = letta) }
            }
    }

    /** "Usa i presenti": i confermati della dashboard diventano i presenti della serata. */
    fun usaIPresenti() {
        val pronta = _stato.value.dashboard as? SerataDashboard.Pronta ?: return
        usa(PropostaDashboard.Presenti, pronta.presenti)
    }

    /** Un girone salvato nella dashboard diventa i presenti: il telefono segna un campo, quindi un girone. */
    fun usaIlGirone(indice: Int) {
        val pronta = _stato.value.dashboard as? SerataDashboard.Pronta ?: return
        val girone = pronta.gironi.getOrNull(indice) ?: return
        usa(PropostaDashboard.Girone(indice), girone.traduzione)
    }

    /**
     * Applica una proposta della dashboard, solo al tocco: i collegati entrano come sono, i non collegati come
     * ospiti, le partite gia' giocate restano. Ricorda la serata di prima per [annullaLaDashboard].
     *
     * Con i collegamenti e il gruppo letto, un non collegato senza omonimi nell'app nasce gia' collegato; per chi
     * ha un omonimo (nella dashboard spesso c'e' solo il cognome) prima si chiede, uno alla volta, se e' la stessa
     * persona ([StatoDellaSerata.domanda], risposte con [rispondi]): la proposta si applica solo dopo l'ultima
     * risposta, e [annullaLaDomanda] la lascia cadere senza scrivere niente.
     */
    private fun usa(
        proposta: PropostaDashboard,
        traduzione: Traduzione,
    ) {
        if (_stato.value.inCorso || traduzione.voci.isEmpty() || inAttesa != null) return
        val gruppo = (_stato.value.dashboard as? SerataDashboard.Pronta)?.gruppo
        viewModelScope.launch {
            if (collegamenti == null || gruppo == null) {
                applica(proposta, traduzione, null, emptyMap())
                return@launch
            }
            inAttesa = UsoInAttesa(proposta, traduzione, gruppo)
            prossimaDomanda()
        }
    }

    /** "E' lui" ([localId]) o "e' un'altra persona" (null) per la domanda aperta; poi la prossima, o si applica. */
    fun rispondi(localId: Int?) {
        val attesa = inAttesa ?: return
        val domanda = _stato.value.domanda ?: return
        attesa.risposte[domanda.idDashboard] = localId
        _stato.update { it.copy(domanda = null) }
        viewModelScope.launch { prossimaDomanda() }
    }

    /** La domanda chiusa senza risposta: la proposta non si applica e niente si scrive. */
    fun annullaLaDomanda() {
        inAttesa = null
        _stato.update { it.copy(domanda = null) }
    }

    /**
     * La prossima domanda sugli omonimi, o l'applicazione se non ne restano. I candidati si calcolano adesso: un
     * omonimo gia' scelto per un altro presente, o collegato a un altro della dashboard, non si offre di nuovo.
     */
    private suspend fun prossimaDomanda() {
        val attesa = inAttesa ?: return
        val dao = collegamenti ?: return
        val locali = playerDao.getAllPlayers().first().map { it.player }
        val presi =
            attesa.traduzione.voci
                .filterIsInstance<PresenteDellaDashboard.Collegato>()
                .map { it.idLocale }
                .toSet() +
                attesa.risposte.values.filterNotNull()
        val collegati = dao.linksOf(attesa.gruppo)
        for (voce in attesa.traduzione.voci.filterIsInstance<PresenteDellaDashboard.Ospite>()) {
            if (voce.idDashboard in attesa.risposte) continue
            if (collegati.any { it.remotePlayerId == voce.idDashboard }) continue
            val candidati =
                RosaGruppo
                    .omonimiLiberi(RemotePlayer(voce.idDashboard, voce.nome), locali, collegati)
                    .filter { it.playerId !in presi }
            if (candidati.isEmpty()) continue
            val domanda = DomandaOmonimo(voce.idDashboard, voce.nome, candidati.map { indizioDellOmonimo(it, partite) })
            // La proposta puo' essere stata lasciata cadere mentre si leggeva.
            if (inAttesa !== attesa) return
            _stato.update { it.copy(domanda = domanda) }
            return
        }
        if (inAttesa !== attesa) return
        inAttesa = null
        applica(attesa.proposta, attesa.traduzione, attesa.gruppo, attesa.risposte)
    }

    private suspend fun applica(
        proposta: PropostaDashboard,
        traduzione: Traduzione,
        gruppo: String?,
        risposte: Map<Int, Int?>,
    ) {
        if (_stato.value.inCorso) return
        val ids = traduzione.voci.map { voce -> idLocale(voce, gruppo, risposte) }.distinct()
        val prima = store.load()
        cambia { it.conIPresenti(ids) }
        _stato.update { it.copy(usata = UsoDellaDashboard(proposta, ids, prima)) }
        _rapporti.trySend(RapportoDashboard(presenti = ids.size, ospiti = traduzione.ospiti))
    }

    /**
     * Il giocatore locale di una voce: il collegato com'e'. Senza collegamenti o gruppo l'ospite e' il suo omonimo
     * (deciso dalla traduzione, se e' ancora in rosa) o un giocatore nuovo, come prima. Con i collegamenti e' il
     * giocatore collegato nel frattempo, l'omonimo che l'utente ha detto essere lui (e ora collegato), o un
     * giocatore nuovo gia' collegato: la volta dopo entra come collegato e non serve ricrearlo.
     */
    private suspend fun idLocale(
        voce: PresenteDellaDashboard,
        gruppo: String?,
        risposte: Map<Int, Int?>,
    ): Int {
        if (voce is PresenteDellaDashboard.Collegato) return voce.idLocale
        val ospite = voce as PresenteDellaDashboard.Ospite
        val dao = collegamenti
        if (dao == null || gruppo == null) {
            return ospite.omonimo?.takeIf { id -> _stato.value.rosa.any { it.id == id } }
                ?: playerDao.insert(Player(playerName = ospite.nome, appearances = 0, goals = 0)).toInt()
        }
        dao.linkOfRemote(gruppo, ospite.idDashboard)?.let { return it.localPlayerId }
        val scelto = risposte[ospite.idDashboard]
        if (scelto != null) {
            dao.link(PadelEliteLink(scelto, gruppo, ospite.idDashboard, ospite.nome))
            return scelto
        }
        return dao.createAndLink(gruppo, ospite.idDashboard, ospite.nome)
    }

    /**
     * Annulla l'ultima proposta usata: torna la serata di prima. Gli ospiti creati restano fra i giocatori
     * (come quelli scritti a mano): sono giocatori locali, e potrebbero gia' servire altrove.
     */
    fun annullaLaDashboard() {
        val uso = _stato.value.usata ?: return
        if (_stato.value.inCorso) return
        store.save(uso.prima)
        _stato.update { it.copy(serata = uso.prima, selezione = null, usata = null) }
    }

    /** Applica [modifica] alla serata, la ricorda e azzera la selezione. Non fa niente in sola lettura. */
    private fun cambia(modifica: (Serata) -> Serata) {
        val stato = _stato.value
        if (stato.inCorso) return
        // Sempre dalla memoria e non dalla copia letta all'apertura: la partita puo' essere stata chiusa nel
        // frattempo da MainViewModel (anche dall'orologio), e riscriverla da una copia vecchia la rifarebbe.
        val nuova = modifica(store.load() ?: Serata(presenti = emptyList()))
        // Una serata vuota e mai giocata non c'e': non si ricorda.
        val daRicordare = nuova.takeUnless { it.presenti.isEmpty() && it.giocate.isEmpty() }
        store.save(daRicordare)
        _stato.value = stato.copy(serata = daRicordare, selezione = null)
    }

    /** Un tocco sulla riga di un giocatore della rosa: arriva o se ne va. */
    fun scambiaPresente(id: Int) {
        val serata = store.load()
        if (serata != null && id in serata.presenti) cambia { it.togli(id) } else cambia { it.aggiungi(id) }
    }

    /**
     * Un ospite: un giocatore locale come gli altri, creato al volo. Se in rosa c'e' gia' qualcuno con
     * lo stesso nome (senza badare alle maiuscole) e' lui, non un doppione: statistiche e storico
     * restano di una persona sola. Ritorna false per un nome vuoto.
     */
    fun aggiungiOspite(nome: String): Boolean {
        val pulito = nome.trim()
        if (pulito.isEmpty() || _stato.value.inCorso) return false
        val esistente = _stato.value.rosa.firstOrNull { it.nome.equals(pulito, ignoreCase = true) }
        if (esistente != null) {
            cambia { it.aggiungi(esistente.id) }
            return true
        }
        viewModelScope.launch {
            val id = playerDao.insert(Player(playerName = pulito, appearances = 0, goals = 0)).toInt()
            cambia { it.aggiungi(id) }
        }
        return true
    }

    fun ruota() = cambia { it.ruota() }

    fun stesseCoppie() = cambia { it.stesseCoppie() }

    fun scambiaILati() = cambia { it.scambiaLati() }

    /** Chiude la serata: nessuna partita si cancella, quelle giocate sono nello storico come ogni altra. */
    fun chiudiLaSerata() {
        if (_stato.value.inCorso) return
        store.save(null)
        _stato.value = _stato.value.copy(serata = null, selezione = null)
    }

    /** Tocco su un posto: lo seleziona; col secondo tocco su un altro posto o sulla panchina scambia. */
    fun toccaIlPosto(posto: Int) {
        val stato = _stato.value
        if (stato.inCorso || stato.serata?.bozza == null) return
        when (val scelta = stato.selezione) {
            null -> {
                _stato.value = stato.copy(selezione = SelezioneSerata.Posto(posto))
            }

            is SelezioneSerata.Posto -> {
                if (scelta.posto == posto) {
                    _stato.value = stato.copy(selezione = null)
                } else {
                    cambia { it.scambia(scelta.posto, posto) }
                }
            }

            is SelezioneSerata.Panchina -> {
                cambia { it.sostituisci(posto, scelta.id) }
            }
        }
    }

    /** Tocco su uno in panchina: lo seleziona; sopra un posto selezionato prende il suo posto. */
    fun toccaLaPanchina(id: Int) {
        val stato = _stato.value
        if (stato.inCorso || stato.serata?.bozza == null) return
        when (val scelta = stato.selezione) {
            null -> {
                _stato.value = stato.copy(selezione = SelezioneSerata.Panchina(id))
            }

            is SelezioneSerata.Posto -> {
                cambia { it.sostituisci(scelta.posto, id) }
            }

            is SelezioneSerata.Panchina -> {
                _stato.value =
                    stato.copy(selezione = if (scelta.id == id) null else SelezioneSerata.Panchina(id))
            }
        }
    }

    /** Il tocco sul fondo o sul "annulla" della selezione. */
    fun togliLaSelezione() {
        _stato.update { it.copy(selezione = null) }
    }

    class Factory(
        private val playerDao: PlayerDao,
        private val store: SerataStore,
        private val partitaInCorso: Boolean,
        private val dashboard: FonteSerataDashboard? = null,
        private val collegamenti: PadelEliteLinkDao? = null,
        private val partite: MatchDao? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SerataViewModel(playerDao, store, partitaInCorso, dashboard, collegamenti, partite) as T
    }
}
