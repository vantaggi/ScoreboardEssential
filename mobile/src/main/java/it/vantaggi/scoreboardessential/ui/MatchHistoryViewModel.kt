package it.vantaggi.scoreboardessential.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import it.vantaggi.scoreboardessential.core.ExportResult
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchWithTeams
import it.vantaggi.scoreboardessential.padelelite.InvioInfo
import it.vantaggi.scoreboardessential.padelelite.PadelEliteServices
import it.vantaggi.scoreboardessential.repository.MatchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * Il ViewModel dello storico: elenco, cancellazione ed export, e nient'altro.
 *
 * Prima lo storico si prendeva un MainViewModel tutto suo. Il suo init mandava 0-0 all'orologio,
 * ripristinava la partita e registrava un secondo receiver mentre quello di MainActivity era vivo
 * nel back stack: con lo storico aperto ogni evento dell'orologio veniva applicato due volte (un
 * gol al polso ne dava due al giocatore, un arretrato due righe vive). Il MainViewModel deve
 * essere uno solo per processo.
 */
class MatchHistoryViewModel(
    private val repository: MatchRepository,
    /** Padel Elite configurato: senza, nessun comando e nessuno stato di invio sulle card. */
    private val padelEliteEnabled: Boolean = false,
    /** Lo stato di invio di ogni partita per `matchUuid`; vuoto con la funzione spenta. */
    private val invii: Flow<Map<String, InvioInfo>> = flowOf(emptyMap()),
    /** C'e' l'accesso a Padel Elite e un gruppo scelto (si rilegge con [rileggiAccesso]); senza, niente "Invia di nuovo". */
    private val accesso: () -> Boolean = { false },
    /** Il calcolo della riga dei set; iniettabile solo perche' il test conta le chiamate. Ultimo: il test lo passa come lambda finale. */
    private val calcolaRiga: (Match) -> String? = RigaDeiSet::of,
) : ViewModel() {
    /**
     * Cio' che determina la riga dei set di una partita. Room rilegge tutta la query a ogni punto
     * della partita viva: senza cache ogni punto rigiocherebbe il registro di tutto lo storico.
     */
    private data class ChiaveRiga(
        val matchId: Int,
        val sportId: String,
        val serveOrder: String?,
        val eventLog: String?,
    )

    // La riga puo' essere null e va ricordata lo stesso: per questo containsKey e non getOrPut.
    private val righe = HashMap<ChiaveRiga, String?>()

    private val conAccesso = MutableStateFlow(accesso())

    /** L'accesso o il gruppo possono cambiare in un'altra schermata: la lista lo rilegge al ritorno. */
    fun rileggiAccesso() {
        conAccesso.value = accesso()
    }

    /** Le partite chiuse, pronte per la lista. */
    val matchHistory: LiveData<List<MatchHistoryUiState>> =
        combine(repository.allMatches, invii, conAccesso) { matches, stati, entrato -> elenco(matches, stati, entrato) }
            .flowOn(Dispatchers.Default)
            .asLiveData()

    /** Una partita chiusa non cambia registro: la sua riga si calcola una volta sola. */
    internal fun elenco(
        matches: List<MatchWithTeams>,
        stati: Map<String, InvioInfo> = emptyMap(),
        entrato: Boolean = false,
    ): List<MatchHistoryUiState> =
        synchronized(righe) {
            val correnti = HashSet<ChiaveRiga>()
            val stati =
                matches.map { match ->
                    val m = match.match
                    val chiave = ChiaveRiga(m.matchId, m.sportId, m.serveOrder, m.eventLog)
                    correnti += chiave
                    if (chiave !in righe) righe[chiave] = calcolaRiga(m)
                    val riga = righe[chiave]
                    // Senza etichetta: «Giocatori:» o «Players:» lo mette la card, da risorsa.
                    val nomi = match.players.joinToString(", ") { it.playerName }
                    MatchHistoryUiState(match, nomi, riga, m.matchUuid?.let { stati[it] }, padelEliteEnabled, padelEliteEnabled && entrato)
                }
            // Le partite cancellate e i registri superati escono dalla cache.
            righe.keys.retainAll(correnti)
            stati
        }

    /** Cancella per sempre una partita dello storico. */
    fun deleteMatch(match: Match) =
        viewModelScope.launch {
            repository.deleteMatch(match)
        }

    /** L'export di una partita chiusa. Null se nel frattempo e' stata cancellata. */
    suspend fun buildSavedExport(matchId: Int): ExportResult? = repository.buildSavedExport(matchId)
}

class MatchHistoryViewModelFactory(
    private val repository: MatchRepository,
    private val padelElite: PadelEliteServices? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MatchHistoryViewModel::class.java)) {
            val acceso = padelElite?.isEnabled == true
            @Suppress("UNCHECKED_CAST")
            return MatchHistoryViewModel(
                repository,
                padelEliteEnabled = acceso,
                invii = if (acceso) padelElite!!.invii.states else flowOf(emptyMap()),
                accesso = { acceso && padelElite!!.hasAccess() },
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
