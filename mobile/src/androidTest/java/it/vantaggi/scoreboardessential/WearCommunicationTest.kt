package it.vantaggi.scoreboardessential

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import it.vantaggi.scoreboardessential.communication.OptimizedWearDataSync
import it.vantaggi.scoreboardessential.communication.WearConstants
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import android.net.Uri

@RunWith(AndroidJUnit4::class)
class WearCommunicationTest {

    @Test
    fun testNodeConnection() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val nodeClient = Wearable.getNodeClient(context)

        val nodes = Tasks.await(nodeClient.connectedNodes)

        assertTrue("At least one node should be connected", nodes.isNotEmpty())
        nodes.forEach { node ->
            Log.d("WearTest", "Connected node: ${node.displayName} (${node.id})")
        }
    }

    @Test
    fun testScoreSync() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val syncManager = OptimizedWearDataSync(context)

        // Test invio score con urgent
        syncManager.syncScores(2, 3, urgent = true)

        // Attendi sincronizzazione
        delay(2000)

        // Verifica che i dati siano stati salvati
        val dataClient = Wearable.getDataClient(context)
        val dataItems = Tasks.await(dataClient.getDataItems(
            Uri.parse("wear://*/scoreboard/score_data")
        ))

        assertTrue("Score data should be synced", dataItems.count > 0)

        dataItems.release()
    }

    @Test
    fun testMessageRetry() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val syncManager = OptimizedWearDataSync(context)

        // Test invio messaggio con retry
        syncManager.sendMessageWithRetry(
            WearConstants.MSG_TIMER_ACTION,
            "START".toByteArray(),
            maxRetries = 3
        )

        // Verifica nei log che il retry sia avvenuto se necessario
    }
}