package it.vantaggi.scoreboardessential.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
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

    private val _appLanguage = MutableLiveData<String>()
    val appLanguage: LiveData<String> = _appLanguage

    private val _activeSport = MutableLiveData<String>()

    /** Lo sport scelto. E' la sola sorgente della voce mostrata nel selettore. */
    val activeSport: LiveData<String> = _activeSport

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
            _appLanguage.value = repository.getAppLanguage()
            _activeSport.value = repository.getActiveSport()
        }
    }

    fun saveTeam1Name(name: String) {
        viewModelScope.launch {
            repository.setTeam1Name(name)
            _team1Name.value = name
        }
    }

    fun saveTeam2Name(name: String) {
        viewModelScope.launch {
            repository.setTeam2Name(name)
            _team2Name.value = name
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
     * Non e' una guardia duplicata: e' la STESSA condizione (`il registro eventi non e' vuoto`)
     * letta dalla copia persistita, invece che dal motore che questa schermata non ha.
     */
    fun saveActiveSport(sportId: String) {
        viewModelScope.launch {
            val partitaIniziata = matchDao.getActiveMatchOnce()?.eventLog?.isNotEmpty() == true
            if (partitaIniziata) {
                sportChangeBlocked.call()
                // Rimette nel selettore la voce vera. LiveData ridistribuisce anche un valore
                // uguale, quindi l'osservatore riscrive l'etichetta e il menu smette di mentire.
                _activeSport.value = _activeSport.value
                return@launch
            }
            repository.setActiveSport(sportId)
            _activeSport.value = sportId
        }
    }

    fun saveAppLanguage(language: String) {
        viewModelScope.launch {
            repository.setAppLanguage(language)
            _appLanguage.value = language
        }
    }
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
