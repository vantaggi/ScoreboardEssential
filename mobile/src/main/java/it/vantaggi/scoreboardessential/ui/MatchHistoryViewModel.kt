package it.vantaggi.scoreboardessential.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import it.vantaggi.scoreboardessential.core.ExportResult
import it.vantaggi.scoreboardessential.database.Match
import it.vantaggi.scoreboardessential.database.MatchWithTeams
import it.vantaggi.scoreboardessential.repository.MatchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
    /** Il calcolo della riga dei set; iniettabile solo perche' il test conta le chiamate. */
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

    /** Le partite chiuse, pronte per la lista. */
    val matchHistory: LiveData<List<MatchHistoryUiState>> =
        repository.allMatches
            .map { elenco(it) }
            .flowOn(Dispatchers.Default)
            .asLiveData()

    /** Una partita chiusa non cambia registro: la sua riga si calcola una volta sola. */
    internal fun elenco(matches: List<MatchWithTeams>): List<MatchHistoryUiState> =
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
                    MatchHistoryUiState(match, nomi, riga)
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
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MatchHistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MatchHistoryViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
