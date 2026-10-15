package it.vantaggi.scoreboardessential.padelelite

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import it.vantaggi.scoreboardessential.ScoreboardEssentialApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * Le chiusure rimaste segnate dal processo di prima si accodano all'avvio del processo, senza aprire la schermata
 * (D-3, debito 13): un messaggio dell'orologio fa risorgere il processo senza `MainViewModel`, e prima la chiusura restava
 * segnata finche' l'app non si apriva.
 */
@RunWith(AndroidJUnit4::class)
class DirettaAlAvvioTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var store: DirettaStore

    @Before
    fun prepara() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        store = DirettaStore(preferenze(context, DirettaStore.FILE))
    }

    @After
    fun chiudi() {
        scope.cancel()
    }

    private fun lavori(matchId: String) =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(DirettaChiusuraWorker.workName(matchId)).get()

    private fun attendi(
        che: String,
        entroMs: Long = 6_000,
        condizione: () -> Boolean,
    ) {
        val limite = System.currentTimeMillis() + entroMs
        while (!condizione()) {
            check(System.currentTimeMillis() < limite) { "non e' successo: $che" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `all'avvio le chiusure rimaste si accodano una per partita e i segni si tolgono a lavoro scritto`() {
        val a = DirettaChiusuraRichiesta("m-a", "g-1", conRisultato = true)
        val b = DirettaChiusuraRichiesta("m-b", "g-2", conRisultato = false)
        store.segnaPendente(a)
        store.segnaPendente(b)

        val lavoro = DirettaChiusuraWorker.accodaLeRimaste(context, store, scope)

        assertNotNull(lavoro)
        runBlocking { lavoro!!.join() }
        assertEquals(1, lavori("m-a").size)
        assertEquals(1, lavori("m-b").size)
        assertTrue("scritti: i segni si tolgono", store.pendenti().isEmpty())
    }

    @Test
    fun `non e' un doppione se il lavoro c'e' gia' (lavoro unico per partita, KEEP)`() {
        val richiesta = DirettaChiusuraRichiesta("m-a", "g-1", conRisultato = true)
        store.segnaPendente(richiesta)
        runBlocking { DirettaChiusuraWorker.accodaLeRimaste(context, store, scope)!!.join() }
        // Il ripristino della schermata (o un secondo avvio) ritrova lo stesso segno e lo accoda ancora.
        store.segnaPendente(richiesta)

        runBlocking { DirettaChiusuraWorker.accodaLeRimaste(context, store, scope)!!.join() }

        assertEquals("un lavoro solo", 1, lavori("m-a").size)
        assertTrue(store.pendenti().isEmpty())
    }

    @Test
    fun `senza segni non fa niente`() {
        assertNull(DirettaChiusuraWorker.accodaLeRimaste(context, store, scope))
        assertTrue(lavori("m-a").isEmpty())
    }

    @Test
    fun `la diretta salvata chiusa un attimo prima della morte si dimentica, quella di un'altra partita no`() {
        store.save(DirettaSalvata("m-a", "g-1", "Padel"))
        store.segnaPendente(DirettaChiusuraRichiesta("m-a", "g-1", conRisultato = false))
        runBlocking { DirettaChiusuraWorker.accodaLeRimaste(context, store, scope)!!.join() }
        // Tolto il segno, solo il ripristino potrebbe riaccenderla: per questo si dimentica prima.
        assertNull(store.load())

        store.save(DirettaSalvata("m-viva", "g-1", "Padel"))
        store.segnaPendente(DirettaChiusuraRichiesta("m-altra", "g-1", conRisultato = false))
        runBlocking { DirettaChiusuraWorker.accodaLeRimaste(context, store, scope)!!.join() }
        assertEquals(DirettaSalvata("m-viva", "g-1", "Padel"), store.load())
    }

    @Test
    fun `l'avvio del processo toglie l'avviso del tetto delle 6 ore di una diretta che il processo morto non trasmette piu'`() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val avviso = DirettaAvvisoNotifica(context)
        val notifiche = shadowOf(context.getSystemService(NotificationManager::class.java))
        avviso.mostra("Padel")
        assertNotNull("l'avviso c'e'", notifiche.getNotification(DirettaAvvisoNotifica.ID_AVVISO))

        (context as ScoreboardEssentialApplication).onCreate()

        assertNull(notifiche.getNotification(DirettaAvvisoNotifica.ID_AVVISO))
    }

    @Test
    fun `l'avvio del processo le accoda anche senza aprire alcuna schermata`() {
        store.segnaPendente(DirettaChiusuraRichiesta("m-a", "g-1", conRisultato = true))
        val app = context as ScoreboardEssentialApplication

        // Il processo che risorge: Application.onCreate, nient'altro (nessuna activity, nessun MainViewModel).
        app.onCreate()

        attendi("il lavoro di chiusura in coda") { lavori("m-a").size == 1 }
        attendi("il segno tolto") { store.pendenti().isEmpty() }
    }
}
