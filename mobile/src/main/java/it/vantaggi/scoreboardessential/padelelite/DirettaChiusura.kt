package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.vantaggi.scoreboardessential.ScoreboardEssentialApplication
import java.util.concurrent.TimeUnit

/**
 * Un tentativo di chiusura della diretta (`end_live_from_app`), senza WorkManager: cosi' ogni esito del
 * contratto si prova con un server finto.
 *
 * A partita finita ([conRisultato]) si chiude COL FILE FINALE, rifatto dal database con i collegamenti del
 * gruppo (lo stesso dell'invio alla casella, con il vincitore); se il file non c'e' (la riga non e'
 * ancora scritta: si aspetta tre giri) o non e' finale (partita interrotta, senza vincitore: il
 * server non lo vuole), si chiude senza. Scartata o interrotta: senza, sempre.
 *
 * Il risultato finale non deve perdersi: rete e 5xx si ritentano. Un file che il server rifiuta
 * (`invalid_payload`, `payload_too_large`) non si ritenta: si chiude senza, almeno la diretta finisce.
 */
class DirettaChiusuraRunner(
    private val account: PadelEliteAccount,
    /** Il file finale della partita per il gruppo, o il perche' non c'e' ([PayloadOutcome]). */
    private val finale: suspend (matchId: String, groupId: String) -> PayloadOutcome,
) {
    suspend fun run(
        matchId: String,
        groupId: String,
        conRisultato: Boolean,
        attempt: Int,
    ): RunOutcome {
        var file: String? = null
        if (conRisultato) {
            when (val esito = finale(matchId, groupId)) {
                is PayloadOutcome.Json -> {
                    file = esito.text
                }

                PayloadOutcome.Missing -> {
                    if (attempt < MAX_MISSING_ATTEMPTS) return RunOutcome.RETRY
                }

                PayloadOutcome.Incomplete -> {
                    Unit
                }
            }
        }
        if (file != null && file.toByteArray(Charsets.UTF_8).size > InvioRunner.MAX_PAYLOAD_BYTES) file = null
        var esito = account.endLive(groupId, matchId, file)
        if (file != null && (esito is LiveResult.InvalidPayload || esito == LiveResult.PayloadTooLarge)) {
            esito = account.endLive(groupId, matchId, null)
        }
        return when (esito) {
            is LiveResult.Ended, LiveResult.NotFound -> RunOutcome.DONE

            LiveResult.Network, LiveResult.LiveFull, LiveResult.JoinCodeExhausted -> RunOutcome.RETRY

            is LiveResult.Unexpected -> if (attempt < MAX_UNEXPECTED_ATTEMPTS) RunOutcome.RETRY else RunOutcome.DONE

            // Senza accesso valido, senza permesso, o la diretta e' di un altro admin: non c'e' niente da ritentare.
            LiveResult.NotAuthenticated, LiveResult.NotAuthorized, LiveResult.LiveConflict -> RunOutcome.DONE

            is LiveResult.InvalidPayload, LiveResult.PayloadTooLarge, is LiveResult.Published -> RunOutcome.DONE
        }
    }

    companion object {
        /** La riga della partita si scrive un attimo dopo la chiusura: poche attese, poi si chiude senza risultato. */
        const val MAX_MISSING_ATTEMPTS = 3
        const val MAX_UNEXPECTED_ATTEMPTS = 5
    }
}

/**
 * Il lavoro in coda della chiusura: **unico per partita** (`padel-elite-diretta-chiusura-<matchId>`, KEEP),
 * con rete e attesa crescente da 30 s; si ferma dopo [MAX_ATTEMPTS] tentativi. Il file non viaggia nel
 * lavoro (e' grande): lo rifa' [DirettaChiusuraRunner] dal database.
 */
class DirettaChiusuraWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    /** I test mettono qui un runner con un server finto; in produzione lo da' l'applicazione. */
    internal var runner: DirettaChiusuraRunner? = null

    override suspend fun doWork(): Result {
        val matchId = inputData.getString(KEY_MATCH) ?: return Result.failure()
        val groupId = inputData.getString(KEY_GROUP) ?: return Result.failure()
        val conRisultato = inputData.getBoolean(KEY_RESULT, false)
        val esecutore = runner ?: (applicationContext as ScoreboardEssentialApplication).padelElite.chiusuraDiretta
        return when (esecutore.run(matchId, groupId, conRisultato, runAttemptCount)) {
            RunOutcome.DONE -> Result.success()
            RunOutcome.RETRY -> if (runAttemptCount + 1 >= MAX_ATTEMPTS) Result.failure() else Result.retry()
        }
    }

    companion object {
        const val KEY_MATCH = "matchId"
        const val KEY_GROUP = "groupId"
        const val KEY_RESULT = "conRisultato"
        const val MAX_ATTEMPTS = 10
        private const val BACKOFF_SECONDS = 30L

        fun workName(matchId: String) = "padel-elite-diretta-chiusura-$matchId"

        fun request(richiesta: DirettaChiusuraRichiesta): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<DirettaChiusuraWorker>()
                .setInputData(
                    Data
                        .Builder()
                        .putString(KEY_MATCH, richiesta.matchId)
                        .putString(KEY_GROUP, richiesta.groupId)
                        .putBoolean(KEY_RESULT, richiesta.conRisultato)
                        .build(),
                ).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()

        /** KEEP: una chiusura gia' in coda per questa partita non si sostituisce (un secondo tocco non ne fa due). */
        fun enqueue(
            context: Context,
            richiesta: DirettaChiusuraRichiesta,
        ) {
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(workName(richiesta.matchId), ExistingWorkPolicy.KEEP, request(richiesta))
        }
    }
}
