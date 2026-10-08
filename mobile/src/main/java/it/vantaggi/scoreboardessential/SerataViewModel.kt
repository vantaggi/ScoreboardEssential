package it.vantaggi.scoreboardessential

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import it.vantaggi.scoreboardessential.core.RotazioneSerata
import it.vantaggi.scoreboardessential.core.Serata
import it.vantaggi.scoreboardessential.database.Player
import it.vantaggi.scoreboardessential.database.PlayerDao
import it.vantaggi.scoreboardessential.repository.SerataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

/**
 * Tutto cio' che la schermata Serata mostra: la rosa, la serata (se c'e'), cosa e' toccato.
 *
 * [inCorso] e' la sola lettura: mentre si gioca le coppie sono quelle della partita e la serata
 * non si tocca (si compone la prossima a fine partita).
 */
data class StatoDellaSerata(
    val rosa: List<GiocatoreDellaSerata> = emptyList(),
    val serata: Serata? = null,
    val selezione: SelezioneSerata? = null,
    val inCorso: Boolean = false,
) {
    /** Il nome di [id], o niente se non e' piu' in rosa. */
    fun nome(id: Int): String = rosa.firstOrNull { it.id == id }?.nome.orEmpty()

    /** Quanti presenti mancano per cominciare (0 quando bastano). */
    val presentiMancanti: Int get() = (RotazioneSerata.MINIMO_PRESENTI - (serata?.presenti?.size ?: 0)).coerceAtLeast(0)

    /** Si puo' cominciare: una bozza composta e niente in corso. */
    val puoIniziare: Boolean get() = !inCorso && serata?.bozza != null
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
) : ViewModel() {
    private val _stato = MutableStateFlow(StatoDellaSerata(serata = store.load(), inCorso = partitaInCorso))
    val stato: StateFlow<StatoDellaSerata> = _stato

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
        val serata = _stato.value.serata ?: return
        val ids = rosa.map { it.id }.toSet()
        val usciti = serata.presenti.filter { it !in ids }
        if (usciti.isEmpty() || _stato.value.inCorso) return
        cambia { usciti.fold(it) { s, id -> s.togli(id) } }
    }

    /** Applica [modifica] alla serata, la ricorda e azzera la selezione. Non fa niente in sola lettura. */
    private fun cambia(modifica: (Serata) -> Serata) {
        val stato = _stato.value
        if (stato.inCorso) return
        val nuova = modifica(stato.serata ?: Serata(presenti = emptyList()))
        // Una serata vuota e mai giocata non c'e': non si ricorda.
        val daRicordare = nuova.takeUnless { it.presenti.isEmpty() && it.giocate.isEmpty() }
        store.save(daRicordare)
        _stato.value = stato.copy(serata = daRicordare, selezione = null)
    }

    /** Un tocco sulla riga di un giocatore della rosa: arriva o se ne va. */
    fun scambiaPresente(id: Int) {
        val serata = _stato.value.serata
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
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SerataViewModel(playerDao, store, partitaInCorso) as T
    }
}
