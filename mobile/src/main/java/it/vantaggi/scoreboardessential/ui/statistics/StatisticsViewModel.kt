package it.vantaggi.scoreboardessential.ui.statistics

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.AppDatabase
import it.vantaggi.scoreboardessential.database.PlayerRecord
import it.vantaggi.scoreboardessential.domain.model.PlayerStatsDTO
import it.vantaggi.scoreboardessential.domain.usecases.GetPlayerStatsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class StatisticsViewModel(
    application: Application,
) : ViewModel() {
    private val playerDao = AppDatabase.getDatabase(application).playerDao()
    private val matchDao = AppDatabase.getDatabase(application).matchDao()
    private val getPlayerStatsUseCase = GetPlayerStatsUseCase(playerDao, matchDao)

    enum class FilterType {
        ALL,
        ATTACK,
        DEFENSE,
    }

    private val filter = MutableStateFlow(FilterType.ALL)

    /** La scheda guardata nel calcio: dopo una rotazione la schermata la rimette scelta. */
    val filtro: StateFlow<FilterType> = filter

    @OptIn(ExperimentalCoroutinesApi::class)
    val topScorers: StateFlow<List<PlayerStatsDTO>> =
        filter
            .flatMapLatest { filterType ->
                getPlayerStatsUseCase.getTopScorers(LIMIT, CATEGORIE[filterType])
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Le schede del calcio che valgono: Generale sempre, Attacco e Difesa solo se in classifica c'e'
     * qualcuno con un ruolo di quella parte. Senza ruoli le due schede ripetevano Generale o erano
     * vuote: cio' che non vale per i dati che ci sono sparisce, come i filtri per ruolo dei Giocatori.
     */
    val schede: StateFlow<List<FilterType>> =
        getPlayerStatsUseCase
            .getTopScorers(Int.MAX_VALUE, null)
            .map { classifica -> schedeCon(classifica.flatMap { it.roles }.map { it.category }) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(FilterType.ALL))

    fun setFilter(newFilter: FilterType) {
        filter.value = newFilter
    }

    /**
     * Lo sport di cui si guardano le statistiche (revisione dei menu, passo 6): decide se la classifica
     * e' di gol o di partite vinte. Parte da quello in uso, ma si cambia dalla schermata senza toccare
     * le Impostazioni: chi gioca a piu' sport li guarda uno alla volta. Null finche' non e' stato letto.
     */
    private val _sport = MutableStateFlow<String?>(null)
    val sport: StateFlow<String?> = _sport

    fun setSport(sportId: String) {
        _sport.value = sportId
    }

    /** Nel padel e nel tennis, senza marcatori: le partite giocate e vinte nello sport in uso. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val records: StateFlow<List<PlayerRecord>> =
        _sport
            .flatMapLatest { id ->
                if (id == null || contaIGol(id)) flowOf(emptyList()) else matchDao.getPlayerRecords(id, LIMIT)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    companion object {
        private const val LIMIT = 10

        /** Le categorie di ruolo di ogni scheda; Generale non filtra. */
        private val CATEGORIE =
            mapOf(
                FilterType.ATTACK to listOf("ATTACCO"),
                FilterType.DEFENSE to listOf("DIFESA", "PORTA"),
            )

        /** Generale, e le schede di cui qualcuno ha una categoria fra [categorie]. */
        fun schedeCon(categorie: Collection<String>): List<FilterType> =
            listOf(FilterType.ALL) + CATEGORIE.filterValues { proprie -> proprie.any { it in categorie } }.keys

        /** Gli sport in cui segnando si dice chi ha segnato hanno la classifica dei gol; gli altri quella delle vittorie. */
        fun contaIGol(sportId: String): Boolean = SportRegistry.byId(sportId).capabilities.attributesScorer
    }

    class Factory(
        private val application: Application,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(StatisticsViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return StatisticsViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
