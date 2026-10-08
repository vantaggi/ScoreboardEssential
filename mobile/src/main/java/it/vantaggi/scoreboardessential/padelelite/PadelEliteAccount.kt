package it.vantaggi.scoreboardessential.padelelite

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Esito della richiesta di un token valido. */
sealed interface TokenResult {
    data class Ok(
        val session: PadelEliteSession,
    ) : TokenResult

    /** Nessuna sessione, o il rinnovo e' stato rifiutato: l'utente deve rifare l'accesso. */
    data object NeedLogin : TokenResult

    data object Network : TokenResult
}

sealed interface GroupsOutcome {
    data class Ok(
        val groups: List<PadelEliteGroup>,
    ) : GroupsOutcome

    data object NeedLogin : GroupsOutcome

    data object Network : GroupsOutcome
}

sealed interface RosterOutcome {
    data class Ok(
        val players: List<RemotePlayer>,
    ) : RosterOutcome

    data object NeedLogin : RosterOutcome

    /** Non si e' piu' membri del gruppo: va scelto un altro gruppo. */
    data object NotAuthorized : RosterOutcome

    data object Network : RosterOutcome
}

/**
 * L'account Padel Elite dell'utente: accesso, uscita, rinnovo automatico del token, gruppo scelto
 * e le chiamate che richiedono di essere autenticati.
 *
 * Un token scaduto (o rifiutato dal server con 401) si rinnova da solo **una volta** e la
 * chiamata si ripete; se anche il rinnovo e' rifiutato la sessione si cancella e si chiede
 * l'accesso. Il rinnovo e' sotto un [Mutex]: Supabase invalida il refresh token a ogni uso, e due
 * rinnovi insieme (la coda e la schermata dei gruppi) ne brucerebbero uno.
 */
class PadelEliteAccount(
    val config: PadelEliteConfig,
    private val api: PadelEliteApi,
    private val store: SessionStore,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val mutex = Mutex()

    /** La funzione e' accesa solo con la configurazione completa. */
    val isEnabled: Boolean get() = config.isConfigured

    fun session(): PadelEliteSession? = if (isEnabled) store.load() else null

    /** Il gruppo scelto (id, nome), o null. */
    fun selectedGroup(): Pair<String, String>? = store.selectedGroup()

    fun selectGroup(group: PadelEliteGroup) = store.selectGroup(group)

    fun clearSelectedGroup() = store.clearGroup()

    suspend fun signIn(
        email: String,
        password: String,
    ): AuthResult {
        val esito = api.signIn(email.trim(), password)
        if (esito is AuthResult.Ok) {
            // Un altro utente non eredita il gruppo del precedente.
            if (store.load()?.userId != esito.session.userId) store.clearGroup()
            store.save(esito.session)
        }
        return esito
    }

    fun signOut() = store.clear()

    /**
     * Un token di accesso valido. Si rinnova se scade entro un minuto, o (con [force]) se il server
     * ha appena rifiutato [rejected]: se nel frattempo qualcun altro l'ha gia' rinnovato si usa
     * quello, senza bruciare un altro refresh token.
     */
    suspend fun accessToken(
        force: Boolean = false,
        rejected: String? = null,
    ): TokenResult =
        mutex.withLock {
            val corrente = store.load() ?: return@withLock TokenResult.NeedLogin
            val ancoraBuono = corrente.expiresAtSec - clock() > RENEW_MARGIN_SEC
            if (!force && ancoraBuono) return@withLock TokenResult.Ok(corrente)
            if (force && rejected != null && corrente.accessToken != rejected) return@withLock TokenResult.Ok(corrente)
            when (val rinnovo = api.refresh(corrente.refreshToken)) {
                is AuthResult.Ok -> {
                    store.save(rinnovo.session)
                    TokenResult.Ok(rinnovo.session)
                }

                is AuthResult.Rejected -> {
                    store.clear()
                    TokenResult.NeedLogin
                }

                AuthResult.Network -> {
                    TokenResult.Network
                }
            }
        }

    /**
     * Esegue [call] col token valido; se il server lo rifiuta ([isUnauthorized]) lo rinnova e
     * ripete una volta sola.
     */
    private suspend fun <R> authenticated(
        needLogin: R,
        network: R,
        isUnauthorized: (R) -> Boolean,
        call: suspend (PadelEliteSession) -> R,
    ): R {
        val sessione =
            when (val token = accessToken()) {
                is TokenResult.Ok -> token.session
                TokenResult.NeedLogin -> return needLogin
                TokenResult.Network -> return network
            }
        val primo = call(sessione)
        if (!isUnauthorized(primo)) return primo
        val rinnovata =
            when (val token = accessToken(force = true, rejected = sessione.accessToken)) {
                is TokenResult.Ok -> token.session
                TokenResult.NeedLogin -> return needLogin
                TokenResult.Network -> return network
            }
        return call(rinnovata)
    }

    suspend fun groups(): GroupsOutcome =
        when (
            val esito =
                authenticated<GroupsResult>(
                    needLogin = GroupsResult.NotAuthenticated,
                    network = GroupsResult.Network,
                    isUnauthorized = { it == GroupsResult.NotAuthenticated },
                ) { api.fetchGroups(it.accessToken, it.userId) }
        ) {
            is GroupsResult.Ok -> GroupsOutcome.Ok(esito.groups)
            GroupsResult.NotAuthenticated -> GroupsOutcome.NeedLogin
            GroupsResult.Network -> GroupsOutcome.Network
        }

    /** La rosa del gruppo [groupId] dalla dashboard, con lo stesso rinnovo del token delle altre chiamate. */
    suspend fun roster(groupId: String): RosterOutcome =
        when (
            val esito =
                authenticated<RosterResult>(
                    needLogin = RosterResult.NotAuthenticated,
                    network = RosterResult.Network,
                    isUnauthorized = { it == RosterResult.NotAuthenticated },
                ) { api.fetchRoster(it.accessToken, groupId) }
        ) {
            is RosterResult.Ok -> RosterOutcome.Ok(esito.players)
            RosterResult.NotAuthenticated -> RosterOutcome.NeedLogin
            RosterResult.NotAuthorized -> RosterOutcome.NotAuthorized
            RosterResult.Network -> RosterOutcome.Network
        }

    suspend fun submit(
        groupId: String,
        payloadJson: String,
    ): SubmitResult =
        authenticated(
            needLogin = SubmitResult.NotAuthenticated,
            network = SubmitResult.Network,
            isUnauthorized = { it == SubmitResult.NotAuthenticated },
        ) { api.submit(it.accessToken, groupId, payloadJson) }

    suspend fun status(matchUuid: String): StatusResult =
        authenticated(
            needLogin = StatusResult.NotAuthenticated,
            network = StatusResult.Network,
            isUnauthorized = { it == StatusResult.NotAuthenticated },
        ) { api.fetchStatus(it.accessToken, matchUuid) }

    private companion object {
        const val RENEW_MARGIN_SEC = 60L
    }
}
