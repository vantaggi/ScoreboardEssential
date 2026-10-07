package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * La coda su WorkManager: un lavoro unico per partita, con rete e attesa crescente; il lavoro
 * ritenta solo dove il runner lo dice.
 */
@RunWith(AndroidJUnit4::class)
class InvioWorkerTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private var finto: ServerFinto? = null

    @Before
    fun avvia() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
    }

    @After
    fun chiudi() {
        finto?.chiudi()
    }

    @Test
    fun `il lavoro vuole la rete e un'attesa crescente fra i ritentativi`() {
        val spec = InvioWorker.request(UUID_PARTITA, "g-1").workSpec

        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(TimeUnit.SECONDS.toMillis(30), spec.backoffDelayDuration)
        assertEquals(UUID_PARTITA, spec.input.getString(InvioWorker.KEY_MATCH))
        assertEquals("g-1", spec.input.getString(InvioWorker.KEY_GROUP))
    }

    @Test
    fun `il comando ripetuto non crea un secondo lavoro per la stessa partita`() {
        val store = InvioStore(preferenze(context, "invii_worker"))

        InvioWorker.enqueue(context, store, UUID_PARTITA, "g-1")
        InvioWorker.enqueue(context, store, UUID_PARTITA, "g-1")
        InvioWorker.enqueue(context, store, UUID_PARTITA, "g-1")

        val lavori = WorkManager.getInstance(context).getWorkInfosForUniqueWork(InvioWorker.workName(UUID_PARTITA)).get()
        assertEquals(1, lavori.size)
        assertEquals(WorkInfo.State.ENQUEUED, lavori.single().state)
        assertEquals(InvioInfo(InvioState.QUEUED), store.get(UUID_PARTITA))
    }

    @Test
    fun `due partite diverse sono due lavori`() {
        val store = InvioStore(preferenze(context, "invii_worker"))

        InvioWorker.enqueue(context, store, "uuid-uno-12345678", "g-1")
        InvioWorker.enqueue(context, store, "uuid-due-12345678", "g-1")

        val manager = WorkManager.getInstance(context)
        assertEquals(1, manager.getWorkInfosForUniqueWork(InvioWorker.workName("uuid-uno-12345678")).get().size)
        assertEquals(1, manager.getWorkInfosForUniqueWork(InvioWorker.workName("uuid-due-12345678")).get().size)
    }

    /** Il lavoro vero, con un runner che parla col server finto. */
    private fun lavoro(
        risposta: okhttp3.mockwebserver.MockResponse,
        tentativo: Int,
    ): ListenableWorker.Result {
        val s = ServerFinto { _, _ -> risposta }.also { finto = it }
        val store = SessionStore(preferenze(context, "sessione_worker"), SoftwareSecretBox())
        store.save(PadelEliteSession("A1", "R1", Long.MAX_VALUE / 2, "u-1", "a@b.it"))
        val account = PadelEliteAccount(s.config(), PadelEliteApi(s.config()), store)
        val runner = InvioRunner(account, InvioStore(preferenze(context, "invii_worker"))) { PayloadOutcome.Json(FILE_V2) }
        val dati =
            androidx.work.Data
                .Builder()
                .putString(InvioWorker.KEY_MATCH, UUID_PARTITA)
                .putString(InvioWorker.KEY_GROUP, "g-1")
                .build()
        val worker =
            TestListenableWorkerBuilder
                .from(context, InvioWorker::class.java)
                .setInputData(dati)
                .setRunAttemptCount(tentativo)
                .setWorkerFactory(
                    object : WorkerFactory() {
                        override fun createWorker(
                            appContext: Context,
                            workerClassName: String,
                            workerParameters: WorkerParameters,
                        ) = InvioWorker(appContext, workerParameters).also { it.runner = runner }
                    },
                ).build()
        return runBlocking { worker.doWork() }
    }

    @Test
    fun `una consegna riuscita chiude il lavoro`() {
        assertEquals(ListenableWorker.Result.success(), lavoro(rispostaDiInvio(), 0))
    }

    @Test
    fun `un 5xx fa ritentare il lavoro`() {
        assertEquals(ListenableWorker.Result.retry(), lavoro(json(503, ""), 2))
    }

    @Test
    fun `un file non valido chiude il lavoro senza ritentare`() {
        assertEquals(ListenableWorker.Result.success(), lavoro(erroreRpc(400, "22023", "invalid_payload", "players"), 0))
        assertEquals(1, finto!!.quante("/rest/v1/rpc/submit_scoreboard_match"))
    }
}
