package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import it.vantaggi.scoreboardessential.core.ExportResult
import it.vantaggi.scoreboardessential.core.MatchExporter
import it.vantaggi.scoreboardessential.repository.MatchRepository

/** Cosa succede al comando "Invia a Padel Elite". */
enum class SendOutcome {
    /** Funzione spenta (nessuna configurazione): il comando non dovrebbe nemmeno esserci. */
    DISABLED,

    /** Senza accesso: la schermata di accesso. */
    NEED_LOGIN,

    /** Accesso fatto ma nessun gruppo scelto: la schermata del gruppo. */
    NEED_GROUP,

    /** In coda: parte da sola. */
    QUEUED,
}

/**
 * Tutto cio' che serve per l'invio, in un posto solo: configurazione, account, stati, coda.
 * Lo tiene l'applicazione; con la funzione spenta non apre rete, preferenze ne' Keystore (le
 * parti pigre non si toccano finche' nessuno le chiede).
 */
class PadelEliteServices(
    private val context: Context,
    val config: PadelEliteConfig,
    private val repository: MatchRepository,
    accountFactory: () -> PadelEliteAccount = {
        PadelEliteAccount(config, PadelEliteApi(config), SessionStore.create(context))
    },
    invioStoreFactory: () -> InvioStore = {
        InvioStore(context.getSharedPreferences(InvioStore.FILE, Context.MODE_PRIVATE))
    },
) {
    /** La funzione e' accesa solo con la configurazione completa. */
    val isEnabled: Boolean get() = config.isConfigured

    val account: PadelEliteAccount by lazy(accountFactory)
    val invii: InvioStore by lazy(invioStoreFactory)

    val runner: InvioRunner by lazy {
        InvioRunner(account, invii) { uuid ->
            when (val esito = repository.buildSavedExportByUuid(uuid)) {
                null -> PayloadOutcome.Missing
                is ExportResult.Incomplete -> PayloadOutcome.Incomplete
                is ExportResult.Ready ->
                    // Senza matchId il server rifiuta il file: meglio dirlo qui che dopo la rete.
                    if (esito.export.matchId.isNullOrEmpty()) PayloadOutcome.Incomplete else PayloadOutcome.Json(MatchExporter.toJson(esito.export))
            }
        }
    }

    /** Il comando: mette in coda la partita o dice dove andare per poterlo fare. */
    fun send(matchUuid: String): SendOutcome {
        if (!isEnabled) return SendOutcome.DISABLED
        if (account.session() == null) return SendOutcome.NEED_LOGIN
        val gruppo = account.selectedGroup() ?: return SendOutcome.NEED_GROUP
        InvioWorker.enqueue(context, invii, matchUuid, gruppo.first)
        return SendOutcome.QUEUED
    }
}
