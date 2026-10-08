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

    /** Voce in attesa di cui non si conosce il gruppo (stato di una versione precedente): non si rimanda. */
    NOT_RESENDABLE,
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
        InvioRunner(account, invii) { uuid, groupId ->
            // Una lettura sola dei collegamenti: il file e la sua firma non possono divergere.
            val salvato = repository.buildSavedExportByUuidWithLinks(uuid, groupId)
            when (val esito = salvato?.result) {
                null -> {
                    PayloadOutcome.Missing
                }

                is ExportResult.Incomplete -> {
                    PayloadOutcome.Incomplete
                }

                is ExportResult.Ready -> {
                    // Senza matchId il server rifiuta il file: meglio dirlo qui che dopo la rete.
                    if (esito.export.matchId.isNullOrEmpty()) {
                        PayloadOutcome.Incomplete
                    } else {
                        PayloadOutcome.Json(
                            MatchExporter.toJson(esito.export),
                            FirmaCollegamenti.of(salvato?.links.orEmpty(), groupId, esito.export.players.map { it.localId }),
                        )
                    }
                }
            }
        }
    }

    /**
     * Il comando dall'interfaccia: mette in coda la partita, oppure, se manca l'accesso o il gruppo,
     * apre la schermata di Padel Elite. Lo stesso per la card dello storico e il dialogo di fine partita.
     */
    fun sendOrOpenLogin(
        activity: Context,
        matchUuid: String,
    ) {
        when (send(matchUuid)) {
            SendOutcome.NEED_LOGIN, SendOutcome.NEED_GROUP -> activity.startActivity(PadelEliteActivity.intent(activity))
            SendOutcome.QUEUED, SendOutcome.DISABLED, SendOutcome.NOT_RESENDABLE -> Unit
        }
    }

    /**
     * Il comando: mette in coda la partita o dice dove andare per poterlo fare.
     *
     * Per una partita gia' nella casella e ancora in attesa e' "Invia di nuovo": va **al gruppo della
     * voce** ([InvioInfo.group]), non a quello scelto adesso (un gruppo cambiato creerebbe un doppione
     * in un'altra casella), e il lavoro rifa' il file coi collegamenti di quel gruppo. Se della voce non
     * si conosce il gruppo (stato di una versione precedente) non si rimanda: [SendOutcome.NOT_RESENDABLE].
     */
    fun send(matchUuid: String): SendOutcome {
        if (!isEnabled) return SendOutcome.DISABLED
        if (account.session() == null) return SendOutcome.NEED_LOGIN
        val voce = invii.get(matchUuid)?.takeIf { it.hasEntryToResend }
        if (voce != null) {
            val gruppoDellaVoce = voce.group ?: return SendOutcome.NOT_RESENDABLE
            InvioWorker.enqueue(context, invii, matchUuid, gruppoDellaVoce)
            return SendOutcome.QUEUED
        }
        val gruppo = account.selectedGroup() ?: return SendOutcome.NEED_GROUP
        InvioWorker.enqueue(context, invii, matchUuid, gruppo.first)
        return SendOutcome.QUEUED
    }

    /** Con l'accesso fatto "Invia di nuovo" puo' partire (va al gruppo della voce, non serve un gruppo scelto); senza, la card non lo offre. */
    fun hasAccess(): Boolean = isEnabled && account.session() != null
}
