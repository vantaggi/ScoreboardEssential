package it.vantaggi.scoreboardessential.padelelite

/** Il file v2 di una partita chiusa, o il perche' non c'e'. */
sealed interface PayloadOutcome {
    data class Json(
        val text: String,
    ) : PayloadOutcome

    /** La partita non e' (ancora) nel database: la riga si scrive un attimo dopo la chiusura. */
    data object Missing : PayloadOutcome

    /** La partita c'e' ma non ha un file valido (quattro giocatori, almeno un punto, identificativo). */
    data object Incomplete : PayloadOutcome
}

/** Cosa deve fare il lavoro in coda dopo un tentativo. */
enum class RunOutcome {
    /** Finito, bene (consegnata) o male senza rimedio (lo stato dice perche'): niente altro da fare. */
    DONE,

    /** Riprovare piu' tardi, con l'attesa crescente di WorkManager. */
    RETRY,
}

/**
 * Un tentativo di invio di una partita: costruisce il file, lo consegna, e scrive nello
 * [InvioStore] il risultato. E' la logica del lavoro in coda, senza WorkManager: cosi' ogni
 * errore del contratto si prova con un server finto e un conteggio dei tentativi.
 *
 * Il contratto (`docs/dashboard/SCOREBOARD_FORMAT.md` §6):
 * - rete, 5xx e casella piena si **ritentano** con lo stesso file (la voce e' idempotente);
 * - `invalid_payload` e `payload_too_large` **non** si ritentano: e' un difetto del file;
 * - `not_authenticated` (dopo un rinnovo gia' tentato) vuol dire accedere di nuovo;
 * - un invio automatico non deve ripetersi a ogni avvio: dopo una consegna riuscita il lavoro e'
 *   finito, e uno scarto dell'admin non lo rimette in coda (solo un nuovo comando dell'utente).
 */
class InvioRunner(
    private val account: PadelEliteAccount,
    private val store: InvioStore,
    private val payload: suspend (matchUuid: String) -> PayloadOutcome,
) {
    suspend fun run(
        matchUuid: String,
        groupId: String,
        attempt: Int,
    ): RunOutcome {
        val file =
            when (val esito = payload(matchUuid)) {
                is PayloadOutcome.Json -> esito.text
                PayloadOutcome.Missing ->
                    return if (attempt < MAX_MISSING_ATTEMPTS) waiting(matchUuid, null) else fail(matchUuid, InvioReason.NO_FILE)
                PayloadOutcome.Incomplete -> return fail(matchUuid, InvioReason.NO_FILE)
            }
        if (file.toByteArray(Charsets.UTF_8).size > MAX_PAYLOAD_BYTES) return fail(matchUuid, InvioReason.TOO_LARGE)

        return when (val esito = account.submit(groupId, file)) {
            is SubmitResult.Accepted -> {
                val stato =
                    when (esito.item.status) {
                        "imported" -> InvioState.IMPORTED
                        "discarded" -> InvioState.DISCARDED
                        else -> InvioState.SENT
                    }
                store.set(matchUuid, InvioInfo(stato))
                RunOutcome.DONE
            }

            SubmitResult.NotAuthenticated -> {
                store.set(matchUuid, InvioInfo(InvioState.LOGIN_AGAIN))
                RunOutcome.DONE
            }

            SubmitResult.NotAuthorized -> fail(matchUuid, InvioReason.NOT_MEMBER)
            is SubmitResult.InvalidPayload -> fail(matchUuid, InvioReason.INVALID_PAYLOAD, esito.details)
            SubmitResult.PayloadTooLarge -> fail(matchUuid, InvioReason.TOO_LARGE)
            is SubmitResult.InboxFull -> waiting(matchUuid, InvioReason.INBOX_FULL)
            SubmitResult.Network -> waiting(matchUuid, null)
            is SubmitResult.Unexpected ->
                if (attempt < MAX_UNEXPECTED_ATTEMPTS) waiting(matchUuid, null) else fail(matchUuid, InvioReason.SERVER)
        }
    }

    private fun waiting(
        matchUuid: String,
        reason: InvioReason?,
    ): RunOutcome {
        store.set(matchUuid, InvioInfo(InvioState.QUEUED, reason))
        return RunOutcome.RETRY
    }

    private fun fail(
        matchUuid: String,
        reason: InvioReason,
        detail: String? = null,
    ): RunOutcome {
        store.set(matchUuid, InvioInfo(InvioState.UNSENDABLE, reason, detail))
        return RunOutcome.DONE
    }

    /**
     * Per le partite SENT chiede alla casella com'e' andata: importata o scartata dall'admin. Si
     * ferma al primo errore di rete o di accesso (le altre falliranno uguale). Non cambia lo
     * stato di chi non e' piu' in attesa.
     */
    suspend fun refreshStatuses() {
        for ((uuid, info) in store.states.value) {
            if (info.state != InvioState.SENT) continue
            when (val esito = account.status(uuid)) {
                is StatusResult.Found ->
                    when (esito.status) {
                        "imported" -> store.set(uuid, InvioInfo(InvioState.IMPORTED))
                        "discarded" -> store.set(uuid, InvioInfo(InvioState.DISCARDED))
                    }

                StatusResult.NotFound -> Unit
                StatusResult.NotAuthenticated, StatusResult.Network -> return
            }
        }
    }

    companion object {
        /** Il limite del server (200 KB; il contratto non dice se da 1000 o 1024 byte: si prende il piu' stretto). */
        const val MAX_PAYLOAD_BYTES = 200_000

        /** La riga della partita si scrive un attimo dopo la chiusura: poche attese, poi e' un difetto. */
        const val MAX_MISSING_ATTEMPTS = 3

        /** Una risposta fuori contratto (404 se la migrazione 64 non c'e' ancora): cinque giri, poi si ferma. */
        const val MAX_UNEXPECTED_ATTEMPTS = 5
    }
}
