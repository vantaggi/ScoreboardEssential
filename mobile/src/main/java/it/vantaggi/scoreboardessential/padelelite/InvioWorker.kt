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
 * Il lavoro in coda: un tentativo di invio di una partita. Il dato che serve sta tutto nei due
 * parametri (`matchUuid` e gruppo scelto al momento del comando), il file lo rifa' [InvioRunner]
 * dal database a ogni tentativo: niente file da 170 KB dentro WorkManager (il suo limite e' 10 KB).
 */
class InvioWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    /** I test mettono qui un [InvioRunner] con un server finto; in produzione lo da' l'applicazione. */
    internal var runner: InvioRunner? = null

    override suspend fun doWork(): Result {
        val matchUuid = inputData.getString(KEY_MATCH) ?: return Result.failure()
        val groupId = inputData.getString(KEY_GROUP) ?: return Result.failure()
        val esecutore = runner ?: (applicationContext as ScoreboardEssentialApplication).padelElite.runner
        return when (esecutore.run(matchUuid, groupId, runAttemptCount)) {
            RunOutcome.DONE -> Result.success()
            RunOutcome.RETRY -> Result.retry()
        }
    }

    companion object {
        const val KEY_MATCH = "matchUuid"
        const val KEY_GROUP = "groupId"
        private const val BACKOFF_SECONDS = 30L

        /** Un lavoro unico per partita: il nome e' l'identificativo del file, lo stesso del server. */
        fun workName(matchUuid: String) = "padel-elite-invio-$matchUuid"

        /** Con rete, e con attesa crescente (30 s, 1 min, 2 min... fino ai 5 ore di WorkManager) dopo ogni ritentativo. */
        fun request(
            matchUuid: String,
            groupId: String,
        ): OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<InvioWorker>()
                .setInputData(
                    Data
                        .Builder()
                        .putString(KEY_MATCH, matchUuid)
                        .putString(KEY_GROUP, groupId)
                        .build(),
                ).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()

        /**
         * Mette la partita in coda. KEEP: se per questa partita c'e' gia' un lavoro in attesa o in
         * corso, il comando ripetuto non ne crea un secondo; un lavoro finito si sostituisce, ed e'
         * cosi' che "rimanda" funziona dopo un errore.
         */
        fun enqueue(
            context: Context,
            store: InvioStore,
            matchUuid: String,
            groupId: String,
        ) {
            store.set(matchUuid, InvioInfo(InvioState.QUEUED))
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(workName(matchUuid), ExistingWorkPolicy.KEEP, request(matchUuid, groupId))
        }
    }
}
