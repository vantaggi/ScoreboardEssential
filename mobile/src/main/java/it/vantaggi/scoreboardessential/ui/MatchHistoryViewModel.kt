package it.vantaggi.scoreboardessential.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import it.vantaggi.scoreboardessential.core.ExportResult
import it.vantaggi.scoreboardessential.database.Match
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
) : ViewModel() {
    /** Le partite chiuse, pronte per la lista. */
    val matchHistory: LiveData<List<MatchHistoryUiState>> =
        repository.allMatches
            .map { matches ->
                matches.map { match ->
                    val formatted =
                        if (match.players.isNotEmpty()) {
                            "Players: ${match.players.joinToString(", ") { it.playerName }}"
                        } else {
                            ""
                        }
                    MatchHistoryUiState(match, formatted)
                }
            }.flowOn(Dispatchers.Default)
            .asLiveData()

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
