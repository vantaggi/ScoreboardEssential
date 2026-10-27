package it.vantaggi.scoreboardessential.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.vantaggi.scoreboardessential.core.DeuceRule
import it.vantaggi.scoreboardessential.core.MatchLogCodec
import it.vantaggi.scoreboardessential.core.SportRegistry
import it.vantaggi.scoreboardessential.database.MatchDao
import it.vantaggi.scoreboardessential.repository.MatchSettingsRepository
import it.vantaggi.scoreboardessential.utils.SingleLiveEvent
import kotlinx.coroutines.launch

class MatchSettingsViewModel(
    private val repository: MatchSettingsRepository,
    private val matchDao: MatchDao,
) : ViewModel() {
    /** Il cambio sport e' stato rifiutato perche' una partita e' gia' cominciata. */
    val sportChangeBlocked = SingleLiveEvent<Unit>()

    /** Il cambio della regola del 40-40 e' stato rifiutato, per la stessa ragione. */
    val deuceChangeBlocked = SingleLiveEvent<Unit>()

    private val _team1Name = MutableLiveData<String>()
    val team1Name: LiveData<String> = _team1Name

    private val _team2Name = MutableLiveData<String>()
    val team2Name: LiveData<String> = _team2Name

    private val _team1Color = MutableLiveData<Int>()
    val team1Color: LiveData<Int> = _team1Color

    private val _team2Color = MutableLiveData<Int>()
    val team2Color: LiveData<Int> = _team2Color

    private val _keeperTimerDuration = MutableLiveData<Long>()
    val keeperTimerDuration: LiveData<Long> = _keeperTimerDuration

    private val _activeSport = MutableLiveData<String>()

    /** Lo sport scelto. E' la sola sorgente della voce mostrata nel selettore. */
    val activeSport: LiveData<String> = _activeSport

    private val _deuceRule = MutableLiveData<DeuceRule>()

    /**
     * La regola del 40-40 in vigore per lo sport scelto: quella scelta, o quella del registro se non
     * e' mai stata scelta. Nel calcio non vuol dire niente e la schermata non la mostra.
     */
    val deuceRule: LiveData<DeuceRule> = _deuceRule

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _team1Name.value = repository.getTeam1Name()
            _team2Name.value = repository.getTeam2Name()
            _team1Color.value = repository.getTeam1Color()
            _team2Color.value = repository.getTeam2Color()
            _keeperTimerDuration.value = repository.getKeeperTimerDuration()
            _activeSport.value = repository.getActiveSport()
            caricaRegolaDel40()
        }
    }

    // Il campo vuoto vuol dire "nessun nome scelto": si salva il predefinito, come prima che il
    // campo lo nascondesse, e non una stringa vuota che orologio e registro mostrerebbero cosi'.
    fun saveTeam1Name(name: String) {
        val nome = name.trim().ifEmpty { MatchSettingsRepository.DEFAULT_TEAM1_NAME }
        viewModelScope.launch {
            repository.setTeam1Name(nome)
            _team1Name.value = nome
        }
    }

    fun saveTeam2Name(name: String) {
        val nome = name.trim().ifEmpty { MatchSettingsRepository.DEFAULT_TEAM2_NAME }
        viewModelScope.launch {
            repository.setTeam2Name(nome)
            _team2Name.value = nome
        }
    }

    fun saveTeam1Color(color: Int) {
        viewModelScope.launch {
            repository.setTeam1Color(color)
            _team1Color.value = color
        }
    }

    fun saveTeam2Color(color: Int) {
        viewModelScope.launch {
            repository.setTeam2Color(color)
            _team2Color.value = color
        }
    }

    fun saveKeeperTimerDuration(duration: Long) {
        viewModelScope.launch {
            repository.setKeeperTimerDuration(duration)
            _keeperTimerDuration.value = duration
        }
    }

    /**
     * Cambia sport, ma non a partita cominciata.
     *
     * Prima la preferenza veniva scritta SEMPRE. La guardia vera vive in
     * [it.vantaggi.scoreboardessential.MainViewModel.selectSport], che a partita viva rifiuta di
     * applicare -- ma nessuno lo diceva a questa schermata, che intanto mostrava lo sport nuovo
     * nel selettore. Risultato: il tabellone continuava col vecchio, il menu diceva il nuovo, e
     * il cambio scattava da solo alla partita SUCCESSIVA. Tre bugie in fila, e la terza e' la
     * peggiore: il sistema faceva una cosa diversa da quella che era sembrata.
     *
     * Il registro si legge dalla copia persistita, perche' il motore questa schermata non ce l'ha,
     * e si legge come lo rilegge il ripristino: decodificato. La stringa nuda non basta, perche'
     * un gol annullato lascia '1|', il registro vuoto codificato, e selectSport quel cambio lo
     * accetta. Un registro illeggibile vale come partita iniziata: il ripristino ne recupera il
     * punteggio, e rifiutare e' l'errore che costa meno.
     */
    fun saveActiveSport(sportId: String) {
        viewModelScope.launch {
            if (partitaIniziata()) {
                sportChangeBlocked.value = Unit
                // Rimette nel selettore la voce vera. LiveData ridistribuisce anche un valore
                // uguale, quindi l'osservatore riscrive l'etichetta e il menu smette di mentire.
                _activeSport.value = _activeSport.value
                return@launch
            }
            repository.setActiveSport(sportId)
            _activeSport.value = sportId
            caricaRegolaDel40()
        }
    }

    /**
     * Cambia la regola del 40-40 dello sport scelto, ma non a partita cominciata: come lo sport,
     * rigiocare i punti gia' dati con un'altra regola cambierebbe un game chiuso sul campo, e il
     * selettore mostrerebbe una regola diversa da quella che il tabellone sta applicando.
     */
    fun saveDeuceRule(regola: DeuceRule) {
        val sportId = _activeSport.value ?: return
        viewModelScope.launch {
            if (partitaIniziata()) {
                deuceChangeBlocked.value = Unit
                _deuceRule.value = _deuceRule.value
                return@launch
            }
            repository.setDeuceRule(sportId, regola)
            _deuceRule.value = regola
        }
    }

    private suspend fun caricaRegolaDel40() {
        val sportId = _activeSport.value ?: return
        _deuceRule.value = repository.getDeuceRule(sportId) ?: SportRegistry.byId(sportId).config.deuce
    }

    /** Lo stesso criterio di [saveActiveSport]: un registro illeggibile vale come partita iniziata. */
    private suspend fun partitaIniziata(): Boolean =
        matchDao.getActiveMatchOnce()?.let { MatchLogCodec.decode(it.eventLog)?.isNotEmpty() ?: true } == true
}

class MatchSettingsViewModelFactory(
    private val repository: MatchSettingsRepository,
    private val matchDao: MatchDao,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MatchSettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MatchSettingsViewModel(repository, matchDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
