package it.vantaggi.scoreboardessential

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.vantaggi.scoreboardessential.core.PresenteDellaDashboard
import it.vantaggi.scoreboardessential.core.RotazioneSerata
import it.vantaggi.scoreboardessential.core.Serata
import it.vantaggi.scoreboardessential.core.Traduzione
import it.vantaggi.scoreboardessential.database.PadelEliteLinkDao
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerDao
import it.vantaggi.scoreboardessential.padelelite.FonteSerataDashboard
import it.vantaggi.scoreboardessential.padelelite.SerataDashboard
import it.vantaggi.scoreboardessential.repository.SerataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
     * ospiti (un giocatore nuovo, o l'omonimo che la traduzione ha scelto), le partite gia' giocate restano.
     * Ricorda la serata di prima per [annullaLaDashboard].
     */
    private fun usa(
        proposta: PropostaDashboard,
        traduzione: Traduzione,
    ) {
        if (_stato.value.inCorso || traduzione.voci.isEmpty()) return
        viewModelScope.launch {
            val gruppo = (_stato.value.dashboard as? SerataDashboard.Pronta)?.gruppo
            val ids = traduzione.voci.map { voce -> idLocale(voce, gruppo) }.distinct()
            val prima = store.load()
            cambia { it.conIPresenti(ids) }
            _stato.update { it.copy(usata = UsoDellaDashboard(proposta, ids, prima)) }
            _rapporti.trySend(RapportoDashboard(presenti = ids.size, ospiti = traduzione.ospiti))
        }
    }

    /**
     * Il giocatore locale di una voce: il collegato com'e', l'ospite nel suo omonimo (deciso dalla traduzione, se
     * e' ancora in rosa) o in un giocatore nuovo col nome della dashboard. Il giocatore nuovo, con un [gruppo],
     * nasce gia' collegato (come la scelta dei giocatori della partita): la volta dopo entra come collegato e non
     * serve ricrearlo. L'omonimo invece non si collega da solo: nella dashboard spesso c'e' solo il cognome, e
     * se sia la stessa persona lo decide l'utente (nella scelta dei giocatori o nella schermata Padel Elite).
     */
    private suspend fun idLocale(
        voce: PresenteDellaDashboard,
        gruppo: String?,
    ): Int =
        when (voce) {
            is PresenteDellaDashboard.Collegato -> {
                voce.idLocale
            }

            is PresenteDellaDashboard.Ospite -> {
                voce.omonimo?.takeIf { id -> _stato.value.rosa.any { it.id == id } }
                    ?: nuovoDallaDashboard(voce, gruppo)
            }
        }

    private suspend fun nuovoDallaDashboard(
        voce: PresenteDellaDashboard.Ospite,
        gruppo: String?,
    ): Int {
        val dao = collegamenti
        if (dao == null || gruppo == null) {
            return playerDao.insert(Player(playerName = voce.nome, appearances = 0, goals = 0)).toInt()
        }
        // Collegato nel frattempo (un'altra schermata): e' il suo giocatore, non uno nuovo.
        dao.linkOfRemote(gruppo, voce.idDashboard)?.let { return it.localPlayerId }
        return dao.createAndLink(gruppo, voce.idDashboard, voce.nome)
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SerataViewModel(playerDao, store, partitaInCorso, dashboard, collegamenti) as T
    }
}
