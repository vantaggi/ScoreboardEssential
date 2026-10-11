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
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.vantaggi.scoreboardessential.ScoreboardEssentialApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/**
 * Un tentativo di chiusura della diretta (`end_live_from_app`), senza WorkManager: cosi' ogni esito del
 * contratto si prova con un server finto.
 *
 * A partita finita ([conRisultato]) si chiude COL FILE FINALE, rifatto dal database con i collegamenti del
 * gruppo (lo stesso dell'invio alla casella, con il vincitore). Anche una partita chiusa senza vincitore
 * (interrotta a mano e salvata) porta il suo file, col `winnerTeam` nullo: il server lo accetta. Si chiude
 * senza file se non c'e' (la riga non e' ancora scritta: si aspettano tre giri) o se il motore non lo costruisce
 * ([PayloadOutcome.Incomplete]). Scartata, cambio sport o diretta interrotta con l'interruttore: senza file, sempre.
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
        // Un file rifiutato non blocca la chiusura. Anche se il file lo puo' scrivere solo chi ha APERTO la diretta
        // (un altro admin la vede rifiutata): senza file chiude qualunque owner o admin del gruppo.
        val rifiutato =
            esito is LiveResult.InvalidPayload ||
                esito == LiveResult.PayloadTooLarge ||
                esito == LiveResult.NotAuthorized ||
                esito == LiveResult.LiveConflict
        if (file != null && rifiutato) {
            esito = account.endLive(groupId, matchId, null)
        }
        return when (esito) {
            is LiveResult.Ended, LiveResult.NotFound -> {
                RunOutcome.DONE
            }

            LiveResult.Network, LiveResult.LiveFull, LiveResult.JoinCodeExhausted -> {
                RunOutcome.RETRY
            }

            // Senza accesso valido (la sessione puo' tornare se l'utente accede di nuovo) si ritenta con un tetto; finito il tetto
            // si rinuncia davvero (FAILED): il foglio PARTITA lo dice, la diretta resta aperta sul server.
            is LiveResult.Unexpected, LiveResult.NotAuthenticated -> {
                if (attempt < MAX_UNEXPECTED_ATTEMPTS) RunOutcome.RETRY else RunOutcome.GIVE_UP
            }

            // Senza permesso, o la diretta e' di un altro admin: non c'e' niente da ritentare.
            LiveResult.NotAuthorized, LiveResult.LiveConflict -> {
                RunOutcome.DONE
            }

            is LiveResult.InvalidPayload, LiveResult.PayloadTooLarge, is LiveResult.Published -> {
                RunOutcome.DONE
            }
        }
    }

    companion object {
        /** La riga della partita si scrive un attimo dopo la chiusura: poche attese, poi si chiude senza risultato. */
        const val MAX_MISSING_ATTEMPTS = 3
        const val MAX_UNEXPECTED_ATTEMPTS = 5
    }
}

/** Come sta la chiusura finale di una diretta, vista dal foglio PARTITA (D-3): una riga, solo se c'e' qualcosa da dire. */
enum class ChiusuraFase {
    /** Chiusa (o niente da chiudere): il foglio dice solo "Diretta chiusa". */
    NESSUNA,

    /** Il lavoro in coda non e' ancora riuscito: aspetta la rete o ritenta. */
    IN_CORSO,

    /** Dieci tentativi e niente: la diretta resta aperta sul server finche' non la chiude la dashboard o la pulizia delle 6 ore. */
    FALLITA,
}

/** Dallo stato del lavoro unico di chiusura (uno solo per partita, ma si guarda l'elenco) alla fase che il foglio mostra. */
fun chiusuraFase(lavori: List<WorkInfo>): ChiusuraFase =
    when {
        lavori.any { it.state == WorkInfo.State.FAILED } -> ChiusuraFase.FALLITA
        lavori.any { !it.state.isFinished } -> ChiusuraFase.IN_CORSO
        else -> ChiusuraFase.NESSUNA
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
            RunOutcome.GIVE_UP -> Result.failure()
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

        /** La fase della chiusura di [matchId], dal lavoro in coda: cambia da sola quando WorkManager ritenta, riesce o rinuncia. */
        fun fase(
            context: Context,
            matchId: String,
        ): Flow<ChiusuraFase> =
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWorkFlow(workName(matchId))
                .map(::chiusuraFase)

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
