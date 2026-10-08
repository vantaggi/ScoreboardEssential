package it.vantaggi.scoreboardessential.padelelite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Cosa non va nell'accesso, detto per il campo o per la schermata (il testo lo sceglie la vista). */
enum class LoginProblem {
    EMAIL_EMPTY,
    PASSWORD_EMPTY,
    INVALID_CREDENTIALS,
    EMAIL_NOT_CONFIRMED,
    RATE_LIMITED,
    NETWORK,
    OTHER,
}

/** Lo stato del modulo di accesso: vuoto (default), invio in corso, errore. Il "riuscito" e' [PadelEliteViewModel.signedInEmail]. */
data class LoginUi(
    val sending: Boolean = false,
    val problem: LoginProblem? = null,
)

sealed interface GroupsUi {
    data object Loading : GroupsUi

    /** I gruppi e quello scelto (null se non se n'e' scelto uno). */
    data class Ready(
        val groups: List<PadelEliteGroup>,
        val selectedId: String?,
    ) : GroupsUi

    /** Accesso fatto ma l'utente non e' membro di nessun gruppo. */
    data object Empty : GroupsUi

    data object Error : GroupsUi
}

/**
 * La schermata "Padel Elite": accesso con email e password, elenco dei gruppi e scelta, esci.
 * Tiene lo stato fuori dall'Activity: un accesso in corso sopravvive a una rotazione.
 */
class PadelEliteViewModel(
    private val account: PadelEliteAccount,
) : ViewModel() {
    private val _signedInEmail = MutableStateFlow(account.session()?.email)

    /** L'email dell'utente collegato, o null se non lo e'. */
    val signedInEmail: StateFlow<String?> = _signedInEmail

    private val _login = MutableStateFlow(LoginUi())
    val login: StateFlow<LoginUi> = _login

    private val _groups = MutableStateFlow<GroupsUi>(GroupsUi.Loading)
    val groups: StateFlow<GroupsUi> = _groups

    init {
        if (_signedInEmail.value != null) loadGroups()
    }

    fun signIn(
        email: String,
        password: String,
    ) {
        if (_login.value.sending) return
        val mancante =
            when {
                email.isBlank() -> LoginProblem.EMAIL_EMPTY
                password.isEmpty() -> LoginProblem.PASSWORD_EMPTY
                else -> null
            }
        if (mancante != null) {
            _login.value = LoginUi(problem = mancante)
            return
        }
        _login.value = LoginUi(sending = true)
        viewModelScope.launch {
            when (val esito = account.signIn(email, password)) {
                is AuthResult.Ok -> {
                    _login.value = LoginUi()
                    _signedInEmail.value = esito.session.email.ifEmpty { email.trim() }
                    loadGroups()
                }

                is AuthResult.Rejected -> {
                    _login.value =
                        LoginUi(
                            problem =
                                when (esito.reason) {
                                    AuthFailure.INVALID_CREDENTIALS -> LoginProblem.INVALID_CREDENTIALS
                                    AuthFailure.EMAIL_NOT_CONFIRMED -> LoginProblem.EMAIL_NOT_CONFIRMED
                                    AuthFailure.RATE_LIMITED -> LoginProblem.RATE_LIMITED
                                    AuthFailure.SESSION_EXPIRED, AuthFailure.OTHER -> LoginProblem.OTHER
                                },
                        )
                }

                AuthResult.Network -> {
                    _login.value = LoginUi(problem = LoginProblem.NETWORK)
                }
            }
        }
    }

    fun loadGroups() {
        _groups.value = GroupsUi.Loading
        viewModelScope.launch {
            when (val esito = account.groups()) {
                is GroupsOutcome.Ok -> {
                    val scelto = account.selectedGroup()?.first
                    // Un gruppo scelto di cui non si e' piu' membri non conta come scelto.
                    val valido = scelto?.takeIf { id -> esito.groups.any { it.id == id } }
                    if (scelto != null && valido == null) account.clearSelectedGroup()
                    _groups.value =
                        if (esito.groups.isEmpty()) {
                            GroupsUi.Empty
                        } else {
                            // Un solo gruppo non e' una scelta: si prende da solo.
                            val unico = esito.groups.singleOrNull()
                            if (unico != null && valido == null) account.selectGroup(unico)
                            GroupsUi.Ready(esito.groups, valido ?: unico?.id)
                        }
                }

                // La sessione e' scaduta e il rinnovo e' stato rifiutato: si torna al modulo di accesso.
                GroupsOutcome.NeedLogin -> {
                    _signedInEmail.value = null
                }

                GroupsOutcome.Network -> {
                    _groups.value = GroupsUi.Error
                }
            }
        }
    }

    /**
     * Un'altra parte della schermata (la rosa) ha scoperto che la sessione e' finita: si torna al
     * modulo di accesso. Non rilegge niente (nessuna richiesta, nessun ciclo) ed e' idempotente.
     */
    fun sessionLost() {
        _signedInEmail.value = null
    }

    fun selectGroup(group: PadelEliteGroup) {
        account.selectGroup(group)
        val corrente = _groups.value
        if (corrente is GroupsUi.Ready) _groups.value = corrente.copy(selectedId = group.id)
    }

    fun signOut() {
        account.signOut()
        _signedInEmail.value = null
        _groups.value = GroupsUi.Loading
        _login.value = LoginUi()
    }
}

class PadelEliteViewModelFactory(
    private val account: PadelEliteAccount,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = PadelEliteViewModel(account) as T
}
