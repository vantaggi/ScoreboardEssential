package it.vantaggi.scoreboardessential.padelelite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Un giocatore dell'app che si puo' scegliere per un collegamento. */
data class LocalChoice(
    val id: Int,
    val name: String,
)

/**
 * Una riga della rosa: il giocatore della dashboard e a chi e' collegato (nome del giocatore
 * locale), oppure a chi lo si proporrebbe per il nome identico (da confermare, non collegato).
 */
data class RosaRow(
    val remote: RemotePlayer,
    val linkedLocalName: String?,
    val proposedLocalName: String?,
)

sealed interface RosaUi {
    /** Nessun gruppo scelto (piu' gruppi, nessuno scelto): non c'e' una rosa da mostrare. */
    data object NoGroup : RosaUi

    data object Loading : RosaUi

    /** Il gruppo non ha giocatori nella dashboard. */
    data object Empty : RosaUi

    /** La rosa non si e' potuta leggere (rete, server): si puo' riprovare. */
    data object Error : RosaUi

    /** La sessione e' scaduta e il rinnovo e' stato rifiutato: la schermata torna all'accesso. */
    data object NeedLogin : RosaUi

    /** Il server rifiuta la lettura (403): non si e' piu' membri di questo gruppo, serve sceglierne un altro. */
    data object NotAuthorized : RosaUi

    /**
     * La rosa. [freeLocals] sono i giocatori dell'app non ancora collegati in questo gruppo, da
     * offrire quando si sceglie; [proposals] quanti nomi uguali il comando "Collega i nomi uguali"
     * collegherebbe.
     */
    data class Ready(
        val rows: List<RosaRow>,
        val freeLocals: List<LocalChoice>,
        val proposals: Int,
    ) : RosaUi
}

/**
 * La sezione "Giocatori del gruppo" della schermata Padel Elite: legge la rosa del gruppo scelto,
 * la tiene in memoria e ricalcola le righe a ogni collegamento. Ogni scrittura la comanda
 * l'utente; qui non nasce niente in silenzio.
 */
class RosaGruppoViewModel(
    private val account: PadelEliteAccount,
    private val rosa: RosaGruppo,
    /** Chiamata una volta per lettura quando la sessione e' scaduta e il rinnovo rifiutato: la schermata torna all'accesso. */
    private val onSessionLost: () -> Unit = {},
) : ViewModel() {
    private val _state = MutableStateFlow<RosaUi>(RosaUi.Loading)
    val state: StateFlow<RosaUi> = _state

    private var groupId: String? = null
    private var roster: List<RemotePlayer> = emptyList()
    private var loading: Job? = null

    private var started = false

    /**
     * Legge la rosa solo se serve: la prima volta, se il gruppo scelto e' cambiato o se l'ultima
     * lettura e' fallita. Una rotazione dello schermo non rifa' la richiesta.
     */
    fun loadIfNeeded() {
        val gruppo = account.selectedGroup()?.first
        val fallita = _state.value is RosaUi.Error || _state.value is RosaUi.NeedLogin || _state.value is RosaUi.NotAuthorized
        if (started && gruppo == groupId && !fallita) return
        load()
    }

    /** Dopo "Esci": la rosa di un altro account non si eredita. */
    fun reset() {
        loading?.cancel()
        started = false
        groupId = null
        roster = emptyList()
        _state.value = RosaUi.Loading
    }

    /** (Ri)legge la rosa del gruppo scelto. Una lettura in corso cede il posto alla nuova. */
    fun load() {
        started = true
        loading?.cancel()
        val gruppo = account.selectedGroup()?.first
        groupId = gruppo
        if (gruppo == null) {
            _state.value = RosaUi.NoGroup
            return
        }
        _state.value = RosaUi.Loading
        loading =
            viewModelScope.launch {
                when (val esito = account.roster(gruppo)) {
                    is RosterOutcome.Ok -> {
                        roster = esito.players
                        provaAScrivere { rosa.sync(gruppo, roster) }
                        refresh()
                    }

                    RosterOutcome.NeedLogin -> {
                        // Una volta sola, qui: la schermata mostra solo lo stato e non reagisce a ogni riconsegna.
                        // Prima la segnalazione e poi lo stato: chi aspetta NeedLogin trova la sessione gia' segnalata.
                        onSessionLost()
                        _state.value = RosaUi.NeedLogin
                    }

                    RosterOutcome.NotAuthorized -> {
                        _state.value = RosaUi.NotAuthorized
                    }

                    RosterOutcome.Network -> {
                        _state.value = RosaUi.Error
                    }
                }
            }
    }

    fun link(
        remoteId: Int,
        localId: Int,
    ) = scrivi(remoteId) { gruppo, remoto -> rosa.link(gruppo, remoto, localId) }

    fun unlink(remoteId: Int) = scrivi(remoteId) { gruppo, remoto -> rosa.unlink(gruppo, remoto) }

    fun createLocal(remoteId: Int) = scrivi(remoteId) { gruppo, remoto -> rosa.createLocalAndLink(gruppo, remoto) }

    /** Il comando "Collega i nomi uguali": scrive le proposte che la schermata ha mostrato. */
    fun linkSameNames(): Job {
        val gruppo = groupId ?: return Job().apply { complete() }
        return viewModelScope.launch {
            provaAScrivere { rosa.linkProposals(gruppo, roster) }
            refresh()
        }
    }

    private fun scrivi(
        remoteId: Int,
        azione: suspend (groupId: String, remote: RemotePlayer) -> Unit,
    ): Job {
        val gruppo = groupId
        val remoto = roster.firstOrNull { it.id == remoteId }
        if (gruppo == null || remoto == null) return Job().apply { complete() }
        return viewModelScope.launch {
            provaAScrivere { azione(gruppo, remoto) }
            refresh()
        }
    }

    /**
     * Una scrittura puo' fallire per cose che la schermata non vede (un giocatore locale cancellato
     * nel frattempo da un'altra schermata: la chiave esterna non regge): non e' un crash, la lista
     * si rilegge dal database e mostra com'e'. L'annullamento della coroutine non si inghiotte.
     */
    private suspend fun provaAScrivere(scrittura: suspend () -> Unit) {
        try {
            scrittura()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Unit
        }
    }

    private suspend fun refresh() {
        val gruppo = groupId ?: return
        if (roster.isEmpty()) {
            _state.value = RosaUi.Empty
            return
        }
        val collegamenti = rosa.linksOf(gruppo)
        val locali = rosa.localPlayers()
        val proposte = RosaGruppo.proposals(roster, locali, collegamenti)
        val nomeLocale = locali.associate { it.playerId to it.playerName }
        _state.value =
            RosaUi.Ready(
                rows =
                    roster.map { remoto ->
                        RosaRow(
                            remote = remoto,
                            linkedLocalName =
                                collegamenti
                                    .firstOrNull {
                                        it.remotePlayerId == remoto.id
                                    }?.let { nomeLocale[it.localPlayerId] },
                            proposedLocalName = proposte.firstOrNull { it.remote.id == remoto.id }?.local?.playerName,
                        )
                    },
                freeLocals =
                    locali
                        .filter { p -> collegamenti.none { it.localPlayerId == p.playerId } }
                        .map { LocalChoice(it.playerId, it.playerName) },
                proposals = proposte.size,
            )
    }
}

class RosaGruppoViewModelFactory(
    private val account: PadelEliteAccount,
    private val rosa: RosaGruppo,
    private val onSessionLost: () -> Unit = {},
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = RosaGruppoViewModel(account, rosa, onSessionLost) as T
}
